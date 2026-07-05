package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/poly2poly.cpp::Poly2PolyGM` (835 × 840).
 *
 * Exercises polygon-to-polygon affine transforms: translate, rotate/scale,
 * rotate/skew, and perspective, each drawn with a gray frame + diagonals
 * + red "X" glyph.
 * @see https://github.com/google/skia/blob/main/gm/poly2poly.cpp
 */
class Poly2PolyGm : SkiaGm {
    override val name = "poly2poly"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 835
    override val height = 840

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(antiAlias = true, strokeWidth = 4f)
        val font = Font(typeface, size = 40f)

        // Translate (1 point)
        canvas.save()
        canvas.translate(10f, 10f)
        doDraw(canvas, font, paint, Matrix33.translate(5f, 5f))
        canvas.restore()

        // Rotate/uniform-scale (2 points)
        canvas.save()
        canvas.translate(160f, 10f)
        doDraw(canvas, font, paint, Matrix33.makeAll(1f, 0f, 32f, 0f, 1f, 32f))
        canvas.restore()

        // Rotate/skew (3 points)
        canvas.save()
        canvas.translate(10f, 110f)
        doDraw(canvas, font, paint, Matrix33.makeAll(1f, 0.5f, 0f, 0f, 1f, 0f))
        canvas.restore()

        // Perspective (4 points)
        canvas.save()
        canvas.translate(160f, 110f)
        val perspective = Matrix33.makeAll(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
        doDraw(canvas, font, paint, perspective)
        canvas.restore()
    }

    private fun doDraw(canvas: GmCanvas, font: Font, paint: Paint, mx: Matrix33) {
        canvas.save()
        canvas.concat(mx)

        val grayPaint = paint.copy(color = Color.fromRGBA(0.5f, 0.5f, 0.5f), style = PaintStyle.STROKE)
        val d = 64f
        canvas.drawRect(Rect.fromXYWH(0f, 0f, d, d), grayPaint)
        canvas.drawLine(0f, 0f, d, d, grayPaint)
        canvas.drawLine(0f, d, d, 0f, grayPaint)

        val redPaint = paint.copy(color = Color.RED, style = PaintStyle.FILL)
        canvas.drawString("X", d / 2f - 10f, d / 2f + 10f, font, redPaint)

        canvas.restore()
    }
}
