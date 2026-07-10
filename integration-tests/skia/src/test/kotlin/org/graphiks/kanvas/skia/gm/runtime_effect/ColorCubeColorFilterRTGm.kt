package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests color-cube color-filter runtime effect with an 8×8 RGB color grid. */
class ColorCubeColorFilterRTGm : SkiaGm {
    override val name = "colorcubecolorfilterrt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val r = x / 7f
                val g = y / 7f
                val b = ((x + y) % 8) / 7f
                canvas.drawRect(Rect(x * 32f, y * 32f, (x + 1) * 32f, (y + 1) * 32f), Paint(color = Color.fromRGBA(r, g, b, 1f)))
            }
        }
    }
}
