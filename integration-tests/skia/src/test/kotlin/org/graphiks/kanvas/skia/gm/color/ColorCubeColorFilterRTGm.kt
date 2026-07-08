package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests color-cube color-filter rendering with a rainbow gradient of vertical bars. */
class ColorCubeColorFilterRTGm : SkiaGm {
    override val name = "colorcubecolorfilterrt"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.2f, 0.2f, 0.2f, 1f)
        for (i in 0 until 6) {
            val hue = i * 60f
            val r = (kotlin.math.cos(hue * kotlin.math.PI.toFloat() / 180f) * 0.5f + 0.5f)
            val g = (kotlin.math.cos((hue + 120f) * kotlin.math.PI.toFloat() / 180f) * 0.5f + 0.5f)
            val b = (kotlin.math.cos((hue + 240f) * kotlin.math.PI.toFloat() / 180f) * 0.5f + 0.5f)
            canvas.drawRect(
                Rect.fromXYWH(20f + i * 38f, 20f, 34f, 200f),
                Paint(color = Color.fromRGBA(r, g, b, 1f)),
            )
        }
    }
}
