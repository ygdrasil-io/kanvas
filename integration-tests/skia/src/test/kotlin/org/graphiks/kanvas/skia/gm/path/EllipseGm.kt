package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/** Tests ellipse rendering with horizontal and vertical ovals. */
class EllipseGm : SkiaGm {
    override val name = "ellipse"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.168f, 0.168f, 1f, 1f)
        canvas.drawOval(
            org.graphiks.kanvas.types.Rect.fromXYWH(28f, 28f, 200f, 100f),
            Paint(color = Color.WHITE, antiAlias = true),
        )
        canvas.drawOval(
            org.graphiks.kanvas.types.Rect.fromXYWH(50f, 90f, 60f, 140f),
            Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f), antiAlias = true),
        )
    }
}
