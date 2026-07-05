package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/blurcircles2.cpp::BlurCircles2GM` (730 × 1350).
 * Draws an array of circles with different radii and different blur
 * radii in a 5×5 grid. Below each circle an almost-circle path (an
 * arc spanning 355° then closed) is drawn with the same blur filter.
 * @see https://github.com/google/skia/blob/main/gm/blurcircles2.cpp
 */
class BlurCircles2Gm : SkiaGm {
    override val name = "blurcircles2"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 49.2
    override val width = 730
    override val height = 1350

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kPad = 5f
        val kRadiusSteps = 5
        val kBlurRadiusSteps = 5
        val kMinRadius = 15f
        val kMaxRadius = 45f
        val kMinBlurRadius = 5f
        val kMaxBlurRadius = 45f

        val kDeltaRadius = (kMaxRadius - kMinRadius) / kRadiusSteps
        val kDeltaBlurRadius = (kMaxBlurRadius - kMinBlurRadius) / kBlurRadiusSteps

        fun almostCircleMaker(radius: Float): Path {
            val r = radius.toDouble()
            val sweep = 355.0
            val sweepRad = sweep * PI / 180.0
            val endX = r * cos(sweepRad)
            val endY = r * sin(sweepRad)
            val largeArc = sweep > 180.0
            return Path {
                moveTo(r.toFloat(), 0f)
                arcTo(radius, radius, 0f, largeArc, true, endX.toFloat(), endY.toFloat())
                close()
            }
        }

        fun blurMaker(radius: Float) =
            MaskFilter.Blur(BlurStyle.NORMAL, convertRadiusToSigma(radius))

        canvas.save()
        canvas.translate(kPad + kMinRadius + kMaxBlurRadius, kPad + kMinRadius + kMaxBlurRadius)

        var lineWidth = 0f
        for (r in 0 until kRadiusSteps - 1) {
            val radius = r * kDeltaRadius + kMinRadius
            lineWidth += 2 * (radius + kMaxBlurRadius) + kPad
        }

        for (br in 0 until kBlurRadiusSteps) {
            val blurRadius = br * kDeltaBlurRadius + kMinBlurRadius
            val maxRowR = blurRadius + kMaxRadius
            val rowFilter = blurMaker(blurRadius)
            val rowPaint = Paint(color = Color.BLACK, maskFilter = rowFilter)

            canvas.save()
            for (r in 0 until kRadiusSteps) {
                val radius = r * kDeltaRadius + kMinRadius
                val almostCircle = almostCircleMaker(radius)

                canvas.save()
                canvas.drawCircle(0f, 0f, radius, rowPaint)
                canvas.translate(0f, 2 * maxRowR + kPad)
                canvas.drawPath(almostCircle, rowPaint)
                canvas.restore()

                val maxColR = radius + kMaxBlurRadius
                canvas.translate(maxColR * 2 + kPad, 0f)
            }
            canvas.restore()

            if (br != kBlurRadiusSteps - 1) {
                val blackPaint = Paint(color = Color.BLACK)
                val lineY = 3 * maxRowR + 1.5f * kPad
                canvas.drawLine(0f, lineY, lineWidth, lineY, blackPaint)
            }
            canvas.translate(0f, maxRowR * 4 + 2 * kPad)
        }
        canvas.restore()
    }

    private companion object {
        fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
