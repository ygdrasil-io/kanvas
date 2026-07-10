package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccTransformPipeline
import org.graphiks.kanvas.color.icc.parametricCurveValidationError
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import kotlin.math.pow

public enum class AlphaType {
    OPAQUE,
    UNPREMULTIPLIED,
    PREMULTIPLIED,
}

public data class ColorTransformRequest(
    public val source: ColorProfile,
    public val destination: ColorProfile,
    public val alphaType: AlphaType,
)

public sealed interface ColorTransformCompileResult {
    public data class Success(public val transform: CompiledColorTransform) : ColorTransformCompileResult

    public data class Failure(
        public val code: String,
        public val message: String = code,
    ) : ColorTransformCompileResult

    public fun getOrThrow(): CompiledColorTransform = when (this) {
        is Success -> transform
        is Failure -> throw IllegalArgumentException("$code: $message")
    }

    public fun failureOrNull(): Failure? = this as? Failure
}

public class CompiledColorTransform internal constructor(
    public val request: ColorTransformRequest,
    private val plan: CompiledRgbPlan,
) {
    /** Applies the compiled transform to RGBA float pixels in place. */
    public fun apply(pixels: FloatArray, pixelCount: Int) {
        require(pixelCount >= 0) { "pixelCount must not be negative" }
        require(pixelCount.toLong() * CHANNELS_PER_PIXEL <= pixels.size.toLong()) {
            "pixels must contain at least $pixelCount RGBA pixels"
        }
        repeat(pixelCount) { pixel -> plan.apply(pixels, pixel * CHANNELS_PER_PIXEL) }
    }

    private companion object {
        const val CHANNELS_PER_PIXEL: Int = 4
    }
}

public object ColorTransform {
    public fun compile(
        source: ColorProfile,
        destination: ColorProfile,
        alphaType: AlphaType,
    ): ColorTransformCompileResult = compile(ColorTransformRequest(source, destination, alphaType))

    public fun compile(request: ColorTransformRequest): ColorTransformCompileResult {
        if (request.alphaType == AlphaType.PREMULTIPLIED) {
            return ColorTransformCompileResult.Failure("color.alpha.premultiplied.unsupported")
        }
        unsupportedProfileFailure(request.source, source = true)?.let { return it }
        unsupportedProfileFailure(request.destination, source = false)?.let { return it }
        if (request.source == request.destination) {
            return ColorTransformCompileResult.Success(CompiledColorTransform(request, NoOpRgbPlan))
        }

        val sourceStage = request.source.toPcs?.let(::LutEndpointStage) ?: MatrixToPcsStage(
            matrixValues(assertNotNull(request.source.toXyzD50)),
            assertNotNull(request.source.transferFunction),
        )
        val destinationStage = request.destination.fromPcs?.let(::LutEndpointStage) ?: PcsToMatrixStage(
            invert3x3(matrixValues(assertNotNull(request.destination.toXyzD50)))!!,
            assertNotNull(request.destination.transferFunction),
        )
        return ColorTransformCompileResult.Success(
            CompiledColorTransform(request, CompositeRgbPlan(sourceStage, destinationStage)),
        )
    }

    private fun unsupportedProfileFailure(
        profile: ColorProfile,
        source: Boolean,
    ): ColorTransformCompileResult.Failure? {
        profile.unsupportedCode?.let { return ColorTransformCompileResult.Failure(it) }
        if (!profile.isSupportedTransformEndpoint) {
            return ColorTransformCompileResult.Failure("color.profile.unsupported")
        }
        if (profile.hasLut) {
            if (source && profile.toPcs == null) return ColorTransformCompileResult.Failure("icc.lut.a2b.missing")
            if (!source && profile.fromPcs == null) return ColorTransformCompileResult.Failure("icc.lut.b2a.missing")
            return null
        }

        val matrix = profile.toXyzD50 ?: return ColorTransformCompileResult.Failure("color.profile.unsupported")
        val values = matrixValues(matrix)
        if (!values.all(Float::isFinite) || invert3x3(values) == null) {
            return ColorTransformCompileResult.Failure("color.profile.matrix")
        }
        val transferFunction = profile.transferFunction
            ?: return ColorTransformCompileResult.Failure("color.profile.unsupported")
        if (!validTransferFunction(transferFunction)) {
            return ColorTransformCompileResult.Failure("color.profile.transfer")
        }
        return null
    }
}

internal interface CompiledRgbPlan {
    fun apply(pixels: FloatArray, offset: Int)
}

private object NoOpRgbPlan : CompiledRgbPlan {
    override fun apply(pixels: FloatArray, offset: Int) = Unit
}

private class CompositeRgbPlan(
    private val sourceToPcs: EndpointStage,
    private val pcsToDestination: EndpointStage,
) : CompiledRgbPlan {
    override fun apply(pixels: FloatArray, offset: Int) {
        val pcs = FloatArray(3)
        val destination = FloatArray(3)
        sourceToPcs.apply(pixels, offset, pcs)
        pcsToDestination.apply(pcs, 0, destination)
        repeat(3) { channel ->
            val value = destination[channel]
            pixels[offset + channel] = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
        }
    }
}

private interface EndpointStage {
    fun apply(input: FloatArray, inputOffset: Int, output: FloatArray)
}

private class LutEndpointStage(
    private val pipeline: IccTransformPipeline,
) : EndpointStage {
    override fun apply(input: FloatArray, inputOffset: Int, output: FloatArray) {
        pipeline.apply(input, inputOffset, output, 0)
    }
}

private class MatrixToPcsStage(
    matrix: FloatArray,
    private val transferFunction: SkcmsTransferFunction,
) : EndpointStage {
    private val matrix: FloatArray = matrix.copyOf()

    override fun apply(input: FloatArray, inputOffset: Int, output: FloatArray) {
        val linear = FloatArray(3) { channel -> decode(transferFunction, input[inputOffset + channel]) }
        multiply3x3(matrix, linear, output)
    }
}

private class PcsToMatrixStage(
    inverseMatrix: FloatArray,
    private val transferFunction: SkcmsTransferFunction,
) : EndpointStage {
    private val inverseMatrix: FloatArray = inverseMatrix.copyOf()

    override fun apply(input: FloatArray, inputOffset: Int, output: FloatArray) {
        val pcs = floatArrayOf(input[inputOffset], input[inputOffset + 1], input[inputOffset + 2])
        val linear = FloatArray(3)
        multiply3x3(inverseMatrix, pcs, linear)
        repeat(3) { channel -> output[channel] = encode(transferFunction, linear[channel]) }
    }
}

private fun decode(transferFunction: SkcmsTransferFunction, encoded: Float): Float {
    val x = if (encoded.isFinite()) encoded.coerceIn(0f, 1f) else 0f
    val value = if (x >= transferFunction.d) {
        (transferFunction.a * x + transferFunction.b).pow(transferFunction.g) + transferFunction.e
    } else {
        transferFunction.c * x + transferFunction.f
    }
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}

private fun encode(transferFunction: SkcmsTransferFunction, linear: Float): Float {
    val y = if (linear.isFinite()) linear.coerceIn(0f, 1f) else 0f
    val boundary = transferFunction.d.coerceIn(0f, 1f)
    val lowerLimit = (transferFunction.c * boundary + transferFunction.f).coerceIn(0f, 1f)
    val upperLimit = decode(transferFunction, boundary)
    val value = when {
        y < lowerLimit && transferFunction.c > 0f -> (y - transferFunction.f) / transferFunction.c
        y < upperLimit -> boundary
        else -> ((y - transferFunction.e).coerceAtLeast(0f).pow(1f / transferFunction.g) -
            transferFunction.b) / transferFunction.a
    }
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}

private fun validTransferFunction(transferFunction: SkcmsTransferFunction): Boolean {
    val parameters = floatArrayOf(
        transferFunction.g,
        transferFunction.a,
        transferFunction.b,
        transferFunction.c,
        transferFunction.d,
        transferFunction.e,
        transferFunction.f,
    )
    return parametricCurveValidationError(4, parameters) == null
}

private fun matrixValues(matrix: SkcmsMatrix3x3): FloatArray = FloatArray(9) { index ->
    matrix[index / 3, index % 3]
}

private fun multiply3x3(matrix: FloatArray, input: FloatArray, output: FloatArray) {
    repeat(3) { row ->
        val base = row * 3
        output[row] = matrix[base] * input[0] + matrix[base + 1] * input[1] + matrix[base + 2] * input[2]
    }
}

private fun invert3x3(matrix: FloatArray): FloatArray? {
    val a = matrix[0].toDouble()
    val b = matrix[1].toDouble()
    val c = matrix[2].toDouble()
    val d = matrix[3].toDouble()
    val e = matrix[4].toDouble()
    val f = matrix[5].toDouble()
    val g = matrix[6].toDouble()
    val h = matrix[7].toDouble()
    val i = matrix[8].toDouble()
    val determinant = a * (e * i - f * h) - b * (d * i - f * g) + c * (d * h - e * g)
    if (!determinant.isFinite() || determinant == 0.0) return null
    val inverseDeterminant = 1.0 / determinant
    val values = doubleArrayOf(
        (e * i - f * h) * inverseDeterminant,
        (c * h - b * i) * inverseDeterminant,
        (b * f - c * e) * inverseDeterminant,
        (f * g - d * i) * inverseDeterminant,
        (a * i - c * g) * inverseDeterminant,
        (c * d - a * f) * inverseDeterminant,
        (d * h - e * g) * inverseDeterminant,
        (b * g - a * h) * inverseDeterminant,
        (a * e - b * d) * inverseDeterminant,
    )
    if (values.any { !it.isFinite() || kotlin.math.abs(it) > Float.MAX_VALUE.toDouble() }) return null
    return FloatArray(9) { values[it].toFloat() }
}

private fun <T : Any> assertNotNull(value: T?): T = checkNotNull(value)
