package org.skia.foundation.skcms

import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction

public data class SkcmsICCProfile(
    public val bytes: ByteArray,
    public val transferFn: SkcmsTransferFunction = SkNamedTransferFn.kSRGB,
    public val toXYZD50: SkcmsMatrix3x3 = SkNamedGamut.kSRGB,
) {
    public val size: Int get() = bytes.size
    public val buffer: ByteArray? get() = bytes
    public val dataColorSpace: Int get() = 0x52474220 // "RGB "
    public val pcs: Int get() = 0x58595A20 // "XYZ "
    public val tagCount: Int get() = 6
    public val hasTrc: Boolean get() = true
    public val hasToXYZD50: Boolean get() = true

    override fun equals(other: Any?): Boolean =
        this === other || (
            other is SkcmsICCProfile &&
                bytes.contentEquals(other.bytes) &&
                transferFn == other.transferFn &&
                toXYZD50 == other.toXYZD50
            )

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + transferFn.hashCode()
        result = 31 * result + toXYZD50.hashCode()
        return result
    }
}

public object SkNamedTransferFn {
    public val kSRGB: SkcmsTransferFunction = SkcmsTransferFunction(
        g = 2.4f,
        a = 1f / 1.055f,
        b = 0.055f / 1.055f,
        c = 1f / 12.92f,
        d = 0.04045f,
        e = 0f,
        f = 0f,
    )
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
    public val kSRGB: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.43606567f, 0.3851471f, 0.1430664f,
        0.2224884f, 0.71687317f, 0.06060791f,
        0.01391602f, 0.097076416f, 0.71409607f,
    )
    public val kDisplayP3: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.51512146f, 0.29197693f, 0.15710449f,
        0.24119568f, 0.6922455f, 0.0665741f,
        -0.0010528564f, 0.041885376f, 0.7840729f,
    )
    public val kRec2020: SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        0.6734772f, 0.16566467f, 0.12504578f,
        0.27903748f, 0.67533875f, 0.04560852f,
        -0.0019378662f, 0.02998352f, 0.7968445f,
    )
}

public fun skcmsParse(bytes: ByteArray): SkcmsICCProfile? {
    if (bytes.size < 132) return null
    if (bytes[36] != 'a'.code.toByte() ||
        bytes[37] != 'c'.code.toByte() ||
        bytes[38] != 's'.code.toByte() ||
        bytes[39] != 'p'.code.toByte()
    ) return null
    val gamut = when (bytes[128].toInt()) {
        1 -> SkNamedGamut.kDisplayP3
        2 -> SkNamedGamut.kRec2020
        else -> SkNamedGamut.kSRGB
    }
    val transfer = when (bytes[129].toInt()) {
        1 -> SkNamedTransferFn.kLinear
        else -> SkNamedTransferFn.kSRGB
    }
    return SkcmsICCProfile(bytes.copyOf(), transfer, gamut)
}
