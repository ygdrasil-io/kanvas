package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/drrect_small_inner.cpp`.
 * Exercises drawDRRect where the inner rounded-rect shrinks to sub-pixel sizes.
 * @see https://github.com/google/skia/blob/main/gm/drrect_small_inner.cpp
 */
class DRRectSmallInnerGm : SkiaGm {
    override val name = "drrect_small_inner"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 170
    override val height = 610

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)
        val outerRadius = 35f
        val outer = RRect(Rect.fromXYWH(0f, 0f, 2f * outerRadius, 2f * outerRadius), outerRadius)

        canvas.translate(10f, 10f)
        for (offcenter in listOf(false, true)) {
            canvas.save()
            for (oval in listOf(false, true)) {
                for (innerRadiusX in floatArrayOf(1f, 0.5f, 0.1f, 0.01f)) {
                    val innerRadiusY = if (oval) innerRadiusX * 0.95f else innerRadiusX
                    var tx = outerRadius - innerRadiusX
                    val ty = outerRadius - innerRadiusY
                    if (offcenter) tx += 1f
                    val inner = RRect(Rect.fromXYWH(tx, ty, 2f * innerRadiusX, 2f * innerRadiusY), 0f)

                    val path = Path { }
                    path.addRRect(outer)
                    if (innerRadiusX > 0f && innerRadiusY > 0f) {
                        path.addRRect(inner)
                    }
                    path.fillType = FillType.EVEN_ODD
                    canvas.drawPath(path, paint)
                    canvas.translate(0f, 2f * outerRadius + 5f)
                }
            }
            canvas.restore()
            canvas.translate(2f * outerRadius + 2f, 0f)
        }
    }
}
