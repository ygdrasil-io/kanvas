package org.graphiks.kanvas.types

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class ColorTest {

    @Test
    fun `fromRGBA uses round-half-up quantization`() {
        val c = Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f)
        assertEquals(128, ((c.packed shr 16) and 0xFFu).toInt(), "R channel must be 128 (round-half-up)")
        assertEquals(128, ((c.packed shr 8) and 0xFFu).toInt(), "G channel must be 128 (round-half-up)")
        assertEquals(128, (c.packed and 0xFFu).toInt(), "B channel must be 128 (round-half-up)")
    }

    @Test
    fun `fromRGBA half value maps to middle-gray`() {
        val c = Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f)
        assertTrue(0.501f <= c.r && c.r <= 0.503f, "r=$c.r should be ~0.502")
        assertTrue(0.501f <= c.g && c.g <= 0.503f, "g=$c.g should be ~0.502")
        assertTrue(0.501f <= c.b && c.b <= 0.503f, "b=$c.b should be ~0.502")
    }
    @Test
    fun `Color packed stores ARGB as UInt`() {
        val c = Color(0xFFFF0000u)
        assertEquals(0xFFFF0000u, c.packed)
    }

    @Test
    fun `Color converts to and from ARGB Int without changing bits`() {
        val argb = 0x8044AA11.toInt()
        val color = Color.fromArgbInt(argb)

        assertEquals(argb, color.toArgbInt())
        assertEquals(0x8044AA11u, color.packed)
    }

    @Test
    fun `Color constructs from byte channels and exposes byte channels`() {
        val color = Color.fromArgb(0x80, 0x11, 0x22, 0x33)

        assertEquals(0x80, color.alphaByte)
        assertEquals(0x11, color.redByte)
        assertEquals(0x22, color.greenByte)
        assertEquals(0x33, color.blueByte)
        assertEquals(0x80112233.toInt(), color.toArgbInt())
    }

    @Test
    fun `Color can replace alpha byte without changing rgb`() {
        val color = Color.fromArgb(0x80, 0x11, 0x22, 0x33)

        assertEquals(Color.fromArgb(0xFE, 0x11, 0x22, 0x33), color.withAlphaByte(0xFE))
        assertTrue(Color.WHITE.isOpaque)
        assertTrue(!Color.TRANSPARENT.isOpaque)
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
