package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/crbug_884166.cpp` (`DEF_SIMPLE_GM(crbug_884166, ...)`).
 *
 * Reduced from a Chromium polygon-fill bug. A single 8-vertex line-only
 * contour with a near-vertical sliver is filled with AA + the default
 * `kWinding` rule.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_884166, canvas, 300, 300) {
 *     SkPaint paint;
 *
 *     paint.setAntiAlias(true);
 *     paint.setStyle(SkPaint::kFill_Style);
 *
 *     SkPathBuilder path;
 *     path.moveTo(153.25, 280.75);
 *     path.lineTo(161.75, 281.75);
 *     path.lineTo(164.25, 282.00);
 *     path.lineTo(  0.00, 276.00);
 *     path.lineTo(161.50,   0.00);
 *     path.lineTo(286.25, 231.25);
 *     path.lineTo(163.75, 282.00);
 *     path.lineTo(150.00, 280.00);
 *     canvas->drawPath(path.detach(), paint);
 * }
 * ```
 */
public class Crbug884166GM : GM() {
    override fun getName(): String = "crbug_884166"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }
        val path = SkPathBuilder()
            .moveTo(153.25f, 280.75f)
            .lineTo(161.75f, 281.75f)
            .lineTo(164.25f, 282.00f)
            .lineTo(  0.00f, 276.00f)
            .lineTo(161.50f,   0.00f)
            .lineTo(286.25f, 231.25f)
            .lineTo(163.75f, 282.00f)
            .lineTo(150.00f, 280.00f)
            .detach()
        c.drawPath(path, paint)
    }
}
