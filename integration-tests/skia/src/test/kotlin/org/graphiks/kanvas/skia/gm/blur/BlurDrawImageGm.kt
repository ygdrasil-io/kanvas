package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurs.cpp::BlurDrawImage` (256 x 256).
 * Draws a test image with normal-style mask blur of sigma=10,
 * under a 0.25x scale.
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class BlurDrawImageGm : SkiaGm {
    override val name = "BlurDrawImage"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0x88 / 255f, 0xFF / 255f, 0x88 / 255f, 1f)

        val imgW = 128
        val imgH = 128
        val pixels = ByteArray(imgW * imgH * 4)
        for (y in 0 until imgH) {
            for (x in 0 until imgW) {
                val i = (y * imgW + x) * 4
                val cx = x - imgW / 2
                val cy = y - imgH / 2
                val dist = kotlin.math.sqrt((cx * cx + cy * cy).toFloat())
                val r = ((kotlin.math.sin(dist * 0.1f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val g = ((kotlin.math.cos(dist * 0.1f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val b = ((kotlin.math.sin(dist * 0.05f + 1f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                pixels[i] = r.toByte()
                pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte()
                pixels[i + 3] = (-1).toByte()
            }
        }
        val image = Image.fromPixels(imgW, imgH, pixels, ColorType.RGBA_8888, "drawImage")

        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 10f),
        )

        canvas.scale(0.25f, 0.25f)
        canvas.drawImage(image, Rect.fromXYWH(256f, 256f, imgW.toFloat(), imgH.toFloat()), paint)
    }
}
