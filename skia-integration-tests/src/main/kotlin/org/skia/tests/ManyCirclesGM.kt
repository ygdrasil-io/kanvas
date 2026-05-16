package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/manypaths.cpp:ManyCirclesGM`.
 *
 * Stress test for the AA oval rasterizer: ten thousand random ovals scattered
 * over an 800 × 600 canvas, each with a deterministic 565-quantised HSV-ish
 * colour. The original GM was a Ganesh regression for crbug.com/688582
 * (more circles than fit in a single index buffer) — for our raster pipeline
 * it's a simple brute-force coverage pile.
 *
 * Reference image: `manycircles.png`, 800 × 600, white BG.
 *
 * Numerically reproducible because:
 *  - [SkRandom] is bit-compatible with upstream;
 *  - the colour generator is the same `gen_color` helper as
 *    `gm/manypaths.cpp` (`HSV` with `nextRangeF` ranges 0.5–1.0 for S and V,
 *    quantised through 565).
 */
public class ManyCirclesGM : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = "manycircles"
    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rand = SkRandom(1)
        val paint = SkPaint().apply { isAntiAlias = true }
        var total = 10_000
        while (total-- > 0) {
            val x = rand.nextF() * kWidth - 100f
            val y = rand.nextF() * kHeight - 100f
            val w = rand.nextF() * 200f
            val circle = SkRect.MakeXYWH(x, y, w, w)
            paint.color = genColor(rand)
            c.drawOval(circle, paint)
        }
    }

    /**
     * Mirrors `gen_color(SkRandom*)` from `gm/manypaths.cpp`. HSV in
     * `[0,360) × [0.5,1] × [0.5,1]`, then HSV→RGB via Skia's algorithm,
     * then [ToolUtils.colorTo565] to round-trip through 16-bit RGB565
     * quantisation (matches the 8888-from-565 reference encoding).
     */
    private fun genColor(rand: SkRandom): Int {
        val hsv = floatArrayOf(
            rand.nextRangeF(0.0f, 360.0f),
            rand.nextRangeF(0.5f, 1.0f),
            rand.nextRangeF(0.5f, 1.0f),
        )
        return ToolUtils.colorTo565(ToolUtils.skHSVToColor(hsv))
    }

    private companion object {
        const val kWidth: Int = 800
        const val kHeight: Int = 600
    }
}
