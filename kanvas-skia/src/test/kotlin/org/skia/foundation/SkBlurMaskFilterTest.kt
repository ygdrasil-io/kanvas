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

    // ─── Phase 7c — non-kNormal style combiners ─────────────────────────

    /**
     * Build a centred opaque "disc" mask : a 5×5 square of 0xFF in the
     * middle of a 21×21 buffer. The interior is fully opaque ; the
     * outside is fully transparent. This is the canonical input for
     * exercising the four blur styles.
     */
    private fun discMask(w: Int = 21, h: Int = 21, side: Int = 5): ByteArray {
        val src = ByteArray(w * h)
        val x0 = (w - side) / 2
        val y0 = (h - side) / 2
        for (y in y0 until y0 + side) {
            for (x in x0 until x0 + side) {
                src[y * w + x] = 0xFF.toByte()
            }
        }
        return src
    }

    @Test
    fun `kSolid keeps the original interior opaque + outer halo`() {
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kSolid, 2f)!!
        val w = 21; val h = 21
        val src = discMask(w, h, side = 5)
        val out = mf.filterMask(src, w, h)

        // Centre pixel (was 0xFF in src) must remain 0xFF — the solid
        // style preserves the original where it was opaque.
        val centre = out[(h / 2) * w + (w / 2)].toInt() and 0xFF
        assertEquals(255, centre)

        // A pixel just outside the original interior (orig = 0) but
        // inside the blur halo must be > 0 (outer blur is preserved).
        val halo = out[(h / 2) * w + (w / 2) + 4].toInt() and 0xFF
        assertTrue(halo in 1..254) { "halo $halo should be a partial blur value" }

        // Far-from-anything pixel : 0.
        val far = out[0].toInt() and 0xFF
        assertEquals(0, far)
    }

    @Test
    fun `kOuter zeros the original interior, keeps the halo`() {
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kOuter, 2f)!!
        val w = 21; val h = 21
        val src = discMask(w, h, side = 5)
        val out = mf.filterMask(src, w, h)

        // Centre pixel (was 0xFF in src) is cleared to 0 (or near 0)
        // because the (255 − orig) factor is 0.
        val centre = out[(h / 2) * w + (w / 2)].toInt() and 0xFF
        assertEquals(0, centre)

        // The halo right outside the disc is preserved (orig = 0,
        // blur > 0 → out = blur).
        val halo = out[(h / 2) * w + (w / 2) + 4].toInt() and 0xFF
        assertTrue(halo in 1..254) { "halo $halo should be a partial blur value" }
    }

    @Test
    fun `kInner clips the blur to the original interior`() {
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kInner, 2f)!!
        val w = 21; val h = 21
        val src = discMask(w, h, side = 5)
        val out = mf.filterMask(src, w, h)

        // Centre pixel (orig = 0xFF, blur ≈ 0xFF after a 5×5 disc + σ=2)
        // → out ≈ blur · orig / 255 ≈ blur. Must be > 0.
        val centre = out[(h / 2) * w + (w / 2)].toInt() and 0xFF
        assertTrue(centre > 0) { "kInner centre $centre should be > 0" }

        // A pixel just outside the disc (orig = 0) — out must be 0
        // regardless of the blur value there.
        val outside = out[(h / 2) * w + (w / 2) + 4].toInt() and 0xFF
        assertEquals(0, outside)
    }

    @Test
    fun `kSolid is at least as opaque as the original mask`() {
        // Property test : for every pixel, kSolid output ≥ original
        // (the formula `out = orig + blur·(255 − orig)/255` is
        // monotonically increasing in `blur` and ≥ `orig` whenever
        // `blur ≥ 0`).
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kSolid, 1f)!!
        val w = 16; val h = 16
        val src = discMask(w, h, side = 4)
        val out = mf.filterMask(src, w, h)
        for (i in src.indices) {
            val o = src[i].toInt() and 0xFF
            val r = out[i].toInt() and 0xFF
            assertTrue(r >= o) { "pixel $i : out $r < orig $o (kSolid violated)" }
        }
    }

    @Test
    fun `kInner is at most as opaque as the original mask`() {
        // Property test : for every pixel, kInner output ≤ original
        // (the formula `out = blur·orig/255` is bounded above by
        // `orig` when `blur ≤ 255`).
        val mf = SkBlurMaskFilter.Make(SkBlurStyle.kInner, 1f)!!
        val w = 16; val h = 16
        val src = discMask(w, h, side = 4)
        val out = mf.filterMask(src, w, h)
        for (i in src.indices) {
            val o = src[i].toInt() and 0xFF
            val r = out[i].toInt() and 0xFF
            assertTrue(r <= o) { "pixel $i : out $r > orig $o (kInner violated)" }
        }
    }
}
