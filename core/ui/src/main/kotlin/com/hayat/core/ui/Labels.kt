package com.hayat.core.ui

import androidx.annotation.StringRes
import com.hayat.core.common.AppError
import com.hayat.core.common.ValidationReason
import com.hayat.core.model.*

@StringRes fun Priority.labelRes() = when (this) {
    Priority.LOW -> R.string.priority_low; Priority.MEDIUM -> R.string.priority_medium
    Priority.HIGH -> R.string.priority_high; Priority.URGENT -> R.string.priority_urgent
}

@StringRes fun ReminderCategory.labelRes() = when (this) {
    ReminderCategory.WATER -> R.string.cat_water; ReminderCategory.MEDICINE -> R.string.cat_medicine
    ReminderCategory.MEAL -> R.string.cat_meal; ReminderCategory.ZIKR -> R.string.cat_zikr
    ReminderCategory.PRAYER -> R.string.cat_prayer; ReminderCategory.QURAN -> R.string.cat_quran
    ReminderCategory.WORK -> R.string.cat_work; ReminderCategory.STUDY -> R.string.cat_study
    ReminderCategory.EXERCISE -> R.string.cat_exercise; ReminderCategory.SLEEP -> R.string.cat_sleep
    ReminderCategory.BILL -> R.string.cat_bill; ReminderCategory.APPOINTMENT -> R.string.cat_appointment
    ReminderCategory.EVENT -> R.string.cat_event; ReminderCategory.TASK -> R.string.cat_task
    ReminderCategory.ROUTINE -> R.string.cat_routine; ReminderCategory.HABIT -> R.string.cat_habit
    ReminderCategory.CUSTOM -> R.string.cat_custom
}

@StringRes fun taskCategoryRes(systemKey: String?): Int? = when (systemKey) {
    "personal" -> R.string.taskcat_personal; "work" -> R.string.taskcat_work; "study" -> R.string.taskcat_study
    "health" -> R.string.taskcat_health; "home" -> R.string.taskcat_home; "finance" -> R.string.taskcat_finance
    else -> null
}

@StringRes fun ActivityStatus.labelRes() = when (this) {
    ActivityStatus.COMPLETED -> R.string.status_completed; ActivityStatus.SKIPPED -> R.string.status_skipped
    ActivityStatus.POSTPONED -> R.string.status_postponed; ActivityStatus.MISSED -> R.string.status_missed
}

@StringRes fun DoseStatus.labelRes() = when (this) {
    DoseStatus.TAKEN -> R.string.status_taken; DoseStatus.SKIPPED -> R.string.status_skipped
    DoseStatus.MISSED -> R.string.status_missed; DoseStatus.POSTPONED -> R.string.status_postponed
}

@StringRes fun OccurrenceStatus.labelRes() = when (this) {
    OccurrenceStatus.SCHEDULED -> R.string.status_scheduled; OccurrenceStatus.SNOOZED -> R.string.status_snoozed
    OccurrenceStatus.DELIVERED -> R.string.status_delivered; OccurrenceStatus.COMPLETED -> R.string.status_completed
    OccurrenceStatus.SKIPPED -> R.string.status_skipped; OccurrenceStatus.MISSED -> R.string.status_missed
    OccurrenceStatus.CANCELLED -> R.string.status_cancelled
}

@StringRes fun Prayer.labelRes() = when (this) {
    Prayer.FAJR -> R.string.prayer_fajr; Prayer.DHUHR -> R.string.prayer_dhuhr; Prayer.ASR -> R.string.prayer_asr
    Prayer.MAGHRIB -> R.string.prayer_maghrib; Prayer.ISHA -> R.string.prayer_isha
}

@StringRes fun PrayerStatus.labelRes() = when (this) {
    PrayerStatus.PRAYED -> R.string.prayer_status_prayed; PrayerStatus.PRAYED_LATE -> R.string.prayer_status_late
    PrayerStatus.QADA -> R.string.prayer_status_qada; PrayerStatus.MISSED -> R.string.prayer_status_missed
    PrayerStatus.EXCUSED -> R.string.prayer_status_excused
}

@StringRes fun MealType.labelRes() = when (this) {
    MealType.BREAKFAST -> R.string.meal_breakfast; MealType.LUNCH -> R.string.meal_lunch; MealType.DINNER -> R.string.meal_dinner
    MealType.SNACK -> R.string.meal_snack; MealType.CUSTOM -> R.string.meal_custom
}

@StringRes fun MedicineForm.labelRes() = when (this) {
    MedicineForm.TABLET -> R.string.form_tablet; MedicineForm.CAPSULE -> R.string.form_capsule; MedicineForm.SYRUP -> R.string.form_syrup
    MedicineForm.INJECTION -> R.string.form_injection; MedicineForm.DROPS -> R.string.form_drops; MedicineForm.INHALER -> R.string.form_inhaler
    MedicineForm.CREAM -> R.string.form_cream; MedicineForm.POWDER -> R.string.form_powder; MedicineForm.OTHER -> R.string.form_other
}

@StringRes fun MealInstruction.labelRes() = when (this) {
    MealInstruction.NONE -> R.string.mealinst_none; MealInstruction.BEFORE_MEAL -> R.string.mealinst_before
    MealInstruction.WITH_MEAL -> R.string.mealinst_with; MealInstruction.AFTER_MEAL -> R.string.mealinst_after
    MealInstruction.EMPTY_STOMACH -> R.string.mealinst_empty
}

@StringRes fun RoutineKind.labelRes() = when (this) {
    RoutineKind.GENERAL -> R.string.kind_general; RoutineKind.WORKDAY -> R.string.kind_workday; RoutineKind.FRIDAY -> R.string.kind_friday
    RoutineKind.HOLIDAY -> R.string.kind_holiday; RoutineKind.RAMADAN -> R.string.kind_ramadan; RoutineKind.STUDY -> R.string.kind_study
    RoutineKind.FITNESS -> R.string.kind_fitness
}

@StringRes fun FinanceKind.labelRes() = when (this) {
    FinanceKind.BILL -> R.string.fin_bill; FinanceKind.RENT -> R.string.fin_rent; FinanceKind.INSTALLMENT -> R.string.fin_installment
    FinanceKind.SUBSCRIPTION -> R.string.fin_subscription; FinanceKind.RECEIVABLE -> R.string.fin_receivable
    FinanceKind.PAYABLE -> R.string.fin_payable; FinanceKind.OTHER -> R.string.fin_other
}

@StringRes fun FinanceRepeat.labelRes() = when (this) {
    FinanceRepeat.NONE -> R.string.fin_repeat_none; FinanceRepeat.WEEKLY -> R.string.fin_repeat_weekly
    FinanceRepeat.MONTHLY -> R.string.fin_repeat_monthly; FinanceRepeat.YEARLY -> R.string.fin_repeat_yearly
}

@StringRes fun EventKind.labelRes() = when (this) {
    EventKind.BIRTHDAY -> R.string.event_birthday; EventKind.ANNIVERSARY -> R.string.event_anniversary
    EventKind.APPOINTMENT -> R.string.event_appointment; EventKind.TRAVEL -> R.string.event_travel
    EventKind.DOCUMENT_EXPIRY -> R.string.event_document; EventKind.LICENSE_RENEWAL -> R.string.event_license
    EventKind.CUSTOM -> R.string.event_custom
}

@StringRes fun FastingType.labelRes() = when (this) {
    FastingType.RAMADAN -> R.string.fast_ramadan; FastingType.SUNNAH -> R.string.fast_sunnah; FastingType.NAFL -> R.string.fast_nafl
    FastingType.QADA -> R.string.fast_qada; FastingType.OTHER -> R.string.fast_other
}

@StringRes fun FastingStatus.labelRes() = when (this) {
    FastingStatus.FASTED -> R.string.fast_status_fasted; FastingStatus.NOT_FASTED -> R.string.fast_status_not
    FastingStatus.PLANNED -> R.string.fast_status_planned
}

@StringRes fun ValidationReason.labelRes() = when (this) {
    ValidationReason.REQUIRED -> R.string.error_required; ValidationReason.TOO_LONG -> R.string.error_too_long
    ValidationReason.OUT_OF_RANGE -> R.string.error_out_of_range; ValidationReason.INVALID_FORMAT -> R.string.error_invalid_format
    ValidationReason.END_BEFORE_START -> R.string.error_end_before_start; ValidationReason.DUPLICATE -> R.string.error_duplicate
    ValidationReason.EMPTY_SELECTION -> R.string.error_empty_selection
}

@StringRes fun AppError.messageRes(): Int = when (this) {
    is AppError.Validation -> errors.firstOrNull()?.reason?.labelRes() ?: R.string.error_generic
    is AppError.NotFound -> R.string.error_not_found
    is AppError.Storage -> R.string.error_storage
    else -> R.string.error_generic
}
