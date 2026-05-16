package org.skia.foundation.skcms

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Phase E of MIGRATION_PLAN_COLORSPACE_PORT.md — exercise
 * `skcmsAdaptToXYZD50`, `skcmsPrimariesToXYZD50`, and `skcmsMv3Mul`.
 */
class SkcmsPrimariesTest {

    @Test
    fun `mv3Mul on identity is the input vector`() {
        val v = floatArrayOf(0.1f, 0.5f, 0.9f)
        val out = skcmsMv3Mul(SkcmsMatrix3x3.IDENTITY, v)
        for (i in 0 until 3) assertEquals(v[i], out[i])
    }

    @Test
    fun `mv3Mul rejects non-3 vector`() {
        try {
            skcmsMv3Mul(SkcmsMatrix3x3.IDENTITY, floatArrayOf(1f, 2f))
            org.junit.jupiter.api.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun `adaptToXYZD50 D50 white point is identity`() {
        // D50 white point in xy = (0.34567, 0.35850)
        val out = skcmsAdaptToXYZD50(0.34567f, 0.35850f)
        assertNotNull(out)
        // Diagonal ≈ 1, off-diagonal ≈ 0 (with float-precision slack).
        for (r in 0 until 3) for (c in 0 until 3) {
            val want = if (r == c) 1f else 0f
            assertTrue(abs(out!!.vals[r][c] - want) < 1e-3f,
                "[$r][$c] = ${out.vals[r][c]} expected ~$want")
        }
    }

    @Test
    fun `adaptToXYZD50 D65 produces a non-identity matrix`() {
        // D65 white point xy = (0.3127, 0.3290)
        val out = skcmsAdaptToXYZD50(0.3127f, 0.3290f)
        assertNotNull(out)
        // Should diverge from identity by > 1e-3.
        var maxDiag = 0f
        for (r in 0 until 3) {
            maxDiag = maxOf(maxDiag, abs(out!!.vals[r][r] - 1f))
        }
        assertTrue(maxDiag > 1e-3f, "D65→D50 should be non-trivial")
    }

    @Test
    fun `adaptToXYZD50 rejects out-of-range white point`() {
        assertNull(skcmsAdaptToXYZD50(-0.1f, 0.5f))
        assertNull(skcmsAdaptToXYZD50(0.5f, 1.5f))
    }

    @Test
    fun `primariesToXYZD50 of sRGB primaries roughly matches kSRGB`() {
        // sRGB (Rec.709) primaries with D65 white point.
        val out = skcmsPrimariesToXYZD50(
            0.64f, 0.33f,    // R
            0.3f,  0.6f,     // G
            0.15f, 0.06f,    // B
            0.3127f, 0.329f, // W (D65)
        )
        assertNotNull(out)
        // Compare with the canonical SkNamedGamut.kSRGB matrix (cell-by-cell
        // tolerance 0.01 — same as upstream `xyz_almost_equal`).
        for (r in 0 until 3) for (c in 0 until 3) {
            val want = SkNamedGamut.kSRGB.vals[r][c]
            val got = out!!.vals[r][c]
            assertTrue(abs(got - want) < 0.01f,
                "[$r][$c] expected ~$want, got $got")
        }
    }

    @Test
    fun `primariesToXYZD50 of Rec_2020 roughly matches kRec2020`() {
        val out = skcmsPrimariesToXYZD50(
            0.708f, 0.292f,
            0.170f, 0.797f,
            0.131f, 0.046f,
            0.3127f, 0.3290f,
        )
        assertNotNull(out)
        for (r in 0 until 3) for (c in 0 until 3) {
            val want = SkNamedGamut.kRec2020.vals[r][c]
            val got = out!!.vals[r][c]
            assertTrue(abs(got - want) < 0.01f,
                "[$r][$c] expected ~$want, got $got")
        }
    }

    @Test
    fun `primariesToXYZD50 rejects out-of-range inputs`() {
        assertNull(skcmsPrimariesToXYZD50(-0.1f, 0.33f, 0.3f, 0.6f, 0.15f, 0.06f, 0.3127f, 0.329f))
        assertNull(skcmsPrimariesToXYZD50(0.64f, 0.33f, 0.3f, 0.6f, 0.15f, 0.06f, 1.5f, 0.329f))
    }
}
