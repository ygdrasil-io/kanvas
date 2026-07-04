package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imageresizetiled.cpp::imageresizetiled` (640 x 480).
 * Tiles the canvas into 100-pixel cells; each cell clips, scales, draws text,
 * then restores creating a visible tiling effect.
 * @see https://github.com/google/skia/blob/main/gm/imageresizetiled.cpp
 */
class ImageResizeTiledGm : SkiaGm {
    override val name = "imageresizetiled"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val font = Font(typeface, size = 100f)
        val tileSize = 100f
        val strs = arrayOf("The quick", "brown fox", "jumped over", "the lazy dog.")

        var y = 0f
        while (y < height.toFloat()) {
            var x = 0f
            while (x < width.toFloat()) {
                canvas.save()
                canvas.clipRect(Rect.fromXYWH(x, y, tileSize, tileSize))
                canvas.scale(1f / RESIZE_FACTOR, 1f / RESIZE_FACTOR)
                canvas.saveLayer(Rect(0f, 0f, width.toFloat() * RESIZE_FACTOR, height.toFloat() * RESIZE_FACTOR))
                var posY = 0f
                for (s in strs) {
                    posY += 100f
                    canvas.drawString(s, 0f, posY, font, Paint())
                }
                canvas.restore()
                canvas.restore()
                x += tileSize
            }
            y += tileSize
        }
    }

    companion object {
        private const val RESIZE_FACTOR = 2f
    }
}
