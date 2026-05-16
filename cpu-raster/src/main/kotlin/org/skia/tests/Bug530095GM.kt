package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/bug530095.cpp::bug530095` (900 × 1200).
 *
 * Stresses [SkDashPathEffect] under extreme intervals + matrix scales :
 *
 *  - Circle r=124 at (200,200), stroke 26, dash `[700, 700]` phase -40.
 *  - Same shape at 1/100 scale (circle r=1.24 at (2,2), stroke 0.26,
 *    dash `[7, 7]` phase -0.40), drawn under a `scale(100, 100)` CTM at
 *    `translate(4, 0)`.
 *  - The two configurations repeat with phase=0 in a second column at
 *    `translate(0, 400)`.
 *
 * Validates that the dasher correctly handles giant intervals (700 ≫
 * the circle's perimeter), negative phases, and tiny intervals under
 * 100× CTM zoom — both should rasterize identically modulo CTM.
 */
public class Bug530095GM : GM() {

    override fun getName(): String = "bug530095"
    override fun getISize(): SkISize = SkISize.Make(900, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path1 = SkPath.Circle(200f, 200f, 124f)
        val path2 = SkPath.Circle(2f, 2f, 1.24f)

        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 26f
        }
        val intervals = floatArrayOf(700f, 700f)
        paint.pathEffect = SkDashPathEffect.Make(intervals, -40f)
        c.drawPath(path1, paint)

        paint.strokeWidth = 0.26f
        val smIntervals = floatArrayOf(7f, 7f)
        paint.pathEffect = SkDashPathEffect.Make(smIntervals, -0.40f)
        c.save()
        c.scale(100f, 100f)
        c.translate(4f, 0f)
        c.drawPath(path2, paint)
        c.restore()

        paint.strokeWidth = 26f
        paint.pathEffect = SkDashPathEffect.Make(intervals, 0f)
        c.save()
        c.translate(0f, 400f)
        c.drawPath(path1, paint)
        c.restore()

        paint.strokeWidth = 0.26f
        paint.pathEffect = SkDashPathEffect.Make(smIntervals, 0f)
        c.scale(100f, 100f)
        c.translate(4f, 4f)
        c.drawPath(path2, paint)
    }
}
