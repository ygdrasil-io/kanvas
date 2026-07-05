package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/make_raster_image.cpp::makeRasterImage` (128 x 128).
 * Loads `images/color_wheel.png` and draws it, making a raster copy.
 * @see https://github.com/google/skia/blob/main/gm/make_raster_image.cpp
 */
class MakeRasterImageGm : SkiaGm {
    override val name = "makeRasterImage"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = this::class.java.classLoader?.getResourceAsStream("images/color_wheel.png")?.readBytes()
        if (bytes != null) {
            val img = Image.decode(bytes)
            canvas.drawImage(img, Rect(0f, 0f, 128f, 128f))
        }
    }
}
