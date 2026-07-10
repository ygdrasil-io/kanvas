package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccTransformPipeline
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.ConsistentCopyVisibility
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

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
    private val lut: LutProfileValue? = null,
    internal val hdrTransferFunction: HdrTransferFunction? = null,
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
        lut = null,
        hdrTransferFunction = null,
    )

    /** Returns a fresh matrix copy so callers cannot mutate this profile. */
    public val toXyzD50: SkcmsMatrix3x3?
        get() = matrix?.toSkcmsMatrix3x3()

    public val hasMatrixTrc: Boolean
        get() = unsupportedCode == null && matrix != null && (transferFunction != null || hdrTransferFunction != null)

    /** Whether this profile uses a transfer function with absolute HDR display-light semantics. */
    public val isHdr: Boolean
        get() = hdrTransferFunction != null

    internal val hasLut: Boolean
        get() = unsupportedCode == null && lut != null

    internal val toPcs: IccTransformPipeline?
        get() = lut?.toPcs

    internal val fromPcs: IccTransformPipeline?
        get() = lut?.fromPcs

    internal val isSupportedTransformEndpoint: Boolean
        get() = colorModel == ColorModel.RGB && (hasMatrixTrc || hasLut)

    public companion object {
        /** Creates a profile marker that must cause typed transform refusal. */
        public fun unsupported(code: String): ColorProfile {
            require(code.isNotBlank()) { "Unsupported profile code must not be blank" }
            return ColorProfile(colorModel = ColorModel.RGB, unsupportedCode = code)
        }

        internal fun lut(
            toPcs: IccTransformPipeline?,
            fromPcs: IccTransformPipeline?,
        ): ColorProfile {
            require(toPcs != null || fromPcs != null) { "A LUT profile requires at least one direction" }
            return ColorProfile(
                colorModel = ColorModel.RGB,
                matrix = null,
                transferFunction = null,
                unsupportedCode = null,
                lut = LutProfileValue(toPcs, fromPcs),
                hdrTransferFunction = null,
            )
        }

        internal fun hdr(
            toXyzD50: SkcmsMatrix3x3,
            transferFunction: HdrTransferFunction,
        ): ColorProfile = ColorProfile(
            colorModel = ColorModel.RGB,
            matrix = Matrix3x3Value.copyOf(toXyzD50),
            transferFunction = null,
            unsupportedCode = null,
            lut = null,
            hdrTransferFunction = transferFunction,
        )
    }
}

internal enum class HdrTransferFunction {
    PQ,
    HLG;

    fun decode(input: FloatArray, inputOffset: Int, output: FloatArray) {
        require(inputOffset >= 0 && inputOffset.toLong() + RGB_CHANNELS <= input.size.toLong()) {
            "input must contain three RGB components at inputOffset"
        }
        require(output.size >= RGB_CHANNELS) { "output must contain three RGB components" }
        when (this) {
            PQ -> repeat(RGB_CHANNELS) { channel ->
                output[channel] = pqEotfNits(finiteUnit(input[inputOffset + channel])).toFloat()
            }
            HLG -> decodeHlg(input, inputOffset, output)
        }
    }

    fun encode(inputNits: FloatArray, output: FloatArray) {
        require(inputNits.size >= RGB_CHANNELS) { "inputNits must contain three RGB components" }
        require(output.size >= RGB_CHANNELS) { "output must contain three RGB components" }
        when (this) {
            PQ -> repeat(RGB_CHANNELS) { channel ->
                output[channel] = pqInverseEotf(nonNegativeFinite(inputNits[channel])).toFloat()
            }
            HLG -> encodeHlg(inputNits, output)
        }
    }

    private fun decodeHlg(input: FloatArray, inputOffset: Int, output: FloatArray) {
        val scene = DoubleArray(RGB_CHANNELS) { channel -> hlgInverseOetf(finiteUnit(input[inputOffset + channel])) }
        val sceneLuminance = dotRec2020(scene)
        if (sceneLuminance <= 0.0) {
            output.fill(0f, 0, RGB_CHANNELS)
            return
        }
        val scale = HLG_PEAK_NITS * sceneLuminance.pow(HLG_SYSTEM_GAMMA - 1.0)
        repeat(RGB_CHANNELS) { channel -> output[channel] = (scene[channel] * scale).toFloat() }
    }

    private fun encodeHlg(inputNits: FloatArray, output: FloatArray) {
        val display = DoubleArray(RGB_CHANNELS) { channel -> nonNegativeFinite(inputNits[channel]) }
        val displayLuminance = dotRec2020(display)
        if (displayLuminance <= 0.0) {
            output.fill(0f, 0, RGB_CHANNELS)
            return
        }
        val sceneLuminance = (displayLuminance / HLG_PEAK_NITS).pow(1.0 / HLG_SYSTEM_GAMMA)
        val scale = HLG_PEAK_NITS * sceneLuminance.pow(HLG_SYSTEM_GAMMA - 1.0)
        repeat(RGB_CHANNELS) { channel ->
            output[channel] = hlgOetf(display[channel] / scale).coerceIn(0.0, 1.0).toFloat()
        }
    }

    private companion object {
        const val RGB_CHANNELS: Int = 3
        const val HLG_A: Double = 0.17883277
        const val HLG_B: Double = 0.28466892
        const val HLG_C: Double = 0.55991073
        const val HLG_PEAK_NITS: Double = 1000.0
        const val HLG_SYSTEM_GAMMA: Double = 1.2
        val REC2020_LUMINANCE: DoubleArray = doubleArrayOf(0.2627, 0.6780, 0.0593)

        fun finiteUnit(value: Float): Double = if (value.isFinite()) value.coerceIn(0f, 1f).toDouble() else 0.0

        fun nonNegativeFinite(value: Float): Double = if (value.isFinite()) value.coerceAtLeast(0f).toDouble() else 0.0

        fun hlgInverseOetf(encoded: Double): Double = if (encoded <= 0.5) {
            encoded * encoded / 3.0
        } else {
            (exp((encoded - HLG_C) / HLG_A) + HLG_B) / 12.0
        }

        fun hlgOetf(sceneLinear: Double): Double = if (sceneLinear <= 1.0 / 12.0) {
            sqrt(3.0 * sceneLinear.coerceAtLeast(0.0))
        } else {
            HLG_A * ln(12.0 * sceneLinear - HLG_B) + HLG_C
        }

        fun dotRec2020(rgb: DoubleArray): Double =
            REC2020_LUMINANCE[0] * rgb[0] + REC2020_LUMINANCE[1] * rgb[1] + REC2020_LUMINANCE[2] * rgb[2]
    }
}

internal fun pqEotfNits(encoded: Double): Double {
    if (!encoded.isFinite() || encoded <= 0.0) return 0.0
    val signal = encoded.coerceAtMost(1.0).pow(1.0 / PQ_M2)
    val numerator = (signal - PQ_C1).coerceAtLeast(0.0)
    val denominator = PQ_C2 - PQ_C3 * signal
    if (denominator <= 0.0) return PQ_PEAK_NITS
    return PQ_PEAK_NITS * (numerator / denominator).pow(1.0 / PQ_M1)
}

internal fun pqInverseEotf(luminanceNits: Double): Double {
    if (!luminanceNits.isFinite() || luminanceNits <= 0.0) return 0.0
    val normalized = (luminanceNits / PQ_PEAK_NITS).coerceAtMost(1.0)
    val power = normalized.pow(PQ_M1)
    return ((PQ_C1 + PQ_C2 * power) / (1.0 + PQ_C3 * power)).pow(PQ_M2)
}

private const val PQ_M1: Double = 2610.0 / 16384.0
private const val PQ_M2: Double = 2523.0 / 4096.0 * 128.0
private const val PQ_C1: Double = 3424.0 / 4096.0
private const val PQ_C2: Double = 2413.0 / 4096.0 * 32.0
private const val PQ_C3: Double = 2392.0 / 4096.0 * 32.0
internal const val PQ_PEAK_NITS: Double = 10_000.0

private data class LutProfileValue(
    val toPcs: IccTransformPipeline?,
    val fromPcs: IccTransformPipeline?,
)

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

    private val rec2020: ColorProfile = ColorProfile(
        colorModel = ColorModel.RGB,
        toXyzD50 = SkcmsMatrix3x3.of(
            0.673459f, 0.165661f, 0.125100f,
            0.279033f, 0.675338f, 0.0456288f,
            -0.00193139f, 0.0299794f, 0.797162f,
        ),
        transferFunction = SkcmsTransferFunction(
            g = 2.22222f,
            a = 0.909672f,
            b = 0.0903276f,
            c = 0.222222f,
            d = 0.0812429f,
            e = 0f,
            f = 0f,
        ),
    )

    public fun sRGB(): ColorProfile = sRgb

    public fun displayP3(): ColorProfile = displayP3

    public fun rec2020(): ColorProfile = rec2020
}
