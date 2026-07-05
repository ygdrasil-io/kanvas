package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/crbug_1086705.cpp`
 * (`DEF_SIMPLE_GM(crbug_1086705, canvas, 200, 200)`).
 *
 * Repro for a chromium issue where the convex-path linearising renderer
 * collapsed too many near-duplicate vertices and degenerated the path
 * into a triangle.
 *
 * The geometry is a 700-vertex polygon approximating a circle of
 * radius 2 around `(100, 100)`. Stroked at `strokeWidth = 5` (so the
 * stroke is wider than the radius — the stroke self-intersects).
 *
 * Stresses :
 *  - Long polylines (700 lineTo verbs in a single contour);
 *  - self-intersecting strokes from very-near-duplicate vertices;
 *  - the AA stroke rasterizer's response to a stroke whose width
 *    overflows the source-shape inscribed circle.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1086705.cpp
 */
class Crbug1086705Gm : SkiaGm {
    override val name = "crbug_1086705"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 94.8
    override val width = 200
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 5f,
            antiAlias = true,
        )

        val path = Path {
            val first = vertexAt(0)
            moveTo(first.first, first.second)
            for (i in 1 until 700) {
                val v = vertexAt(i)
                lineTo(v.first, v.second)
            }
            close()
        }

        canvas.drawPath(path, paint)
    }

    private fun vertexAt(i: Int): Pair<Float, Float> {
        val angleRads = 2f * PI.toFloat() * i / 700f
        val x = 100f + 2f * cos(angleRads.toDouble()).toFloat()
        val y = 100f + 2f * sin(angleRads.toDouble()).toFloat()
        return x to y
    }
}
