package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests linear-gradient runtime effect with a vertical red-to-blue gradient. */
class LinearGradientRTGm : SkiaGm {
    override val name = "lineargradientrt"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.save()
        for (i in 0 until 16) {
            val t = i / 15f
            canvas.drawRect(Rect(0f, i * 16f, width.toFloat(), (i + 1) * 16f), Paint(color = Color.fromRGBA(t, 0f, 1f - t, 1f)))
        }
        canvas.restore()
    }
}
