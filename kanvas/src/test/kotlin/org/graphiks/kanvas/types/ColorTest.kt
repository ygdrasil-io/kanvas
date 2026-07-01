package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class ColorTest {
    @Test
    fun `Color packed stores ARGB as UInt`() {
        val c = Color(0xFFFF0000u)
        assertEquals(0xFFFF0000u, c.packed)
    }

    @Test
    fun `Color r g b a extract components`() {
        val c = Color(0xFF112233u)
        assertEquals(0x11.toFloat() / 255f, c.r, 0.01f)
        assertEquals(0x22.toFloat() / 255f, c.g, 0.01f)
        assertEquals(0x33.toFloat() / 255f, c.b, 0.01f)
        assertEquals(1f, c.a, 0.01f)
    }

    @Test
    fun `Color alpha extraction`() {
        val c = Color(0x80FF0000u)
        assertEquals(0x80.toFloat() / 255f, c.a, 0.01f)
    }

    @Test
    fun `Color companion constants`() {
        assertEquals(Color(0xFF000000u), Color.BLACK)
        assertEquals(Color(0xFFFFFFFFu), Color.WHITE)
        assertEquals(Color(0xFFFF0000u), Color.RED)
        assertEquals(Color(0xFF00FF00u), Color.GREEN)
        assertEquals(Color(0xFF0000FFu), Color.BLUE)
        assertEquals(Color(0x00000000u), Color.TRANSPARENT)
    }

    @Test
    fun `Color fromRGBA factory`() {
        val c = Color.fromRGBA(0.5f, 0.25f, 0.75f, 0.8f)
        assertEquals(0.5f, c.r, 0.01f)
        assertEquals(0.25f, c.g, 0.01f)
        assertEquals(0.75f, c.b, 0.01f)
        assertEquals(0.8f, c.a, 0.01f)
    }
}
