package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/srgb.cpp`.
 * Exercises LinearToSRGB / SRGBToLinear color filters and Compose, using a generated image.
 * @see https://github.com/google/skia/blob/main/gm/srgb.cpp
 */
class SrgbGm : SkiaGm {
    override val name = "srgb_colorfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 256 * 3

    private val sourceImage: Image = run {
        val w = 128; val h = 128
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                pixels[i] = ((x * 255 / (w - 1)) and 0xFF).toByte()
                pixels[i + 1] = ((y * 255 / (h - 1)) and 0xFF).toByte()
                pixels[i + 2] = (((x + y) * 255 / (w + h - 2)) and 0xFF).toByte()
                pixels[i + 3] = 0xFF.toByte()
            }
        }
        Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "srgb-mandrill")
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val img = sourceImage
        val imgRect = Rect(0f, 0f, 128f, 128f)

        val array = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 1f, 0f,
        )
        val cf0 = ColorFilter.Matrix(array)
        val cf1 = ColorFilter.LinearToSRGB
        val cf2 = ColorFilter.SRGBToLinear

        // Row 0 — reference + negate-red matrix
        canvas.drawImage(img, Rect(0f, 0f, 128f, 128f))
        canvas.drawImage(img, Rect(256f, 0f, 384f, 128f), Paint(colorFilter = cf0))

        // Row 1 — LinearToSRGB alone, then composed with matrix
        canvas.drawImage(img, Rect(0f, 128f, 128f, 256f), Paint(colorFilter = cf1))
        canvas.drawImage(img, Rect(256f, 128f, 384f, 256f), Paint(colorFilter = ColorFilter.Compose(cf1, cf0)))

        // Row 2 — SRGBToLinear alone, then composed with matrix
        canvas.drawImage(img, Rect(0f, 256f, 128f, 384f), Paint(colorFilter = cf2))
        canvas.drawImage(img, Rect(256f, 256f, 384f, 384f), Paint(colorFilter = ColorFilter.Compose(cf2, cf0)))
    }
}
