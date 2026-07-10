package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/largeglyphblur.cpp::largeglyphblur` (1920 x 600).
 * Renders large text with a blur mask filter, then opaque on top.
 * @see https://github.com/google/skia/blob/main/gm/largeglyphblur.cpp
 */
class LargeGlyphBlurGm : SkiaGm {
    override val name = "largeglyphblur"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1920
    override val height = 600

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val text = "Hamburgefons"
        val font = Font(typeface, size = 256f)

        val kSigma = convertRadiusToSigma(40f)
        val blurPaint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, kSigma),
        )

        canvas.drawString(text, 10f, 200f, font, blurPaint)
        canvas.drawString(text, 10f, 200f, font, Paint())

        canvas.drawString(text, 10f, 500f, font, blurPaint)
        canvas.drawString(text, 10f, 500f, font, Paint())
    }

    private fun convertRadiusToSigma(radius: Float): Float =
        if (radius > 0f) 0.57735f * radius + 0.5f else 0f
}
