package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import kotlin.math.PI

/**
 * Port of Skia's `gm/dashcircle.cpp::DashCircle2GM` (635 × 900,
 * non-animated frame).
 *
 * Walks 10 dash patterns (relative to `τ = 2π`, including patterns
 * that sum to > 1 and individual intervals > 1) over 7 matrices
 * (identity, scale 1.2, y-flip, x-flip, scale 0.7, rotate 25°, and a
 * composed flip + rotate²). Each cell draws two concentric ovals :
 * the main one at radius 20 / stroke-width 15, and a thin one at
 * radius 30 / stroke-width 0.4.
 *
 * Initial phase = 12° (non-zero so the GM doesn't degenerate to
 * an aligned starting tick when the animator isn't running).
 */
public class DashCircle2GM : GM() {

    override fun getName(): String = "dashcircle2"
    override fun getISize(): SkISize = SkISize.Make(635, 900)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val intervals = arrayOf(
            floatArrayOf(0.333f, 0.333f),
            floatArrayOf(0.015f, 0.015f),
            floatArrayOf(0.01f, 0.09f),
            floatArrayOf(0.097f, 0.003f),
            floatArrayOf(0.02f, 0.04f),
            floatArrayOf(0.1f, 0.2f),
            floatArrayOf(0.25f, 0.25f),
            floatArrayOf(0.6f, 0.7f),
            floatArrayOf(1.2f, 0.8f),
            floatArrayOf(0.1f, 1.1f),
        )

        val kRadius = 20f
        val kStrokeWidth = 15f
        val kPad = 5f
        val kCircle = SkRect.MakeLTRB(-kRadius, -kRadius, kRadius, kRadius)

        val kThinRadius = kRadius * 1.5f
        val kThinCircle = SkRect.MakeLTRB(-kThinRadius, -kThinRadius, kThinRadius, kThinRadius)
        val kThinStrokeWidth = 0.4f
        val phaseDegrees = 12f

        val kTau = 2f * PI.toFloat()
        val kCircumference = kRadius * kTau
        val kThinCircumference = kThinRadius * kTau

        val deffects = Array(intervals.size) { i ->
            val scaled = floatArrayOf(
                kCircumference * intervals[i][0],
                kCircumference * intervals[i][1],
            )
            SkDashPathEffect.Make(scaled, kCircumference * phaseDegrees * kTau / 360f)
        }
        val thinDEffects = Array(intervals.size) { i ->
            val scaled = floatArrayOf(
                kThinCircumference * intervals[i][0],
                kThinCircumference * intervals[i][1],
            )
            SkDashPathEffect.Make(scaled, kThinCircumference * phaseDegrees * kTau / 360f)
        }

        val rotate = SkMatrix.MakeRotate(25f)
        val flipX = SkMatrix.MakeAll(-1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        val flipY = SkMatrix.MakeAll(1f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 1f)
        val matrices = arrayOf(
            SkMatrix.I(),
            SkMatrix.MakeScale(1.2f, 1.2f),
            flipY,
            flipX,
            SkMatrix.MakeScale(0.7f, 0.7f),
            rotate,
            SkMatrix.concat(SkMatrix.concat(flipX, rotate), rotate),
        )

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = kStrokeWidth
        }

        // Compute union of bounds across matrices for layout.
        var bounds = SkRect.MakeLTRB(0f, 0f, 0f, 0f)
        val kBounds = kThinCircle.makeOutset(kThinStrokeWidth / 2f, kThinStrokeWidth / 2f)
        var first = true
        for (m in matrices) {
            val dev = m.mapRect(kBounds)
            if (first) { bounds = dev; first = false } else bounds.join(dev)
        }

        c.save()
        c.translate(-bounds.left + kPad, -bounds.top + kPad)
        for (i in deffects.indices) {
            c.save()
            for (m in matrices) {
                c.save()
                c.concat(m)

                paint.pathEffect = deffects[i]
                paint.strokeWidth = kStrokeWidth
                c.drawOval(kCircle, paint)

                paint.pathEffect = thinDEffects[i]
                paint.strokeWidth = kThinStrokeWidth
                c.drawOval(kThinCircle, paint)

                c.restore()
                c.translate(bounds.width() + kPad, 0f)
            }
            c.restore()
            c.translate(0f, bounds.height() + kPad)
        }
        c.restore()
    }
}
