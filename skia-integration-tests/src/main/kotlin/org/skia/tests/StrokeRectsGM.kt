package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/strokerects.cpp:StrokeRectsGM(false)` — the
 * non-rotated variant (the rotated variant is a separate GM that we
 * skip here; it just adds `canvas.rotate(45, SW, SH)` at the start).
 *
 * 2 × 2 panes (AA off / AA on × strokeWidth 0 / 3) of `N = 100` random
 * stroked rects each, drawn under a 2-pixel inset clipRect. Each pane
 * uses a fresh default-seeded [SkRandom] so the same rects are drawn
 * across all 4 panes — what differs is the AA mode and the stroke
 * width.
 *
 * Reference image: `strokerects.png`, 800 × 800, default white BG.
 *
 * Bonus harvest — was always portable (no rotate / skew / shader needs)
 * but never landed before. Stresses the AA + non-AA stroked-rect
 * rasterizer on `100 × 4 = 400` random rects, including degenerate
 * negative-extent rects from the offset arithmetic.
 */
public class StrokeRectsGM : GM() {

    override fun getName(): String = "strokerects"
    override fun getISize(): SkISize = SkISize.Make(W * 2, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }

        for (y in 0 until 2) {
            paint.isAntiAlias = (y != 0)
            for (x in 0 until 2) {
                paint.strokeWidth = (x * 3).toFloat()

                // Iso with upstream `SkAutoCanvasRestore acr(canvas, true);`.
                c.withSave {
                    translate((SW * x).toFloat(), (SH * y).toFloat())
                    clipRect(SkRect.MakeLTRB(2f, 2f, SW - 2f, SH - 2f))

                    val rand = SkRandom()
                    for (i in 0 until N) {
                        val r = rndRect(rand)
                        drawRect(r, paint)
                    }
                }
            }
        }
    }

    /** Mirrors upstream's `rnd_rect`. */
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
