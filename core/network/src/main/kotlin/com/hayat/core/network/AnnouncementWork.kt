package com.hayat.core.network

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hayat.core.domain.announcement.AnnouncementNotifier
import com.hayat.core.domain.announcement.AnnouncementRepository
import com.hayat.core.domain.announcement.FeedClient
import com.hayat.core.domain.announcement.SyncAnnouncementsUseCase
import com.hayat.core.domain.announcement.SyncOutcome
import com.hayat.core.notifications.AndroidAnnouncementNotifier
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@HiltWorker
class AnnouncementSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: SyncAnnouncementsUseCase,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        when (sync()) {
            SyncOutcome.RETRY -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.success() // never crash-loop on bad data; the next period tries again
    }
}

/** Starts or stops background fetching when the user's setting changes. */
@Singleton
class AnnouncementWorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: AnnouncementRepository,
) {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    suspend fun apply(enabled: Boolean, configured: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || !configured) {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(NOW)
            if (!enabled) repo.clear() // turning the feature off also removes downloaded content
            return
        }
        val periodic = PeriodicWorkRequestBuilder<AnnouncementSyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
            .setConstraints(online)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
        syncNow()
    }

    /** One fetch as soon as a network is available (e.g. on app start). */
    fun syncNow() {
        val req = OneTimeWorkRequestBuilder<AnnouncementSyncWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, req)
    }

    companion object {
        private const val PERIODIC = "announcements-periodic"
        private const val NOW = "announcements-now"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds abstract fun feedClient(impl: HttpFeedClient): FeedClient
    @Binds abstract fun notifier(impl: AndroidAnnouncementNotifier): AnnouncementNotifier
}
