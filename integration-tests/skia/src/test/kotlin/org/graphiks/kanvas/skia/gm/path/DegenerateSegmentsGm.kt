package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/degeneratesegments.cpp`.
 * Random-but-deterministic paths assembled from a palette of 21 segment functions.
 * @see https://github.com/google/skia/blob/main/gm/degeneratesegments.cpp
 */
class DegenerateSegmentsGm : SkiaGm {
    override val name = "degeneratesegments"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 896
    override val height = 930

    private fun interface AddSegment {
        fun add(path: Path, start: Point): Point
    }

    private fun pt(x: Float, y: Float) = Point(x, y)

    private val addMove = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y)
        moveTo
    }
    private val addMoveClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y); path.close()
        moveTo
    }
    private val addDegenLine = AddSegment { path, startPt ->
        path.lineTo(startPt.x, startPt.y); startPt
    }
    private val addMoveDegenLine = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y); path.lineTo(moveTo.x, moveTo.y)
        moveTo
    }
    private val addMoveDegenLineClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y); path.lineTo(moveTo.x, moveTo.y); path.close()
        moveTo
    }
    private val addDegenQuad = AddSegment { path, startPt ->
        path.quadTo(startPt.x, startPt.y, startPt.x, startPt.y); startPt
    }
    private val addMoveDegenQuad = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y); path.quadTo(moveTo.x, moveTo.y, moveTo.x, moveTo.y)
        moveTo
    }
    private val addMoveDegenQuadClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y); path.quadTo(moveTo.x, moveTo.y, moveTo.x, moveTo.y); path.close()
        moveTo
    }
    private val addDegenCubic = AddSegment { path, startPt ->
        path.cubicTo(startPt.x, startPt.y, startPt.x, startPt.y, startPt.x, startPt.y); startPt
    }
    private val addMoveDegenCubic = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y)
        path.cubicTo(moveTo.x, moveTo.y, moveTo.x, moveTo.y, moveTo.x, moveTo.y)
        moveTo
    }
    private val addMoveDegenCubicClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        path.moveTo(moveTo.x, moveTo.y)
        path.cubicTo(moveTo.x, moveTo.y, moveTo.x, moveTo.y, moveTo.x, moveTo.y); path.close()
        moveTo
    }
    private val addClose = AddSegment { path, startPt ->
        path.close(); startPt
    }
    private val addLine = AddSegment { path, startPt ->
        val endPt = pt(startPt.x + 40f, startPt.y)
        path.lineTo(endPt.x, endPt.y); endPt
    }
    private val addMoveLine = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        val endPt = pt(moveTo.x + 40f, moveTo.y)
        path.moveTo(moveTo.x, moveTo.y); path.lineTo(endPt.x, endPt.y)
        endPt
    }
    private val addMoveLineClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        val endPt = pt(moveTo.x + 40f, moveTo.y)
        path.moveTo(moveTo.x, moveTo.y); path.lineTo(endPt.x, endPt.y); path.close()
        endPt
    }
    private val addQuad = AddSegment { path, startPt ->
        val midPt = pt(startPt.x + 20f, startPt.y + 5f)
        val endPt = pt(startPt.x + 40f, startPt.y)
        path.quadTo(midPt.x, midPt.y, endPt.x, endPt.y); endPt
    }
    private val addMoveQuad = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        val midPt = pt(moveTo.x + 20f, moveTo.y + 5f)
        val endPt = pt(moveTo.x + 40f, moveTo.y)
        path.moveTo(moveTo.x, moveTo.y); path.quadTo(midPt.x, midPt.y, endPt.x, endPt.y)
        endPt
    }
    private val addMoveQuadClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        val midPt = pt(moveTo.x + 20f, moveTo.y + 5f)
        val endPt = pt(moveTo.x + 40f, moveTo.y)
        path.moveTo(moveTo.x, moveTo.y); path.quadTo(midPt.x, midPt.y, endPt.x, endPt.y); path.close()
        endPt
    }
    private val addCubic = AddSegment { path, startPt ->
        val t1 = pt(startPt.x + 15f, startPt.y + 5f)
        val t2 = pt(startPt.x + 25f, startPt.y + 5f)
        val endPt = pt(startPt.x + 40f, startPt.y)
        path.cubicTo(t1.x, t1.y, t2.x, t2.y, endPt.x, endPt.y)
        endPt
    }
    private val addMoveCubic = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        val t1 = pt(moveTo.x + 15f, moveTo.y + 5f)
        val t2 = pt(moveTo.x + 25f, moveTo.y + 5f)
        val endPt = pt(moveTo.x + 40f, moveTo.y)
        path.moveTo(moveTo.x, moveTo.y)
        path.cubicTo(t1.x, t1.y, t2.x, t2.y, endPt.x, endPt.y)
        endPt
    }
    private val addMoveCubicClose = AddSegment { path, startPt ->
        val moveTo = pt(startPt.x, startPt.y + 10f)
        val t1 = pt(moveTo.x + 15f, moveTo.y + 5f)
        val t2 = pt(moveTo.x + 25f, moveTo.y + 5f)
        val endPt = pt(moveTo.x + 40f, moveTo.y)
        path.moveTo(moveTo.x, moveTo.y)
        path.cubicTo(t1.x, t1.y, t2.x, t2.y, endPt.x, endPt.y); path.close()
        endPt
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val segments = arrayOf(
            addMove, addMoveClose, addDegenLine, addMoveDegenLine, addMoveDegenLineClose,
            addDegenQuad, addMoveDegenQuad, addMoveDegenQuadClose,
            addDegenCubic, addMoveDegenCubic, addMoveDegenCubicClose,
            addClose,
            addLine, addMoveLine, addMoveLineClose,
            addQuad, addMoveQuad, addMoveQuadClose,
            addCubic, addMoveCubic, addMoveCubicClose,
        )

        val fills = arrayOf(FillType.WINDING, FillType.EVEN_ODD, FillType.INVERSE_WINDING, FillType.INVERSE_EVEN_ODD)
        val styles = arrayOf(PaintStyle.FILL, PaintStyle.STROKE)
        val caps = arrayOf(StrokeCap.BUTT to StrokeJoin.BEVEL, StrokeCap.ROUND to StrokeJoin.ROUND, StrokeCap.SQUARE to StrokeJoin.BEVEL)

        val rand = Random(0)
        val rect = Rect(0f, 0f, 220f, 50f)
        canvas.save()
        canvas.translate(2f, 30f)
        canvas.save()
        for (row in 0 until 6) {
            if (row > 0) canvas.translate(0f, 150f)
            canvas.save()
            for (column in 0 until 4) {
                if (column > 0) canvas.translate(224f, 0f)

                val style = styles[rand.nextInt(styles.size)]
                val (cap, join) = caps[rand.nextInt(caps.size)]
                val fill = fills[rand.nextInt(fills.size)]
                val s1 = rand.nextInt(segments.size)
                val s2 = rand.nextInt(segments.size)
                val s3 = rand.nextInt(segments.size)
                val s4 = rand.nextInt(segments.size)
                val s5 = rand.nextInt(segments.size)

                var pt = Point(10f, 0f)
                val path = Path { }
                pt = segments[s1].add(path, pt)
                pt = segments[s2].add(path, pt)
                pt = segments[s3].add(path, pt)
                pt = segments[s4].add(path, pt)
                pt = segments[s5].add(path, pt)
                path.fillType = fill

                val paint = Paint(
                    color = Color.fromRGBA(0f, 0x70 / 255f, 0f, 1f),
                    style = style,
                    strokeWidth = 6f,
                    strokeCap = cap,
                    strokeJoin = join,
                )
                canvas.save()
                canvas.clipRect(rect)
                canvas.drawPath(path, paint)
                canvas.restore()

                canvas.drawRect(rect, Paint(color = Color.BLACK, style = PaintStyle.STROKE, strokeWidth = 0f, antiAlias = true))
            }
            canvas.restore()
        }
        canvas.restore()
        canvas.restore()
    }
}
