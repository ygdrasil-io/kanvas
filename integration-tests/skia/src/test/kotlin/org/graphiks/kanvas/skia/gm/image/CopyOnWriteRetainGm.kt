package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class CopyOnWriteRetainGm : SkiaGm {
    override val name = "copy_on_write_retain"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(256, 256)
        surf.canvas { clear(Color.RED) }
        val image = surf.makeImageSnapshot()
        surf.canvas {
            clipRect(Rect.fromLTRB(0f, 0f, 128f, 256f))
            clear(Color.BLUE)
        }
        canvas.drawImage(surf.makeImageSnapshot(), Rect(0f, 0f, 256f, 256f))
    }
}
