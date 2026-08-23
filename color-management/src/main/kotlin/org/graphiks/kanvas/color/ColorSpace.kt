package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.kanvas.color.icc.toMatrixTrcIccBytes
import org.graphiks.math.color.ColorMatrix3x3F32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.isNear

/** Stable public descriptor for a named image colour space. */
public data class ColorSpace(
    public val name: String,
    public val transferFunction: TransferFunction,
    public val gamut: Gamut,
) {
    public companion object {
        public val SRGB: ColorSpace = ColorSpace("sRGB", TransferFunction.SRGB, Gamut.SRGB)
        public val DISPLAY_P3: ColorSpace = ColorSpace("Display P3", TransferFunction.SRGB, Gamut.DISPLAY_P3)
        public val LINEAR_SRGB: ColorSpace = ColorSpace("Linear sRGB", TransferFunction.LINEAR, Gamut.SRGB)
    }
}

/** Named transfer functions exposed by [ColorSpace]. */
public enum class TransferFunction { SRGB, LINEAR, PQ, HLG }

/** Named RGB primaries exposed by [ColorSpace]. */
public enum class Gamut { SRGB, DISPLAY_P3, REC2020 }

/** A classification failure that an API facade may map to its own diagnostic. */
public enum class ColorSpaceClassificationFailure { PROFILE, GAMUT, TRANSFER }

/** Result of classifying a parsed profile as a named public [ColorSpace]. */
public sealed interface ColorSpaceClassification {
    public data class Supported(public val colorSpace: ColorSpace) : ColorSpaceClassification

    public data class Unsupported(public val reason: ColorSpaceClassificationFailure) : ColorSpaceClassification
}

/** Classifies this supported RGB profile without depending on a codec or Skia facade. */
public fun ColorProfile.classifyColorSpace(): ColorSpaceClassification {
    if (colorModel != ColorModel.RGB || unsupportedCode != null) {
        return ColorSpaceClassification.Unsupported(ColorSpaceClassificationFailure.PROFILE)
    }
    val matrix = toXyzD50
        ?: return ColorSpaceClassification.Unsupported(ColorSpaceClassificationFailure.PROFILE)
    val gamut = matrix.classifyNamedGamut()
        ?: return ColorSpaceClassification.Unsupported(ColorSpaceClassificationFailure.GAMUT)
    val transfer = when (hdrTransferFunction) {
        HdrTransferFunction.PQ -> TransferFunction.PQ
        HdrTransferFunction.HLG -> TransferFunction.HLG
        null -> when {
            transferFunction == null -> return ColorSpaceClassification.Unsupported(ColorSpaceClassificationFailure.PROFILE)
            transferFunction.isNear(ColorTransferFunction.sRgb, TRANSFER_CLASSIFICATION_TOLERANCE) -> TransferFunction.SRGB
            transferFunction.isNear(ColorTransferFunction.linear, TRANSFER_CLASSIFICATION_TOLERANCE) -> TransferFunction.LINEAR
            else -> return ColorSpaceClassification.Unsupported(ColorSpaceClassificationFailure.TRANSFER)
        }
    }
    return ColorSpaceClassification.Supported(knownColorSpace(transfer, gamut))
}

/** Returns the named public descriptor, or `null` when this profile is not in that subset. */
public fun ColorProfile.toColorSpaceOrNull(): ColorSpace? =
    (classifyColorSpace() as? ColorSpaceClassification.Supported)?.colorSpace

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

private val NAMED_GAMUTS: List<Pair<List<ColorMatrix3x3F32>, Gamut>> by lazy {
    listOf(
        allowedGamutMatrices(ColorProfiles.sRGB()) to Gamut.SRGB,
        allowedGamutMatrices(ColorProfiles.displayP3()) to Gamut.DISPLAY_P3,
        allowedGamutMatrices(ColorProfiles.rec2020()) to Gamut.REC2020,
    )
}

private fun ColorMatrix3x3F32.classifyNamedGamut(): Gamut? =
    NAMED_GAMUTS.firstOrNull { (matrices, _) ->
        matrices.any { matrix -> isNear(matrix, GAMUT_CLASSIFICATION_TOLERANCE) }
    }?.second

private fun allowedGamutMatrices(profile: ColorProfile): List<ColorMatrix3x3F32> {
    val matrix = requireNotNull(profile.toXyzD50)
    return listOf(
        matrix,
        serializedGamutMatrix(matrix, ColorTransferFunction.sRgb),
        serializedGamutMatrix(matrix, ColorTransferFunction.linear),
    )
}

private fun serializedGamutMatrix(
    matrix: ColorMatrix3x3F32,
    transferFunction: ColorTransferFunction.Parametric,
): ColorMatrix3x3F32 = requireNotNull(
    IccProfileParser.parse(
        ColorProfile(ColorModel.RGB, matrix, transferFunction).toMatrixTrcIccBytes(),
        IccParseLimits(),
    ).getOrThrow().toXyzD50,
)

private const val GAMUT_CLASSIFICATION_TOLERANCE: Float = 2f / 65_536f
private const val TRANSFER_CLASSIFICATION_TOLERANCE: Float = 2f / 65_536f
