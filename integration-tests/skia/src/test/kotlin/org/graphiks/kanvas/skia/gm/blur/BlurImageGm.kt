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
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurimagevmask.cpp::blur_image` (500 x 500).
 * Draws an image with normal-style mask blur both unscaled and scaled.
 * @see https://github.com/google/skia/blob/main/gm/blurimagevmask.cpp
 */
class BlurImageGm : SkiaGm {
    override val name = "blur_image"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 78.4
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val imgW = 128
        val imgH = 128
        val pixels = ByteArray(imgW * imgH * 4)
        for (y in 0 until imgH) {
            for (x in 0 until imgW) {
                val i = (y * imgW + x) * 4
                val cx = x - imgW / 2
                val cy = y - imgH / 2
                val dist = kotlin.math.sqrt((cx * cx + cy * cy).toFloat())
                val r = ((kotlin.math.sin(dist * 0.08f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val g = ((kotlin.math.cos(dist * 0.12f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val b = ((kotlin.math.sin(dist * 0.06f + 2f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                pixels[i] = r.toByte()
                pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte()
                pixels[i + 3] = (-1).toByte()
            }
        }
        val image = Image.fromPixels(imgW, imgH, pixels, ColorType.RGBA_8888, "blurImage")

        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 4f),
        )

        canvas.drawImage(image, Rect.fromXYWH(10f, 10f, imgW.toFloat(), imgH.toFloat()), paint)
        canvas.scale(1.01f, 1.01f)
        canvas.drawImage(image, Rect.fromXYWH(10f + imgW + 10f, 10f, imgW.toFloat(), imgH.toFloat()), paint)
    }
}
