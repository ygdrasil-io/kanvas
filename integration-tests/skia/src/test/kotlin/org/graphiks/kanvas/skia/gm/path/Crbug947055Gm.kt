package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_947055.cpp::crbug_947055`.
 * Reference green axis-aligned rect highlighting a red rect drawn under
 * a 3×3 perspective matrix. Originally exposed a Ganesh AA bug where
 * extreme corner outsets under perspective produced jagged edges.
 * @see https://github.com/google/skia/blob/main/gm/crbug_947055.cpp
 */
class Crbug947055Gm : SkiaGm {
    override val name = "crbug_947055"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 50

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 1f)

        val paint = Paint(
            antiAlias = true,
            color = Color.GREEN,
        )
        canvas.drawRect(Rect.fromXYWH(19f, 7f, 180f, 10f), paint)

        canvas.concat(Matrix33.makeAll(
            1f, 2.4520f, 19f,
            0f, 0.3528f, 9.5f,
            0f, 0.0225f, 1f,
        ))
        canvas.drawRect(Rect.fromXYWH(0f, 0f, 180f, 500f), Paint(antiAlias = true, color = Color.RED))
    }
}
