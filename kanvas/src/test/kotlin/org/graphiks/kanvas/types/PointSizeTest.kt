package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class PointSizeTest {
    @Test
    fun `Point data class`() {
        val p = Point(3f, 4f)
        assertEquals(3f, p.x)
        assertEquals(4f, p.y)
    }

    @Test
    fun `Point ZERO`() {
        assertEquals(Point(0f, 0f), Point.ZERO)
    }

    @Test
    fun `Point copy`() {
        val p = Point(1f, 2f).copy(x = 5f)
        assertEquals(Point(5f, 2f), p)
    }

    @Test
    fun `Size data class`() {
        val s = Size(100f, 50f)
        assertEquals(100f, s.width)
        assertEquals(50f, s.height)
    }
}
