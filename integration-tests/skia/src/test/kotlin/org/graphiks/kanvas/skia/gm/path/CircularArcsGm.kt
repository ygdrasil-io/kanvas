package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/circulararcs.cpp`.
 * Tests circular arc rendering at various sweep angles with stroke and fill.
 * @see https://github.com/google/skia/blob/main/gm/circulararcs.cpp
 */
class CircularArcsGm : SkiaGm {
    override val name = "circular_arcs"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        val paint = Paint(color = Color.BLUE, style = PaintStyle.STROKE, strokeWidth = 2f, antiAlias = true)
        val fillPaint = Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.3f), antiAlias = true)
        val r = 60f
        var x = r + 10f
        var y = r + 10f
        val sweepAngles = floatArrayOf(30f, 60f, 90f, 120f, 180f, 270f, 350f)
        for (sweep in sweepAngles) {
            canvas.drawArc(org.graphiks.kanvas.types.Rect.fromLTRB(x - r, y - r, x + r, y + r), 0f, sweep, false, paint)
            canvas.drawArc(org.graphiks.kanvas.types.Rect.fromLTRB(x - r / 2f, y - r / 2f, x + r / 2f, y + r / 2f), 0f, sweep, false, fillPaint)
            x += 2 * r + 20f
            if (x + r > width) { x = r + 10f; y += 2 * r + 20f }
        }
    }
}
