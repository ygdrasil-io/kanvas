package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * C1.3 verification suite — arithmetic family.
 *
 * Covers :
 *  - **Arithmetic** : the canonical recipes (`(0, 1, 0, 0)` =
 *    pass-through-fg ; `(0, 0, 1, 0)` = pass-through-bg ;
 *    `(1, 0, 0, 0)` = src·dst multiply) ; `enforcePMColor` clamping.
 *  - **Blend** : every Porter-Duff mode produces the expected
 *    output for opaque + half-alpha inputs.
 *  - **Merge** : N inputs SrcOver-stacked in order ; empty list
 *    collapses to transparent black ; null entries treated as src.
 *  - **DropShadowOnly** : same shape as `DropShadow` but skips the
 *    original-input composite (output bbox = shadow bbox only).
 */
class SkImageFiltersArithmeticFamilyTest {

    private val identity = SkMatrix.Identity

    /** A 4×4 image, all pixels opaque red. */
    private val redImg: SkImage = SkImage(4, 4, IntArray(16) { 0xFFFF0000.toInt() })

    /** A 4×4 image, all pixels opaque blue. */
    private val blueImg: SkImage = SkImage(4, 4, IntArray(16) { 0xFF0000FF.toInt() })

    /** A 4×4 half-alpha green image (premul-equivalent : (0, 128, 0, 128)). */
    private val halfGreenImg: SkImage = SkImage(4, 4, IntArray(16) { 0x8000FF00.toInt() })

    private val anyDriver: SkImage = SkImage(2, 2, IntArray(4))

    // ─── Arithmetic ───────────────────────────────────────────────────

    @Test
    fun `Arithmetic with (0, 1, 0, 0) recipe is fg pass-through`() {
        val filter = SkImageFilters.Arithmetic(
            k1 = 0f, k2 = 1f, k3 = 0f, k4 = 0f,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFF0000FF.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Arithmetic with (0, 0, 1, 0) recipe is bg pass-through`() {
        val filter = SkImageFilters.Arithmetic(
            k1 = 0f, k2 = 0f, k3 = 1f, k4 = 0f,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Arithmetic enforcePMColor clamps RGB to alpha`() {
        // (0, 0, 0, 0.5) should produce alpha=0.5, RGB=0.5 — but with
        // half-alpha out the channels need to be ≤ 0.5 in premul-
        // valid form. The byte-output decodes as (alpha=128, RGB=255)
        // in non-premul (since enforcePMColor clamps premul channels
        // ≤ alpha, then we un-premul for the 8-bit output → RGB stays
        // as-is *modulo* the un-premul divide).
        val filter = SkImageFilters.Arithmetic(
            k1 = 0f, k2 = 0f, k3 = 0f, k4 = 0.5f,
            enforcePMColor = true,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(0, 0)
        val alpha = (px ushr 24) and 0xFF
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        val b = px and 0xFF
        // alpha = 0.5 → 128 byte (round-half-to-nearest-even might
        // produce 127 or 128 depending on impl ; allow either).
        assertTrue(alpha in 127..128)
        // After enforcePMColor + unpremul, RGB lands at full saturation
        // since the premul value matched alpha exactly.
        assertEquals(255, r)
        assertEquals(255, g)
        assertEquals(255, b)
    }

    // ─── Blend ────────────────────────────────────────────────────────

    @Test
    fun `Blend with kSrc returns fg`() {
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kSrc,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFF0000FF.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Blend with kDst returns bg`() {
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kDst,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Blend with kClear produces transparent`() {
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kClear,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0, result.image.peekPixel(0, 0))
    }

    @Test
    fun `Blend with kSrcOver of opaque fg over bg returns fg`() {
        // Opaque fg over opaque bg : out = fg (alpha = 1, dst contribution = 0).
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kSrcOver,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFF0000FF.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Blend with kSrcOver of half-alpha fg over opaque bg blends`() {
        // half-alpha green over opaque red : out = green*0.5 + red*0.5
        // → R=128, G=128, B=0 ish.
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kSrcOver,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(halfGreenImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(0, 0)
        val a = (px ushr 24) and 0xFF
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        // Result must stay opaque ; R and G should both be ~128 ulp.
        assertEquals(0xFF, a)
        assertTrue(kotlin.math.abs(r - 128) <= 2, "R=$r should be ~128")
        assertTrue(kotlin.math.abs(g - 128) <= 2, "G=$g should be ~128")
    }

    @Test
    fun `Blend with kPlus saturates channels at 1`() {
        // red (255, 0, 0) + blue (0, 0, 255) = (255, 0, 255) — magenta.
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kPlus,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(0, 0)
        // Should be fully saturated magenta.
        assertEquals(0xFF, (px ushr 24) and 0xFF) // alpha
        assertEquals(0xFF, (px ushr 16) and 0xFF) // R
        assertEquals(0x00, (px ushr 8) and 0xFF) // G
        assertEquals(0xFF, px and 0xFF) // B
    }

    @Test
    fun `Blend with kModulate of red and blue is black`() {
        // red * blue = (1*0, 0*0, 0*1) = 0. Premul math.
        val filter = SkImageFilters.Blend(
            mode = SkBlendMode.kModulate,
            bg = SkImageFilters.Image(redImg),
            fg = SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(0, 0)
        // Alpha is 1*1 = 1 (opaque). RGB all zero (no overlap of the
        // two colours).
        assertEquals(0xFF, (px ushr 24) and 0xFF)
        assertEquals(0, (px ushr 16) and 0xFF)
        assertEquals(0, (px ushr 8) and 0xFF)
        assertEquals(0, px and 0xFF)
    }

    // ─── Merge ────────────────────────────────────────────────────────

    @Test
    fun `Merge with empty filter list returns transparent`() {
        val filter = SkImageFilters.Merge()
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0, result.image.peekPixel(0, 0))
    }

    @Test
    fun `Merge with single opaque filter returns that filter`() {
        val filter = SkImageFilters.Merge(SkImageFilters.Image(redImg))
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFFFF0000.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Merge stacks two opaque filters with second on top`() {
        // Bottom = red, top = blue. Output should be blue everywhere
        // (both fully opaque).
        val filter = SkImageFilters.Merge(
            SkImageFilters.Image(redImg),
            SkImageFilters.Image(blueImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        assertEquals(0xFF0000FF.toInt(), result.image.peekPixel(0, 0))
    }

    @Test
    fun `Merge stacks half-alpha on top of opaque via SrcOver`() {
        // Bottom = red (opaque), top = half-green. SrcOver gives
        // ~half-green-over-red blend.
        val filter = SkImageFilters.Merge(
            SkImageFilters.Image(redImg),
            SkImageFilters.Image(halfGreenImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        val px = result.image.peekPixel(0, 0)
        val r = (px ushr 16) and 0xFF
        val g = (px ushr 8) and 0xFF
        // Both R and G should be ~128 (50/50 blend).
        assertTrue(kotlin.math.abs(r - 128) <= 2, "R=$r should be ~128")
        assertTrue(kotlin.math.abs(g - 128) <= 2, "G=$g should be ~128")
    }

    // ─── DropShadowOnly ──────────────────────────────────────────────

    @Test
    fun `DropShadowOnly produces a shadow-only output (no original on top)`() {
        // Use a 4×4 opaque red input with NO blur, NO offset, and a
        // black shadow tint. With sigma=0 + dx/dy=0, the "shadow" is
        // just the input tinted black via SrcIn (= same alpha, all
        // channels black). DropShadow would composite the original
        // red on top → output = red. DropShadowOnly skips that step
        // → output = black (the tinted shadow).
        val filter = SkImageFilters.DropShadowOnly(
            dx = 0f, dy = 0f,
            sigmaX = 0f, sigmaY = 0f,
            color = 0xFF000000.toInt(),
            input = SkImageFilters.Image(redImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        // 4x4 opaque black expected.
        for (y in 0 until 4) for (x in 0 until 4) {
            assertEquals(
                0xFF000000.toInt(),
                result.image.peekPixel(x, y),
                "shadow-only black at ($x, $y)",
            )
        }
    }

    @Test
    fun `DropShadowOnly with offset translates the output`() {
        val filter = SkImageFilters.DropShadowOnly(
            dx = 10f, dy = 5f,
            sigmaX = 0f, sigmaY = 0f,
            color = 0xFF000000.toInt(),
            input = SkImageFilters.Image(redImg),
        )
        val result = filter.filterImage(anyDriver, identity)
        // Offset should apply to the shadow bbox.
        assertEquals(10, result.offsetX)
        assertEquals(5, result.offsetY)
    }
}
