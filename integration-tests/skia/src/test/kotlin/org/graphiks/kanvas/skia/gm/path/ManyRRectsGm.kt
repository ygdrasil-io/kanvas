package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/manyrrects.cpp`.
 * Renders 7,000 small round-rect paths across the canvas.
 * @see https://github.com/google/skia/blob/main/gm/manyrrects.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

class ManyRRectsGm : SkiaGm {
    override val name = "manyrrects"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            color = Color.BLUE,
            antiAlias = true,
        )

        var x = 0
        var y = 0
        val kXLimit = 700
        val kYIncrement = 5
        val kXIncrement = 5

        val rect = Rect.fromLTRB(0f, 0f, 4f, 4f)
        val rrect = RRect(rect, 1f)
        var total = 7_000
        while (total-- > 0) {
            canvas.save()
            canvas.translate(x.toFloat(), y.toFloat())
            canvas.drawPath(Path { }.apply { addRRect(rrect) }, paint)
            x += kXIncrement
            if (x > kXLimit) {
                x = 0
                y += kYIncrement
            }
            canvas.restore()
        }
    }
}
