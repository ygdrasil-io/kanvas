package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/crbug_887103.cpp` (`DEF_SIMPLE_GM(crbug_887103, ...)`).
 *
 * Reduced from a Chromium triangle-rasterization bug. Three coincident
 * triangles formed entirely of `moveTo` + `lineTo` verbs are filled with
 * the default `kWinding` rule and `paint.isAntiAlias = true`.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_887103, canvas, 520, 520) {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setStyle(SkPaint::kFill_Style);
 *
 *     SkPathBuilder path;
 *     path.moveTo(510,  20);
 *     path.lineTo(500,  20);
 *     path.lineTo(510, 500);
 *
 *     path.moveTo(500,  20);
 *     path.lineTo(510, 500);
 *     path.lineTo(500, 510);
 *
 *     path.moveTo(500,  30);
 *     path.lineTo(510,  10);
 *     path.lineTo( 10,  30);
 *     canvas->drawPath(path.detach(), paint);
 * }
 * ```
 */
public class Crbug887103GM : GM() {
    override fun getName(): String = "crbug_887103"
    override fun getISize(): SkISize = SkISize.Make(520, 520)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }
        val path = SkPathBuilder()
            .moveTo(510f, 20f)
            .lineTo(500f, 20f)
            .lineTo(510f, 500f)
            .moveTo(500f, 20f)
            .lineTo(510f, 500f)
            .lineTo(500f, 510f)
            .moveTo(500f, 30f)
            .lineTo(510f, 10f)
            .lineTo(10f, 30f)
            .detach()
        c.drawPath(path, paint)
    }
}
