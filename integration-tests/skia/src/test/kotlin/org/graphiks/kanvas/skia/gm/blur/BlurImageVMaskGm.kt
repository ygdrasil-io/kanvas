package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.ImageFilter
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
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurimagevmask.cpp::DEF_SIMPLE_GM(blurimagevmask, ...)`.
 * @see https://github.com/google/skia/blob/main/gm/blurimagevmask.cpp
 */
class BlurImageVMaskGm : SkiaGm {
    override val name = "blurimagevmask"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 1200

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 25f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val paint = Paint(antiAlias = true, color = Color.BLACK)

        canvas.drawString("mask blur", 285f, 50f, font, paint)
        canvas.drawString("image blur", 285f + 250f, 50f, font, paint)

        val sigmas = floatArrayOf(3f, 8f, 16f, 24f, 32f)
        val r = Rect.fromLTRB(35f, 100f, 135f, 200f)

        for (sigma in sigmas) {
            canvas.drawRect(r, paint)

            val out = "Sigma: ${if (sigma == sigma.toInt().toFloat()) sigma.toInt().toString() else sigma.toString()}"
            canvas.drawString(out, r.left, r.bottom + 35f, font, paint)

            var r2 = r.copy(left = r.left + 250f, right = r.right + 250f)
            val maskBlurPaint = Paint(
                color = Color.BLACK,
                antiAlias = true,
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
            )
            canvas.drawRect(r2, maskBlurPaint)

            r2 = Rect.fromLTRB(r2.left + 250f, r2.top, r2.right + 250f, r2.bottom)
            canvas.saveLayer(
                bounds = null,
                paint = Paint(imageFilter = ImageFilter.Blur(sigma, sigma)),
            )
            canvas.drawRect(r2, paint)
            canvas.restore()

            // r.offset(-500, 200) for each row
            val newLeft = r.left - 500f
            val newTop = r.top + 200f
            r.left = newLeft
            r.right = newLeft + 100f
            r.top = newTop
            r.bottom = newTop + 100f
        }
    }
}
