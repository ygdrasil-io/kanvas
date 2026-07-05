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
 * Port of Skia's gm/analytic_gradients.cpp:AnalyticGradientShaderGM.
 * An 8 x 4 grid of linear gradients arranged to test the analytic
 * unrolled binary gradient colorizer's behaviour on 1 -> 8 interpolation
 * intervals, mixed with hardstops (duplicate stop positions).
 * @see https://github.com/google/skia/blob/main/gm/analytic_gradients.cpp
 */
class AnalyticGradientShaderGm : SkiaGm {
    override val name = "analytic_gradients"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val points = arrayOf(
            Point(0f, 0f),
            Point(RECT_WIDTH.toFloat(), 0f),
        )

        for (cellRow in 0 until NUM_ROWS) {
            val colorCounts = INTERVAL_COLOR_COUNTS[cellRow]

            for (cellCol in 0 until NUM_COLS) {
                val colorCount = colorCounts[cellCol]
                val layout = M_POSITIONS[cellCol]

                val stops = mutableListOf<GradientStop>()
                for (i in 0 until colorCount) {
                    val pos = layout[i].toFloat() / (cellRow + 1).toFloat()
                    stops.add(GradientStop(pos, COLORS[i % COLORS.size]))
                }

                val shader = Shader.LinearGradient(
                    start = points[0], end = points[1],
                    stops = stops, tileMode = TileMode.CLAMP,
                )
                shadeRect(canvas, shader, cellRow, cellCol)
            }
        }
    }

    private fun shadeRect(canvas: GmCanvas, shader: Shader, cellRow: Int, cellCol: Int) {
        val paint = Paint(shader = shader)
        canvas.save()
        canvas.translate(
            (cellCol * CELL_WIDTH + PAD_WIDTH).toFloat(),
            (cellRow * CELL_HEIGHT + PAD_HEIGHT).toFloat(),
        )
        canvas.drawRect(Rect(0f, 0f, RECT_WIDTH.toFloat(), RECT_HEIGHT.toFloat()), paint)
        canvas.restore()
    }

    private companion object {
        const val WIDTH: Int = 500
        const val HEIGHT: Int = 500
        const val NUM_ROWS: Int = 8
        const val NUM_COLS: Int = 4
        const val CELL_WIDTH: Int = WIDTH / NUM_COLS
        const val CELL_HEIGHT: Int = HEIGHT / NUM_ROWS
        const val PAD_WIDTH: Int = 3
        const val PAD_HEIGHT: Int = 3
        const val RECT_WIDTH: Int = CELL_WIDTH - 2 * PAD_WIDTH
        const val RECT_HEIGHT: Int = CELL_HEIGHT - 2 * PAD_HEIGHT

        val M1_POSITIONS: IntArray = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        val M2_POSITIONS: IntArray = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8)
        val M3_POSITIONS: IntArray = intArrayOf(0, 1, 2, 2, 3, 4, 4, 5, 6, 6, 7, 8)
        val M4_POSITIONS: IntArray = intArrayOf(0, 1, 1, 2, 3, 3, 4, 5, 5, 6, 7, 7, 8)
        val M_POSITIONS: Array<IntArray> = arrayOf(M1_POSITIONS, M2_POSITIONS, M3_POSITIONS, M4_POSITIONS)

        val INTERVAL_COLOR_COUNTS: Array<IntArray> = arrayOf(
            intArrayOf(2, 2, 2, 2),
            intArrayOf(3, 4, 3, 4),
            intArrayOf(4, 6, 5, 5),
            intArrayOf(5, 8, 6, 7),
            intArrayOf(6, 10, 8, 8),
            intArrayOf(7, 12, 9, 10),
            intArrayOf(8, 14, 11, 11),
            intArrayOf(9, 16, 12, 13),
        )

        val COLORS: List<Color> = listOf(
            Color.fromRGBA(0x44 / 255f, 0x44 / 255f, 0x44 / 255f, 1f),
            Color.fromRGBA(1f, 0f, 0f, 1f),
            Color.fromRGBA(1f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 0f, 1f),
            Color.fromRGBA(0f, 1f, 1f, 1f),
            Color.fromRGBA(0f, 0f, 1f, 1f),
            Color.fromRGBA(1f, 0f, 1f, 1f),
            Color.fromRGBA(0f, 0f, 0f, 1f),
            Color.fromRGBA(0xCC / 255f, 0xCC / 255f, 0xCC / 255f, 1f),
        )
    }
}
