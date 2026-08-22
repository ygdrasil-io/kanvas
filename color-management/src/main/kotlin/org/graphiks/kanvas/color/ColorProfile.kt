package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccTransformPipeline
import org.graphiks.math.color.ColorMatrix3x3F32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.hlgInverseOetf as normalizedHlgInverseOetf
import org.graphiks.math.color.hlgOetf as normalizedHlgOetf
import org.graphiks.math.color.pqEotf as normalizedPqEotf
import org.graphiks.math.color.pqInverseEotf as normalizedPqInverseEotf
import kotlin.ConsistentCopyVisibility
import kotlin.math.pow

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
    private val matrix: ColorMatrix3x3F32?,
    public val transferFunction: ColorTransferFunction.Parametric? = null,
    public val unsupportedCode: String? = null,
    private val lut: LutProfileValue? = null,
    internal val hdrTransferFunction: HdrTransferFunction? = null,
) {
    public constructor(
        colorModel: ColorModel,
        toXyzD50: ColorMatrix3x3F32? = null,
        transferFunction: ColorTransferFunction.Parametric? = null,
        unsupportedCode: String? = null,
    ) : this(
        colorModel = colorModel,
        matrix = toXyzD50,
        transferFunction = transferFunction,
        unsupportedCode = unsupportedCode,
        lut = null,
        hdrTransferFunction = null,
    )

    public val toXyzD50: ColorMatrix3x3F32?
        get() = matrix

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
            toXyzD50: ColorMatrix3x3F32,
            transferFunction: HdrTransferFunction,
        ): ColorProfile = ColorProfile(
            colorModel = ColorModel.RGB,
            matrix = toXyzD50,
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
                output[channel] = (normalizedPqEotf(finiteUnit(input[inputOffset + channel])) * PQ_PEAK_NITS).toFloat()
            }
            HLG -> decodeHlg(input, inputOffset, output)
        }
    }

    fun encode(inputNits: FloatArray, output: FloatArray) {
        require(inputNits.size >= RGB_CHANNELS) { "inputNits must contain three RGB components" }
        require(output.size >= RGB_CHANNELS) { "output must contain three RGB components" }
        when (this) {
            PQ -> repeat(RGB_CHANNELS) { channel ->
                output[channel] = normalizedPqInverseEotf(
                    (nonNegativeFinite(inputNits[channel]) / PQ_PEAK_NITS).coerceAtMost(1.0),
                ).toFloat()
            }
            HLG -> encodeHlg(inputNits, output)
        }
    }

    private fun decodeHlg(input: FloatArray, inputOffset: Int, output: FloatArray) {
        val scene = DoubleArray(RGB_CHANNELS) { channel -> normalizedHlgInverseOetf(finiteUnit(input[inputOffset + channel])) }
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
            output[channel] = normalizedHlgOetf(display[channel] / scale).coerceIn(0.0, 1.0).toFloat()
        }
    }

    private companion object {
        const val RGB_CHANNELS: Int = 3
        const val HLG_PEAK_NITS: Double = 1000.0
        const val HLG_SYSTEM_GAMMA: Double = 1.2
        val REC2020_LUMINANCE: DoubleArray = doubleArrayOf(0.2627, 0.6780, 0.0593)

        fun finiteUnit(value: Float): Double = if (value.isFinite()) value.coerceIn(0f, 1f).toDouble() else 0.0

        fun nonNegativeFinite(value: Float): Double = if (value.isFinite()) value.coerceAtLeast(0f).toDouble() else 0.0

        fun dotRec2020(rgb: DoubleArray): Double =
            REC2020_LUMINANCE[0] * rgb[0] + REC2020_LUMINANCE[1] * rgb[1] + REC2020_LUMINANCE[2] * rgb[2]
    }
}

internal const val PQ_PEAK_NITS: Double = 10_000.0

private data class LutProfileValue(
    val toPcs: IccTransformPipeline?,
    val fromPcs: IccTransformPipeline?,
)

public object ColorProfiles {
    private val sRgb: ColorProfile = ColorProfile(
        colorModel = ColorModel.RGB,
        toXyzD50 = ColorMatrix3x3F32.of(
            0.43606567f, 0.3851471f, 0.1430664f,
            0.2224884f, 0.71687317f, 0.06060791f,
            0.01391602f, 0.097076416f, 0.71409607f,
        ),
        transferFunction = ColorTransferFunction.sRgb,
    )

    private val displayP3: ColorProfile = ColorProfile(
        colorModel = ColorModel.RGB,
        toXyzD50 = ColorMatrix3x3F32.of(
            0.51512146f, 0.29197693f, 0.15710449f,
            0.24119568f, 0.6922455f, 0.0665741f,
            -0.0010528564f, 0.041885376f, 0.7840729f,
        ),
        transferFunction = sRgb.transferFunction,
    )

    private val rec2020: ColorProfile = ColorProfile(
        colorModel = ColorModel.RGB,
        toXyzD50 = ColorMatrix3x3F32.of(
            0.673459f, 0.165661f, 0.125100f,
            0.279033f, 0.675338f, 0.0456288f,
            -0.00193139f, 0.0299794f, 0.797162f,
        ),
        transferFunction = ColorTransferFunction.rec2020,
    )

    public fun sRGB(): ColorProfile = sRgb

    public fun displayP3(): ColorProfile = displayP3

    public fun rec2020(): ColorProfile = rec2020
}
