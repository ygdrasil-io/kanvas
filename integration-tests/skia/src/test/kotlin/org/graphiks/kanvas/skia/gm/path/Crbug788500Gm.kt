package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.Canvas
import org.graphiks.kanvas.KanvasFillType
import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

class Crbug788500Gm : SkiaGm {
    override val name = "crbug_788500"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 90.0

    override fun draw(canvas: Canvas, width: Int, height: Int) {
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
