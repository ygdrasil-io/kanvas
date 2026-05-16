package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/crbug_908646.cpp` (`DEF_SIMPLE_GM(crbug_908646, ...)`).
 *
 * Reduced from a Chromium fill-rule bug. Two contours: an outer 4-vertex
 * line-only square and two interior triangles, filled with `kEvenOdd`. The
 * even-odd rule should leave the triangles as holes inside the square.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_908646, canvas, 300, 300) {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     SkPathBuilder path;
 *     path.setFillType(SkPathFillType::kEvenOdd);
 *     path.moveTo(50,  50);
 *     path.lineTo(50,  300);
 *     path.lineTo(250, 300);
 *     path.lineTo(250, 50);
 *     path.moveTo(200, 100);
 *     path.lineTo(100, 100);
 *     path.lineTo(150, 200);
 *     path.moveTo(100, 250);
 *     path.lineTo(150, 150);
 *     path.lineTo(200, 250);
 *     canvas->drawPath(path.detach(), paint);
 * }
 * ```
 */
public class Crbug908646GM : GM() {
    override fun getName(): String = "crbug_908646"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .moveTo(50f, 50f)
            .lineTo(50f, 300f)
            .lineTo(250f, 300f)
            .lineTo(250f, 50f)
            .moveTo(200f, 100f)
            .lineTo(100f, 100f)
            .lineTo(150f, 200f)
            .moveTo(100f, 250f)
            .lineTo(150f, 150f)
            .lineTo(200f, 250f)
            .detach()
        c.drawPath(path, paint)
    }
}
