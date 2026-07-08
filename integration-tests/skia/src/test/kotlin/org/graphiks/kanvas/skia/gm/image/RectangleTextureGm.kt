package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/** STUB: OpenGL rectangle textures are not available in Kanvas. */
class RectangleTextureGm : SkiaGm {
    override val name = "rectangle_texture"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1180
    override val height = 710

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // STUB: OpenGL rectangle textures not available in Kanvas
    }
}
