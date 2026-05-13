package org.skia.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRegion
import org.skia.math.SkIRect

/**
 * Phase R2.13 — `SkCanvas.drawRegion(region, paint)` semantics.
 *
 * A region is a union of axis-aligned integer rects. `drawRegion`
 * iterates the rects via `SkRegion.Iterator` and fills each via
 * `drawRect(rect, paint)`, so pixels inside any rect get the paint
 * colour and pixels outside the region's bounding rect stay
 * unchanged (white background here).
 */
class SkCanvasDrawRegionTest {

    @Test
    fun `drawRegion fills every rect of a multi-rect region`() {
        val bm = SkBitmap(20, 20).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)

        // Two disjoint rects in different Y bands so the region
        // canonicalises into 2 separate bands.
        val region = SkRegion(SkIRect(2, 2, 6, 6))
        region.op(SkIRect(10, 12, 14, 16), SkRegion.Op.kUnion)

        canvas.drawRegion(region, SkPaint(SK_ColorRED))

        for (y in 0 until 20) {
            for (x in 0 until 20) {
                val inFirst = x in 2 until 6 && y in 2 until 6
                val inSecond = x in 10 until 14 && y in 12 until 16
                val expected = if (inFirst || inSecond) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }

    @Test
    fun `drawRegion on an empty region is a no-op`() {
        val bm = SkBitmap(8, 8).also { it.eraseColor(SK_ColorWHITE) }
        val before = bm.pixels.copyOf()
        SkCanvas(bm).drawRegion(SkRegion(), SkPaint(SK_ColorRED))
        assertEquals(before.toList(), bm.pixels.toList())
    }

    @Test
    fun `drawRegion with a single-rect region equals drawRect`() {
        val bmRegion = SkBitmap(10, 10).also { it.eraseColor(SK_ColorBLACK) }
        val bmRect = SkBitmap(10, 10).also { it.eraseColor(SK_ColorBLACK) }
        SkCanvas(bmRegion).drawRegion(
            SkRegion(SkIRect(1, 1, 8, 8)),
            SkPaint(SK_ColorRED),
        )
        SkCanvas(bmRect).drawRect(
            org.skia.math.SkRect.MakeLTRB(1f, 1f, 8f, 8f),
            SkPaint(SK_ColorRED),
        )
        assertEquals(bmRect.pixels.toList(), bmRegion.pixels.toList())
    }

    @Test
    fun `drawRegion honours the active CTM via per-rect drawRect`() {
        val bm = SkBitmap(20, 20).also { it.eraseColor(SK_ColorWHITE) }
        val canvas = SkCanvas(bm)
        canvas.save()
        canvas.translate(4f, 5f)
        canvas.drawRegion(SkRegion(SkIRect(0, 0, 3, 3)), SkPaint(SK_ColorRED))
        canvas.restore()

        for (y in 0 until 20) {
            for (x in 0 until 20) {
                val expected = if (x in 4 until 7 && y in 5 until 8) SK_ColorRED else SK_ColorWHITE
                assertEquals(expected, bm.getPixel(x, y), "($x,$y)")
            }
        }
    }
}
