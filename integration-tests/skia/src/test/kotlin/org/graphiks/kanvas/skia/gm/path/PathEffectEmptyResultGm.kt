package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(path_effect_empty_result, …)`.
 * Strokes a degenerate 0×0 closed rect with a {2, 2} dash pattern.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class PathEffectEmptyResultGm : SkiaGm {
    override val name = "path_effect_empty_result"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            pathEffect = PathEffect.Dash(floatArrayOf(2f, 2f), 0f),
        )

        val r = 70f
        val l = 70f
        val t = 70f
        val b = 70f
        val path = Path {
            moveTo(l, t)
            lineTo(r, t)
            lineTo(r, b)
            lineTo(l, b)
            close()
        }

        canvas.drawPath(path, paint)
    }
}
