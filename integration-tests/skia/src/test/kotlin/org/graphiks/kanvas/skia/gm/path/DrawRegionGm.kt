package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/drawregion.cpp` (`DrawRegionGM`, GM name `drawregion`).
 * Builds a 100x100 region of 1x1 pixels at every other position inside (50, 50, 250, 250).
 * @see https://github.com/google/skia/blob/main/gm/drawregion.cpp
 */
class DrawRegionGm : SkiaGm {
    override val name = "drawregion"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)

        val fillPaint = Paint(
            style = PaintStyle.FILL,
            color = Color.MAGENTA,
        )
        canvas.drawRect(Rect.fromLTRB(50f, 50f, 250f, 250f), fillPaint)

        val regionPaint = Paint(color = Color.CYAN)
        var x = 50
        while (x < 250) {
            var y = 50
            while (y < 250) {
                canvas.drawRect(Rect.fromXYWH(x.toFloat(), y.toFloat(), 1f, 1f), regionPaint)
                y += 2
            }
            x += 2
        }
    }

    private companion object {
        val Color.Companion.MAGENTA get() = Color.fromRGBA(1f, 0f, 1f)
        val Color.Companion.CYAN get() = Color.fromRGBA(0f, 1f, 1f)
    }
}
