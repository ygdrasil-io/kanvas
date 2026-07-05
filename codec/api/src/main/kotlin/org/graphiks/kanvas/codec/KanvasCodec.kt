package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.EncodedOrigin
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.ColorSpace
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkImageInfo

public fun Codec.getKanvasInfo(): ImageInfo = getInfo().toKanvasImageInfo()

public fun Codec.getKanvasImage(): Pair<Bitmap?, Codec.Result> {
    val (bitmap, result) = getImage()
    return if (result == Codec.Result.kSuccess && bitmap != null) {
        bitmap.toKanvasBitmap() to result
    } else {
        null to result
    }
}

public fun SkBitmap.toKanvasBitmap(): Bitmap {
    val target = Bitmap(
        width = width,
        height = height,
        colorType = colorType.toKanvasColorType(),
        colorSpace = colorSpace.toKanvasColorSpace(),
    )
    for (y in 0 until height) {
        for (x in 0 until width) {
            target.setPixel(x, y, Color.fromArgbInt(getPixel(x, y)))
        }
    }
    return target
}

public fun SkImageInfo.toKanvasImageInfo(): ImageInfo =
    ImageInfo(
        width = width,
        height = height,
        colorType = colorType.toKanvasColorType(),
        alphaType = alphaType.toKanvasAlphaType(),
        colorSpace = colorSpace.toKanvasColorSpace(),
    )

public fun SkColorType.toKanvasColorType(): ColorType = when (this) {
    SkColorType.kRGBA_8888 -> ColorType.RGBA_8888
    SkColorType.kBGRA_8888 -> ColorType.BGRA_8888
    SkColorType.kAlpha_8 -> ColorType.ALPHA_8
    SkColorType.kGray_8 -> ColorType.GRAY_8
    SkColorType.kRGBA_F16,
    SkColorType.kRGBA_F16Norm,
        -> ColorType.RGBA_F16
    SkColorType.kRGB_565 -> ColorType.RGB_565
    SkColorType.kARGB_4444 -> ColorType.ARGB_4444
    else -> error("Unsupported SkColorType for Kanvas conversion: $this")
}

public fun SkAlphaType.toKanvasAlphaType(): AlphaType = when (this) {
    SkAlphaType.kUnknown -> AlphaType.UNKNOWN
    SkAlphaType.kOpaque -> AlphaType.OPAQUE
    SkAlphaType.kPremul -> AlphaType.PREMUL
    SkAlphaType.kUnpremul -> AlphaType.UNPREMUL
}

public fun SkEncodedImageFormat.toKanvasEncodedImageFormat(): EncodedImageFormat = when (this) {
    SkEncodedImageFormat.kBMP -> EncodedImageFormat.BMP
    SkEncodedImageFormat.kGIF -> EncodedImageFormat.GIF
    SkEncodedImageFormat.kICO -> EncodedImageFormat.ICO
    SkEncodedImageFormat.kJPEG -> EncodedImageFormat.JPEG
    SkEncodedImageFormat.kPNG -> EncodedImageFormat.PNG
    SkEncodedImageFormat.kWBMP -> EncodedImageFormat.WBMP
    SkEncodedImageFormat.kWEBP -> EncodedImageFormat.WEBP
    SkEncodedImageFormat.kPKM -> EncodedImageFormat.PKM
    SkEncodedImageFormat.kKTX -> EncodedImageFormat.KTX
    SkEncodedImageFormat.kASTC -> EncodedImageFormat.ASTC
    SkEncodedImageFormat.kDNG -> EncodedImageFormat.DNG
    SkEncodedImageFormat.kHEIF -> EncodedImageFormat.HEIF
    SkEncodedImageFormat.kAVIF -> EncodedImageFormat.AVIF
    SkEncodedImageFormat.kJPEGXL -> EncodedImageFormat.JPEGXL
}

public fun SkEncodedOrigin.toKanvasEncodedOrigin(): EncodedOrigin = when (this) {
    SkEncodedOrigin.kTopLeft -> EncodedOrigin.TOP_LEFT
    SkEncodedOrigin.kTopRight -> EncodedOrigin.TOP_RIGHT
    SkEncodedOrigin.kBottomRight -> EncodedOrigin.BOTTOM_RIGHT
    SkEncodedOrigin.kBottomLeft -> EncodedOrigin.BOTTOM_LEFT
    SkEncodedOrigin.kLeftTop -> EncodedOrigin.LEFT_TOP
    SkEncodedOrigin.kRightTop -> EncodedOrigin.RIGHT_TOP
    SkEncodedOrigin.kRightBottom -> EncodedOrigin.RIGHT_BOTTOM
    SkEncodedOrigin.kLeftBottom -> EncodedOrigin.LEFT_BOTTOM
}

private fun SkColorSpace.toKanvasColorSpace(): ColorSpace = when {
    isSRGB() -> ColorSpace.SRGB
    gammaIsLinear() -> ColorSpace.LINEAR_SRGB
    else -> ColorSpace.SRGB
}
