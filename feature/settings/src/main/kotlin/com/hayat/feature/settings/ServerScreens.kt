package com.hayat.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.ButtonVariant
import com.hayat.core.designsystem.component.HayatButton
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InlineEmpty
import com.hayat.core.designsystem.component.KeyValueRow
import com.hayat.core.designsystem.component.PillTabs
import com.hayat.core.designsystem.component.StatusDot
import com.hayat.core.designsystem.theme.HayatThemeExt
import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.sync.AuthResult
import com.hayat.core.domain.sync.ConnectionError
import com.hayat.core.domain.sync.HealthResult
import com.hayat.core.domain.sync.ServerApi
import com.hayat.core.domain.sync.ServerSession
import com.hayat.core.domain.sync.ServerStatus
import com.hayat.core.domain.sync.SessionStore
import com.hayat.core.domain.sync.SnapshotGet
import com.hayat.core.domain.sync.SnapshotSource
import com.hayat.core.domain.sync.SyncEngine
import com.hayat.core.domain.sync.SyncState
import com.hayat.core.domain.sync.SyncStatusProvider
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LocalFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Duration
import javax.inject.Inject

enum class AuthMode { EMAIL, KEY }

data class ConnectUiState(
    val url: String = "",
    val testing: Boolean = false,
    val health: HealthResult? = null,
    val mode: AuthMode = AuthMode.EMAIL,
    val signingIn: Boolean = false,
    val authError: ConnectionError? = null,
    /** Both this phone and the server hold data: the user decides which to keep. */
    val askConflict: Boolean = false,
    val done: Boolean = false,
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val api: ServerApi,
    private val sessions: SessionStore,
    private val source: SnapshotSource,
    private val syncState: SyncState,
    private val sync: SyncStatusProvider,
    private val prefs: PreferencesRepository,
    private val time: TimeProvider,
    config: AppConfig,
) : HayatViewModel() {
    private val _ui = MutableStateFlow(ConnectUiState(url = config.defaultServerUrl))
    val ui = _ui.asStateFlow()
    val session = sessions.session.stateInVm(null)
    val status = sync.status.stateInVm(ServerStatus.NOT_CONNECTED)
    private var pending: ServerSession? = null

    fun setUrl(v: String) = _ui.update { it.copy(url = v, health = null, authError = null) }
    fun setMode(m: AuthMode) = _ui.update { it.copy(mode = m, authError = null) }

    private fun url(): String? = SyncEngine.normalizeUrl(_ui.value.url)

    fun testConnection() = safeLaunch {
        val u = url() ?: run { _ui.update { it.copy(health = HealthResult.Failed(ConnectionError.INVALID_URL)) }; return@safeLaunch }
        if (!u.startsWith("https://")) { _ui.update { it.copy(health = HealthResult.Failed(ConnectionError.NOT_HTTPS)) }; return@safeLaunch }
        _ui.update { it.copy(testing = true, health = null, url = u) }
        val r = api.health(u)
        _ui.update { it.copy(testing = false, health = r) }
    }

    fun signIn(email: String, password: String, register: Boolean) = auth { u ->
        if (register) api.register(u, email, password) else api.login(u, email, password)
    }
    fun signInWithKey(key: String) = auth { u -> api.loginWithKey(u, key) }

    private fun auth(call: suspend (String) -> AuthResult) = safeLaunch {
        val u = url() ?: return@safeLaunch
        if (_ui.value.health !is HealthResult.Ok) return@safeLaunch
        _ui.update { it.copy(signingIn = true, authError = null) }
        when (val r = call(u)) {
            is AuthResult.Failed -> _ui.update { it.copy(signingIn = false, authError = r.error) }
            is AuthResult.Ok -> {
                val s = ServerSession(u, r.tokens.account, r.tokens.accessToken, r.tokens.refreshToken, r.tokens.accessExpiresAt)
                val serverHasData = api.getSnapshot(u, s.accessToken, null) is SnapshotGet.Found
                val localHasData = source.hasUserData()
                if (serverHasData && localHasData) {
                    pending = s
                    _ui.update { it.copy(signingIn = false, askConflict = true) }
                } else {
                    finish(s, keepLocal = localHasData)
                }
            }
        }
    }

    /** keepLocal = upload this phone's data (replacing the server copy); otherwise download. */
    fun resolveConflict(keepLocal: Boolean) = safeLaunch {
        val s = pending ?: return@safeLaunch
        pending = null
        _ui.update { it.copy(askConflict = false) }
        finish(s, keepLocal)
    }

    private suspend fun finish(s: ServerSession, keepLocal: Boolean) {
        sessions.save(s)
        syncState.setDirty(keepLocal)
        syncState.setFailure(null)
        prefs.update { it.copy(serverSetupPending = false) }
        sync.requestSync()
        _ui.update { it.copy(signingIn = false, done = true) }
    }

    fun syncNow() = sync.requestSync()

    fun logout() = safeLaunch {
        val s = sessions.load() ?: return@safeLaunch
        runCatching { api.logout(s.baseUrl, s.refreshToken) }
        sessions.clear()
        syncState.setDirty(false)
        syncState.setFailure(null)
        _ui.update { ConnectUiState(url = s.baseUrl) }
    }

    fun prepareChange() = _ui.update { ConnectUiState(url = session.value?.baseUrl ?: it.url) }

    fun minutesSince(s: ServerSession): Long? = s.lastSyncedAt?.let { Duration.between(it, time.now()).toMinutes().coerceAtLeast(0) }
}

@Composable
private fun errorText(e: ConnectionError): String = stringResource(
    when (e) {
        ConnectionError.INVALID_URL -> R.string.srv_err_url
        ConnectionError.NOT_HTTPS -> R.string.srv_err_https
        ConnectionError.OFFLINE -> R.string.srv_err_offline
        ConnectionError.TIMEOUT -> R.string.srv_err_timeout
        ConnectionError.SSL -> R.string.srv_err_ssl
        ConnectionError.NOT_HAYAT -> R.string.srv_err_not_hayat
        ConnectionError.SERVER_ERROR -> R.string.srv_err_server
        ConnectionError.WRONG_CREDENTIALS -> R.string.srv_err_credentials
        ConnectionError.RATE_LIMITED -> R.string.srv_err_rate
        ConnectionError.SIGNUP_CLOSED -> R.string.srv_err_signup_closed
        ConnectionError.EMAIL_TAKEN -> R.string.srv_err_email_taken
        ConnectionError.WEAK_PASSWORD -> R.string.srv_err_weak
    },
)

@Composable
private fun ResultLine(ok: Boolean, text: String) {
    val ext = HayatThemeExt.colors
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .background(if (ok) ext.success.copy(alpha = 0.12f) else MaterialTheme.colorScheme.errorContainer)
            .semantics { liveRegion = LiveRegionMode.Polite }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null, tint = if (ok) ext.success else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (ok) ext.success else MaterialTheme.colorScheme.onErrorContainer)
    }
}

/**
 * "Connect to your server": URL → Test connection (GET /api/health, shows latency or a precise
 * error) → sign in with email + password or an access key. Continue happens only after success.
 */
@Composable
fun ConnectServerScreen(firstRun: Boolean, onBack: (() -> Unit)?, onConnected: () -> Unit, vm: ServerViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    if (s.done) { androidx.compose.runtime.LaunchedEffect(Unit) { onConnected() } }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPw by rememberSaveable { mutableStateOf(false) }
    var key by rememberSaveable { mutableStateOf("") }
    val connected = s.health is HealthResult.Ok
    HayatScreen(title = stringResource(R.string.srv_connect_title), onBack = if (firstRun) null else onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Text(stringResource(R.string.srv_connect_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                HayatCard {
                    Text(stringResource(R.string.srv_step1), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = s.url, onValueChange = vm::setUrl, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.srv_url)) }, placeholder = { Text("https://api.mydomain.com") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { vm.testConnection() }),
                    )
                    Spacer(Modifier.height(12.dp))
                    HayatButton(stringResource(R.string.srv_test), vm::testConnection, Modifier.fillMaxWidth(), variant = if (connected) ButtonVariant.TONAL else ButtonVariant.PRIMARY,
                        icon = Icons.Outlined.NetworkCheck, loading = s.testing, enabled = s.url.isNotBlank())
                    s.health?.let { h ->
                        Spacer(Modifier.height(12.dp))
                        when (h) {
                            is HealthResult.Ok -> ResultLine(true, stringResource(R.string.srv_connected_ms, fmt.number(h.latencyMs.toInt())))
                            is HealthResult.Failed -> ResultLine(false, errorText(h.error))
                        }
                    }
                }
            }
            item {
                HayatCard(Modifier.then(if (connected) Modifier else Modifier)) {
                    Text(stringResource(R.string.srv_step2), style = MaterialTheme.typography.titleMedium,
                        color = if (connected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!connected) {
                        Text(stringResource(R.string.srv_step2_locked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        return@HayatCard
                    }
                    Spacer(Modifier.height(10.dp))
                    PillTabs(listOf(stringResource(R.string.srv_mode_email), stringResource(R.string.srv_mode_key)), s.mode.ordinal, { vm.setMode(AuthMode.entries[it]) })
                    Spacer(Modifier.height(12.dp))
                    if (s.mode == AuthMode.EMAIL) {
                        OutlinedTextField(email, { email = it.take(254) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.srv_email)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(password, { password = it.take(72) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.srv_password)) },
                            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { IconButton(onClick = { showPw = !showPw }) { Icon(if (showPw) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, stringResource(if (showPw) R.string.srv_hide else R.string.srv_show)) } },
                            supportingText = { Text(stringResource(R.string.srv_password_hint)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
                        Spacer(Modifier.height(10.dp))
                        HayatButton(stringResource(R.string.srv_sign_in), { vm.signIn(email, password, register = false) }, Modifier.fillMaxWidth(),
                            icon = Icons.Outlined.Login, loading = s.signingIn, enabled = email.contains('@') && password.length >= 12)
                        HayatButton(stringResource(R.string.srv_create_account), { vm.signIn(email, password, register = true) }, Modifier.fillMaxWidth(),
                            variant = ButtonVariant.TEXT, enabled = !s.signingIn && email.contains('@') && password.length >= 12)
                    } else {
                        OutlinedTextField(key, { key = it.trim().take(100) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.srv_key)) },
                            placeholder = { Text("hk_…") }, leadingIcon = { Icon(Icons.Outlined.Key, null) })
                        Spacer(Modifier.height(10.dp))
                        HayatButton(stringResource(R.string.srv_sign_in), { vm.signInWithKey(key) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Login,
                            loading = s.signingIn, enabled = key.length >= 20)
                    }
                    s.authError?.let { Spacer(Modifier.height(10.dp)); ResultLine(false, errorText(it)) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.srv_security_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (s.askConflict) AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.srv_conflict_title)) },
        text = { Text(stringResource(R.string.srv_conflict_text)) },
        confirmButton = { TextButton(onClick = { vm.resolveConflict(keepLocal = false) }) { Text(stringResource(R.string.srv_conflict_server)) } },
        dismissButton = { TextButton(onClick = { vm.resolveConflict(keepLocal = true) }) { Text(stringResource(R.string.srv_conflict_local)) } },
    )
}

/** Settings → Server: status, last sync, sync now, re-test, change server, log out. */
@Composable
fun ServerSettingsScreen(onBack: () -> Unit, onChangeServer: () -> Unit, vm: ServerViewModel = hiltViewModel()) {
    val session by vm.session.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val fmt = LocalFormatter.current
    val ext = HayatThemeExt.colors
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    HayatScreen(title = stringResource(R.string.srv_title), onBack = onBack) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val s = session
            if (s == null) {
                item {
                    HayatCard {
                        InlineEmpty(Icons.Outlined.CloudOff, stringResource(R.string.srv_not_connected), stringResource(R.string.srv_not_connected_text))
                        Spacer(Modifier.height(12.dp))
                        HayatButton(stringResource(R.string.srv_connect), { vm.prepareChange(); onChangeServer() }, Modifier.fillMaxWidth(), icon = Icons.Outlined.Cloud)
                    }
                }
                return@LazyColumn
            }
            item {
                val (color, label) = when (status) {
                    ServerStatus.SYNCED -> ext.success to stringResource(R.string.srv_status_synced)
                    ServerStatus.SYNCING -> ext.gold to stringResource(R.string.srv_status_syncing)
                    ServerStatus.PENDING -> ext.gold to stringResource(R.string.srv_status_pending)
                    ServerStatus.OFFLINE -> MaterialTheme.colorScheme.error to stringResource(R.string.srv_status_offline)
                    ServerStatus.ERROR -> MaterialTheme.colorScheme.error to stringResource(R.string.srv_status_error)
                    ServerStatus.NOT_CONNECTED -> MaterialTheme.colorScheme.outline to stringResource(R.string.srv_not_connected)
                }
                HayatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            StatusDot(color, label, size = 14.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            val mins = vm.minutesSince(s)
                            Text(if (mins == null) stringResource(R.string.srv_never_synced) else stringResource(R.string.srv_last_synced, fmt.ago(mins)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    KeyValueRow(stringResource(R.string.srv_url), s.baseUrl.removePrefix("https://"))
                    KeyValueRow(stringResource(R.string.srv_account), s.account)
                    Spacer(Modifier.height(12.dp))
                    HayatButton(stringResource(R.string.srv_sync_now), vm::syncNow, Modifier.fillMaxWidth(), icon = Icons.Outlined.Sync, loading = status == ServerStatus.SYNCING)
                }
            }
            item {
                HayatCard {
                    Text(stringResource(R.string.srv_connection), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    HayatButton(stringResource(R.string.srv_retest), { vm.setUrl(s.baseUrl); vm.testConnection() }, Modifier.fillMaxWidth(),
                        variant = ButtonVariant.TONAL, icon = Icons.Outlined.NetworkCheck, loading = ui.testing)
                    ui.health?.let { h ->
                        Spacer(Modifier.height(10.dp))
                        when (h) {
                            is HealthResult.Ok -> ResultLine(true, stringResource(R.string.srv_connected_ms, fmt.number(h.latencyMs.toInt())))
                            is HealthResult.Failed -> ResultLine(false, errorText(h.error))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    HayatButton(stringResource(R.string.srv_change), { vm.prepareChange(); onChangeServer() }, Modifier.fillMaxWidth(), variant = ButtonVariant.TEXT, icon = Icons.Outlined.SwapHoriz)
                    HayatButton(stringResource(R.string.srv_logout), { confirmLogout = true }, Modifier.fillMaxWidth(), variant = ButtonVariant.TEXT, icon = Icons.Outlined.Logout)
                }
            }
            item { Text(stringResource(R.string.srv_sync_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text(stringResource(R.string.srv_logout_title)) },
        text = { Text(stringResource(R.string.srv_logout_text)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; vm.logout() }) { Text(stringResource(R.string.srv_logout)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(com.hayat.core.ui.R.string.action_cancel)) } },
    )
}

