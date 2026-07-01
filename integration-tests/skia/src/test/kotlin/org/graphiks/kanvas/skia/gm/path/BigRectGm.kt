package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bigrect.cpp`.
 * 8×3 grid of giant rects (10⁵, 5×10¹⁰, 10⁶ wide) drawn with
 * varying style/strokeWidth/antiAlias under a 35×35 clip.
 * @see https://github.com/google/skia/blob/main/gm/bigrect.cpp
 */
class BigRectGm : SkiaGm {
    override val name = "bigrect"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 41.9
    override val width = 325
    override val height = 125

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val sizes = floatArrayOf(100f, 5e10f, 1e6f)
        for (i in 0 until 8) {
            for (j in sizes.indices) {
                canvas.save()
                canvas.translate((i * 40 + 5).toFloat(), (j * 40 + 5).toFloat())
                val paint = Paint(
                    color = Color.BLUE,
                    style = if (i and 1 != 0) PaintStyle.FILL else PaintStyle.STROKE,
                    strokeWidth = if (i and 2 != 0) 1f else 0f,
                    antiAlias = (i and 4) != 0,
                )
                drawBigRect(canvas, sizes[j], paint)
                canvas.restore()
            }
        }
    }

    private fun drawBigRect(canvas: GmCanvas, big: Float, rectPaint: Paint) {
        canvas.clipRect(Rect(0f, 0f, 35f, 35f))
        canvas.translate(0.5f, 0.5f)

        canvas.drawRect(Rect(-big, 5f, big, 10f), rectPaint)
        canvas.drawRect(Rect(5f, -big, 10f, big), rectPaint)
        canvas.drawRect(Rect(-big, 20f, 17f, 25f), rectPaint)
        canvas.drawRect(Rect(20f, -big, 25f, 17f), rectPaint)
        canvas.drawRect(Rect(28f, 20f, big, 25f), rectPaint)
        canvas.drawRect(Rect(20f, 28f, 25f, big), rectPaint)
        canvas.drawRect(Rect(-2f, -1f, 0f, 35f), rectPaint)
        canvas.drawRect(Rect(-1f, -2f, 35f, 0f), rectPaint)
        canvas.drawRect(Rect(34f, -1f, 36f, 35f), rectPaint)
        canvas.drawRect(Rect(-1f, 34f, 35f, 36f), rectPaint)

        val outOfBoundsPaint = Paint(
            color = Color.RED,
            style = PaintStyle.STROKE,
            strokeWidth = 0f,
        )
        canvas.drawRect(Rect(-1f, -1f, 35f, 35f), outOfBoundsPaint)
    }
}
