package com.hayat.feature.settings

import com.hayat.core.domain.reminder.ReminderCapabilities
import com.hayat.core.domain.reminder.ReminderCapabilityChecker
import com.hayat.core.domain.reminder.SystemSettingsTarget
import com.hayat.core.domain.repository.DataMaintenanceRepository
import com.hayat.core.domain.repository.PreferencesRepository
import com.hayat.core.domain.security.AppLockManager
import com.hayat.core.domain.security.PinCheck
import com.hayat.core.domain.security.PinPolicy
import com.hayat.core.model.DashboardCard
import com.hayat.core.model.UserPreferences
import com.hayat.core.ui.HayatViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: PreferencesRepository,
    private val capabilityChecker: ReminderCapabilityChecker,
    private val appLock: AppLockManager,
    private val maintenance: DataMaintenanceRepository,
) : HayatViewModel() {
    val preferences: StateFlow<UserPreferences?> = prefs.preferences.stateInVm(null)

    private val _capabilities = MutableStateFlow(capabilityChecker.capabilities())
    val capabilities: StateFlow<ReminderCapabilities> = _capabilities.asStateFlow()

    fun refreshCapabilities() { _capabilities.value = capabilityChecker.capabilities() }
    fun openSystem(target: SystemSettingsTarget) = capabilityChecker.open(target)

    /** Updates preferences; reminder-affecting changes trigger a reconciliation pass. */
    fun update(affectsReminders: Boolean = false, transform: (UserPreferences) -> UserPreferences) = safeLaunch {
        prefs.update(transform)
        if (affectsReminders) capabilityChecker.requestReconcile()
    }

    fun moveCard(card: DashboardCard, delta: Int) = update { p ->
        val list = p.dashboardCards.toMutableList()
        val i = list.indexOfFirst { it.card == card }
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i < 0 || i == j) p else { val item = list.removeAt(i); list.add(j, item); p.copy(dashboardCards = list) }
    }

    fun setCardVisible(card: DashboardCard, visible: Boolean) = update { p ->
        p.copy(dashboardCards = p.dashboardCards.map { if (it.card == card) it.copy(visible = visible) else it })
    }

    /** Returns false with a reason when the PIN is not acceptable. */
    fun enableAppLock(pin: CharArray, confirm: CharArray, onResult: (Int?) -> Unit) = safeLaunch {
        when {
            !PinPolicy.isValid(pin) -> onResult(R.string.pin_invalid)
            !pin.contentEquals(confirm) -> onResult(R.string.pin_mismatch)
            else -> {
                appLock.setPin(pin)
                prefs.update { it.copy(appLock = it.appLock.copy(enabled = true)) }
                onResult(null)
            }
        }
        pin.fill('0'); confirm.fill('0')
    }

    fun disableAppLock(pin: CharArray, onResult: (PinCheck) -> Unit) = safeLaunch {
        val r = appLock.verify(pin)
        pin.fill('0')
        if (r == PinCheck.Correct) {
            appLock.clearPin()
            prefs.update { it.copy(appLock = it.appLock.copy(enabled = false, biometricEnabled = false)) }
        }
        onResult(r)
    }

    fun deleteAllData(onDone: () -> Unit) = safeLaunch {
        maintenance.deleteAllUserData()
        appLock.clearPin()
        prefs.update { UserPreferences(onboardingCompleted = true, language = it.language) }
        capabilityChecker.requestReconcile()
        message(R.string.delete_all_done)
        onDone()
    }
}
