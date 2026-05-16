package org.skia.foundation.skcms
import org.graphiks.math.SkcmsMatrix3x3

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.pow

/**
 * Phase 1 unit tests for the skcms foundation port. Each test independently
 * checks one piece of the contract; combined they ensure the full sRGB →
 * Rec.2020 round-trip we'll need in later phases is bit-stable.
 */
class SkcmsFoundationTest {

    @Test
    fun `kSRGB classifies as sRGBish`() {
        assertEquals(SkcmsTFType.sRGBish, classify(SkNamedTransferFn.kSRGB))
    }

    @Test
    fun `kLinear is identity over the unit interval`() {
        for (x in listOf(0f, 0.1f, 0.5f, 0.9f, 1f)) {
            val y = skcmsTransferFunctionEval(SkNamedTransferFn.kLinear, x)
            assertTrue(abs(y - x) < 1e-6f, "kLinear($x) = $y, expected $x")
        }
    }

    @Test
    fun `eval kSRGB at known sample points matches sRGB EOTF`() {
        // Reference values from the canonical sRGB EOTF:
        //   x = 0.5 → 0.21404 (well-known)
        //   x = 0.04045 (boundary) → linear * 0.04045 / 12.92 = 0.003130668…
        //   x = 1.0 → 1.0 (TF is normalized so eval(1) == 1)
        assertNear(0.21404f, skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, 0.5f), 1e-3f)
        assertNear(0.04045f / 12.92f, skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, 0.04045f), 1e-5f)
        assertNear(1f, skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, 1f), 1e-5f)
        assertNear(0f, skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, 0f), 1e-7f)
    }

    @Test
    fun `kRec2020 round-trips with its inverse`() {
        val inv = skcmsTransferFunctionInvert(SkNamedTransferFn.kRec2020)
        assertNotNull(inv, "kRec2020 must be invertible")
        for (x in listOf(0f, 0.05f, 0.0812f, 0.5f, 0.9f, 1f)) {
            val y = skcmsTransferFunctionEval(SkNamedTransferFn.kRec2020, x)
            val xBack = skcmsTransferFunctionEval(inv!!, y)
            assertTrue(abs(xBack - x) < 1f / 512f,
                "Rec.2020 round-trip failed at x=$x: y=$y, xBack=$xBack")
        }
    }

    @Test
    fun `inv eval src 1_0 equals 1_0 exactly`() {
        // The pin from `skcms_TransferFunction_invert`. This is the property
        // that lets us match Skia bit-for-bit on saturated colors.
        val inv = skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)!!
        val one = skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, 1f)
        val back = skcmsTransferFunctionEval(inv, one)
        assertEquals(1f, back, "inv must round-trip 1.0 exactly")
    }

    @Test
    fun `inverting kLinear yields kLinear-equivalent`() {
        val inv = skcmsTransferFunctionInvert(SkNamedTransferFn.kLinear)
        // kLinear has c=0,d=0 so the algorithm collapses the linear branch.
        // Either we get an Invalid (and skip), or we get a function that
        // round-trips. Both are acceptable; what we forbid is silently
        // returning a non-identity inverse.
        if (inv != null) {
            for (x in listOf(0f, 0.5f, 1f)) {
                val y = skcmsTransferFunctionEval(SkNamedTransferFn.kLinear, x)
                val back = skcmsTransferFunctionEval(inv, y)
                assertTrue(abs(back - x) < 1e-5f, "kLinear inv mismatch at $x")
            }
        }
    }

    @Test
    fun `Matrix3x3 concat with identity is identity`() {
        val m = SkNamedGamut.kSRGB
        val a = skcmsMatrix3x3Concat(SkcmsMatrix3x3.IDENTITY, m)
        val b = skcmsMatrix3x3Concat(m, SkcmsMatrix3x3.IDENTITY)
        for (r in 0 until 3) for (c in 0 until 3) {
            assertEquals(m.vals[r][c], a.vals[r][c], "I*M[$r][$c]")
            assertEquals(m.vals[r][c], b.vals[r][c], "M*I[$r][$c]")
        }
    }

    @Test
    fun `Matrix3x3 invert of identity is identity`() {
        val inv = skcmsMatrix3x3Invert(SkcmsMatrix3x3.IDENTITY)
        assertNotNull(inv)
        for (r in 0 until 3) for (c in 0 until 3) {
            // Use `==` rather than assertEquals: skcms cofactor expansion
            // produces -0.0f for some off-diagonal cells, and IEEE 754
            // considers 0.0 == -0.0 mathematically equal.
            val want = SkcmsMatrix3x3.IDENTITY.vals[r][c]
            val got = inv!!.vals[r][c]
            assertTrue(want == got, "[$r][$c] want=$want got=$got")
        }
    }

    @Test
    fun `Matrix3x3 invert of singular matrix returns null`() {
        val singular = SkcmsMatrix3x3.of(
            1f, 2f, 3f,
            2f, 4f, 6f,
            3f, 6f, 9f,
        )
        assertNull(skcmsMatrix3x3Invert(singular))
    }

    @Test
    fun `Matrix3x3 invert times original is identity`() {
        for ((label, m) in listOf("kSRGB" to SkNamedGamut.kSRGB, "kRec2020" to SkNamedGamut.kRec2020)) {
            val inv = skcmsMatrix3x3Invert(m) ?: error("$label not invertible")
            val product = skcmsMatrix3x3Concat(m, inv)
            for (r in 0 until 3) for (c in 0 until 3) {
                val want = if (r == c) 1f else 0f
                val got = product.vals[r][c]
                assertTrue(abs(got - want) < 1e-4f, "$label: M*M^-1[$r][$c] = $got, want $want")
            }
        }
    }

    @Test
    fun `sRGB to Rec_2020 pipeline reproduces (43, 13, 241) for pure blue`() {
        // Concrete end-to-end test that mirrors what Phase 5 will do via
        // SkColorSpaceXformSteps. Validates that all four primitives compose
        // correctly to produce the value we measured in bigrect.png.
        val srgbBlueLinear = floatArrayOf(0f, 0f, 1f)
        val xyz = matVec(SkNamedGamut.kSRGB, srgbBlueLinear)
        val rec2020Inv = skcmsMatrix3x3Invert(SkNamedGamut.kRec2020)!!
        val rec2020Linear = matVec(rec2020Inv, xyz)
        val invTF = skcmsTransferFunctionInvert(SkNamedTransferFn.kRec2020)!!
        val rec2020Encoded = floatArrayOf(
            skcmsTransferFunctionEval(invTF, rec2020Linear[0]),
            skcmsTransferFunctionEval(invTF, rec2020Linear[1]),
            skcmsTransferFunctionEval(invTF, rec2020Linear[2]),
        )
        val r = (rec2020Encoded[0] * 255f + 0.5f).toInt()
        val g = (rec2020Encoded[1] * 255f + 0.5f).toInt()
        val b = (rec2020Encoded[2] * 255f + 0.5f).toInt()
        assertEquals(43, r, "R channel")
        assertEquals(13, g, "G channel")
        assertTrue(b in 240..242, "B channel = $b, expected ~241")
    }

    private fun matVec(m: SkcmsMatrix3x3, v: FloatArray): FloatArray =
        FloatArray(3) { i -> m.vals[i][0] * v[0] + m.vals[i][1] * v[1] + m.vals[i][2] * v[2] }

    private fun assertNear(want: Float, got: Float, eps: Float) {
        assertTrue(abs(want - got) <= eps, "expected $want ± $eps, got $got")
    }
}
