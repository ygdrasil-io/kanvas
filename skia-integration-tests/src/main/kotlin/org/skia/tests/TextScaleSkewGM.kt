package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/text_scale_skew.cpp::text_scale_skew`
 * (DEF_SIMPLE_GM, 256 × 128).
 *
 * Renders the string `"Skia"` in a 5 × 3 grid of `font.scaleX` ×
 * `font.skewX` permutations (`scaleX ∈ {0.5, 0.71, 1, 1.41, 2}`,
 * `skewX ∈ {-0.5, 0, 0.5}`). Each cell is centre-aligned. Originally
 * a regression test for skbug.com/40038559.
 */
public open class TextScaleSkewGM : GM() {

    override fun getName(): String = "text_scale_skew"
    override fun getISize(): SkISize = SkISize.Make(256, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawTheText(c)
    }

    protected fun drawTheText(canvas: SkCanvas) {
        val p = SkPaint().apply { isAntiAlias = true }
        val font = ToolUtils.DefaultPortableFont(18f)
        var y = 10f
        for (scale in floatArrayOf(0.5f, 0.71f, 1f, 1.41f, 2f)) {
            font.scaleX = scale
            y += font.getSpacing()
            var x = 50f
            for (skew in floatArrayOf(-0.5f, 0f, 0.5f)) {
                font.skewX = skew
                SkTextUtils.DrawString(
                    canvas, "Skia", x, y, font, p, SkTextUtils.Align.kCenter_Align,
                )
                x += 78f
            }
        }
    }
}

/**
 * Port of Skia's `gm/text_scale_skew.cpp::text_scale_skew_rotate`
 * (DEF_SIMPLE_GM, 256 × 128). Same grid as [TextScaleSkewGM] wrapped in
 * `canvas.rotate(30, 128, 64)`. Verifies matrix-application order
 * inside the scaler context.
 */
public class TextScaleSkewRotateGM : TextScaleSkewGM() {

    override fun getName(): String = "text_scale_skew_rotate"

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.rotate(30f, 128f, 64f)
        drawTheText(c)
    }
}
