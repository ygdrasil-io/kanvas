package org.graphiks.kanvas.codec

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.cicp.CicpColorInfo
import org.graphiks.kanvas.color.cicp.toColorProfile
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.EncodedImageFormat
import org.graphiks.kanvas.image.EncodedOrigin
import org.graphiks.kanvas.image.ImageInfo
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.ColorSpace
import org.graphiks.kanvas.types.Gamut
import org.graphiks.kanvas.types.TransferFunction
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkEncodedImageFormat
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkICC
import org.skia.foundation.SkImageInfo
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.foundation.skcms.skcmsParse
import kotlin.math.abs

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

internal class UnsupportedKanvasColorSpaceException(
    public val reason: String,
) : IllegalArgumentException("Unsupported SkColorSpace for Kanvas conversion: $reason")

internal fun SkColorSpace.toKanvasColorSpace(): ColorSpace {
    if (colorProfile.isHdr) {
        return HDR_COLOR_SPACES.firstOrNull { (profile, _) -> profile == colorProfile }?.second
            ?: throw UnsupportedKanvasColorSpaceException("hdr")
    }
    if (!isProfileSupported()) {
        throw UnsupportedKanvasColorSpaceException(profileRefusalCode ?: "profile")
    }

    val gamut = toXYZD50.classifyNamedGamut()
        ?: throw UnsupportedKanvasColorSpaceException("gamut")
    val transferFunction = when {
        transferFn.isNear(SkNamedTransferFn.kSRGB) -> TransferFunction.SRGB
        transferFn.isNear(SkNamedTransferFn.kLinear) -> TransferFunction.LINEAR
        else -> throw UnsupportedKanvasColorSpaceException("transfer")
    }
    return knownColorSpace(transferFunction, gamut)
}

private val HDR_COLOR_SPACES: List<Pair<ColorProfile, ColorSpace>> by lazy {
    listOf(
        cicpProfile(primaries = 1, transfer = 16) to knownColorSpace(TransferFunction.PQ, Gamut.SRGB),
        cicpProfile(primaries = 12, transfer = 16) to knownColorSpace(TransferFunction.PQ, Gamut.DISPLAY_P3),
        cicpProfile(primaries = 9, transfer = 16) to knownColorSpace(TransferFunction.PQ, Gamut.REC2020),
        cicpProfile(primaries = 9, transfer = 18) to knownColorSpace(TransferFunction.HLG, Gamut.REC2020),
    )
}

private fun cicpProfile(primaries: Int, transfer: Int): ColorProfile =
    CicpColorInfo(
        primaries = primaries,
        transfer = transfer,
        matrix = 0,
        fullRange = true,
    ).toColorProfile().getOrThrow()

private fun knownColorSpace(transferFunction: TransferFunction, gamut: Gamut): ColorSpace = when {
    transferFunction == TransferFunction.SRGB && gamut == Gamut.SRGB -> ColorSpace.SRGB
    transferFunction == TransferFunction.SRGB && gamut == Gamut.DISPLAY_P3 -> ColorSpace.DISPLAY_P3
    transferFunction == TransferFunction.LINEAR && gamut == Gamut.SRGB -> ColorSpace.LINEAR_SRGB
    else -> ColorSpace(
        name = when (transferFunction) {
            TransferFunction.SRGB -> gamut.displayName
            TransferFunction.LINEAR -> "Linear ${gamut.displayName}"
            TransferFunction.PQ -> "${gamut.displayName} PQ"
            TransferFunction.HLG -> "${gamut.displayName} HLG"
        },
        transferFunction = transferFunction,
        gamut = gamut,
    )
}

private val Gamut.displayName: String
    get() = when (this) {
        Gamut.SRGB -> "sRGB"
        Gamut.DISPLAY_P3 -> "Display P3"
        Gamut.REC2020 -> "Rec.2020"
    }

private val NAMED_GAMUTS: List<Pair<List<SkcmsMatrix3x3>, Gamut>> by lazy {
    listOf(
        allowedGamutMatrices(SkNamedGamut.kSRGB) to Gamut.SRGB,
        allowedGamutMatrices(SkNamedGamut.kDisplayP3) to Gamut.DISPLAY_P3,
        allowedGamutMatrices(SkNamedGamut.kRec2020) to Gamut.REC2020,
    )
}

private fun SkcmsMatrix3x3.classifyNamedGamut(): Gamut? =
    NAMED_GAMUTS.firstOrNull { (matrices, _) ->
        matrices.any { matrix -> isNear(matrix, GAMUT_CLASSIFICATION_TOLERANCE) }
    }?.second

private fun allowedGamutMatrices(canonical: SkcmsMatrix3x3): List<SkcmsMatrix3x3> {
    val canonicalSnapshot = canonical.copy()
    return listOf(
        canonicalSnapshot,
        serializedGamutMatrix(SkNamedTransferFn.kSRGB, canonicalSnapshot),
        serializedGamutMatrix(SkNamedTransferFn.kLinear, canonicalSnapshot),
    )
}

private fun serializedGamutMatrix(
    transferFunction: SkcmsTransferFunction,
    gamut: SkcmsMatrix3x3,
): SkcmsMatrix3x3 = requireNotNull(
    SkColorSpace.make(requireNotNull(skcmsParse(SkICC.WriteToICC(transferFunction, gamut)))),
).toXYZD50

private fun SkcmsMatrix3x3.isNear(other: SkcmsMatrix3x3, tolerance: Float): Boolean {
    for (row in 0 until 3) for (column in 0 until 3) {
        if (abs(this[row, column] - other[row, column]) > tolerance) return false
    }
    return true
}

private fun SkcmsTransferFunction.isNear(other: SkcmsTransferFunction): Boolean = listOf(
    g to other.g,
    a to other.a,
    b to other.b,
    c to other.c,
    d to other.d,
    e to other.e,
    f to other.f,
).all { (left, right) -> abs(left - right) <= TRANSFER_FUNCTION_TOLERANCE }

private const val GAMUT_CLASSIFICATION_TOLERANCE: Float = 2f / 65_536f
private const val TRANSFER_FUNCTION_TOLERANCE: Float = 2f / 65_536f
