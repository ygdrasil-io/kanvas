package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/strokes.cpp` (`StrokesGM`, GM name `strokes_round`).
 *
 * Two horizontal panes (AA off / AA on) of `N = 50` random stroked oval
 * + roundrect pairs, drawn under a per-pane `clipRect` border of
 * 2 pixels and a `kRound_Cap` + `kMiter_Join` stroke (paint defaults).
 * Each random rect is generated through the `rnd_rect` helper —
 * an `SkRandom` consumes 6 calls per rect (`x`, `y`, `w`, `h`,
 * `hoffset`, `woffset`) plus 1 for the colour. Three rects are pulled
 * per loop iteration but only two are drawn (oval + roundrect — the
 * third advances the rand state to match upstream).
 *
 * Reference image: `strokes_round.png`, 400 × 800.
 *
 * Stresses:
 *  - `clipRect` against ovals/roundrects that may straddle the clip;
 *  - `drawOval` and `drawRoundRect` rasterization (cubic Béziers per
 *    quadrant for ovals; rect-with-corner-arcs for rrects);
 *  - the AA on/off split runs the whole pipeline twice with different
 *    rasterizer modes (analytic AA vs hard-edge), sharing one
 *    rand-driven geometry source.
 */
public class StrokesGM : GM() {

    override fun getName(): String = "strokes_round"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 9f / 2f          // SkIntToScalar(9) / 2
        }

        for (y in 0 until 2) {
            paint.isAntiAlias = (y != 0)
            c.save()
            c.translate(0f, SH * y)
            // Inset 2 px on every side, matching upstream.
            c.clipRect(SkRect.MakeLTRB(2f, 2f, SW - 2f, SH - 2f))

            val rand = SkRandom()
            for (i in 0 until N) {
                var r = rndRect(paint, rand)
                c.drawOval(r, paint)
                r = rndRect(paint, rand)
                c.drawRoundRect(r, r.width() / 4f, r.height() / 4f, paint)
                rndRect(paint, rand)   // discarded — advances rand state
            }

            c.restore()
        }
    }

    /**
     * Mirrors upstream's `rnd_rect(SkRect*, SkPaint*, SkRandom&)` from
     * `gm/strokes.cpp`. Mutates `paint`'s colour as a side effect, then
     * returns a freshly constructed offset rect.
     */
    private fun rndRect(paint: SkPaint, rand: SkRandom): SkRect {
        val x = rand.nextUScalar1() * W
        val y = rand.nextUScalar1() * H
        val w = rand.nextUScalar1() * (W shr 2)
        val h = rand.nextUScalar1() * (H shr 2)
        val hoffset = rand.nextSScalar1()
        val woffset = rand.nextSScalar1()

        val r = SkRect.MakeXYWH(x, y, w, h)
        r.offset(-w / 2f + woffset, -h / 2f + hoffset)

        // Mirrors `paint.setColor(rand.nextU()); paint.setAlphaf(1.0f);` —
        // forces alpha to 0xFF, leaves the random RGB.
        val c32 = rand.nextU()
        paint.color = (c32 and 0x00FFFFFF) or 0xFF000000.toInt()
        return r
    }

    private companion object {
        // From upstream `gm/strokes.cpp`.
        private const val W: Int = 400
        private const val H: Int = 400
        private const val N: Int = 50
        private const val SW: SkScalar = 400f
        private const val SH: SkScalar = 400f
    }
}
