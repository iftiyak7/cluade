package com.hayat.core.network

import com.hayat.core.domain.announcement.AppConfig
import com.hayat.core.domain.announcement.FeedClient
import com.hayat.core.domain.announcement.FeedResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches the feed over HTTPS with the platform's system trust store. Sends only a fixed
 * User-Agent ("Hayat/<versionCode>", replacing the default one that names the device model),
 * Accept and If-None-Match. No cookies, no identifiers, no redirects.
 */
class HttpFeedClient @Inject constructor(private val config: AppConfig) : FeedClient {
    override suspend fun fetch(url: String, etag: String?): FeedResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext FeedResult.Failed(retryable = false)
        val conn = try {
            URL(url).openConnection() as? HttpsURLConnection ?: return@withContext FeedResult.Failed(retryable = false)
        } catch (e: IOException) {
            return@withContext FeedResult.Failed(retryable = false)
        }
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Hayat/${config.versionCode}")
            conn.setRequestProperty("Accept", "application/json")
            etag?.takeIf { it.length <= 100 && it.none { c -> c == '\r' || c == '\n' } }?.let { conn.setRequestProperty("If-None-Match", it) }
            when (val code = conn.responseCode) {
                HttpsURLConnection.HTTP_NOT_MODIFIED -> FeedResult.NotModified
                HttpsURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.use { readCapped(it) } ?: return@withContext FeedResult.Failed(retryable = false)
                    val items = FeedParser.parse(body) ?: return@withContext FeedResult.Failed(retryable = false)
                    FeedResult.Updated(items, conn.getHeaderField("ETag"))
                }
                429, in 500..599 -> FeedResult.Failed(retryable = true)
                else -> FeedResult.Failed(retryable = code == 408)
            }
        } catch (e: IOException) {
            FeedResult.Failed(retryable = true) // offline, DNS, TLS or timeout
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(input: java.io.InputStream): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        const val MAX_BYTES = 256 * 1024
    }
}
