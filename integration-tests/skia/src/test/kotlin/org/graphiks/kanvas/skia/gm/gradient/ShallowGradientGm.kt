package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/shallowgradient.cpp`.
 * Tests shallow-gradient rendering with overlapping colored rectangles.
 * @see https://github.com/google/skia/blob/main/gm/shallowgradient.cpp
 */
class ShallowGradientGm : SkiaGm {
    override val name = "shallowgradient"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.drawRect(Rect.fromXYWH(10f, 10f, 100f, 100f),
            Paint(color = Color.fromRGBA(1f, 0f, 0f, 1f)))
        canvas.drawRect(Rect.fromXYWH(60f, 60f, 100f, 100f),
            Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.5f)))
        canvas.drawRect(Rect.fromXYWH(120f, 120f, 100f, 100f),
            Paint(color = Color.fromRGBA(1f, 1f, 0f, 0.3f)))
    }
}
