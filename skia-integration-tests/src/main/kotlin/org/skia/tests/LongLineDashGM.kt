package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(longlinedash, …)` (512 × 512).
 *
 * Draws a single enormously wide dashed rect: the rect extends from
 * x = -10000 to x = +10000 (width 20000) but is clipped to the 512 × 512
 * canvas. The thick stroke (80 px) with a `{2, 2}` pattern is specifically
 * chosen to trigger off-by-one errors in the dash interval counter at very
 * large coordinates.
 *
 * Reference image: `longlinedash.png`, 512 × 512, default white BG.
 */
public class LongLineDashGM : GM() {

    override fun getName(): String = "longlinedash"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 80f
            pathEffect = SkDashPathEffect.Make(floatArrayOf(2f, 2f), 0f)
        }

        c.drawRect(SkRect.MakeXYWH(-10000f, 100f, 20000f, 20f), paint)
    }
}
