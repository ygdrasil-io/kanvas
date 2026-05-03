package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorCYAN
import org.skia.foundation.SK_ColorGRAY
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorMAGENTA
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/bitmaprect.cpp` (`DrawBitmapRect3`).
 *
 * Probes `drawImageRect` with a partial source rect: a 3x3 bitmap is drawn
 * with `srcR = (0.5, 0.5, 2.5, 2.5)` into a 200x100 device rect. With the
 * default sampling (nearest, no mipmap) the output should be a 2x2 grid of
 * coloured stripes — the eight pixels on the border of the source rect
 * contribute half the width/height of the central pixel.
 */
public class DrawBitmapRect3 : GM() {
    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "3x3bitmaprect"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val bitmap = make3x3Bitmap()
        val srcR = SkRect.MakeLTRB(0.5f, 0.5f, 2.5f, 2.5f)
        val dstR = SkRect.MakeLTRB(100f, 100f, 300f, 200f)
        c.drawImageRect(
            bitmap.asImage(),
            srcR,
            dstR,
            SkSamplingOptions.Default,
            paint = null,
            constraint = SrcRectConstraint.kStrict,
        )
    }

    /**
     * Build the 3x3 reference bitmap. Mirrors `make_3x3_bitmap` from upstream
     * `bitmaprect.cpp`. C++ indexes `textureData[x][y]`, so pixel `(x, y)`
     * receives `data[x][y]`.
     */
    private fun make3x3Bitmap(): SkBitmap {
        val bitmap = SkBitmap(3, 3)
        val data = arrayOf(
            intArrayOf(SK_ColorRED,    SK_ColorWHITE, SK_ColorBLUE),     // x=0, y=0..2
            intArrayOf(SK_ColorGREEN,  SK_ColorBLACK, SK_ColorCYAN),     // x=1
            intArrayOf(SK_ColorYELLOW, SK_ColorGRAY,  SK_ColorMAGENTA),  // x=2
        )
        for (x in 0 until 3) {
            for (y in 0 until 3) {
                bitmap.setPixel(x, y, data[x][y])
            }
        }
        return bitmap
    }
}
