package org.graphiks.kanvas.color.icc

import org.graphiks.kanvas.color.ColorProfile
import org.graphiks.kanvas.color.ColorProfileParseResult

/** Immutable encoded ICC provenance together with its parsed [ColorProfile]. */
public class IccProfile private constructor(
    public val colorProfile: ColorProfile,
    bytes: ByteArray,
) {
    private val originalBytes: ByteArray = bytes.copyOf()

    /** A defensive copy of the exact encoded ICC bytes. */
    public val bytes: ByteArray get() = originalBytes.copyOf()

    public val size: Int get() = originalBytes.size

    public val tagCount: Int
        get() = if (originalBytes.size < HEADER_AND_COUNT_SIZE) 0 else readU32(originalBytes, TAG_COUNT_OFFSET)

    public val hasTrc: Boolean get() = colorProfile.transferFunction != null

    public val hasToXyzD50: Boolean get() = colorProfile.toXyzD50 != null

    override fun equals(other: Any?): Boolean =
        this === other || (other is IccProfile && colorProfile == other.colorProfile && originalBytes.contentEquals(other.originalBytes))

    override fun hashCode(): Int = 31 * colorProfile.hashCode() + originalBytes.contentHashCode()

    override fun toString(): String = "IccProfile(size=$size, colorProfile=$colorProfile)"

    public companion object {
        /** Parses an ICC artifact while retaining a snapshot of the original bytes. */
        public fun parse(
            bytes: ByteArray,
            limits: IccParseLimits = IccParseLimits(),
        ): IccProfileParseResult {
            val snapshot = bytes.copyOf()
            return when (val result = IccProfileParser.parse(snapshot, limits)) {
                is ColorProfileParseResult.Success -> IccProfileParseResult.Success(IccProfile(result.profile, snapshot))
                is ColorProfileParseResult.Failure -> IccProfileParseResult.Failure(result.code, result.message)
            }
        }

        /** Serializes the supported Matrix/TRC subset and retains the resulting encoded artifact. */
        public fun fromMatrixTrc(profile: ColorProfile): IccProfile =
            parse(IccProfileWriter.writeMatrixTrc(profile)).getOrThrow()

    }
}

/** Result of parsing an [IccProfile] while retaining its encoded provenance. */
public sealed interface IccProfileParseResult {
    public data class Success(public val profile: IccProfile) : IccProfileParseResult

    public data class Failure(
        public val code: String,
        public val message: String = code,
    ) : IccProfileParseResult

    public fun getOrThrow(): IccProfile = when (this) {
        is Success -> profile
        is Failure -> throw IllegalArgumentException("$code: $message")
    }

    public fun failureOrNull(): Failure? = this as? Failure
}

private const val TAG_COUNT_OFFSET: Int = 128
private const val HEADER_AND_COUNT_SIZE: Int = 132

private fun readU32(bytes: ByteArray, offset: Int): Int =
    ((bytes[offset].toInt() and 0xff) shl 24) or
        ((bytes[offset + 1].toInt() and 0xff) shl 16) or
        ((bytes[offset + 2].toInt() and 0xff) shl 8) or
        (bytes[offset + 3].toInt() and 0xff)
