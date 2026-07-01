package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/pathcontourstart.cpp`.
 * Tests contour start position variation across rect, oval, and rrect path types with dash patterns.
 * @see https://github.com/google/skia/blob/main/gm/pathcontourstart.cpp
 */
class ContourStartGm : SkiaGm {
    override val name = "contour_start"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 36.5
    override val width = 1200
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kMaxDashLen = 100f
        val kDashGrowth = 1.2f
        val intervals = mutableListOf<Float>()
        var len = 1f
        while (len < kMaxDashLen) {
            intervals.add(len)
            intervals.add(len)
            len *= kDashGrowth
        }

        val dashPaint = Paint(
            color = Color.fromRGBA(0f, 0x80 / 255f, 0f, 1f),
            style = PaintStyle.STROKE,
            strokeWidth = 6f,
            antiAlias = true,
            pathEffect = PathEffect.Dash(intervals.toFloatArray(), 0f),
        )

        val rect = Rect(10f, 10f, 100f, 70f)
        val zeroRadii = CornerRadii(0f, 0f)
        val rectCorners = listOf(Point(rect.left, rect.top), Point(rect.right, rect.top), Point(rect.right, rect.bottom), Point(rect.left, rect.bottom))

        fun rectPath(cw: Boolean, startIndex: Int): Path {
            val ordered = if (cw) rectCorners else rectCorners.reversed()
            val s = startIndex % 4
            val shifted = ordered.subList(s, ordered.size) + ordered.subList(0, s)
            return Path {
                moveTo(shifted[0].x, shifted[0].y)
                lineTo(shifted[1].x, shifted[1].y)
                lineTo(shifted[2].x, shifted[2].y)
                lineTo(shifted[3].x, shifted[3].y)
                close()
            }
        }

        drawDirs(canvas, dashPaint, ::rectPath)
        drawDirs(canvas, dashPaint) { _, _ -> Path { }.apply { addOval(rect) } }
        drawDirs(canvas, dashPaint) { _, _ -> Path { }.apply { addRRect(RRect(rect, 15f)) } }
        drawDirs(canvas, dashPaint) { _, _ -> Path { }.apply { addRRect(RRect(rect, zeroRadii, zeroRadii, zeroRadii, zeroRadii)) } }
        drawDirs(canvas, dashPaint) { _, _ -> Path { }.apply { addOval(rect) } }
    }

    private fun drawDirs(canvas: GmCanvas, paint: Paint, makePath: (Boolean, Int) -> Path) {
        for (cw in listOf(true, false)) {
            canvas.save()
            for (i in 0 until 8) {
                canvas.drawPath(makePath(cw, i), paint)
                canvas.translate(0f, 75f)
            }
            canvas.restore()
            canvas.translate(120f, 0f)
        }
    }
}
