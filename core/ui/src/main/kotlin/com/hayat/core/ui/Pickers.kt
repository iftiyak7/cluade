package com.hayat.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Read-only field that opens a Material date picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    date: LocalDate?,
    onDateChange: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    PickerField(label, date?.let(fmt::date).orEmpty(), { open = true }, Icons.Outlined.CalendarMonth, modifier, isError, supportingText, stringResource(R.string.action_select_date))
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (date ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                if (allowClear) TextButton(onClick = { onDateChange(null); open = false }) { Text(stringResource(R.string.action_clear)) }
                else TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

/** Read-only field that opens a Material time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    time: LocalTime?,
    onTimeChange: (LocalTime?) -> Unit,
    modifier: Modifier = Modifier,
    allowClear: Boolean = false,
    is24Hour: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val fmt = LocalFormatter.current
    PickerField(label, time?.let(fmt::time).orEmpty(), { open = true }, Icons.Outlined.Schedule, modifier, isError, supportingText, stringResource(R.string.action_select_time))
    if (open) {
        val initial = time ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = { TextButton(onClick = { onTimeChange(LocalTime.of(state.hour, state.minute)); open = false }) { Text(stringResource(R.string.action_ok)) } },
            dismissButton = {
                if (allowClear) TextButton(onClick = { onTimeChange(null); open = false }) { Text(stringResource(R.string.action_clear)) }
                else TextButton(onClick = { open = false }) { Text(stringResource(R.string.action_cancel)) }
            },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
private fun PickerField(
    label: String, value: String, onOpen: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier, isError: Boolean, supportingText: String?, actionLabel: String,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { Icon(icon, null) }, isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparent overlay makes the whole field a single accessible button.
        Box(Modifier.matchParentSize().clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onOpen).semantics { onClick(actionLabel) { onOpen(); true } })
    }
}
