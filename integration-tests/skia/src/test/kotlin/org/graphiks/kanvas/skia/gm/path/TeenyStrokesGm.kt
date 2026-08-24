package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/teenystrokes.cpp`.
 * Tests extremely thin stroke rendering with varying sub-pixel widths.
 * @see https://github.com/google/skia/blob/main/gm/teenystrokes.cpp
 */

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

class TeenyStrokesGm : SkiaGm {
    override val name = "teenyStrokes"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 93.2
    override val width = 400
    override val height = 800

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        line(canvas, 0.00005f, ColorARGB.Black)
        line(canvas, 0.000045f, ColorARGB.Red)
        line(canvas, 0.0000035f, ColorARGB.Green)
        line(canvas, 0.000003f, ColorARGB.Blue)
        line(canvas, 0.000002f, ColorARGB.Black)
    }

    private fun line(canvas: GmCanvas, scale: Float, color: ColorARGB) {
        val paint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            color = color,
            strokeWidth = scale * 5f,
        )
        canvas.translate(50f, 0f)
        canvas.save()
        canvas.scale(1f / scale, 1f / scale)
        canvas.drawLine(20f * scale, 20f * scale, 20f * scale, 100f * scale, paint)
        canvas.drawLine(20f * scale, 20f * scale, 100f * scale, 100f * scale, paint)
        canvas.restore()
    }
}
