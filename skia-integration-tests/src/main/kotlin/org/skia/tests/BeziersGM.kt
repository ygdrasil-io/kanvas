package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkScalar
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/beziers.cpp` (`BeziersGM`, GM name `beziers`).
 *
 * Two stacked panes of `N = 10` random AA-stroked Bezier paths each:
 *  - top pane : `moveTo` + 2 × `quadTo` per path;
 *  - bottom pane : `moveTo` + 2 × `cubicTo` per path.
 *
 * Each path consumes a deterministic stream of `SkRandom` floats — bit
 * compatible with upstream so geometry and colors match. `paint.color`
 * is mutated per path to a random opaque RGB; `strokeWidth` is the
 * square of `nextRangeScalar(1, 5)` (so widths span ~1 → 25 px).
 *
 * Reference image: `beziers.png`, 400 × 800.
 *
 * Stresses :
 *  - Quad and cubic stroker on long, asymmetric AA segments;
 *  - per-path strokeWidth/color updates between draws (no shared state
 *    other than the rand state, so this is also a smoke test for the
 *    SkRandom port);
 *  - the AA rasterizer's response to wide-Bezier coverage, the same
 *    code path Hairlines and several Strokes* GMs share.
 */
public class BeziersGM : GM() {

    override fun getName(): String = "beziers"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 9f / 2f          // SkIntToScalar(9) / 2
            isAntiAlias = true
        }

        val rand = SkRandom()
        for (i in 0 until N) {
            c.drawPath(rndQuad(paint, rand), paint)
        }
        c.translate(0f, SH)
        for (i in 0 until N) {
            c.drawPath(rndCubic(paint, rand), paint)
        }
    }

    /** Mirrors upstream's `rnd_quad(paint, rand)` from `gm/beziers.cpp`. */
    private fun rndQuad(paint: SkPaint, rand: SkRandom): org.skia.foundation.SkPath {
        val a = rand.nextRangeScalar(0f, W.toFloat())
        val b = rand.nextRangeScalar(0f, H.toFloat())

        val builder = SkPathBuilder()
        builder.moveTo(a, b)
        for (x in 0 until 2) {
            val cc = rand.nextRangeScalar(W / 4f, W.toFloat())
            val d = rand.nextRangeScalar(0f, H.toFloat())
            val e = rand.nextRangeScalar(0f, W.toFloat())
            val f = rand.nextRangeScalar(H / 4f, H.toFloat())
            builder.quadTo(cc, d, e, f)
        }
        // setColor(rand.nextU()); setAlphaf(1.0f) — force fully opaque.
        val c32 = rand.nextU()
        paint.color = (c32 and 0x00FFFFFF) or 0xFF000000.toInt()
        var width = rand.nextRangeScalar(1f, 5f)
        width *= width
        paint.strokeWidth = width
        return builder.detach()
    }

    /** Mirrors upstream's `rnd_cubic(paint, rand)` from `gm/beziers.cpp`. */
    private fun rndCubic(paint: SkPaint, rand: SkRandom): org.skia.foundation.SkPath {
        val a = rand.nextRangeScalar(0f, W.toFloat())
        val b = rand.nextRangeScalar(0f, H.toFloat())

        val builder = SkPathBuilder()
        builder.moveTo(a, b)
        for (x in 0 until 2) {
            val cc = rand.nextRangeScalar(W / 4f, W.toFloat())
            val d = rand.nextRangeScalar(0f, H.toFloat())
            val e = rand.nextRangeScalar(0f, W.toFloat())
            val f = rand.nextRangeScalar(H / 4f, H.toFloat())
            val g = rand.nextRangeScalar(W / 4f, W.toFloat())
            val h = rand.nextRangeScalar(H / 4f, H.toFloat())
            builder.cubicTo(cc, d, e, f, g, h)
        }
        val c32 = rand.nextU()
        paint.color = (c32 and 0x00FFFFFF) or 0xFF000000.toInt()
        var width = rand.nextRangeScalar(1f, 5f)
        width *= width
        paint.strokeWidth = width
        return builder.detach()
    }

    private companion object {
        // From upstream `gm/beziers.cpp`.
        const val W: Int = 400
        const val H: Int = 400
        const val N: Int = 10
        const val SH: SkScalar = 400f
    }
}
