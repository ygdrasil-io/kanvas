package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorF32Test {
    @Test
    fun `hsvToColor treats out-of-range hue as zero instead of wrapping`() {
        val red = ColorARGB.of(0x80, 0xFF, 0x00, 0x00)
        assertEquals(red, hsvToColor(0x80, -60f, 1f, 1f))
        assertEquals(red, hsvToColor(0x80, 360f, 1f, 1f))
        assertEquals(red, hsvToColor(0x80, 420f, 1f, 1f))
    }

    @Test
    fun `toBytes_RGBA places R in LSB and A in MSB`() {
        val rOnly = ColorF32(1f, 0f, 0f, 0f).toBytes_RGBA()
        assertEquals(0xFF, rOnly and 0xFF)
        assertEquals(0x00, (rOnly ushr 8) and 0xFF)
        assertEquals(0x00, (rOnly ushr 16) and 0xFF)
        assertEquals(0x00, (rOnly ushr 24) and 0xFF)

        val gOnly = ColorF32(0f, 1f, 0f, 0f).toBytes_RGBA()
        assertEquals(0x00, gOnly and 0xFF)
        assertEquals(0xFF, (gOnly ushr 8) and 0xFF)
        assertEquals(0x00, (gOnly ushr 16) and 0xFF)
        assertEquals(0x00, (gOnly ushr 24) and 0xFF)

        val bOnly = ColorF32(0f, 0f, 1f, 0f).toBytes_RGBA()
        assertEquals(0x00, bOnly and 0xFF)
        assertEquals(0x00, (bOnly ushr 8) and 0xFF)
        assertEquals(0xFF, (bOnly ushr 16) and 0xFF)
        assertEquals(0x00, (bOnly ushr 24) and 0xFF)

        val aOnly = ColorF32(0f, 0f, 0f, 1f).toBytes_RGBA()
        assertEquals(0x00, aOnly and 0xFF)
        assertEquals(0x00, (aOnly ushr 8) and 0xFF)
        assertEquals(0x00, (aOnly ushr 16) and 0xFF)
        assertEquals(0xFF, (aOnly ushr 24) and 0xFF)
    }

    @Test
    fun `toBytes_RGBA exact bit layout`() {
        val packed = ColorF32(0.5f, 0.25f, 0.125f, 1f).toBytes_RGBA()
        assertEquals(0xFF204080.toInt(), packed)
    }

    @Test
    fun `fromBytes_RGBA reads bytes in RGBA order`() {
        val c = ColorF32.fromBytes_RGBA(0x44332211)
        assertEquals(0x11 / 255f, c.red, 1e-6f)
        assertEquals(0x22 / 255f, c.green, 1e-6f)
        assertEquals(0x33 / 255f, c.blue, 1e-6f)
        assertEquals(0x44 / 255f, c.alpha, 1e-6f)
    }

    @Test
    fun `roundtrip toBytes_RGBA fromBytes_RGBA`() {
        val samples = listOf(
            ColorF32(0f, 0f, 0f, 0f),
            ColorF32(1f, 1f, 1f, 1f),
            ColorF32(0.25f, 0.5f, 0.75f, 1f),
            ColorF32(0.5f, 0.25f, 0.125f, 1f),
            ColorF32(1f, 0f, 0f, 1f),
            ColorF32(0f, 1f, 0f, 1f),
            ColorF32(0f, 0f, 1f, 1f),
            ColorF32(0.123f, 0.456f, 0.789f, 0.5f),
        )
        for (c in samples) {
            val round = ColorF32.fromBytes_RGBA(c.toBytes_RGBA())
            assertEquals(c.red, round.red, 1f / 255f, "red for $c")
            assertEquals(c.green, round.green, 1f / 255f, "green for $c")
            assertEquals(c.blue, round.blue, 1f / 255f, "blue for $c")
            assertEquals(c.alpha, round.alpha, 1f / 255f, "alpha for $c")
        }
    }

    @Test
    fun `roundtrip fromBytes_RGBA toBytes_RGBA preserves raw int`() {
        val packedSamples = intArrayOf(
            0x00000000,
            0xFFFFFFFF.toInt(),
            0xFF0000FF.toInt(),
            0xFF00FF00.toInt(),
            0xFFFF0000.toInt(),
            0xDEADBEEF.toInt(),
            0x12345678,
        )
        for (p in packedSamples) {
            val back = ColorF32.fromBytes_RGBA(p).toBytes_RGBA()
            assertEquals(p, back, "raw uint32 0x${p.toString(16)}")
        }
    }

    @Test
    fun `isOpaque is true when alpha is 1`() {
        assertTrue(ColorF32.White.isOpaque)
        assertFalse(ColorF32.Transparent.isOpaque)
    }

    @Test
    fun `toColorARGB conversion`() {
        val c = ColorF32(1f, 0f, 0f, 1f)
        assertEquals(ColorARGB.Red, c.toColorARGB())
    }

    @Test
    fun `premultiplied and unpremultiplied`() {
        val c = ColorF32(1f, 0.5f, 0.25f, 0.5f)
        val pm = c.premultiplied()
        assertEquals(c.red * c.alpha, pm.red, 1e-6f)
        assertEquals(c.green * c.alpha, pm.green, 1e-6f)
        assertEquals(c.blue * c.alpha, pm.blue, 1e-6f)
        assertEquals(c.alpha, pm.alpha, 1e-6f)

        val round = pm.unpremultiplied()
        assertEquals(c.red, round.red, 1e-6f)
        assertEquals(c.green, round.green, 1e-6f)
        assertEquals(c.blue, round.blue, 1e-6f)
    }

    @Test
    fun `unpremultiplied with zero alpha returns zero`() {
        val c = ColorF32(1f, 1f, 1f, 0f)
        val r = c.unpremultiplied()
        assertEquals(0f, r.red)
        assertEquals(0f, r.green)
        assertEquals(0f, r.blue)
        assertEquals(0f, r.alpha)
    }

    @Test
    fun `fromColorARGB`() {
        val c = ColorF32.fromColorARGB(ColorARGB.Red)
        assertEquals(1f, c.red, 1e-6f)
        assertEquals(0f, c.green, 1e-6f)
        assertEquals(0f, c.blue, 1e-6f)
        assertEquals(1f, c.alpha, 1e-6f)
    }

    @Test
    fun `companion constants`() {
        assertEquals(0f, ColorF32.Transparent.alpha)
        assertEquals(0f, ColorF32.Black.red)
        assertEquals(1f, ColorF32.White.red)
        assertEquals(1f, ColorF32.Red.red)
        assertEquals(0f, ColorF32.Red.green)
    }

    @Test
    fun `component wise times and plus`() {
        val a = ColorF32(1f, 0.5f, 0.25f, 1f)
        val b = ColorF32(0.5f, 0.5f, 0.5f, 0.5f)
        val prod = a * b
        assertEquals(0.5f, prod.red, 1e-6f)
        assertEquals(0.25f, prod.green, 1e-6f)

        val sum = a + b
        assertEquals(1.5f, sum.red, 1e-6f)
    }

    @Test
    fun `scalar times color`() {
        val c = ColorF32(0.5f, 0.25f, 0.125f, 0.5f)
        val scaled = 2f * c
        assertEquals(1f, scaled.red, 1e-6f)
    }

    @Test
    fun `fromBytesRGBA`() {
        val c = ColorF32.fromBytesRGBA(0xFF.toByte(), 0x80.toByte(), 0x00.toByte())
        assertEquals(1f, c.red, 1f / 255f)
        assertEquals(0x80 / 255f, c.green, 1f / 255f)
        assertEquals(0f, c.blue, 1f / 255f)
        assertEquals(1f, c.alpha, 1f / 255f)
    }

    @Test
    fun `clampToFit`() {
        val c = ColorF32(2f, -1f, 0.5f, 1.5f).clampToFit()
        assertEquals(1f, c.red, 1e-6f)
        assertEquals(0f, c.green, 1e-6f)
        assertEquals(0.5f, c.blue, 1e-6f)
        assertEquals(1f, c.alpha, 1e-6f)
    }
}
