package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Tests picture image rendering with overlapping red rect, blue circle, and green rect. */
class PictureGm : SkiaGm {
    override val name = "picture"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.drawRect(Rect.fromXYWH(20f, 20f, 100f, 100f),
            Paint(color = Color.RED, antiAlias = true))
        canvas.drawCircle(120f, 80f, 40f,
            Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.7f), antiAlias = true))
        canvas.drawRect(Rect.fromXYWH(60f, 60f, 100f, 100f),
            Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.5f), antiAlias = true))
    }
}
