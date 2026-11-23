package com.hayat.core.domain.reminder

import com.hayat.core.common.Limits
import com.hayat.core.common.ValidationError
import com.hayat.core.common.ValidationReason
import com.hayat.core.common.validate
import com.hayat.core.model.DateRule
import com.hayat.core.model.Reminder
import com.hayat.core.model.TimeRule

object ReminderValidator {
    fun validate(reminder: Reminder): List<ValidationError> = validate {
        text(reminder.title, "title", maxLength = Limits.TITLE_MAX)
        text(reminder.description, "description", required = false, maxLength = Limits.DESCRIPTION_MAX)
        require(reminder.schedules.isNotEmpty(), "schedule", ValidationReason.REQUIRED)
        range(reminder.snoozeMinutes, "snooze", 1.0, 240.0)
        reminder.schedules.forEach { rs ->
            val s = rs.schedule
            if (s.endDate != null) require(!s.endDate!!.isBefore(s.startDate), "endDate", ValidationReason.END_BEFORE_START)
            when (val t = s.timeRule) {
                is TimeRule.At -> require(t.times.isNotEmpty(), "time", ValidationReason.REQUIRED)
                is TimeRule.Every -> range(t.intervalMinutes, "interval", 5.0, 1440.0)
            }
            when (val d = s.dateRule) {
                is DateRule.Weekly -> require(!d.days.isEmpty, "weekdays", ValidationReason.EMPTY_SELECTION)
                is DateRule.Daily -> range(d.everyNDays, "everyN", 1.0, 365.0)
                is DateRule.Monthly -> range(d.dayOfMonth, "dayOfMonth", 1.0, 31.0)
                is DateRule.Yearly -> range(d.dayOfMonth, "dayOfMonth", 1.0, 31.0)
                DateRule.Once -> Unit
            }
        }
    }
}
