package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

internal fun imageBlurDraw(sigmaX: Float, sigmaY: Float, canvas: GmCanvas) {
    val blurPaint = Paint(
        imageFilter = ImageFilter.Blur(sigmaX, sigmaY),
    )
    canvas.save()
    val rand = Random(0)
    repeat(25) {
        val x = rand.nextInt(500)
        val y = rand.nextInt(500)
        val raw = rand.nextInt() or 0xFF000000.toInt()
        val r = ((raw ushr 16) and 0xFF) / 255f
        val g = ((raw ushr 8) and 0xFF) / 255f
        val b = (raw and 0xFF) / 255f
        val color = Color.fromRGBA(r, g, b, 1f)

        val paint = Paint(color = color, imageFilter = blurPaint.imageFilter)
        val rect = Rect.fromXYWH(x.toFloat(), y.toFloat(), 20f, 12f)
        canvas.drawRect(rect, paint)
    }
    canvas.restore()
}

/**
 * Port of Skia's `gm/imageblur.cpp`.
 * 25 strings rendered with image-filter Gaussian blur.
 * @see https://github.com/google/skia/blob/main/gm/imageblur.cpp
 */
class ImageBlurGm : SkiaGm {
    override val name = "imageblur"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 27.9
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)
        imageBlurDraw(24.0f, 0.0f, canvas)
    }
}

class ImageBlurLargeGm : SkiaGm {
    override val name = "imageblur_large"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 27.9
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)
        imageBlurDraw(80.0f, 80.0f, canvas)
    }
}
