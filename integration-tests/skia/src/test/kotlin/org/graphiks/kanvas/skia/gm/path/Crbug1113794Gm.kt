package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_1113794.cpp` (600 x 200).
 * Sub-pixel-wide dashed line under non-uniform scale.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1113794.cpp
 */
class Crbug1113794Gm : SkiaGm {
    override val name = "crbug_1113794"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 600
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(50f, 80f)
            lineTo(50f, 20f)
        }

        val paint = Paint(
            color = Color.BLACK,
            antiAlias = true,
            strokeWidth = 0.25f,
            style = PaintStyle.STROKE,
            pathEffect = PathEffect.Dash(floatArrayOf(10f, 10f), 0f),
        )

        canvas.concat(Matrix33.scale(6f, 2f))
        canvas.drawPath(path, paint)
    }
}
