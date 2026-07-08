package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.canvas.drawCircle
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/tiledscaledbitmap.cpp`.
 *  Tests tiled scaled bitmap rendering — creates a gradient image and
 *  renders it as a scaled tiled shader with linear filter.
 *  @see https://github.com/google/skia/blob/main/gm/tiledscaledbitmap.cpp
 */
class TiledScaledBitmapGm : SkiaGm {
    override val name = "tiledscaledbitmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1016
    override val height = 616

    private val fBitmap: Image by lazy { makeBm(360, 288) }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)

        val mat = Matrix33.scale(121f / 360f, 93f / 288f) * Matrix33.translate(-72f, -72f)
        val shader = Shader.WithLocalMatrix(
            Shader.Image(fBitmap, TileMode.REPEAT, TileMode.REPEAT), mat,
        )

        canvas.drawRect(Rect(8f, 8f, 1008f, 608f), paint.copy(shader = shader))
    }

    private fun makeBm(width: Int, height: Int): Image {
        val surf = Surface(width, height)
        surf.canvas {
            val paint = Paint(antiAlias = true)
            drawCircle(width / 2f, height / 2f, width / 4f, paint)
        }
        return surf.makeImageSnapshot()
    }
}
