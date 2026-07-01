package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Simplified port of Skia's `gm/strokerect.cpp::StrokeRectGM` (1400 x 740).
 *
 * Stresses [GmCanvas.drawRect] in stroke + stroke-and-fill styles across
 * three joins and twelve geometry variants including inverted, zero-extent,
 * and epsilon-thin rectangles. The grid is 12 columns x 6 rows
 * (2 styles x 3 joins). Per cell, the rect is drawn in gray; the stroker
 * overlay and control-point dots from the upstream are omitted because
 * Kanvas does not expose [SkStroker].
 */
/**
 * Port of Skia's `gm/strokerect.cpp`.
 * Stroke and stroke-and-fill rects across joins and geometry variants.
 * @see https://github.com/google/skia/blob/main/gm/strokerect.cpp
 */
class StrokeRectGm : SkiaGm {
    override val name = "strokerect"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 71.67
    override val width = 1400
    override val height = 740

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.translate(STROKE_WIDTH * 3f / 2f, STROKE_WIDTH * 3f / 2f)

        val joins = arrayOf(
            StrokeJoin.MITER,
            StrokeJoin.ROUND,
            StrokeJoin.BEVEL,
        )

        val w = 80f
        val h = 80f
        val rects = arrayOf(
            Rect.fromLTRB(0f, 0f, w, h),
            Rect.fromLTRB(w, 0f, 0f, h),
            Rect.fromLTRB(0f, h, w, 0f),
            Rect.fromLTRB(0f, 0f, STROKE_WIDTH, h),
            Rect.fromLTRB(0f, 0f, w, STROKE_WIDTH),
            Rect.fromLTRB(0f, 0f, STROKE_WIDTH / 2f, STROKE_WIDTH / 2f),
            Rect.fromLTRB(0f, 0f, w, 0f),
            Rect.fromLTRB(0f, 0f, 0f, h),
            Rect.fromLTRB(0f, 0f, 0f, 0f),
            Rect.fromLTRB(0f, 0f, w, FLT_EPSILON),
            Rect.fromLTRB(0f, 0f, FLT_EPSILON, h),
            Rect.fromLTRB(0f, 0f, FLT_EPSILON, FLT_EPSILON),
        )

        for (doFill in 0..1) {
            for (join in joins) {
                canvas.save()
                for (r in rects) {
                    val paint = Paint(
                        antiAlias = true,
                        style = if (doFill != 0) PaintStyle.STROKE_AND_FILL else PaintStyle.STROKE,
                        color = Color.fromRGBA(0.5f, 0.5f, 0.5f, 1f),
                        strokeWidth = STROKE_WIDTH,
                        strokeJoin = join,
                    )
                    canvas.drawRect(r, paint)
                    canvas.translate(w + 2f * STROKE_WIDTH, 0f)
                }
                canvas.restore()
                canvas.translate(0f, h + 2f * STROKE_WIDTH)
            }
        }
    }

    private companion object {
        private const val STROKE_WIDTH: Float = 20f
        private const val FLT_EPSILON: Float = 1.1920929e-7f
    }
}
