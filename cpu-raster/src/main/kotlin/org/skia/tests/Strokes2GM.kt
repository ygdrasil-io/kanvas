package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/strokes.cpp:Strokes2GM`.
 *
 * Twenty-five stroked 13-segment polylines per pane (AA off / AA on),
 * each rotated cumulatively by 15° around the pane centre `(W/2, H/2)`.
 * The per-iteration `rnd_rect` is consumed for its colour side-effect;
 * the rect bounds themselves are unused.
 *
 * The path itself is built once in `onOnceBeforeDraw` from a default-seeded
 * [SkRandom] (13 random `lineTo`s starting at the origin), so all 50
 * draws share the same geometry; only the cumulative rotation and the
 * randomized stroke colour vary.
 *
 * Reference image: `strokes_poly.png`, 400 × 800, white BG.
 *
 * Stresses [SkCanvas.rotate]`(deg, px, py)` (the new pivot-rotation
 * overload) under both AA and non-AA stroke rasterization.
 */
public class Strokes2GM : GM() {

    private lateinit var fPath: SkPath

    override fun getName(): String = "strokes_poly"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onOnceBeforeDraw() {
        val rand = SkRandom()
        val b = SkPathBuilder().moveTo(0f, 0f)
        for (i in 0 until 13) {
            val x = rand.nextUScalar1() * (W shr 1)
            val y = rand.nextUScalar1() * (H shr 1)
            b.lineTo(x, y)
        }
        fPath = b.detach()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Upstream drawColor(SK_ColorWHITE) is redundant — the harness
        // pre-fills with white. Skipping; matches reference.

        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 9f / 2f      // SkIntToScalar(9) / 2
        }

        for (y in 0 until 2) {
            paint.isAntiAlias = (y != 0)
            c.save()
            c.translate(0f, SH * y)
            c.clipRect(SkRect.MakeLTRB(2f, 2f, SW - 2f, SH - 2f))

            val rand = SkRandom()
            for (i in 0 until N / 2) {
                rndRect(paint, rand)              // mutates paint.color, rect discarded
                c.rotate(15f, SW / 2f, SH / 2f)
                c.drawPath(fPath, paint)
            }
            c.restore()
        }
    }

    /** Mirrors `gm/strokes.cpp:rnd_rect`. We only use the colour side-effect here. */
    private fun rndRect(paint: SkPaint, rand: SkRandom): SkRect {
        val x = rand.nextUScalar1() * W
        val y = rand.nextUScalar1() * H
        val w = rand.nextUScalar1() * (W shr 2)
        val h = rand.nextUScalar1() * (H shr 2)
        val hoffset = rand.nextSScalar1()
        val woffset = rand.nextSScalar1()

        val r = SkRect.MakeXYWH(x, y, w, h)
        r.offset(-w / 2f + woffset, -h / 2f + hoffset)

        // setColor(rand.nextU()) ; setAlphaf(1) — random RGB, alpha forced to 0xFF.
        val c32 = rand.nextU()
        paint.color = (c32 and 0x00FFFFFF) or 0xFF000000.toInt()
        return r
    }

    private companion object {
        const val W: Int = 400
        const val H: Int = 400
        const val N: Int = 50
        const val SW: Float = 400f
        const val SH: Float = 400f
    }
}
