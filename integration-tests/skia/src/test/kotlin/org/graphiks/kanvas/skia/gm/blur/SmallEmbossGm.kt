package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class SmallEmbossGm : SkiaGm {
    override val name = "smallemboss"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 50
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            color = Color.BLACK,
            antiAlias = true,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 3f * 0.57735f + 0.5f),
        )
        val surf = Surface(50, 50)
        surf.canvas { drawRect(Rect.fromXYWH(1f, 1f, 3f, 3f), paint) }

        canvas.scale(30f, 30f)
        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 50f, 50f))
    }
}
