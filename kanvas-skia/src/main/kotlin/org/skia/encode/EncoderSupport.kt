package org.skia.encode

import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
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
        if (bitmap.colorType == SkColorType.kRGBA_F16Norm) {
            val argb = IntArray(bitmap.width * bitmap.height)
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    argb[y * bitmap.width + x] = bitmap.getPixel(x, y)
                }
            }
            img.setRGB(0, 0, bitmap.width, bitmap.height, argb, 0, bitmap.width)
        } else {
            img.setRGB(0, 0, bitmap.width, bitmap.height, bitmap.pixels, 0, bitmap.width)
        }
        return img
    }
}
