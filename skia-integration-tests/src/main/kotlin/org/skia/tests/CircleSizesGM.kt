package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/circle_sizes.cpp` (`DEF_SIMPLE_GM(circle_sizes, ...)`).
 *
 * Sixteen anti-aliased circles in a 4×4 grid, each with radius `i + 1` where
 * `i` runs `0 → 15` — so the smallest is a 1-px disc and the largest spans
 * roughly 16 × 2 = 32 px. Centres are evenly spaced at `(14 + 32 i, 14 + 32 j)`.
 *
 * Reference image: `circle_sizes.png`, 128 × 128. The test is a regression
 * fixture from [crbug.com/772953](https://crbug.com/772953) — it's small,
 * visually deterministic, and hits every radius bucket for the AA rasterizer.
 */
public class CircleSizesGM : GM() {

    override fun getName(): String = "circle_sizes"
    override fun getISize(): SkISize = SkISize.Make(128, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        for (i in 0 until 16) {
            val cx = 14f + 32f * (i % 4)
            val cy = 14f + 32f * (i / 4)
            c.drawCircle(cx, cy, (i + 1).toFloat(), paint)
        }
    }
}
