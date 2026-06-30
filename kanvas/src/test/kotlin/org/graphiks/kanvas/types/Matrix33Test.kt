package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class Matrix33Test {
    @Test
    fun `identity matrix`() {
        val m = Matrix33.identity()
        assertEquals(1f, m.scaleX)
        assertEquals(0f, m.skewX)
        assertEquals(0f, m.transX)
        assertEquals(0f, m.skewY)
        assertEquals(1f, m.scaleY)
        assertEquals(0f, m.transY)
        assertEquals(0f, m.persp0)
        assertEquals(0f, m.persp1)
        assertEquals(1f, m.persp2)
    }

    @Test
    fun `translate matrix`() {
        val m = Matrix33.translate(10f, 20f)
        assertEquals(1f, m.scaleX)
        assertEquals(0f, m.skewX)
        assertEquals(10f, m.transX)
        assertEquals(0f, m.skewY)
        assertEquals(1f, m.scaleY)
        assertEquals(20f, m.transY)
    }

    @Test
    fun `scale matrix`() {
        val m = Matrix33.scale(2f, 3f)
        assertEquals(2f, m.scaleX)
        assertEquals(3f, m.scaleY)
        assertEquals(0f, m.transX)
        assertEquals(0f, m.transY)
    }

    @Test
    fun `rotate matrix`() {
        val m = Matrix33.rotate(90f)
        assertEquals(0f, m.scaleX, 0.001f)
        assertEquals(0f, m.scaleY, 0.001f)
        assertEquals(-1f, m.skewX, 0.001f)
        assertEquals(1f, m.skewY, 0.001f)
    }

    @Test
    fun `matrix multiply by point`() {
        val m = Matrix33.translate(10f, 20f)
        val p = m * Point(5f, 5f)
        assertEquals(15f, p.x)
        assertEquals(25f, p.y)
    }

    @Test
    fun `matrix multiply by matrix`() {
        val t = Matrix33.translate(10f, 0f)
        val s = Matrix33.scale(2f, 2f)
        val m = t * s
        val p = m * Point(5f, 5f)
        assertEquals(20f, p.x)
        assertEquals(10f, p.y)
    }
}
