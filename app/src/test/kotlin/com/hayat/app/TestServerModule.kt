package com.hayat.app

import com.hayat.core.domain.sync.AuthResult
import com.hayat.core.domain.sync.AuthTokens
import com.hayat.core.domain.sync.ConnectionError
import com.hayat.core.domain.sync.HealthResult
import com.hayat.core.domain.sync.ServerApi
import com.hayat.core.domain.sync.SnapshotGet
import com.hayat.core.domain.sync.SnapshotPut
import com.hayat.core.network.ServerApiModule
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import java.time.Instant
import javax.inject.Singleton

/** In-memory Hayat server for UI tests (no network). URLs containing "badcert" fail with an SSL error. */
class FakeServerApi : ServerApi {
    var snapshot: Pair<Long, ByteArray>? = null
    val password = "a long password 1"

    override suspend fun health(baseUrl: String): HealthResult = when {
        "badcert" in baseUrl -> HealthResult.Failed(ConnectionError.SSL)
        "offline" in baseUrl -> HealthResult.Failed(ConnectionError.OFFLINE)
        else -> HealthResult.Ok(42, "3")
    }
    private fun tokens(account: String) = AuthResult.Ok(AuthTokens("access", "refresh", Instant.now().plusSeconds(900), account))
    override suspend fun login(baseUrl: String, email: String, password: String) =
        if (password == this.password) tokens(email) else AuthResult.Failed(ConnectionError.WRONG_CREDENTIALS)
    override suspend fun register(baseUrl: String, email: String, password: String) = tokens(email)
    override suspend fun loginWithKey(baseUrl: String, accessKey: String) = tokens("key")
    override suspend fun refresh(baseUrl: String, refreshToken: String) = tokens("refreshed")
    override suspend fun logout(baseUrl: String, refreshToken: String) {}
    override suspend fun getSnapshot(baseUrl: String, accessToken: String, knownRevision: Long?): SnapshotGet {
        val s = snapshot ?: return SnapshotGet.None
        return if (knownRevision == s.first) SnapshotGet.NotModified else SnapshotGet.Found(s.first, s.second)
    }
    override suspend fun putSnapshot(baseUrl: String, accessToken: String, baseRevision: Long?, data: ByteArray): SnapshotPut {
        val next = (snapshot?.first ?: 0) + 1
        snapshot = next to data
        return SnapshotPut.Ok(next)
    }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ServerApiModule::class])
object TestServerModule {
    @Provides @Singleton fun fake(): FakeServerApi = FakeServerApi()
    @Provides fun api(fake: FakeServerApi): ServerApi = fake
}
