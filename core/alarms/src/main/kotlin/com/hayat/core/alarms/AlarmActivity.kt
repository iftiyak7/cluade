package com.hayat.core.alarms

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hayat.core.common.ApplicationScope
import com.hayat.core.designsystem.theme.HayatTheme
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.model.ReminderAction
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

/** Full-screen UI for alarm-style reminders only. Every button updates persistent state. */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
        if (occurrenceId == null) { finish(); return }
        val now = LocalTime.now().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        setContent {
            HayatTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Alarm, null, Modifier.height(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text(now, style = MaterialTheme.typography.displayLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(48.dp))
                        Button(onClick = { act(occurrenceId, ReminderAction.DONE) }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.alarm_screen_done)) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { act(occurrenceId, ReminderAction.SNOOZE) }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.alarm_screen_snooze)) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { act(occurrenceId, ReminderAction.DISMISS) }, Modifier.fillMaxWidth().height(56.dp)) { Text(stringResource(R.string.alarm_screen_dismiss)) }
                    }
                }
            }
        }
    }

    private fun act(occurrenceId: String, action: ReminderAction) {
        scope.launch { scheduler.handleAction(occurrenceId, action) }
        finish()
    }

    companion object {
        const val EXTRA_OCCURRENCE = "occurrence_id"
        const val EXTRA_REQUEST_CODE = "request_code"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
    }
}
