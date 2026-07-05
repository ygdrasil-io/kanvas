package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmapfilters.cpp::TestExtractAlphaGM`
 * (`extractalpha`, 540 x 330).
 * Draws a blue stroked circle as 8888 and then as A8 with a red tint.
 * @see https://github.com/google/skia/blob/main/gm/bitmapfilters.cpp
 */
class TestExtractAlphaGm : SkiaGm {
    override val name = "extractalpha"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 540
    override val height = 330

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val blueCircle = makeBlueCircleImage()
        canvas.drawImage(blueCircle, Rect.fromXYWH(10f, 10f, 100f, 100f))

        val alphaImage = makeAlphaImage()
        val redPaint = Paint(color = Color.RED)
        canvas.drawImage(alphaImage, Rect.fromXYWH(120f, 10f, 100f, 100f), redPaint)
    }

    private fun makeBlueCircleImage(): Image {
        val pixels = ByteArray(100 * 100 * 4)
        val cx = 50f; val cy = 50f; val r = 39f
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val dx = x - cx; val dy = y - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val i = (y * 100 + x) * 4
                if (dist >= r - 10f && dist <= r + 10f) {
                    pixels[i] = 0xFF.toByte(); pixels[i + 1] = 0x00.toByte()
                    pixels[i + 2] = 0x00.toByte(); pixels[i + 3] = 0xFF.toByte()
                } else {
                    pixels[i] = 0x00.toByte(); pixels[i + 1] = 0x00.toByte()
                    pixels[i + 2] = 0x00.toByte(); pixels[i + 3] = 0x00.toByte()
                }
            }
        }
        return Image.fromPixels(100, 100, pixels, sourceId = "blue_circle")
    }

    private fun makeAlphaImage(): Image {
        val pixels = ByteArray(100 * 100)
        val cx = 50f; val cy = 50f; val r = 39f
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val dx = x - cx; val dy = y - cy
                val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                val i = y * 100 + x
                pixels[i] = if (dist >= r - 10f && dist <= r + 10f) 0xFF.toByte() else 0x00.toByte()
            }
        }
        return Image.fromPixels(100, 100, pixels, sourceId = "alpha_circle")
    }
}
