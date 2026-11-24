package com.hayat.core.designsystem

import com.hayat.core.designsystem.component.ringArc
import org.junit.Assert.assertEquals
import org.junit.Test

class RingArcTest {
    @Test fun `visible arc equals the value exactly`() {
        val stroke = 30f; val size = 300f
        listOf(0.01f, 0.1f, 0.25f, 0.42f, 0.5f, 0.83f, 0.99f, 1f).forEach { p ->
            val a = ringArc(p, stroke, size)
            assertEquals("p=$p", 360f * p, a.visibleDegrees(stroke, size), 0.01f)
        }
    }

    @Test fun `42 percent no longer looks like 36 or 48`() {
        val a = ringArc(0.42f, 33f, 324f) // 108 dp ring, 11 dp stroke at 3x density
        assertEquals(151.2f, a.visibleDegrees(33f, 324f), 0.01f)
    }
}
