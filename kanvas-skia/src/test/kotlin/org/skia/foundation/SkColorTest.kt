package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkColorTest {

    @Test
    fun `SkColorSetARGB packs four bytes`() {
        val c = SkColorSetARGB(0x12, 0x34, 0x56, 0x78)
        assertEquals(0x12345678, c)
        assertEquals(0x12, SkColorGetA(c))
        assertEquals(0x34, SkColorGetR(c))
        assertEquals(0x56, SkColorGetG(c))
        assertEquals(0x78, SkColorGetB(c))
    }

    @Test
    fun `SkColorSetA replaces alpha keeping RGB`() {
        val c = SK_ColorRED                     // 0xFFFF0000
        val a = SkColorSetA(c, 0x80)
        assertEquals(0x80, SkColorGetA(a))
        assertEquals(0xFF, SkColorGetR(a))
        assertEquals(0x00, SkColorGetG(a))
        assertEquals(0x00, SkColorGetB(a))
    }

    @Test
    fun `alpha constants are 0 and 255`() {
        assertEquals(0x00, SK_AlphaTRANSPARENT)
        assertEquals(0xFF, SK_AlphaOPAQUE)
    }

    @Test
    fun `SkRGBToHSV cardinals`() {
        val hsv = FloatArray(3)

        SkRGBToHSV(0, 0, 0, hsv)
        assertEquals(0f, hsv[0]); assertEquals(0f, hsv[1]); assertEquals(0f, hsv[2])

        SkRGBToHSV(255, 255, 255, hsv)
        assertEquals(0f, hsv[1]); assertEquals(1f, hsv[2])

        SkRGBToHSV(255, 0, 0, hsv)
        assertEquals(0f, hsv[0], 1e-3f); assertEquals(1f, hsv[1]); assertEquals(1f, hsv[2])

        SkRGBToHSV(0, 255, 0, hsv)
        assertEquals(120f, hsv[0], 1e-3f); assertEquals(1f, hsv[1]); assertEquals(1f, hsv[2])

        SkRGBToHSV(0, 0, 255, hsv)
        assertEquals(240f, hsv[0], 1e-3f); assertEquals(1f, hsv[1]); assertEquals(1f, hsv[2])
    }

    @Test
    fun `SkHSVToColor cardinals round-trip`() {
        val cases = listOf(
            SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE,
            SK_ColorYELLOW, SK_ColorCYAN, SK_ColorMAGENTA, SK_ColorWHITE,
        )
        val hsv = FloatArray(3)
        for (c in cases) {
            SkColorToHSV(c, hsv)
            val round = SkHSVToColor(0xFF, hsv)
            assertEquals(c, round, "round-trip failed for ${c.toUInt().toString(16)}")
        }
    }

    @Test
    fun `SkHSVToColor pins hue mod 360`() {
        // H = 480 should fold to 120 (green), S = V = 1.
        val hsv = floatArrayOf(480f, 1f, 1f)
        assertEquals(SK_ColorGREEN, SkHSVToColor(0xFF, hsv))
    }

    @Test
    fun `SkHSVToColor pins saturation and value to 0-to-1`() {
        // Out-of-range S, V get clamped.
        val hsv = floatArrayOf(0f, 2f, 2f)    // saturated red, but clamped to 1, 1
        assertEquals(SK_ColorRED, SkHSVToColor(0xFF, hsv))
    }

    @Test
    fun `SkPreMultiplyARGB scales RGB by alpha`() {
        // Opaque: identical to source.
        assertEquals(SkColorSetARGB(0xFF, 0x80, 0x40, 0x20),
            SkPreMultiplyARGB(0xFF, 0x80, 0x40, 0x20))
        // Half alpha: each channel ~halved (with rounding).
        val half = SkPreMultiplyARGB(0x80, 0xFF, 0xFF, 0xFF)
        assertEquals(0x80, SkColorGetA(half))
        assertEquals(0x80, SkColorGetR(half), "R should ~ 128")
        // Zero alpha: all RGB go to 0.
        assertEquals(0, SkPreMultiplyARGB(0, 0xFF, 0xFF, 0xFF) and 0x00FFFFFF)
    }

    @Test
    fun `SkPreMultiplyColor delegates to ARGB version`() {
        val c = SkColorSetARGB(0x80, 0xFF, 0xFF, 0xFF)
        assertEquals(SkPreMultiplyARGB(0x80, 0xFF, 0xFF, 0xFF), SkPreMultiplyColor(c))
    }

    @Test
    fun `colorToRGB565 quantizes to 5-6-5 bits`() {
        // Pure white stays white (all bits set).
        assertEquals(SK_ColorWHITE, colorToRGB565(SK_ColorWHITE))
        // Red channel quantizes to 248 (lower 3 bits cleared, top 3 replicated).
        val red = colorToRGB565(SK_ColorRED)
        // 0xFF -> 0xF8 | (0xF8 >> 5) = 0xF8 | 7 = 0xFF
        assertEquals(0xFF, SkColorGetR(red))
    }

    @Test
    fun `channel flags are powers of two`() {
        assertEquals(1, SkColorChannelFlag.kRed_SkColorChannelFlag)
        assertEquals(2, SkColorChannelFlag.kGreen_SkColorChannelFlag)
        assertEquals(4, SkColorChannelFlag.kBlue_SkColorChannelFlag)
        assertEquals(8, SkColorChannelFlag.kAlpha_SkColorChannelFlag)
        assertEquals(15, SkColorChannelFlag.kRGBA_SkColorChannelFlags)
    }
}
