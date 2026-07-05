package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurpositioning.cpp::check_small_sigma_offset` (200 x 1200).
 * Validates small-sigma blur image filters preserve geometric centering.
 * @see https://github.com/google/skia/blob/main/gm/blurpositioning.cpp
 */
class BlurPositioningGm : SkiaGm {
    override val name = "check_small_sigma_offset"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 91.7
    override val width = 200
    override val height = 1200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sigmas = floatArrayOf(0.0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.6f, 0.8f, 1.0f, 1.2f)
        for (sigma in sigmas) {
            val border = kotlin.math.ceil((sigma * 3f).toDouble()).toInt()

            val r = Rect.fromXYWH(50f, 50f, 100f, 50f)
            val b = Rect.fromLTRB(
                r.left - (border + 1) + 0.5f,
                r.top - (border + 1) + 0.5f,
                r.right + (border + 1) - 0.5f,
                r.bottom + (border + 1) - 0.5f,
            )

            val outlinePaint = Paint(
                color = Color.RED,
                style = PaintStyle.STROKE,
            )
            canvas.drawRect(b, outlinePaint)

            val fillPaint = Paint(
                color = Color.BLACK,
                imageFilter = ImageFilter.Blur(sigma, sigma),
            )
            canvas.drawRect(r, fillPaint)

            canvas.translate(0f, 100f)
        }
    }
}
