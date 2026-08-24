package org.graphiks.kanvas.skia.gm.color

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/** Tests a pastel color filter palette: light red, green, blue, and mid-gray quadrants. */
class FilterGm : SkiaGm {
    override val name = "filter"
    override val renderFamily = RenderFamily.COLOR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val w2 = width / 2f
        val h2 = height / 2f
        canvas.drawRect(RectF32(0f, 0f, w2, h2), Paint(color = ColorARGB.fromRGBA(1f, 0.5f, 0.5f, 1f)))
        canvas.drawRect(RectF32(w2, 0f, width.toFloat(), h2), Paint(color = ColorARGB.fromRGBA(0.5f, 1f, 0.5f, 1f)))
        canvas.drawRect(RectF32(0f, h2, w2, height.toFloat()), Paint(color = ColorARGB.fromRGBA(0.5f, 0.5f, 1f, 1f)))
        canvas.drawRect(RectF32(w2, h2, width.toFloat(), height.toFloat()), Paint(color = ColorARGB.fromRGBA(0.75f, 0.75f, 0.75f, 1f)))
    }
}
