package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/hardstop_gradients_many.cpp`
 * (`HardstopGradientsManyGM`, GM name `hardstop_gradients_many`).
 *
 * 100 stacked rows. Row `i` (1-indexed) draws a single horizontal linear
 * gradient that contains `i` adjacent blue→white blends, all packed into
 * `[0, 1]`. Each repeated section starts and ends with a hardstop (a
 * duplicate position), so for `i = 1` we just see a smooth blue→white
 * sweep, while `i = 100` shows 100 razor-thin blue/white tiles.
 *
 * The colour list grows linearly with `i` — `2 * i` colours for `i`
 * sections — and the corresponding positions are `[0, 1/i, 1/i, 2/i,
 * 2/i, …, 1]` (each interior position duplicated to make the hardstop).
 *
 * Reference image: `hardstop_gradients_many.png`, 1000 × 2000.
 *
 * Stresses :
 *  - the gradient stop binary search across very dense stop counts
 *    (`~200` entries by row 100 in this single GM, 100× more than what
 *    `HardstopGradientShaderGM` covers);
 *  - rendering of consecutive hardstops in a long horizontal sweep —
 *    the row stride is exactly `kCellHeight = 20 px`, with rect content
 *    spanning the inner `kRectHeight = 18 px`.
 */
public class HardstopGradientsManyGM : GM() {

    override fun getName(): String = "hardstop_gradients_many"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val p0 = SkPoint(0f, (RECT_HEIGHT / 2).toFloat())
        val p1 = SkPoint(WIDTH.toFloat(), (RECT_HEIGHT / 2).toFloat())

        for (row in 1..NUM_ROWS) {
            // Build colors and positions for `row` adjacent blue→white blends.
            val colors = IntArray(2 * row)
            val positions = FloatArray(2 * row)
            for (k in 0 until row) {
                colors[2 * k] = SK_ColorBLUE
                colors[2 * k + 1] = SK_ColorWHITE
            }
            // positions = {0, 1/row, 1/row, 2/row, 2/row, ..., (row-1)/row, (row-1)/row, 1}.
            positions[0] = 0f
            for (pos in 1 until row) {
                val place = pos.toFloat() / row.toFloat()
                positions[2 * pos - 1] = place
                positions[2 * pos] = place
            }
            positions[2 * row - 1] = 1f

            val shader = SkLinearGradient.Make(
                p0 = p0, p1 = p1,
                colors = colors,
                positions = positions,
                tileMode = SkTileMode.kClamp,
            )
            val paint = SkPaint().apply { this.shader = shader }
            c.drawRect(
                SkRect.MakeXYWH(
                    0f,
                    PAD_HEIGHT.toFloat(),
                    WIDTH.toFloat(),
                    RECT_HEIGHT.toFloat(),
                ),
                paint,
            )
            c.translate(0f, CELL_HEIGHT.toFloat())
        }
    }

    private companion object {
        // From upstream `gm/hardstop_gradients_many.cpp` constants.
        const val WIDTH: Int = 1000
        const val HEIGHT: Int = 2000
        const val NUM_ROWS: Int = 100
        const val CELL_HEIGHT: Int = HEIGHT / NUM_ROWS
        const val PAD_HEIGHT: Int = 1
        const val RECT_HEIGHT: Int = CELL_HEIGHT - 2 * PAD_HEIGHT
    }
}
