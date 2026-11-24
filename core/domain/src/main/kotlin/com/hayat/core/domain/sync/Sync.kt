package com.hayat.core.domain.sync

import com.hayat.core.common.TimeProvider
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject

/** What the header dot and Settings → Server show. */
enum class ServerStatus { NOT_CONNECTED, SYNCED, PENDING, SYNCING, OFFLINE, ERROR }

/** Reasons a connection test or login can fail, each with its own message in the UI. */
enum class ConnectionError { INVALID_URL, NOT_HTTPS, OFFLINE, TIMEOUT, SSL, NOT_HAYAT, SERVER_ERROR, WRONG_CREDENTIALS, RATE_LIMITED, SIGNUP_CLOSED, EMAIL_TAKEN, WEAK_PASSWORD }

sealed interface HealthResult {
    data class Ok(val latencyMs: Long, val serverVersion: String) : HealthResult
    data class Failed(val error: ConnectionError) : HealthResult
}

data class AuthTokens(val accessToken: String, val refreshToken: String, val accessExpiresAt: Instant, val account: String)

sealed interface AuthResult {
    data class Ok(val tokens: AuthTokens) : AuthResult
    data class Failed(val error: ConnectionError) : AuthResult
}

sealed interface SnapshotGet {
    data class Found(val revision: Long, val data: ByteArray) : SnapshotGet
    data object NotModified : SnapshotGet
    data object None : SnapshotGet
    data object Unauthorized : SnapshotGet
    data class Failed(val retryable: Boolean) : SnapshotGet
}

sealed interface SnapshotPut {
    data class Ok(val revision: Long) : SnapshotPut
    data class Conflict(val serverRevision: Long) : SnapshotPut
    data object Unauthorized : SnapshotPut
    data object TooLarge : SnapshotPut
    data class Failed(val retryable: Boolean) : SnapshotPut
}

/** HTTPS client for the user's Hayat server (REST, JSON, JWT bearer tokens). */
interface ServerApi {
    suspend fun health(baseUrl: String): HealthResult
    suspend fun login(baseUrl: String, email: String, password: String): AuthResult
    suspend fun register(baseUrl: String, email: String, password: String): AuthResult
    suspend fun loginWithKey(baseUrl: String, accessKey: String): AuthResult
    suspend fun refresh(baseUrl: String, refreshToken: String): AuthResult
    suspend fun logout(baseUrl: String, refreshToken: String)
    suspend fun getSnapshot(baseUrl: String, accessToken: String, knownRevision: Long?): SnapshotGet
    /** [baseRevision] null = no snapshot expected on the server yet; -1 = overwrite whatever is there. */
    suspend fun putSnapshot(baseUrl: String, accessToken: String, baseRevision: Long?, data: ByteArray): SnapshotPut
}

/** Connection + sync bookkeeping. Tokens are kept encrypted with an Android Keystore key. */
data class ServerSession(
    val baseUrl: String,
    val account: String,
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: Instant,
    val revision: Long? = null,
    val lastSyncedAt: Instant? = null,
)

interface SessionStore {
    val session: Flow<ServerSession?>
    suspend fun load(): ServerSession?
    suspend fun save(session: ServerSession)
    suspend fun clear()
}

/** Serialises all user data to bytes and back (the backup format), and reports local changes. */
interface SnapshotSource {
    suspend fun export(): ByteArray
    suspend fun import(data: ByteArray): Boolean
    suspend fun hasUserData(): Boolean
    /** Emits whenever user data changes (not for changes made by [import]). */
    fun changes(): Flow<Unit>
}

/** Pending-change flag and live state, owned by the network layer. */
interface SyncState {
    val dirty: Flow<Boolean>
    val running: Flow<Boolean>
    val lastFailure: Flow<SyncFailure?>
    suspend fun isDirty(): Boolean
    suspend fun setDirty(dirty: Boolean)
    fun setRunning(running: Boolean)
    fun setFailure(failure: SyncFailure?)
}

enum class SyncFailure { OFFLINE, AUTH, SERVER }

interface SyncStatusProvider {
    val status: Flow<ServerStatus>
    /** Ask for a sync as soon as a network is available (no-op when not connected). */
    fun requestSync()
}

enum class SyncOutcome { NOT_CONNECTED, PUSHED, PULLED, UP_TO_DATE, RETRY, SIGNED_OUT, FAILED }

/**
 * Offline-first snapshot sync. Local data is the working copy; the server keeps the latest
 * snapshot (plus a short history). Rules:
 * * local changes → push with the last known revision; on conflict, this device's version wins
 *   and the server keeps the replaced revision in its history (nothing is lost on the server);
 * * no local changes → pull if the server has a newer revision;
 * * expired access token → refresh once; a rejected refresh token signs the device out.
 */
class SyncEngine @Inject constructor(
    private val api: ServerApi,
    private val sessions: SessionStore,
    private val source: SnapshotSource,
    private val state: SyncState,
    private val time: TimeProvider,
) {
    suspend fun sync(): SyncOutcome {
        var s = sessions.load() ?: return SyncOutcome.NOT_CONNECTED
        state.setRunning(true)
        try {
            s = ensureFresh(s) ?: return signedOut()
            val dirty = state.isDirty()
            if (dirty) return push(s, s.revision)
            return when (val g = api.getSnapshot(s.baseUrl, s.accessToken, s.revision)) {
                SnapshotGet.NotModified -> done(s, s.revision, SyncOutcome.UP_TO_DATE)
                is SnapshotGet.Found -> {
                    if (!source.import(g.data)) return failed(SyncFailure.SERVER, SyncOutcome.FAILED)
                    state.setDirty(false)
                    done(s, g.revision, SyncOutcome.PULLED)
                }
                SnapshotGet.None -> if (source.hasUserData()) push(s, null) else done(s, null, SyncOutcome.UP_TO_DATE)
                SnapshotGet.Unauthorized -> signedOut()
                is SnapshotGet.Failed -> failed(if (g.retryable) SyncFailure.OFFLINE else SyncFailure.SERVER, if (g.retryable) SyncOutcome.RETRY else SyncOutcome.FAILED)
            }
        } finally {
            state.setRunning(false)
        }
    }

    private suspend fun push(s: ServerSession, base: Long?): SyncOutcome {
        val data = source.export()
        return when (val r = api.putSnapshot(s.baseUrl, s.accessToken, base, data)) {
            is SnapshotPut.Ok -> { state.setDirty(false); done(s, r.revision, SyncOutcome.PUSHED) }
            is SnapshotPut.Conflict -> when (val r2 = api.putSnapshot(s.baseUrl, s.accessToken, r.serverRevision, data)) {
                is SnapshotPut.Ok -> { state.setDirty(false); done(s, r2.revision, SyncOutcome.PUSHED) }
                SnapshotPut.Unauthorized -> signedOut()
                else -> failed(SyncFailure.SERVER, SyncOutcome.RETRY)
            }
            SnapshotPut.Unauthorized -> signedOut()
            SnapshotPut.TooLarge -> failed(SyncFailure.SERVER, SyncOutcome.FAILED)
            is SnapshotPut.Failed -> failed(if (r.retryable) SyncFailure.OFFLINE else SyncFailure.SERVER, if (r.retryable) SyncOutcome.RETRY else SyncOutcome.FAILED)
        }
    }

    private suspend fun ensureFresh(s: ServerSession): ServerSession? {
        if (s.accessExpiresAt.isAfter(time.now().plusSeconds(30))) return s
        return when (val r = api.refresh(s.baseUrl, s.refreshToken)) {
            is AuthResult.Ok -> s.copy(accessToken = r.tokens.accessToken, refreshToken = r.tokens.refreshToken, accessExpiresAt = r.tokens.accessExpiresAt).also { sessions.save(it) }
            is AuthResult.Failed -> if (r.error == ConnectionError.WRONG_CREDENTIALS) null else s // offline: try with the old token
        }
    }

    private suspend fun done(s: ServerSession, revision: Long?, outcome: SyncOutcome): SyncOutcome {
        sessions.save(s.copy(revision = revision, lastSyncedAt = time.now()))
        state.setFailure(null)
        return outcome
    }

    private fun failed(f: SyncFailure, o: SyncOutcome): SyncOutcome { state.setFailure(f); return o }

    private suspend fun signedOut(): SyncOutcome {
        state.setFailure(SyncFailure.AUTH)
        return SyncOutcome.SIGNED_OUT
    }

    /** Status shown in the UI, derived from session, pending changes, activity and last failure. */
    companion object {
        fun status(connected: Boolean, dirty: Boolean, running: Boolean, failure: SyncFailure?): ServerStatus = when {
            !connected -> ServerStatus.NOT_CONNECTED
            running -> ServerStatus.SYNCING
            failure == SyncFailure.OFFLINE -> ServerStatus.OFFLINE
            failure != null -> ServerStatus.ERROR
            dirty -> ServerStatus.PENDING
            else -> ServerStatus.SYNCED
        }

        /** Normalises what the user typed: trims, adds https:// if no scheme, drops a trailing slash. */
        fun normalizeUrl(input: String): String? {
            var u = input.trim()
            if (u.isEmpty()) return null
            if (!u.contains("://")) u = "https://$u"
            u = u.trimEnd('/')
            val rest = u.substringAfter("://")
            val host = rest.substringBefore('/').substringBefore(':')
            if (host.isEmpty() || host.any { it.isWhitespace() } || '@' in rest.substringBefore('/')) return null
            if (!host.contains('.') && host != "localhost") return null
            return u
        }
    }
}
