package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/closedcappedhairlines.cpp::hairlines_squarecap` (250 × 250).
 *
 * Same layout as HairlinesButtcapGM (4 rows × 3 shapes, 1× + 4× renders,
 * pixel grid, endpoint highlights) but with [StrokeCap.SQUARE]. Tests
 * that square caps (which extend 0.5 px past the endpoint) do not produce
 * visible artefacts on closed contours where no cap should appear.
 */
class HairlinesSquarecapGm : SkiaGm {
    override val name = "hairlines_squarecap"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kScale = 4
        val kGridWh = 70

        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
            color = Color.BLACK,
            antiAlias = true,
            strokeCap = StrokeCap.SQUARE,
        )

        data class PathDef(val path: Path, val first: Pair<Float, Float>, val last: Pair<Float, Float>)

        fun makeOffset(builder: Path, dx: Float, dy: Float): Path =
            builder.transform(Matrix33.translate(dx, dy))

        val lineBase = Path { moveTo(0f, 0f); lineTo(0f, 5f); lineTo(5f, 5f); lineTo(5f, 0f) }
        val quadBase = Path { moveTo(0f, 0f); quadTo(15f, 5f, 0f, 10f) }
        val cubicBase = Path { moveTo(0f, 0f); cubicTo(-5f, 0f, -5f, 5f, 0f, 10f) }

        val lineOnOpen = PathDef(makeOffset(lineBase, 5f, 5f), 5f to 5f, 10f to 5f)
        val quadOnOpen = PathDef(makeOffset(quadBase, 20f, 5f), 20f to 5f, 20f to 15f)
        val cubicOnOpen = PathDef(makeOffset(cubicBase, 40f, 5f), 40f to 5f, 40f to 15f)

        val lineOffOpen = PathDef(makeOffset(lineBase, 5.5f, 20.5f), 5.5f to 20.5f, 10.5f to 20.5f)
        val quadOffOpen = PathDef(makeOffset(quadBase, 20.5f, 20.5f), 20.5f to 20.5f, 20.5f to 30.5f)
        val cubicOffOpen = PathDef(makeOffset(cubicBase, 40.5f, 20.5f), 40.5f to 20.5f, 40.5f to 30.5f)

        fun closePath(p: Path): Path {
            val p2 = Path { }; p2.addPath(p); p2.close(); return p2
        }

        val lineOnClosed = PathDef(closePath(makeOffset(lineBase, 5f, 35f)), 5f to 35f, 10f to 35f)
        val quadOnClosed = PathDef(closePath(makeOffset(quadBase, 20f, 35f)), 20f to 35f, 20f to 45f)
        val cubicOnClosed = PathDef(closePath(makeOffset(cubicBase, 40f, 35f)), 40f to 35f, 40f to 45f)

        val lineOffClosed = PathDef(closePath(makeOffset(lineBase, 5.5f, 50.5f)), 5.5f to 50.5f, 10.5f to 50.5f)
        val quadOffClosed = PathDef(closePath(makeOffset(quadBase, 20.5f, 50.5f)), 20.5f to 50.5f, 20.5f to 60.5f)
        val cubicOffClosed = PathDef(closePath(makeOffset(cubicBase, 40.5f, 50.5f)), 40.5f to 50.5f, 40.5f to 60.5f)

        val allPaths = listOf(lineOnOpen, quadOnOpen, cubicOnOpen, lineOffOpen, quadOffOpen, cubicOffOpen,
            lineOnClosed, quadOnClosed, cubicOnClosed, lineOffClosed, quadOffClosed, cubicOffClosed)
        val openPaths = listOf(lineOnOpen, quadOnOpen, cubicOnOpen, lineOffOpen, quadOffOpen, cubicOffOpen)

        for ((p, _, _) in allPaths) canvas.drawPath(p, paint)

        canvas.save()
        canvas.translate(60f, 0f)
        canvas.scale(kScale.toFloat(), kScale.toFloat())
        for ((p, _, _) in allPaths) canvas.drawPath(p, paint)

        val gridPaint = Paint(color = Color.fromRGBA(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f),
            style = PaintStyle.STROKE, strokeWidth = 0f)
        for (y in 0..kGridWh) canvas.drawLine(0f, y.toFloat(), kGridWh.toFloat(), y.toFloat(), gridPaint)
        for (x in 0..kGridWh) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), kGridWh.toFloat(), gridPaint)

        val highlightPaint = Paint(color = Color.RED, style = PaintStyle.STROKE, strokeWidth = 0f, antiAlias = true)
        for ((_, first, last) in openPaths) {
            canvas.drawRect(Rect.fromXYWH(first.first - 2f, first.second - 2f, 4f, 4f), highlightPaint)
            canvas.drawRect(Rect.fromXYWH(last.first - 2f, last.second - 2f, 4f, 4f), highlightPaint)
        }
        canvas.restore()
    }
}
