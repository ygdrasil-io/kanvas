package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/blurcircles2.cpp::BlurCircles2GM` (730 × 1350).
 *
 * Draws an array of circles with different radii (5 columns × 5 rows of
 * blur radius) and different blur radii. Below each circle an
 * almost-circle path (an arc spanning 355° then closed) is drawn with
 * the same blur filter for comparison. Horizontal separator lines mark
 * the rows.
 *
 * `kMinRadius=15`, `kMaxRadius=45`, `kMinBlurRadius=5`, `kMaxBlurRadius=45`,
 * `kRadiusSteps=kBlurRadiusSteps=5`. `sigma = SkBlurMask::ConvertRadiusToSigma`.
 *
 * Bench/Sample modes are GM-irrelevant — we render the GM (non-bench)
 * variant.
 */
public class BlurCircles2GM : GM() {

    override fun getName(): String = "blurcircles2"
    override fun getISize(): SkISize = SkISize.Make(730, 1350)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kPad = 5f
        val kRadiusSteps = 5
        val kBlurRadiusSteps = 5
        val kMinRadius = 15f
        val kMaxRadius = 45f
        val kMinBlurRadius = 5f
        val kMaxBlurRadius = 45f

        val kDeltaRadius = (kMaxRadius - kMinRadius) / kRadiusSteps
        val kDeltaBlurRadius = (kMaxBlurRadius - kMinBlurRadius) / kBlurRadiusSteps

        fun almostCircleMaker(radius: Float) =
            SkPathBuilder()
                .addArc(
                    SkRect.MakeXYWH(-radius, -radius, 2 * radius, 2 * radius),
                    0f, 355f,
                )
                .close()
                .detach()

        fun blurMaker(radius: Float) =
            SkBlurMaskFilter.Make(SkBlurStyle.kNormal, convertRadiusToSigma(radius))

        c.withSave {
            translate(kPad + kMinRadius + kMaxBlurRadius, kPad + kMinRadius + kMaxBlurRadius)

            // Pre-compute the row's total width — used to draw the
            // horizontal separator line that mirrors upstream.
            var lineWidth = 0f
            for (r in 0 until kRadiusSteps - 1) {
                val radius = r * kDeltaRadius + kMinRadius
                lineWidth += 2 * (radius + kMaxBlurRadius) + kPad
            }

            for (br in 0 until kBlurRadiusSteps) {
                val blurRadius = br * kDeltaBlurRadius + kMinBlurRadius
                val maxRowR = blurRadius + kMaxRadius
                val rowFilter = blurMaker(blurRadius)
                val rowPaint = SkPaint().apply {
                    color = SK_ColorBLACK
                    maskFilter = rowFilter
                }

                save()
                for (r in 0 until kRadiusSteps) {
                    val radius = r * kDeltaRadius + kMinRadius
                    val almostCircle = almostCircleMaker(radius)

                    save()
                    drawCircle(0f, 0f, radius, rowPaint)
                    translate(0f, 2 * maxRowR + kPad)
                    drawPath(almostCircle, rowPaint)
                    restore()

                    val maxColR = radius + kMaxBlurRadius
                    translate(maxColR * 2 + kPad, 0f)
                }
                restore()

                // Horizontal separator line between rows (omitted on the
                // final row, matching upstream).
                if (br != kBlurRadiusSteps - 1) {
                    val blackPaint = SkPaint().apply { color = SK_ColorBLACK }
                    val lineY = 3 * maxRowR + 1.5f * kPad
                    drawLine(0f, lineY, lineWidth, lineY, blackPaint)
                }
                translate(0f, maxRowR * 4 + 2 * kPad)
            }
        }
    }

    public companion object {
        /** Mirrors Skia's `SkBlurMask::ConvertRadiusToSigma`. */
        public fun convertRadiusToSigma(radius: Float): Float =
            if (radius > 0f) 0.57735f * radius + 0.5f else 0f
    }
}
