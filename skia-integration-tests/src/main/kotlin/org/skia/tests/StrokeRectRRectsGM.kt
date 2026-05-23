package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector
import kotlin.math.min

/**
 * Port of Skia's `gm/rrects.cpp` — `DEF_SIMPLE_GM(stroke_rect_rrects, …)`.
 *
 * GM name: `stroke_rect_rrects`. A grid of stroked and filled rectangles /
 * rrects drawn at 0.5× scale to fit within 1350 × 700. The grid tests six
 * stroke widths (fill, 50, 30, 10, 1, hairline) × three join types
 * (miter, bevel, round) for both plain rects and rrects, plus two sections
 * of "football" and "D-shaped" rotated rrects with alternating corner
 * patterns. Designed so Graphite can batch all of these into a single draw
 * using its AnalyticRoundRectRenderStep.
 *
 * Reference image: `stroke_rect_rrects.png`, 1350 × 700, white BG.
 */
public class StrokeRectRRectsGM : GM() {

    override fun getName(): String = "stroke_rect_rrects"
    override fun getISize(): SkISize = SkISize.Make(1350, 700)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.scale(0.5f, 0.5f)
        c.translate(50f, 50f)

        // --- helper lambdas --------------------------------------------------

        fun draw(cx: Int, cy: Int, rrect: Boolean, width: Float, join: SkPaint.Join) {
            val p = SkPaint().apply {
                isAntiAlias = true
                strokeWidth = width
                style = if (width >= 0f) SkPaint.Style.kStroke_Style else SkPaint.Style.kFill_Style
                strokeJoin = join
            }

            c.save()
            c.translate(cx * 110f, cy * 110f)
            val dx = if (cx % 2 != 0) 0.5f else 0f
            val dy = if (cy % 2 != 0) 0.5f else 0f
            var rect = SkRect.MakeWH(50f, 40f)
            rect = SkRect.MakeLTRB(rect.left + dx, rect.top + dy,
                                   rect.right + dx, rect.bottom + dy)

            if (width < 0f) {
                rect = SkRect.MakeLTRB(rect.left - 25f, rect.top - 25f,
                                       rect.right + 25f, rect.bottom + 25f)
            }

            val cornerScale = min(rect.width(), rect.height())
            val outerRadii = arrayOf(
                SkVector(0.25f * cornerScale, 0.75f * cornerScale),
                SkVector(0f, 0f),
                SkVector(0.50f * cornerScale, 0.50f * cornerScale),
                SkVector(0.75f * cornerScale, 0.25f * cornerScale),
            )
            val strokeRadii = arrayOf(
                SkVector(0.25f * cornerScale, 0.25f * cornerScale),
                SkVector(0f, 0f),
                SkVector(0.50f * cornerScale, 0.50f * cornerScale),
                SkVector(0.75f * cornerScale, 0.75f * cornerScale),
            )

            if (rrect) {
                val r = SkRRect()
                if (width >= 0f) {
                    r.setRectRadii(rect, strokeRadii)
                } else {
                    r.setRectRadii(rect, outerRadii)
                }
                c.drawRRect(r, p)
            } else {
                c.drawRect(rect, p)
            }
            c.restore()
        }

        val widths = floatArrayOf(-1f, 50f, 30f, 10f, 1f, 0f)
        val joins = arrayOf(SkPaint.Join.kMiter_Join,
                            SkPaint.Join.kBevel_Join,
                            SkPaint.Join.kRound_Join)

        // Top half: plain rects
        var i = 0
        for (width in widths) {
            var j = 0
            for (join in joins) {
                if (width < 0f && join != SkPaint.Join.kMiter_Join) continue
                draw(2*i, 2*j,   false, width, join)
                draw(2*i+1, 2*j, false, width, join)
                draw(2*i, 2*j+1, false, width, join)
                draw(2*i+1, 2*j+1, false, width, join)
                j++
            }
            i++
        }

        c.translate(0f, 50f)

        // Bottom half: rrects (rows j = 3+)
        i = 0
        for (width in widths) {
            var j = 3
            for (join in joins) {
                if (width < 0f && join != SkPaint.Join.kMiter_Join) continue
                draw(2*i, 2*j,   true, width, join)
                draw(2*i+1, 2*j, true, width, join)
                draw(2*i, 2*j+1, true, width, join)
                draw(2*i+1, 2*j+1, true, width, join)
                j++
            }
            i++
        }

        // --- "football" shapes (rotated alternating big corners) -------------
        fun drawComplex(cx: Int, cy: Int, width: Float, stretch: Float) {
            val p = SkPaint().apply {
                isAntiAlias = true
                strokeWidth = width
                style = SkPaint.Style.kStroke_Style
                strokeJoin = SkPaint.Join.kBevel_Join
            }
            c.save()
            c.translate(cx * 110f, cy * 110f)
            val rect = SkRect.MakeWH(
                if (cx % 2 != 0) 50f else (40f + stretch),
                if (cx % 2 != 0) (40f + stretch) else 50f
            )
            val bigCorner = SkVector(30f, 30f)
            val rectCorner = SkVector(0f, 0f)
            val strokeRadii = arrayOf(
                if (cy % 2 != 0) rectCorner else bigCorner,
                if (cy % 2 != 0) bigCorner  else rectCorner,
                if (cy % 2 != 0) rectCorner else bigCorner,
                if (cy % 2 != 0) bigCorner  else rectCorner,
            )
            val r = SkRRect()
            r.setRectRadii(rect, strokeRadii)
            c.drawRRect(r, p)
            c.restore()
        }

        c.translate(0f, -50f)
        i = 6
        for (width in floatArrayOf(50f, 30f, 20f, 10f, 1f, 0f)) {
            var j = 0
            for (stretch in floatArrayOf(0f, 5f, 10f)) {
                drawComplex(2*i, 2*j,   width, stretch)
                drawComplex(2*i+1, 2*j, width, stretch)
                drawComplex(2*i, 2*j+1, width, stretch)
                drawComplex(2*i+1, 2*j+1, width, stretch)
                j++
            }
            i++
        }

        // --- "D" shapes (miter join, alternating corner pattern) -------------
        fun drawComplex2(cx: Int, cy: Int, width: Float, stretch: Float) {
            val p = SkPaint().apply {
                isAntiAlias = true
                strokeWidth = width
                style = SkPaint.Style.kStroke_Style
                strokeJoin = SkPaint.Join.kMiter_Join
            }
            c.save()
            c.translate(cx * 110f, cy * 110f)
            val rect = SkRect.MakeWH(
                if (cx % 2 != 0) 50f else (40f + stretch),
                if (cx % 2 != 0) (40f + stretch) else 50f
            )
            val bigCorner = SkVector(30f, 30f)
            val rectCorner = SkVector(0f, 0f)
            val strokeRadii = arrayOf(
                if (cx % 2 != 0) rectCorner else bigCorner,
                if ((cx % 2) xor (cy % 2) != 0) bigCorner  else rectCorner,
                if (cx % 2 != 0) bigCorner  else rectCorner,
                if ((cx % 2) xor (cy % 2) != 0) rectCorner else bigCorner,
            )
            val r = SkRRect()
            r.setRectRadii(rect, strokeRadii)
            c.drawRRect(r, p)
            c.restore()
        }

        c.translate(0f, 50f)
        i = 6
        for (width in floatArrayOf(50f, 30f, 20f, 10f, 1f, 0f)) {
            var j = 3
            for (stretch in floatArrayOf(0f, 5f, 10f)) {
                drawComplex2(2*i, 2*j,   width, stretch)
                drawComplex2(2*i+1, 2*j, width, stretch)
                drawComplex2(2*i, 2*j+1, width, stretch)
                drawComplex2(2*i+1, 2*j+1, width, stretch)
                j++
            }
            i++
        }
    }
}
