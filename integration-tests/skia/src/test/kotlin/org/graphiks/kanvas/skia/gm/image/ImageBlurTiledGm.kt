package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imageblurtiled.cpp`.
 * Tiles the canvas into cells with a blurred saveLayer for each tile.
 * Text is replaced by black bars since GmCanvas does not support drawString.
 * @see https://github.com/google/skia/blob/main/gm/imageblurtiled.cpp
 */
class ImageBlurTiledGm(
    private val sigmaX: Float = 3f,
    private val sigmaY: Float = 3f,
) : SkiaGm {
    override val name = "imageblurtiled"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val textImage = makeTextImage()
        val paint = Paint(imageFilter = ImageFilter.Blur(sigmaX, sigmaY))
        val tileSize = 128f

        var y = 0f
        while (y < height) {
            var x = 0f
            while (x < width) {
                val tile = Rect(x, y, x + tileSize, y + tileSize)
                canvas.save()
                canvas.clipRect(tile)
                canvas.saveLayer(tile, paint)
                canvas.drawImage(textImage, tile)
                canvas.restore()
                canvas.restore()
                x += tileSize
            }
            y += tileSize
        }
    }

    private fun makeTextImage(): Image {
        val textSurface = Surface(128, 128)
        textSurface.canvas {
            drawRect(Rect(0f, 0f, 128f, 128f), Paint(color = Color.WHITE))
            for (i in 0 until 4) {
                val y = 15f + i * 30f
                drawRect(Rect(10f, y, 118f, y + 22f), Paint(color = Color.BLACK))
            }
        }
        return textSurface.makeImageSnapshot()
    }
}
