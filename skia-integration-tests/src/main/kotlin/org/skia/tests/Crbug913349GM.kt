package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/crbug_913349.cpp` (`DEF_SIMPLE_GM(crbug_913349, ...)`).
 *
 * Reduced from a Chromium polygon-fill bug. A 5-vertex line-only contour
 * with one near-zero-area sliver, filled with AA + the default `kWinding`
 * rule.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_913349, canvas, 500, 600) {
 *     SkPaint paint;
 *
 *     paint.setAntiAlias(true);
 *     paint.setStyle(SkPaint::kFill_Style);
 *
 *     // This is a reduction from crbug.com/913349 to 5 verts.
 *     SkPathBuilder path;
 *     path.moveTo( 349.5,  225.75);
 *     path.lineTo(  96.5,   74);
 *     path.lineTo( 500.50, 226);
 *     path.lineTo( 350,    226);
 *     path.lineTo( 350,    224);
 *
 *     canvas->drawPath(path.detach(), paint);
 * }
 * ```
 */
public class Crbug913349GM : GM() {
    override fun getName(): String = "crbug_913349"
    override fun getISize(): SkISize = SkISize.Make(500, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }
        val path = SkPathBuilder()
            .moveTo(349.5f, 225.75f)
            .lineTo(96.5f, 74f)
            .lineTo(500.50f, 226f)
            .lineTo(350f, 226f)
            .lineTo(350f, 224f)
            .detach()
        c.drawPath(path, paint)
    }
}
