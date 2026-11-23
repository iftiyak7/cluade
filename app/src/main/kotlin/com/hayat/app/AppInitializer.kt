package com.hayat.app

import com.hayat.core.alarms.ReconciliationScheduler
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.repository.CategoryRepository
import com.hayat.core.domain.repository.IbadahRepository
import com.hayat.core.domain.worship.ZikrPresets
import com.hayat.core.model.Category
import com.hayat.core.notifications.NotificationChannelManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** One-time work at process start. Everything here is idempotent. */
@Singleton
class AppInitializer @Inject constructor(
    private val channels: NotificationChannelManager,
    private val categories: CategoryRepository,
    private val ibadah: IbadahRepository,
    private val reconciliation: ReconciliationScheduler,
    private val scheduler: ReminderScheduler,
    private val time: TimeProvider,
) {
    suspend fun initialize() {
        channels.ensureChannels()
        seedCategories()
        ibadah.ensurePresets(ZikrPresets.presets(time.now()))
        reconciliation.ensurePeriodic()
        // Catches gaps after force-stop, process death or updates: missed occurrences are recorded, alarms re-registered.
        runCatching { scheduler.reconcile(notifyMissed = true) }
    }

    private suspend fun seedCategories() {
        if (categories.observeCategories().first().isNotEmpty()) return
        listOf(
            "personal" to 0xFF167C5A, "work" to 0xFF3B6FD6, "study" to 0xFF8E5BD6,
            "health" to 0xFFC2477A, "home" to 0xFFD06A2B, "finance" to 0xFF9C7A14,
        ).forEachIndexed { i, (key, color) -> categories.upsert(Category("cat.$key", key, key, color, key, i)) }
    }
}
