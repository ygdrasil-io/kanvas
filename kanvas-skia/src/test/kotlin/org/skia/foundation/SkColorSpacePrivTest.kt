package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.SkcmsMatrix3x3
import org.skia.foundation.skcms.SkcmsTransferFunction

/**
 * Phase B of MIGRATION_PLAN_COLORSPACE_PORT.md — exercise the
 * `is_almost_*` helpers and the `makeRGB` snap behaviour.
 */
class SkColorSpacePrivTest {

    @Test
    fun `colorSpaceAlmostEqual fires within 0_01`() {
        assertTrue(xyzAlmostEqual(SkNamedGamut.kSRGB, SkNamedGamut.kSRGB))
        // Perturb every cell by 0.005 (< 0.01) → still equal
        val perturbed = SkcmsMatrix3x3(Array(3) { r ->
            FloatArray(3) { c -> SkNamedGamut.kSRGB.vals[r][c] + 0.005f }
        })
        assertTrue(xyzAlmostEqual(SkNamedGamut.kSRGB, perturbed))
        // Perturb by 0.02 → diverge
        val tooFar = SkcmsMatrix3x3(Array(3) { r ->
            FloatArray(3) { c -> SkNamedGamut.kSRGB.vals[r][c] + 0.02f }
        })
        assertFalse(xyzAlmostEqual(SkNamedGamut.kSRGB, tooFar))
    }

    @Test
    fun `isAlmostSRGB accepts kSRGB exact`() {
        assertTrue(isAlmostSRGB(SkNamedTransferFn.kSRGB))
    }

    @Test
    fun `isAlmostSRGB accepts kSRGB perturbed within 0_001`() {
        val perturbed = SkcmsTransferFunction(
            g = SkNamedTransferFn.kSRGB.g + 0.0005f,
            a = SkNamedTransferFn.kSRGB.a + 0.0005f,
            b = SkNamedTransferFn.kSRGB.b - 0.0005f,
            c = SkNamedTransferFn.kSRGB.c + 0.0005f,
            d = SkNamedTransferFn.kSRGB.d - 0.0005f,
            e = SkNamedTransferFn.kSRGB.e,
            f = SkNamedTransferFn.kSRGB.f,
        )
        assertTrue(isAlmostSRGB(perturbed))
    }

    @Test
    fun `isAlmostSRGB rejects kLinear`() {
        assertFalse(isAlmostSRGB(SkNamedTransferFn.kLinear))
    }

    @Test
    fun `isAlmost2Dot2 accepts k2Dot2 exact`() {
        assertTrue(isAlmost2Dot2(SkNamedTransferFn.k2Dot2))
    }

    @Test
    fun `isAlmost2Dot2 rejects 2_4 power`() {
        val pure24 = SkcmsTransferFunction(g = 2.4f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f)
        assertFalse(isAlmost2Dot2(pure24))
    }

    @Test
    fun `isAlmostLinear accepts kLinear in both forms`() {
        // Form 1: y = x^1
        val expForm = SkcmsTransferFunction(g = 1f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f)
        assertTrue(isAlmostLinear(expForm))

        // Form 2: y = 1*x + 0 (with d ≥ 1 so the linear branch always wins)
        val linearForm = SkcmsTransferFunction(g = 0f, a = 0f, b = 0f, c = 1f, d = 1f, e = 0f, f = 0f)
        assertTrue(isAlmostLinear(linearForm))
    }

    @Test
    fun `isAlmostLinear rejects kSRGB and k2Dot2`() {
        assertFalse(isAlmostLinear(SkNamedTransferFn.kSRGB))
        assertFalse(isAlmostLinear(SkNamedTransferFn.k2Dot2))
    }

    // -----------------------------------------------------------------------
    // makeRGB snap behaviour
    // -----------------------------------------------------------------------

    @Test
    fun `makeRGB snaps perturbed kSRGB plus perturbed kSRGB-gamut to sRGB singleton`() {
        // Inputs noisy by < TF_tolerance (0.001) and < gamut_tolerance (0.01).
        val noisyTf = SkcmsTransferFunction(
            g = SkNamedTransferFn.kSRGB.g + 0.0005f,
            a = SkNamedTransferFn.kSRGB.a - 0.0005f,
            b = SkNamedTransferFn.kSRGB.b,
            c = SkNamedTransferFn.kSRGB.c,
            d = SkNamedTransferFn.kSRGB.d + 0.0005f,
            e = 0f,
            f = 0f,
        )
        val noisyGamut = SkcmsMatrix3x3(Array(3) { r ->
            FloatArray(3) { c -> SkNamedGamut.kSRGB.vals[r][c] + 0.005f }
        })
        val cs = SkColorSpace.makeRGB(noisyTf, noisyGamut)
        assertSame(SkColorSpace.makeSRGB(), cs)
    }

    @Test
    fun `makeRGB snaps perturbed kLinear plus perturbed kSRGB-gamut to sRGB-linear singleton`() {
        val noisyLinear = SkcmsTransferFunction(
            g = 1.0005f, a = 0.9995f, b = 0.0005f, c = 0f, d = 0f, e = 0f, f = 0f,
        )
        val cs = SkColorSpace.makeRGB(noisyLinear, SkNamedGamut.kSRGB)
        assertSame(SkColorSpace.makeSRGBLinear(), cs)
    }

    @Test
    fun `makeRGB snaps quasi-sRGB TF without snapping a non-sRGB gamut`() {
        // TF snappable but gamut is Rec.2020 — fresh instance, but TF is kSRGB.
        val noisyTf = SkcmsTransferFunction(
            g = SkNamedTransferFn.kSRGB.g + 0.0005f,
            a = SkNamedTransferFn.kSRGB.a,
            b = SkNamedTransferFn.kSRGB.b,
            c = SkNamedTransferFn.kSRGB.c,
            d = SkNamedTransferFn.kSRGB.d,
            e = 0f, f = 0f,
        )
        val cs = SkColorSpace.makeRGB(noisyTf, SkNamedGamut.kRec2020)!!
        // Snapped TF — gammaCloseToSRGB() (which does an exact compare on
        // SkNamedTransferFn.kSRGB) returns true even though the input was noisy.
        assertTrue(cs.gammaCloseToSRGB(), "TF should be snapped to kSRGB exact")
    }

    @Test
    fun `makeRGB does not snap a TF too far from any named TF`() {
        // Diverge by 0.005 on every component → too far for the 0.001 TF tolerance.
        val notSnappable = SkcmsTransferFunction(
            g = SkNamedTransferFn.kSRGB.g + 0.005f,
            a = SkNamedTransferFn.kSRGB.a + 0.005f,
            b = SkNamedTransferFn.kSRGB.b,
            c = SkNamedTransferFn.kSRGB.c,
            d = SkNamedTransferFn.kSRGB.d,
            e = 0f, f = 0f,
        )
        val cs = SkColorSpace.makeRGB(notSnappable, SkNamedGamut.kSRGB)!!
        assertFalse(cs.gammaCloseToSRGB(), "TF too far from kSRGB; should not snap")
    }
}
