package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRegion
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/drawregion.cpp` (`DrawRegionGM`, GM name
 * `drawregion`).
 *
 * Builds a 100 × 100 region of 1×1 pixels at every other position
 * (a regular dotted pattern) inside `(50, 50, 250, 250)`, draws it on
 * top of a magenta backing rect through [SkCanvas.drawRegion]. The
 * GM stresses [drawRegion] over a region that decomposes into ~10 000
 * tiny rectangles.
 *
 * Reference image: `drawregion.png`, 500 × 500.
 */
public class DrawRegionGM : GM() {

    override fun getName(): String = "drawregion"
    override fun getISize(): SkISize = SkISize.Make(500, 500)

    private val fRegion: SkRegion = SkRegion()

    override fun onOnceBeforeDraw() {
        var x = 50
        while (x < 250) {
            var y = 50
            while (y < 250) {
                fRegion.op(SkIRect(x, y, x + 1, y + 1), SkRegion.Op.kUnion)
                y += 2
            }
            x += 2
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 10f)

        val paint = SkPaint().apply {
            style = SkPaint.Style.kFill_Style
            color = 0xFFFF00FF.toInt()
        }
        c.drawRect(SkRect.MakeLTRB(50f, 50f, 250f, 250f), paint)

        paint.color = 0xFF00FFFF.toInt()
        c.drawRegion(fRegion, paint)
    }
}
