package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorGREEN
import org.skia.math.SkColor
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/rect_poly_stroke.cpp::rect_poly_stroke`
 * (DEF_SIMPLE_GM, 1150 × 920).
 *
 * Compares stroked rect rasterization via two paths :
 *  1. `drawRect(rect, paint)` — the dedicated rect rasterizer.
 *  2. `drawPath(SkPath.Rect(rect), paint)` — the general path rasterizer.
 *
 * Both should produce identical outlines under the same stroke
 * settings. Drawn 3 × 4 × 2 × 2 = 48 cells (3 joins × 4 rect shapes ×
 * 2 rotations × 2 procs) plus a 0-width green hairline overlay per
 * cell to expose any geometry mismatch between the two procs.
 *
 * Rect shapes include `(0×H)` / `(W×0)` / `(0×0)` degenerate cases —
 * the dedicated rect rasterizer drops them entirely while the path
 * rasterizer may emit a stroked line / point.
 */
public class RectPolyStrokeGM : GM() {

    override fun getName(): String = "rect_poly_stroke"
    override fun getISize(): SkISize = SkISize.Make(1150, 920)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val rects = arrayOf(
            SkRect.MakeLTRB(0f, 0f, W, H),
            SkRect.MakeLTRB(0f, 0f, W, 0f),
            SkRect.MakeLTRB(0f, 0f, 0f, H),
            SkRect.MakeLTRB(0f, 0f, 0f, 0f),  // we don't expect this to draw
        )
        val degrees = floatArrayOf(0f, -30f)
        val joins = arrayOf(
            SkPaint.Join.kMiter_Join,
            SkPaint.Join.kRound_Join,
            SkPaint.Join.kBevel_Join,
        )
        val procs: Array<Pair<DrawRectProc, SkColor>> = arrayOf(
            ::drawRectAsRect to SK_ColorBLACK,
            ::drawRectWithPath to 0xFF000088.toInt(),
        )

        c.translate(30f, 50f)
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = THICKNESS
        }
        for (j in joins) {
            paint.strokeJoin = j

            c.save()
            for (r in rects) {
                for (angle in degrees) {
                    c.save()
                    for ((proc, color) in procs) {
                        c.save()
                        c.rotate(angle, r.centerX(), r.centerY())
                        paint.strokeWidth = THICKNESS
                        paint.color = color
                        proc(c, r, paint)

                        paint.strokeWidth = 0f
                        paint.color = SK_ColorGREEN
                        proc(c, r, paint)
                        c.restore()
                        c.translate(0f, SPACING)
                    }
                    c.restore()
                    c.translate(SPACING, 0f)
                }
            }
            c.restore()
            c.translate(0f, 2f * SPACING)
        }
    }

    private fun drawRectAsRect(canvas: SkCanvas, rect: SkRect, paint: SkPaint) {
        canvas.drawRect(rect, paint)
    }

    private fun drawRectWithPath(canvas: SkCanvas, rect: SkRect, paint: SkPaint) {
        canvas.drawPath(SkPath.Rect(rect), paint)
    }

    private companion object {
        const val W: Float = 100f
        const val H: Float = 80f
        const val SPACING: Float = 150f
        const val THICKNESS: Float = 20f
    }
}

private typealias DrawRectProc = (SkCanvas, SkRect, SkPaint) -> Unit
