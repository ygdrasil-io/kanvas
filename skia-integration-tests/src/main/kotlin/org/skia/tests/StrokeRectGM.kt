package org.skia.tests


import org.skia.math.FLT_EPSILON
import org.skia.core.SkCanvas
import org.skia.math.SK_ColorGRAY
import org.skia.math.SK_ColorRED
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkStroker
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/strokerect.cpp::StrokeRectGM` (1400 × 740).
 *
 * Stresses [SkCanvas.drawRect] in stroke + stroke-and-fill styles
 * across three joins ([SkPaint.Join.kMiter_Join],
 * [SkPaint.Join.kRound_Join], [SkPaint.Join.kBevel_Join]) and twelve
 * geometry variants — including normal, inverted (negative width /
 * height), small-than-stroke, zero-extent and `FLT_EPSILON`-thin
 * rectangles. The grid is 12 columns × 6 rows (2 styles × 3 joins).
 *
 * Per cell, three overlays:
 *  - The frame `drawRect(r)` with the row's join + style (gray, 20 px).
 *  - The stroker's output path (`SkStroker.stroke(SkPath.Rect(r))`) in
 *     red 0-width — the geometric outline that the wide gray stroke
 *     would fill (skpathutils::FillPathWithPaint substitute).
 *  - The path's control points (`drawPoints(kPoints_Mode)`) in red
 *     with a 3-px stroke, for visual landmarks.
 *
 * Upstream uses `skpathutils::FillPathWithPaint(SkPath::Rect(r), paint)`
 * to produce the fill-path; we go through [SkStroker.fromPaint] /
 * [SkStroker.stroke] directly (same path-stroker that
 * [SkCanvas.drawPath] dispatches through internally).
 */
public class StrokeRectGM : GM() {

    override fun getName(): String = "strokerect"
    override fun getISize(): SkISize = SkISize.Make(1400, 740)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorWHITE)
        c.translate(STROKE_WIDTH * 3f / 2f, STROKE_WIDTH * 3f / 2f)

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = STROKE_WIDTH
        }

        val joins = arrayOf(
            SkPaint.Join.kMiter_Join,
            SkPaint.Join.kRound_Join,
            SkPaint.Join.kBevel_Join,
        )

        val w = 80f
        val h = 80f
        val rects = arrayOf(
            SkRect.MakeLTRB(0f, 0f, w, h),
            SkRect.MakeLTRB(w, 0f, 0f, h),
            SkRect.MakeLTRB(0f, h, w, 0f),
            SkRect.MakeLTRB(0f, 0f, STROKE_WIDTH, h),
            SkRect.MakeLTRB(0f, 0f, w, STROKE_WIDTH),
            SkRect.MakeLTRB(0f, 0f, STROKE_WIDTH / 2f, STROKE_WIDTH / 2f),
            SkRect.MakeLTRB(0f, 0f, w, 0f),
            SkRect.MakeLTRB(0f, 0f, 0f, h),
            SkRect.MakeLTRB(0f, 0f, 0f, 0f),
            SkRect.MakeLTRB(0f, 0f, w, FLT_EPSILON),
            SkRect.MakeLTRB(0f, 0f, FLT_EPSILON, h),
            SkRect.MakeLTRB(0f, 0f, FLT_EPSILON, FLT_EPSILON),
        )

        for (doFill in 0..1) {
            for (join in joins) {
                paint.strokeJoin = join

                val saveCount = c.save()
                for (r in rects) {
                    val fillPath = SkStroker.fromPaint(paint).stroke(SkPath.Rect(r))
                    drawPath(c, fillPath, r, join, doFill)
                    c.translate(w + 2f * STROKE_WIDTH, 0f)
                }
                c.restoreToCount(saveCount)
                c.translate(0f, h + 2f * STROKE_WIDTH)
            }
            paint.style = SkPaint.Style.kStrokeAndFill_Style
        }
    }

    private fun drawPath(
        canvas: SkCanvas,
        path: SkPath,
        rect: SkRect,
        join: SkPaint.Join,
        doFill: Int,
    ) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = if (doFill != 0) SkPaint.Style.kStrokeAndFill_Style else SkPaint.Style.kStroke_Style
            color = SK_ColorGRAY
            strokeWidth = STROKE_WIDTH
            strokeJoin = join
        }
        canvas.drawRect(rect, paint)

        paint.style = SkPaint.Style.kStroke_Style
        paint.strokeWidth = 0f
        paint.color = SK_ColorRED
        canvas.drawPath(path, paint)

        paint.strokeWidth = 3f
        paint.strokeJoin = SkPaint.Join.kMiter_Join
        canvas.drawPoints(SkCanvas.PointMode.kPoints, path.points(), paint)
    }

    public companion object {
        private const val STROKE_WIDTH: Float = 20f

        // Skia uses <float.h>'s FLT_EPSILON — the IEEE 754 single-precision
        // unit roundoff (1.19209290e-7). Kotlin exposes it via
        // [Float.MIN_VALUE], but that constant is denormal min (1.4e-45);
        // we hard-code the C value to mirror the upstream geometry exactly.
        private const val FLT_EPSILON: Float = 1.1920929e-7f
    }
}
