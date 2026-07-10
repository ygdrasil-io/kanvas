package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmappremul.cpp` (bitmap_premul).
 * Checks that unpremultiplied bitmap pixels render correctly.
 * @see https://github.com/google/skia/blob/main/gm/bitmappremul.cpp
 */
class BitmapPremulGm : SkiaGm {
    override val name = "bitmap_premul"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = SLIDE_SIZE * 2
    override val height = SLIDE_SIZE * 2

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val slide = SLIDE_SIZE.toFloat()
        canvas.drawImage(makeArgb8888Gradient(), Rect.fromXYWH(0f, 0f, slide, slide))
        canvas.drawImage(makeArgb8888Gradient(), Rect.fromXYWH(slide, 0f, slide, slide))
        canvas.drawImage(makeArgb8888Stripes(), Rect.fromXYWH(0f, slide, slide, slide))
        canvas.drawImage(makeArgb8888Stripes(), Rect.fromXYWH(slide, slide, slide, slide))
    }

    private companion object {
        const val SLIDE_SIZE: Int = 256

        private fun makeArgb8888Gradient(): Image {
            val pixels = ByteArray(SLIDE_SIZE * SLIDE_SIZE * 4)
            for (y in 0 until SLIDE_SIZE) {
                for (x in 0 until SLIDE_SIZE) {
                    val alpha = y
                    val rgb = if (alpha == 0) 0 else 0xFF
                    val offset = (y * SLIDE_SIZE + x) * 4
                    pixels[offset] = rgb.toByte()
                    pixels[offset + 1] = rgb.toByte()
                    pixels[offset + 2] = rgb.toByte()
                    pixels[offset + 3] = alpha.toByte()
                }
            }
            return Image.fromPixels(SLIDE_SIZE, SLIDE_SIZE, pixels)
        }

        private fun makeArgb8888Stripes(): Image {
            val pixels = ByteArray(SLIDE_SIZE * SLIDE_SIZE * 4)
            var rowColor = 0
            for (y in 0 until SLIDE_SIZE) {
                for (x in 0 until SLIDE_SIZE) {
                    val offset = (y * SLIDE_SIZE + x) * 4
                    if (rowColor == 0) {
                        pixels[offset] = 0; pixels[offset + 1] = 0
                        pixels[offset + 2] = 0; pixels[offset + 3] = 0
                    } else {
                        pixels[offset] = (-1).toByte(); pixels[offset + 1] = (-1).toByte()
                        pixels[offset + 2] = (-1).toByte(); pixels[offset + 3] = (-1).toByte()
                    }
                }
                rowColor = if (rowColor == 0) 255 else 0
            }
            return Image.fromPixels(SLIDE_SIZE, SLIDE_SIZE, pixels)
        }
    }
}
