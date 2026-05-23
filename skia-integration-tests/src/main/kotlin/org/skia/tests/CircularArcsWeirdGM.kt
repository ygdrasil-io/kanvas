package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/circulararcs.cpp::circular_arcs_weird`
 * (`DEF_SIMPLE_GM(circular_arcs_weird, canvas, 1000, 400)`).
 *
 * Exercises pathological arc parameters:
 * - **noDrawArcs**: zero sweep or degenerate (negative/zero extent) ovals — these
 *   should produce no visible output.
 * - **arcs**: large/negative start angles, full or over-full sweeps (±360, ±540),
 *   and a large combined start + sweep. Each arc is rendered with five paints:
 *   fill, stroke, hairline, stroke-and-fill, and dashed stroke. The arcs are
 *   drawn twice (non-center and center) side-by-side, separated by a red line.
 */
public class CircularArcsWeirdGM : GM() {

    override fun getName(): String = "circular_arcs_weird"
    override fun getISize(): SkISize = SkISize.Make(1000, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val kS = 50f

        data class Arc(val oval: SkRect, val start: Float, val sweep: Float)

        val noDrawArcs = listOf(
            // no sweep
            Arc(SkRect.MakeWH(kS, kS),   0f,  0f),
            // empty rect in x
            Arc(SkRect.MakeWH(-kS, kS),  0f, 90f),
            // empty rect in y
            Arc(SkRect.MakeWH(kS, -kS),  0f, 90f),
            // empty rect in x and y
            Arc(SkRect.MakeWH(0f,   0f), 0f, 90f),
        )

        val arcs = listOf(
            // large start
            Arc(SkRect.MakeWH(kS, kS),   810f,   90f),
            // large negative start
            Arc(SkRect.MakeWH(kS, kS),  -810f,   90f),
            // exactly 360 sweep
            Arc(SkRect.MakeWH(kS, kS),     0f,  360f),
            // exactly -360 sweep
            Arc(SkRect.MakeWH(kS, kS),     0f, -360f),
            // exactly 540 sweep
            Arc(SkRect.MakeWH(kS, kS),     0f,  540f),
            // exactly -540 sweep
            Arc(SkRect.MakeWH(kS, kS),     0f, -540f),
            // generic large sweep and large start
            Arc(SkRect.MakeWH(kS, kS),  1125f,  990f),
        )

        // Five paint configurations: fill, stroke, hairline, stroke-and-fill, dash.
        val dashIntervals = floatArrayOf(kS / 15f, 2f * kS / 15f)
        val paints = listOf(
            SkPaint(),                                              // fill
            SkPaint().apply {                                       // stroke
                style = SkPaint.Style.kStroke_Style
                strokeWidth = kS / 6f
            },
            SkPaint().apply {                                       // hairline
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 0f
            },
            SkPaint().apply {                                       // stroke-and-fill
                style = SkPaint.Style.kStrokeAndFill_Style
                strokeWidth = kS / 6f
            },
            SkPaint().apply {                                       // dashed stroke
                style = SkPaint.Style.kStroke_Style
                strokeWidth = kS / 6f
                pathEffect = SkDashPathEffect.Make(dashIntervals, 0f)
            },
        )

        val kPad = 20f
        c.translate(kPad, kPad)

        // This loop should draw nothing.
        for (arc in noDrawArcs) {
            for (paint in paints) {
                val p = paint.copy().apply { isAntiAlias = true }
                c.drawArc(arc.oval, arc.start, arc.sweep, false, p)
                c.drawArc(arc.oval, arc.start, arc.sweep, true, p)
            }
        }

        // Separator line between no-draw region and real arcs.
        val linePaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
        }
        val midX  = arcs.size * (kS + kPad) - kPad / 2f
        val height = paints.size * (kS + kPad)
        c.drawLine(midX, -kPad, midX, height, linePaint)

        for (paint in paints) {
            val p = paint.copy().apply { isAntiAlias = true }
            c.save()
            // useCenter = false
            for (arc in arcs) {
                c.drawArc(arc.oval, arc.start, arc.sweep, false, p)
                c.translate(kS + kPad, 0f)
            }
            // useCenter = true
            for (arc in arcs) {
                c.drawArc(arc.oval, arc.start, arc.sweep, true, p)
                c.translate(kS + kPad, 0f)
            }
            c.restore()
            c.translate(0f, kS + kPad)
        }
    }
}
