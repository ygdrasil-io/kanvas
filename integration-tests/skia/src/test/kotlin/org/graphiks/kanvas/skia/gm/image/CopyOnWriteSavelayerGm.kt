package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class CopyOnWriteSavelayerGm : SkiaGm {
    override val name = "copy_on_write_savelayer"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(256, 256)
        surf.canvas { clear(Color.RED) }
        val image = surf.makeImageSnapshot()
        val layerPaint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 0.25f))
        surf.canvas {
            saveLayer(Rect(0f, 0f, 256f, 256f), layerPaint)
            clear(Color.BLUE)
            restore()
        }
        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 256f, 256f))
    }
}
