package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/skbug_4868.cpp::skbug_4868` (DEF_SIMPLE_GM, 32 × 32).
 *
 * Tiny regression test : `clipRect` and `drawLine` should align exactly
 * when both consume the same point, even after a large translate that
 * pushes the user-space coordinates near the float-precision limit
 * (~5995 px). Originally caught a SkPDF rounding bug.
 *
 * Steps : `translate(-68, -3378)`, `scale(0.567, 0.567)`, `clipRect(rc)`,
 * `clear(0xFFCECFCE)`, two AA-stroked diagonals across `rc`.
 */
public class Skbug4868GM : GM() {

    override fun getName(): String = "skbug_4868"
    override fun getISize(): SkISize = SkISize.Make(32, 32)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(-68f, -3378f)
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
        }
        c.scale(0.56692914f, 0.56692914f)
        val rc = SkRect.MakeLTRB(158f, 5994.80273f, 165f, 5998.80225f)
        c.clipRect(rc)
        c.clear(0xFFCECFCE.toInt())
        c.drawLine(rc.left, rc.top, rc.right, rc.bottom, paint)
        c.drawLine(rc.right, rc.top, rc.left, rc.bottom, paint)
    }
}
