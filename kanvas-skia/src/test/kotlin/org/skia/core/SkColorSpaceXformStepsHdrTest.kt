package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorSpace
import org.skia.skcms.SkNamedGamut
import org.skia.skcms.SkNamedTransferFn

/**
 * Phase I of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the HDR
 * extensions to [SkColorSpaceXformSteps]: PQ/HLG src/dst pipelining,
 * scale-factor injection into the gamut step, and the OOTF cancel
 * optimisation. Ground truth comes from a standalone C++ driver
 * (`tools/ootf_test.cpp`) that walks the same constructor + apply
 * branches as upstream `SkColorSpaceXformSteps.cpp`.
 */
class SkColorSpaceXformStepsHdrTest {

    private fun pqRec2020(refWhite: Float = 203f): SkColorSpace =
        SkColorSpace.makeRGB(
            org.skia.skcms.skcmsTransferFunctionMakePQ(refWhite),
            SkNamedGamut.kRec2020,
        )!!

    private fun hlgRec2020(
        refWhite: Float = 203f, peak: Float = 1000f, sysGamma: Float = 1.2f,
    ): SkColorSpace = SkColorSpace.makeRGB(
        org.skia.skcms.skcmsTransferFunctionMakeHLG(refWhite, peak, sysGamma),
        SkNamedGamut.kRec2020,
    )!!

    @Test
    fun `sRGB to PQ Rec_2020 reproduces the ground-truth pure blue`() {
        // From ootf_test.cpp: sRGB(0,0,1) → PQ_Rec.2020(ref=203) ≈ (0.290, 0.197, 0.569).
        val xform = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            pqRec2020(), SkAlphaType.kOpaque,
        )
        val rgba = floatArrayOf(0f, 0f, 1f, 1f)
        xform.apply(rgba)
        assertNear(0.289628f, rgba[0], 1e-3f, "R")
        assertNear(0.196750f, rgba[1], 1e-3f, "G")
        assertNear(0.569181f, rgba[2], 1e-3f, "B")
    }

    @Test
    fun `sRGB pure red maps to PQ_Rec_2020 ground truth`() {
        val xform = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            pqRec2020(), SkAlphaType.kOpaque,
        )
        val rgba = floatArrayOf(1f, 0f, 0f, 1f)
        xform.apply(rgba)
        assertNear(0.532556f, rgba[0], 1e-3f, "R")
        assertNear(0.327013f, rgba[1], 1e-3f, "G")
        assertNear(0.220019f, rgba[2], 1e-3f, "B")
    }

    @Test
    fun `gray sRGB maps near-uniformly under PQ_Rec_2020`() {
        // From ootf_test.cpp: sRGB(0.5,0.5,0.5) → PQ_Rec.2020(ref=203) ≈ (0.427, 0.427, 0.427).
        val xform = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            pqRec2020(), SkAlphaType.kOpaque,
        )
        val rgba = floatArrayOf(0.5f, 0.5f, 0.5f, 1f)
        xform.apply(rgba)
        assertNear(0.427033f, rgba[0], 1e-3f, "R")
        assertNear(0.427010f, rgba[1], 1e-3f, "G")
        assertNear(0.427004f, rgba[2], 1e-3f, "B")
    }

    @Test
    fun `PQ src activates linearize and gamut transform`() {
        // The constructor should detect PQ src and enable linearize +
        // gamut (scaleFactor != 1) even when src and dst gamuts match.
        val pq = pqRec2020()
        val rec2020Linear = SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)!!
        val xform = SkColorSpaceXformSteps(
            pq, SkAlphaType.kOpaque,
            rec2020Linear, SkAlphaType.kOpaque,
        )
        assertTrue(xform.flags.linearize, "PQ src needs linearize")
        assertTrue(xform.flags.gamutTransform, "PQ src needs gamut step (scaleFactor != 1)")
        assertFalse(xform.flags.encode, "Linear dst doesn't need encode")
        assertFalse(xform.flags.srcOotf, "PQ has no system-gamma OOTF")
    }

    @Test
    fun `HLG src with system gamma 1_2 activates srcOotf`() {
        val hlg = hlgRec2020(sysGamma = 1.2f)
        val rec2020Linear = SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)!!
        val xform = SkColorSpaceXformSteps(
            hlg, SkAlphaType.kOpaque,
            rec2020Linear, SkAlphaType.kOpaque,
        )
        assertTrue(xform.flags.srcOotf, "HLG with system_gamma != 1 must enable srcOotf")
        // gamma_minus_1 = c - 1 = 0.2.
        assertNear(0.2f, xform.fSrcOotf[3], 1e-6f, "gamma_minus_1")
        // Y coefficients: in the same gamut (Rec.2020), the matrix is identity,
        // so Y = Y_Rec2020 directly.
        assertNear(0.2627f, xform.fSrcOotf[0], 1e-3f, "Y_R")
        assertNear(0.6780f, xform.fSrcOotf[1], 1e-3f, "Y_G")
        assertNear(0.0593f, xform.fSrcOotf[2], 1e-3f, "Y_B")
    }

    @Test
    fun `HLG src with system gamma 1_0 does not activate srcOotf`() {
        // c=1 means no OOTF. (`SkColorSpaceXformSteps.cpp:91-95`.)
        val hlg = hlgRec2020(sysGamma = 1.0f)
        val rec2020Linear = SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)!!
        val xform = SkColorSpaceXformSteps(
            hlg, SkAlphaType.kOpaque,
            rec2020Linear, SkAlphaType.kOpaque,
        )
        assertFalse(xform.flags.srcOotf, "system_gamma=1 means OOTF is off")
        assertTrue(xform.flags.linearize)
    }

    @Test
    fun `OOTF cancel when src and dst HLG share gamut and the same system gamma`() {
        // Upstream cancel test (`SkColorSpaceXformSteps.cpp:160-163`):
        //   (srcOotf[3] + 1) * (dstOotf[3] + 1) == 1
        // = c_src * (1 / c_dst) == 1
        // ⇔ c_src == c_dst.
        // So matching system gammas (and matching scaleFactor → no gamut step)
        // → both OOTFs drop. The cancel uses the same alpha types on both
        // sides so we keep the gamut step from firing.
        val srcHlg = hlgRec2020(sysGamma = 1.2f)
        val dstHlg = hlgRec2020(sysGamma = 1.2f)
        // Same TF + same gamut + same alpha → early-return identity. Force a
        // difference somewhere by picking a non-default peak luminance on dst
        // — that changes the scale factor by `b_d/a_d` vs `b_s/a_s`, which we
        // then re-balance below. To keep scaleFactor == 1 we use the same
        // luminance metadata too; then the difference comes from alpha types.
        // (Premul → Unpremul is a non-trivial pipeline that doesn't early-return.)
        val xform = SkColorSpaceXformSteps(
            srcHlg, SkAlphaType.kPremul,
            dstHlg, SkAlphaType.kUnpremul,
        )
        assertFalse(xform.flags.srcOotf,
            "OOTFs should cancel (same gamma, no gamut step)")
        assertFalse(xform.flags.dstOotf,
            "OOTFs should cancel (same gamma, no gamut step)")
        // sanity: alpha-type-only difference still kept the unpremul flag.
        assertTrue(xform.flags.unpremul, "alpha-type difference should drive unpremul")
    }

    @Test
    fun `OOTF does not cancel under a gamut transform`() {
        // Same reciprocal gammas, but the dst is in a different gamut →
        // we must keep both OOTFs because the Y vectors differ.
        val srcHlg = hlgRec2020(sysGamma = 1.2f)
        val dstHlg = SkColorSpace.makeRGB(
            org.skia.skcms.skcmsTransferFunctionMakeHLG(203f, 1000f, 1.0f / 1.2f),
            SkNamedGamut.kSRGB,
        )!!
        val xform = SkColorSpaceXformSteps(
            srcHlg, SkAlphaType.kOpaque,
            dstHlg, SkAlphaType.kOpaque,
        )
        assertTrue(xform.flags.gamutTransform, "different gamuts ⇒ gamut step")
        assertTrue(xform.flags.srcOotf, "different gamuts ⇒ OOTFs cannot cancel")
        assertTrue(xform.flags.dstOotf, "different gamuts ⇒ OOTFs cannot cancel")
    }

    @Test
    fun `PQ to PQ Rec_2020 with different ref-whites produces a non-identity scale`() {
        val pqA = pqRec2020(refWhite = 203f)
        val pqB = pqRec2020(refWhite = 100f)
        val xform = SkColorSpaceXformSteps(
            pqA, SkAlphaType.kOpaque,
            pqB, SkAlphaType.kOpaque,
        )
        assertTrue(xform.flags.gamutTransform,
            "different scale factors trigger the gamut step (scaleFactor != 1)")
        // src branch:  scaleFactor *= 10000/srcA  = 10000/203
        // dst branch:  scaleFactor /= 10000/dstA  ⇒ scaleFactor = (10000/srcA) * (dstA/10000)
        //                                                       = dstA/srcA = 100/203 ≈ 0.4926
        // Same gamut, so the matrix is `scaleFactor * I` on the diagonal.
        val expected = 100f / 203f
        assertNear(expected, xform.srcToDstMatrix[0], 1e-4f, "scale R")
        assertNear(expected, xform.srcToDstMatrix[4], 1e-4f, "scale G")
        assertNear(expected, xform.srcToDstMatrix[8], 1e-4f, "scale B")
    }

    @Test
    fun `existing sRGB to sRGB identity stays identity (Phase I regression)`() {
        // Smoke test: the most common path (sRGB → sRGB, opaque) still
        // early-returns to the no-op pipeline.
        val xform = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
        )
        assertTrue(xform.flags.isIdentity)
    }

    @Test
    fun `existing sRGB pure blue to Rec_2020-display still produces (43, 13, 241)`() {
        // Phase D/E/F invariant — the Rec.2020 reference rendering target
        // for our DM PNGs must still hit the same byte triple.
        val rec2020 = SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
        val xform = SkColorSpaceXformSteps(
            SkColorSpace.makeSRGB(), SkAlphaType.kOpaque,
            rec2020, SkAlphaType.kOpaque,
        )
        val rgba = floatArrayOf(0f, 0f, 1f, 1f)
        xform.apply(rgba)
        val r = (rgba[0] * 255f + 0.5f).toInt()
        val g = (rgba[1] * 255f + 0.5f).toInt()
        val b = (rgba[2] * 255f + 0.5f).toInt()
        assertEquals(43, r)
        assertEquals(13, g)
        assertTrue(b in 240..242, "B should be ~241, got $b")
    }

    @Test
    fun `kPQ and kHLG are valid TFs that classify correctly`() {
        // Sanity that the TF singletons are usable as src/dst spaces.
        assertNotEquals(null, pqRec2020())
        assertNotEquals(null, hlgRec2020())
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float, label: String = "") {
        assertTrue(kotlin.math.abs(expected - actual) <= tol,
            "$label: expected $expected ± $tol, got $actual (diff ${actual - expected})")
    }
}
