package com.hayat.core.common

/** Small builder for collecting field errors in validators. */
class ValidationScope {
    private val errors = mutableListOf<ValidationError>()

    fun require(condition: Boolean, field: String, reason: ValidationReason) {
        if (!condition) errors += ValidationError(field, reason)
    }

    fun text(value: String?, field: String, required: Boolean = true, maxLength: Int = 200) {
        val trimmed = value?.trim().orEmpty()
        if (required && trimmed.isEmpty()) errors += ValidationError(field, ValidationReason.REQUIRED)
        if (trimmed.length > maxLength) errors += ValidationError(field, ValidationReason.TOO_LONG)
    }

    fun range(value: Number?, field: String, min: Double, max: Double, required: Boolean = false) {
        if (value == null) {
            if (required) errors += ValidationError(field, ValidationReason.REQUIRED)
            return
        }
        val v = value.toDouble()
        if (v.isNaN() || v < min || v > max) errors += ValidationError(field, ValidationReason.OUT_OF_RANGE)
    }

    fun result(): List<ValidationError> = errors.toList()
}

inline fun validate(block: ValidationScope.() -> Unit): List<ValidationError> =
    ValidationScope().apply(block).result()

/** Converts a list of errors into a failure, or returns null when valid. */
fun List<ValidationError>.toFailureOrNull(): AppResult.Failure? =
    if (isEmpty()) null else AppResult.Failure(AppError.Validation(this))

object Limits {
    const val TITLE_MAX = 120
    const val SHORT_TEXT_MAX = 200
    const val DESCRIPTION_MAX = 2_000
    const val NOTE_BODY_MAX = 50_000
}
