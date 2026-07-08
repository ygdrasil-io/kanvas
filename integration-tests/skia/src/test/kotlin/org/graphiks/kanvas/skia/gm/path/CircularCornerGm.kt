package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/** Tests circular-corner rounded rect rendering with various sizes and corner radii. */
class CircularCornerGm : SkiaGm {
    override val name = "circular_corner"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.168f, 0.168f, 1f, 1f)
        val paint = Paint(color = Color.WHITE, antiAlias = true)
        canvas.drawRRect(RRect(Rect(20f, 20f, 200f, 200f), 50f), paint)
        canvas.drawRRect(RRect(Rect(240f, 20f, 440f, 100f), 30f), paint)
        canvas.drawRRect(RRect(Rect(20f, 240f, 300f, 440f), 80f), paint)
        canvas.drawRRect(RRect(Rect(340f, 200f, 480f, 400f), 15f), paint)
    }
}
