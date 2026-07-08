package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests color-cube runtime effect with an 8×8 grid of RGB color samples. */
class ColorCubeRTGm : SkiaGm {
    override val name = "colorcubert"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val r = x / 7f; val g = y / 7f; val b = ((x + y) % 8) / 7f
                canvas.drawRect(
                    Rect.fromXYWH(10f + x * 30f, 10f + y * 30f, 28f, 28f),
                    Paint(color = Color.fromRGBA(r, g, b, 1f)),
                )
            }
        }
    }
}
