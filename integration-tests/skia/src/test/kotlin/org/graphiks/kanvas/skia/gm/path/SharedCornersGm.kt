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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Port of Skia's `gm/sharedcorners.cpp::SharedCornersGM`
 * Stress-tests analytic AA at shared corners of triangle meshes.
 * @see https://github.com/google/skia/blob/main/gm/sharedcorners.cpp
 */
class SharedCornersGm : SkiaGm {
    override val name = "sharedcorners"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 720
    override val height = 740

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x1A / 255f, 0x65 / 255f, 0xD7 / 255f)

        val fillPaint = Paint(color = Color.WHITE, antiAlias = true)
        val wirePaint = Paint(color = Color.WHITE, antiAlias = true, style = PaintStyle.STROKE)

        canvas.translate(kPadSize.toFloat(), kPadSize.toFloat())
        canvas.save()

        drawTriangleBoxes(canvas, fillPaint, wirePaint, listOf(
            Point(0f, 0f), Point(40f, 0f), Point(80f, 0f), Point(120f, 0f),
            Point(0f, 20f), Point(40f, 20f), Point(80f, 20f), Point(120f, 20f),
            Point(40f, 40f), Point(80f, 40f),
            Point(40f, 60f), Point(80f, 60f),
        ), listOf(
            intArrayOf(0, 1, 4), intArrayOf(1, 5, 4),
            intArrayOf(5, 1, 6), intArrayOf(1, 2, 6),
            intArrayOf(2, 3, 6), intArrayOf(3, 7, 6),
            intArrayOf(8, 5, 9), intArrayOf(5, 6, 9),
            intArrayOf(10, 8, 11), intArrayOf(8, 9, 11),
        ))

        drawTriangleBoxes(canvas, fillPaint, wirePaint, listOf(
            Point(0f, 0f), Point(10f, 0f), Point(20f, 0f),
            Point(0f, 2f), Point(20f, 2f),
            Point(10f, 4f),
            Point(0f, 6f), Point(20f, 6f),
            Point(0f, 8f), Point(10f, 8f), Point(20f, 8f),
        ), listOf(
            intArrayOf(3, 1, 4), intArrayOf(4, 5, 3), intArrayOf(6, 5, 7), intArrayOf(7, 9, 6),
            intArrayOf(0, 1, 3), intArrayOf(1, 2, 4),
            intArrayOf(3, 5, 6), intArrayOf(5, 4, 7),
            intArrayOf(6, 9, 8), intArrayOf(9, 7, 10),
        ))

        canvas.restore()
        canvas.translate(((kBoxSize + kPadSize) * 4).toFloat(), 0f)

        drawTriangleBoxes(canvas, fillPaint, wirePaint, listOf(
            Point(0f, 0f), Point(-1f, 0f), Point(0f, -1f), Point(1f, 0f), Point(0f, 1f),
        ), listOf(
            intArrayOf(0, 1, 2), intArrayOf(0, 2, 3), intArrayOf(0, 3, 4), intArrayOf(0, 4, 1),
        ))

        val rand = Random(42)
        val pts = mutableListOf(Point(0f, 0f))
        val tris = mutableListOf<IntArray>()
        var theta = 0f
        while (theta < 2f * PI.toFloat()) {
            pts.add(Point(cos(theta), sin(theta)))
            if (pts.size > 2) {
                tris.add(intArrayOf(0, pts.size - 2, pts.size - 1))
            }
            theta += rand.nextFloat() * (PI.toFloat() / 3f)
        }
        tris.add(intArrayOf(0, pts.size - 1, 1))
        drawTriangleBoxes(canvas, fillPaint, wirePaint, pts, tris)
    }

    private fun drawTriangleBoxes(
        canvas: GmCanvas,
        fillPaint: Paint,
        wirePaint: Paint,
        points: List<Point>,
        triangles: List<IntArray>,
    ) {
        val path = Path {
            for (tri in triangles) {
                moveTo(points[tri[0]].x, points[tri[0]].y)
                lineTo(points[tri[1]].x, points[tri[1]].y)
                lineTo(points[tri[2]].x, points[tri[2]].y)
                close()
            }
        }.apply { fillType = FillType.EVEN_ODD }

        val (minX, minY, maxX, maxY) = computeBounds(points)
        val pw = maxX - minX
        val ph = maxY - minY
        val scale = kBoxSize.toFloat() / maxOf(pw, ph)
        val scaled = path.transform(Matrix33.scale(scale, scale))

        drawRow(canvas, fillPaint, wirePaint, scaled)
        canvas.translate(0f, (kBoxSize + kPadSize).toFloat())

        val rot1 = scaled.transform(Matrix33.rotate(45f))
        drawRow(canvas, fillPaint, wirePaint, rot1)
        canvas.translate(0f, (kBoxSize + kPadSize).toFloat())

        val rot2 = rot1.transform(Matrix33.rotate(-69.38111f))
        drawRow(canvas, fillPaint, wirePaint, rot2)
        canvas.translate(0f, (kBoxSize + kPadSize).toFloat())
    }

    private fun drawRow(canvas: GmCanvas, fillPaint: Paint, wirePaint: Paint, path: Path) {
        canvas.save()
        canvas.drawPath(path, wirePaint)
        canvas.translate((kBoxSize + kPadSize).toFloat(), 0f)
        for (jitter in kJitters) {
            canvas.save()
            canvas.translate(jitter.x, jitter.y)
            canvas.drawPath(path, fillPaint)
            canvas.restore()
            canvas.translate((kBoxSize + kPadSize).toFloat(), 0f)
        }
        canvas.restore()
    }

    private fun computeBounds(points: List<Point>): Quadruple {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for (p in points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
        return Quadruple(minX, minY, maxX, maxY)
    }

    private data class Quadruple(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float)

    private companion object {
        const val kPadSize = 20
        const val kBoxSize = 100
        val kJitters = listOf(
            Point(0f, 0f),
            Point(0.5f, 0.5f),
            Point(2f / 3f, 1f / 3f),
        )
    }
}
