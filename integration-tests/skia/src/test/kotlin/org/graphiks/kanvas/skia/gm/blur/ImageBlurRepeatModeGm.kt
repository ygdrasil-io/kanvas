package org.graphiks.kanvas.skia.gm.blur

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
 * Port of Skia's `gm/imageblurrepeatmode.cpp`.
 * Tests repeat tile mode on Gaussian blur at varying sigmas.
 * @see https://github.com/google/skia/blob/main/gm/imageblurrepeatmode.cpp
 */
class ImageBlurRepeatModeGm : SkiaGm {
    override val name = "imageblurrepeatmode"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 850
    override val height = 920

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f)

        canvas.translate(0f, 30f)
        for (sigma in SIGMAS) {
            canvas.save()

            drawContent(canvas, ImageFilter.Blur(sigma, 0f, TileMode.REPEAT), direction = 1)
            canvas.translate(IMAGE_WIDTH + 20f, 0f)

            drawContent(canvas, ImageFilter.Blur(0f, sigma, TileMode.REPEAT), direction = 2)
            canvas.translate(IMAGE_WIDTH + 20f, 0f)

            drawContent(canvas, ImageFilter.Blur(sigma, sigma, TileMode.REPEAT), direction = 3)
            canvas.translate(IMAGE_WIDTH + 20f, 0f)

            canvas.restore()
            canvas.translate(0f, IMAGE_HEIGHT + 20f)
        }
    }

    private fun drawContent(canvas: GmCanvas, filter: ImageFilter, direction: Int) {
        canvas.save()
        canvas.translate(30f, 0f)

        val colors = listOf(Color.RED, Color.BLUE, Color.GREEN, Color.fromRGBA(1f, 1f, 0f, 1f), Color.BLACK)
        val bandWidth = 25f
        val xDir = (direction and 0x1) == 1
        val yDir = (direction and 0x2) == 2

        if (xDir) {
            var x = 0f
            while (x < IMAGE_WIDTH) {
                val color = colors[(x.toInt() / bandWidth.toInt()) % 5]
                val alpha = if (yDir) 0.5f else 1f
                val paint = Paint(
                    color = color,
                    imageFilter = filter,
                )
                canvas.drawRect(Rect.fromXYWH(x, 0f, bandWidth, IMAGE_HEIGHT.toFloat()), paint)
                x += bandWidth
            }
        }
        if (yDir) {
            var y = 0f
            while (y < IMAGE_HEIGHT) {
                val color = colors[(y.toInt() / bandWidth.toInt()) % 5]
                val alpha = if (xDir) 0.5f else 1f
                val paint = Paint(
                    color = color,
                    imageFilter = filter,
                )
                canvas.drawRect(Rect.fromXYWH(0f, y, IMAGE_WIDTH.toFloat(), bandWidth), paint)
                y += bandWidth
            }
        }

        canvas.restore()
    }

    private companion object {
        const val IMAGE_WIDTH = 250
        const val IMAGE_HEIGHT = 200

        private val SIGMAS: FloatArray = floatArrayOf(0.6f, 3.0f, 8.0f, 20.0f)
    }
}
