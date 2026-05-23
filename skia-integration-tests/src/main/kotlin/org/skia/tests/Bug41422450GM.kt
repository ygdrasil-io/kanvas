package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkM44
import org.graphiks.math.SkRect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder

/**
 * Port of Skia's `gm/bug12866.cpp::bug41422450`
 * (`DEF_SIMPLE_GM_BG(bug41422450, canvas, 863, 473, SK_ColorWHITE)`).
 *
 * Regression test for conics hitting the stroker recursion limit : the
 * path is a large off-screen arc driven through a strong perspective CTM.
 * When incorrect the stroker emitted a large black rectangle covering half
 * the slide. The correct output is an entirely **blank white canvas** —
 * the arc is invisible once projected.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM_BG(bug41422450, canvas, 863, 473, SK_ColorWHITE) {
 *     SkM44 mat{1, -0.00000139566271f, 0, -2321738,
 *               0.000113059919f, 0.0123444516f, 0, -353,
 *               0, 0, 1, 0,
 *               0, 0, 0, 1};
 *     canvas->concat(mat);
 *     SkRect circle = SkRect::MakeLTRB(-3299135.5f, -12312541.0f, 9897407.0f, 884000.812f);
 *     SkPath strokePath = SkPathBuilder().arcTo(circle, 59.9999962f, 59.9999962f, true).detach();
 *     SkPaint strokePaint;
 *     strokePaint.setStyle(SkPaint::kStroke_Style);
 *     strokePaint.setStrokeWidth(2);
 *     canvas->drawPath(strokePath, strokePaint);
 * }
 * ```
 */
public class Bug41422450GM : GM() {

    init { setBGColor(SK_ColorWHITE) }

    override fun getName(): String = "bug41422450"
    override fun getISize(): SkISize = SkISize.Make(863, 473)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Perspective matrix — row-major constructor (m0 m4 m8 m12 / m1 m5 m9 m13 / …)
        val mat = SkM44(
            1f, -0.00000139566271f, 0f, -2321738f,
            0.000113059919f, 0.0123444516f, 0f, -353f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        c.concat(mat)

        val circle = SkRect.MakeLTRB(-3299135.5f, -12312541.0f, 9897407.0f, 884000.812f)
        val strokePath = SkPathBuilder().arcTo(circle, 59.9999962f, 59.9999962f, true).detach()

        val strokePaint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 2f
        }
        c.drawPath(strokePath, strokePaint)
    }
}
