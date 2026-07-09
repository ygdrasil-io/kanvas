package org.graphiks.kanvas.color

import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.ConsistentCopyVisibility

public enum class ColorModel {
    RGB,
    GRAY,
}

public sealed interface ColorProfileParseResult {
    public data class Success(public val profile: ColorProfile) : ColorProfileParseResult

    public data class Failure(
        public val code: String,
        public val message: String = code,
    ) : ColorProfileParseResult

    public fun getOrThrow(): ColorProfile = when (this) {
        is Success -> profile
        is Failure -> throw IllegalArgumentException("$code: $message")
    }

    public fun failureOrNull(): Failure? = this as? Failure
}

/** An immutable description of a color profile supported by the transform pipeline. */
@ConsistentCopyVisibility
public data class ColorProfile private constructor(
    public val colorModel: ColorModel,
    private val matrix: Matrix3x3Value?,
    public val transferFunction: SkcmsTransferFunction? = null,
    public val unsupportedCode: String? = null,
) {
    public constructor(
        colorModel: ColorModel,
        toXyzD50: SkcmsMatrix3x3? = null,
        transferFunction: SkcmsTransferFunction? = null,
        unsupportedCode: String? = null,
    ) : this(
        colorModel = colorModel,
        matrix = toXyzD50?.let(Matrix3x3Value::copyOf),
        transferFunction = transferFunction,
        unsupportedCode = unsupportedCode,
    )

    /** Returns a fresh matrix copy so callers cannot mutate this profile. */
    public val toXyzD50: SkcmsMatrix3x3?
        get() = matrix?.toSkcmsMatrix3x3()

    public val hasMatrixTrc: Boolean
        get() = unsupportedCode == null && matrix != null && transferFunction != null

    internal val isSupportedByTask1: Boolean
        get() = colorModel == ColorModel.RGB && hasMatrixTrc

    public companion object {
        /** Creates a profile marker that must cause typed transform refusal. */
        public fun unsupported(code: String): ColorProfile {
            require(code.isNotBlank()) { "Unsupported profile code must not be blank" }
            return ColorProfile(colorModel = ColorModel.RGB, unsupportedCode = code)
        }
    }
}

private data class Matrix3x3Value(
    val r0c0: Float,
    val r0c1: Float,
    val r0c2: Float,
    val r1c0: Float,
    val r1c1: Float,
    val r1c2: Float,
    val r2c0: Float,
    val r2c1: Float,
    val r2c2: Float,
) {
    fun toSkcmsMatrix3x3(): SkcmsMatrix3x3 = SkcmsMatrix3x3.of(
        r0c0, r0c1, r0c2,
        r1c0, r1c1, r1c2,
        r2c0, r2c1, r2c2,
    )

    companion object {
        fun copyOf(matrix: SkcmsMatrix3x3): Matrix3x3Value = Matrix3x3Value(
            r0c0 = matrix[0, 0],
            r0c1 = matrix[0, 1],
            r0c2 = matrix[0, 2],
            r1c0 = matrix[1, 0],
            r1c1 = matrix[1, 1],
            r1c2 = matrix[1, 2],
            r2c0 = matrix[2, 0],
            r2c1 = matrix[2, 1],
            r2c2 = matrix[2, 2],
        )
    }
}

public object ColorProfiles {
    private val sRgb: ColorProfile = ColorProfile(
        colorModel = ColorModel.RGB,
        toXyzD50 = SkcmsMatrix3x3.of(
            0.43606567f, 0.3851471f, 0.1430664f,
            0.2224884f, 0.71687317f, 0.06060791f,
            0.01391602f, 0.097076416f, 0.71409607f,
        ),
        transferFunction = SkcmsTransferFunction(
            g = 2.4f,
            a = 1f / 1.055f,
            b = 0.055f / 1.055f,
            c = 1f / 12.92f,
            d = 0.04045f,
            e = 0f,
            f = 0f,
        ),
    )

    private val displayP3: ColorProfile = ColorProfile(
        colorModel = ColorModel.RGB,
        toXyzD50 = SkcmsMatrix3x3.of(
            0.51512146f, 0.29197693f, 0.15710449f,
            0.24119568f, 0.6922455f, 0.0665741f,
            -0.0010528564f, 0.041885376f, 0.7840729f,
        ),
        transferFunction = sRgb.transferFunction,
    )

    public fun sRGB(): ColorProfile = sRgb

    public fun displayP3(): ColorProfile = displayP3
}
