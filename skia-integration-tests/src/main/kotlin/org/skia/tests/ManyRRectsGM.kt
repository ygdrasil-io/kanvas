package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/manypaths.cpp:ManyRRectsGM`.
 *
 * Seven thousand identical small rrects (4 × 4 with `(rx, ry) = (1, 1)`)
 * blue-filled with AA, laid out on a 5-px grid that wraps every 700 px.
 * Originally a Ganesh stress test for crbug.com/684112 (more rrects than
 * fit in a single index buffer) — for our raster pipeline it's a hot loop
 * over `addRRect` + scanline fill, with the rrect built once and translated
 * into position per iteration.
 *
 * Reference image: `manyrrects.png`, 800 × 300, white BG.
 */
public class ManyRRectsGM : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = "manyrrects"
    override fun getISize(): SkISize = SkISize.Make(800, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = 0xFF0000FF.toInt()    // SK_ColorBLUE
        }

        // Rectangle positioning variables.
        var x = 0
        var y = 0
        val kXLimit = 700
        val kYIncrement = 5
        val kXIncrement = 5

        val rect = SkRect.MakeLTRB(0f, 0f, 4f, 4f)
        val rrect = SkRRect.MakeRectXY(rect, 1f, 1f)
        var total = 7_000
        while (total-- > 0) {
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            c.drawRRect(rrect, paint)
            x += kXIncrement
            if (x > kXLimit) {
                x = 0
                y += kYIncrement
            }
            c.restore()
        }
    }
}
