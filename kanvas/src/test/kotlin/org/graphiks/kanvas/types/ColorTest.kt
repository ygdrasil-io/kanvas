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
        assertEquals(0x11f / 255f, c.r, 0.01f)
        assertEquals(0x22f / 255f, c.g, 0.01f)
        assertEquals(0x33f / 255f, c.b, 0.01f)
        assertEquals(1f, c.a, 0.01f)
    }

    @Test
    fun `Color alpha extraction`() {
        val c = Color(0x80FF0000u)
        assertEquals(0x80f / 255f, c.a, 0.01f)
    }

    @Test
    fun `Color constants`() {
        assertEquals(Color(0xFF000000u), BLACK)
        assertEquals(Color(0xFFFFFFFFu), WHITE)
        assertEquals(Color(0xFFFF0000u), RED)
        assertEquals(Color(0xFF00FF00u), GREEN)
        assertEquals(Color(0xFF0000FFu), BLUE)
        assertEquals(Color(0x00000000u), TRANSPARENT)
    }

    @Test
    fun `Color fromRGBA factory`() {
        val c = fromRGBA(0.5f, 0.25f, 0.75f, 0.8f)
        assertEquals(0.5f, c.r, 0.01f)
        assertEquals(0.25f, c.g, 0.01f)
        assertEquals(0.75f, c.b, 0.01f)
        assertEquals(0.8f, c.a, 0.01f)
    }
}
