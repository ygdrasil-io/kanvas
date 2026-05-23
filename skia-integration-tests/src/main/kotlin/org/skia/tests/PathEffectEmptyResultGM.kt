package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(path_effect_empty_result, …)` (100 × 100).
 *
 * Strokes a degenerate 0×0 closed rect (all four points at `(70, 70)`)
 * with a `{2, 2}` dash pattern. Because the path has zero arc-length the
 * dash decomposer produces an empty output path — this GM verifies that
 * the rasterizer gracefully handles an empty result from `filterPath`
 * without crashing or asserting.
 *
 * Reference image: `path_effect_empty_result.png`, 100 × 100, default white BG.
 */
public class PathEffectEmptyResultGM : GM() {

    override fun getName(): String = "path_effect_empty_result"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 1f
            pathEffect = SkDashPathEffect.Make(floatArrayOf(2f, 2f), 0f)
        }

        // Degenerate rect: all corners at (70, 70) → zero arc-length.
        val r = 70f
        val l = 70f
        val t = 70f
        val b = 70f
        val path = SkPathBuilder()
            .moveTo(l, t)
            .lineTo(r, t)
            .lineTo(r, b)
            .lineTo(l, b)
            .close()
            .detach()

        c.drawPath(path, paint)
    }
}
