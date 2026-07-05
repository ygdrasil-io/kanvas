package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import kotlin.random.Random

class PointsGm : SkiaGm {
    override val name = "points"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 490

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(1f, 1f)

        val rand = Random(0)
        val n = 99
        val pts = List(n) {
            val y = rand.nextFloat() * 480f
            val x = rand.nextFloat() * 640f
            Point(x, y)
        }

        val p0 = Paint(color = Color.RED, style = PaintStyle.STROKE, strokeWidth = 4f)
        val p1 = Paint(color = Color.GREEN, style = PaintStyle.STROKE)
        val p2 = Paint(color = Color.BLUE, style = PaintStyle.STROKE, strokeCap = StrokeCap.ROUND, strokeWidth = 6f)
        val p3 = Paint(color = Color.WHITE, style = PaintStyle.STROKE)

        canvas.drawPoints(PointMode.POLYGON, pts, p0)
        canvas.drawPoints(PointMode.LINES, pts, p1)
        canvas.drawPoints(PointMode.POINTS, pts, p2)
        canvas.drawPoints(PointMode.POINTS, pts, p3)
    }
}
