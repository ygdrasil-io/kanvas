package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

class ThinStrokedRectsGm : SkiaGm {
    override val name = "thinstrokedrects"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 240
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val paint = Paint(
            color = Color.WHITE,
            style = PaintStyle.STROKE,
            antiAlias = true,
        )

        val rect = Rect(0f, 0f, 10f, 10f)
        val rect2 = Rect(0f, 0f, 20f, 20f)

        canvas.translate(5f, 5f)
        for (i in 0 until 8) {
            canvas.save()
            canvas.translate(i * 0.125f, i * 30f)
            for (j in STROKE_WIDTHS.indices) {
                canvas.drawRect(rect, paint.copy(strokeWidth = STROKE_WIDTHS[j]))
                canvas.translate(15f, 0f)
            }
            canvas.restore()
        }

        paint.copy(color = Color.RED)
        canvas.translate(0f, 15f)
        for (i in 0 until 8) {
            canvas.save()
            canvas.translate(i * 0.125f, i * 30f)
            canvas.scale(0.5f, 0.5f)
            for (j in STROKE_WIDTHS.indices) {
                canvas.drawRect(rect2, paint.copy(strokeWidth = 2f * STROKE_WIDTHS[j], color = Color.RED))
                canvas.translate(30f, 0f)
            }
            canvas.restore()
        }
    }

    private companion object {
        val STROKE_WIDTHS: FloatArray = floatArrayOf(4f, 2f, 1f, 0.5f, 0.25f, 0.125f, 0f)
    }
}
