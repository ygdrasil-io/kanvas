package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPixmap
import java.awt.image.BufferedImage

/**
 * Shared encoder helpers — internal to the `org.skia.encode` package.
 *
 * The encoders all need the same `SkBitmap → BufferedImage`
 * projection : ImageIO writers consume `BufferedImage`, our bitmap
 * holds an `IntArray` of `0xAARRGGBB` (8888) or a `FloatArray` of
 * premul F16. The conversion below mirrors the legacy
 * `TestUtils.bitmapToBufferedImage` that the test harness used pre-D3.
 */
internal object EncoderSupport {

    /**
     * Project [bitmap] onto a fresh [BufferedImage] of type
     * `TYPE_INT_ARGB`. F16 bitmaps go through
     * [SkBitmap.getPixel] so the colour-space-aware unpremul step
     * matches what the test harness used to produce.
     */
    fun bitmapToBufferedImage(bitmap: SkBitmap): BufferedImage {
        val img = BufferedImage(bitmap.width, bitmap.height, BufferedImage.TYPE_INT_ARGB)
        // 8888 has a direct IntArray storage backing [SkBitmap.pixels] —
        // hand it to ImageIO via setRGB without re-materialising. Every
        // other colour type is materialised through the colour-aware
        // [SkBitmap.getPixel] accessor, which already deals with F16
        // unpremul, A8 alpha-only, 4444/565 expansion, and Gray_8
        // luminance replication.
        if (bitmap.colorType == SkColorType.kRGBA_8888) {
            img.setRGB(0, 0, bitmap.width, bitmap.height, bitmap.pixels, 0, bitmap.width)
        } else {
            val argb = IntArray(bitmap.width * bitmap.height)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    argb[y * bitmap.width + x] = bitmap.getPixel(x, y)
                }
            }
            img.setRGB(0, 0, bitmap.width, bitmap.height, argb, 0, bitmap.width)
        }
        return img
    }

    /**
     * Project [src] into a freshly-allocated 8888 [SkBitmap] so the
     * existing [bitmapToBufferedImage] pipeline can encode it. Returns
     * `null` for an empty pixmap (zero width or height) or one with an
     * unknown colour type — both unencodeable at the upstream level too.
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
