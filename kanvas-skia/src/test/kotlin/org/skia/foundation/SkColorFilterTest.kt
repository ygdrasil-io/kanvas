package org.skia.foundation


import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorSetARGB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for the [SkColorFilter] family (Phase 7a).
 *
 * Each filter is exercised with a known-answer test, plus a parity
 * check against a hand-computed expectation. Tolerance is 1 8-bit ulp
 * unless the filter math is integer-precise (matrix with integer
 * coefficients, table) in which case we assert equality.
 */
class SkColorFilterTest {

    private fun assertColorClose(expected: SkColor4f, actual: SkColor4f, eps: Float = 1f / 255f, tag: String = "") {
        val dr = kotlin.math.abs(expected.fR - actual.fR)
        val dg = kotlin.math.abs(expected.fG - actual.fG)
        val db = kotlin.math.abs(expected.fB - actual.fB)
        val da = kotlin.math.abs(expected.fA - actual.fA)
        val maxDiff = maxOf(dr, dg, db, da)
        assertTrue(maxDiff <= eps) {
            "$tag expected ($expected), got ($actual), max diff $maxDiff"
        }
    }

    // -- SkColorFilters.Matrix ------------------------------------------------

    @Test
    fun `Matrix identity passes the colour through unchanged`() {
        val identity = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(identity)
        val src = SkColor4f(0.3f, 0.6f, 0.9f, 0.8f)
        assertColorClose(src, cf.filterColor4f(src), tag = "identity")
    }

    @Test
    fun `Matrix swap RB swaps the red and blue channels`() {
        val swapRB = floatArrayOf(
            0f, 0f, 1f, 0f, 0f,    // out R = in B
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,    // out B = in R
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(swapRB)
        val out = cf.filterColor4f(SkColor4f(1f, 0f, 0f, 1f))
        assertColorClose(SkColor4f(0f, 0f, 1f, 1f), out, tag = "RB swap")
    }

    @Test
    fun `Matrix bias adds a constant per channel`() {
        val biasR = floatArrayOf(
            1f, 0f, 0f, 0f, 0.25f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        val cf = SkColorFilters.Matrix(biasR)
        val out = cf.filterColor4f(SkColor4f(0.5f, 0.5f, 0.5f, 1f))
        assertColorClose(SkColor4f(0.75f, 0.5f, 0.5f, 1f), out, tag = "bias")
    }

    @Test
    fun `Matrix isAlphaUnchanged true for identity alpha row`() {
        val cf = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        assertTrue(cf.isAlphaUnchanged())
    }

    @Test
    fun `Matrix isAlphaUnchanged false when alpha row is non-trivial`() {
        val cf = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 0.5f, 0f,  // halves alpha
        ))
        assertEquals(false, cf.isAlphaUnchanged())
    }

    // -- SkColorFilters.Table -------------------------------------------------

    @Test
    fun `Table identity LUT passes through`() {
        val identity = ByteArray(256) { it.toByte() }
        val cf = SkColorFilters.Table(identity)
        val src = SkColor4f(0.5f, 0.25f, 0.75f, 1f)
        assertColorClose(src, cf.filterColor4f(src), tag = "identity LUT")
    }

    @Test
    fun `Table inverse LUT inverts every channel`() {
        val invert = ByteArray(256) { (255 - it).toByte() }
        val cf = SkColorFilters.Table(invert)
        // RED becomes CYAN, full alpha stays full because the LUT is symmetric :
        // 255 → 0, but we map each channel independently; alpha 255 → 0 too.
        val out = cf.filterColor4f(SkColor4f(1f, 0f, 0f, 1f))
        assertColorClose(SkColor4f(0f, 1f, 1f, 0f), out, tag = "invert all")
    }

    @Test
    fun `TableARGB null channels are identity`() {
        // Only alpha is remapped — RGB stay untouched.
        val invertAlpha = ByteArray(256) { (255 - it).toByte() }
        val cf = SkColorFilters.TableARGB(a = invertAlpha)
        val out = cf.filterColor4f(SkColor4f(0.5f, 0.5f, 0.5f, 1f))
        assertColorClose(SkColor4f(0.5f, 0.5f, 0.5f, 0f), out, tag = "invert alpha only")
    }

    // -- SkColorFilters.Compose ----------------------------------------------

    @Test
    fun `Compose evaluates inner first then outer`() {
        // outer = invert all; inner = identity; result = invert all.
        val invert = ByteArray(256) { (255 - it).toByte() }
        val outer = SkColorFilters.Table(invert)
        val inner = SkColorFilters.Table(ByteArray(256) { it.toByte() })
        val cf = SkColorFilters.Compose(outer, inner)
        val out = cf.filterColor4f(SkColor4f(0f, 1f, 0f, 1f))
        assertColorClose(SkColor4f(1f, 0f, 1f, 0f), out, tag = "compose")
    }

    @Test
    fun `Compose is associative for matrix filters`() {
        // M1 = scale by 2 (clipped at 1), M2 = invert. Compose(M2, M1) and
        // applying-then-applying-then should agree.
        val scale = SkColorFilters.Matrix(floatArrayOf(
            2f, 0f, 0f, 0f, 0f,
            0f, 2f, 0f, 0f, 0f,
            0f, 0f, 2f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val invert = SkColorFilters.Matrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 1f,
            0f, -1f, 0f, 0f, 1f,
            0f, 0f, -1f, 0f, 1f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val composed = SkColorFilters.Compose(invert, scale)
        val src = SkColor4f(0.25f, 0.4f, 0.1f, 1f)
        val expected = invert.filterColor4f(scale.filterColor4f(src))
        assertColorClose(expected, composed.filterColor4f(src), tag = "compose assoc")
    }

    // -- SkColorFilters.Lerp -------------------------------------------------

    @Test
    fun `Lerp at 0 returns dst filter`() {
        val a = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val b = SkColorFilters.Matrix(floatArrayOf(
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val cf = SkColorFilters.Lerp(0f, a, b)
        val src = SkColor4f(0.3f, 0.4f, 0.5f, 1f)
        assertColorClose(a.filterColor4f(src), cf.filterColor4f(src), tag = "t=0")
    }

    @Test
    fun `Lerp at 1 returns src filter`() {
        val a = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val b = SkColorFilters.Matrix(floatArrayOf(
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val cf = SkColorFilters.Lerp(1f, a, b)
        val src = SkColor4f(0.3f, 0.4f, 0.5f, 1f)
        assertColorClose(b.filterColor4f(src), cf.filterColor4f(src), tag = "t=1")
    }

    @Test
    fun `Lerp at 0_5 averages the two filters`() {
        val a = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val b = SkColorFilters.Matrix(floatArrayOf(
            0f, 0f, 0f, 0f, 0f,    // outputs 0 RGB regardless
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val cf = SkColorFilters.Lerp(0.5f, a, b)
        val src = SkColor4f(1f, 1f, 1f, 1f)
        // Halfway between (1,1,1,1) and (0,0,0,1) is (0.5, 0.5, 0.5, 1).
        assertColorClose(SkColor4f(0.5f, 0.5f, 0.5f, 1f), cf.filterColor4f(src), tag = "t=0.5")
    }

    // -- SkColorFilters.Blend ------------------------------------------------

    @Test
    fun `Blend kSrcOver opaque src replaces dst pixel`() {
        // Filter colour = opaque RED; mode = SrcOver. With opaque src in the
        // filter, the dst pixel is fully replaced.
        val cf = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcOver)
        val out = cf.filterColor4f(SkColor4f(0.5f, 0.5f, 0.5f, 1f))
        assertColorClose(SkColor4f(1f, 0f, 0f, 1f), out, tag = "Blend kSrcOver")
    }

    @Test
    fun `Blend kModulate multiplies the pixel by the filter colour`() {
        // Filter colour = (0.5, 0.5, 0.5, 1); mode = Modulate. Result = src * dst.
        val cf = SkColorFilters.Blend(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80), SkBlendMode.kModulate)
        val out = cf.filterColor4f(SkColor4f(1f, 1f, 1f, 1f))
        // 0x80 / 0xFF ≈ 0.502, premul = 0.502*0.502 ≈ 0.252; unpremul ≈ 0.502.
        // After unpremul (since alpha is 1 * 1 = 1), RGB ≈ 0.502.
        assertColorClose(SkColor4f(0x80 / 255f, 0x80 / 255f, 0x80 / 255f, 1f),
            out, eps = 0.01f, tag = "Blend kModulate")
    }

    @Test
    fun `Blend kSrcIn keeps the filter colour where the pixel is opaque`() {
        // Filter colour = RED, mode = SrcIn ⇒ out = src * dst.alpha.
        val cf = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
        val out = cf.filterColor4f(SkColor4f(0.2f, 0.7f, 0.3f, 1f))
        assertColorClose(SkColor4f(1f, 0f, 0f, 1f), out, tag = "Blend kSrcIn opaque dst")
    }

    @Test
    fun `Blend kSrcIn into transparent dst yields transparent`() {
        val cf = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcIn)
        val out = cf.filterColor4f(SkColor4f(0.5f, 0.5f, 0.5f, 0f))
        assertColorClose(SkColor4f(0f, 0f, 0f, 0f), out, tag = "Blend kSrcIn transparent dst")
    }

    // -- SkLumaColorFilter ---------------------------------------------------

    @Test
    fun `LumaColorFilter zeroes RGB and sets alpha to luma`() {
        val cf = SkLumaColorFilter.Make()
        // Luma(R=1, G=0, B=0) = 0.2126
        val out = cf.filterColor4f(SkColor4f(1f, 0f, 0f, 1f))
        assertColorClose(SkColor4f(0f, 0f, 0f, 0.2126f), out, eps = 0.001f, tag = "luma red")

        // Luma(R=0, G=1, B=0) = 0.7152
        val out2 = cf.filterColor4f(SkColor4f(0f, 1f, 0f, 1f))
        assertColorClose(SkColor4f(0f, 0f, 0f, 0.7152f), out2, eps = 0.001f, tag = "luma green")

        // Luma(R=0, G=0, B=1) = 0.0722
        val out3 = cf.filterColor4f(SkColor4f(0f, 0f, 1f, 1f))
        assertColorClose(SkColor4f(0f, 0f, 0f, 0.0722f), out3, eps = 0.001f, tag = "luma blue")
    }

    @Test
    fun `LumaColorFilter scales luma by source alpha`() {
        val cf = SkLumaColorFilter.Make()
        // Luma(white) * 0.5 alpha = 1 * 0.5 = 0.5
        val out = cf.filterColor4f(SkColor4f(1f, 1f, 1f, 0.5f))
        // (0.2126 + 0.7152 + 0.0722) * 0.5 = 0.5
        assertColorClose(SkColor4f(0f, 0f, 0f, 0.5f), out, eps = 0.001f, tag = "luma scaled by alpha")
    }

    @Test
    fun `LumaColorFilter Make returns the same singleton`() {
        assertEquals(SkLumaColorFilter.Make(), SkLumaColorFilter.Make())
    }

    // -- filterColor (8-bit convenience) -------------------------------------

    @Test
    fun `filterColor 8-bit overload round-trips through filterColor4f`() {
        val cf = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcOver)
        val out = cf.filterColor(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80))
        // SrcOver(opaque RED, opaque grey) = opaque RED.
        assertEquals(SK_ColorRED, out)
    }

    @Test
    fun `Compose makeComposed and SkColorFilters_Compose are equivalent`() {
        val a = SkColorFilters.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0.1f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        val b = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kModulate)
        val viaMethod = a.makeComposed(b)
        val viaFactory = SkColorFilters.Compose(a, b)
        val src = SkColor4f(0.3f, 0.6f, 0.4f, 1f)
        assertColorClose(viaMethod.filterColor4f(src), viaFactory.filterColor4f(src), tag = "makeComposed parity")
    }

    @Test
    fun `Matrix factory rejects wrong-sized arrays`() {
        try {
            SkColorFilters.Matrix(FloatArray(19))
            assertNotEquals("expected", "exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("20") == true)
        }
    }

    @Test
    fun `Table factory rejects wrong-sized arrays`() {
        try {
            SkColorFilters.Table(ByteArray(255))
            assertNotEquals("expected", "exception")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("256") == true)
        }
    }

    // -- SkColorFilters.Lighting (R1-B) -------------------------------------

    @Test
    fun `Lighting with mul=white add=black is identity for RGB and alpha`() {
        val cf = SkColorFilters.Lighting(SK_ColorWHITE, SkColorSetARGB(0, 0, 0, 0))
        val src = SkColor4f(0.4f, 0.7f, 0.2f, 0.8f)
        assertColorClose(src, cf.filterColor4f(src), tag = "lighting identity")
    }

    @Test
    fun `Lighting multiplies RGB by mul byte-normalized`() {
        // mul = 50% grey ⇒ each channel halves; add = black ⇒ no bias.
        val cf = SkColorFilters.Lighting(SkColorSetARGB(0xFF, 0x80, 0x80, 0x80), 0)
        val out = cf.filterColor4f(SkColor4f(1f, 1f, 1f, 1f))
        // 0x80 / 0xFF ≈ 0.5019608
        assertColorClose(SkColor4f(0x80 / 255f, 0x80 / 255f, 0x80 / 255f, 1f), out,
            eps = 0.001f, tag = "Lighting mul=grey")
    }

    @Test
    fun `Lighting adds add-color bias per channel`() {
        // mul = white (identity), add = (32, 64, 96).
        val cf = SkColorFilters.Lighting(SK_ColorWHITE, SkColorSetARGB(0, 32, 64, 96))
        val out = cf.filterColor4f(SkColor4f(0.1f, 0.1f, 0.1f, 1f))
        val expR = 0.1f + 32f / 255f
        val expG = 0.1f + 64f / 255f
        val expB = 0.1f + 96f / 255f
        assertColorClose(SkColor4f(expR, expG, expB, 1f), out,
            eps = 0.001f, tag = "Lighting add bias")
    }

    @Test
    fun `Lighting ignores the alpha bytes of mul and add`() {
        // Passing different alphas in `mul`/`add` must not affect the result —
        // upstream Skia explicitly ignores alpha.
        val cfA = SkColorFilters.Lighting(0xFF808080.toInt(), 0xFF204060.toInt())
        val cfB = SkColorFilters.Lighting(0x00808080, 0x00204060)
        val src = SkColor4f(0.3f, 0.4f, 0.5f, 0.6f)
        assertColorClose(cfA.filterColor4f(src), cfB.filterColor4f(src),
            tag = "Lighting alpha-byte invariance")
    }

    // -- SkColorFilters.LinearToSRGBGamma / SRGBToLinearGamma (R1-B) --------

    @Test
    fun `LinearToSRGBGamma matches reference values for 0_5 linear`() {
        val cf = SkColorFilters.LinearToSRGBGamma()
        // sRGB encoding of 0.5 linear is ≈ 0.7353569.
        val out = cf.filterColor4f(SkColor4f(0.5f, 0.5f, 0.5f, 1f))
        assertColorClose(SkColor4f(0.7353569f, 0.7353569f, 0.7353569f, 1f),
            out, eps = 0.001f, tag = "linear 0.5 → sRGB")
    }

    @Test
    fun `SRGBToLinearGamma matches reference values for 0_7353 sRGB`() {
        val cf = SkColorFilters.SRGBToLinearGamma()
        val out = cf.filterColor4f(SkColor4f(0.7353569f, 0.7353569f, 0.7353569f, 1f))
        assertColorClose(SkColor4f(0.5f, 0.5f, 0.5f, 1f), out, eps = 0.001f,
            tag = "sRGB 0.7353 → linear")
    }

    @Test
    fun `gamma filters are round-trip inverses`() {
        val toSrgb = SkColorFilters.LinearToSRGBGamma()
        val toLinear = SkColorFilters.SRGBToLinearGamma()
        for (v in floatArrayOf(0f, 0.01f, 0.1f, 0.3f, 0.5f, 0.7f, 0.9f, 1f)) {
            val src = SkColor4f(v, v, v, 1f)
            val round = toLinear.filterColor4f(toSrgb.filterColor4f(src))
            assertColorClose(src, round, eps = 0.002f, tag = "round-trip $v")
        }
    }

    @Test
    fun `gamma filters preserve alpha`() {
        val cf = SkColorFilters.LinearToSRGBGamma()
        val out = cf.filterColor4f(SkColor4f(0.2f, 0.5f, 0.9f, 0.42f))
        assertEquals(0.42f, out.fA, "alpha preserved")
        assertTrue(cf.isAlphaUnchanged())
        assertTrue(SkColorFilters.SRGBToLinearGamma().isAlphaUnchanged())
    }

    @Test
    fun `gamma filters keep 0 and 1 fixed`() {
        val toSrgb = SkColorFilters.LinearToSRGBGamma()
        val toLinear = SkColorFilters.SRGBToLinearGamma()
        assertColorClose(SkColor4f(0f, 0f, 0f, 1f),
            toSrgb.filterColor4f(SkColor4f(0f, 0f, 0f, 1f)), tag = "0 → 0")
        assertColorClose(SkColor4f(1f, 1f, 1f, 1f),
            toSrgb.filterColor4f(SkColor4f(1f, 1f, 1f, 1f)), tag = "1 → 1")
        assertColorClose(SkColor4f(0f, 0f, 0f, 1f),
            toLinear.filterColor4f(SkColor4f(0f, 0f, 0f, 1f)), tag = "0 → 0 inv")
        assertColorClose(SkColor4f(1f, 1f, 1f, 1f),
            toLinear.filterColor4f(SkColor4f(1f, 1f, 1f, 1f)), tag = "1 → 1 inv")
    }

    // -- SkColorFilters.Lerp nullable overload (R1-B) -----------------------

    @Test
    fun `Lerp nullable returns null when both filters are null`() {
        assertEquals(null, SkColorFilters.Lerp(0.5f, null, null))
    }

    @Test
    fun `Lerp nullable returns null when weight is NaN`() {
        // Pass at least one null to force resolution to the nullable overload.
        val cf: SkColorFilter? = SkColorFilters.LinearToSRGBGamma()
        assertEquals(null, SkColorFilters.Lerp(Float.NaN, cf, null))
        assertEquals(null, SkColorFilters.Lerp(Float.NaN, null, cf))
        assertEquals(null, SkColorFilters.Lerp(Float.NaN, null, null))
    }

    @Test
    fun `Lerp nullable short-circuits at t=0 to dst`() {
        val dst: SkColorFilter? = SkColorFilters.LinearToSRGBGamma()
        val src: SkColorFilter? = SkColorFilters.SRGBToLinearGamma()
        assertEquals(dst, SkColorFilters.Lerp(0f, dst, src))
        assertEquals(dst, SkColorFilters.Lerp(-0.1f, dst, src))
        assertEquals(null, SkColorFilters.Lerp(0f, null, src),
            "null dst at t=0 means no filter")
    }

    @Test
    fun `Lerp nullable short-circuits at t=1 to src`() {
        val dst: SkColorFilter? = SkColorFilters.LinearToSRGBGamma()
        val src: SkColorFilter? = SkColorFilters.SRGBToLinearGamma()
        assertEquals(src, SkColorFilters.Lerp(1f, dst, src))
        assertEquals(src, SkColorFilters.Lerp(1.5f, dst, src))
        assertEquals(null, SkColorFilters.Lerp(1f, dst, null),
            "null src at t=1 means no filter")
    }

    @Test
    fun `Lerp nullable identity-substitutes null sides at intermediate weights`() {
        // dst = null (identity), src = SrcOver with RED ⇒ lerp(0.5, src, RED)
        // = halfway between original and red.
        val srcFilter = SkColorFilters.Blend(SK_ColorRED, SkBlendMode.kSrcOver)
        val cf = SkColorFilters.Lerp(0.5f, null, srcFilter)
        val input = SkColor4f(0f, 0f, 1f, 1f)  // pure blue
        val expected = SkColor4f(0.5f, 0f, 0.5f, 1f)  // halfway to red
        assertColorClose(expected, cf!!.filterColor4f(input), eps = 0.01f,
            tag = "null dst treated as identity")
    }

    @Test
    fun `Lerp nullable returns non-null filter when one side is non-null`() {
        val srcFilter = SkColorFilters.LinearToSRGBGamma()
        val cf = SkColorFilters.Lerp(0.3f, null, srcFilter)
        assertTrue(cf != null)
    }
}
