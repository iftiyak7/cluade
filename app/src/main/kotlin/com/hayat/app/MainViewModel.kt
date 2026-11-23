package com.hayat.app

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.model.ReminderAction
import com.hayat.core.model.ReminderTargetType
import com.hayat.core.model.UserPreferences
import com.hayat.core.navigation.*
import com.hayat.core.security.AppLockController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    preferences: PreferencesRepository,
    private val lock: AppLockController,
    private val scheduler: ReminderScheduler,
) : ViewModel() {
    val prefs: StateFlow<UserPreferences?> = preferences.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The lock overlay is shown only when app lock is enabled and the session is locked. */
    val locked: StateFlow<Boolean> = combine(preferences.preferences, lock.locked) { p, l -> p.appLock.enabled && l }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _pendingRoute = MutableStateFlow<Any?>(null)
    val pendingRoute: StateFlow<Any?> = _pendingRoute.asStateFlow()

    fun unlock() = lock.unlock()
    fun consumeRoute() { _pendingRoute.value = null }

    fun handleIntent(intent: Intent?) {
        intent ?: return
        val occurrenceId = intent.getStringExtra(DeepLinkKeys.EXTRA_OCCURRENCE_ID)
        val action = intent.getStringExtra(DeepLinkKeys.EXTRA_ACTION)
        if (intent.action == DeepLinkKeys.ACTION_QUICK_ADD_WATER) { _pendingRoute.value = WaterRoute; return }
        if (occurrenceId == null || action == null) return
        if (action == DeepLinkKeys.ACTION_RESCHEDULE) { _pendingRoute.value = ReminderRescheduleRoute(occurrenceId); return }
        viewModelScope.launch { scheduler.handleAction(occurrenceId, ReminderAction.OPEN) }
        val type = intent.getStringExtra(DeepLinkKeys.EXTRA_TARGET_TYPE)?.let { t -> ReminderTargetType.entries.firstOrNull { it.name == t } }
        _pendingRoute.value = routeFor(type, intent.getStringExtra(DeepLinkKeys.EXTRA_TARGET_ID))
        // Prevent re-handling on configuration change.
        intent.removeExtra(DeepLinkKeys.EXTRA_OCCURRENCE_ID)
    }

    private fun routeFor(type: ReminderTargetType?, id: String?): Any = when (type) {
        ReminderTargetType.TASK -> id?.let { TaskDetailRoute(it) } ?: TasksRoute
        ReminderTargetType.ROUTINE_ACTIVITY -> CalendarRoute()
        ReminderTargetType.MEDICINE, ReminderTargetType.MEDICINE_REFILL -> MedicineRoute
        ReminderTargetType.WATER -> WaterRoute
        ReminderTargetType.MEAL -> DietRoute
        ReminderTargetType.HABIT -> HabitsRoute
        ReminderTargetType.PRAYER, ReminderTargetType.ZIKR, ReminderTargetType.QURAN -> IbadahRoute
        ReminderTargetType.SLEEP_BEDTIME, ReminderTargetType.SLEEP_WAKE -> SleepRoute
        ReminderTargetType.WORKOUT -> FitnessRoute
        ReminderTargetType.STUDY, ReminderTargetType.FOCUS -> StudyRoute
        ReminderTargetType.FINANCE -> FinanceRoute
        ReminderTargetType.EVENT -> EventsRoute
        ReminderTargetType.NOTE -> id?.let { NoteEditRoute(it) } ?: NotesRoute
        ReminderTargetType.NONE, null -> RemindersRoute
    }
}
