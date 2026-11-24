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
import com.hayat.core.domain.sync.ServerApi
import com.hayat.core.domain.sync.ServerSession
import com.hayat.core.domain.sync.ServerStatus
import com.hayat.core.domain.sync.SessionStore
import com.hayat.core.domain.sync.SyncEngine
import com.hayat.core.domain.sync.SyncFailure
import com.hayat.core.domain.sync.SyncOutcome
import com.hayat.core.domain.sync.SyncState
import com.hayat.core.domain.sync.SyncStatusProvider
import com.hayat.core.security.SecretWrapper
import dagger.Binds
import dagger.Module
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable private data class SessionDto(
    val baseUrl: String, val account: String, val accessToken: String, val refreshToken: String,
    val accessExpiresAt: Long, val revision: Long? = null, val lastSyncedAt: Long? = null,
)

/**
 * Session (server URL, account, tokens, sync revision) stored as one file encrypted with an
 * AES-256-GCM key that lives in Android Keystore and never leaves the device. Excluded from
 * Android backup (allowBackup=false).
 */
@Singleton
class KeystoreSessionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val wrapper: SecretWrapper,
) : SessionStore {
    private val file = File(context.noBackupFilesDir, "server_session.bin")
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val state = MutableStateFlow<ServerSession?>(null)
    @Volatile private var loaded = false
    override val session: Flow<ServerSession?> = state.asStateFlow()

    override suspend fun load(): ServerSession? = lock.withLock { ensureLoaded(); state.value }

    private suspend fun ensureLoaded() {
        if (loaded) return
        state.value = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists()) return@runCatching null
                val d = json.decodeFromString(SessionDto.serializer(), wrapper.unwrap(file.readBytes()).decodeToString())
                ServerSession(d.baseUrl, d.account, d.accessToken, d.refreshToken, Instant.ofEpochSecond(d.accessExpiresAt), d.revision, d.lastSyncedAt?.let(Instant::ofEpochSecond))
            }.getOrNull() // unreadable (e.g. Keystore reset) → treated as signed out
        }
        loaded = true
    }

    override suspend fun save(session: ServerSession) = lock.withLock {
        val d = SessionDto(session.baseUrl, session.account, session.accessToken, session.refreshToken, session.accessExpiresAt.epochSecond, session.revision, session.lastSyncedAt?.epochSecond)
        withContext(Dispatchers.IO) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(wrapper.wrap(json.encodeToString(SessionDto.serializer(), d).encodeToByteArray()))
            tmp.renameTo(file)
        }
        state.value = session
        loaded = true
    }

    override suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        state.value = null
        loaded = true
    }

    /** Loads the session eagerly so status flows are correct right after start. */
    suspend fun warmUp() { load() }
}

/** Pending-change flag persisted across restarts (the offline queue), plus live progress. */
@Singleton
class PrefsSyncState @Inject constructor(@ApplicationContext context: Context) : SyncState {
    private val prefs = context.getSharedPreferences("hayat_sync_state", Context.MODE_PRIVATE)
    private val dirtyFlow = MutableStateFlow(prefs.getBoolean(KEY_DIRTY, false))
    private val runningFlow = MutableStateFlow(false)
    private val failureFlow = MutableStateFlow<SyncFailure?>(null)
    override val dirty: Flow<Boolean> = dirtyFlow
    override val running: Flow<Boolean> = runningFlow
    override val lastFailure: Flow<SyncFailure?> = failureFlow
    override suspend fun isDirty() = dirtyFlow.value
    override suspend fun setDirty(dirty: Boolean) { prefs.edit().putBoolean(KEY_DIRTY, dirty).apply(); dirtyFlow.value = dirty }
    override fun setRunning(running: Boolean) { runningFlow.value = running }
    override fun setFailure(failure: SyncFailure?) { failureFlow.value = failure }
    private companion object { const val KEY_DIRTY = "dirty" }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: SyncEngine,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        when (engine.sync()) {
            SyncOutcome.RETRY -> if (runAttemptCount < 8) Result.retry() else Result.success()
            else -> Result.success()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.success()
    }
}

/** Schedules syncs: debounced after changes, on demand, and every 6 h; always waits for a network. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    sessions: SessionStore,
    state: SyncState,
) : SyncStatusProvider {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override val status: Flow<ServerStatus> = combine(sessions.session, state.dirty, state.running, state.lastFailure) { s, d, r, f ->
        SyncEngine.status(s != null, d, r, f)
    }.distinctUntilChanged()

    override fun requestSync() = enqueue(delaySeconds = 0)

    /** Called on every local change: coalesces bursts of edits into one upload. */
    fun onLocalChange() = enqueue(delaySeconds = 8)

    private fun enqueue(delaySeconds: Long) {
        val req = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    fun ensurePeriodic() {
        val req = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS).setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(NOW)
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    private companion object {
        const val NOW = "sync-now"
        const val PERIODIC = "sync-periodic"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds abstract fun state(impl: PrefsSyncState): SyncState
    @Binds abstract fun status(impl: SyncScheduler): SyncStatusProvider
    @Binds abstract fun sessions(impl: KeystoreSessionStore): SessionStore
}

/** Separate so tests can swap in a fake server. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServerApiModule {
    @Binds abstract fun api(impl: HttpServerApi): ServerApi
}
