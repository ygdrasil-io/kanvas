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
 * Port of Skia's `gm/hardstop_gradients.cpp`.
 * 8×3 grid of linear gradients across clamp/repeat/mirror tile modes.
 * @see https://github.com/google/skia/blob/main/gm/hardstop_gradients.cpp
 */
class HardstopGradientShaderGm : SkiaGm {
    override val name = "hardstop_gradients"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private fun toStops(colors: IntArray, positions: FloatArray?): List<GradientStop> {
        return colors.indices.map { i ->
            val c = colors[i]
            GradientStop(
                positions?.getOrElse(i) { if (i == colors.lastIndex) 1f else 0f }
                    ?: (i.toFloat() / (colors.size - 1).coerceAtLeast(1)),
                Color.fromRGBA(
                    ((c ushr 16) and 0xFF) / 255f,
                    ((c ushr 8) and 0xFF) / 255f,
                    (c and 0xFF) / 255f,
                    ((c ushr 24) and 0xFF) / 255f,
                ),
            )
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val colors = intArrayOf(
            0xFFFF0000.toInt(),
            0xFF00FF00.toInt(),
            0xFF0000FF.toInt(),
            0xFFFFFF00.toInt(),
            0xFFFF00FF.toInt(),
        )

        val positions: Array<FloatArray?> = arrayOf(
            null,
            null,
            floatArrayOf(0.00f, 0.25f, 1.00f),
            floatArrayOf(0.00f, 0.25f, 0.50f, 0.50f, 1.00f),
            floatArrayOf(0.00f, 0.50f, 0.50f, 1.00f),
            floatArrayOf(0.00f, 0.00f, 1.00f),
            floatArrayOf(0.00f, 1.00f, 1.00f),
            floatArrayOf(0.00f, 0.30f, 0.30f, 1.00f),
        )

        val numGradientColors = intArrayOf(2, 3, 3, 5, 4, 3, 3, 4)

        val tileModes = arrayOf(TileMode.CLAMP, TileMode.REPEAT, TileMode.MIRROR)

        for (cellRow in 0 until NUM_ROWS) {
            for (cellCol in 0 until NUM_COLS) {
                val pts = createGradientPoints(cellRow, cellCol)

                val n = numGradientColors[cellRow]
                val cellColors = colors.copyOfRange(0, n)
                val cellPositions = positions[cellRow]
                val stops = toStops(cellColors, cellPositions)
                val shader = Shader.LinearGradient(
                    start = pts[0], end = pts[1],
                    stops = stops, tileMode = tileModes[cellCol],
                )
                shadeRect(canvas, shader, cellRow, cellCol)
            }
        }
    }

    private fun shadeRect(canvas: GmCanvas, shader: Shader, cellRow: Int, cellCol: Int) {
        val paint = Paint(shader = shader)
        val rect = Rect.fromXYWH(
            (cellCol * CELL_WIDTH + PAD_WIDTH).toFloat(),
            (cellRow * CELL_HEIGHT + PAD_HEIGHT).toFloat(),
            RECT_WIDTH.toFloat(),
            RECT_HEIGHT.toFloat(),
        )
        canvas.drawRect(rect, paint)
    }

    private fun createGradientPoints(cellRow: Int, cellCol: Int): Array<Point> {
        val x0 = (cellCol * CELL_WIDTH + PAD_WIDTH + X_OFFSET).toFloat()
        val x1 = ((cellCol + 1) * CELL_WIDTH - PAD_WIDTH - X_OFFSET).toFloat()
        val y = (cellRow * CELL_HEIGHT + PAD_HEIGHT + RECT_HEIGHT / 2).toFloat()
        return arrayOf(Point(x0, y), Point(x1, y))
    }

    private companion object {
        const val NUM_ROWS: Int = 8
        const val NUM_COLS: Int = 3
        const val CELL_WIDTH: Int = 512 / NUM_COLS
        const val CELL_HEIGHT: Int = 512 / NUM_ROWS
        const val PAD_WIDTH: Int = 3
        const val PAD_HEIGHT: Int = 3
        const val RECT_WIDTH: Int = CELL_WIDTH - 2 * PAD_WIDTH
        const val RECT_HEIGHT: Int = CELL_HEIGHT - 2 * PAD_HEIGHT
        const val X_OFFSET: Int = 30
    }
}
