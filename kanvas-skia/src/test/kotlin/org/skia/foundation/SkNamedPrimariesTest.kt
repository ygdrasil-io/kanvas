package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Phase D of MIGRATION_PLAN_COLORSPACE_PORT.md — verify the
 * `SkColorSpacePrimaries` data class and the `SkNamedPrimaries` constants.
 */
class SkNamedPrimariesTest {

    @Test
    fun `kRec709 has the canonical xy values`() {
        val p = SkNamedPrimaries.kRec709
        assertEquals(0.64f, p.fRX); assertEquals(0.33f, p.fRY)
        assertEquals(0.3f, p.fGX); assertEquals(0.6f, p.fGY)
        assertEquals(0.15f, p.fBX); assertEquals(0.06f, p.fBY)
        assertEquals(0.3127f, p.fWX); assertEquals(0.329f, p.fWY)
    }

    @Test
    fun `kRec2020 has the canonical xy values`() {
        val p = SkNamedPrimaries.kRec2020
        assertEquals(0.708f, p.fRX); assertEquals(0.292f, p.fRY)
        assertEquals(0.170f, p.fGX); assertEquals(0.797f, p.fGY)
        assertEquals(0.131f, p.fBX); assertEquals(0.046f, p.fBY)
        assertEquals(0.3127f, p.fWX); assertEquals(0.3290f, p.fWY)
    }

    @Test
    fun `kSMPTE_ST_240 is an alias of kRec601`() {
        assertSame(SkNamedPrimaries.kRec601, SkNamedPrimaries.kSMPTE_ST_240)
    }

    @Test
    fun `CicpId values match ITU-T H_273 Table 2`() {
        assertEquals(1, SkNamedPrimaries.CicpId.kRec709.value)
        assertEquals(4, SkNamedPrimaries.CicpId.kRec470SystemM.value)
        assertEquals(5, SkNamedPrimaries.CicpId.kRec470SystemBG.value)
        assertEquals(6, SkNamedPrimaries.CicpId.kRec601.value)
        assertEquals(7, SkNamedPrimaries.CicpId.kSMPTE_ST_240.value)
        assertEquals(8, SkNamedPrimaries.CicpId.kGenericFilm.value)
        assertEquals(9, SkNamedPrimaries.CicpId.kRec2020.value)
        assertEquals(10, SkNamedPrimaries.CicpId.kSMPTE_ST_428_1.value)
        assertEquals(11, SkNamedPrimaries.CicpId.kSMPTE_RP_431_2.value)
        assertEquals(12, SkNamedPrimaries.CicpId.kSMPTE_EG_432_1.value)
        assertEquals(22, SkNamedPrimaries.CicpId.kITU_T_H273_Value22.value)
    }

    @Test
    fun `kCicpIdApplicationDefined is 2 per the spec`() {
        assertEquals(2, SkNamedPrimaries.CicpId.kCicpIdApplicationDefined)
    }

    @Test
    fun `toXYZD50 returns a 3x3 matrix for kRec709 (Phase E activated)`() {
        // Phase E ports skcmsPrimariesToXYZD50; the matrix produced for
        // kRec709 must be near-identical to SkNamedGamut.kSRGB.
        val m = SkNamedPrimaries.kRec709.toXYZD50()
        org.junit.jupiter.api.Assertions.assertNotNull(m)
        org.junit.jupiter.api.Assertions.assertTrue(
            xyzAlmostEqual(org.skia.skcms.SkNamedGamut.kSRGB, m!!),
            "kRec709 primaries should produce ~kSRGB-gamut matrix"
        )
    }
}
