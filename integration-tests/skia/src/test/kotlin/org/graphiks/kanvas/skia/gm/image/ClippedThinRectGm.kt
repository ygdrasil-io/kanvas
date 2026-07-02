package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ClippedThinRectGm : SkiaGm {
    override val name = "clipped_thinrect"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val surf = Surface(10, 10)
        val paint = Paint(color = Color.RED, antiAlias = true)
        surf.canvas {
            save()
            clipRect(Rect.fromXYWH(0f, 5f, 256f, 10f), antiAlias = true)
            drawRect(Rect.fromXYWH(0f, 0f, 100f, 5.5f), paint)
            restore()
        }
        val img = surf.makeImageSnapshot()
        val src = Rect(0f, 0f, img.width.toFloat(), img.height.toFloat())
        val dst = Rect.fromXYWH(0f, 10f, 200f, 200f)
        canvas.drawImageRect(img, src, dst)
    }
}
