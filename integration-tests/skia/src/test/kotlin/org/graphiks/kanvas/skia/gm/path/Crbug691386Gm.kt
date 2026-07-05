package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_691386.cpp::crbug_691386`.
 * A tiny unit-radius half-arc stroked at sub-pixel width, scaled up 96x and centred.
 * @see https://github.com/google/skia/blob/main/gm/crbug_691386.cpp
 */
class Crbug691386Gm : SkiaGm {
    override val name = "crbug_691386"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(-1f, 0f)
            arcTo(1f, 1f, 0f, false, false, 1f, 0f)
            close()
        }
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 0.025f,
        )
        canvas.scale(96f, 96f)
        canvas.translate(1.25f, 1.25f)
        canvas.drawPath(path, paint)
    }
}
