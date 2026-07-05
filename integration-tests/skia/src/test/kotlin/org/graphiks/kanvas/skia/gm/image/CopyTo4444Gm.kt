package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/copy_to_4444.cpp::CopyTo4444GM`.
 *
 * Decodes `images/dog.jpg` as 8888, draws it at (0, 0),
 * then converts to ARGB_4444 storage and draws the converted
 * image to the right of the original.
 *
 * **Adaptation** — upstream's `ToolUtils::copy_to(&bm4444, kARGB_4444, bm)`
 * is an 8888→4444 colour-type conversion. We perform the conversion by
 * copying pixels through [Bitmap]'s getPixel/setPixel which handles the
 * quantisation (no dither — same as the original port).
 * @see https://github.com/google/skia/blob/main/gm/copy_to_4444.cpp
 */
class CopyTo4444Gm : SkiaGm {
    override val name = "copyTo4444"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 360
    override val height = 180

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/dog.jpg")
            ?: error("Resource not found: images/dog.jpg")
        val image = Image.decode(bytes)

        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()))

        val bm4444 = copyTo4444(image)
        val image4444 = bm4444.toImage()
        canvas.drawImage(
            image4444,
            Rect(
                image.width.toFloat(), 0f,
                (image.width + image4444.width).toFloat(), image4444.height.toFloat(),
            ),
        )
    }

    private fun copyTo4444(src: Image): Bitmap {
        val bm = Bitmap(src.width, src.height, ColorType.ARGB_4444)
        val srcBm = Bitmap.fromImage(src)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                bm.setPixel(x, y, srcBm.getPixel(x, y))
            }
        }
        return bm
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }
}
