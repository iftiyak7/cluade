package com.hayat.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.domain.sync.AuthResult
import com.hayat.core.domain.sync.AuthTokens
import com.hayat.core.domain.sync.ConnectionError
import com.hayat.core.domain.sync.HealthResult
import com.hayat.core.domain.sync.ServerApi
import com.hayat.core.domain.sync.SnapshotGet
import com.hayat.core.domain.sync.SnapshotPut
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException

@Serializable private data class TokenDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    val account: String = "",
)
@Serializable private data class HealthDto(val status: String = "", val service: String = "", val version: String = "")
@Serializable private data class ErrorDto(val error: String = "", val revision: Long? = null)
@Serializable private data class RevisionDto(val revision: Long)
@Serializable private data class Creds(val email: String, val password: String)
@Serializable private data class KeyBody(@SerialName("access_key") val accessKey: String)
@Serializable private data class RefreshBody(@SerialName("refresh_token") val refreshToken: String)

/**
 * HTTPS-only client for the Hayat server. Uses the system trust store (no user CAs), no redirects,
 * a fixed User-Agent, and maps every failure to a [ConnectionError] the UI can explain.
 */
class HttpServerApi @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: AppConfig,
) : ServerApi {
    private val json = Json { ignoreUnknownKeys = true }

    private class Response(val code: Int, val body: ByteArray, val headers: Map<String, String>)

    private sealed interface Outcome {
        data class Ok(val r: Response, val ms: Long) : Outcome
        data class Err(val e: ConnectionError) : Outcome
    }

    private fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun call(
        base: String, path: String, method: String, body: ByteArray? = null, token: String? = null,
        headers: Map<String, String> = emptyMap(), contentType: String = "application/json",
    ): Outcome = withContext(Dispatchers.IO) {
        if (!base.startsWith("https://")) return@withContext Outcome.Err(ConnectionError.NOT_HTTPS)
        val url = try { URL(base.trimEnd('/') + path) } catch (e: MalformedURLException) { return@withContext Outcome.Err(ConnectionError.INVALID_URL) }
        val conn = try { url.openConnection() as? HttpsURLConnection } catch (e: IOException) { null } ?: return@withContext Outcome.Err(ConnectionError.INVALID_URL)
        val started = System.nanoTime()
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.requestMethod = method
            conn.setRequestProperty("User-Agent", "Hayat/${config.versionCode}")
            conn.setRequestProperty("Accept", "application/json")
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val bytes = stream?.use { readCapped(it, MAX_RESPONSE) } ?: ByteArray(0)
            val hdrs = conn.headerFields.filterKeys { it != null }.mapKeys { it.key.lowercase() }.mapValues { it.value.firstOrNull().orEmpty() }
            Outcome.Ok(Response(code, bytes, hdrs), (System.nanoTime() - started) / 1_000_000)
        } catch (e: SSLException) {
            Outcome.Err(ConnectionError.SSL)
        } catch (e: UnknownHostException) {
            Outcome.Err(if (online()) ConnectionError.INVALID_URL else ConnectionError.OFFLINE)
        } catch (e: SocketTimeoutException) {
            Outcome.Err(if (online()) ConnectionError.TIMEOUT else ConnectionError.OFFLINE)
        } catch (e: ConnectException) {
            Outcome.Err(ConnectionError.OFFLINE)
        } catch (e: IOException) {
            Outcome.Err(if (online()) ConnectionError.SERVER_ERROR else ConnectionError.OFFLINE)
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > max) throw IOException("response too large")
        }
        return out.toByteArray()
    }

    private fun errorCode(r: Response): String = runCatching { json.decodeFromString(ErrorDto.serializer(), r.body.decodeToString()).error }.getOrDefault("")

    private fun mapError(r: Response): ConnectionError = when (r.code) {
        401 -> ConnectionError.WRONG_CREDENTIALS
        403 -> if (errorCode(r) == "signup_closed") ConnectionError.SIGNUP_CLOSED else ConnectionError.WRONG_CREDENTIALS
        404 -> ConnectionError.NOT_HAYAT
        409 -> ConnectionError.EMAIL_TAKEN
        422 -> if (errorCode(r) == "weak_password") ConnectionError.WEAK_PASSWORD else ConnectionError.WRONG_CREDENTIALS
        429 -> ConnectionError.RATE_LIMITED
        in 300..399 -> ConnectionError.NOT_HAYAT
        else -> ConnectionError.SERVER_ERROR
    }

    override suspend fun health(baseUrl: String): HealthResult = when (val o = call(baseUrl, "/api/health", "GET")) {
        is Outcome.Err -> HealthResult.Failed(o.e)
        is Outcome.Ok -> {
            val h = runCatching { json.decodeFromString(HealthDto.serializer(), o.r.body.decodeToString()) }.getOrNull()
            when {
                o.r.code == 200 && h?.service == "hayat" -> HealthResult.Ok(o.ms, h.version)
                o.r.code in 500..599 && h?.service == "hayat" -> HealthResult.Failed(ConnectionError.SERVER_ERROR)
                else -> HealthResult.Failed(ConnectionError.NOT_HAYAT)
            }
        }
    }

    private suspend fun auth(baseUrl: String, path: String, body: ByteArray): AuthResult = when (val o = call(baseUrl, path, "POST", body)) {
        is Outcome.Err -> AuthResult.Failed(o.e)
        is Outcome.Ok -> if (o.r.code in 200..201) {
            runCatching { json.decodeFromString(TokenDto.serializer(), o.r.body.decodeToString()) }.getOrNull()?.let { t ->
                AuthResult.Ok(AuthTokens(t.accessToken, t.refreshToken, Instant.now().plusSeconds(t.expiresIn), t.account))
            } ?: AuthResult.Failed(ConnectionError.NOT_HAYAT)
        } else AuthResult.Failed(mapError(o.r))
    }

    override suspend fun login(baseUrl: String, email: String, password: String) =
        auth(baseUrl, "/api/auth/login", json.encodeToString(Creds.serializer(), Creds(email.trim(), password)).encodeToByteArray())
    override suspend fun register(baseUrl: String, email: String, password: String) =
        auth(baseUrl, "/api/auth/register", json.encodeToString(Creds.serializer(), Creds(email.trim(), password)).encodeToByteArray())
    override suspend fun loginWithKey(baseUrl: String, accessKey: String) =
        auth(baseUrl, "/api/auth/key", json.encodeToString(KeyBody.serializer(), KeyBody(accessKey.trim())).encodeToByteArray())
    override suspend fun refresh(baseUrl: String, refreshToken: String) =
        auth(baseUrl, "/api/auth/refresh", json.encodeToString(RefreshBody.serializer(), RefreshBody(refreshToken)).encodeToByteArray())

    override suspend fun logout(baseUrl: String, refreshToken: String) {
        call(baseUrl, "/api/auth/logout", "POST", json.encodeToString(RefreshBody.serializer(), RefreshBody(refreshToken)).encodeToByteArray())
    }

    override suspend fun getSnapshot(baseUrl: String, accessToken: String, knownRevision: Long?): SnapshotGet {
        val o = call(baseUrl, "/api/sync/snapshot", "GET", token = accessToken, headers = knownRevision?.let { mapOf("If-None-Match" to "\"$it\"") } ?: emptyMap())
        if (o is Outcome.Err) return SnapshotGet.Failed(retryable = o.e in setOf(ConnectionError.OFFLINE, ConnectionError.TIMEOUT, ConnectionError.SERVER_ERROR))
        val r = (o as Outcome.Ok).r
        return when (r.code) {
            200 -> r.headers["x-hayat-revision"]?.toLongOrNull()?.let { SnapshotGet.Found(it, r.body) } ?: SnapshotGet.Failed(false)
            304 -> SnapshotGet.NotModified
            404 -> SnapshotGet.None
            401 -> SnapshotGet.Unauthorized
            429, in 500..599 -> SnapshotGet.Failed(true)
            else -> SnapshotGet.Failed(false)
        }
    }

    override suspend fun putSnapshot(baseUrl: String, accessToken: String, baseRevision: Long?, data: ByteArray): SnapshotPut {
        val ifMatch = when (baseRevision) { null -> "\"none\""; -1L -> "*"; else -> "\"$baseRevision\"" }
        val o = call(baseUrl, "/api/sync/snapshot", "PUT", data, accessToken, mapOf("If-Match" to ifMatch), "application/octet-stream")
        if (o is Outcome.Err) return SnapshotPut.Failed(retryable = true)
        val r = (o as Outcome.Ok).r
        return when (r.code) {
            200 -> runCatching { SnapshotPut.Ok(json.decodeFromString(RevisionDto.serializer(), r.body.decodeToString()).revision) }.getOrDefault(SnapshotPut.Failed(false))
            409 -> SnapshotPut.Conflict(runCatching { json.decodeFromString(ErrorDto.serializer(), r.body.decodeToString()).revision }.getOrNull() ?: 0L)
            401 -> SnapshotPut.Unauthorized
            413 -> SnapshotPut.TooLarge
            429, in 500..599 -> SnapshotPut.Failed(true)
            else -> SnapshotPut.Failed(false)
        }
    }

    companion object { private const val MAX_RESPONSE = 12 * 1024 * 1024 }
}
