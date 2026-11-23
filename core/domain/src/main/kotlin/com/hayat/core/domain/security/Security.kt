package com.hayat.core.domain.security

import com.hayat.core.common.AppResult
import java.io.InputStream
import java.io.OutputStream
import java.time.Duration
import java.time.Instant

sealed interface PinCheck {
    data object Correct : PinCheck
    data class Wrong(val remainingAttempts: Int) : PinCheck
    data class LockedOut(val until: Instant) : PinCheck
    data object NotSet : PinCheck
}

/** App-lock PIN storage and verification (implemented in :core:security with PBKDF2 + Keystore). */
interface AppLockManager {
    suspend fun hasPin(): Boolean
    suspend fun setPin(pin: CharArray)
    suspend fun verify(pin: CharArray): PinCheck
    suspend fun clearPin()
}

object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8
    const val FREE_ATTEMPTS = 5

    fun isValid(pin: CharArray): Boolean = pin.size in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** Exponential lockout after [FREE_ATTEMPTS] failures: 30 s, 1 min, 2 min, … capped at 1 hour. */
    fun lockoutFor(failedAttempts: Int): Duration {
        if (failedAttempts < FREE_ATTEMPTS) return Duration.ZERO
        val step = (failedAttempts - FREE_ATTEMPTS).coerceAtMost(7)
        return Duration.ofSeconds(30L shl step).coerceAtMost(Duration.ofHours(1))
    }

    private fun Duration.coerceAtMost(max: Duration) = if (this > max) max else this
}

data class BackupPreview(
    val createdAt: Instant,
    val appVersion: String,
    val schemaVersion: Int,
    val encrypted: Boolean,
    /** Table name → row count. */
    val counts: Map<String, Int>,
) {
    val totalRecords: Int get() = counts.values.sum()
}

/** Export/import of all user data (implemented in :core:backup). Streams come from the Storage Access Framework. */
interface BackupService {
    suspend fun export(output: OutputStream, passphrase: CharArray?): AppResult<BackupPreview>
    /** Decodes and validates without changing any data. */
    suspend fun inspect(input: InputStream, passphrase: CharArray?): AppResult<BackupPreview>
    /** Replaces all user data with the backup's content in one transaction, then reschedules reminders. */
    suspend fun restore(input: InputStream, passphrase: CharArray?): AppResult<BackupPreview>
    /** True if the stream looks like an encrypted Hayat backup (so the UI can ask for a passphrase). */
    suspend fun isEncrypted(input: InputStream): Boolean
}
