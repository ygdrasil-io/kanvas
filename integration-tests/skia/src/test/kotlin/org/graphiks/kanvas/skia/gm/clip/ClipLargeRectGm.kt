package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/scaledrects.cpp::cliplargerect` (DEF_SIMPLE_GM,
 * 256 × 256).
 *
 * Stress test for `clipRect` interaction with a giant translate
 * (`1e24f` !). The outer clip narrows to `(0, 0, 120, 256)`, then a
 * nested `translate(1e24, 0)` + `clear(GREEN)` would (incorrectly)
 * paint outside the clip bounds — the clipRect must dominate. Final
 * black hairline at `x = 120` shows the clip boundary.
 * @see https://github.com/google/skia/blob/main/gm/scaledrects.cpp
 */
class ClipLargeRectGm : SkiaGm {
    override val name = "cliplargerect"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 47.7
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save()
        canvas.clipRect(Rect(0f, 0f, 120f, 256f))
        canvas.save()
        canvas.translate(1e24f, 0f)
        canvas.drawColor(0f, 1f, 0f)
        canvas.restore()
        canvas.restore()

        val line = Paint(style = PaintStyle.STROKE, color = Color.BLACK)
        canvas.drawLine(120f, 0f, 120f, 256f, line)
    }
}
