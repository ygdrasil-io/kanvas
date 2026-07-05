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
 * Port of Skia's `gm/imagemagnifier.cpp::ImageMagnifierCroppedGM` (256 x 256).
 * A blue-grid image through a magnifier filter cropped to the centre.
 * @see https://github.com/google/skia/blob/main/gm/imagemagnifier.cpp
 */
class ImageMagnifierCroppedGm : SkiaGm {
    override val name = "imagemagnifier_cropped"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = Color.BLACK))

        val source = makeBlueGridImage()
        val wh = width.toFloat()

        val magnifier = ImageFilter.Magnifier(
            src = Rect.fromXYWH(0f, 0f, wh, wh),
            zoom = wh / (wh - 96f),
            inset = 64f,
        )

        canvas.saveLayer(null, Paint(imageFilter = magnifier))
        canvas.drawImage(source, Rect(0f, 0f, wh, wh))
        canvas.restore()
    }

    private fun makeBlueGridImage(): Image {
        val wh = 256f
        val surface = Surface(wh.toInt(), wh.toInt())
        surface.canvas {
            val paint = Paint(color = Color.BLUE)
            var pos = 0f
            while (pos < wh) {
                drawRect(Rect(0f, pos, wh, pos + 1f), paint)
                drawRect(Rect(pos, 0f, pos + 1f, wh), paint)
                pos += 16f
            }
        }
        return surface.makeImageSnapshot()
    }
}
