package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRegion
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/clip_sierpinski_region.cpp::clip_sierpinski_region`
 * (243 + 2·10 = 263 px square).
 *
 * Builds a 4-level Sierpinski-carpet **complement** region by unioning
 * 1 + 9 + 81 + 729 → 820 axis-aligned rectangles into an [SkRegion],
 * translates it by `(kTrans, kTrans)`, then :
 *  - opens a `saveLayer` with the layer origin offset by `(kTrans, kTrans)`
 *    (validates that the layer bookkeeping survives the offset) ;
 *  - rotates the canvas 25° about `(50, 50)` (validates that
 *    [SkCanvas.clipRegion] is **CTM-invariant** — the clip snaps to the
 *    pre-rotation device pixels regardless of the rotation) ;
 *  - calls `clipRegion(region)` to bind the carpet shape ;
 *  - draws a full-canvas red `drawPaint` — only the clip-uncovered pixels
 *    bleed through to the output.
 *
 * The result is a red Sierpinski-carpet-shaped print on a white
 * background, with the centre 81-rect carpet visible against the
 * white outer margin.
 *
 * Validates :
 *  - [SkRegion.op] under `kUnion` at 4 nested levels (the inner loops
 *    union new rects into a growing region) ;
 *  - [SkRegion.translate] (shift of an 820-rect complex region) ;
 *  - [SkCanvas.clipRegion] CTM-invariance (the clip ignores the rotate).
 */
public class ClipSierpinskiRegionGM : GM() {

    override fun getName(): String = "clip_sierpinski_region"
    override fun getISize(): SkISize = SkISize.Make(2 * kTrans + kSize, 2 * kTrans + kSize)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val region = SkRegion()
        var n = 1
        var s = kSize / 3f
        for (i in 0 until kSteps) {
            for (x in 0 until n) {
                for (y in 0 until n) {
                    val l = ((3 * x + 1) * s).toInt()
                    val t = ((3 * y + 1) * s).toInt()
                    val w = s.toInt()
                    region.op(SkIRect.MakeXYWH(l, t, w, w), SkRegion.Op.kUnion)
                }
            }
            n *= 3
            s /= 3f
        }
        // Translate the assembled carpet by (kTrans, kTrans) — exercises
        // SkRegion.translate on an 820-rect region.
        region.translate(kTrans, kTrans)

        // Save layer with an offset origin — Skia's GM comment notes
        // that this validates that the layer bookkeeping plumbs through
        // a non-zero origin correctly.
        c.saveLayer(SkRect.MakeXYWH(kTrans.toFloat(), kTrans.toFloat(), 1000f, 1000f), null)

        // The CTM rotate that clipRegion must IGNORE.
        c.rotate(25f, 50f, 50f)
        c.clipRegion(region)

        val red = SkPaint().apply { color = SK_ColorRED }
        c.drawPaint(red)

        c.restore()
    }

    private companion object {
        const val kSize: Int = 3 * 3 * 3 * 3 * 3  // 243
        const val kTrans: Int = 10
        const val kSteps: Int = 4
    }
}
