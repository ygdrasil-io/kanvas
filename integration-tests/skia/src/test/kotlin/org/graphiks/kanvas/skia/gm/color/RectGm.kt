package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests rectangular color rendering with a stepped diagonal of five colored squares. */
class RectGm : SkiaGm {
    override val name = "rect"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val inset = 10f
        val steps = 5
        val stepW = (width - 2 * inset) / steps
        val stepH = (height - 2 * inset) / steps
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(1f, 1f, 0f, 1f), Color.fromRGBA(1f, 0f, 1f, 1f))
        for (i in 0 until steps) {
            canvas.drawRect(
                Rect(inset + i * stepW, inset + i * stepH, inset + (i + 1) * stepW, inset + (i + 1) * stepH),
                Paint(color = colors[i]),
            )
        }
    }
}
