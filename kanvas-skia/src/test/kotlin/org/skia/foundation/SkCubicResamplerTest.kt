package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Unit tests for the Mitchell-Netravali B-C cubic kernel implementation.
 *
 * Verifies that:
 *  - Mitchell `(1/3, 1/3)` produces the closed-form scalar weights at
 *    known fractional offsets.
 *  - Catmull-Rom `(0, 1/2)` produces the interpolating-kernel signature
 *    (weight `1` at zero offset, `0` at integer offsets).
 *  - The 4-tap kernel sums to 1 (partition of unity) for every fractional
 *    offset across the family.
 *  - SkSamplingOptions(SkCubicResampler) sets cubic, leaving filter
 *    / mipmap at the defaults.
 */
class SkCubicResamplerTest {

    @Test
    fun `Mitchell weight at t=0 is (8 - 12B) over 6 - i e  1 - 4B over 6`() {
        // Closed form for x = 0 in branch 1: ((12 - 9B - 6C) * 0 + (-18 + 12B + 6C) * 0 + (6 - 2B)) / 6
        //                                  = (6 - 2B) / 6 = 1 - B/3
        // Mitchell: B = 1/3 → 1 - 1/9 = 8/9
        val r = SkCubicResampler.Mitchell
        val w = SkCubicBC.weight(0f, r.B, r.C)
        assertEquals(8f / 9f, w, 1e-6f)
    }

    @Test
    fun `Mitchell weight at t=1 is (B + 6C) over 6 - 0 for B=C=1 over 3`() {
        // Both branches converge to the same value at t = 1.
        // Branch 1 at t=1: (12 - 9B - 6C) + (-18 + 12B + 6C) + (6 - 2B) all / 6
        //                = (B - 2B) / 6  no wait: (12 -9B -6C -18 + 12B + 6C + 6 - 2B) / 6 = (0 + B) / 6
        // Actually: 12 - 9B - 6C - 18 + 12B + 6C + 6 - 2B = (12 - 18 + 6) + (-9B + 12B - 2B) + (-6C + 6C)
        //                                                = 0 + B + 0 = B
        // So weight(1) = B / 6.
        // Mitchell B=1/3 → 1/18.
        val r = SkCubicResampler.Mitchell
        val w = SkCubicBC.weight(1f, r.B, r.C)
        assertEquals(1f / 18f, w, 1e-6f)
    }

    @Test
    fun `weight at t=2 is exactly zero across the family`() {
        for ((B, C) in listOf(
            SkCubicResampler.Mitchell.B to SkCubicResampler.Mitchell.C,
            SkCubicResampler.CatmullRom.B to SkCubicResampler.CatmullRom.C,
            0f to 0f, // B-spline
            0.25f to 0.75f, // off-family arbitrary
        )) {
            val w = SkCubicBC.weight(2f, B, C)
            assertEquals(0f, w, 1e-5f, "weight(2) for B=$B C=$C")
        }
    }

    @Test
    fun `CatmullRom is interpolating - weight 1 at zero and 0 at integer offsets`() {
        val r = SkCubicResampler.CatmullRom
        assertEquals(1f, SkCubicBC.weight(0f, r.B, r.C), 1e-6f)
        assertEquals(0f, SkCubicBC.weight(1f, r.B, r.C), 1e-6f)
        assertEquals(0f, SkCubicBC.weight(2f, r.B, r.C), 1e-6f)
    }

    @Test
    fun `4-tap kernel sums to 1 for all fractional offsets`() {
        for ((B, C) in listOf(
            SkCubicResampler.Mitchell.B to SkCubicResampler.Mitchell.C,
            SkCubicResampler.CatmullRom.B to SkCubicResampler.CatmullRom.C,
            0f to 0f,
            0.5f to 0.25f,
        )) {
            for (n in 0..100) {
                val fx = n / 100f
                val s = SkCubicBC.weight(1f + fx, B, C) +
                        SkCubicBC.weight(fx, B, C) +
                        SkCubicBC.weight(1f - fx, B, C) +
                        SkCubicBC.weight(2f - fx, B, C)
                assertTrue(abs(s - 1f) < 1e-4f, "sum at fx=$fx B=$B C=$C : $s")
            }
        }
    }

    @Test
    fun `SkSamplingOptions cubic ctor leaves filter and mipmap at defaults`() {
        val s = SkSamplingOptions(SkCubicResampler.Mitchell)
        assertEquals(SkCubicResampler.Mitchell, s.cubic)
        assertEquals(SkFilterMode.kNearest, s.filter)
        assertEquals(SkMipmapMode.kNone, s.mipmap)
    }

    @Test
    fun `Default SkSamplingOptions has no cubic`() {
        assertEquals(null, SkSamplingOptions.Default.cubic)
        assertEquals(null, SkSamplingOptions.nearest().cubic)
        assertEquals(null, SkSamplingOptions.linear().cubic)
    }

    @Test
    fun `Mitchell and CatmullRom presets carry expected B and C`() {
        assertEquals(1f / 3f, SkCubicResampler.Mitchell.B, 1e-6f)
        assertEquals(1f / 3f, SkCubicResampler.Mitchell.C, 1e-6f)
        assertEquals(0f, SkCubicResampler.CatmullRom.B, 1e-6f)
        assertEquals(0.5f, SkCubicResampler.CatmullRom.C, 1e-6f)
    }
}
