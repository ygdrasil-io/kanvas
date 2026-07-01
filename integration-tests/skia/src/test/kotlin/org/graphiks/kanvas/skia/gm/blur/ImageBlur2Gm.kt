package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import kotlin.random.Random

/**
 * Port of Skia's `gm/imageblur2.cpp`.
 * 6×6 grid of saveLayer'd text blocks with varying blur sigmas.
 * @see https://github.com/google/skia/blob/main/gm/imageblur2.cpp
 */
class ImageBlur2Gm : SkiaGm {
    override val name = "imageblur2"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sigmaCount = kBlurSigmas.size
        val dx = IMAGE_BLUR2_WIDTH.toFloat() / sigmaCount.toFloat()
        val dy = IMAGE_BLUR2_HEIGHT.toFloat() / sigmaCount.toFloat()

        for (x in 0 until sigmaCount) {
            val sigmaX = kBlurSigmas[x]
            for (y in 0 until sigmaCount) {
                val sigmaY = kBlurSigmas[y]

                val paint = Paint(
                    imageFilter = ImageFilter.Blur(sigmaX, sigmaY),
                )
                canvas.save()
                val rand = Random(0)
                val raw = rand.nextInt() or 0xFF000000.toInt()
                val rr = ((raw ushr 16) and 0xFF) / 255f
                val gg = ((raw ushr 8) and 0xFF) / 255f
                val bb = (raw and 0xFF) / 255f
                val textColor = Color.fromRGBA(rr, gg, bb, 1f)

                for (i in 0 until 6) {
                    val cellX = x * dx
                    val cellY = y * dy + 12f * i + 12f
                    val fillPaint = Paint(
                        color = textColor,
                        imageFilter = paint.imageFilter,
                    )
                    canvas.drawRect(
                        Rect.fromXYWH(cellX, cellY, dx - 4f, 10f),
                        fillPaint,
                    )
                }
                canvas.restore()
            }
        }
    }

    private companion object {
        const val IMAGE_BLUR2_WIDTH: Int = 500
        const val IMAGE_BLUR2_HEIGHT: Int = 500

        val kBlurSigmas: FloatArray = floatArrayOf(0.0f, 0.3f, 0.5f, 2.0f, 32.0f, 80.0f)
    }
}
