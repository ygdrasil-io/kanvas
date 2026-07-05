package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/strokes.cpp::Strokes3GM` (`strokes3`, 1500 x 1500).
 * 6 rows x 13 columns of nested-contour paths under wide strokes.
 * Note: SkStroker (FillPathWithPaint) is not available in the new API;
 * the third column (filled outline) is skipped.
 * @see https://github.com/google/skia/blob/main/gm/strokes.cpp
 */
class Strokes3Gm : SkiaGm {
    override val name = "strokes3"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1500
    override val height = 1500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val origPaint = Paint(antiAlias = true, style = PaintStyle.STROKE)
        val strokePaint = Paint(antiAlias = true, style = PaintStyle.STROKE)

        canvas.translate(20f, 80f)

        val bounds = Rect.fromXYWH(0f, 0f, 50f, 50f)
        val dx = bounds.width * 4f / 3f
        val dy = bounds.height * 5f

        for (i in 0 until 6) {
            val orig = makeProc(i, bounds)
            canvas.save()
            for (j in 0 until 13) {
                val w = (j * j).toFloat()
                val sp = strokePaint.copy(strokeWidth = w)
                canvas.drawPath(orig, sp)
                canvas.drawPath(orig, origPaint)
                canvas.translate(dx + w, 0f)
            }
            canvas.restore()
            canvas.translate(0f, dy)
        }
    }

    private fun makeProc(i: Int, bounds: Rect): Path = when (i) {
        0 -> Path { }.apply { addRect(bounds); addRect(insetRect(bounds)) }
        1 -> Path { }.apply { addRect(bounds); addRect(insetRect(bounds)); fillType = FillType.EVEN_ODD }
        2 -> Path { }.apply { addOval(bounds); addOval(insetRect(bounds)) }
        3 -> Path { }.apply { addOval(bounds); addOval(insetRect(bounds)); fillType = FillType.EVEN_ODD }
        4 -> {
            val r = Rect.fromLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
            val ir = r.let { Rect.fromLTRB(it.left + it.width / 10f, it.top - it.height / 10f, it.right - it.width / 10f, it.bottom + it.height / 10f) }
            Path { }.apply { addRect(bounds); addOval(ir) }
        }
        else -> {
            val r = Rect.fromLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
            val ir = r.let { Rect.fromLTRB(it.left + it.width / 10f, it.top - it.height / 10f, it.right - it.width / 10f, it.bottom + it.height / 10f) }
            Path { }.apply { addRect(bounds); addOval(ir); fillType = FillType.EVEN_ODD }
        }
    }

    private fun insetRect(r: Rect): Rect {
        val dw = r.width / 10f
        val dh = r.height / 10f
        return Rect.fromLTRB(r.left + dw, r.top + dh, r.right - dw, r.bottom - dh)
    }
}
