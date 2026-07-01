package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/aaa.cpp::analytic_antialias_inverse`.
 * Circle path with inverse winding fill — fills red everywhere
 * except inside the circle (AA hole).
 * @see https://github.com/google/skia/blob/main/gm/aaa.cpp
 */
class AnalyticAntialiasInverseGm : SkiaGm {
    override val name = "analytic_antialias_inverse"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 800
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true, color = Color.RED)
        canvas.save()
        val path = Path { }.also { it.addCircle(100f, 100f, 30f) }
        path.fillType = FillType.INVERSE_WINDING
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}
