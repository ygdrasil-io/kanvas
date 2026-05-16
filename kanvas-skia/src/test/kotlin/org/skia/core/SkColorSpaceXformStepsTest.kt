package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorSpace
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import kotlin.math.abs

class SkColorSpaceXformStepsTest {

    @Test
    fun `sRGB to sRGB Opaque is identity no-op`() {
        val steps = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
        )
        assertTrue(steps.flags.isIdentity)

        val rgba = floatArrayOf(0.5f, 0.25f, 0.75f, 1f)
        val before = rgba.copyOf()
        steps.apply(rgba)
        for (i in 0 until 4) {
            assertEquals(before[i], rgba[i], "channel $i")
        }
    }

    @Test
    fun `sRGB to sRGB-linear at midpoint matches sRGB EOTF`() {
        val steps = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            SkColorSpace.makeSRGBLinear(), SkAlphaType.kOpaque,
        )
        // Same gamut, only TF differs: linearize, then no encode (Phase A
        // Opt 3 skips the identity encode because dst is linear).
        assertTrue(steps.flags.linearize)
        assertFalse(steps.flags.gamutTransform)
        assertFalse(steps.flags.encode)

        val rgba = floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
        steps.apply(rgba)
        // sRGB(0.5) → linear ≈ 0.21404
        for (i in 0 until 3) {
            assertTrue(abs(rgba[i] - 0.21404f) < 1e-3f,
                "channel $i: ${rgba[i]} vs 0.21404")
        }
        assertEquals(1f, rgba[3])
    }

    @Test
    fun `sRGB to Rec_2020 produces (43, 13, 241) bit-pattern for pure blue`() {
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val steps = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            rec2020, SkAlphaType.kOpaque,
        )
        // Source side: 1.0 sRGB-encoded blue.
        val rgba = floatArrayOf(0f, 0f, 1f, 1f)
        steps.apply(rgba)
        val r = (rgba[0] * 255f + 0.5f).toInt()
        val g = (rgba[1] * 255f + 0.5f).toInt()
        val b = (rgba[2] * 255f + 0.5f).toInt()
        assertEquals(43, r, "R must be 43 (matches bigrect.png)")
        assertEquals(13, g, "G must be 13 (matches bigrect.png)")
        assertTrue(b in 240..242, "B must be ~241; got $b")
        assertEquals(1f, rgba[3], "alpha preserved")
    }

    @Test
    fun `Premul to Unpremul recovers component values`() {
        val steps = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kPremul,
            SkColorSpace.makeSRGB(), SkAlphaType.kUnpremul,
        )
        // Same colorspace but different alpha type means linearize+encode are
        // off; only the unpremul step runs.
        val rgba = floatArrayOf(0.25f, 0.125f, 0.375f, 0.5f) // premul: components scaled by 0.5
        steps.apply(rgba)
        // After unpremul, components should be doubled.
        assertTrue(abs(rgba[0] - 0.5f) < 1e-6f)
        assertTrue(abs(rgba[1] - 0.25f) < 1e-6f)
        assertTrue(abs(rgba[2] - 0.75f) < 1e-6f)
        assertEquals(0.5f, rgba[3])
    }

    @Test
    fun `zero alpha unpremul yields zeros (no NaN)`() {
        val steps = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kPremul,
            SkColorSpace.makeSRGB(), SkAlphaType.kUnpremul,
        )
        val rgba = floatArrayOf(0f, 0f, 0f, 0f)
        steps.apply(rgba)
        for (i in 0 until 4) {
            assertEquals(0f, rgba[i], "channel $i must stay 0, no NaN/Inf")
        }
    }
}
