package org.skia.foundation


import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorGREEN
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColor4f
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.math.SkColorSetARGB
import org.skia.math.SkPreMultiplyColor
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
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

    // ─── Iso-alignment additions ────────────────────────────────────────

    @Test
    fun `array is the same shape as vec`() {
        val c = SkColor4f(0.1f, 0.2f, 0.3f, 0.4f)
        assertArrayEquals(c.vec(), c.array(), 0f)
    }

    @Test
    fun `index access returns each channel and rejects out-of-range`() {
        val c = SkColor4f(0.1f, 0.2f, 0.3f, 0.4f)
        assertEquals(0.1f, c[0]); assertEquals(0.2f, c[1])
        assertEquals(0.3f, c[2]); assertEquals(0.4f, c[3])
        assertThrows(IndexOutOfBoundsException::class.java) { c[4] }
    }

    @Test
    fun `isOpaque true only when alpha equals 1`() {
        assertTrue(SkColor4f.kRed.isOpaque())
        assertFalse(SkColor4f(1f, 0f, 0f, 0.5f).isOpaque())
        assertFalse(SkColor4f.kTransparent.isOpaque())
    }

    @Test
    fun `times scalar multiplies all channels`() {
        val c = SkColor4f(0.5f, 0.25f, 0.1f, 1f)
        assertEquals(SkColor4f(1f, 0.5f, 0.2f, 2f), c * 2f)
    }

    @Test
    fun `times other multiplies channelwise`() {
        val a = SkColor4f(0.5f, 0.5f, 0.5f, 1f)
        val b = SkColor4f(0.4f, 0.6f, 0.8f, 0.5f)
        assertEquals(SkColor4f(0.2f, 0.3f, 0.4f, 0.5f), a * b)
    }

    @Test
    fun `makeOpaque sets alpha to 1`() {
        assertEquals(SkColor4f(0.1f, 0.2f, 0.3f, 1f),
            SkColor4f(0.1f, 0.2f, 0.3f, 0.4f).makeOpaque())
    }

    @Test
    fun `pinAlpha clamps alpha to 0-to-1`() {
        assertEquals(SkColor4f(0.5f, 0.5f, 0.5f, 0f),
            SkColor4f(0.5f, 0.5f, 0.5f, -2f).pinAlpha())
        assertEquals(SkColor4f(0.5f, 0.5f, 0.5f, 1f),
            SkColor4f(0.5f, 0.5f, 0.5f, 5f).pinAlpha())
    }

    @Test
    fun `withAlpha and withAlphaByte set alpha`() {
        val c = SkColor4f(0.1f, 0.2f, 0.3f, 1f)
        assertEquals(SkColor4f(0.1f, 0.2f, 0.3f, 0.5f), c.withAlpha(0.5f))
        assertEquals(0.50196f, c.withAlphaByte(128).fA, 1e-4f)
    }

    @Test
    fun `premul multiplies RGB by alpha`() {
        val c = SkColor4f(1f, 0.5f, 0f, 0.5f)
        assertEquals(SkColor4f(0.5f, 0.25f, 0f, 0.5f), c.premul())
    }

    @Test
    fun `unpremul divides RGB by alpha`() {
        val pm = SkColor4f(0.5f, 0.25f, 0f, 0.5f)
        val u = pm.unpremul()
        assertEquals(1f, u.fR, 1e-6f); assertEquals(0.5f, u.fG, 1e-6f)
        assertEquals(0f, u.fB, 1e-6f); assertEquals(0.5f, u.fA, 1e-6f)
    }

    @Test
    fun `unpremul of zero-alpha returns transparent`() {
        assertEquals(SkColor4f.kTransparent, SkColor4f(1f, 1f, 1f, 0f).unpremul())
    }

    @Test
    fun `toBytes_RGBA packs R in MSB`() {
        // pure red, opaque ⇒ R=FF, G=00, B=00, A=FF
        assertEquals(0xFF0000FF.toInt(), SkColor4f(1f, 0f, 0f, 1f).toBytes_RGBA())
    }

    @Test
    fun `FromBytes_RGBA round-trip with toBytes_RGBA`() {
        val c = SkColor4f(0.25f, 0.5f, 0.75f, 1f)
        val round = SkColor4f.FromBytes_RGBA(c.toBytes_RGBA())
        assertEquals(0.25f, round.fR, 1f / 255f)
        assertEquals(0.5f, round.fG, 1f / 255f)
        assertEquals(0.75f, round.fB, 1f / 255f)
        assertEquals(1f, round.fA, 1f / 255f)
    }

    @Test
    fun `FromPMColor decodes a packed PM ARGB`() {
        val pm = SkPreMultiplyColor(SkColorSetARGB(0x80, 0xFF, 0xFF, 0xFF))
        val c = SkColor4f.FromPMColor(pm)
        assertEquals(0.502f, c.fA, 1e-3f)
    }
}
