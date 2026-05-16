package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import kotlin.math.abs

/**
 * Phase R2.12 — covers [SkImage.makeColorSpace].
 *
 * Asserts the three transformations the GM harness needs at the
 * surface level :
 *  - sRGB ↔ Display P3
 *  - sRGB ↔ Rec.2020
 *  - identity (sRGB → sRGB) is a fast-path no-op.
 *
 * The conversion is tested for the "white preserves" invariant
 * (`(255, 255, 255)` round-trips through both gamut transforms with
 * ≤ 2 LSB drift), the "primary shifts" invariant (`(255, 0, 0)` in
 * sRGB maps to a different value in Display P3 — the wide gamut
 * carries more saturated red), and the round-trip property
 * (`sRGB → P3 → sRGB` returns within 2 LSB of the original).
 */
class SkImageColorSpaceTest {

    private fun displayP3(): SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kDisplayP3)!!

    private fun rec2020(): SkColorSpace =
        SkColorSpace.makeRGB(SkNamedTransferFn.kSRGB, SkNamedGamut.kRec2020)!!

    private fun solidImage(color: SkColor): SkImage {
        val bm = SkBitmap(4, 4, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
        bm.pixels.fill(color)
        return bm.asImage()
    }

    private fun closeEnough(a: Int, b: Int, tol: Int = 2): Boolean =
        abs(SkColorGetR(a) - SkColorGetR(b)) <= tol &&
            abs(SkColorGetG(a) - SkColorGetG(b)) <= tol &&
            abs(SkColorGetB(a) - SkColorGetB(b)) <= tol &&
            abs(SkColorGetA(a) - SkColorGetA(b)) <= tol

    @Test
    fun `same colorspace returns the same image instance (identity)`() {
        val src = solidImage(SK_ColorRED)
        val out = src.makeColorSpace(SkColorSpace.makeSRGB())
        assertSame(src, out, "identity colour-space transform must short-circuit")
    }

    @Test
    fun `sRGB white round-trips through DisplayP3 within 2 LSB`() {
        val src = solidImage(SK_ColorWHITE)
        val p3 = src.makeColorSpace(displayP3())!!
        assertTrue(SkColorSpace.equals(displayP3(), p3.colorSpace))
        // White is a gamut-invariant — DisplayP3 (255, 255, 255) is
        // still our white.
        assertTrue(
            closeEnough(SK_ColorWHITE, p3.peekPixel(0, 0)),
            "white must stay close to white through gamut transform, got ${
                Integer.toHexString(p3.peekPixel(0, 0))}",
        )
    }

    @Test
    fun `sRGB white round-trips through Rec2020 within 2 LSB`() {
        val src = solidImage(SK_ColorWHITE)
        val r2020 = src.makeColorSpace(rec2020())!!
        assertTrue(
            closeEnough(SK_ColorWHITE, r2020.peekPixel(0, 0)),
            "white must stay close to white through gamut transform, got ${
                Integer.toHexString(r2020.peekPixel(0, 0))}",
        )
    }

    @Test
    fun `pure red in sRGB shifts when retagged to DisplayP3`() {
        val src = solidImage(SK_ColorRED)
        val p3 = src.makeColorSpace(displayP3())!!
        val converted = p3.peekPixel(0, 0)
        // Display P3 has a wider red primary, so encoding sRGB red
        // into P3 should *desaturate* the stored value (the same
        // wavelength is less saturated in P3 coordinates).
        // Concretely : the R channel should drop below 255.
        assertTrue(
            SkColorGetR(converted) < 255,
            "sRGB red should desaturate when re-tagged to P3 — got R=${SkColorGetR(converted)}",
        )
        // …and green should pick up a small positive contribution
        // (the sRGB red primary projects outside the P3 red corner).
        assertTrue(
            SkColorGetG(converted) > 0,
            "sRGB red should pick up green channel when re-tagged to P3 — got G=${SkColorGetG(converted)}",
        )
        assertNotEquals(
            SK_ColorRED, converted,
            "P3-tagged red must differ from sRGB red",
        )
    }

    @Test
    fun `round-trip sRGB to P3 to sRGB stays within 2 LSB`() {
        val src = solidImage(SkColorSetARGB(0xFF, 128, 64, 200))
        val out = src.makeColorSpace(displayP3())!!.makeColorSpace(SkColorSpace.makeSRGB())!!
        val before = src.peekPixel(0, 0)
        val after = out.peekPixel(0, 0)
        assertTrue(
            closeEnough(before, after),
            "sRGB → P3 → sRGB must round-trip within 2 LSB, got ${
                Integer.toHexString(before)} → ${Integer.toHexString(after)}",
        )
    }

    @Test
    fun `output image carries the target colorspace tag`() {
        val src = solidImage(SK_ColorGREEN)
        val p3 = src.makeColorSpace(displayP3())
        assertNotNull(p3)
        assertTrue(SkColorSpace.equals(displayP3(), p3!!.colorSpace))
        // And matches expected hash with target.
        assertEquals(displayP3().hash(), p3.colorSpace.hash())
    }
}
