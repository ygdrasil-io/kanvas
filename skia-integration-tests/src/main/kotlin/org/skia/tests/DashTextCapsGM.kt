package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/dashing.cpp` `DEF_SIMPLE_GM(dashtextcaps, …)` (512 × 512).
 *
 * Draws the string "Sausages" and a horizontal line below it, both stroked
 * with a `{12, 12}` round-capped dash pattern (stroke-width 10). The round
 * caps on the dashes interact with the glyph outlines in non-trivial ways
 * and previously exposed a bug where the cap geometry was applied twice.
 *
 * Uses `ToolUtils::DefaultPortableTypeface()` at size 100 — the same
 * portable font used across the test suite for cross-platform reproducibility.
 *
 * Reference image: `dashtextcaps.png`, 512 × 512, default white BG.
 */
public class DashTextCapsGM : GM() {

    override fun getName(): String = "dashtextcaps"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 10f
            strokeCap = SkPaint.Cap.kRound_Cap
            strokeJoin = SkPaint.Join.kRound_Join
            setARGB(0xff, 0xbb, 0x00, 0x00)
            pathEffect = SkDashPathEffect.Make(floatArrayOf(12f, 12f), 0f)
        }

        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 100f)

        c.drawString("Sausages", 10f, 90f, font, paint)
        c.drawLine(8f, 120f, 456f, 120f, paint)
    }
}
