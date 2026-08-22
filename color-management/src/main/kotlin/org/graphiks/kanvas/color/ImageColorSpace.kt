package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccProfile
import org.graphiks.math.color.ColorMatrix3x3F32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.isNear

/** Whether an image-facing profile is representable by the current Matrix/TRC image API. */
public enum class ImageColorSpaceProfileStatus {
    SUPPORTED,
    UNSUPPORTED,
}

/**
 * Image-facing color metadata backed by a [ColorProfile] and optional encoded ICC provenance.
 *
 * The support status deliberately preserves the current Matrix/TRC image compatibility boundary.
 * It does not limit the transform pipeline's independent HDR or LUT capabilities.
 */
public class ImageColorSpace private constructor(
    public val colorProfile: ColorProfile,
    public val iccProfile: IccProfile?,
    public val profileStatus: ImageColorSpaceProfileStatus,
    public val profileRefusalCode: String?,
) {
    public val transferFunction: ColorTransferFunction.Parametric?
        get() = colorProfile.transferFunction

    public val toXyzD50: ColorMatrix3x3F32?
        get() = colorProfile.toXyzD50

    public fun isSrgb(): Boolean =
        profileStatus == ImageColorSpaceProfileStatus.SUPPORTED && toColorSpaceOrNull() == ColorSpace.SRGB

    public fun isLinear(): Boolean =
        transferFunction?.isNear(ColorTransferFunction.linear, TRANSFER_TOLERANCE) == true

    public fun isProfileSupported(): Boolean = profileStatus == ImageColorSpaceProfileStatus.SUPPORTED

    /** Returns the named public descriptor when this profile belongs to that subset. */
    public fun toColorSpaceOrNull(): ColorSpace? = colorProfile.toColorSpaceOrNull()

    override fun toString(): String = when {
        isSrgb() -> "ImageColorSpace(sRGB)"
        profileStatus == ImageColorSpaceProfileStatus.UNSUPPORTED ->
            "ImageColorSpace(unsupported=${profileRefusalCode ?: "unknown"})"
        else -> "ImageColorSpace(RGB)"
    }

    public companion object {
        private val SRGB: ImageColorSpace = fromColorProfile(ColorProfiles.sRGB())
        private val LINEAR_SRGB: ImageColorSpace = fromColorProfile(
            ColorProfile(
                colorModel = ColorModel.RGB,
                toXyzD50 = requireNotNull(ColorProfiles.sRGB().toXyzD50),
                transferFunction = ColorTransferFunction.linear,
            ),
        )

        public fun sRGB(): ImageColorSpace = SRGB

        public fun linearSrgb(): ImageColorSpace = LINEAR_SRGB

        public fun fromIccProfile(profile: IccProfile): ImageColorSpace =
            fromColorProfile(profile.colorProfile, profile)

        public fun fromMatrixTrc(
            transferFunction: ColorTransferFunction.Parametric,
            toXyzD50: ColorMatrix3x3F32,
        ): ImageColorSpace = fromColorProfile(
            ColorProfile(
                colorModel = ColorModel.RGB,
                toXyzD50 = toXyzD50,
                transferFunction = transferFunction,
            ),
        )

        public fun fromColorProfile(
            colorProfile: ColorProfile,
            iccProfile: IccProfile? = null,
        ): ImageColorSpace {
            val supported = colorProfile.colorModel == ColorModel.RGB &&
                colorProfile.unsupportedCode == null &&
                !colorProfile.isHdr &&
                colorProfile.hasMatrixTrc
            return ImageColorSpace(
                colorProfile = colorProfile,
                iccProfile = iccProfile,
                profileStatus = if (supported) {
                    ImageColorSpaceProfileStatus.SUPPORTED
                } else {
                    ImageColorSpaceProfileStatus.UNSUPPORTED
                },
                profileRefusalCode = if (supported) null else colorProfile.refusalCode(),
            )
        }
    }
}

private fun ColorProfile.refusalCode(): String = when {
    unsupportedCode != null -> unsupportedCode
    colorModel == ColorModel.GRAY -> "icc.gray.unsupported"
    isHdr -> "color.hdr.unsupported"
    else -> "icc.profile.shape.unsupported"
}

private const val TRANSFER_TOLERANCE: Float = 2f / 65_536f
