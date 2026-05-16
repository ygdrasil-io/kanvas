package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(longwavyline, …)`.
 *
 * One very long stroked path (`x ∈ [-10000, 10000]`, ~ 1000 quad
 * segments forming a wave around `y = 100`). The 512 × 512 canvas sees
 * only the central slice; the test stresses the rasterizer's clipping
 * + flattening on a path that extends far outside the visible area.
 *
 * Reference image: `longwavyline.png`, 512 × 512, default white BG.
 *
 * The only `dashing.cpp` `DEF_SIMPLE_GM` that doesn't actually use a
 * dash effect — bonus harvest.
 */
public class LongWavyLineGM : GM() {

    override fun getName(): String = "longwavyline"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }

        val wavy = SkPathBuilder().moveTo(-10000f, 100f)
        var i = -10000f
        while (i < 10000f) {
            wavy.quadTo(i + 5f, 95f, i + 10f, 100f)
            wavy.quadTo(i + 15f, 105f, i + 20f, 100f)
            i += 20f
        }
        c.drawPath(wavy.detach(), paint)
    }
}
