package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/** Tests child sampling in a runtime effect with semi-transparent crossed lines. */
class ChildSamplingRTGm : SkiaGm {
    override val name = "child_sampling_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bg = Paint(color = Color.fromRGBA(0.9f, 0.9f, 0.9f, 1f))
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), bg)
        val p = Paint(color = Color.fromRGBA(1f, 0f, 0f, 0.8f), antiAlias = true)
        canvas.drawLine(10f, 10f, 100f, 100f, p)
        canvas.drawLine(10f, 100f, 100f, 10f, p)
    }
}
