package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imageblurclampmode.cpp`.
 * Tests clamp tile mode on blur image filter at various sigmas.
 * @see https://github.com/google/skia/blob/main/gm/imageblurclampmode.cpp
 */
class ImageBlurClampModeGm : SkiaGm {
    override val name = "imageblurclampmode"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 850
    override val height = 920

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f)

        canvas.translate(0f, 30f)
        val sigmas = floatArrayOf(0.6f, 3.0f, 8.0f, 20.0f)
        for (sigma in sigmas) {
            canvas.save()

            drawContent(canvas, ImageFilter.Blur(sigma, 0.0f, TileMode.CLAMP))
            canvas.translate(IMAGE_WIDTH + 20f, 0f)

            drawContent(canvas, ImageFilter.Blur(0.0f, sigma, TileMode.CLAMP))
            canvas.translate(IMAGE_WIDTH + 20f, 0f)

            drawContent(canvas, ImageFilter.Blur(sigma, sigma, TileMode.CLAMP))
            canvas.translate(IMAGE_WIDTH + 20f, 0f)

            canvas.restore()
            canvas.translate(0f, IMAGE_HEIGHT + 20f)
        }
    }

    private fun drawContent(canvas: GmCanvas, filter: ImageFilter) {
        canvas.save()
        canvas.translate(30f, 0f)

        val bluePaint = Paint(color = Color.BLUE, imageFilter = filter)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat()), bluePaint)

        val greenPaint = Paint(color = Color.GREEN, imageFilter = filter)
        canvas.drawCircle(125f, 100f, 100f, greenPaint)

        val redPaint = Paint(color = Color.RED, imageFilter = filter)
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 80f, 80f), redPaint)

        canvas.restore()
    }

    private companion object {
        const val IMAGE_WIDTH = 250
        const val IMAGE_HEIGHT = 200
    }
}
