package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/drrect_small_inner.cpp::drrect_small_inner`
 * (DEF_SIMPLE_GM, 170 × 610).
 *
 * Exercises `drawDRRect(outer, inner)` where the **inner** rounded-rect
 * shrinks to sub-pixel sizes (1 px → 0.01 px), tested on/off-centre
 * and as oval-vs-circle. The outer is a large oval (`r = 35`). 16 cells
 * stacked vertically, 2 columns wide :
 *   - column 0 : centred inner.
 *   - column 1 : `+1 px` X-offset inner.
 * Originally a regression test for a tessellator divide-by-zero on
 * vanishing inner radii.
 */
public class DRRectSmallInnerGM : GM() {

    override fun getName(): String = "drrect_small_inner"
    override fun getISize(): SkISize = SkISize.Make(170, 610)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }
        val outerRadius = 35f
        val outer = SkRRect.MakeOval(SkRect.MakeXYWH(0f, 0f, 2f * outerRadius, 2f * outerRadius))

        c.translate(10f, 10f)
        c.save()
        for (offcenter in arrayOf(false, true)) {
            for (oval in arrayOf(false, true)) {
                for (innerRadiusX in floatArrayOf(1f, 0.5f, 0.1f, 0.01f)) {
                    val innerRadiusY = if (oval) innerRadiusX * 0.95f else innerRadiusX
                    var tx = outerRadius - innerRadiusX
                    val ty = outerRadius - innerRadiusY
                    if (offcenter) tx += 1f
                    val inner = SkRRect.MakeOval(
                        SkRect.MakeXYWH(tx, ty, 2f * innerRadiusX, 2f * innerRadiusY)
                    )
                    c.drawDRRect(outer, inner, paint)
                    c.translate(0f, 2f * outerRadius + 5f)
                }
            }
            // Mirror upstream's unbalanced `restore()` — the first
            // iteration pops the save, the second is a no-op (stack at
            // root). The trailing translate then shifts to column 1.
            c.restore()
            c.translate(2f * outerRadius + 2f, 0f)
        }
        c.restore()
    }
}
