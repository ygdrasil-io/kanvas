package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/rrects.cpp` — `RRectGM(kAA_Draw_Type)`.
 *
 * GM name: `rrect_draw_aa`. Identical to [RRectDrawBwGM] but with
 * [SkPaint.isAntiAlias] set to `true`, producing smooth edges on the 43
 * rrects across the 640 × 480 tiled grid.
 *
 * Reference image: `rrect_draw_aa.png`, 640 × 480, BG 0xFFDDDDDD.
 */
public class RRectDrawAaGM : GM() {

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun getName(): String = "rrect_draw_aa"
    override fun getISize(): SkISize = SkISize.Make(kImageWidth, kImageHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rrects = buildRRects()
        val paint = SkPaint().apply { isAntiAlias = true }

        var x = 1
        var y = 1
        for (idx in 0 until kNumRRects) {
            c.save()
            c.translate(x.toFloat(), y.toFloat())
            if (idx == kNumRRects - 1) {
                c.clipRect(SkRect.MakeWH((kTileX - 2).toFloat(), (kTileY - 2).toFloat()))
                c.translate(-0.14f * rrects[idx].rect().width(),
                             -0.14f * rrects[idx].rect().height())
            }
            c.drawRRect(rrects[idx], paint)
            c.restore()
            x += kTileX
            if (x > kImageWidth) {
                x = 1
                y += kTileY
            }
        }
    }
}
