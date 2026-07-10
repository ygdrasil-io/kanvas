package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/convexpolyeffect.cpp`.
 * Tests convex polygon AA effect with triangles, octagons, and degenerate paths.
 * @see https://github.com/google/skia/blob/main/gm/convexpolyeffect.cpp
 */
class ConvexPolyEffectGm : SkiaGm {
    override val name = "convex_poly_effect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 720
    override val height = 550

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 1f, g = 1f, b = 1f)
        val paths = mutableListOf<Path>()
        val pathBounds = mutableListOf<Rect>()

        val tri = Path {
            moveTo(5f, 5f)
            lineTo(100f, 20f)
            lineTo(15f, 100f)
        }
        paths.add(tri)
        pathBounds.add(Rect(5f, 5f, 100f, 100f))
        paths.add(makeReverse(tri))
        pathBounds.add(Rect(5f, 5f, 100f, 100f))
        tri.close()
        paths.add(tri)
        pathBounds.add(Rect(5f, 5f, 100f, 100f))

        val ngon = Path {
            val cx = 50f
            val cy = 50f
            val r = 50f
            val n = 8
            for (i in 0 until n) {
                val angle = 2f * PI.toFloat() * i / n
                val px = cx + r * cos(angle)
                val py = cy + r * sin(angle)
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
            close()
        }
        paths.add(ngon)
        pathBounds.add(Rect(0f, 0f, 100f, 100f))
        val ngonScaled = ngon.transform(org.graphiks.kanvas.types.Matrix33.scale(1.1f, 0.4f))
        paths.add(ngonScaled)
        pathBounds.add(Rect(0f, 0f, 110f, 40f))

        val line = Path { moveTo(5f, 5f); lineTo(6f, 6f) }
        paths.add(line)
        pathBounds.add(Rect(5f, 5f, 6f, 6f))

        var y = 0f
        val dx = 12f
        val outset = 5f
        val fillPaint = Paint(antiAlias = false)
        val aaPaint = Paint(antiAlias = true)

        for ((idx, path) in paths.withIndex()) {
            val bounds = pathBounds[idx]
            val bw = bounds.right - bounds.left
            val bh = bounds.bottom - bounds.top

            canvas.save()
            canvas.translate(0f, y)
            canvas.drawPath(path, fillPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(bw + dx, y)
            canvas.drawPath(path, aaPaint)
            canvas.restore()

            val rectPaint = Paint(style = PaintStyle.STROKE, color = Color.BLACK, strokeWidth = 0.5f)
            canvas.save()
            canvas.translate(bw + dx, y)
            canvas.drawRect(
                Rect(
                    bounds.left - outset, bounds.top - outset,
                    bounds.right + outset, bounds.bottom + outset,
                ),
                rectPaint,
            )
            canvas.restore()

            y += bh + 20f
        }
    }

    private fun makeReverse(path: Path): Path {
        return Path { }.apply { reverseAddPath(path) }
    }
}
