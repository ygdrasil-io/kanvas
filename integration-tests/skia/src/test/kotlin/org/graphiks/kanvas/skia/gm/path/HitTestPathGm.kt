package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.PointMode
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/** Port of Skia's `gm/hittestpath.cpp`.
 *  Tests hit-testing on paths — draws paths and points with various
 *  fill types, transforms, and point modes.
 *  @see https://github.com/google/skia/blob/main/gm/hittestpath.cpp
 */
class HitTestPathGm : SkiaGm {
    override val name = "hittestpath"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 460

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rand = Random(0)
        val scale = 300

        val path = Path { }
        path.moveTo(0f, 0f)

        var minX = 0f; var maxX = 0f
        var minY = 0f; var maxY = 0f

        fun track(x: Float, y: Float) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }

        for (i in 0 until 4) {
            val randoms = FloatArray(12) { rand.nextFloat() }
            val x1 = randoms[0] * scale; val y1 = randoms[1] * scale
            track(x1, y1)
            path.lineTo(x1, y1)

            val qx = randoms[2] * scale; val qy = randoms[3] * scale
            val qex = randoms[4] * scale; val qey = randoms[5] * scale
            track(qx, qy); track(qex, qey)
            path.quadTo(qx, qy, qex, qey)

            val cx1 = randoms[6] * scale; val cy1 = randoms[7] * scale
            val cx2 = randoms[8] * scale; val cy2 = randoms[9] * scale
            val cex = randoms[10] * scale; val cey = randoms[11] * scale
            track(cx1, cy1); track(cx2, cy2); track(cex, cey)
            path.cubicTo(cx1, cy1, cx2, cy2, cex, cey)
        }

        path.fillType = FillType.EVEN_ODD
        val offsetPath = path.transform(Matrix33.translate(20f, 20f))
        val margin = 4f
        val bounds = Rect.fromLTRB(minX + 20f, minY + 20f, maxX + 20f, maxY + 20f)

        testHittest(canvas, offsetPath, bounds, margin)

        canvas.translate(scale.toFloat(), 0f)
        offsetPath.fillType = FillType.WINDING
        testHittest(canvas, offsetPath, bounds, margin)
    }

    private fun testHittest(
        canvas: GmCanvas,
        path: Path,
        r: Rect,
        margin: Float,
    ) {
        val paint = Paint(color = Color.RED)
        canvas.drawPath(path, paint)

        val hits = ArrayList<Point>()
        var y = r.top + 0.5f - margin
        while (y < r.bottom + margin) {
            var x = r.left + 0.5f - margin
            while (x < r.right + margin) {
                if (path.contains(Point(x, y))) {
                    hits.add(Point(x, y))
                }
                x += 1f
            }
            y += 1f
        }
        if (hits.isNotEmpty()) {
            canvas.drawPoints(
                PointMode.POINTS, hits,
                Paint(
                    color = Color(0x800000FFu),
                    style = PaintStyle.STROKE,
                    strokeWidth = 0f,
                ),
            )
        }
    }
}
