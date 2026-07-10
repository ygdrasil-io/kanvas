package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's gm/hardstop_gradients_many.cpp.
 * 100 stacked rows of horizontal linear gradients with hardstops.
 * @see https://github.com/google/skia/blob/main/gm/hardstop_gradients_many.cpp
 */
class HardstopGradientsManyGm : SkiaGm {
    override val name = "hardstop_gradients_many"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 1000
    override val height = 2000

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p0 = Point(0f, (RECT_HEIGHT / 2).toFloat())
        val p1 = Point(WIDTH.toFloat(), (RECT_HEIGHT / 2).toFloat())

        for (row in 1..NUM_ROWS) {
            val stops = mutableListOf<GradientStop>()
            for (k in 0 until row) {
                val place = k.toFloat() / row.toFloat()
                stops.add(GradientStop(place, Color.BLUE))
                stops.add(GradientStop(place, Color.WHITE))
            }
            stops.add(GradientStop(1f, Color.WHITE))

            val paint = Paint(shader = Shader.LinearGradient(
                start = p0, end = p1,
                stops = stops, tileMode = TileMode.CLAMP,
            ))
            canvas.drawRect(
                Rect(0f, PAD_HEIGHT.toFloat(), WIDTH.toFloat(), RECT_HEIGHT.toFloat()),
                paint
            )
            canvas.translate(0f, CELL_HEIGHT.toFloat())
        }
    }

    private companion object {
        const val WIDTH: Int = 1000
        const val HEIGHT: Int = 2000
        const val NUM_ROWS: Int = 100
        const val CELL_HEIGHT: Int = HEIGHT / NUM_ROWS
        const val PAD_HEIGHT: Int = 1
        const val RECT_HEIGHT: Int = CELL_HEIGHT - 2 * PAD_HEIGHT
    }
}
