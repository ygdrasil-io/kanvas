package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
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
 * Reference image: `crbug_1086705.png`, 200 × 200, default white BG.
 *
 * Stresses :
 *  - Long polylines (700 lineTo verbs in a single contour);
 *  - self-intersecting strokes from very-near-duplicate vertices;
 *  - the AA stroke rasterizer's response to a stroke whose width
 *    overflows the source-shape inscribed circle.
 */
public class Crbug1086705GM : GM() {

    override fun getName(): String = "crbug_1086705"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 5f
            isAntiAlias = true
        }

        val builder = SkPathBuilder()
        // Match upstream's tight loop: 700 vertices around a 2-radius
        // circle, then close.
        val first = vertexAt(0)
        builder.moveTo(first.first, first.second)
        for (i in 1 until 700) {
            val v = vertexAt(i)
            builder.lineTo(v.first, v.second)
        }
        builder.close()

        c.drawPath(builder.detach(), paint)
    }

    private fun vertexAt(i: Int): Pair<Float, Float> {
        val angleRads = 2f * PI.toFloat() * i / 700f
        val x = 100f + 2f * cos(angleRads.toDouble()).toFloat()
        val y = 100f + 2f * sin(angleRads.toDouble()).toFloat()
        return x to y
    }
}
