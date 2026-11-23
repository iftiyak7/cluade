package com.hayat.core.security

import android.content.Context
import android.util.AtomicFile
import com.hayat.core.common.TimeProvider
import com.hayat.core.domain.security.AppLockManager
import com.hayat.core.domain.security.PinCheck
import com.hayat.core.domain.security.PinPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Binary record persisted for the app-lock PIN. The hash is wrapped by [SecretWrapper]. */
internal data class PinRecord(val iterations: Int, val salt: ByteArray, val wrappedHash: ByteArray, val failedAttempts: Int, val lockoutUntilMillis: Long)

/** Storage abstraction so the policy can be tested without Android files. */
internal interface PinRecordStore {
    fun read(): PinRecord?
    fun write(record: PinRecord?)
}

internal class AtomicFilePinStore(file: File) : PinRecordStore {
    private val atomic = AtomicFile(file)
    override fun read(): PinRecord? = runCatching {
        if (!atomic.baseFile.exists()) return null
        DataInputStream(atomic.openRead()).use { i ->
            require(i.readInt() == MAGIC)
            val it = i.readInt()
            val salt = ByteArray(i.readInt()).also(i::readFully)
            val hash = ByteArray(i.readInt()).also(i::readFully)
            PinRecord(it, salt, hash, i.readInt(), i.readLong())
        }
    }.getOrNull()

    override fun write(record: PinRecord?) {
        if (record == null) { atomic.delete(); return }
        val out = atomic.startWrite()
        try {
            DataOutputStream(out).apply {
                writeInt(MAGIC); writeInt(record.iterations)
                writeInt(record.salt.size); write(record.salt)
                writeInt(record.wrappedHash.size); write(record.wrappedHash)
                writeInt(record.failedAttempts); writeLong(record.lockoutUntilMillis)
                flush()
            }
            atomic.finishWrite(out)
        } catch (e: Exception) { atomic.failWrite(out); throw e }
    }

    private companion object { const val MAGIC = 0x48504E31 } // "HPN1"
}

/**
 * PIN verification: PBKDF2-HMAC-SHA256 (120k iterations) + device-bound wrapping + exponential
 * lockout persisted across restarts. The PIN itself is never stored or logged.
 */
internal class PinLockCore(
    private val store: PinRecordStore,
    private val wrapper: SecretWrapper,
    private val time: TimeProvider,
    private val iterations: Int = ITERATIONS,
) {
    private val mutex = Mutex()

    suspend fun hasPin(): Boolean = mutex.withLock { store.read() != null }

    suspend fun setPin(pin: CharArray) = mutex.withLock {
        require(PinPolicy.isValid(pin)) { "invalid pin" }
        val salt = Crypto.randomBytes(Crypto.SALT_BYTES)
        val hash = Crypto.pbkdf2(pin, salt, iterations)
        store.write(PinRecord(iterations, salt, wrapper.wrap(hash), 0, 0))
        hash.fill(0)
    }

    suspend fun clearPin() = mutex.withLock { store.write(null) }

    suspend fun verify(pin: CharArray): PinCheck = mutex.withLock {
        val rec = store.read() ?: return@withLock PinCheck.NotSet
        val now = time.now().toEpochMilli()
        if (rec.lockoutUntilMillis > now) return@withLock PinCheck.LockedOut(Instant.ofEpochMilli(rec.lockoutUntilMillis))
        val expected = runCatching { wrapper.unwrap(rec.wrappedHash) }.getOrNull()
            ?: return@withLock PinCheck.Wrong(0) // device key unavailable: PIN cannot be verified
        val actual = Crypto.pbkdf2(pin, rec.salt, rec.iterations)
        val ok = Crypto.constantTimeEquals(expected, actual)
        expected.fill(0); actual.fill(0)
        if (ok) {
            if (rec.failedAttempts != 0) store.write(rec.copy(failedAttempts = 0, lockoutUntilMillis = 0))
            PinCheck.Correct
        } else {
            val failed = rec.failedAttempts + 1
            val lock = PinPolicy.lockoutFor(failed)
            store.write(rec.copy(failedAttempts = failed, lockoutUntilMillis = if (lock.isZero) 0 else now + lock.toMillis()))
            if (lock.isZero) PinCheck.Wrong((PinPolicy.FREE_ATTEMPTS - failed).coerceAtLeast(0)) else PinCheck.LockedOut(Instant.ofEpochMilli(now + lock.toMillis()))
        }
    }

    companion object { const val ITERATIONS = 120_000 }
}

@Singleton
class PinLockManager internal constructor(private val core: PinLockCore) : AppLockManager {
    @Inject constructor(@ApplicationContext context: Context, wrapper: SecretWrapper, time: TimeProvider) :
        this(PinLockCore(AtomicFilePinStore(File(context.noBackupFilesDir, "app_lock.bin")), wrapper, time))

    override suspend fun hasPin() = withContext(Dispatchers.IO) { core.hasPin() }
    override suspend fun setPin(pin: CharArray) = withContext(Dispatchers.Default) { core.setPin(pin) }
    override suspend fun verify(pin: CharArray) = withContext(Dispatchers.Default) { core.verify(pin) }
    override suspend fun clearPin() = withContext(Dispatchers.IO) { core.clearPin() }
}
