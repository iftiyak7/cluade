package com.hayat.core.common

/** Outcome of a domain operation. Failures are values, not exceptions. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

/** Typed error hierarchy. Never carries user content in [debugMessage]. */
sealed interface AppError {
    val debugMessage: String

    data class Validation(val errors: List<ValidationError>) : AppError {
        override val debugMessage: String get() = "validation: ${errors.map { it.field + ":" + it.reason }}"
    }
    data class NotFound(val entity: String, val id: String) : AppError {
        override val debugMessage: String get() = "$entity not found ($id)"
    }
    data class Storage(val cause: Throwable? = null) : AppError {
        override val debugMessage: String get() = "storage failure: ${cause?.javaClass?.simpleName}"
    }
    data class Permission(val permission: String) : AppError {
        override val debugMessage: String get() = "missing permission $permission"
    }
    data class Backup(val reason: BackupFailure) : AppError {
        override val debugMessage: String get() = "backup failure $reason"
    }
    data class Unexpected(val cause: Throwable? = null) : AppError {
        override val debugMessage: String get() = "unexpected: ${cause?.javaClass?.simpleName}"
    }
}

enum class BackupFailure {
    NOT_A_HAYAT_BACKUP,
    UNSUPPORTED_FORMAT_VERSION,
    NEWER_SCHEMA,
    WRONG_PASSPHRASE_OR_CORRUPT,
    CHECKSUM_MISMATCH,
    CORRUPT_CONTENT,
    TOO_LARGE,
    PASSPHRASE_REQUIRED,
    IO_ERROR,
}

/** A single field-level validation failure. [reason] is a stable machine key mapped to a localized string in UI. */
data class ValidationError(val field: String, val reason: ValidationReason)

enum class ValidationReason {
    REQUIRED,
    TOO_LONG,
    OUT_OF_RANGE,
    INVALID_FORMAT,
    END_BEFORE_START,
    DUPLICATE,
    EMPTY_SELECTION,
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(value))
    is AppResult.Failure -> this
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value

val AppResult<*>.isSuccess: Boolean get() = this is AppResult.Success

/** Runs [block], converting unexpected exceptions into [AppError]. Cancellation is rethrown. */
suspend inline fun <T> appCatching(crossinline block: suspend () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (e: kotlinx.coroutines.CancellationException) {
    throw e
} catch (e: AppException) {
    AppResult.Failure(e.error)
} catch (e: Exception) {
    AppResult.Failure(AppError.Unexpected(e))
}

/** Exception wrapper used to abort a transaction with a typed error. */
class AppException(val error: AppError) : RuntimeException(error.debugMessage)
