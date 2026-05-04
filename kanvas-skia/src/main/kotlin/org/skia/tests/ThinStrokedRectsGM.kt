package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/thinstrokedrects.cpp` (`ThinStrokedRectsGM`,
 * GM name `thinstrokedrects`).
 *
 * Black background. Two grids of stroked AA rects in white then red,
 * with the red grid inside a `scale(0.5, 0.5)` so the visible stroke
 * widths match the white grid for the matching column.
 *
 * Per pane, eight rows (offset by `i*0.125` x and `i*30` y) of seven
 * stroked rects with widths `4, 2, 1, 0.5, 0.25, 0.125, 0`. Width 0
 * is upstream's "hairline" — Skia falls back to a 1 px AA line; our
 * stroker mirrors that ("hairline = 1 px") within a fraction of a pixel.
 *
 * Reference image: `thinstrokedrects.png`, 240 × 320, bg `0xFF000000`.
 *
 * Stresses :
 *  - sub-pixel stroke widths under AA (`0.5`, `0.25`, `0.125`);
 *  - `scale(0.5, 0.5)` interaction with the stroker (each red row
 *    starts at world `(i*0.125 + 0, …)` then scales by 0.5 — a different
 *    test of the resScale path than ScaledStrokesGM's larger factors);
 *  - `i*0.125f` per-row sub-pixel x-translation across both panes.
 */
public class ThinStrokedRectsGM : GM() {

    init { setBGColor(0xFF000000.toInt()) }

    override fun getName(): String = "thinstrokedrects"
    override fun getISize(): SkISize = SkISize.Make(240, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            style = SkPaint.Style.kStroke_Style
            isAntiAlias = true
        }

        val rect = SkRect.MakeLTRB(0f, 0f, 10f, 10f)
        val rect2 = SkRect.MakeLTRB(0f, 0f, 20f, 20f)

        c.translate(5f, 5f)
        for (i in 0 until 8) {
            c.save()
            c.translate(i * 0.125f, i * 30f)
            for (j in STROKE_WIDTHS.indices) {
                paint.strokeWidth = STROKE_WIDTHS[j]
                c.drawRect(rect, paint)
                c.translate(15f, 0f)
            }
            c.restore()
        }

        // Second pane in red, with a scale(0.5, 0.5) per row.
        paint.color = SK_ColorRED
        c.translate(0f, 15f)
        for (i in 0 until 8) {
            c.save()
            c.translate(i * 0.125f, i * 30f)
            c.scale(0.5f, 0.5f)
            for (j in STROKE_WIDTHS.indices) {
                paint.strokeWidth = 2f * STROKE_WIDTHS[j]
                c.drawRect(rect2, paint)
                c.translate(30f, 0f)
            }
            c.restore()
        }
    }

    private companion object {
        // From upstream's `gStrokeWidths` array.
        val STROKE_WIDTHS: FloatArray = floatArrayOf(4f, 2f, 1f, 0.5f, 0.25f, 0.125f, 0f)
    }
}
