package org.graphiks.kanvas.codec

import org.graphiks.kanvas.color.ColorSpace
import org.graphiks.kanvas.color.ColorSpaceClassification
import org.graphiks.kanvas.color.ColorSpaceClassificationFailure
import org.graphiks.kanvas.color.ImageColorSpace
import org.graphiks.kanvas.color.classifyColorSpace
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.types.Color
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
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
        alphaType = alphaType,
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

internal class UnsupportedKanvasColorSpaceException(
    public val reason: String,
) : IllegalArgumentException("Unsupported ImageColorSpace for Kanvas conversion: $reason")

internal fun ImageColorSpace.toKanvasColorSpace(): ColorSpace {
    return when (val classification = colorProfile.classifyColorSpace()) {
        is ColorSpaceClassification.Supported -> classification.colorSpace
        is ColorSpaceClassification.Unsupported -> throw UnsupportedKanvasColorSpaceException(
            when (classification.reason) {
                ColorSpaceClassificationFailure.PROFILE -> profileRefusalCode ?: "profile"
                ColorSpaceClassificationFailure.GAMUT -> "gamut"
                ColorSpaceClassificationFailure.TRANSFER -> "transfer"
            },
        )
    }
}
