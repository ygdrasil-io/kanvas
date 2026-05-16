package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorGREEN
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/scaledrects.cpp::cliplargerect` (DEF_SIMPLE_GM,
 * 256 × 256).
 *
 * Stress test for `clipRect` interaction with a giant translate
 * (`1e24f` !). The outer clip narrows to `(0, 0, 120, 256)`, then a
 * nested `translate(1e24, 0)` + `clear(GREEN)` would (incorrectly)
 * paint outside the clip bounds — the clipRect must dominate. Final
 * black hairline at `x = 120` shows the clip boundary.
 *
 * `clipIRect` upstream → we use `clipRect(SkRect.MakeLTRB(0, 0, 120,
 * 256))` (same effect since the integer rect is already pixel-aligned).
 */
public class ClipLargeRectGM : GM() {

    override fun getName(): String = "cliplargerect"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.save()
        c.clipRect(SkRect.MakeLTRB(0f, 0f, 120f, 256f))
        c.save()
        c.translate(1e24f, 0f)
        c.clear(SK_ColorGREEN)
        c.restore()
        c.restore()

        val line = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            color = SK_ColorBLACK
        }
        c.drawLine(120f, 0f, 120f, 256f, line)
    }
}
