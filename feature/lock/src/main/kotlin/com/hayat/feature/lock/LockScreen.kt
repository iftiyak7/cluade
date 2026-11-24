package com.hayat.feature.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hayat.core.domain.security.AppLockManager
import com.hayat.core.domain.security.PinCheck
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LocalFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class LockViewModel @Inject constructor(private val lock: AppLockManager) : HayatViewModel() {
    private val _result = MutableStateFlow<PinCheck?>(null)
    val result = _result.asStateFlow()

    fun verify(pin: String, onSuccess: () -> Unit) = safeLaunch {
        val chars = pin.toCharArray()
        val r = lock.verify(chars)
        chars.fill('0')
        _result.value = r
        if (r == PinCheck.Correct) onSuccess()
    }
}

/** Full-screen overlay shown while the session is locked. Content behind it is not composed visibly. */
@Composable
fun LockScreen(biometricEnabled: Boolean, onUnlocked: () -> Unit, vm: LockViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val hostActivity = androidx.activity.compose.LocalActivity.current as? FragmentActivity
    var pin by remember { mutableStateOf("") }
    val result by vm.result.collectAsStateWithLifecycle()
    val canBiometric = remember {
        biometricEnabled && BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val bioTitle = stringResource(R.string.lock_biometric_title)
    val bioNegative = stringResource(com.hayat.core.ui.R.string.action_cancel)
    fun showBiometric() {
        val activity = hostActivity ?: return
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(context), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder().setTitle(bioTitle).setNegativeButtonText(bioNegative)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build(),
        )
    }
    LaunchedEffect(Unit) { if (canBiometric) showBiometric() }

    val fmt = LocalFormatter.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.lock_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.lock_enter_pin), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics { contentDescription = "${pin.length}/8" }) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
                }
            }
            val message = when (val r = result) {
                is PinCheck.Wrong -> if (r.remainingAttempts == 0 && pin.isEmpty()) stringResource(R.string.lock_unavailable) else stringResource(R.string.lock_wrong, r.remainingAttempts)
                is PinCheck.LockedOut -> stringResource(R.string.lock_locked_out, fmt.time(r.until.atZone(ZoneId.systemDefault()).toLocalTime()))
                else -> null
            }
            Spacer(Modifier.height(12.dp))
            Text(message.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(12.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    row.forEach { key ->
                        when (key) {
                            "del" -> IconButton(onClick = { pin = pin.dropLast(1) }, Modifier.size(72.dp)) { Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.lock_backspace)) }
                            "bio" -> if (canBiometric) IconButton(onClick = ::showBiometric, Modifier.size(72.dp)) { Icon(Icons.Outlined.Fingerprint, stringResource(R.string.lock_use_biometric)) }
                                else Spacer(Modifier.size(72.dp))
                            else -> {
                                val label = fmt.digits(key)
                                val cd = stringResource(R.string.lock_digit, label)
                                FilledIconButton(onClick = { if (pin.length < 8) pin += key }, Modifier.size(72.dp).semantics { contentDescription = cd }) {
                                    Text(label, style = MaterialTheme.typography.headlineSmall)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { vm.verify(pin) { onUnlocked() }; pin = "" }, enabled = pin.length >= 4) {
                Icon(Icons.Outlined.LockOpen, null); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.lock_submit))
            }
        }
    }
}
