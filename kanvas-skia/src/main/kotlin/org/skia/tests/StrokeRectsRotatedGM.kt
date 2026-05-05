package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/strokerects.cpp:StrokeRectsGM(true)` — the rotated
 * variant of the existing [StrokeRectsGM]. Same iteration (2 × 2 panes,
 * 100 random rects each, AA × strokeWidth combos) under a `rotate(45,
 * SW, SH)` applied at the canvas root before drawing.
 *
 * Reference image: `strokerects_rotated.png`, 800 × 800.
 */
public class StrokeRectsRotatedGM : GM() {

    override fun getName(): String = "strokerects_rotated"
    override fun getISize(): SkISize = SkISize.Make(W * 2, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.rotate(45f, SW.toFloat(), SH.toFloat())

        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }

        for (y in 0 until 2) {
            paint.isAntiAlias = (y != 0)
            for (x in 0 until 2) {
                paint.strokeWidth = (x * 3).toFloat()

                c.save()
                c.translate((SW * x).toFloat(), (SH * y).toFloat())
                c.clipRect(SkRect.MakeLTRB(2f, 2f, SW - 2f, SH - 2f))

                val rand = SkRandom()
                for (i in 0 until N) {
                    val r = rndRect(rand)
                    c.drawRect(r, paint)
                }
                c.restore()
            }
        }
    }

    private fun rndRect(rand: SkRandom): SkRect {
        val x = rand.nextUScalar1() * W
        val y = rand.nextUScalar1() * H
        val w = rand.nextUScalar1() * (W shr 2)
        val h = rand.nextUScalar1() * (H shr 2)
        val hoffset = rand.nextSScalar1()
        val woffset = rand.nextSScalar1()

        val r = SkRect.MakeXYWH(x, y, w, h)
        r.offset(-w / 2f + woffset, -h / 2f + hoffset)
        return r
    }

    private companion object {
        const val W: Int = 400
        const val H: Int = 400
        const val N: Int = 100
        const val SW: Int = 400
        const val SH: Int = 400
    }
}
