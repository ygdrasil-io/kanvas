package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

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
            Paint().apply { r = 1f; g = 1f; b = 1f; a = 1f },
        )
        val path = Path().apply {
            fillType = KanvasFillType.EVEN_ODD
            moveTo(0f, 0f)
            moveTo(245.5f, 98.5f)
            cubicTo(245.5f, 98.5f, 242f, 78f, 260f, 75f)
        }
        canvas.drawPath(path, Paint().apply {
            r = 0f; g = 0f; b = 0f; a = 1f
        })
    }
}
