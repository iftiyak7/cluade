package com.hayat.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hayat.core.common.ValidationReason
import com.hayat.core.designsystem.component.ChoiceChips
import com.hayat.core.designsystem.component.DayToggleRow
import com.hayat.core.model.DateRule
import com.hayat.core.model.MonthOverflow
import com.hayat.core.model.WeekdaySet
import java.time.DayOfWeek
import java.time.LocalDate

/** Text field that shows a localized validation message. */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: ValidationReason? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    maxLength: Int? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(if (maxLength != null) v.take(maxLength) else v) },
        label = { Text(label) },
        isError = error != null,
        supportingText = (error?.let { stringResource(it.labelRes()) } ?: supporting)?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (keyboardType == KeyboardType.Text) KeyboardCapitalization.Sentences else KeyboardCapitalization.None,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Numeric field backed by text so partial input ("1.") is allowed while typing. */
@Composable
fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, decimal: Boolean = false, error: ValidationReason? = null, supporting: String? = null) {
    FormTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.matches(if (decimal) Regex("\\d{0,7}([.,]\\d{0,2})?") else Regex("\\d{0,7}"))) onValueChange(v) },
        label = label, modifier = modifier, error = error, keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number, supporting = supporting,
    )
}

fun orderedWeek(firstDay: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { firstDay.plus(it) }

@Composable
fun WeekdayPicker(selected: WeekdaySet, onChange: (WeekdaySet) -> Unit, firstDay: DayOfWeek, modifier: Modifier = Modifier) {
    val fmt = LocalFormatter.current
    DayToggleRow(
        days = orderedWeek(firstDay),
        isSelected = { it in selected },
        label = { fmt.weekdayNarrow(it) },
        fullLabel = { fmt.weekdayFull(it) },
        onToggle = { onChange(selected.toggle(it)) },
        modifier = modifier,
        selectedText = stringResource(R.string.state_selected),
        notSelectedText = stringResource(R.string.state_not_selected),
    )
}

enum class RepeatKind { ONCE, DAILY, EVERY_N_DAYS, WEEKLY, MONTHLY, YEARLY }

fun DateRule.kind(): RepeatKind = when (this) {
    DateRule.Once -> RepeatKind.ONCE
    is DateRule.Daily -> if (everyNDays == 1) RepeatKind.DAILY else RepeatKind.EVERY_N_DAYS
    is DateRule.Weekly -> RepeatKind.WEEKLY
    is DateRule.Monthly -> RepeatKind.MONTHLY
    is DateRule.Yearly -> RepeatKind.YEARLY
}

@Composable
fun RepeatKind.label(): String = stringResource(
    when (this) {
        RepeatKind.ONCE -> R.string.repeat_once; RepeatKind.DAILY -> R.string.repeat_daily
        RepeatKind.EVERY_N_DAYS -> R.string.repeat_every_n_days; RepeatKind.WEEKLY -> R.string.repeat_weekly
        RepeatKind.MONTHLY -> R.string.repeat_monthly; RepeatKind.YEARLY -> R.string.repeat_yearly
    },
)

/**
 * Edits a [DateRule] relative to [anchor] (the start/due date). Used by tasks, routines and
 * reminders so recurrence behaves identically everywhere.
 */
@Composable
fun DateRuleEditor(
    rule: DateRule,
    anchor: LocalDate,
    onChange: (DateRule) -> Unit,
    firstDay: DayOfWeek,
    modifier: Modifier = Modifier,
    allowOnce: Boolean = true,
) {
    val kinds = RepeatKind.entries.filter { allowOnce || it != RepeatKind.ONCE }
    val labels = kinds.associateWith { it.label() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceChips(options = kinds, selected = rule.kind(), label = { labels.getValue(it) }, onSelect = { k ->
            onChange(
                when (k) {
                    RepeatKind.ONCE -> DateRule.Once
                    RepeatKind.DAILY -> DateRule.Daily(1)
                    RepeatKind.EVERY_N_DAYS -> DateRule.Daily(2)
                    RepeatKind.WEEKLY -> DateRule.Weekly((rule as? DateRule.Weekly)?.days ?: WeekdaySet.of(anchor.dayOfWeek))
                    RepeatKind.MONTHLY -> DateRule.Monthly(anchor.dayOfMonth)
                    RepeatKind.YEARLY -> DateRule.Yearly(anchor.monthValue, anchor.dayOfMonth)
                },
            )
        })
        when (rule) {
            is DateRule.Daily -> if (rule.everyNDays != 1) {
                NumberField(rule.everyNDays.toString(), { v -> v.toIntOrNull()?.takeIf { it in 2..365 }?.let { onChange(DateRule.Daily(it)) } },
                    stringResource(R.string.label_every_n_days, rule.everyNDays))
            }
            is DateRule.Weekly -> {
                WeekdayPicker(rule.days, { onChange(rule.copy(days = it)) }, firstDay)
                if (rule.days.isEmpty) Text(stringResource(R.string.error_empty_selection), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            is DateRule.Monthly -> {
                NumberField(rule.dayOfMonth.toString(), { v -> v.toIntOrNull()?.takeIf { it in 1..31 }?.let { onChange(rule.copy(dayOfMonth = it)) } }, stringResource(R.string.label_day_of_month))
                if (rule.dayOfMonth > 28) OverflowChoice(rule.overflow) { onChange(rule.copy(overflow = it)) }
            }
            is DateRule.Yearly -> if (rule.month == 2 && rule.dayOfMonth == 29) OverflowChoice(rule.overflow) { onChange(rule.copy(overflow = it)) }
            DateRule.Once -> Unit
        }
    }
}

@Composable
private fun OverflowChoice(value: MonthOverflow, onChange: (MonthOverflow) -> Unit) {
    Column {
        MonthOverflow.entries.forEach { o ->
            Row(
                Modifier.fillMaxWidth().selectable(selected = o == value, role = Role.RadioButton) { onChange(o) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = o == value, onClick = null)
                Text(stringResource(if (o == MonthOverflow.CLAMP) R.string.month_overflow_clamp else R.string.month_overflow_skip), Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Human-readable summary of a rule, e.g. "Every day" / "Sun, Mon, Tue". */
@Composable
fun DateRule.summary(): String {
    val fmt = LocalFormatter.current
    return when (this) {
        DateRule.Once -> stringResource(R.string.repeat_once)
        is DateRule.Daily -> if (everyNDays == 1) stringResource(R.string.repeat_daily) else stringResource(R.string.label_every_n_days, everyNDays)
        is DateRule.Weekly -> if (days == WeekdaySet.ALL) stringResource(R.string.repeat_daily) else days.days.joinToString(", ") { fmt.weekdayShort(it) }
        is DateRule.Monthly -> stringResource(R.string.repeat_monthly) + " · " + fmt.number(dayOfMonth)
        is DateRule.Yearly -> stringResource(R.string.repeat_yearly)
    }
}
