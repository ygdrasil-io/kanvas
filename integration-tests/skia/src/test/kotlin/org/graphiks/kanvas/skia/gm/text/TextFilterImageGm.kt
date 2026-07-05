package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces

/**
 * Port of Skia's `gm/imagefiltersbase.cpp::ImageFiltersText_IF`
 * (registered as `textfilter_image`).
 * Draws "Hamburgefon" in three edging modes with and without a
 * mild blur image filter, with and without saveLayer.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersbase.cpp
 */
class TextFilterImageGm : SkiaGm {
    override val name = "textfilter_image"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 65.0
    override val width = 512
    override val height = 342

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(20f, 40f)

        for (doSaveLayer in 0..1) {
            canvas.save()
            for (useFilter in 0..1) {
                canvas.save()
                val blurPaint = Paint(
                    imageFilter = ImageFilter.Blur(sigmaX = 1.5f, sigmaY = 1.5f),
                )
                if (useFilter == 1) {
                    if (doSaveLayer == 1) {
                        canvas.saveLayer(paint = blurPaint)
                        drawWaterfall(canvas, Paint())
                    } else {
                        drawWaterfall(canvas, blurPaint)
                    }
                } else {
                    drawWaterfall(canvas, Paint())
                }
                canvas.restore()
                canvas.translate(250f, 0f)
            }
            canvas.restore()
            canvas.translate(0f, 200f)
        }
    }

    private fun drawWaterfall(canvas: GmCanvas, paint: Paint) {
        val edgingModes = arrayOf(
            false to false,
            true to false,
            true to true,
        )
        canvas.save()
        for ((aa, sp) in edgingModes) {
            val font = Font(typeface, size = 30f, antiAlias = aa, subpixel = sp)
            canvas.drawString("Hamburgefon", 0f, 0f, font, paint)
            canvas.translate(0f, 40f)
        }
        canvas.restore()
    }
}
