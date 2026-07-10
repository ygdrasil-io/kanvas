package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests circle rendering with overlapping white, red, green, and blue circles. */
class CircleGm : SkiaGm {
    override val name = "circle"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.168f, 0.168f, 1f, 1f)
        val cx = width / 2f; val cy = height / 2f
        canvas.drawCircle(cx, cy, 100f, Paint(color = Color.WHITE))
        canvas.drawCircle(cx - 30f, cy - 30f, 40f, Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f)))
        canvas.drawCircle(cx + 30f, cy + 10f, 30f, Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f)))
        canvas.drawCircle(cx + 10f, cy - 40f, 25f, Paint(color = Color.fromRGBA(0f, 0f, 1f, 1f)))
    }
}
