package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector

/**
 * Port of Skia's `gm/drrect.cpp` (`DRRectGM`).
 *
 * A 4 × 5 grid of double-rounded-rectangles (donuts) mixing every
 * [SkRRect.Type] for the outer (rect / oval / simple / complex) and the
 * inner (empty / rect / oval / simple / complex). Each cell calls
 * [SkCanvas.drawDRRect], which builds a single path with the outer ring CW
 * and the inner ring CCW so the default `kWinding` fill paints the band
 * between them.
 *
 * Reference image: `drrect.png`, 640 × 480, default white BG.
 */
public class DRRectGM : GM() {

    override fun getName(): String = "drrect"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }

        var r = SkRect.MakeLTRB(0f, 0f, 100f, 100f)
        val radii = arrayOf(
            SkVector(0f, 0f),
            SkVector(30f, 1f),
            SkVector(10f, 40f),
            SkVector(40f, 40f),
        )
        val dx = r.width() + 16f
        val dy = r.height() + 16f

        // Outer rrects: rect / oval / simple / complex.
        val outers = Array(4) { SkRRect() }
        outers[0].setRect(r)
        outers[1].setOval(r)
        outers[2].setRectXY(r, 20f, 20f)
        outers[3].setRectRadii(r, radii)

        // Inset r by 25 px on every side for the inner rrects.
        r = SkRect.MakeLTRB(r.left + 25f, r.top + 25f,
                            r.right - 25f, r.bottom - 25f)

        // Inner rrects: empty / rect / oval / simple / complex.
        val inners = Array(5) { SkRRect() }
        inners[0].setEmpty()
        inners[1].setRect(r)
        inners[2].setOval(r)
        inners[3].setRectXY(r, 20f, 20f)
        inners[4].setRectRadii(r, radii)

        c.translate(16f, 16f)
        for (j in inners.indices) {
            for (i in outers.indices) {
                c.save()
                c.translate(dx * j, dy * i)
                c.drawDRRect(outers[i], inners[j], paint)
                c.restore()
            }
        }
    }
}
