package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

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
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class CircularArcsWeirdGm : SkiaGm {
    override val name = "circular_arcs_weird"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 73.4
    override val width = 1000
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kS = 50f

        data class Arc(val oval: Rect, val start: Float, val sweep: Float)

        val noDrawArcs = listOf(
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 0f, 0f),
            Arc(Rect.fromXYWH(0f, 0f, -kS, kS), 0f, 90f),
            Arc(Rect.fromXYWH(0f, 0f, kS, -kS), 0f, 90f),
            Arc(Rect.fromXYWH(0f, 0f, 0f, 0f), 0f, 90f),
        )

        val arcs = listOf(
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 810f, 90f),
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), -810f, 90f),
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 0f, 360f),
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 0f, -360f),
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 0f, 540f),
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 0f, -540f),
            Arc(Rect.fromXYWH(0f, 0f, kS, kS), 1125f, 990f),
        )

        val dashIntervals = floatArrayOf(kS / 15f, 2f * kS / 15f)
        val paints = listOf(
            Paint(),
            Paint(style = PaintStyle.STROKE, strokeWidth = kS / 6f),
            Paint(style = PaintStyle.STROKE, strokeWidth = 0f),
            Paint(style = PaintStyle.STROKE, strokeWidth = kS / 6f),
            Paint(style = PaintStyle.STROKE, strokeWidth = kS / 6f, pathEffect = PathEffect.Dash(dashIntervals)),
        )

        val kPad = 20f
        canvas.translate(kPad, kPad)

        for (arc in noDrawArcs) {
            for (paint in paints) {
                val p = paint.copy(antiAlias = true)
                canvas.drawArc(arc.oval, arc.start, arc.sweep, false, p)
                canvas.drawArc(arc.oval, arc.start, arc.sweep, true, p)
            }
        }

        val linePaint = Paint(antiAlias = true, color = Color.RED)
        val midX = arcs.size * (kS + kPad) - kPad / 2f
        val h = paints.size * (kS + kPad)
        canvas.drawLine(midX, -kPad, midX, h, linePaint)

        for (paint in paints) {
            val p = paint.copy(antiAlias = true)
            canvas.save()
            for (arc in arcs) {
                canvas.drawArc(arc.oval, arc.start, arc.sweep, false, p)
                canvas.translate(kS + kPad, 0f)
            }
            for (arc in arcs) {
                canvas.drawArc(arc.oval, arc.start, arc.sweep, true, p)
                canvas.translate(kS + kPad, 0f)
            }
            canvas.restore()
            canvas.translate(0f, kS + kPad)
        }
    }
}
