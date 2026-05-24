package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPixmap

/**
 * Shared encoder helpers — internal to the `org.skia.encode` package.
 */
internal object EncoderSupport {

    /**
     * Project [src] into a freshly-allocated 8888 [SkBitmap] so the
     * bitmap encoder paths can consume it. Returns `null` for an empty
     * pixmap (zero width or height) or one with an unknown colour type
     * — both unencodeable at the upstream level too.
     *
     * The conversion goes through [SkPixmap.getColor] (non-premultiplied
     * 8-bit ARGB) so any colour type the pixmap supports
     * (`kAlpha_8`, `kARGB_4444`, `kRGBA_8888`, `kBGRA_8888`) round-trips
     * correctly without us having to re-derive the per-type byte order.
     */
    fun pixmapToBitmap(src: SkPixmap): SkBitmap? {
        if (src.width() <= 0 || src.height() <= 0) return null
        if (src.colorType() == SkColorType.kUnknown) return null
        val cs = src.colorSpace() ?: org.skia.foundation.SkColorSpace.makeSRGB()
        val bm = SkBitmap(src.width(), src.height(), cs, SkColorType.kRGBA_8888)
        for (y in 0 until src.height()) {
            for (x in 0 until src.width()) {
                bm.setPixel(x, y, src.getColor(x, y))
            }
        }
        return bm
    }
}
