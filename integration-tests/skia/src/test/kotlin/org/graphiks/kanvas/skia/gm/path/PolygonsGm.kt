package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.skia.SkiaRandom
import org.graphiks.kanvas.types.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/polygons.cpp::PolygonsGM` (840 x 1140).
 *
 * Grid of 8 polygons (triangle, trapezoid, diamond, octagon, 32-edge
 * circle-ish, concave quad, stairs, 5-point star) drawn under:
 *   - 3 stroke joins (Miter / Round / Bevel) x 3 stroke widths
 *     (0 hairline / 10 / 40) = 9 stroke rows.
 *   - 2 extra rows: STROKE (equivalent to kStrokeAndFill_Style) and FILL,
 *     each with Miter join and stroke width 20.
 *
 * Total 11 rows x 8 cols x 100 px cells + 30 px padding =
 * (8 x 100 + 40) x (11 x 100 + 40) = 840 x 1140.
 */
class PolygonsGm : SkiaGm {
    override val name = "polygons"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 69.0

    override val width = kNumPolygons * kCellSize + 40
    override val height = (kNumJoins * kNumStrokeWidths + kNumExtraStyles) * kCellSize + 40

    private val polygons: List<Path> = buildPolygons()

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = SkiaRandom(42u)
        var counter = 0

        // Stroke section: 3 joins x 3 widths x 8 polygons
        for (joinIdx in 0 until kNumJoins) {
            for (widthIdx in 0 until kNumStrokeWidths) {
                for (poly in polygons) {
                    canvas.save()
                    setLocation(canvas, counter, polygons.size)
                    val paint = makePaint(rand, kJoins[joinIdx], kStrokeWidths[widthIdx], PaintStyle.STROKE)
                    canvas.drawPath(poly, paint)
                    canvas.restore()
                    counter++
                }
            }
        }

        // 2 extra rows: stroke (stand-in for strokeAndFill) and fill
        for (style in kExtraStyles) {
            for (poly in polygons) {
                canvas.save()
                setLocation(canvas, counter, polygons.size)
                val paint = makePaint(rand, StrokeJoin.MITER, 20f, style)
                canvas.drawPath(poly, paint)
                canvas.restore()
                counter++
            }
        }
    }

    private fun setLocation(canvas: GmCanvas, counter: Int, lineNum: Int) {
        val x = (kCellSize * (counter % lineNum) + 30) + 0.25f
        val y = (kCellSize * (counter / lineNum) + 30) + 0.75f
        canvas.translate(x, y)
    }

    private fun makePaint(rand: SkiaRandom, join: StrokeJoin, width: Float, style: PaintStyle): Paint {
        val raw = rand.nextS() or 0xFF000000.toInt()
        val alpha = if (width == 40f) 0xA0 else 0xFF
        val adjusted = (raw and 0x00FFFFFF) or (alpha shl 24)
        return Paint(
            color = Color.fromRGBA(
                ((adjusted shr 16) and 0xFF) / 255f,
                ((adjusted shr 8) and 0xFF) / 255f,
                (adjusted and 0xFF) / 255f,
                alpha / 255f,
            ),
            strokeJoin = join,
            strokeWidth = width,
            style = style,
            antiAlias = true,
        )
    }

    private fun buildPolygons(): List<Path> {
        val p0 = listOf(0f to 0f, 60f to 0f, 90f to 40f)
        val p1 = listOf(0f to 0f, 0f to 40f, 60f to 40f, 40f to 0f)
        val p2 = listOf(0f to 0f, 40f to 40f, 80f to 40f, 40f to 0f)
        val p3 = listOf(
            10f to 0f, 50f to 0f, 60f to 10f, 60f to 30f,
            50f to 40f, 10f to 40f, 0f to 30f, 0f to 10f,
        )
        val p4 = (0 until 32).map { i ->
            val angle = 2 * PI.toFloat() * i / 32f
            (20f * cos(angle) + 20f) to (20f * sin(angle) + 20f)
        }
        val p5 = listOf(0f to 0f, 20f to 20f, 0f to 40f, 60f to 20f)
        val p6 = listOf(
            0f to 40f, 0f to 30f, 15f to 30f, 15f to 20f, 30f to 20f,
            30f to 10f, 45f to 10f, 45f to 0f, 60f to 0f, 60f to 40f,
        )
        val p7 = listOf(
            0f to 20f, 20f to 20f, 30f to 0f, 40f to 20f, 60f to 20f,
            45f to 30f, 55f to 50f, 30f to 40f, 5f to 50f, 15f to 30f,
        )

        val all = listOf(p0, p1, p2, p3, p4, p5, p6, p7)
        return all.map { pts ->
            Path {
                moveTo(pts[0].first, pts[0].second)
                for (j in 1 until pts.size) lineTo(pts[j].first, pts[j].second)
                close()
            }
        }
    }

    private companion object {
        const val kNumPolygons: Int = 8
        const val kCellSize: Int = 100
        const val kNumExtraStyles: Int = 2
        const val kNumStrokeWidths: Int = 3
        const val kNumJoins: Int = 3

        val kStrokeWidths: FloatArray = floatArrayOf(0f, 10f, 40f)
        val kJoins: List<StrokeJoin> = listOf(
            StrokeJoin.MITER, StrokeJoin.ROUND, StrokeJoin.BEVEL,
        )
        val kExtraStyles: List<PaintStyle> = listOf(
            PaintStyle.STROKE, PaintStyle.FILL,
        )
    }
}
