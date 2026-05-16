package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/analytic_gradients.cpp:AnalyticGradientShaderGM`.
 *
 * An 8 × 4 grid of linear gradients arranged to test the analytic
 * unrolled binary gradient colorizer's behaviour on 1 → 8 interpolation
 * intervals, mixed with hardstops (duplicate stop positions). Each row
 * is one interval count; each column is one of four mode layouts:
 *
 *  - **M1** : all smooth transitions.
 *  - **M2** : all hardstops.
 *  - **M3** : alternating smooth-then-hard.
 *  - **M4** : alternating hard-then-smooth.
 *
 * Reference image: `analytic_gradients.png`, 1024 × 512 (the GM declares
 * a `1024 × 512` ISize but only fills `WIDTH = HEIGHT = 500` worth of
 * the canvas).
 *
 * Stresses the gradient lookup's binary search across many stop counts
 * (2 → 16) and the "duplicate position" hardstop dispatch.
 */
public class AnalyticGradientShaderGM : GM() {

    override fun getName(): String = "analytic_gradients"
    override fun getISize(): SkISize = SkISize.Make(1024, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val points = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(RECT_WIDTH.toFloat(), 0f),
        )

        for (cellRow in 0 until NUM_ROWS) {
            // Each interval count has 4 colour counts, one per mode column.
            val colorCounts = INTERVAL_COLOR_COUNTS[cellRow]   // length 4

            for (cellCol in 0 until NUM_COLS) {
                val colorCount = colorCounts[cellCol]
                val layout = M_POSITIONS[cellCol]

                val colors = IntArray(colorCount)
                val positions = FloatArray(colorCount)
                for (i in 0 until colorCount) {
                    positions[i] = layout[i].toFloat() / (cellRow + 1).toFloat()
                    colors[i] = COLORS[i % COLORS.size]
                }

                val shader = SkLinearGradient.Make(
                    p0 = points[0], p1 = points[1],
                    colors = colors, positions = positions,
                    tileMode = SkTileMode.kClamp,
                )
                shadeRect(c, shader, cellRow, cellCol)
            }
        }
    }

    private fun shadeRect(c: SkCanvas, shader: SkLinearGradient, cellRow: Int, cellCol: Int) {
        val paint = SkPaint().apply { this.shader = shader }
        c.save()
        c.translate(
            (cellCol * CELL_WIDTH + PAD_WIDTH).toFloat(),
            (cellRow * CELL_HEIGHT + PAD_HEIGHT).toFloat(),
        )
        c.drawRect(SkRect.MakeWH(RECT_WIDTH.toFloat(), RECT_HEIGHT.toFloat()), paint)
        c.restore()
    }

    private companion object {
        // From upstream `gm/analytic_gradients.cpp` file-level constants.
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

        // M-mode position layouts (same as upstream — divided by interval
        // count `cellRow + 1` to get [0, 1] positions).
        val M1_POSITIONS: IntArray = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8)
        val M2_POSITIONS: IntArray = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8)
        val M3_POSITIONS: IntArray = intArrayOf(0, 1, 2, 2, 3, 4, 4, 5, 6, 6, 7, 8)
        val M4_POSITIONS: IntArray = intArrayOf(0, 1, 1, 2, 3, 3, 4, 5, 5, 6, 7, 7, 8)
        val M_POSITIONS: Array<IntArray> = arrayOf(M1_POSITIONS, M2_POSITIONS, M3_POSITIONS, M4_POSITIONS)

        // Per-row colour counts: index by mode column.
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

        // Cycle through these for stops 0..8.
        val COLORS: IntArray = intArrayOf(
            0xFF444444.toInt(),    // SK_ColorDKGRAY
            0xFFFF0000.toInt(),    // SK_ColorRED
            0xFFFFFF00.toInt(),    // SK_ColorYELLOW
            0xFF00FF00.toInt(),    // SK_ColorGREEN
            0xFF00FFFF.toInt(),    // SK_ColorCYAN
            0xFF0000FF.toInt(),    // SK_ColorBLUE
            0xFFFF00FF.toInt(),    // SK_ColorMAGENTA
            0xFF000000.toInt(),    // SK_ColorBLACK
            0xFFCCCCCC.toInt(),    // SK_ColorLTGRAY
        )
    }
}
