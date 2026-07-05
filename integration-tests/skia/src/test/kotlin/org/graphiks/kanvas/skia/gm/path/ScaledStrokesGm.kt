package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/scaledstrokes.cpp`.
 * 4×4 cells (4 scales × 4 shapes) × 2 panes (no-AA / AA).
 * @see https://github.com/google/skia/blob/main/gm/scaledstrokes.cpp
 */
class ScaledStrokesGm : SkiaGm {
    override val name = "scaledstrokes"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 56.9
    override val width = 640
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        var paint = Paint(style = PaintStyle.STROKE)
        canvas.translate(5f, 5f)
        val size = 60f

        for (i in 0 until 2) {
            val aa = i == 1
            for (j in 0 until 4) {
                val scale = (4 - j).toFloat()
                val sw = 4f / scale
                paint = paint.copy(antiAlias = aa, strokeWidth = sw)

                canvas.save()
                canvas.translate(size / 2f, size / 2f)
                canvas.scale(scale, scale)
                drawPath(size / 2f / scale, canvas, paint)
                canvas.restore()

                canvas.save()
                canvas.translate(size / 2f, 80f + size / 2f)
                canvas.scale(scale, scale)
                canvas.drawCircle(0f, 0f, size / 2f / scale, paint)
                canvas.restore()

                canvas.save()
                canvas.translate(0f, 160f)
                canvas.scale(scale, scale)
                canvas.drawRect(Rect.fromXYWH(0f, 0f, size / scale, size / scale), paint)
                canvas.restore()

                canvas.save()
                canvas.translate(0f, 240f)
                canvas.scale(scale, scale)
                canvas.drawLine(0f, 0f, size / scale, size / scale, paint)
                canvas.restore()

                canvas.translate(80f, 0f)
            }
        }
    }

    private fun drawPath(size: Float, canvas: GmCanvas, paint: Paint) {
        val cc = 0.551915024494f * size
        val path = Path {
            moveTo(0f, size)
            cubicTo(cc, size, size, cc, size, 0f)
            cubicTo(size, -cc, cc, -size, 0f, -size)
            cubicTo(-cc, -size, -size, -cc, -size, 0f)
            cubicTo(-size, cc, -cc, size, 0f, size)
            close()
        }
        canvas.drawPath(path, paint)
    }
}
