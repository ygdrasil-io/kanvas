package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/tinybitmap.cpp::TinyBitmapGM` (100 × 100).
 *
 * 1 × 1 ARGB bitmap with the single pixel packed as
 * `0x80800000` (premul-encoded but stored non-premul in our 8888
 * bitmap), built into a shader with `tileX=kRepeat` / `tileY=kMirror`,
 * then `drawPaint` over the whole canvas with `paint.alphaf = 0.5`.
 *
 * The mirror tile mode in Y is identity-equivalent for a 1 × 1 source —
 * every device row samples the same single texel. The visual result is
 * a uniform translucent dark-red wash over the BG.
 */
public class TinyBitmapGM : GM() {

    init {
        setBGColor(0xFFDDDDDDu.toInt())
    }

    override fun getName(): String = "tinybitmap"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bm = SkBitmap(1, 1)
        // Upstream packs `SkPackARGB32(0x80, 0x80, 0, 0)` into an N32
        // (premultiplied) bitmap, so the stored pixel is **premul red
        // at 50 % alpha** — i.e. unpremul (R=0xFF, A=0x80). Our 8888
        // backing stores non-premul ARGB, so we set it as such.
        bm.setPixel(0, 0, SkColorSetARGB(0x80, 0xFF, 0, 0))

        val paint = SkPaint().apply {
            alphaf = 0.5f
            shader = bm.makeShader(SkTileMode.kRepeat, SkTileMode.kMirror, SkSamplingOptions.Default)
        }
        c.drawPaint(paint)
    }
}
