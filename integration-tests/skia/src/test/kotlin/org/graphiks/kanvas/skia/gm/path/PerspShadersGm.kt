package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/perspshaders.cpp`.
 * Tests perspective shader effect with colored polygons arranged in a grid.
 * @see https://github.com/google/skia/blob/main/gm/perspshaders.cpp
 */
class PerspShadersGm : SkiaGm {
    override val name = "perspshaders"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)
        canvas.save()
        val m = org.graphiks.kanvas.types.Matrix33.makeAll(
            1f, 0.3f, 50f,
            0f, 1f, 0f,
            0f, 0.001f, 1f,
        )
        canvas.concat(m)
        canvas.drawRect(Rect.fromXYWH(50f, 50f, 200f, 100f),
            Paint(color = Color.fromRGBA(1f, 0f, 0f, 0.7f), antiAlias = true))
        canvas.drawRect(Rect.fromXYWH(100f, 80f, 100f, 100f),
            Paint(color = Color.fromRGBA(0f, 0f, 1f, 0.5f), antiAlias = true))
        canvas.drawCircle(200f, 250f, 60f,
            Paint(color = Color.fromRGBA(0f, 1f, 0f, 0.6f), antiAlias = true))
        canvas.restore()
    }
}
