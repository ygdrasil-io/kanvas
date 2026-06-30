package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

class RectTest {
    @Test
    fun `Rect fromLTRB`() {
        val r = Rect.fromLTRB(1f, 2f, 11f, 12f)
        assertEquals(1f, r.left)
        assertEquals(2f, r.top)
        assertEquals(11f, r.right)
        assertEquals(12f, r.bottom)
    }

    @Test
    fun `Rect fromXYWH`() {
        val r = Rect.fromXYWH(5f, 10f, 100f, 200f)
        assertEquals(5f, r.left)
        assertEquals(10f, r.top)
        assertEquals(105f, r.right)
        assertEquals(210f, r.bottom)
    }

    @Test
    fun `Rect computed properties`() {
        val r = Rect.fromLTRB(0f, 0f, 100f, 50f)
        assertEquals(100f, r.width, 0.01f)
        assertEquals(50f, r.height, 0.01f)
        assertEquals(Point(50f, 25f), r.center)
        assertFalse(r.isEmpty)
    }

    @Test
    fun `Rect isEmpty`() {
        assertTrue(Rect.EMPTY.isEmpty)
        assertTrue(Rect.fromLTRB(10f, 10f, 10f, 20f).isEmpty)
        assertTrue(Rect.fromLTRB(10f, 10f, 20f, 10f).isEmpty)
    }

    @Test
    fun `Rect contains point`() {
        val r = Rect.fromLTRB(0f, 0f, 100f, 100f)
        assertTrue(Point(50f, 50f) in r)
        assertFalse(Point(150f, 50f) in r)
    }
}
