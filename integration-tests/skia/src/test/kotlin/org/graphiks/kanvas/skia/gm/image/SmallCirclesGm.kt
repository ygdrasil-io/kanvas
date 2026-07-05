package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect

class SmallCirclesGm : SkiaGm {
    override val name = "smallcircles"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 425
    override val height = 425

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(100, 100)
        val paint = Paint(antiAlias = true)
        surf.canvas {
            translate(5f, 5f)
            for (i in 0 until 11) {
                save()
                for (j in 0 until 11) {
                    val circle = Path { }.apply { addCircle(0f, 0f, 0.8f) }
                    drawPath(circle, paint)
                    translate(5.1f, 0f)
                }
                restore()
                translate(0f, 5.1f)
            }
        }
        canvas.scale(7f, 7f)
        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 100f, 100f))
    }
}
