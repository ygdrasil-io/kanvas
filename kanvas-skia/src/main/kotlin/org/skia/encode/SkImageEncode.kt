package org.skia.encode

import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage

/**
 * Phase R2.12 helper — produces the raw `ByteArray` for
 * [SkImage.encodeToData], factored out so the member method on
 * [SkImage] (which returns the upstream-shaped `sk_sp<SkData>` /
 * Kotlin [org.skia.foundation.SkData]) can share its pixel-snapshot
 * + per-format dispatch with the historical D3.6 surface.
 *
 * The wrapper takes the same single snapshot ([SkImage.peekPixel]
 * into a fresh `SkBitmap` tagged sRGB) the previous extension built,
 * then routes through [PngCall] or [SkJpegEncoder]. Returns
 * `null` for formats that this convenience API intentionally does
 * not dispatch. BMP, WBMP, and lossless WebP have dedicated encoder
 * entry points, but `SkImage.encodeToData` remains PNG/JPEG-only to
 * preserve its documented public contract.
 */
internal fun encodeImageToBytes(
    image: SkImage,
    format: SkEncodedImageFormat,
    quality: Int,
): ByteArray? {
    val bitmap = SkBitmap(image.width, image.height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            bitmap.pixels[y * image.width + x] = image.peekPixel(x, y)
        }
    }
    return when (format) {
        SkEncodedImageFormat.kPNG -> PngCall.encode(bitmap)
        SkEncodedImageFormat.kJPEG -> SkJpegEncoder.Encode(
            bitmap,
            SkJpegEncoder.Options(quality = quality),
        )
        else -> null
    }
}
