package org.skia.encode

import org.skia.codec.SkEncodedImageFormat
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage

/**
 * Convenience wrapper over the D3.5 encoders — the
 * `SkImage::encodeToData(format, quality)` entry point upstream
 * Skia exposes for the "I just want bytes" call site.
 *
 * Lives on the `org.skia.encode` side rather than as an instance
 * method on [SkImage] so that the [org.skia.foundation] package
 * stays free of any dependency on the encoder / codec layers
 * (`encode` depends on `foundation`, not the other way round).
 *
 * Dispatch table :
 *  - [SkEncodedImageFormat.kPNG] → [SkPngEncoder.Encode] with
 *    default options ([quality] is ignored — PNG is lossless).
 *  - [SkEncodedImageFormat.kJPEG] → [SkJpegEncoder.Encode] with
 *    `SkJpegEncoder.Options(quality = quality)`.
 *  - Every other format returns `null` ; the codec family ships
 *    decoders only for GIF / BMP / WBMP, encoders are out of
 *    scope for D3.6.
 *
 * The returned bytes encode a snapshot of the image's pixel buffer
 * tagged sRGB ([SkImage] is always 8888 with no colour-space slot,
 * which mirrors upstream's choice for the convenience overload).
 * Workflows that need to encode a non-sRGB working-space bitmap
 * call the per-format encoders directly with their own
 * `SkBitmap`.
 */
public fun SkImage.encodeToData(
    format: SkEncodedImageFormat = SkEncodedImageFormat.kPNG,
    quality: Int = 100,
): ByteArray? {
    val bitmap = SkBitmap(width, height, SkColorSpace.makeSRGB(), SkColorType.kRGBA_8888)
    for (y in 0 until height) {
        for (x in 0 until width) {
            bitmap.pixels[y * width + x] = peekPixel(x, y)
        }
    }
    return when (format) {
        SkEncodedImageFormat.kPNG -> SkPngEncoder.Encode(bitmap)
        SkEncodedImageFormat.kJPEG -> SkJpegEncoder.Encode(
            bitmap,
            SkJpegEncoder.Options(quality = quality),
        )
        else -> null
    }
}
