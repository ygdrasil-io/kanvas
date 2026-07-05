package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class NewSurfaceGm : SkiaGm {
    override val name = "surfacenew"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 140

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(100, 100)
        surf.canvas { drawColor(Color.RED) }
        val image = surf.makeImageSnapshot()
        canvas.drawImage(image, Rect.fromXYWH(10f, 10f, 100f, 100f))

        val surf2 = Surface(100, 100)
        surf2.canvas { drawColor(Color.RED) }
        val image2 = surf2.makeImageSnapshot()
        canvas.drawImage(image2, Rect.fromXYWH(120f, 10f, 100f, 100f))
    }
}
