package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/crbug_946965.cpp::crbug_946965` (DEF_SIMPLE_GM,
 * 75 × 150).
 *
 * Exposed a bug in elliptical RRect rendering : per-corner radii were
 * mapped wrong under 90° rotation. Drawn twice (filled then stroked) so
 * the regression is visible on both fill and stroke paths.
 *
 * `translate(25, 80)`, `rotate(90)`, `scale(1.5, 1)`, then `drawRRect`
 * (filled) ; offset up by `-20` and `drawRRect` (stroke=3).
 */
public class Crbug946965GM : GM() {

    override fun getName(): String = "crbug_946965"
    override fun getISize(): SkISize = SkISize.Make(75, 150)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        c.translate(25f, 80f)
        c.rotate(90f)
        c.scale(1.5f, 1f)
        val rrect = SkRRect.MakeRectXY(SkRect.MakeLTRB(-20f, -5f, 20f, 5f), 10f, 10f)
        c.drawRRect(rrect, paint)
        c.translate(0f, -20f)
        paint.strokeWidth = 3f
        paint.style = SkPaint.Style.kStroke_Style
        c.drawRRect(rrect, paint)
    }
}
