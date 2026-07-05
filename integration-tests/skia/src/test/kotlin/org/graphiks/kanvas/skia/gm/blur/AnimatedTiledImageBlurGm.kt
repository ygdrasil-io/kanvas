package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/animatedimageblurs.cpp::AnimatedTiledImageBlur`.
 * Draws an image blurred through four different TileMode values.
 * @see https://github.com/google/skia/blob/main/gm/animatedimageblurs.cpp
 */
class AnimatedTiledImageBlurGm : SkiaGm {
    override val name = "animated-tiled-image-blur"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 530
    override val height = 530

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f)

        val imgW = 512
        val imgH = 512
        val pixels = ByteArray(imgW * imgH * 4)
        for (y in 0 until imgH) {
            for (x in 0 until imgW) {
                val i = (y * imgW + x) * 4
                val cx = x - imgW / 2
                val cy = y - imgH / 2
                val dist = sqrt((cx * cx + cy * cy).toFloat())
                val r = ((sin(dist * 0.06f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val g = ((sin(dist * 0.08f + 2f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                val b = ((sin(dist * 0.10f + 4f) * 0.5f + 0.5f) * 255f).toInt().coerceIn(0, 255)
                pixels[i] = r.toByte()
                pixels[i + 1] = g.toByte()
                pixels[i + 2] = b.toByte()
                pixels[i + 3] = (-1).toByte()
            }
        }
        val image = Image.fromPixels(imgW, imgH, pixels, ColorType.RGBA_8888, "synth")

        val sigma = 0.3f * 250f
        val drawRect = Rect.fromXYWH(0f, 0f, 250f, 250f)

        fun drawBlurredImage(tx: Float, ty: Float, tileMode: TileMode) {
            canvas.save()
            canvas.translate(tx, ty)
            val paint = Paint(imageFilter = ImageFilter.Blur(sigma, sigma, tileMode))
            canvas.drawImageRect(image, drawRect, drawRect, paint)
            canvas.restore()
        }

        drawBlurredImage(10f, 10f, TileMode.DECAL)
        drawBlurredImage(270f, 10f, TileMode.CLAMP)
        drawBlurredImage(10f, 270f, TileMode.REPEAT)
        drawBlurredImage(270f, 270f, TileMode.MIRROR)
    }
}
