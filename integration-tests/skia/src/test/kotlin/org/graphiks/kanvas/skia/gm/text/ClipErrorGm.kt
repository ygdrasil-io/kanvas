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
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clip_error.cpp::ClipErrorGM`.
 * @see https://github.com/google/skia/blob/main/gm/clip_error.cpp
 */
class ClipErrorGm : SkiaGm {
    override val name = "cliperror"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val font = Font(
            typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
            size = 256f,
        )

        val kSigma = convertRadiusToSigma(50f)
        val blurPaint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, kSigma),
        )
        val paint = Paint()
        val clearPaint = Paint(color = Color.WHITE)

        canvas.save()
        canvas.translate(0f, 0f)
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 800f, 256f))
        drawTextSlot(canvas, font, paint, blurPaint, clearPaint)
        canvas.restore()

        canvas.save()
        canvas.translate(0f, 256f)
        canvas.clipRect(Rect.fromLTRB(0f, 256f, 800f, 510f))
        drawTextSlot(canvas, font, paint, blurPaint, clearPaint)
        canvas.restore()
    }

    private fun drawTextSlot(
        canvas: GmCanvas,
        font: Font,
        paint: Paint,
        blurPaint: Paint,
        clearPaint: Paint,
    ) {
        canvas.save()
        canvas.clipRect(Rect.fromLTRB(0f, 0f, 1081f, 665f))
        canvas.drawRect(Rect.fromLTRB(0f, 0f, 1081f, 665f), clearPaint)
        canvas.drawString("hambur", 0f, 256f, font, blurPaint)
        canvas.drawString("hambur", 0f, 477f, font, paint)
        canvas.restore()
    }

    private companion object {
        fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
