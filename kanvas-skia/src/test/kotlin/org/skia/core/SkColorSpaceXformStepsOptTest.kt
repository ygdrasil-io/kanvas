package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColorSpace
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import kotlin.math.abs

/**
 * Phase A of MIGRATION_PLAN_COLORSPACE_PORT.md — checks that each of the
 * four constructor optimizations Skia ships actually fires for the input
 * conditions documented in the upstream `SkColorSpaceXformSteps.cpp`.
 */
class SkColorSpaceXformStepsOptTest {

    private val rec2020 =
        SkColorSpace.makeRGB(SkNamedTransferFn.kRec2020, SkNamedGamut.kRec2020)!!
    private val srgbLinear = SkColorSpace.makeSRGBLinear()
    private val srgb = SkColorSpace.makeSRGB()
    private val rec2020Linear =
        SkColorSpace.makeRGB(SkNamedTransferFn.kLinear, SkNamedGamut.kRec2020)!!

    // -----------------------------------------------------------------------
    // Opt 1 — Opaque-output hint: dstAT == kOpaque → dstAT = srcAT
    // -----------------------------------------------------------------------

    @Test
    fun `opaque dst with premul src does not premul output`() {
        val steps = SkColorSpaceXformSteps(
            srgb, SkAlphaType.kPremul,
            srgb, SkAlphaType.kOpaque,
        )
        // dstAT==Opaque is rewritten to srcAT==Premul, so src and dst
        // alphas match and we hit Opt 2 (early-return identity pipeline).
        assertTrue(steps.flags.isIdentity, "expected identity, got ${steps.flags}")
    }

    // -----------------------------------------------------------------------
    // Opt 2 — Early-return when src==dst and alpha matches
    // -----------------------------------------------------------------------

    @Test
    fun `same colorspace and alpha returns identity pipeline`() {
        for (at in listOf(SkAlphaType.kOpaque, SkAlphaType.kPremul, SkAlphaType.kUnpremul)) {
            val steps = SkColorSpaceXformSteps(srgb, at, srgb, at)
            assertTrue(steps.flags.isIdentity, "alpha=$at expected identity, got ${steps.flags}")
        }
    }

    @Test
    fun `same colorspace different alpha runs only unpremul or premul`() {
        // Premul → Unpremul: only unpremul fires
        val s1 = SkColorSpaceXformSteps(srgb, SkAlphaType.kPremul, srgb, SkAlphaType.kUnpremul)
        assertTrue(s1.flags.unpremul)
        assertFalse(s1.flags.premul)
        assertFalse(s1.flags.linearize)
        assertFalse(s1.flags.encode)
        assertFalse(s1.flags.gamutTransform)

        // Unpremul → Premul: only premul fires
        val s2 = SkColorSpaceXformSteps(srgb, SkAlphaType.kUnpremul, srgb, SkAlphaType.kPremul)
        assertFalse(s2.flags.unpremul)
        assertTrue(s2.flags.premul)
    }

    // -----------------------------------------------------------------------
    // Opt 3 — linearize/encode skip-if-linear
    // -----------------------------------------------------------------------

    @Test
    fun `Linear-to-Linear different gamut skips linearize and encode`() {
        val steps = SkColorSpaceXformSteps(
            srgbLinear, SkAlphaType.kUnpremul,
            rec2020Linear, SkAlphaType.kUnpremul,
        )
        assertFalse(steps.flags.linearize, "src is linear; linearize must be skipped")
        assertFalse(steps.flags.encode, "dst is linear; encode must be skipped")
        assertTrue(steps.flags.gamutTransform, "gamut differs; transform required")
    }

    @Test
    fun `sRGB-to-Linear-different-gamut linearizes but does not encode`() {
        val steps = SkColorSpaceXformSteps(
            srgb, SkAlphaType.kUnpremul,
            rec2020Linear, SkAlphaType.kUnpremul,
        )
        assertTrue(steps.flags.linearize, "src has non-linear TF")
        assertFalse(steps.flags.encode, "dst is linear")
        assertTrue(steps.flags.gamutTransform)
    }

    @Test
    fun `Linear-to-sRGB-different-gamut encodes but does not linearize`() {
        val steps = SkColorSpaceXformSteps(
            srgbLinear, SkAlphaType.kUnpremul,
            rec2020, SkAlphaType.kUnpremul,
        )
        assertFalse(steps.flags.linearize, "src is linear")
        assertTrue(steps.flags.encode, "dst has non-linear TF")
        assertTrue(steps.flags.gamutTransform)
    }

    // -----------------------------------------------------------------------
    // Opt 4a — linearize+encode same TF + no gamut → both cancel
    //
    // Upstream condition (SkColorSpaceXformSteps.cpp:181-200):
    //   linearize && encode && !gamut_transform && transferFnHash matches.
    // The trigger case is: same colorspace but different alpha types — Opt 2
    // doesn't fire (alpha differs), and the linearize→encode round-trip is
    // identity, so we cancel both.
    // -----------------------------------------------------------------------

    @Test
    fun `same TF same gamut different alpha skips both linearize and encode`() {
        // sRGB Premul → sRGB Unpremul: same TF, same gamut, different alpha.
        // Naive code would set linearize=true, encode=true. Opt 4a kills
        // both, leaving only the unpremul step.
        val steps = SkColorSpaceXformSteps(
            srgb, SkAlphaType.kPremul,
            srgb, SkAlphaType.kUnpremul,
        )
        assertFalse(steps.flags.linearize, "Opt 4a should cancel linearize")
        assertFalse(steps.flags.encode, "Opt 4a should cancel encode")
        assertFalse(steps.flags.gamutTransform)
        assertTrue(steps.flags.unpremul)
        assertFalse(steps.flags.premul)
    }

    // -----------------------------------------------------------------------
    // Opt 4b — unpremul+premul cancel
    // -----------------------------------------------------------------------

    @Test
    fun `Premul-to-Premul same colorspace cancels both unpremul and premul`() {
        // Same CS triggers Opt 2 (early-return identity), not Opt 4b. To test
        // Opt 4b in isolation, we'd need same CS + alpha conversion + no
        // color work — but Opt 2 covers that case faster. So Opt 4b is more
        // of a defensive belt-and-braces: verify identity output.
        val steps = SkColorSpaceXformSteps(
            srgb, SkAlphaType.kPremul,
            srgb, SkAlphaType.kPremul,
        )
        assertTrue(steps.flags.isIdentity)
    }

    @Test
    fun `Premul-to-Premul different gamut, both linear, cancels unpremul and premul`() {
        // Linear → Linear different gamut: linearize=false, encode=false
        // (Opt 3), gamut=true. With srcAT=Premul, dstAT=Premul, naive code
        // would set unpremul=true, premul=true. Opt 4b says: since there's
        // no non-linear op between them... wait, gamut_transform is between
        // them. So Opt 4b should NOT fire here (linearize/encode are off
        // but gamut is ON, and Opt 4b only checks linearize+encode).
        // Upstream `SkColorSpaceXformSteps.cpp:202-210`:
        //   if (unpremul && !linearize && !encode && premul) { cancel; }
        // So gamut_transform doesn't block 4b. Both should cancel.
        val steps = SkColorSpaceXformSteps(
            srgbLinear, SkAlphaType.kPremul,
            rec2020Linear, SkAlphaType.kPremul,
        )
        assertFalse(steps.flags.unpremul, "Opt 4b should cancel unpremul")
        assertFalse(steps.flags.premul, "Opt 4b should cancel premul")
        assertTrue(steps.flags.gamutTransform)
    }

    // -----------------------------------------------------------------------
    // Behavioral check — bit-stable round-trip with α<1 thanks to Opt 4b
    // -----------------------------------------------------------------------

    @Test
    fun `Premul same colorspace alpha 0_5 round-trip is bit-stable`() {
        val steps = SkColorSpaceXformSteps(
            srgb, SkAlphaType.kPremul,
            srgb, SkAlphaType.kPremul,
        )
        // Identity pipeline (Opt 2). No floating-point drift on any α.
        for (a in listOf(0.5f, 0.25f, 0.75f, 0.001f, 1f)) {
            val rgba = floatArrayOf(0.3f * a, 0.5f * a, 0.7f * a, a)
            val before = rgba.copyOf()
            steps.apply(rgba)
            for (i in 0 until 4) {
                assertEquals(before[i], rgba[i], "α=$a, channel $i drifted")
            }
        }
    }

    @Test
    fun `Premul to Premul different gamut, both linear, alpha preserved exactly`() {
        // With Opt 4b, no unpremul/premul round-trip → α preserved exactly.
        val steps = SkColorSpaceXformSteps(
            srgbLinear, SkAlphaType.kPremul,
            rec2020Linear, SkAlphaType.kPremul,
        )
        for (a in listOf(0.5f, 0.25f, 0.001f, 1f)) {
            val rgba = floatArrayOf(0.3f * a, 0.5f * a, 0.7f * a, a)
            steps.apply(rgba)
            assertEquals(a, rgba[3], "α=$a not preserved")
            // Components went through gamut transform but alpha did not.
        }
    }

    // -----------------------------------------------------------------------
    // Regression — sRGB → Rec.2020 must still produce (43, 13, 241) for blue
    // -----------------------------------------------------------------------

    @Test
    fun `sRGB to Rec_2020 still produces (43, 13, 241) for pure blue`() {
        val steps = SkColorSpaceXformSteps(
            srgb, SkAlphaType.kOpaque,
            rec2020, SkAlphaType.kOpaque,
        )
        val rgba = floatArrayOf(0f, 0f, 1f, 1f)
        steps.apply(rgba)
        val r = (rgba[0] * 255f + 0.5f).toInt()
        val g = (rgba[1] * 255f + 0.5f).toInt()
        val b = (rgba[2] * 255f + 0.5f).toInt()
        assertEquals(43, r)
        assertEquals(13, g)
        assertTrue(abs(b - 241) <= 1, "B=$b expected ~241")
    }
}
