package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/crbug_788500.cpp`.
 * @see https://github.com/google/skia/blob/main/gm/crbug_788500.cpp
 */
class Crbug788500Gm : SkiaGm {
    override val name = "crbug_788500"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(color = Color.fromRGBA(1f, 1f, 1f, 1f)),
        )
        val path = Path {
            moveTo(0f, 0f)
            moveTo(245.5f, 98.5f)
            cubicTo(245.5f, 98.5f, 242f, 78f, 260f, 75f)
        }.also { it.fillType = FillType.EVEN_ODD }
        canvas.drawPath(path, Paint(color = Color.fromRGBA(0f, 0f, 0f, 1f)))
    }
}
