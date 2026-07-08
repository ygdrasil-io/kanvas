package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests named color rendering: red, yellow, green, blue quadrants. */
class NameGm : SkiaGm {
    override val name = "name"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w2 = width / 2f
        val h2 = height / 2f
        canvas.drawRect(Rect(0f, 0f, w2, h2), Paint(color = Color.RED))
        canvas.drawRect(Rect(w2, 0f, width.toFloat(), h2), Paint(color = Color.fromRGBA(1f, 1f, 0f, 1f)))
        canvas.drawRect(Rect(0f, h2, w2, height.toFloat()), Paint(color = Color.GREEN))
        canvas.drawRect(Rect(w2, h2, width.toFloat(), height.toFloat()), Paint(color = Color.BLUE))
    }
}
