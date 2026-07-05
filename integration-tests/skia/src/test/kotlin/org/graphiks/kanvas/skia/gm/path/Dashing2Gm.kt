package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Port of Skia's `gm/dashing.cpp` (Dashing2GM).
 * 3 dash patterns × 4 shapes (line, rect, oval, star).
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class Dashing2Gm : SkiaGm {
    override val name = "dashing2"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 83.9
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val patterns: Array<FloatArray> = arrayOf(
            floatArrayOf(10f, 10f),
            floatArrayOf(20f, 5f, 5f, 5f),
            floatArrayOf(2f, 2f),
        )

        val procs: List<(Rect) -> Path> = listOf(
            ::makePathLine, ::makePathRect, ::makePathOval, ::makePathStar,
        )

        val bounds = Rect.fromXYWH(20f, 20f, 120f, 120f)
        val dx = bounds.width * 4f / 3f
        val dy = bounds.height * 4f / 3f

        for (y in patterns.indices) {
            val vals = patterns[y]
            val phase = vals[0] / 2f
            val paint = Paint(
                style = PaintStyle.STROKE,
                strokeWidth = 6f,
                antiAlias = true,
                pathEffect = PathEffect.Dash(vals, phase),
            )

            for (x in procs.indices) {
                val r = Rect.fromLTRB(
                    bounds.left + x * dx, bounds.top + y * dy,
                    bounds.right + x * dx, bounds.bottom + y * dy,
                )
                canvas.drawPath(procs[x](r), paint)
            }
        }
    }

    private companion object {
        fun makePathLine(b: Rect): Path =
            Path { moveTo(b.left, b.top); lineTo(b.right, b.bottom) }

        fun makePathRect(b: Rect): Path =
            Path { }.also { it.addRect(b) }

        fun makePathOval(b: Rect): Path =
            Path { }.also { it.addOval(b) }

        fun makePathStar(b: Rect): Path {
            val cx = b.center.x
            val cy = b.center.y
            val r = min(b.width, b.height) / 2f
            val n = 5
            var rad = -PI.toFloat() / 2f
            val drad = (n shr 1) * PI.toFloat() * 2f / n
            val path = Path { moveTo(cx, cy - r) }
            for (i in 1 until n) {
                rad += drad
                path.lineTo(cx + r * cos(rad), cy + r * sin(rad))
            }
            path.close()
            return path
        }
    }
}
