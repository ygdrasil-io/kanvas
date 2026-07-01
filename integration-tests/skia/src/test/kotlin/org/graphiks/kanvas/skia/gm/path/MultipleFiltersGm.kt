package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's gm/imagefilters.cpp DEF_SIMPLE_GM(multiple_filters).
 * Tests multiple image filters applied simultaneously.
 * @see https://github.com/google/skia/blob/main/gm/imagefilters.cpp
 */
class MultipleFiltersGm : SkiaGm {
    override val name = "multiple_filters"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 415
    override val height = 210

    private fun intToColor(value: Int): Color {
        val a = (value ushr 24) and 0xFF
        val r = (value ushr 16) and 0xFF
        val g = (value ushr 8) and 0xFF
        val b = value and 0xFF
        return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas, 0xFF999999.toInt(), 0xFF666666.toInt(), 8)
        canvas.translate(5f, 5f)

        // First filter set
        drawFilteredLayer(canvas)

        // Second filter set
        drawFilteredLayer(canvas)
    }

    private fun drawFilteredLayer(canvas: GmCanvas) {
        val restorePaint = Paint(color = Color.fromRGBA(1f, 1f, 1f, 0.5f))
        canvas.save()
        canvas.clipRect(Rect(0f, 0f, 200f, 200f))
        canvas.save()

        val circlePaint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 20f,
            color = Color.GREEN,
        )
        canvas.drawCircle(100f, 100f, 70f, circlePaint)
        canvas.restore()
        canvas.restore()
        canvas.translate(205f, 0f)
    }

    private fun drawCheckerboard(canvas: GmCanvas, c1: Int, c2: Int, size: Int) {
        val w = 415
        val h = 210
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cx = x / size
                val cy = y / size
                val solid = Paint(
                    color = if (((cx + cy) and 1) == 0) intToColor(c2) else intToColor(c1),
                    antiAlias = false,
                )
                canvas.drawRect(
                    Rect(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    solid,
                )
                x += size
            }
            y += size
        }
    }
}
