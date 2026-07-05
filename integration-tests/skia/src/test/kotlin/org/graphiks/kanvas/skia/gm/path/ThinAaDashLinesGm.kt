package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/dashing.cpp` `thin_aa_dash_lines`.
 * Sub-pixel-width dashed lines with three cap types, scaled up 100x.
 * @see https://github.com/google/skia/blob/main/gm/dashing.cpp
 */
class ThinAaDashLinesGm : SkiaGm {
    override val name = "thin_aa_dash_lines"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 75.6
    override val width = 330
    override val height = 110

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val kScale = 100f
        val intervals = floatArrayOf(10f / kScale, 5f / kScale)
        val kSubstep = 0.05f / kScale
        val kStep = intervals[0] + intervals[1]

        canvas.scale(kScale, kScale)
        canvas.translate(intervals[1], intervals[1])

        for (cap in arrayOf(StrokeCap.BUTT, StrokeCap.SQUARE, StrokeCap.ROUND)) {
            var x = -0.5f * intervals[1]
            while (x < 105f / kScale) {
                val paint = Paint(
                    style = PaintStyle.STROKE,
                    strokeWidth = 0.25f / kScale,
                    antiAlias = true,
                    strokeCap = cap,
                    pathEffect = PathEffect.Dash(intervals, 0f),
                )
                canvas.drawLine(x, 0f, x, 100f / kScale, paint)
                canvas.drawLine(0f, x, 100f / kScale, x, paint)
                x += kStep + kSubstep
            }
            canvas.translate(110f / kScale, 0f)
        }
    }
}
