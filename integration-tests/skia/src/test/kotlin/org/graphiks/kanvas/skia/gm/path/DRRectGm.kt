package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/drrect.cpp`.
 * A 4 × 5 grid of double-rounded-rectangles (donuts) mixing outer/inner RRect types.
 * @see https://github.com/google/skia/blob/main/gm/drrect.cpp
 */
class DRRectGm : SkiaGm {
    override val name = "drrect"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true)
        var r = Rect(0f, 0f, 100f, 100f)
        val radii = listOf(CornerRadii(0f, 0f), CornerRadii(30f, 1f), CornerRadii(10f, 40f), CornerRadii(40f, 40f))
        val dx = r.width + 16f
        val dy = r.height + 16f

        val outers = listOf(
            RRect(r, CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
            RRect(r, CornerRadii(r.width / 2f, r.height / 2f)),
            RRect(r, 20f),
            RRect(r, radii[0], radii[1], radii[2], radii[3]),
        )

        r = Rect(r.left + 25f, r.top + 25f, r.right - 25f, r.bottom - 25f)
        val inners = listOf(
            null,
            RRect(r, CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f), CornerRadii(0f, 0f)),
            RRect(r, CornerRadii(r.width / 2f, r.height / 2f)),
            RRect(r, 20f),
            RRect(r, radii[0], radii[1], radii[2], radii[3]),
        )

        canvas.translate(16f, 16f)
        for (oj in inners.indices) {
            for (oi in outers.indices) {
                canvas.save()
                canvas.translate(dx * oj, dy * oi)
                val path = Path { }
                path.addRRect(outers[oi])
                val inner = inners[oj]
                if (inner != null && !inner.rect.isEmpty) {
                    path.addRRect(inner)
                }
                path.fillType = FillType.EVEN_ODD
                canvas.drawPath(path, paint)
                canvas.restore()
            }
        }
    }
}
