package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefiltersunpremul.cpp`.
 * Renders a 64×64 unpremultiplied red-50/255 bitmap onto a black background.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersunpremul.cpp
 */
class ImageFiltersUnpremulGm : SkiaGm {
    override val name = "imagefiltersunpremul"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 64
    override val height = 64

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val pixels = ByteArray(64 * 64 * 4)
        for (i in 0 until 64 * 64) {
            pixels[i * 4] = (-1).toByte()
            pixels[i * 4 + 3] = 50.toByte()
        }
        val image = Image.fromPixels(64, 64, pixels, ColorType.RGBA_8888, "unpremul")
        canvas.drawImage(image, Rect(0f, 0f, 64f, 64f))
    }
}
