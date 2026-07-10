package org.graphiks.kanvas.color.cicp

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.HdrTransferFunction
import org.graphiks.math.SkcmsTransferFunction

/** H.273 colour signalling carried by a PNG cICP chunk. */
public data class CicpColorInfo(
    public val primaries: Int,
    public val transfer: Int,
    public val matrix: Int,
    public val fullRange: Boolean,
)

/** Builds a profile for the RGB-only subset supported by PNG. */
public fun CicpColorInfo.toColorProfile(): ColorProfileParseResult {
    if (matrix != RGB_MATRIX_COEFFICIENTS) return failure("cicp.matrix.unsupported")
    val gamut = when (primaries) {
        PRIMARIES_REC709 -> ColorProfiles.sRGB()
        PRIMARIES_REC2020 -> ColorProfiles.rec2020()
        PRIMARIES_DISPLAY_P3 -> ColorProfiles.displayP3()
        else -> return failure("cicp.primaries.unsupported")
    }
    val hdrTransfer = when (transfer) {
        TRANSFER_PQ -> HdrTransferFunction.PQ
        TRANSFER_HLG -> HdrTransferFunction.HLG
        else -> null
    }
    if (hdrTransfer != null) {
        return ColorProfileParseResult.Success(ColorProfile.hdr(checkNotNull(gamut.toXyzD50), hdrTransfer))
    }
    val sdrTransfer = when (transfer) {
        TRANSFER_REC709, TRANSFER_REC2020_10, TRANSFER_REC2020_12 ->
            checkNotNull(ColorProfiles.rec2020().transferFunction)
        TRANSFER_LINEAR -> LINEAR_TRANSFER
        TRANSFER_SRGB -> checkNotNull(ColorProfiles.sRGB().transferFunction)
        else -> return failure("cicp.transfer.unsupported")
    }
    return ColorProfileParseResult.Success(
        ColorProfile(
            colorModel = ColorModel.RGB,
            toXyzD50 = checkNotNull(gamut.toXyzD50),
            transferFunction = sdrTransfer,
        ),
    )
}

private fun failure(code: String): ColorProfileParseResult.Failure = ColorProfileParseResult.Failure(code)

private const val RGB_MATRIX_COEFFICIENTS: Int = 0
private const val PRIMARIES_REC709: Int = 1
private const val PRIMARIES_REC2020: Int = 9
private const val PRIMARIES_DISPLAY_P3: Int = 12
private const val TRANSFER_REC709: Int = 1
private const val TRANSFER_LINEAR: Int = 8
private const val TRANSFER_SRGB: Int = 13
private const val TRANSFER_REC2020_10: Int = 14
private const val TRANSFER_REC2020_12: Int = 15
private const val TRANSFER_PQ: Int = 16
private const val TRANSFER_HLG: Int = 18

private val LINEAR_TRANSFER: SkcmsTransferFunction = SkcmsTransferFunction(
    g = 1f,
    a = 1f,
    b = 0f,
    c = 1f,
    d = 0f,
    e = 0f,
    f = 0f,
)
