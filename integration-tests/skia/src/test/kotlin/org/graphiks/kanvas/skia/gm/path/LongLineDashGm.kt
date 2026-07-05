package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/longlinedash.cpp`.
 * Tests dashed stroke on a very long wide rect.
 * @see https://github.com/google/skia/blob/main/gm/longlinedash.cpp
 */

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class LongLineDashGm : SkiaGm {
    override val name = "longlinedash"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 79.4
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 80f,
            pathEffect = PathEffect.Dash(floatArrayOf(2f, 2f), 0f),
            antiAlias = true,
        )
        canvas.drawRect(Rect.fromXYWH(-10000f, 100f, 20000f, 20f), paint)
    }
}
