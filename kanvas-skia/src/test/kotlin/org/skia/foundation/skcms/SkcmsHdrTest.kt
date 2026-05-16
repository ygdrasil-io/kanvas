package org.skia.foundation.skcms
import org.skia.math.SkcmsTransferFunction

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase I of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the HDR transfer
 * function support added to [Skcms.kt]: classify sentinels, eval/invert
 * branches for PQ / PQish / HLG / HLGish / HLGinvish, and the named-TF
 * makers. Ground-truth values come from a standalone C++ driver
 * (`tools/hdr_test.cpp`) that mirrors `skcms.cc:135-296, 1983-2007`.
 */
class SkcmsHdrTest {

    @Test
    fun `tfKindMarker matches negated ordinal`() {
        assertEquals(-SkcmsTFType.PQish.ordinal.toFloat(), tfKindMarker(SkcmsTFType.PQish))
        assertEquals(-SkcmsTFType.HLGish.ordinal.toFloat(), tfKindMarker(SkcmsTFType.HLGish))
        assertEquals(-SkcmsTFType.HLGinvish.ordinal.toFloat(), tfKindMarker(SkcmsTFType.HLGinvish))
        assertEquals(-SkcmsTFType.PQ.ordinal.toFloat(), tfKindMarker(SkcmsTFType.PQ))
        assertEquals(-SkcmsTFType.HLG.ordinal.toFloat(), tfKindMarker(SkcmsTFType.HLG))
    }

    @Test
    fun `classify returns the right kind for each sentinel`() {
        assertEquals(SkcmsTFType.PQish,
            classify(skcmsTransferFunctionMakePQish(1f, 0f, 0f, 1f, 0f, 1f)))
        assertEquals(SkcmsTFType.HLGish,
            classify(skcmsTransferFunctionMakeHLGish(2f, 2f, 5.5f, 0.28f, 0.56f)))
        assertEquals(SkcmsTFType.PQ, classify(skcmsTransferFunctionMakePQ(203f)))
        assertEquals(SkcmsTFType.HLG, classify(skcmsTransferFunctionMakeHLG(203f, 1000f, 1.2f)))
    }

    @Test
    fun `classify rejects fractional negative g`() {
        // g = -2.5 is not representable as -enum_g for any whole enum_g.
        val tf = SkcmsTransferFunction(-2.5f, 1f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SkcmsTFType.Invalid, classify(tf))
    }

    @Test
    fun `classify rejects very large negative g`() {
        val tf = SkcmsTransferFunction(-200f, 1f, 0f, 0f, 0f, 0f, 0f)
        assertEquals(SkcmsTFType.Invalid, classify(tf))
    }

    @Test
    fun `classify rejects raw PQ with non-zero b through f`() {
        // A raw PQ requires b=c=d=e=f=0 per skcms.cc:165-167.
        val tf = SkcmsTransferFunction(tfKindMarker(SkcmsTFType.PQ), 203f, 0.5f, 0f, 0f, 0f, 0f)
        assertEquals(SkcmsTFType.Invalid, classify(tf))
    }

    @Test
    fun `classify rejects raw HLG with non-zero d through f`() {
        // HLG requires d=e=f=0 per skcms.cc:170-172.
        val tf = SkcmsTransferFunction(tfKindMarker(SkcmsTFType.HLG), 203f, 1000f, 1.2f, 0.5f, 0f, 0f)
        assertEquals(SkcmsTFType.Invalid, classify(tf))
    }

    // ----- eval -----

    @Test
    fun `PQ eval matches upstream ground truth`() {
        val pq = SkNamedTransferFn.kPQ  // refWhite=203
        // PQ(0.0) is NaN upstream (p=0 → (-c1/c2)^(1/m1) with negative base).
        assertTrue(skcmsTransferFunctionEval(pq, 0.0f).isNaN())
        // Spot values from the C++ driver. Tolerance 5e-6 covers the
        // double-vs-float drift between Kotlin's Math.pow (double internal)
        // and C powf (float internal) on stacked exponentiations.
        assertNear(0.000515420f, skcmsTransferFunctionEval(pq, 0.25f), 5e-6f)
        assertNear(0.009224533f, skcmsTransferFunctionEval(pq, 0.50f), 5e-6f)
        assertNear(0.098336257f, skcmsTransferFunctionEval(pq, 0.75f), 5e-6f)
        assertNear(1.000000000f, skcmsTransferFunctionEval(pq, 1.00f), 5e-6f)
    }

    @Test
    fun `HLG eval matches upstream ground truth`() {
        val hlg = SkNamedTransferFn.kHLG  // ref=203, peak=1000, sysGamma=1.2
        assertEquals(0f, skcmsTransferFunctionEval(hlg, 0f))
        assertNear(0.020833334f, skcmsTransferFunctionEval(hlg, 0.25f), 1e-7f)
        assertNear(0.083333336f, skcmsTransferFunctionEval(hlg, 0.50f), 1e-7f)
        assertNear(0.264962584f, skcmsTransferFunctionEval(hlg, 0.75f), 1e-7f)
        // HLG(1.0) is 1.000000119 in the C ref — float drift of one ulp.
        assertNear(1.0f, skcmsTransferFunctionEval(hlg, 1.0f), 2e-7f)
    }

    @Test
    fun `PQish parametric matches PQ at the same x`() {
        // PQish with the BT.2100 standard params reproduces raw PQ.
        val pqish = skcmsTransferFunctionMakePQish(
            -107f / 128f, 1f, 32f / 2523f,
            2413f / 128f, -2392f / 128f, 8192f / 1305f,
        )
        assertNear(0.009224533f, skcmsTransferFunctionEval(pqish, 0.5f), 5e-6f)
        // x close to 1 amplifies the double-vs-float drift through the
        // stacked pow chain; relax tolerance to 1e-4.
        assertNear(0.990488529f, skcmsTransferFunctionEval(pqish, 0.999f), 1e-4f)
    }

    @Test
    fun `HLGish linear region returns exact x at K=1`() {
        // HLGish with R=2, G=2 in linear region: K * (xR)^G = 1*(0.5)^2 = 0.25
        val hlgish = skcmsTransferFunctionMakeHLGish(
            R = 2f, G = 2f, a = 1f / 0.17883277f, b = 0.28466892f, c = 0.55991073f,
        )
        assertNear(0.25f, skcmsTransferFunctionEval(hlgish, 0.25f), 1e-7f)
    }

    // ----- invert -----

    @Test
    fun `invert PQ returns null`() {
        // Raw PQ has no closed-form inverse; only PQish does.
        assertNull(skcmsTransferFunctionInvert(SkNamedTransferFn.kPQ))
    }

    @Test
    fun `invert HLG returns null`() {
        assertNull(skcmsTransferFunctionInvert(SkNamedTransferFn.kHLG))
    }

    @Test
    fun `invert PQish gives a PQish back with mangled params`() {
        val pqish = skcmsTransferFunctionMakePQish(1f, 2f, 3f, 4f, 5f, 6f)
        val inv = skcmsTransferFunctionInvert(pqish)!!
        assertEquals(SkcmsTFType.PQish, classify(inv))
        // Spec: { -A, D, 1/F, B, -E, 1/C }.
        assertEquals(-1f, inv.a)
        assertEquals(4f, inv.b)
        assertNear(1f / 6f, inv.c, 1e-6f)
        assertEquals(2f, inv.d)
        assertEquals(-5f, inv.e)
        assertNear(1f / 3f, inv.f, 1e-6f)
    }

    @Test
    fun `invert HLGish flips to HLGinvish with reciprocal R G a`() {
        val hlgish = skcmsTransferFunctionMakeHLGish(R = 2f, G = 4f, a = 5f, b = 0.3f, c = 0.6f)
        val inv = skcmsTransferFunctionInvert(hlgish)!!
        assertEquals(SkcmsTFType.HLGinvish, classify(inv))
        assertNear(0.5f, inv.a, 1e-7f)   // 1/R
        assertNear(0.25f, inv.b, 1e-7f)  // 1/G
        assertNear(0.2f, inv.c, 1e-7f)   // 1/a
        assertEquals(0.3f, inv.d)
        assertEquals(0.6f, inv.e)
        assertEquals(0f, inv.f)  // K_minus_1 preserved
    }

    @Test
    fun `invert HLGinvish flips back to HLGish`() {
        val invish = SkcmsTransferFunction(
            tfKindMarker(SkcmsTFType.HLGinvish),
            a = 0.5f, b = 0.25f, c = 0.2f, d = 0.3f, e = 0.6f, f = 0f,
        )
        val back = skcmsTransferFunctionInvert(invish)!!
        assertEquals(SkcmsTFType.HLGish, classify(back))
        assertNear(2f, back.a, 1e-6f)
        assertNear(4f, back.b, 1e-6f)
        assertNear(5f, back.c, 1e-6f)
    }

    @Test
    fun `HLGish then HLGinvish round-trips exactly at K=1`() {
        val hlgish = skcmsTransferFunctionMakeHLGish(
            R = 2f, G = 2f, a = 1f / 0.17883277f, b = 0.28466892f, c = 0.55991073f,
        )
        val invish = skcmsTransferFunctionInvert(hlgish)!!
        // x in linear region 0..0.25.
        for (x in floatArrayOf(0.05f, 0.1f, 0.15f, 0.2f, 0.25f)) {
            val y = skcmsTransferFunctionEval(hlgish, x)
            val back = skcmsTransferFunctionEval(invish, y)
            assertNear(x, back, 1e-5f)
        }
        // x in non-linear region.
        for (x in floatArrayOf(0.4f, 0.6f, 0.8f, 0.95f)) {
            val y = skcmsTransferFunctionEval(hlgish, x)
            val back = skcmsTransferFunctionEval(invish, y)
            assertNear(x, back, 1e-4f)
        }
    }

    @Test
    fun `PQish then PQish-inv round-trips`() {
        // Use a synthetic well-conditioned PQish: A=1, C=1, F=1 so the
        // pow chain stays smooth.
        val pqish = skcmsTransferFunctionMakePQish(1f, 1f, 1f, 1f, 0.5f, 1f)
        val inv = skcmsTransferFunctionInvert(pqish)!!
        for (x in floatArrayOf(0.1f, 0.3f, 0.5f, 0.7f, 0.9f)) {
            val y = skcmsTransferFunctionEval(pqish, x)
            val back = skcmsTransferFunctionEval(inv, y)
            assertNear(x, back, 1e-4f)
        }
    }

    @Test
    fun `sRGBish branch is unchanged by the HDR rewrite`() {
        // Regression: the existing sRGB round-trip must stay exact.
        val srgb = SkNamedTransferFn.kSRGB
        val inv = skcmsTransferFunctionInvert(srgb)!!
        assertEquals(SkcmsTFType.sRGBish, classify(inv))
        assertEquals(1f, skcmsTransferFunctionEval(inv, skcmsTransferFunctionEval(srgb, 1f)), 1e-6f)
        assertEquals(0f, skcmsTransferFunctionEval(inv, skcmsTransferFunctionEval(srgb, 0f)), 1e-6f)
    }

    @Test
    fun `make PQ and HLG named TFs differ from each other`() {
        // Sanity: the six HDR makers each produce a distinct kind.
        assertNotEquals(SkNamedTransferFn.kPQ, SkNamedTransferFn.kHLG)
        assertNotEquals(classify(SkNamedTransferFn.kPQ), classify(SkNamedTransferFn.kHLG))
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float) {
        assertTrue(kotlin.math.abs(expected - actual) <= tol,
            "expected $expected ± $tol, got $actual (diff ${actual - expected})")
    }
}
