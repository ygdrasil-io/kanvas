package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.skia.utils.SkPathUtils
import kotlin.math.max

/**
 * Port of Skia's `gm/strokedlines.cpp::strokedline_caps` (DEF_SIMPLE_GM, 1400 × 740).
 *
 * For each of the three stroke caps (square, butt, round) and four line
 * lengths (4×, 1×, ½×, ¼× stroke width) this GM draws:
 *  - The line stroked with a red→green(75%)→blue linear gradient.
 *  - The geometric outline of that stroke (via [SkPathUtils.FillPathWithPaint])
 *    as a red hairline.
 *  - The control points of that outline as red 3-px square points.
 *
 * In addition each cap row ends with a zero-length degenerate line at
 * (`kStrokeWidth/2`, `kStrokeWidth/2`) → same point.
 *
 * The gradient rect is intentionally *not* aligned with the line's
 * endpoints — it spans `[-kStrokeWidth, -kStrokeWidth, 2×kStrokeWidth,
 * 4×kStrokeWidth]` — to verify that local coordinates are tracked
 * correctly through the stroke outline.
 *
 * C++ source: `gm/strokedlines.cpp` (second half, `DEF_SIMPLE_GM`).
 * Reference: `strokedline_caps.png`.
 */
public class StrokedLineCapsGM : GM() {

    override fun getName(): String = "strokedline_caps"
    override fun getISize(): SkISize = SkISize.Make(1400, 740)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(kStrokeWidth * 3f / 2f, kStrokeWidth * 3f / 2f)

        val caps = arrayOf(
            SkPaint.Cap.kSquare_Cap,
            SkPaint.Cap.kButt_Cap,
            SkPaint.Cap.kRound_Cap,
        )

        val lengths = floatArrayOf(
            4f * kStrokeWidth,
            kStrokeWidth,
            kStrokeWidth / 2f,
            kStrokeWidth / 4f,
        )

        for (i in caps.indices) {
            val saveCount = c.save()

            val drawLine: (Float, Float, Float, Float) -> Unit = { x0, y0, x1, y1 ->
                drawPath(c, x0, y0, x1, y1, caps[i])
                c.translate(max(x0, x1) + 2f * kStrokeWidth, 0f)
            }

            for (j in lengths.indices) {
                val l = lengths[j]
                drawLine(0f, 0f, l, l)
                drawLine(l, l, 0f, 0f)
                drawLine(l / 2f, 0f, l / 2f, l)
                drawLine(0f, l / 2f, l, l / 2f)
            }

            // zero-length degenerate line
            drawLine(kStrokeWidth / 2f, kStrokeWidth / 2f, kStrokeWidth / 2f, kStrokeWidth / 2f)

            c.restoreToCount(saveCount)
            c.translate(0f, lengths[0] + 2f * kStrokeWidth)
        }
    }

    private fun drawPath(
        canvas: SkCanvas,
        x0: Float, y0: Float,
        x1: Float, y1: Float,
        cap: SkPaint.Cap,
    ) {
        // Gradient not aligned with the line's points (verifies local coord tracking).
        val p0 = SkPoint.Make(-kStrokeWidth, -kStrokeWidth)
        val p1 = SkPoint.Make(2f * kStrokeWidth, 4f * kStrokeWidth)

        val shader = SkLinearGradient.Make(
            p0, p1,
            intArrayOf(SK_ColorRED, SK_ColorGREEN, SK_ColorBLUE),
            floatArrayOf(0f, 0.75f, 1f),
            SkTileMode.kClamp,
        )

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            this.shader = shader
            strokeWidth = kStrokeWidth
            strokeCap = cap
        }
        canvas.drawLine(x0, y0, x1, y1, paint)

        // Compute the fill outline via SkPathUtils.FillPathWithPaint.
        val linePath = SkPath.Line(x0 to y0, x1 to y1)
        val builder = SkPathBuilder()
        SkPathUtils.FillPathWithPaint(linePath, paint, builder)
        val fillPath = builder.detach()

        // Draw the outline as a red hairline.
        val outlinePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
            color = SK_ColorRED
        }
        canvas.drawPath(fillPath, outlinePaint)

        // Draw the control points as red 3-px square points.
        val pointsPaint = SkPaint().apply {
            strokeWidth = 3f
            strokeCap = SkPaint.Cap.kSquare_Cap
            color = SK_ColorRED
        }
        canvas.drawPoints(SkCanvas.PointMode.kPoints, fillPath.points(), pointsPaint)
    }

    private companion object {
        const val kStrokeWidth: Float = 20f
    }
}
