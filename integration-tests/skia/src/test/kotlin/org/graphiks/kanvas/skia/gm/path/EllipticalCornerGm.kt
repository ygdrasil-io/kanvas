package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Tests elliptical-corner rounded rect rendering with asymmetric corner radii. */
class EllipticalCornerGm : SkiaGm {
    override val name = "elliptical_corner"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.168f, 0.168f, 1f, 1f)
        val paint = Paint(color = Color.WHITE, antiAlias = true)
        canvas.drawRRect(RRect(Rect(20f, 20f, 300f, 200f), CornerRadii(30f, 60f), CornerRadii(30f, 60f), CornerRadii(30f, 60f), CornerRadii(30f, 60f)), paint)
        canvas.drawRRect(RRect(Rect(20f, 240f, 250f, 450f), CornerRadii(80f, 30f), CornerRadii(80f, 30f), CornerRadii(80f, 30f), CornerRadii(80f, 30f)), paint)
        canvas.drawRRect(RRect(Rect(280f, 260f, 480f, 480f), CornerRadii(60f, 40f), CornerRadii(60f, 40f), CornerRadii(60f, 40f), CornerRadii(60f, 40f)), paint)
    }
}
