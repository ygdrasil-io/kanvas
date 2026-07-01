package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.PI

/**
 * Port of Dashing2GM — 10 dash patterns × 7 matrices on concentric circles.
 *
 * Matrices that only affect rotation/flip are no-ops for circles.
 * GmCanvas does not support `concat` or `rotate`, so matrices 6 (rotate 25°)
 * and 7 (flipX + rotate × 2) apply only the scale/flip components.
 * Stroke width is not transformed (GmCanvas transforms path geometry only).
 */
class DashCircle2Gm : SkiaGm {
    override val name = "dashcircle2"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 635
    override val height = 900

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
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

        val kThinRadius = kRadius * 1.5f
        val kThinStrokeWidth = 0.4f
        val phaseDegrees = 12f

        val kTau = 2f * PI.toFloat()
        val kCircumference = kRadius * kTau
        val kThinCircumference = kThinRadius * kTau

        // Matrices represented as (sx, sy) — rotation is no-op for circles.
        data class Mat(val sx: Float, val sy: Float)
        val matrices = listOf(
            Mat(1f, 1f),
            Mat(1.2f, 1.2f),
            Mat(1f, -1f),
            Mat(-1f, 1f),
            Mat(0.7f, 0.7f),
            Mat(1f, 1f),
            Mat(-1f, 1f),
        )

        val deffects = intervals.map { interval ->
            val scaled = floatArrayOf(
                kCircumference * interval[0],
                kCircumference * interval[1],
            )
            PathEffect.Dash(scaled, kCircumference * phaseDegrees * kTau / 360f)
        }
        val thinDEffects = intervals.map { interval ->
            val scaled = floatArrayOf(
                kThinCircumference * interval[0],
                kThinCircumference * interval[1],
            )
            PathEffect.Dash(scaled, kThinCircumference * phaseDegrees * kTau / 360f)
        }

        // Compute layout bounds: scale(1.2) gives the widest bounds.
        val kBoundsHalf = kThinRadius + kThinStrokeWidth / 2f
        val maxHalf = kBoundsHalf * 1.2f
        val cellWidth = maxHalf * 2f
        val cellHeight = maxHalf * 2f

        canvas.save()
        canvas.translate(-kBoundsHalf * 1.2f + kPad, -kBoundsHalf * 1.2f + kPad)

        val kCircle = Rect.fromLTRB(-kRadius, -kRadius, kRadius, kRadius)
        val kThinCircle = Rect.fromLTRB(-kThinRadius, -kThinRadius, kThinRadius, kThinRadius)

        for (i in deffects.indices) {
            canvas.save()
            for (m in matrices) {
                canvas.save()
                canvas.scale(m.sx, m.sy)

                val outerPaint = Paint(
                    style = PaintStyle.STROKE,
                    strokeWidth = kStrokeWidth,
                    pathEffect = deffects[i],
                    antiAlias = true,
                )
                canvas.drawOval(kCircle, outerPaint)

                val thinPaint = Paint(
                    style = PaintStyle.STROKE,
                    strokeWidth = kThinStrokeWidth,
                    pathEffect = thinDEffects[i],
                    antiAlias = true,
                )
                canvas.drawOval(kThinCircle, thinPaint)

                canvas.restore()
                canvas.translate(cellWidth + kPad, 0f)
            }
            canvas.restore()
            canvas.translate(0f, cellHeight + kPad)
        }
        canvas.restore()
    }
}
