package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Phase C of MIGRATION_PLAN_COLORSPACE_PORT.md — modifiers
 * (`makeLinearGamma`, `makeSRGBGamma`, `makeColorSpin`).
 */
class SkColorSpaceModifiersTest {

    private val srgb = SkColorSpace.makeSRGB()
    private val srgbLinear = SkColorSpace.makeSRGBLinear()
    private val rec2020 =
        SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!

    // -----------------------------------------------------------------------
    // makeLinearGamma
    // -----------------------------------------------------------------------

    @Test
    fun `makeLinearGamma on sRGB returns sRGB-linear singleton`() {
        assertSame(srgbLinear, srgb.makeLinearGamma())
    }

    @Test
    fun `makeLinearGamma on already-linear returns this`() {
        // Note: upstream returns `sk_ref_sp(this)` — we just return `this`.
        assertSame(srgbLinear, srgbLinear.makeLinearGamma())
    }

    @Test
    fun `makeLinearGamma on Rec_2020 keeps gamut, swaps TF to linear`() {
        val linRec2020 = rec2020.makeLinearGamma()
        assertTrue(linRec2020.gammaIsLinear())
        assertEquals(rec2020.toXYZD50, linRec2020.toXYZD50)
        assertNotEquals(rec2020, linRec2020)
        assertFalse(linRec2020.isSRGB())
    }

    // -----------------------------------------------------------------------
    // makeSRGBGamma
    // -----------------------------------------------------------------------

    @Test
    fun `makeSRGBGamma on sRGB-linear returns sRGB singleton`() {
        assertSame(srgb, srgbLinear.makeSRGBGamma())
    }

    @Test
    fun `makeSRGBGamma on already-sRGB-gamma returns this`() {
        assertSame(srgb, srgb.makeSRGBGamma())
    }

    @Test
    fun `makeSRGBGamma on Rec_2020 keeps gamut, swaps TF to sRGB`() {
        val sRgbGammaRec2020 = rec2020.makeSRGBGamma()
        assertTrue(sRgbGammaRec2020.gammaCloseToSRGB())
        assertEquals(rec2020.toXYZD50, sRgbGammaRec2020.toXYZD50)
    }

    // -----------------------------------------------------------------------
    // makeColorSpin (R→G→B→R cycle)
    // -----------------------------------------------------------------------

    @Test
    fun `makeColorSpin three times returns matrix-equivalent original`() {
        // spin matrix:
        //   [0 0 1]
        //   [1 0 0]
        //   [0 1 0]
        // spin^3 = identity. So `cs.makeColorSpin().makeColorSpin().makeColorSpin()` should
        // produce a colorspace whose toXYZD50 matches the original (within float precision).
        val spin1 = srgb.makeColorSpin()
        val spin2 = spin1.makeColorSpin()
        val spin3 = spin2.makeColorSpin()

        for (r in 0 until 3) for (c in 0 until 3) {
            val expected = srgb.toXYZD50.vals[r][c]
            val got = spin3.toXYZD50.vals[r][c]
            assertTrue(kotlin.math.abs(expected - got) < 1e-5f,
                "[$r][$c] expected=$expected got=$got")
        }
    }

    @Test
    fun `makeColorSpin once differs from original`() {
        val spun = srgb.makeColorSpin()
        assertFalse(SkColorSpace.equals(srgb, spun))
        // TF preserved, gamut spun.
        assertTrue(spun.gammaCloseToSRGB())
    }

    @Test
    fun `makeColorSpin maps R column of toXYZD50 onto B column`() {
        // spin = {{0,0,1},{1,0,0},{0,1,0}}
        // spun = toXYZD50 * spin
        // → spun col_0 = toXYZD50 col_1 (because spin col_0 = (0,1,0))
        // → spun col_1 = toXYZD50 col_2 (because spin col_1 = (0,0,1))
        // → spun col_2 = toXYZD50 col_0 (because spin col_2 = (1,0,0))
        val spun = srgb.makeColorSpin()
        for (r in 0 until 3) {
            assertEquals(srgb.toXYZD50.vals[r][1], spun.toXYZD50.vals[r][0],
                "spun col 0 should equal original col 1 at row $r")
            assertEquals(srgb.toXYZD50.vals[r][2], spun.toXYZD50.vals[r][1])
            assertEquals(srgb.toXYZD50.vals[r][0], spun.toXYZD50.vals[r][2])
        }
    }

    private fun assertEquals(want: Float, got: Float, msg: String? = null) {
        org.junit.jupiter.api.Assertions.assertEquals(want, got, msg ?: "")
    }

    private fun assertEquals(want: org.graphiks.math.SkcmsMatrix3x3, got: org.graphiks.math.SkcmsMatrix3x3) {
        for (r in 0 until 3) for (c in 0 until 3) {
            assertEquals(want.vals[r][c], got.vals[r][c], "[$r][$c]")
        }
    }
}
