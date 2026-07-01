package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class DistantClipGm : SkiaGm {
    override val name = "distantclip"
    override val renderFamily = RenderFamily.CLIP
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kExtents = 1000f
        canvas.drawColor(1f, 0f, 0f, 1f)
        canvas.save()
        val r = Rect(-kExtents, kExtents - kExtents, kExtents, kExtents + kExtents)
        canvas.clipRect(r)
        canvas.drawColor(0f, 1f, 0f, 1f)
        canvas.restore()
    }
}
