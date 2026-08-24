package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/imagemagnifier.cpp::ImageMagnifierCroppedGM` (256 x 256).
 * A blue-grid image through a magnifier filter cropped to the centre.
 * @see https://github.com/google/skia/blob/main/gm/imagemagnifier.cpp
 */
class ImageMagnifierCroppedGm : SkiaGm {
    override val name = "imagemagnifier_cropped"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()), Paint(color = ColorARGB.Black))

        val source = makeBlueGridImage()
        val wh = width.toFloat()

        val magnifier = ImageFilter.Magnifier(
            src = RectF32.ofOriginSize(0f, 0f, wh, wh),
            zoom = wh / (wh - 96f),
            inset = 64f,
        )

        canvas.saveLayer(null, Paint(imageFilter = magnifier))
        canvas.drawImage(source, RectF32(0f, 0f, wh, wh))
        canvas.restore()
    }

    private fun makeBlueGridImage(): Image {
        val wh = 256f
        val surface = Surface(wh.toInt(), wh.toInt())
        surface.canvas {
            val paint = Paint(color = ColorARGB.Blue)
            var pos = 0f
            while (pos < wh) {
                drawRect(RectF32(0f, pos, wh, pos + 1f), paint)
                drawRect(RectF32(pos, 0f, pos + 1f, wh), paint)
                pos += 16f
            }
        }
        return surface.makeImageSnapshot()
    }
}
