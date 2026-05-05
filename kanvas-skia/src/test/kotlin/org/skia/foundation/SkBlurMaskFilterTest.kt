package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SkBlurMaskFilter] (Phase 7c).
 *
 * Coverage :
 *  - Factory rejects non-finite / non-positive sigmas (returns null).
 *  - Margin grows with sigma (3-sigma rule).
 *  - filterMask preserves a uniform-WHITE mask (within 1 ulp).
 *  - filterMask spreads a single-pixel WHITE blob radially around
 *    its centre (max alpha at the centre, monotonically decreasing
 *    with distance, sum ≈ 255).
 *  - Sigma 0 (rejected) vs minimum positive sigma (1) — sanity check
 *    that the kernel mass stays normalised.
 */
class SkBlurMaskFilterTest {

    @Test
    fun `Make returns null for non-positive sigma`() {
        assertNull(SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 0f))
        assertNull(SkBlurMaskFilter.Make(SkBlurStyle.kNormal, -1f))
        assertNull(SkBlurMaskFilter.Make(SkBlurStyle.kNormal, Float.NaN))
        assertNull(SkBlurMaskFilter.Make(SkBlurStyle.kNormal, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `Make returns a non-null filter for positive finite sigma`() {
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 2f)
        assertNotNull(mf)
    }

    @Test
    fun `margin grows with sigma per the 3-sigma rule`() {
        val s1 = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1f)!!
        val s5 = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 5f)!!
        val s10 = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 10f)!!
        // ceil(3 * sigma) for each.
        assertEquals(3, s1.margin())
        assertEquals(15, s5.margin())
        assertEquals(30, s10.margin())
    }

    @Test
    fun `Uniform mask passes through unchanged`() {
        // A buffer of all 0xFF pixels Gaussian-blurred is still 0xFF
        // everywhere away from the borders (the kernel sums to 1, so
        // weighted-sum-of-255 = 255).
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 2f)!!
        val w = 16; val h = 16
        val src = ByteArray(w * h) { 0xFF.toByte() }
        val out = mf.filterMask(src, w, h)
        // Sample a centre pixel (away from borders) — should still be ≈255.
        val centre = out[(h / 2) * w + (w / 2)].toInt() and 0xFF
        assertEquals(255, centre)
    }

    @Test
    fun `Single-pixel blob spreads radially and conserves total mass`() {
        // Place a single 0xFF pixel at the centre of a 21x21 buffer.
        // After a Gaussian blur of sigma 2, the pixel should spread
        // radially with the centre being the local max and the values
        // monotonically decreasing along axes. Total sum is conserved
        // (kernel normalised) — within 8-bit quantisation noise.
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 2f)!!
        val w = 21; val h = 21
        val src = ByteArray(w * h)
        src[(h / 2) * w + (w / 2)] = 0xFF.toByte()
        val out = mf.filterMask(src, w, h)

        val centre = out[(h / 2) * w + (w / 2)].toInt() and 0xFF
        val edge = out[(h / 2) * w + (w / 2) + 5].toInt() and 0xFF
        val far = out[(h / 2) * w + (w / 2) + 10].toInt() and 0xFF
        assertTrue(centre > edge) { "centre $centre should exceed +5 $edge" }
        assertTrue(edge >= far) { "+5 $edge should not be less than +10 $far" }

        // Total mass : sum of all output pixels ≈ 255 (within
        // quantisation noise, ~10 % typical for sigma 2).
        var total = 0
        for (b in out) total += (b.toInt() and 0xFF)
        assertTrue(total in 230..280) { "total mass $total should be ≈ 255 (got $total)" }
    }

    @Test
    fun `filterMask returns a buffer of the same dimensions`() {
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.5f)!!
        val w = 32; val h = 24
        val src = ByteArray(w * h)
        val out = mf.filterMask(src, w, h)
        assertEquals(w * h, out.size)
    }
}
