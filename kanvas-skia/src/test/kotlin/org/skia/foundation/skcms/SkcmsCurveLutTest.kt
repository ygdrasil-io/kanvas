package org.skia.foundation.skcms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

/**
 * Phase F3 of MIGRATION_PLAN_COLORSPACE_PORT.md — verify LUT-curve
 * evaluation, the `minus_1_ulp` boundary trick, and the round-trip
 * approximate-inverse helpers.
 *
 * Ground-truth values come from a standalone C++ driver
 * (`tools/curve_lut_test.cpp`) that mirrors `skcms.cc:113-119, 302-326`
 * verbatim.
 */
class SkcmsCurveLutTest {

    // ----- minus1Ulp -----

    @Test
    fun `minus1Ulp on a positive float decreases by exactly one bit`() {
        val v = 1.0f
        val less = minus1Ulp(v)
        assertTrue(less < v, "minus1Ulp must move toward zero")
        // The next float toward zero from 1.0 is 0.99999994.
        assertEquals(0.99999994f, less, 0f)
    }

    @Test
    fun `minus1Ulp on integer-aligned table boundary keeps lo eq hi inside the array`() {
        // For ix == 3.0f exactly, ix.toInt() == 3 and minus1Ulp(4.0f).toInt()
        // must also == 3 (not 4) — that's the whole point of the trick.
        val ix = 3f
        val lo = ix.toInt()
        val hi = minus1Ulp(ix + 1f).toInt()
        assertEquals(lo, hi)
        assertEquals(3, hi)
    }

    // ----- evalCurve: parametric -----

    @Test
    fun `evalCurve on a Parametric delegates to skcmsTransferFunctionEval`() {
        val curve = SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB)
        val direct = skcmsTransferFunctionEval(SkNamedTransferFn.kSRGB, 0.5f)
        val viaCurve = evalCurve(curve, 0.5f)
        assertEquals(direct, viaCurve, 0f)
    }

    @Test
    fun `evalCurve on a Parametric Linear is identity for x in 0_1`() {
        val curve = SkcmsCurve.Parametric(SkNamedTransferFn.kLinear)
        for (x in floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            assertEquals(x, evalCurve(curve, x), 1e-7f)
        }
    }

    // ----- evalCurve: Table8 -----

    @Test
    fun `evalCurve on a 4-entry Table8 matches upstream ground truth`() {
        // Driver values for table8 = [0, 64, 128, 255].
        val curve = SkcmsCurve.Table(
            tableEntries = 4,
            table8 = byteArrayOf(0, 64, 128.toByte(), 255.toByte()),
        )
        assertNear(0.000000000f, evalCurve(curve, 0.00f), 1e-7f)
        assertNear(0.188235313f, evalCurve(curve, 0.25f), 1e-6f)
        assertNear(0.376470625f, evalCurve(curve, 0.50f), 1e-6f)
        assertNear(0.626470625f, evalCurve(curve, 0.75f), 1e-6f)
        assertNear(1.000000000f, evalCurve(curve, 1.00f), 1e-7f)
        assertNear(0.075294122f, evalCurve(curve, 0.10f), 1e-6f)
        assertNear(0.301176488f, evalCurve(curve, 0.40f), 1e-6f)
    }

    @Test
    fun `evalCurve on Table8 clamps out-of-range x to 0_1`() {
        val curve = SkcmsCurve.Table(
            tableEntries = 4,
            table8 = byteArrayOf(0, 64, 128.toByte(), 255.toByte()),
        )
        assertEquals(0f, evalCurve(curve, -0.5f), 0f)
        assertEquals(1f, evalCurve(curve, 1.5f), 0f)
    }

    // ----- evalCurve: Table16 (big-endian) -----

    @Test
    fun `evalCurve on a 4-entry Table16 reads big-endian uint16 correctly`() {
        // table16 entries: 0x0000, 0x4000, 0x8000, 0xFFFF (= 0/0.25/0.5/1.0
        // approximately). Stored big-endian: 8 bytes.
        val curve = SkcmsCurve.Table(
            tableEntries = 4,
            table16 = byteArrayOf(
                0x00, 0x00,
                0x40, 0x00,
                0x80.toByte(), 0x00,
                0xFF.toByte(), 0xFF.toByte(),
            ),
        )
        assertNear(0.000000000f, evalCurve(curve, 0.00f), 1e-7f)
        assertNear(0.187502861f, evalCurve(curve, 0.25f), 1e-6f)
        assertNear(0.250003815f, evalCurve(curve, 1f / 3f), 1e-6f)
        assertNear(0.375005722f, evalCurve(curve, 0.50f), 1e-6f)
        assertNear(0.625005722f, evalCurve(curve, 0.75f), 1e-6f)
        assertNear(1.000000000f, evalCurve(curve, 1.00f), 1e-7f)
    }

    @Test
    fun `evalCurve byte-swap is the only thing distinguishing BE from LE`() {
        // 0x0001 written big-endian → bytes [0x00, 0x01], decoded value = 1.
        // If we read it little-endian by mistake we'd get 0x0100 = 256.
        // 1/65535 ≈ 1.526e-5. 256/65535 ≈ 0.00391.
        val curve = SkcmsCurve.Table(
            tableEntries = 2,
            table16 = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0xFF.toByte()),
        )
        assertNear(1f / 65535f, evalCurve(curve, 0f), 1e-9f)
    }

    // ----- approximate inverses -----

    @Test
    fun `parametric sRGB curve is approximate inverse of its analytic inverse`() {
        val curve = SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB)
        val inv = skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)!!
        assertTrue(skcmsAreApproximateInverses(curve, inv),
            "round-trip error must be < 1/512")
    }

    @Test
    fun `Linear curve is its own inverse`() {
        val curve = SkcmsCurve.Parametric(SkNamedTransferFn.kLinear)
        assertTrue(skcmsAreApproximateInverses(curve, SkNamedTransferFn.kLinear))
    }

    @Test
    fun `unrelated TFs are not approximate inverses`() {
        // sRGB curve vs the inverse of 2.2-power: round-trip drifts well
        // beyond 1/512 (large `pow` mismatch).
        val curve = SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB)
        val unrelated = skcmsTransferFunctionInvert(SkNamedTransferFn.k2Dot2)!!
        assertFalse(skcmsAreApproximateInverses(curve, unrelated))
    }

    @Test
    fun `256-entry sampled sRGB encode round-trips through the analytic decoder`() {
        // SkNamedTransferFn.kSRGB is the *decoder* (encoded → linear). Its
        // inverse is the encoder (linear → encoded). Sampling the encoder
        // into a LUT and then decoding gives back the original linear value
        // up to 8-bit quantization noise.
        val encoder = skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)!!
        val table = ByteArray(256) { i ->
            val L = i.toFloat() / 255f
            val E = skcmsTransferFunctionEval(encoder, L)
            (E * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        val curve = SkcmsCurve.Table(tableEntries = 256, table8 = table)
        // round-trip: linear x → curve(x) (encoded byte) → kSRGB(encoded) (linear)
        val err = skcmsMaxRoundtripError(curve, SkNamedTransferFn.kSRGB)
        // 8-bit quantization sets the floor a bit above 1/512 (~0.002); but
        // it should be well within 1/64 (~0.0156).
        assertTrue(err < 1f / 64f, "error within 1/64, got $err")
        assertTrue(err > 0f, "8-bit sampling shouldn't be exact")
    }

    // ----- skcmsTRCsAreApproximateInverse on a profile -----

    @Test
    fun `TRCs check returns false for a profile with no TRC`() {
        val profile = SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOfNulls(3),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = false,
            hasToXYZD50 = true,
        )
        assertFalse(skcmsTRCsAreApproximateInverse(profile, SkNamedTransferFn.kSRGB))
    }

    @Test
    fun `TRCs check returns true when all 3 TRCs are sRGB and inv_tf is sRGB-inverse`() {
        val profile = SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        val inv = skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)!!
        assertTrue(skcmsTRCsAreApproximateInverse(profile, inv))
    }

    @Test
    fun `TRCs check returns false when one TRC disagrees with inv_tf`() {
        val profile = SkcmsICCProfile(
            buffer = ByteArray(0),
            tagCount = 0,
            trc = arrayOf(
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
                SkcmsCurve.Parametric(SkNamedTransferFn.k2Dot2),  // odd one out
                SkcmsCurve.Parametric(SkNamedTransferFn.kSRGB),
            ),
            toXYZD50 = SkNamedGamut.kSRGB,
            hasTrc = true,
            hasToXYZD50 = true,
        )
        val inv = skcmsTransferFunctionInvert(SkNamedTransferFn.kSRGB)!!
        assertFalse(skcmsTRCsAreApproximateInverse(profile, inv))
    }

    private fun assertNear(expected: Float, actual: Float, tol: Float) {
        assertTrue(kotlin.math.abs(expected - actual) <= tol,
            "expected $expected ± $tol, got $actual (diff ${actual - expected})")
    }
}
