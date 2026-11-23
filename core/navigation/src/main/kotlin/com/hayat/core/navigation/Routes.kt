package com.hayat.core.navigation

import kotlinx.serialization.Serializable

/**
 * Every destination in the app. Living in one module lets features navigate to each other
 * without depending on each other. Arguments are primitives only (ids, ISO dates).
 */
// ---- top level (bottom bar)
@Serializable data object HomeRoute
@Serializable data class CalendarRoute(val date: String? = null)
@Serializable data object ProgressRoute
@Serializable data object SettingsRoute

// ---- flows
@Serializable data object OnboardingRoute
@Serializable data object LockRoute
@Serializable data object AllFeaturesRoute
@Serializable data object SearchRoute

// ---- organisation
@Serializable data object TasksRoute
@Serializable data class TaskEditRoute(val id: String? = null, val date: String? = null)
@Serializable data class TaskDetailRoute(val id: String)
@Serializable data object RoutinesRoute
@Serializable data class RoutineEditRoute(val id: String? = null)
@Serializable data class RoutineDetailRoute(val id: String)
@Serializable data object TemplatesRoute
@Serializable data object RemindersRoute
@Serializable data class ReminderEditRoute(val id: String? = null)
@Serializable data class ReminderRescheduleRoute(val occurrenceId: String)
@Serializable data object EventsRoute
@Serializable data class EventEditRoute(val id: String? = null, val date: String? = null)

// ---- health
@Serializable data object WaterRoute
@Serializable data object MedicineRoute
@Serializable data class MedicineEditRoute(val id: String? = null)
@Serializable data object DietRoute
@Serializable data class MealPlanEditRoute(val id: String? = null)
@Serializable data object SleepRoute
@Serializable data object FitnessRoute
@Serializable data class WorkoutPlanEditRoute(val id: String? = null)

// ---- ibadah
@Serializable data object IbadahRoute
@Serializable data class ZikrCounterRoute(val id: String)
@Serializable data class ZikrEditRoute(val id: String? = null)
@Serializable data object PrayerSettingsRoute

// ---- productivity
@Serializable data object HabitsRoute
@Serializable data class HabitEditRoute(val id: String? = null)
@Serializable data object StudyRoute
@Serializable data object FocusRoute
@Serializable data object FinanceRoute
@Serializable data class FinanceEditRoute(val id: String? = null)
@Serializable data object NotesRoute
@Serializable data class NoteEditRoute(val id: String? = null, val checklist: Boolean = false)
@Serializable data class JournalEditRoute(val id: String? = null)

// ---- settings
@Serializable data object BackupRoute
@Serializable data object AppearanceSettingsRoute
@Serializable data object NotificationSettingsRoute
@Serializable data object PrivacySettingsRoute
@Serializable data object DashboardSettingsRoute
@Serializable data object GeneralSettingsRoute
@Serializable data object LifestyleSettingsRoute
@Serializable data object AboutRoute

/** Keys used by notification intents to open a screen (see MainActivity deep-link handling). */
object DeepLinkKeys {
    const val EXTRA_TARGET_TYPE = "hayat.target_type"
    const val EXTRA_TARGET_ID = "hayat.target_id"
    const val EXTRA_OCCURRENCE_ID = "hayat.occurrence_id"
    const val EXTRA_ACTION = "hayat.open_action"
    const val ACTION_OPEN = "open"
    const val ACTION_RESCHEDULE = "reschedule"
    const val ACTION_QUICK_ADD_WATER = "hayat.action.QUICK_ADD_WATER"
}
