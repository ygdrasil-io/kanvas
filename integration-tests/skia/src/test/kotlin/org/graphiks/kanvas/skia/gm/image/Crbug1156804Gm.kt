package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/crbug_1156804.cpp`.
 * Regression test for blur with crop input and transparent border.
 * @see https://github.com/google/skia/blob/main/gm/crbug_1156804.cpp
 */
class Crbug1156804Gm : SkiaGm {
    override val name = "crbug_1156804"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 250
    override val height = 250

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawOne(canvas, Rect.fromXYWH(64f, 64f, 25f, 25f), 1f, 3f, Color.GREEN)
        drawOne(canvas, Rect.fromXYWH(164f, 64f, 25f, 25f), 30f, 3f, Color.GREEN)
        drawOne(canvas, Rect.fromXYWH(64f, 164f, 25f, 25f), 1f, 20f, Color.RED)
        drawOne(canvas, Rect.fromXYWH(164f, 164f, 25f, 25f), 30f, 20f, Color.GREEN)
    }

    private fun drawOne(
        canvas: GmCanvas,
        rect: Rect,
        saveBorder: Float,
        sigma: Float,
        color: Color,
    ) {
        val blurFilter = ImageFilter.Blur(
            sigmaX = sigma, sigmaY = sigma,
            tileMode = TileMode.CLAMP,
        )
        canvas.saveLayer(null, Paint(imageFilter = blurFilter, color = color, antiAlias = true))
        canvas.drawRect(rect, Paint(color = color, antiAlias = true))
        canvas.restore()
    }
}
