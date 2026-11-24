package com.hayat.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hayat.core.common.AppError
import com.hayat.core.common.AppResult
import com.hayat.core.common.BackupFailure
import com.hayat.core.common.TimeProvider
import com.hayat.core.designsystem.component.HayatCard
import com.hayat.core.designsystem.component.InfoBanner
import com.hayat.core.designsystem.component.KeyValueRow
import com.hayat.core.designsystem.component.SettingsRow
import com.hayat.core.designsystem.component.StatusTone
import com.hayat.core.designsystem.component.SwitchRow
import com.hayat.core.domain.security.BackupPreview
import com.hayat.core.domain.security.BackupService
import com.hayat.core.navigation.BackupRoute
import com.hayat.core.ui.HayatScreen
import com.hayat.core.ui.HayatViewModel
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.MessageEffect
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import com.hayat.core.ui.R as UiR

/** Abstracts SAF stream access so the ViewModel stays testable. */
interface StreamOpener {
    fun openInput(uri: Uri): InputStream?
    fun openOutput(uri: Uri): OutputStream?
}

sealed interface RestoreStep {
    data object Idle : RestoreStep
    data class NeedPassphrase(val uri: Uri, val wrong: Boolean = false) : RestoreStep
    data class Preview(val uri: Uri, val passphrase: CharArray?, val preview: BackupPreview) : RestoreStep
}

@HiltViewModel
class BackupViewModel @Inject constructor(private val backup: BackupService, private val time: TimeProvider) : HayatViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _restore = MutableStateFlow<RestoreStep>(RestoreStep.Idle)
    val restore = _restore.asStateFlow()

    fun suggestedName(): String = "hayat-backup-" + time.zonedNow().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".hayat"

    fun export(uri: Uri, passphrase: CharArray?, opener: StreamOpener) = work {
        val out = opener.openOutput(uri) ?: return@work message(R.string.bk_err_io)
        val r = out.use { backup.export(it, passphrase) }
        passphrase?.fill('0')
        report(r) { message(R.string.bk_exported, it.totalRecords) }
    }

    fun pickedForRestore(uri: Uri, opener: StreamOpener) = work {
        val encrypted = opener.openInput(uri)?.use { backup.isEncrypted(it) } ?: return@work message(R.string.bk_err_io)
        if (encrypted) _restore.value = RestoreStep.NeedPassphrase(uri) else inspect(uri, null, opener)
    }

    fun submitPassphrase(uri: Uri, passphrase: CharArray, opener: StreamOpener) = work { inspect(uri, passphrase, opener) }

    private suspend fun inspect(uri: Uri, passphrase: CharArray?, opener: StreamOpener) {
        val input = opener.openInput(uri) ?: return message(R.string.bk_err_io)
        val r = input.use { backup.inspect(it, passphrase) }
        if (r is AppResult.Failure && (r.error as? AppError.Backup)?.reason == BackupFailure.WRONG_PASSPHRASE_OR_CORRUPT && passphrase != null) {
            _restore.value = RestoreStep.NeedPassphrase(uri, wrong = true); return
        }
        report(r) { _restore.value = RestoreStep.Preview(uri, passphrase, it) }
    }

    fun confirmRestore(step: RestoreStep.Preview, opener: StreamOpener) = work {
        _restore.value = RestoreStep.Idle
        val input = opener.openInput(step.uri) ?: return@work message(R.string.bk_err_io)
        val r = input.use { backup.restore(it, step.passphrase) }
        step.passphrase?.fill('0')
        report(r) { message(R.string.bk_restored, it.totalRecords) }
    }

    fun cancelRestore() { (_restore.value as? RestoreStep.Preview)?.passphrase?.fill('0'); _restore.value = RestoreStep.Idle }

    private fun <T> report(r: AppResult<T>, onSuccess: (T) -> Unit) {
        when (r) {
            is AppResult.Success -> onSuccess(r.value)
            is AppResult.Failure -> message(when ((r.error as? AppError.Backup)?.reason) {
                BackupFailure.NOT_A_HAYAT_BACKUP -> R.string.bk_err_not_backup
                BackupFailure.UNSUPPORTED_FORMAT_VERSION, BackupFailure.NEWER_SCHEMA -> R.string.bk_err_version
                BackupFailure.WRONG_PASSPHRASE_OR_CORRUPT, BackupFailure.PASSPHRASE_REQUIRED -> R.string.bk_err_passphrase
                BackupFailure.CHECKSUM_MISMATCH, BackupFailure.CORRUPT_CONTENT -> R.string.bk_err_corrupt
                BackupFailure.TOO_LARGE -> R.string.bk_err_large
                BackupFailure.IO_ERROR -> R.string.bk_err_io
                null -> UiR.string.error_generic
            })
        }
    }

    private fun work(block: suspend () -> Unit) = safeLaunch { _busy.value = true; try { block() } finally { _busy.value = false } }
}

fun NavGraphBuilder.backupGraph(nav: NavController) { composable<BackupRoute> { BackupScreen(nav::popBackStack) } }

@Composable
private fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    val restore by vm.restore.collectAsStateWithLifecycle()
    val host = remember { SnackbarHostState() }
    MessageEffect(vm.messages, host)
    val context = LocalContext.current
    val opener = remember {
        object : StreamOpener {
            override fun openInput(uri: Uri) = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            override fun openOutput(uri: Uri) = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
        }
    }
    var encrypt by rememberSaveable { mutableStateOf(true) }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf<Int?>(null) }
    var pendingPass by remember { mutableStateOf<CharArray?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.export(uri, pendingPass, opener)
        pendingPass = null; pass = ""; pass2 = ""
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.pickedForRestore(uri, opener) }

    HayatScreen(title = stringResource(R.string.bk_title), onBack = onBack, snackbarHostState = host) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoBanner(stringResource(R.string.bk_explain), tone = StatusTone.WARNING)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            HayatCard {
                SettingsRow(stringResource(R.string.bk_export), subtitle = stringResource(R.string.bk_export_sub), icon = Icons.Outlined.Upload)
                SwitchRow(stringResource(R.string.bk_encrypt), encrypt, { encrypt = it }, subtitle = stringResource(R.string.bk_encrypt_sub), icon = Icons.Outlined.Lock)
                if (encrypt) {
                    PassField(pass, { pass = it; passError = null }, stringResource(R.string.bk_passphrase), passError != null)
                    PassField(pass2, { pass2 = it; passError = null }, stringResource(R.string.bk_passphrase_confirm), passError != null)
                    passError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
                    Text(stringResource(R.string.bk_passphrase_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(enabled = !busy, onClick = {
                    when {
                        encrypt && pass.length < 8 -> passError = R.string.bk_passphrase_short
                        encrypt && pass != pass2 -> passError = R.string.bk_passphrase_mismatch
                        else -> { pendingPass = if (encrypt) pass.toCharArray() else null; exportLauncher.launch(vm.suggestedName()) }
                    }
                }) { Text(stringResource(R.string.bk_export)) }
            }
            HayatCard {
                SettingsRow(stringResource(R.string.bk_import), subtitle = stringResource(R.string.bk_import_sub), icon = Icons.Outlined.Download)
                TextButton(enabled = !busy, onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.bk_import)) }
            }
        }
    }

    when (val step = restore) {
        is RestoreStep.NeedPassphrase -> {
            var p by remember(step) { mutableStateOf("") }
            AlertDialog(onDismissRequest = vm::cancelRestore, title = { Text(stringResource(R.string.bk_passphrase_enter)) },
                text = {
                    Column {
                        PassField(p, { p = it }, stringResource(R.string.bk_passphrase), step.wrong)
                        if (step.wrong) Text(stringResource(R.string.bk_err_passphrase), color = MaterialTheme.colorScheme.error)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.submitPassphrase(step.uri, p.toCharArray(), opener); p = "" }) { Text(stringResource(UiR.string.action_continue)) } },
                dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(UiR.string.action_cancel)) } })
        }
        is RestoreStep.Preview -> {
            val fmt = LocalFormatter.current
            AlertDialog(onDismissRequest = vm::cancelRestore, title = { Text(stringResource(R.string.bk_preview_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(stringResource(R.string.bk_preview_created, fmt.dateTime(LocalDateTime.ofInstant(step.preview.createdAt, ZoneId.systemDefault()))))
                        Text(fmt.digits(stringResource(R.string.bk_preview_records, step.preview.totalRecords)))
                        if (step.preview.encrypted) Text(stringResource(R.string.bk_preview_encrypted))
                        Text(stringResource(R.string.bk_counts), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        step.preview.counts.filter { it.value > 0 }.forEach { (table, n) -> KeyValueRow(table.replace('_', ' '), fmt.number(n)) }
                        InfoBanner(stringResource(R.string.bk_preview_warning), Modifier.padding(top = 8.dp), tone = StatusTone.ERROR)
                    }
                },
                confirmButton = { TextButton(onClick = { vm.confirmRestore(step, opener) }) { Text(stringResource(R.string.bk_restore), color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = vm::cancelRestore) { Text(stringResource(UiR.string.action_cancel)) } })
        }
        RestoreStep.Idle -> Unit
    }
}

@Composable
private fun PassField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean) {
    OutlinedTextField(value, { onChange(it.take(128)) }, Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, isError = isError,
        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
