package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests texture rendering with a 10×10 checkerboard and stroked white rect overlay. */
class TextureGm : SkiaGm {
    override val name = "texture"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.9f, 0.9f, 0.9f, 1f)
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val even = (x + y) % 2 == 0
                val c = if (even) Color.fromRGBA(0.2f, 0.2f, 0.2f, 1f) else Color.fromRGBA(0.8f, 0.8f, 0.8f, 1f)
                canvas.drawRect(Rect.fromXYWH(x * 25f, y * 25f, 25f, 25f), Paint(color = c))
            }
        }
        canvas.drawRect(Rect.fromXYWH(50f, 50f, 100f, 100f),
            Paint(color = Color.WHITE, style = PaintStyle.STROKE, strokeWidth = 4f))
    }
}
