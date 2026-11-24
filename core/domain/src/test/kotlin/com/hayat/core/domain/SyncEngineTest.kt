package com.hayat.core.domain

import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.sync.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SyncEngineTest {
    private val now = Instant.parse("2026-11-24T06:00:00Z")

    private inner class H(var session: ServerSession? = ServerSession("https://h.test", "a@b.co", "acc", "ref", now.plusSeconds(600))) {
        var server: Pair<Long, ByteArray>? = null
        var local = "local-v1".encodeToByteArray()
        var localHasData = true
        var imported: ByteArray? = null
        var refreshOk = true
        var conflictOnce = false
        var offline = false
        val state = FakeState()
        inner class FakeState : SyncState {
            val d = MutableStateFlow(false); var f: SyncFailure? = null
            override val dirty: Flow<Boolean> = d
            override val running: Flow<Boolean> = MutableStateFlow(false)
            override val lastFailure: Flow<SyncFailure?> = MutableStateFlow(null)
            override suspend fun isDirty() = d.value
            override suspend fun setDirty(dirty: Boolean) { d.value = dirty }
            override fun setRunning(running: Boolean) {}
            override fun setFailure(failure: SyncFailure?) { f = failure }
        }
        val sessions = object : SessionStore {
            override val session: Flow<ServerSession?> = emptyFlow()
            override suspend fun load() = this@H.session
            override suspend fun save(session: ServerSession) { this@H.session = session }
            override suspend fun clear() { this@H.session = null }
        }
        val api = object : ServerApi {
            override suspend fun health(baseUrl: String) = HealthResult.Ok(1, "3")
            override suspend fun login(baseUrl: String, email: String, password: String) = AuthResult.Failed(ConnectionError.WRONG_CREDENTIALS)
            override suspend fun register(baseUrl: String, email: String, password: String) = AuthResult.Failed(ConnectionError.WRONG_CREDENTIALS)
            override suspend fun loginWithKey(baseUrl: String, accessKey: String) = AuthResult.Failed(ConnectionError.WRONG_CREDENTIALS)
            override suspend fun refresh(baseUrl: String, refreshToken: String): AuthResult =
                if (refreshOk) AuthResult.Ok(AuthTokens("acc2", "ref2", now.plusSeconds(900), "a@b.co")) else AuthResult.Failed(ConnectionError.WRONG_CREDENTIALS)
            override suspend fun logout(baseUrl: String, refreshToken: String) {}
            override suspend fun getSnapshot(baseUrl: String, accessToken: String, knownRevision: Long?): SnapshotGet {
                if (offline) return SnapshotGet.Failed(true)
                val s = server ?: return SnapshotGet.None
                return if (s.first == knownRevision) SnapshotGet.NotModified else SnapshotGet.Found(s.first, s.second)
            }
            override suspend fun putSnapshot(baseUrl: String, accessToken: String, baseRevision: Long?, data: ByteArray): SnapshotPut {
                if (offline) return SnapshotPut.Failed(true)
                val latest = server?.first ?: 0
                if (conflictOnce) { conflictOnce = false; return SnapshotPut.Conflict(latest) }
                if ((baseRevision ?: 0) != latest && baseRevision != -1L) return SnapshotPut.Conflict(latest)
                server = (latest + 1) to data
                return SnapshotPut.Ok(latest + 1)
            }
        }
        val source = object : SnapshotSource {
            override suspend fun export() = local
            override suspend fun import(data: ByteArray): Boolean { imported = data; return true }
            override suspend fun hasUserData() = localHasData
            override fun changes(): Flow<Unit> = emptyFlow()
        }
        val engine = SyncEngine(api, sessions, source, state, object : TimeProvider {
            override fun now() = this@SyncEngineTest.now
            override fun zone(): ZoneId = ZoneOffset.UTC
            override fun today(): LocalDate = LocalDate.of(2026, 11, 24)
        })
    }

    @Test fun `not connected does nothing`() = runBlocking {
        assertEquals(SyncOutcome.NOT_CONNECTED, H(session = null).engine.sync())
    }

    @Test fun `first sync pushes local data and records revision`() = runBlocking {
        val h = H()
        assertEquals(SyncOutcome.PUSHED, h.engine.sync())
        assertEquals(1L, h.session!!.revision)
        assertEquals(now, h.session!!.lastSyncedAt)
        assertEquals(SyncOutcome.UP_TO_DATE, h.engine.sync())
    }

    @Test fun `queued offline changes are kept and pushed when back online`() = runBlocking {
        val h = H()
        h.engine.sync()
        h.state.setDirty(true)
        h.offline = true
        assertEquals(SyncOutcome.RETRY, h.engine.sync())
        assertTrue("change must stay queued", h.state.isDirty())
        assertEquals(SyncFailure.OFFLINE, h.state.f)
        h.offline = false
        h.local = "local-v2".encodeToByteArray()
        assertEquals(SyncOutcome.PUSHED, h.engine.sync())
        assertFalse(h.state.isDirty())
        assertEquals("local-v2", h.server!!.second.decodeToString())
    }

    @Test fun `newer server data is pulled when there are no local changes`() = runBlocking {
        val h = H()
        h.engine.sync()
        h.server = 5L to "from-other-phone".encodeToByteArray()
        assertEquals(SyncOutcome.PULLED, h.engine.sync())
        assertEquals("from-other-phone", h.imported!!.decodeToString())
        assertEquals(5L, h.session!!.revision)
    }

    @Test fun `conflict keeps this device's changes`() = runBlocking {
        val h = H()
        h.engine.sync()
        h.server = 9L to "other".encodeToByteArray()
        h.state.setDirty(true)
        assertEquals(SyncOutcome.PUSHED, h.engine.sync())
        assertEquals("local-v1", h.server!!.second.decodeToString())
        assertEquals(10L, h.session!!.revision)
    }

    @Test fun `expired access token is refreshed, rejected refresh signs out`() = runBlocking {
        val h = H(ServerSession("https://h.test", "a@b.co", "old", "ref", now.minusSeconds(5)))
        h.engine.sync()
        assertEquals("acc2", h.session!!.accessToken)
        val h2 = H(ServerSession("https://h.test", "a@b.co", "old", "ref", now.minusSeconds(5)))
        h2.refreshOk = false
        assertEquals(SyncOutcome.SIGNED_OUT, h2.engine.sync())
        assertEquals(SyncFailure.AUTH, h2.state.f)
    }

    @Test fun `status reflects connection, queue, activity and failures`() {
        assertEquals(ServerStatus.NOT_CONNECTED, SyncEngine.status(false, true, false, null))
        assertEquals(ServerStatus.SYNCING, SyncEngine.status(true, true, true, null))
        assertEquals(ServerStatus.OFFLINE, SyncEngine.status(true, true, false, SyncFailure.OFFLINE))
        assertEquals(ServerStatus.ERROR, SyncEngine.status(true, false, false, SyncFailure.AUTH))
        assertEquals(ServerStatus.PENDING, SyncEngine.status(true, true, false, null))
        assertEquals(ServerStatus.SYNCED, SyncEngine.status(true, false, false, null))
    }

    @Test fun `server url normalisation`() {
        assertEquals("https://api.mydomain.com", SyncEngine.normalizeUrl("  api.mydomain.com/ "))
        assertEquals("https://api.mydomain.com:8443/hayat", SyncEngine.normalizeUrl("https://api.mydomain.com:8443/hayat/"))
        assertEquals("http://x.com", SyncEngine.normalizeUrl("http://x.com")) // kept, then refused as NOT_HTTPS by the UI
        listOf("", "   ", "https://", "no spaces.com x", "https://user:pw@evil.com", "localhostish").forEach { assertNull(it, SyncEngine.normalizeUrl(it)) }
    }
}
