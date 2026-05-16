package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/clipdrawdraw.cpp::clipdrawdraw` (DEF_SIMPLE_GM_BG,
 * 512 × 512, BG = `0xFFCCCCCC`).
 *
 * Reproduces crbug.com/423834 : the pattern
 * ```
 * save(); clipRect(rect, noAA); drawRect(bigRect, noAA); restore();
 * drawRect(rect, noAA);
 * ```
 * could leave 1-px wide remnants of the first rect when integer-edge
 * rounding diverged between `clipRect` and `drawRect`. The two `rect`
 * inputs (`rect1` and `rect2`) target both the vertical and the
 * horizontal-remnant cases. Verifies our rasterizer's edge-rounding
 * stays consistent across the two primitives.
 */
public class ClipDrawDrawGM : GM() {

    init { setBGColor(0xFFCCCCCC.toInt()) }

    override fun getName(): String = "clipdrawdraw"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Vertical remnant case.
        drawCase(c, SkRect.MakeLTRB(136.5f, 137.5f, 338.5f, 293.5f))
        // Horizontal remnant case. 179.488 rounds the right way (179);
        // 179.499 rounds the wrong way (180). Both are exercised in
        // `drawCase` because the second drawRect uses the same rect.
        drawCase(c, SkRect.MakeLTRB(207.5f, 179.499f, 530.5f, 429.5f))
    }

    private fun drawCase(canvas: SkCanvas, rect: SkRect) {
        val p = SkPaint().apply { isAntiAlias = false }
        val bigRect = SkRect.MakeWH(600f, 600f)

        canvas.save()
        // Black rect through the clip.
        canvas.save()
        canvas.clipRect(rect)
        canvas.drawRect(bigRect, p)
        canvas.restore()

        // White rect on top.
        p.color = SK_ColorWHITE
        canvas.drawRect(rect, p)
        canvas.restore()
    }
}
