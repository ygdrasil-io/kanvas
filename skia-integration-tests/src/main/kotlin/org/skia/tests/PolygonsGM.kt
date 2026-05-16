package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.skia.tools.SkRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/polygons.cpp::PolygonsGM` (840 × 1140).
 *
 * Grid of 8 polygons (triangle, trapezoid, diamond, octagon, 32-edge
 * circle-ish, concave quad, stairs, 5-point star) drawn under :
 *   - 3 stroke joins (Miter / Round / Bevel) × 3 stroke widths
 *     (0 hairline / 10 / 40) = 9 stroke rows.
 *   - 2 extra rows : `kStrokeAndFill_Style` and `kFill_Style`, each with
 *     `kMiter_Join` and stroke width 20.
 *
 * Total 11 rows × 8 cols × 100 px cells + 30 px padding =
 * `(8 × 100 + 40)` × `(11 × 100 + 40)` = 840 × 1140.
 *
 * Each cell consumes one `SkRandom.nextU()` for its colour ; widths of
 * 40 force a translucent alpha (0xA0) so the `setStrokeWidth >=
 * geometry-extent` cells render as filled-with-alpha contours.
 */
public class PolygonsGM : GM() {

    override fun getName(): String = "polygons"
    override fun getISize(): SkISize = SkISize.Make(
        kNumPolygons * kCellSize + 40,
        (kNumJoins * kNumStrokeWidths + kNumExtraStyles) * kCellSize + 40,
    )

    private val polygons: Array<SkPath> = buildPolygons()

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply { isAntiAlias = true }
        val rand = SkRandom()

        // Stroke section : 3 joins × 3 widths × 8 polygons.
        paint.style = SkPaint.Style.kStroke_Style
        var counter = 0
        for (joinIdx in 0 until kNumJoins) {
            for (widthIdx in 0 until kNumStrokeWidths) {
                for (poly in polygons) {
                    c.save()
                    setLocation(c, counter, polygons.size)
                    setColorAndAlpha(paint, rand)
                    paint.strokeJoin = kJoins[joinIdx]
                    paint.strokeWidth = kStrokeWidths[widthIdx].toFloat()
                    c.drawPath(poly, paint)
                    c.restore()
                    counter++
                }
            }
        }

        // 2 extra rows : strokeAndFill, fill.
        paint.strokeJoin = SkPaint.Join.kMiter_Join
        paint.strokeWidth = 20f
        for (style in kExtraStyles) {
            paint.style = style
            for (poly in polygons) {
                c.save()
                setLocation(c, counter, polygons.size)
                setColorAndAlpha(paint, rand)
                c.drawPath(poly, paint)
                c.restore()
                counter++
            }
        }
    }

    private fun setLocation(canvas: SkCanvas, counter: Int, lineNum: Int) {
        val x = kCellSize * (counter % lineNum) + 30 + 0.25f
        val y = kCellSize * (counter / lineNum) + 30 + 0.75f
        canvas.translate(x, y)
    }

    private fun setColorAndAlpha(paint: SkPaint, rand: SkRandom) {
        var color = rand.nextU()
        color = color or 0xFF000000.toInt()
        paint.color = color
        if (paint.strokeWidth == 40f) {
            paint.color = (paint.color and 0x00FFFFFF) or (0xA0 shl 24)
        }
    }

    private fun buildPolygons(): Array<SkPath> {
        val p0 = listOf(0f to 0f, 60f to 0f, 90f to 40f) // triangle
        val p1 = listOf(0f to 0f, 0f to 40f, 60f to 40f, 40f to 0f) // trapezoid
        val p2 = listOf(0f to 0f, 40f to 40f, 80f to 40f, 40f to 0f) // diamond
        val p3 = listOf(
            10f to 0f, 50f to 0f, 60f to 10f, 60f to 30f,
            50f to 40f, 10f to 40f, 0f to 30f, 0f to 10f,
        ) // octagon
        val p4 = (0 until 32).map { i ->                              // 32-edge circle-ish
            val angle = 2 * PI.toFloat() * i / 32f
            (20f * cos(angle) + 20f) to (20f * sin(angle) + 20f)
        }
        val p5 = listOf(0f to 0f, 20f to 20f, 0f to 40f, 60f to 20f) // concave quad
        val p6 = listOf(
            0f to 40f, 0f to 30f, 15f to 30f, 15f to 20f, 30f to 20f,
            30f to 10f, 45f to 10f, 45f to 0f, 60f to 0f, 60f to 40f,
        ) // stairs
        val p7 = listOf(
            0f to 20f, 20f to 20f, 30f to 0f, 40f to 20f, 60f to 20f,
            45f to 30f, 55f to 50f, 30f to 40f, 5f to 50f, 15f to 30f,
        ) // 5-point star

        val all = arrayOf(p0, p1, p2, p3, p4, p5, p6, p7)
        return Array(all.size) { i ->
            val pts = all[i]
            val b = SkPathBuilder()
            b.moveTo(pts[0].first, pts[0].second)
            for (j in 1 until pts.size) b.lineTo(pts[j].first, pts[j].second)
            b.close()
            b.detach()
        }
    }

    private companion object {
        const val kNumPolygons: Int = 8
        const val kCellSize: Int = 100
        const val kNumExtraStyles: Int = 2
        const val kNumStrokeWidths: Int = 3
        const val kNumJoins: Int = 3

        val kStrokeWidths: IntArray = intArrayOf(0, 10, 40)
        val kJoins: Array<SkPaint.Join> = arrayOf(
            SkPaint.Join.kMiter_Join, SkPaint.Join.kRound_Join, SkPaint.Join.kBevel_Join,
        )
        val kExtraStyles: Array<SkPaint.Style> = arrayOf(
            SkPaint.Style.kStrokeAndFill_Style, SkPaint.Style.kFill_Style,
        )
    }
}
