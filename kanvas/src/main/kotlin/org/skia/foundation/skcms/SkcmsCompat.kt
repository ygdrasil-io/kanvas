package org.skia.foundation.skcms

import org.graphiks.kanvas.color.ColorModel
import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult
import org.graphiks.kanvas.color.ColorProfiles
import org.graphiks.kanvas.color.icc.IccParseLimits
import org.graphiks.kanvas.color.icc.IccProfileParser
import org.graphiks.kanvas.color.icc.IccProfileWriter
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import java.nio.ByteBuffer
import java.nio.ByteOrder

public class SkcmsICCProfile private constructor(
    public val colorProfile: ColorProfile,
    originalBytes: ByteArray,
    /** Non-null legacy projection; use [colorProfile] to determine actual facade support. */
    public val transferFn: SkcmsTransferFunction,
    /** Non-null legacy projection; use [colorProfile] to determine actual facade support. */
    public val toXYZD50: SkcmsMatrix3x3,
) {
    private val originalBytes: ByteArray = originalBytes.copyOf()

    public constructor(
        bytes: ByteArray,
        transferFn: SkcmsTransferFunction = SkNamedTransferFn.kSRGB,
        toXYZD50: SkcmsMatrix3x3 = SkNamedGamut.kSRGB,
    ) : this(
        colorProfile = ColorProfile(ColorModel.RGB, toXYZD50, transferFn),
        originalBytes = bytes,
        transferFn = transferFn,
        toXYZD50 = toXYZD50,
    )

    public val bytes: ByteArray get() = originalBytes.copyOf()
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

    public operator fun component1(): ByteArray = bytes
    public operator fun component2(): SkcmsTransferFunction = transferFn
    public operator fun component3(): SkcmsMatrix3x3 = toXYZD50

    public fun copy(
        bytes: ByteArray = this.bytes,
        transferFn: SkcmsTransferFunction = this.transferFn,
        toXYZD50: SkcmsMatrix3x3 = this.toXYZD50,
    ): SkcmsICCProfile = if (
        bytes.contentEquals(originalBytes) && transferFn == this.transferFn && toXYZD50 == this.toXYZD50
    ) {
        SkcmsICCProfile(colorProfile, bytes, transferFn, toXYZD50)
    } else {
        SkcmsICCProfile(bytes, transferFn, toXYZD50)
    }

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SkcmsICCProfile &&
                originalBytes.contentEquals(other.originalBytes) &&
                transferFn == other.transferFn &&
                toXYZD50 == other.toXYZD50
            )

    override fun hashCode(): Int {
        var result = originalBytes.contentHashCode()
        result = 31 * result + transferFn.hashCode()
        result = 31 * result + toXYZD50.hashCode()
        return result
    }

    override fun toString(): String =
        "SkcmsICCProfile(bytes=${originalBytes.contentToString()}, transferFn=$transferFn, toXYZD50=$toXYZD50)"

    public companion object {
        public fun fromColorProfile(colorProfile: ColorProfile): SkcmsICCProfile {
            if (!isFacadeMatrixTrc(colorProfile)) {
                return create(colorProfile, ByteArray(0))
            }
            val bytes = IccProfileWriter.writeMatrixTrc(colorProfile)
            val normalized = IccProfileParser.parse(bytes, IccParseLimits()).getOrThrow()
            return create(normalized, bytes)
        }

        internal fun fromParsedColorProfile(
            colorProfile: ColorProfile,
            originalBytes: ByteArray,
        ): SkcmsICCProfile = create(colorProfile, originalBytes)

        private fun create(colorProfile: ColorProfile, bytes: ByteArray): SkcmsICCProfile = SkcmsICCProfile(
            colorProfile = colorProfile,
            originalBytes = bytes,
            transferFn = colorProfile.transferFunction ?: SkNamedTransferFn.kSRGB,
            toXYZD50 = colorProfile.toXyzD50 ?: SkNamedGamut.kSRGB,
        )

        private fun isFacadeMatrixTrc(colorProfile: ColorProfile): Boolean =
            colorProfile.colorModel == ColorModel.RGB &&
                colorProfile.unsupportedCode == null &&
                colorProfile.hasMatrixTrc &&
                !colorProfile.isHdr &&
                colorProfile.transferFunction != null &&
                colorProfile.toXyzD50 != null

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
    val snapshot = bytes.copyOf()
    return when (val result = IccProfileParser.parse(snapshot, IccParseLimits())) {
        is ColorProfileParseResult.Success -> SkcmsICCProfile.fromParsedColorProfile(result.profile, snapshot)
        is ColorProfileParseResult.Failure -> null
    }
}
