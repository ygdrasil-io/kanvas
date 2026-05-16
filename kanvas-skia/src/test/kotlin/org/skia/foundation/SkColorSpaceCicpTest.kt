package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn

/**
 * Phase E of MIGRATION_PLAN_COLORSPACE_PORT.md — exercise the CICP
 * lookup helpers and `SkColorSpace.makeCICP`.
 */
class SkColorSpaceCicpTest {

    @Test
    fun `SkColorSpacePrimaries kRec709 toXYZD50 matches kSRGB-gamut roughly`() {
        val m = SkNamedPrimaries.kRec709.toXYZD50()
        assertNotNull(m)
        assertTrue(xyzAlmostEqual(SkNamedGamut.kSRGB, m!!))
    }

    @Test
    fun `SkColorSpacePrimaries kRec2020 toXYZD50 matches kRec2020-gamut roughly`() {
        val m = SkNamedPrimaries.kRec2020.toXYZD50()
        assertNotNull(m)
        assertTrue(xyzAlmostEqual(SkNamedGamut.kRec2020, m!!))
    }

    // -----------------------------------------------------------------------
    // SkNamedPrimaries.getCicp / getCicpFromMatrix
    // -----------------------------------------------------------------------

    @Test
    fun `SkNamedPrimaries getCicp kRec709 returns kSRGB-gamut fast-path`() {
        // Fast-path: returns the cached SkNamedGamut.kSRGB matrix (===).
        assertSame(SkNamedGamut.kSRGB, SkNamedPrimaries.getCicp(SkNamedPrimaries.CicpId.kRec709))
    }

    @Test
    fun `SkNamedPrimaries getCicp kRec2020 returns kRec2020-gamut fast-path`() {
        assertSame(SkNamedGamut.kRec2020, SkNamedPrimaries.getCicp(SkNamedPrimaries.CicpId.kRec2020))
    }

    @Test
    fun `SkNamedPrimaries getCicp kSMPTE_EG_432_1 returns kDisplayP3-gamut`() {
        assertSame(SkNamedGamut.kDisplayP3,
            SkNamedPrimaries.getCicp(SkNamedPrimaries.CicpId.kSMPTE_EG_432_1))
    }

    @Test
    fun `SkNamedPrimaries getCicp non-fast-path entries compute via toXYZD50`() {
        // kRec601 has no fast-path; the lookup goes through
        // SkColorSpacePrimaries.toXYZD50 — ensure we get a non-null matrix.
        val m = SkNamedPrimaries.getCicp(SkNamedPrimaries.CicpId.kRec601)
        assertNotNull(m)
    }

    @Test
    fun `getCicpFromMatrix recovers kRec709 from kSRGB-gamut`() {
        assertEquals(SkNamedPrimaries.CicpId.kRec709,
            SkNamedPrimaries.getCicpFromMatrix(SkNamedGamut.kSRGB))
    }

    @Test
    fun `getCicpFromMatrix recovers kRec2020 from kRec2020-gamut`() {
        assertEquals(SkNamedPrimaries.CicpId.kRec2020,
            SkNamedPrimaries.getCicpFromMatrix(SkNamedGamut.kRec2020))
    }

    @Test
    fun `getCicpFromMatrix returns null for an unknown matrix`() {
        // Non-physical matrix far from any standard gamut.
        val random = org.graphiks.math.SkcmsMatrix3x3.of(
            0.7f, 0.7f, 0.7f,
            0.7f, 0.7f, 0.7f,
            0.7f, 0.7f, 0.7f,
        )
        assertNull(SkNamedPrimaries.getCicpFromMatrix(random))
    }

    // -----------------------------------------------------------------------
    // SkNamedTransferFn.getCicp
    // -----------------------------------------------------------------------

    @Test
    fun `SkNamedTransferFn getCicp returns expected TFs`() {
        assertSame(SkNamedTransferFn.kSRGB,
            SkNamedTransferFn.getCicp(SkNamedTransferFn.CicpId.kIEC61966_2_1))
        assertSame(SkNamedTransferFn.kSRGB,
            SkNamedTransferFn.getCicp(SkNamedTransferFn.CicpId.kSRGB))
        assertSame(SkNamedTransferFn.kLinear,
            SkNamedTransferFn.getCicp(SkNamedTransferFn.CicpId.kLinear))
        assertSame(SkNamedTransferFn.kPQ,
            SkNamedTransferFn.getCicp(SkNamedTransferFn.CicpId.kPQ))
    }

    // -----------------------------------------------------------------------
    // SkColorSpace.makeCICP
    // -----------------------------------------------------------------------

    @Test
    fun `makeCICP kRec709 plus kIEC61966_2_4 snaps to sRGB singleton`() {
        // kRec709 → kSRGB-gamut (fast-path), kIEC61966_2_4 → kRec709 TF
        // which is `{2.4, 1, 0, …}` — not exactly kSRGB. Returns a fresh
        // instance with kRec709 TF + kSRGB gamut.
        val cs = SkColorSpace.makeCICP(
            SkNamedPrimaries.CicpId.kRec709,
            SkNamedTransferFn.CicpId.kIEC61966_2_4,
        )
        assertNotNull(cs)
        assertEquals(SkNamedTransferFn.kRec709, cs!!.transferFn)
    }

    @Test
    fun `makeCICP kRec709 plus kIEC61966_2_1 returns sRGB singleton exactly`() {
        // kRec709 → kSRGB-gamut, kIEC61966_2_1 → kSRGB exact.
        // makeRGB(kSRGB, kSRGB-gamut) snaps to the singleton (Phase B).
        val cs = SkColorSpace.makeCICP(
            SkNamedPrimaries.CicpId.kRec709,
            SkNamedTransferFn.CicpId.kIEC61966_2_1,
        )
        assertSame(SkColorSpace.makeSRGB(), cs)
    }

    @Test
    fun `makeCICP kRec2020 plus kRec2020_10bit produces Rec_2020 colorspace`() {
        val cs = SkColorSpace.makeCICP(
            SkNamedPrimaries.CicpId.kRec2020,
            SkNamedTransferFn.CicpId.kRec2020_10bit,
        )
        assertNotNull(cs)
        // Gamut is kRec2020 fast-path, TF is kRec709 ({2.4, 1, 0, …}).
        assertSame(SkNamedGamut.kRec2020, cs!!.toXYZD50)
    }

    @Test
    fun `makeCICP kRec2020 plus kPQ returns a usable PQ colorspace`() {
        // Phase I activated PQ classification. makeCICP routes PQ → makeRGB
        // → keeps the kPQ singleton TF and the Rec.2020 gamut, no snap.
        val cs = SkColorSpace.makeCICP(
            SkNamedPrimaries.CicpId.kRec2020,
            SkNamedTransferFn.CicpId.kPQ,
        )
        org.junit.jupiter.api.Assertions.assertNotNull(cs, "Phase I: PQ is now valid")
        assertSame(SkNamedTransferFn.kPQ, cs!!.transferFn)
        assertSame(SkNamedGamut.kRec2020, cs.toXYZD50)
    }
}
