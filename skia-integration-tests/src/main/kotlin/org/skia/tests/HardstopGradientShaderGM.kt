package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/hardstop_gradients.cpp:HardstopGradientShaderGM`.
 *
 * An 8 × 3 grid of linear gradients. Rows are different stop layouts
 * (no positions / 3 evenly / hard stops at various places), columns are
 * the three "interesting" tile modes:
 *
 *  - `kClamp`  : t < 0 → first stop, t > 1 → last stop.
 *  - `kRepeat` : `t = t mod 1`.
 *  - `kMirror` : reflected each integer interval.
 *
 * The gradient endpoints are inset by `X_OFFSET = 30` from each cell
 * edge so the tile-mode behaviour outside `[0, 1]` is visible inside
 * each rect.
 *
 * Reference image: `hardstop_gradients.png`, 512 × 512, default white BG.
 *
 * First multi-tile-mode GM end-to-end — exercises [SkTileMode.kRepeat]
 * and [SkTileMode.kMirror] on real geometry, in addition to the
 * already-covered [SkTileMode.kClamp].
 */
public class HardstopGradientShaderGM : GM() {

    override fun getName(): String = "hardstop_gradients"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val colors = intArrayOf(
            0xFFFF0000.toInt(),    // RED
            0xFF00FF00.toInt(),    // GREEN
            0xFF0000FF.toInt(),    // BLUE
            0xFFFFFF00.toInt(),    // YELLOW
            0xFFFF00FF.toInt(),    // MAGENTA
        )

        // null = evenly spaced. Each FloatArray is the explicit stop layout.
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

        val tileModes = arrayOf(SkTileMode.kClamp, SkTileMode.kRepeat, SkTileMode.kMirror)

        for (cellRow in 0 until NUM_ROWS) {
            for (cellCol in 0 until NUM_COLS) {
                val pts = createGradientPoints(cellRow, cellCol)

                val n = numGradientColors[cellRow]
                val cellColors = colors.copyOfRange(0, n)
                val cellPositions = positions[cellRow]
                val shader = SkLinearGradient.Make(
                    p0 = pts[0], p1 = pts[1],
                    colors = cellColors,
                    positions = cellPositions,
                    tileMode = tileModes[cellCol],
                )
                shadeRect(c, shader, cellRow, cellCol)
            }
        }
    }

    private fun shadeRect(c: SkCanvas, shader: SkLinearGradient, cellRow: Int, cellCol: Int) {
        val paint = SkPaint().apply { this.shader = shader }
        val rect = SkRect.MakeXYWH(
            (cellCol * CELL_WIDTH + PAD_WIDTH).toFloat(),
            (cellRow * CELL_HEIGHT + PAD_HEIGHT).toFloat(),
            RECT_WIDTH.toFloat(),
            RECT_HEIGHT.toFloat(),
        )
        c.drawRect(rect, paint)
    }

    private fun createGradientPoints(cellRow: Int, cellCol: Int): Array<SkPoint> {
        val x0 = (cellCol * CELL_WIDTH + PAD_WIDTH + X_OFFSET).toFloat()
        val x1 = ((cellCol + 1) * CELL_WIDTH - PAD_WIDTH - X_OFFSET).toFloat()
        val y = (cellRow * CELL_HEIGHT + PAD_HEIGHT + RECT_HEIGHT / 2).toFloat()
        return arrayOf(SkPoint(x0, y), SkPoint(x1, y))
    }

    private companion object {
        const val WIDTH: Int = 500
        const val HEIGHT: Int = 500
        const val NUM_ROWS: Int = 8
        const val NUM_COLS: Int = 3
        const val CELL_WIDTH: Int = WIDTH / NUM_COLS
        const val CELL_HEIGHT: Int = HEIGHT / NUM_ROWS
        const val PAD_WIDTH: Int = 3
        const val PAD_HEIGHT: Int = 3
        const val RECT_WIDTH: Int = CELL_WIDTH - 2 * PAD_WIDTH
        const val RECT_HEIGHT: Int = CELL_HEIGHT - 2 * PAD_HEIGHT
        const val X_OFFSET: Int = 30
    }
}
