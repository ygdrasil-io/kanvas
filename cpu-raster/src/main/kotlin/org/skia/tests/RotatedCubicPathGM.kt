package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/pathfill.cpp` `DEF_SIMPLE_GM(rotatedcubicpath, …)`.
 *
 * Two stacked cubic-only closed paths — one drawn axis-aligned in blue,
 * one drawn rotated 90° in red. The rotated path tests the GPU's
 * AA-on-rotated-cubic-fill code path; for our raster backend it's a
 * basic regression for `rotate` interaction with cubic flattening.
 *
 * Reference image: `rotatedcubicpath.png`, 200 × 200, default white BG.
 */
public class RotatedCubicPathGM : GM() {

    override fun getName(): String = "rotatedcubicpath"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }

        c.translate(50f, 50f)
        val path = SkPathBuilder()
            .moveTo(48f, -23f)
            .cubicTo(48f, -29.5f, 6f, -30f, 6f, -30f)
            .cubicTo(6f, -30f, 2f, 0f, 2f, 0f)
            .cubicTo(2f, 0f, 44f, -21.5f, 48f, -23f)
            .close()
            .detach()

        p.color = 0xFF0000FF.toInt()      // SK_ColorBLUE
        c.drawPath(path, p)

        // Rotated path — same geometry, but 90° CW under our CTM.
        p.color = 0xFFFF0000.toInt()      // SK_ColorRED
        c.rotate(90f)
        c.drawPath(path, p)
    }
}
