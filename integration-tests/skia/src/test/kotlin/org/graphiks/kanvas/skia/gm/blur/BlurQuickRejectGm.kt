package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurquickreject.cpp::BlurQuickRejectGM` (300 × 300).
 * Stresses the rasteriser's interaction between a clipRect and a
 * blurred draw whose bbox extends outside the clip.
 * @see https://github.com/google/skia/blob/main/gm/blurquickreject.cpp
 */
class BlurQuickRejectGm : SkiaGm {
    override val name = "blurquickreject"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 70.5
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kBoxSize = 100f
        val kBlurRadius = 30f
        val sigma = 0.57735f * kBlurRadius + 0.5f

        val clipRect = Rect(0f, 0f, kBoxSize, kBoxSize)

        val blurRects = arrayOf(
            Rect(-kBlurRadius - 1f, 0f, -1f, kBoxSize),
            Rect(0f, -kBlurRadius - 1f, kBoxSize, -1f),
            Rect(kBoxSize + 1f, 0f, kBoxSize + kBlurRadius + 1f, kBoxSize),
            Rect(0f, kBoxSize + kBlurRadius + 1f, kBoxSize, 2 * kBoxSize + kBlurRadius + 1f),
        )
        val colors = arrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.fromRGBA(1f, 1f, 0f, 1f))

        val hairlinePaint = Paint(
            style = PaintStyle.STROKE,
            color = Color.WHITE,
            strokeWidth = 0f,
        )

        canvas.drawColor(0f, 0f, 0f)
        canvas.save()
        canvas.translate(kBoxSize, kBoxSize)
        canvas.drawRect(clipRect, hairlinePaint)
        canvas.clipRect(clipRect)
        for (i in blurRects.indices) {
            val blurPaint = Paint(
                color = colors[i],
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, sigma),
            )
            canvas.drawRect(blurRects[i], blurPaint)
            canvas.drawRect(blurRects[i], hairlinePaint)
        }
        canvas.restore()
    }
}
