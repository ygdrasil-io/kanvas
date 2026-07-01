package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect
import kotlin.math.min

/**
 * Port of Skia's `gm/rrects.cpp` — `stroke_rect_rrects`.
 * A grid of stroked and filled rectangles / rrects drawn at 0.5× scale.
 * Tests six stroke widths × three join types for both plain rects and
 * rrects, plus "football" and "D-shaped" rotated rrect sections.
 * @see https://github.com/google/skia/blob/main/gm/rrects.cpp
 */
class StrokeRectRRectsGm : SkiaGm {
    override val name = "stroke_rect_rrects"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1350
    override val height = 700

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(0.5f, 0.5f)
        canvas.translate(50f, 50f)

        val widths = floatArrayOf(-1f, 50f, 30f, 10f, 1f, 0f)
        val joins = arrayOf(StrokeJoin.MITER, StrokeJoin.BEVEL, StrokeJoin.ROUND)

        var i = 0
        for (width in widths) {
            var j = 0
            for (join in joins) {
                if (width < 0f && join != StrokeJoin.MITER) continue
                drawCell(canvas, 2 * i, 2 * j, false, width, join)
                drawCell(canvas, 2 * i + 1, 2 * j, false, width, join)
                drawCell(canvas, 2 * i, 2 * j + 1, false, width, join)
                drawCell(canvas, 2 * i + 1, 2 * j + 1, false, width, join)
                j++
            }
            i++
        }

        canvas.translate(0f, 50f)

        i = 0
        for (width in widths) {
            var j = 3
            for (join in joins) {
                if (width < 0f && join != StrokeJoin.MITER) continue
                drawCell(canvas, 2 * i, 2 * j, true, width, join)
                drawCell(canvas, 2 * i + 1, 2 * j, true, width, join)
                drawCell(canvas, 2 * i, 2 * j + 1, true, width, join)
                drawCell(canvas, 2 * i + 1, 2 * j + 1, true, width, join)
                j++
            }
            i++
        }

        canvas.translate(0f, -50f)
        i = 6
        for (width in floatArrayOf(50f, 30f, 20f, 10f, 1f, 0f)) {
            var j = 0
            for (stretch in floatArrayOf(0f, 5f, 10f)) {
                drawFootball(canvas, 2 * i, 2 * j, width, stretch)
                drawFootball(canvas, 2 * i + 1, 2 * j, width, stretch)
                drawFootball(canvas, 2 * i, 2 * j + 1, width, stretch)
                drawFootball(canvas, 2 * i + 1, 2 * j + 1, width, stretch)
                j++
            }
            i++
        }

        canvas.translate(0f, 50f)
        i = 6
        for (width in floatArrayOf(50f, 30f, 20f, 10f, 1f, 0f)) {
            var j = 3
            for (stretch in floatArrayOf(0f, 5f, 10f)) {
                drawDShape(canvas, 2 * i, 2 * j, width, stretch)
                drawDShape(canvas, 2 * i + 1, 2 * j, width, stretch)
                drawDShape(canvas, 2 * i, 2 * j + 1, width, stretch)
                drawDShape(canvas, 2 * i + 1, 2 * j + 1, width, stretch)
                j++
            }
            i++
        }
    }

    private fun drawCell(canvas: GmCanvas, cx: Int, cy: Int, rrect: Boolean, width: Float, join: StrokeJoin) {
        val paint = Paint(
            antiAlias = true,
            strokeWidth = width,
            style = if (width >= 0f) PaintStyle.STROKE else PaintStyle.FILL,
            strokeJoin = join,
        )
        canvas.save()
        canvas.translate((cx * 110).toFloat(), (cy * 110).toFloat())
        val dx = if (cx % 2 != 0) 0.5f else 0f
        val dy = if (cy % 2 != 0) 0.5f else 0f
        var rect = Rect.fromXYWH(0f, 0f, 50f, 40f)
        rect = Rect.fromLTRB(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy)
        val finalRect = if (width < 0f) {
            Rect.fromLTRB(rect.left - 25f, rect.top - 25f, rect.right + 25f, rect.bottom + 25f)
        } else rect

        if (rrect) {
            val cornerScale = min(finalRect.width, finalRect.height)
            val strokeRadii = arrayOf(
                CornerRadii(0.25f * cornerScale, 0.25f * cornerScale),
                CornerRadii(0f, 0f),
                CornerRadii(0.50f * cornerScale, 0.50f * cornerScale),
                CornerRadii(0.75f * cornerScale, 0.75f * cornerScale),
            )
            val outerRadii = arrayOf(
                CornerRadii(0.25f * cornerScale, 0.75f * cornerScale),
                CornerRadii(0f, 0f),
                CornerRadii(0.50f * cornerScale, 0.50f * cornerScale),
                CornerRadii(0.75f * cornerScale, 0.25f * cornerScale),
            )
            val radii = if (width >= 0f) strokeRadii else outerRadii
            val rr = RRect(finalRect, radii[0], radii[1], radii[2], radii[3])
            val path = Path { }.apply { addRRect(rr) }
            canvas.drawPath(path, paint)
        } else {
            canvas.drawRect(finalRect, paint)
        }
        canvas.restore()
    }

    private fun drawFootball(canvas: GmCanvas, cx: Int, cy: Int, width: Float, stretch: Float) {
        val paint = Paint(
            antiAlias = true,
            strokeWidth = width,
            style = PaintStyle.STROKE,
            strokeJoin = StrokeJoin.BEVEL,
        )
        canvas.save()
        canvas.translate((cx * 110).toFloat(), (cy * 110).toFloat())
        val rect = Rect.fromXYWH(0f, 0f,
            if (cx % 2 != 0) 50f else (40f + stretch),
            if (cx % 2 != 0) (40f + stretch) else 50f,
        )
        val bigCorner = CornerRadii(30f, 30f)
        val rectCorner = CornerRadii(0f, 0f)
        val strokeRadii = arrayOf(
            if (cy % 2 != 0) rectCorner else bigCorner,
            if (cy % 2 != 0) bigCorner else rectCorner,
            if (cy % 2 != 0) rectCorner else bigCorner,
            if (cy % 2 != 0) bigCorner else rectCorner,
        )
        val rr = RRect(rect, strokeRadii[0], strokeRadii[1], strokeRadii[2], strokeRadii[3])
        val path = Path { }.apply { addRRect(rr) }
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawDShape(canvas: GmCanvas, cx: Int, cy: Int, width: Float, stretch: Float) {
        val paint = Paint(
            antiAlias = true,
            strokeWidth = width,
            style = PaintStyle.STROKE,
            strokeJoin = StrokeJoin.MITER,
        )
        canvas.save()
        canvas.translate((cx * 110).toFloat(), (cy * 110).toFloat())
        val rect = Rect.fromXYWH(0f, 0f,
            if (cx % 2 != 0) 50f else (40f + stretch),
            if (cx % 2 != 0) (40f + stretch) else 50f,
        )
        val bigCorner = CornerRadii(30f, 30f)
        val rectCorner = CornerRadii(0f, 0f)
        val strokeRadii = arrayOf(
            if (cx % 2 != 0) rectCorner else bigCorner,
            if ((cx % 2) xor (cy % 2) != 0) bigCorner else rectCorner,
            if (cx % 2 != 0) bigCorner else rectCorner,
            if ((cx % 2) xor (cy % 2) != 0) rectCorner else bigCorner,
        )
        val rr = RRect(rect, strokeRadii[0], strokeRadii[1], strokeRadii[2], strokeRadii[3])
        val path = Path { }.apply { addRRect(rr) }
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
