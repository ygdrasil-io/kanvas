package org.skia.foundation.skcms

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import java.nio.ByteBuffer
import java.nio.ByteOrder

public class SkcmsICCProfile private constructor(
    public val colorProfile: ColorProfile,
    originalBytes: ByteArray,
) {
    private val originalBytes: ByteArray = originalBytes.copyOf()

    public constructor(
        bytes: ByteArray,
        transferFn: SkcmsTransferFunction = SkNamedTransferFn.kSRGB,
        toXYZD50: SkcmsMatrix3x3 = SkNamedGamut.kSRGB,
    ) : this(
        colorProfile = ColorProfile(ColorModel.RGB, toXYZD50, transferFn),
        originalBytes = bytes,
    )

    public val bytes: ByteArray get() = originalBytes.copyOf()
    public val transferFn: SkcmsTransferFunction? get() = colorProfile.transferFunction
    public val toXYZD50: SkcmsMatrix3x3? get() = colorProfile.toXyzD50
    public val size: Int get() = originalBytes.size
    public val buffer: ByteArray? get() = originalBytes.copyOf()
    public val dataColorSpace: Int
        get() = when (colorProfile.colorModel) {
            ColorModel.RGB -> RGB_SIGNATURE
            ColorModel.GRAY -> GRAY_SIGNATURE
        }
    public val pcs: Int get() = 0x58595A20 // "XYZ "
    public val tagCount: Int get() = readTagCount(originalBytes)
    public val hasTrc: Boolean get() = colorProfile.transferFunction != null
    public val hasToXYZD50: Boolean get() = colorProfile.toXyzD50 != null

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SkcmsICCProfile &&
                originalBytes.contentEquals(other.originalBytes) &&
                colorProfile == other.colorProfile
            )

    override fun hashCode(): Int {
        var result = originalBytes.contentHashCode()
        result = 31 * result + colorProfile.hashCode()
        return result
    }

    public companion object {
        public fun fromColorProfile(
            colorProfile: ColorProfile,
            originalBytes: ByteArray = ByteArray(0),
        ): SkcmsICCProfile = SkcmsICCProfile(colorProfile, originalBytes)

        private const val RGB_SIGNATURE: Int = 0x52474220
        private const val GRAY_SIGNATURE: Int = 0x47524159
        private const val ICC_TAG_COUNT_OFFSET: Int = 128
        private const val ICC_HEADER_AND_COUNT_SIZE: Int = 132

        private fun readTagCount(bytes: ByteArray): Int {
            if (bytes.size < ICC_HEADER_AND_COUNT_SIZE) return 0
            return ByteBuffer.wrap(bytes)
                .order(ByteOrder.BIG_ENDIAN)
                .getInt(ICC_TAG_COUNT_OFFSET)
                .coerceAtLeast(0)
        }
    }
}

public object SkNamedTransferFn {
    public val kSRGB: SkcmsTransferFunction = checkNotNull(ColorProfiles.sRGB().transferFunction)
    public val kLinear: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 1f,
        a = 1f,
        b = 0f,
        c = 1f,
        d = 0f,
        e = 0f,
        f = 0f,
    )
}

public object SkNamedGamut {
    public val kSRGB: SkcmsMatrix3x3 = checkNotNull(ColorProfiles.sRGB().toXyzD50)
    public val kDisplayP3: SkcmsMatrix3x3 = checkNotNull(ColorProfiles.displayP3().toXyzD50)
    public val kRec2020: SkcmsMatrix3x3 = checkNotNull(ColorProfiles.rec2020().toXyzD50)
}

public fun skcmsParse(bytes: ByteArray): SkcmsICCProfile? {
    return when (val result = IccProfileParser.parse(bytes, IccParseLimits())) {
        is ColorProfileParseResult.Success -> SkcmsICCProfile.fromColorProfile(result.profile, bytes)
        is ColorProfileParseResult.Failure -> null
    }
}
