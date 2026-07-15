package org.graphiks.math.color

import kotlin.test.Test
import kotlin.test.assertEquals

class ColorARGBTest {
    @Test
    fun `pack and unpack channels`() {
        val c = ColorARGB.of(0x80, 0x12, 0x34, 0x56)
        assertEquals(0x80, c.alpha)
        assertEquals(0x12, c.red)
        assertEquals(0x34, c.green)
        assertEquals(0x56, c.blue)
    }

    @Test
    fun `colorRGB sets alpha to 0xFF`() {
        val c = ColorARGB.of(0x12, 0x34, 0x56)
        assertEquals(0xFF, c.alpha)
        assertEquals(0x12, c.red)
        assertEquals(0x34, c.green)
        assertEquals(0x56, c.blue)
    }

    @Test
    fun `withAlpha replaces alpha`() {
        val c = ColorARGB.of(0xFF, 0x10, 0x20, 0x30)
        val d = c.withAlpha(0x80)
        assertEquals(0x80, d.alpha)
        assertEquals(0x10, d.red)
        assertEquals(0x20, d.green)
        assertEquals(0x30, d.blue)
    }

    @Test
    fun `companion constants`() {
        assertEquals(0x00000000u, ColorARGB.Transparent.value)
        assertEquals(0xFF000000u, ColorARGB.Black.value)
        assertEquals(0xFFFFFFFFu, ColorARGB.White.value)
        assertEquals(0xFFFF0000u, ColorARGB.Red.value)
        assertEquals(0xFF00FF00u, ColorARGB.Green.value)
        assertEquals(0xFF0000FFu, ColorARGB.Blue.value)
    }

    @Test
    fun `premultiply opaque is identity`() {
        val c = ColorARGB.of(0xFF, 0x80, 0x40, 0x20)
        val pm = c.premultiplied()
        assertEquals(c, pm)
    }

    @Test
    fun `premultiply transparent is zero`() {
        val c = ColorARGB.of(0x00, 0x80, 0x40, 0x20)
        val pm = c.premultiplied()
        assertEquals(ColorARGB.Transparent, pm)
    }

    @Test
    fun `premultiply and unpremultiply roundtrip`() {
        val c = ColorARGB.of(0x80, 0x80, 0x40, 0x20)
        val pm = c.premultiplied()
        val round = pm.unpremultiplied()
        assertEquals(c.alpha, round.alpha)
    }

    @Test
    fun `multiply alpha 255`() {
        assertEquals(128, multiplyAlpha255(255, 128))
        assertEquals(0, multiplyAlpha255(0, 128))
        assertEquals(255, multiplyAlpha255(255, 255))
    }

    @Test
    fun `multiplyAlpha32 does not overflow`() {
        assertEquals(65535, multiplyAlpha32(65535, 65535))
    }

    @Test
    fun `hsv to color produces expected values`() {
        val red = hsvToColor(0f, 1f, 1f)
        assertEquals(ColorARGB.Red, red)

        val green = hsvToColor(120f, 1f, 1f)
        assertEquals(ColorARGB.Green, green)

        val blue = hsvToColor(240f, 1f, 1f)
        assertEquals(ColorARGB.Blue, blue)
    }

    @Test
    fun `hsv with zero saturation produces gray`() {
        val gray = hsvToColor(0f, 0f, 0.5f)
        assertEquals(gray.red, gray.green)
        assertEquals(gray.green, gray.blue)
    }

    @Test
    fun `color to hsv roundtrip`() {
        val c = ColorARGB.of(0xFF, 0x80, 0x40, 0x20)
        val hsv = FloatArray(3)
        colorToHSV(c, hsv)
        val back = hsvToColor(hsv[0], hsv[1], hsv[2])
        val hsv2 = FloatArray(3)
        colorToHSV(back, hsv2)
        assertEquals(hsv[2], hsv2[2], 0.01f)
    }

    @Test
    fun `hsvToColor with alpha`() {
        val c = hsvToColor(0x80, 0f, 1f, 1f)
        assertEquals(0x80, c.alpha)
    }

    @Test
    fun `hsv out of range hue treats as zero`() {
        val red = ColorARGB.of(0xFF, 0xFF, 0x00, 0x00)
        assertEquals(red, hsvToColor(-60f, 1f, 1f))
        assertEquals(red, hsvToColor(360f, 1f, 1f))
        assertEquals(red, hsvToColor(420f, 1f, 1f))
    }
}
