package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.math.SkRect

/**
 * Phase R1-C — coverage for [SkPaint.computeFastBounds] and
 * [SkPaint.canComputeFastBounds]. Mirrors Skia's
 * `SkPaint::computeFastBounds` test suite (`tests/PaintTest.cpp`).
 *
 * Properties exercised :
 *  - Fill-only paint, no filters : bounds returned untouched (the
 *    `orig` reference itself per Skia's contract).
 *  - Stroked paint : bounds inflated by half stroke width per side.
 *  - Miter-joined stroked paint : inflated by `halfWidth * miterLimit`.
 *  - Paint with [SkPaint.maskFilter] : bounds inflated by the
 *    filter's `margin()` per side.
 *  - Paint with [SkPaint.imageFilter] : bounds derived from the
 *    filter's own [SkImageFilter.computeFastBounds].
 *  - `canComputeFastBounds` returns false when a [SkPathEffect] is set.
 */
class SkPaintComputeFastBoundsTest {

    private val origRect = SkRect.MakeLTRB(10f, 20f, 50f, 60f)

    @Test
    fun `fill-only paint with no filters returns the original rect untouched`() {
        val paint = SkPaint()
        val storage = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        val bounds = paint.computeFastBounds(origRect, storage)
        // Skia's contract : returned ref is either orig or storage. With
        // no inflation requested, it's orig.
        assertSame(origRect, bounds, "fill-only paint must return orig unchanged")
    }

    @Test
    fun `stroked paint inflates bounds by half stroke width per side`() {
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 6f
            strokeJoin = SkPaint.Join.kBevel_Join     // no miter padding
        }
        val storage = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        val bounds = paint.computeFastBounds(origRect, storage)
        // ±3 per side.
        assertEquals(origRect.left - 3f, bounds.left)
        assertEquals(origRect.top - 3f, bounds.top)
        assertEquals(origRect.right + 3f, bounds.right)
        assertEquals(origRect.bottom + 3f, bounds.bottom)
    }

    @Test
    fun `miter-joined stroked paint pads by halfWidth times miterLimit`() {
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 4f
            strokeJoin = SkPaint.Join.kMiter_Join
            strokeMiter = 3f
        }
        val storage = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        val bounds = paint.computeFastBounds(origRect, storage)
        // halfWidth=2 ; miter pad = 2 * 3 - 2 = 4 ; total inflation = 6 per side.
        assertEquals(origRect.left - 6f, bounds.left)
        assertEquals(origRect.right + 6f, bounds.right)
    }

    @Test
    fun `mask filter inflates bounds by the filter margin`() {
        val paint = SkPaint().apply {
            maskFilter = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 5f)
        }
        val storage = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        val bounds = paint.computeFastBounds(origRect, storage)
        val margin = paint.maskFilter!!.margin().toFloat()
        assertTrue(margin > 0f, "blur filter must report a positive margin")
        assertEquals(origRect.left - margin, bounds.left)
        assertEquals(origRect.right + margin, bounds.right)
    }

    @Test
    fun `image filter bounds delegate to the filter's computeFastBounds`() {
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.Offset(10f, 20f, null)
        }
        val storage = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        val bounds = paint.computeFastBounds(origRect, storage)
        // Offset filter translates the rect by (10, 20).
        assertEquals(origRect.left + 10f, bounds.left)
        assertEquals(origRect.top + 20f, bounds.top)
        assertEquals(origRect.right + 10f, bounds.right)
        assertEquals(origRect.bottom + 20f, bounds.bottom)
    }

    @Test
    fun `canComputeFastBounds is true for an empty paint`() {
        assertTrue(SkPaint().canComputeFastBounds())
    }

    @Test
    fun `canComputeFastBounds is false when a path effect is set`() {
        val paint = SkPaint().apply {
            pathEffect = SkDashPathEffect.Make(floatArrayOf(2f, 2f), 0f)
        }
        assertNotNull(paint.pathEffect)
        assertFalse(paint.canComputeFastBounds(),
            "path effects can grow bounds arbitrarily ; fast bound is not safe")
    }
}
