package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkColor4fTest {

    @Test
    fun `vec returns RGBA tuple in order`() {
        val c = SkColor4f(0.1f, 0.2f, 0.3f, 0.4f)
        val v = c.vec()
        assertEquals(0.1f, v[0])
        assertEquals(0.2f, v[1])
        assertEquals(0.3f, v[2])
        assertEquals(0.4f, v[3])
    }

    @Test
    fun `kBlack and kWhite have expected channel values`() {
        assertEquals(SkColor4f(0f, 0f, 0f, 1f), SkColor4f.kBlack)
        assertEquals(SkColor4f(1f, 1f, 1f, 1f), SkColor4f.kWhite)
        assertEquals(SkColor4f(0f, 0f, 0f, 0f), SkColor4f.kTransparent)
    }

    @Test
    fun `fitsInBytes accepts in-range channels`() {
        assertTrue(SkColor4f(0f, 0.5f, 1f, 1f).fitsInBytes())
    }

    @Test
    fun `fitsInBytes rejects out-of-range channels`() {
        assertFalse(SkColor4f(-0.01f, 0.5f, 1f, 1f).fitsInBytes())
        assertFalse(SkColor4f(0f, 0.5f, 1.0001f, 1f).fitsInBytes())
    }

    @Test
    fun `toSkColor encodes pure primaries to expected bytes`() {
        assertEquals(SK_ColorRED, SkColor4f.kRed.toSkColor())
        assertEquals(SK_ColorGREEN, SkColor4f.kGreen.toSkColor())
        assertEquals(SK_ColorBLUE, SkColor4f.kBlue.toSkColor())
        assertEquals(SK_ColorWHITE, SkColor4f.kWhite.toSkColor())
        assertEquals(SK_ColorBLACK, SkColor4f.kBlack.toSkColor())
        assertEquals(SK_ColorTRANSPARENT, SkColor4f.kTransparent.toSkColor())
    }

    @Test
    fun `toSkColor clamps out-of-range channels`() {
        val c = SkColor4f(2f, -1f, 0.5f, 1f)
        val packed = c.toSkColor()
        assertEquals(0xFF, SkColorGetA(packed))
        assertEquals(0xFF, SkColorGetR(packed))
        assertEquals(0x00, SkColorGetG(packed))
        assertEquals(0x80, SkColorGetB(packed)) // round(127.5 + 0.5) = 128
    }

    @Test
    fun `toSkColor rounds half up`() {
        // 0.5 * 255 = 127.5 -> +0.5 floor = 128
        val v = SkColor4f(0.5f, 0.5f, 0.5f, 1f).toSkColor()
        assertEquals(0x80, SkColorGetR(v))
        assertEquals(0x80, SkColorGetG(v))
        assertEquals(0x80, SkColorGetB(v))
    }

    @Test
    fun `FromColor decodes packed ARGB to normalised floats`() {
        val packed = SkColorSetARGB(0xFF, 0xFF, 0x80, 0x00)
        val c = SkColor4f.FromColor(packed)
        assertEquals(1f, c.fA, 1e-6f)
        assertEquals(1f, c.fR, 1e-6f)
        assertEquals(0x80 / 255f, c.fG, 1e-6f)
        assertEquals(0f, c.fB, 1e-6f)
    }

    @Test
    fun `FromColor and toSkColor roundtrip across the full byte range`() {
        for (a in intArrayOf(0, 64, 128, 192, 255)) {
            for (r in intArrayOf(0, 1, 127, 254, 255)) {
                for (g in intArrayOf(0, 64, 200, 255)) {
                    for (b in intArrayOf(0, 32, 128, 255)) {
                        val packed = SkColorSetARGB(a, r, g, b)
                        val out = SkColor4f.FromColor(packed).toSkColor()
                        assertEquals(packed, out, "roundtrip mismatch on a=$a r=$r g=$g b=$b")
                    }
                }
            }
        }
    }
}
