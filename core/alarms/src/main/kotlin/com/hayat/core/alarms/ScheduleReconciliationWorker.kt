package com.hayat.core.alarms

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.reminder.ReminderScheduler
import com.hayat.core.domain.repository.DataMaintenanceRepository
import com.hayat.core.domain.repository.DeliveryLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deferrable maintenance: reconciles stored schedules with OS alarms (idempotent), then prunes
 * old diagnostics and soft-deleted rows. Not used for exact-time delivery.
 */
@HiltWorker
class ScheduleReconciliationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scheduler: ReminderScheduler,
    private val deliveryLog: DeliveryLogRepository,
    private val maintenance: DataMaintenanceRepository,
    private val time: TimeProvider,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        when (inputData.getString(KEY_REASON)) {
            REASON_ZONE -> scheduler.onTimeZoneChanged()
            REASON_BOOT -> scheduler.reconcile(notifyMissed = true)
            else -> scheduler.reconcile(notifyMissed = inputData.getString(KEY_REASON) != REASON_PERIODIC)
        }
        val now = time.now()
        deliveryLog.pruneBefore(now.minus(Duration.ofDays(30)))
        maintenance.purgeSoftDeleted(now.minus(Duration.ofDays(30)))
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }

    companion object {
        const val KEY_REASON = "reason"
        const val REASON_BOOT = "boot"
        const val REASON_TIME = "time"
        const val REASON_ZONE = "zone"
        const val REASON_PERMISSION = "permission"
        const val REASON_PERIODIC = "periodic"
        const val REASON_APP = "app"
    }
}

@Singleton
class ReconciliationScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun enqueueNow(reason: String) {
        val request = OneTimeWorkRequestBuilder<ScheduleReconciliationWorker>()
            .setInputData(workDataOf(ScheduleReconciliationWorker.KEY_REASON to reason))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("reconcile-now", ExistingWorkPolicy.REPLACE, request)
    }

    fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<ScheduleReconciliationWorker>(6, TimeUnit.HOURS)
            .setInputData(workDataOf(ScheduleReconciliationWorker.KEY_REASON to ScheduleReconciliationWorker.REASON_PERIODIC))
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reconcile-periodic", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
