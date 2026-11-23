package com.hayat.core.backup

import com.hayat.core.common.AppException
import com.hayat.core.common.AppError
import com.hayat.core.common.BackupFailure
import com.hayat.core.security.Crypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException

/**
 * Hayat backup container (version 1):
 *
 * ```
 * magic "HAYATBK" (7) | format version (1) | flags (1: bit0 = encrypted)
 * plain:     sha256(gzip payload) (32) | gzip(JSON)
 * encrypted: iterations (int32) | salt (16) | iv (12) | AES-256-GCM(gzip(JSON)) incl. 16-byte tag
 * ```
 * The header is authenticated as AAD. Decompression is capped to defeat zip bombs.
 */
object BackupFormat {
    private val MAGIC = "HAYATBK".toByteArray(Charsets.US_ASCII)
    const val FORMAT_VERSION: Byte = 1
    private const val FLAG_ENCRYPTED = 0x01
    const val KDF_ITERATIONS = 210_000
    /** Max compressed file accepted and max decompressed JSON. */
    const val MAX_FILE_BYTES = 64L * 1024 * 1024
    const val MAX_JSON_BYTES = 256L * 1024 * 1024
    private const val HEADER = 9

    fun encode(json: ByteArray, passphrase: CharArray?, iterations: Int = KDF_ITERATIONS): ByteArray {
        val gz = gzip(json)
        val encrypted = passphrase != null
        val header = MAGIC + byteArrayOf(FORMAT_VERSION, if (encrypted) FLAG_ENCRYPTED.toByte() else 0)
        val out = ByteArrayOutputStream()
        out.write(header)
        if (!encrypted) {
            out.write(Crypto.sha256(gz)); out.write(gz)
        } else {
            val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
            val iv = Crypto.randomBytes(Crypto.IV_BYTES)
            val key = Crypto.pbkdf2(passphrase!!, salt, iterations)
            val params = ByteBuffer.allocate(4).putInt(iterations).array() + salt + iv
            try {
                out.write(params)
                out.write(Crypto.aesGcmEncrypt(key, iv, gz, header + params))
            } finally { key.fill(0) }
        }
        return out.toByteArray()
    }

    fun isEncrypted(bytes: ByteArray): Boolean = bytes.size > HEADER && hasMagic(bytes) && (bytes[8].toInt() and FLAG_ENCRYPTED) != 0

    /** Returns the JSON bytes or throws [AppException] with a precise [BackupFailure]. */
    fun decode(bytes: ByteArray, passphrase: CharArray?): ByteArray {
        if (bytes.size < HEADER || !hasMagic(bytes)) fail(BackupFailure.NOT_A_HAYAT_BACKUP)
        if (bytes[7] != FORMAT_VERSION) fail(BackupFailure.UNSUPPORTED_FORMAT_VERSION)
        val header = bytes.copyOfRange(0, HEADER)
        val gz = if ((bytes[8].toInt() and FLAG_ENCRYPTED) == 0) {
            if (bytes.size < HEADER + 32) fail(BackupFailure.CORRUPT_CONTENT)
            val digest = bytes.copyOfRange(HEADER, HEADER + 32)
            val payload = bytes.copyOfRange(HEADER + 32, bytes.size)
            if (!Crypto.constantTimeEquals(digest, Crypto.sha256(payload))) fail(BackupFailure.CHECKSUM_MISMATCH)
            payload
        } else {
            if (passphrase == null || passphrase.isEmpty()) fail(BackupFailure.PASSPHRASE_REQUIRED)
            val p = HEADER
            if (bytes.size < p + 4 + Crypto.SALT_BYTES + Crypto.IV_BYTES + 16) fail(BackupFailure.CORRUPT_CONTENT)
            val iterations = ByteBuffer.wrap(bytes, p, 4).int
            if (iterations !in 10_000..5_000_000) fail(BackupFailure.CORRUPT_CONTENT)
            val salt = bytes.copyOfRange(p + 4, p + 4 + Crypto.SALT_BYTES)
            val iv = bytes.copyOfRange(p + 4 + Crypto.SALT_BYTES, p + 4 + Crypto.SALT_BYTES + Crypto.IV_BYTES)
            val paramsEnd = p + 4 + Crypto.SALT_BYTES + Crypto.IV_BYTES
            val key = Crypto.pbkdf2(passphrase!!, salt, iterations)
            try {
                Crypto.aesGcmDecrypt(key, iv, bytes.copyOfRange(paramsEnd, bytes.size), header + bytes.copyOfRange(p, paramsEnd))
            } catch (e: AEADBadTagException) {
                fail(BackupFailure.WRONG_PASSPHRASE_OR_CORRUPT)
            } finally { key.fill(0) }
        }
        return gunzipLimited(gz)
    }

    /** Reads at most [MAX_FILE_BYTES] from [input]. */
    fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_FILE_BYTES) fail(BackupFailure.TOO_LARGE)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun hasMagic(b: ByteArray) = b.size >= MAGIC.size && MAGIC.indices.all { b[it] == MAGIC[it] }

    private fun gzip(data: ByteArray): ByteArray = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(data) } }.toByteArray()

    private fun gunzipLimited(data: ByteArray): ByteArray = try {
        GZIPInputStream(ByteArrayInputStream(data)).use { gz ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_JSON_BYTES) fail(BackupFailure.TOO_LARGE)
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
    } catch (e: AppException) { throw e } catch (e: Exception) { fail(BackupFailure.CORRUPT_CONTENT) }

    private fun fail(reason: BackupFailure): Nothing = throw AppException(AppError.Backup(reason))
}
