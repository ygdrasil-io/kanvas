package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

class FadeFilterGm : SkiaGm {
    override val name = "fadefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0.5f,
            0f, 1f, 0f, 0f, 0.5f,
            0f, 0f, 1f, 0f, 0.5f,
            0f, 0f, 0f, 1f, 0f,
        )
        val paint = Paint(
            colorFilter = ColorFilter.Matrix(matrix),
        )
        canvas.drawRect(Rect.fromLTRB(64f, 64f, 192f, 192f), paint)
    }
}
