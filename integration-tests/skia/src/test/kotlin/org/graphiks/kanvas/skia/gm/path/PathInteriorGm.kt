package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/pathinterior.cpp:PathInteriorGM`.
 *
 * 64 cells laid out 8 × 8, each containing a path with two contours
 * under every combination of fill rules and rect/rrect shapes.
 *
 * @see https://github.com/google/skia/blob/main/gm/pathinterior.cpp
 */
class PathInteriorGm : SkiaGm {
    override val name = "pathinterior"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 770
    override val height = 770

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Set background color
        canvas.drawColor(0xDD.toFloat() / 255f, 0xDD.toFloat() / 255f, 0xDD.toFloat() / 255f)
        canvas.translate(8.5f, 8.5f)

        val rect = Rect(0f, 0f, 80f, 80f)
        val rad = CornerRadii(rect.width / 8f, rect.height / 8f)

        var i = 0
        for (insetFirst in 0..1) {
            for (doEvenOdd in 0..1) {
                for (outerRR in 0..1) {
                    for (innerRR in 0..1) {
                        val fillType = if (doEvenOdd != 0) FillType.EVEN_ODD else FillType.WINDING

                        var r = if (insetFirst != 0) inset(rect) else rect
                        val builder = Path { }.also { it.fillType = fillType }

                        if (outerRR != 0) {
                            val rrect = org.graphiks.kanvas.types.RRect(r, rad, rad, rad, rad)
                            builder.addRRect(rrect)
                        } else {
                            builder.addRect(r)
                        }

                        r = if (insetFirst != 0) rect else inset(rect)
                        if (innerRR != 0) {
                            val rrect = org.graphiks.kanvas.types.RRect(r, rad, rad, rad, rad)
                            builder.addRRect(rrect)
                        } else {
                            builder.addRect(r)
                        }

                        val dx = (i / 8) * rect.width * 6f / 5f
                        val dy = (i % 8) * rect.height * 6f / 5f
                        i++
                        show(canvas, builder.transform(dx, dy, 1f, 1f))
                    }
                }
            }
        }
    }

    private fun show(c: GmCanvas, path: Path) {
        val fillPaint = Paint(
            antiAlias = true,
            color = Color.fromRGBA(0x88.toFloat() / 255f, 0x88.toFloat() / 255f, 0x88.toFloat() / 255f, 1f),
        )
        c.drawPath(path, fillPaint)
        val strokePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = Color.RED,
        )
        c.drawPath(path, strokePaint)
    }

    private fun inset(r: Rect): Rect {
        val ix = r.width / 8f
        val iy = r.height / 8f
        return Rect(r.left + ix, r.top + iy, r.right - ix, r.bottom - iy)
    }
}
