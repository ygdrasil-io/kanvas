package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.graphiks.math.SkRect

/**
 * Phase R1-C — coverage for the new
 * [SkImageFilter.computeFastBounds] surface and the per-subclass
 * overrides on Offset / Blur / DropShadow / Compose / ImageSource.
 * Mirrors Skia's `gm/filterfastbounds.cpp` and
 * `tests/ImageFilterTest.cpp` invariants:
 *
 *  - Base class default : returns `src` untouched.
 *  - Offset(dx, dy) : translates the rect.
 *  - Blur(sigmaX, sigmaY) : inflates by `± ceil(3·sigma)` per axis.
 *  - DropShadow : union of `src` and `src offset by (dx, dy) + blur margin`.
 *  - Compose(outer, inner) : outer(inner(src)).
 */
class SkImageFilterComputeFastBoundsTest {

    private val rect = SkRect.MakeLTRB(0f, 0f, 100f, 100f)

    @Test
    fun `offset filter translates the bounds`() {
        val f = SkImageFilters.Offset(5f, -7f, null)
        val out = f.computeFastBounds(rect)
        assertEquals(5f, out.left)
        assertEquals(-7f, out.top)
        assertEquals(105f, out.right)
        assertEquals(93f, out.bottom)
    }

    @Test
    fun `crop filter reports the crop output bounds`() {
        val crop = SkRect.MakeLTRB(40f, 10f, 80f, 50f)
        val f = SkImageFilters.Crop(crop, SkTileMode.kDecal, SkImageFilters.Offset(20f, 0f, null))
        val out = f.computeFastBounds(rect)
        assertEquals(40f, out.left)
        assertEquals(10f, out.top)
        assertEquals(80f, out.right)
        assertEquals(50f, out.bottom)
    }

    @Test
    fun `blur filter inflates the bounds by 3-sigma per axis`() {
        val f = SkImageFilters.Blur(4f, 8f, SkTileMode.kClamp, null)!!
        val out = f.computeFastBounds(rect)
        // radiusX = ceil(3 * 4) = 12 ; radiusY = ceil(3 * 8) = 24.
        assertEquals(-12f, out.left)
        assertEquals(-24f, out.top)
        assertEquals(112f, out.right)
        assertEquals(124f, out.bottom)
    }

    @Test
    fun `drop shadow filter unions the source with the shadow bbox`() {
        val f = SkImageFilters.DropShadow(20f, 30f, 2f, 2f, 0xFF000000.toInt(), null)
        val out = f.computeFastBounds(rect)
        // shadow radius = ceil(3 * 2) = 6 per side.
        // shadow bbox = (20-6, 30-6) - (120+6, 130+6) = (14, 24) - (126, 136).
        // union with src (0, 0)-(100, 100) = (0, 0)-(126, 136).
        assertEquals(0f, out.left)
        assertEquals(0f, out.top)
        assertEquals(126f, out.right)
        assertEquals(136f, out.bottom)
    }

    @Test
    fun `compose filter applies outer to inner's bounds`() {
        val inner = SkImageFilters.Offset(10f, 10f, null)
        val outer = SkImageFilters.Blur(2f, 2f, SkTileMode.kClamp, null)!!
        val f = SkImageFilters.Compose(outer, inner)!!
        val out = f.computeFastBounds(rect)
        // inner(rect) = (10, 10) - (110, 110)
        // outer (blur 2σ => radius 6) inflates => (4, 4) - (116, 116).
        assertEquals(4f, out.left)
        assertEquals(4f, out.top)
        assertEquals(116f, out.right)
        assertEquals(116f, out.bottom)
    }

    @Test
    fun `base class default returns src untouched`() {
        // A color-filter-image-filter has no bounds-altering effect ;
        // base-class default fastBounds applies.
        val f = SkImageFilters.ColorFilter(SkColorFilters.Blend(0xFFFF0000.toInt(), SkBlendMode.kSrcIn), null)
        val out = f.computeFastBounds(rect)
        assertEquals(rect.left, out.left)
        assertEquals(rect.top, out.top)
        assertEquals(rect.right, out.right)
        assertEquals(rect.bottom, out.bottom)
    }

    @Test
    fun `offset chain stacks translations through nested input filters`() {
        val inner = SkImageFilters.Offset(3f, 4f, null)
        val outer = SkImageFilters.Offset(10f, 20f, inner)
        val out = outer.computeFastBounds(rect)
        // outer takes inner.bounds and translates by (10, 20).
        // inner.bounds = (3, 4) - (103, 104).
        // outer => (13, 24) - (113, 124).
        assertEquals(13f, out.left)
        assertEquals(24f, out.top)
        assertEquals(113f, out.right)
        assertEquals(124f, out.bottom)
    }
}
