package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Phase R1-C — coverage for the new
 * [SkMaskFilter.MakeBlur] (3-arg) overload and the corresponding
 * `respectCTM` plumbing on [SkBlurMaskFilter].
 *
 *  - The 2-arg overload (legacy) sets `respectCTM = true` (Skia default).
 *  - The 3-arg overload accepts `respectCTM = false` and threads through.
 *  - `respectCTM = false` filters round-trip [SkMaskFilter.withCtmScale]
 *    by adjusting their effective sigma ; `respectCTM = true` filters
 *    are returned unchanged.
 */
class SkMaskFilterMakeBlurRespectCTMTest {

    @Test
    fun `2-arg MakeBlur defaults to respectCTM=true`() {
        val mf = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 4f)
        assertNotNull(mf)
        assertTrue(mf!!.respectCTM, "legacy 2-arg overload mirrors Skia's respectCTM=true default")
    }

    @Test
    fun `3-arg MakeBlur with respectCTM=false produces a non-CTM-respecting filter`() {
        val mf = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 4f, respectCTM = false)
        assertNotNull(mf)
        assertEquals(false, mf!!.respectCTM)
    }

    @Test
    fun `MakeBlur with non-positive sigma returns null`() {
        assertNull(SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 0f))
        assertNull(SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, -1f, respectCTM = false))
    }

    @Test
    fun `withCtmScale on respectCTM=true returns the same instance`() {
        val mf = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 4f)!!
        // respectCTM=true => scale is ignored, filter returned untouched.
        val scaled = mf.withCtmScale(3f)
        assertEquals(mf, scaled)
    }

    @Test
    fun `withCtmScale on respectCTM=false produces a smaller-margin filter`() {
        val mf = SkMaskFilter.MakeBlur(SkBlurStyle.kNormal, 9f, respectCTM = false)!!
        val baselineMargin = mf.margin()
        val scaled = mf.withCtmScale(3f)
        // sigma/3 => margin should be ~1/3 of the baseline.
        assertTrue(scaled.margin() < baselineMargin,
            "withCtmScale(3) must shrink the blur margin (baseline=$baselineMargin scaled=${scaled.margin()})")
        // Sanity : an identity scale of 1 returns the same instance.
        assertEquals(mf, mf.withCtmScale(1f))
    }
}
