package com.hayat.core.common

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonTest {
    @Test
    fun `validation collects required and length errors`() {
        val errors = validate {
            text("   ", "title")
            text("x".repeat(300), "description", required = false, maxLength = 200)
            range(25, "hour", 0.0, 23.0)
        }
        assertEquals(
            listOf(
                ValidationError("title", ValidationReason.REQUIRED),
                ValidationError("description", ValidationReason.TOO_LONG),
                ValidationError("hour", ValidationReason.OUT_OF_RANGE),
            ),
            errors,
        )
    }

    @Test
    fun `escapeLike escapes wildcards`() {
        assertEquals("50\\% \\_off\\\\", "50% _off\\".escapeLike())
        assertEquals("পানি পান", "  পানি   পান ".normalizeQuery())
    }

    @Test
    fun `appCatching converts exceptions`() = runTest {
        val r = appCatching<Int> { throw IllegalStateException("boom") }
        assertTrue(r is AppResult.Failure && r.error is AppError.Unexpected)
        val typed = appCatching<Int> { throw AppException(AppError.NotFound("Task", "1")) }
        assertEquals(AppResult.Failure(AppError.NotFound("Task", "1")), typed)
    }
}
