package org.graphiks.kanvas.skia.gm.runtime_effect

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests a simple runtime effect with overlapping rectangles and a circle on dark background. */
class SimpleRtGm : SkiaGm {
    override val name = "simplert"
    override val renderFamily = RenderFamily.RUNTIME_EFFECT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.1f, 0.1f, 0.1f, 1f)
        canvas.drawRect(Rect.fromXYWH(20f, 20f, 100f, 100f),
            Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f)))
        canvas.drawRect(Rect.fromXYWH(60f, 60f, 100f, 100f),
            Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.7f)))
        canvas.drawCircle(150f, 150f, 40f,
            Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.8f)))
    }
}
