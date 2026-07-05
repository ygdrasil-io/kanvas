package org.graphiks.kanvas.skia.gm.blur

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
 * Port of Skia's `gm/blurs.cpp::DEF_SIMPLE_GM_BG(blurs, ...)`.
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class BlursGm : SkiaGm {
    override val name = "blurs"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 500

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 25f,
    )

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f, 1f)

        data class Rec(val style: BlurStyle?, val cx: Int, val cy: Int)
        val gRecs = listOf(
            Rec(null,               0,  0),
            Rec(BlurStyle.INNER,   -1,  0),
            Rec(BlurStyle.NORMAL,   0,  1),
            Rec(BlurStyle.SOLID,    0, -1),
            Rec(BlurStyle.OUTER,    1,  0),
        )

        val paint = Paint(antiAlias = true, color = Color(0xFF0000FFu))

        canvas.translate(-40f, 0f)

        for (rec in gRecs) {
            val p = if (rec.style != null) {
                paint.copy(maskFilter = MaskFilter.Blur(rec.style, convertRadiusToSigma(20f)))
            } else {
                paint
            }
            canvas.drawCircle(
                (200 + rec.cx * 100).toFloat(),
                (200 + rec.cy * 100).toFloat(),
                50f, p,
            )
        }

        var x = 70f
        var y = 400f
        var textPaint = Paint(
            color = Color.BLACK,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, convertRadiusToSigma(4f)),
        )
        canvas.drawString("Hamburgefons Style", x, y, font, textPaint)
        canvas.drawString("Hamburgefons Style", x, y + 50f, font, textPaint)

        textPaint = Paint(color = Color.WHITE)
        x -= 2f
        y -= 2f
        canvas.drawString("Hamburgefons Style", x, y, font, textPaint)
    }

    private companion object {
        fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
