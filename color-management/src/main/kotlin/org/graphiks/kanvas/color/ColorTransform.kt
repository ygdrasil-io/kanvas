package org.graphiks.kanvas.color

import org.graphiks.kanvas.color.icc.IccTransformPipeline
import org.graphiks.kanvas.color.hdr.Bt2390ToneMapper
import org.graphiks.kanvas.color.hdr.ToneMapper
import org.graphiks.math.color.ColorMatrix3x3F32
import org.graphiks.math.color.ColorTransferFunction
import org.graphiks.math.color.toEncoded

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
        if (request.source == request.destination) {
            unsupportedProfileFailure(request.source, source = null)?.let { return it }
            return ColorTransformCompileResult.Success(CompiledColorTransform(request, NoOpRgbPlan))
        }
        unsupportedProfileFailure(request.source, source = true)?.let { return it }
        unsupportedProfileFailure(request.destination, source = false)?.let { return it }

        if (request.alphaType == AlphaType.PREMULTIPLIED && (request.source.hasLut || request.destination.hasLut)) {
            return ColorTransformCompileResult.Failure("color.alpha.premultiplied.unsupported")
        }
        if ((request.source.isHdr || request.destination.isHdr) &&
            (request.source.hasLut || request.destination.hasLut)
        ) {
            return ColorTransformCompileResult.Failure("color.hdr.lut.unsupported")
        }

        val sourceMatrix = request.source.toXyzD50
        val destinationMatrix = request.destination.toXyzD50
        if (sourceMatrix != null && destinationMatrix != null) {
            val destinationInverse = requireNotNull(destinationMatrix.inverseOrNull())
            if (request.source.isHdr || request.destination.isHdr) {
                val toneMapper = if (request.source.isHdr && !request.destination.isHdr) {
                    Bt2390ToneMapper(targetPeakNits = SDR_REFERENCE_WHITE_NITS.toDouble())
                } else {
                    null
                }
                val (sourceToWorking, workingToDestination) = if (toneMapper != null) {
                    val rec2020ToXyzD50 = requireNotNull(ColorProfiles.rec2020().toXyzD50)
                    val sourceToRec2020 = if (sourceMatrix == rec2020ToXyzD50) {
                        ColorMatrix3x3F32.Identity
                    } else {
                        requireNotNull(rec2020ToXyzD50.inverseOrNull()).concat(sourceMatrix)
                    }
                    sourceToRec2020 to destinationInverse.concat(rec2020ToXyzD50)
                } else {
                    sourceMatrix to destinationInverse
                }
                return ColorTransformCompileResult.Success(
                    CompiledColorTransform(
                        request,
                        HdrMatrixColorTransform(
                            sourceToWorking = sourceToWorking,
                            workingToDestination = workingToDestination,
                            sourceTransferFunction = request.source.transferFunction,
                            sourceHdrTransferFunction = request.source.hdrTransferFunction,
                            destinationTransferFunction = request.destination.transferFunction,
                            destinationHdrTransferFunction = request.destination.hdrTransferFunction,
                            alphaType = request.alphaType,
                            toneMapper = toneMapper,
                        ),
                    ),
                )
            }
            return ColorTransformCompileResult.Success(
                CompiledColorTransform(
                    request,
                    MatrixColorTransform(
                        sourceToXyzD50 = sourceMatrix,
                        destinationFromXyzD50 = destinationInverse,
                        sourceTransferFunction = assertNotNull(request.source.transferFunction),
                        destinationTransferFunction = assertNotNull(request.destination.transferFunction),
                        alphaType = request.alphaType,
                    ),
                ),
            )
        }

        val sourceStage = request.source.toPcs?.let(::LutEndpointStage) ?: MatrixToPcsStage(
            assertNotNull(sourceMatrix),
            assertNotNull(request.source.transferFunction),
        )
        val destinationStage = request.destination.fromPcs?.let(::LutEndpointStage) ?: PcsToMatrixStage(
            requireNotNull(assertNotNull(destinationMatrix).inverseOrNull()),
            assertNotNull(request.destination.transferFunction),
        )
        return ColorTransformCompileResult.Success(
            CompiledColorTransform(request, CompositeRgbPlan(sourceStage, destinationStage)),
        )
    }

    private fun unsupportedProfileFailure(
        profile: ColorProfile,
        source: Boolean?,
    ): ColorTransformCompileResult.Failure? {
        profile.unsupportedCode?.let { return ColorTransformCompileResult.Failure(it) }
        if (!profile.isSupportedTransformEndpoint) {
            return ColorTransformCompileResult.Failure("color.profile.unsupported")
        }
        if (profile.hasLut) {
            if (source == true && profile.toPcs == null) return ColorTransformCompileResult.Failure("icc.lut.a2b.missing")
            if (source == false && profile.fromPcs == null) return ColorTransformCompileResult.Failure("icc.lut.b2a.missing")
            return null
        }

        val matrix = profile.toXyzD50 ?: return ColorTransformCompileResult.Failure("color.profile.unsupported")
        if (!matrix.toFloatArray().all(Float::isFinite) || matrix.inverseOrNull() == null) {
            return ColorTransformCompileResult.Failure("color.profile.matrix")
        }
        val transferFunction = profile.transferFunction
        if (profile.hdrTransferFunction != null) return null
        if (transferFunction == null) return ColorTransformCompileResult.Failure("color.profile.unsupported")
        if (!validTransferFunction(transferFunction)) {
            return ColorTransformCompileResult.Failure("color.profile.transfer")
        }
        return null
    }
}

private class HdrMatrixColorTransform(
    private val sourceToWorking: ColorMatrix3x3F32,
    private val workingToDestination: ColorMatrix3x3F32,
    private val sourceTransferFunction: ColorTransferFunction.Parametric?,
    private val sourceHdrTransferFunction: HdrTransferFunction?,
    private val destinationTransferFunction: ColorTransferFunction.Parametric?,
    private val destinationHdrTransferFunction: HdrTransferFunction?,
    private val alphaType: AlphaType,
    private val toneMapper: ToneMapper?,
) : CompiledRgbPlan {
    private val destinationUnitEncoder = destinationTransferFunction?.let(::UnitFiniteTransferEncoder)

    init {
        require((sourceTransferFunction == null) != (sourceHdrTransferFunction == null)) {
            "source must have exactly one transfer function"
        }
        require((destinationTransferFunction == null) != (destinationHdrTransferFunction == null)) {
            "destination must have exactly one transfer function"
        }
    }

    override fun apply(pixels: FloatArray, offset: Int) {
        val alpha = pixels[offset + ALPHA_OFFSET]
        if (alphaType == AlphaType.PREMULTIPLIED && (!alpha.isFinite() || alpha == 0f)) {
            pixels.fill(0f, offset, offset + RGB_CHANNELS)
            return
        }
        val unpremultiplied = FloatArray(RGB_CHANNELS) { channel ->
            if (alphaType == AlphaType.PREMULTIPLIED) pixels[offset + channel] / alpha else pixels[offset + channel]
        }
        val sourceLinearNits = FloatArray(RGB_CHANNELS)
        if (sourceHdrTransferFunction != null) {
            sourceHdrTransferFunction.decode(unpremultiplied, 0, sourceLinearNits)
        } else {
            repeat(RGB_CHANNELS) { channel ->
                sourceLinearNits[channel] = decodeUnitFinite(checkNotNull(sourceTransferFunction), unpremultiplied[channel]) *
                    SDR_REFERENCE_WHITE_NITS
            }
        }

        val workingLinear = FloatArray(RGB_CHANNELS)
        val destinationLinear = FloatArray(RGB_CHANNELS)
        sourceToWorking.map(sourceLinearNits, 0, workingLinear, 0)
        toneMapper?.map(workingLinear, 0)
        workingToDestination.map(workingLinear, 0, destinationLinear, 0)

        val encoded = FloatArray(RGB_CHANNELS)
        if (destinationHdrTransferFunction != null) {
            destinationHdrTransferFunction.encode(destinationLinear, encoded)
        } else {
            repeat(RGB_CHANNELS) { channel ->
                encoded[channel] = encodeUnitFinite(
                    checkNotNull(destinationUnitEncoder),
                    clipLinearSdrDestination(destinationLinear[channel]),
                )
            }
        }
        val premultiply = if (alphaType == AlphaType.PREMULTIPLIED) alpha else 1f
        repeat(RGB_CHANNELS) { channel ->
            val value = encoded[channel]
            pixels[offset + channel] = (if (value.isFinite()) value.coerceIn(0f, 1f) else 0f) * premultiply
        }
    }

    private companion object {
        const val RGB_CHANNELS: Int = 3
        const val ALPHA_OFFSET: Int = 3
        /** Component clipping is the explicit post-tone-map destination-gamut policy. */
        fun clipLinearSdrDestination(value: Float): Float =
            if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
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
    private val matrix: ColorMatrix3x3F32,
    private val transferFunction: ColorTransferFunction.Parametric,
) : EndpointStage {
    override fun apply(input: FloatArray, inputOffset: Int, output: FloatArray) {
        val linear = FloatArray(3) { channel -> decodeUnitFinite(transferFunction, input[inputOffset + channel]) }
        matrix.map(linear, 0, output, 0)
    }
}

private class PcsToMatrixStage(
    private val inverseMatrix: ColorMatrix3x3F32,
    private val transferFunction: ColorTransferFunction.Parametric,
) : EndpointStage {
    private val unitEncoder = UnitFiniteTransferEncoder(transferFunction)

    override fun apply(input: FloatArray, inputOffset: Int, output: FloatArray) {
        val pcs = floatArrayOf(input[inputOffset], input[inputOffset + 1], input[inputOffset + 2])
        val linear = FloatArray(3)
        inverseMatrix.map(pcs, 0, linear, 0)
        repeat(3) { channel -> output[channel] = encodeUnitFinite(unitEncoder, linear[channel]) }
    }
}

private fun decodeUnitFinite(transferFunction: ColorTransferFunction.Parametric, encoded: Float): Float {
    val x = if (encoded.isFinite()) encoded.coerceIn(0f, 1f) else 0f
    val value = transferFunction.toLinear(x)
    return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
}

private fun encodeUnitFinite(encoder: UnitFiniteTransferEncoder, linear: Float): Float = encoder.encode(linear)

private class UnitFiniteTransferEncoder(
    private val transferFunction: ColorTransferFunction.Parametric,
) {
    private val boundary = transferFunction.d.coerceIn(0f, 1f)
    private val lowerLimit = (transferFunction.c * boundary + transferFunction.f).coerceIn(0f, 1f)
    private val upperLimit = decodeUnitFinite(transferFunction, boundary)
    private val unitBoundaryTransfer = ColorTransferFunction.parametric(
        g = transferFunction.g,
        a = transferFunction.a,
        b = transferFunction.b,
        c = transferFunction.c,
        d = boundary,
        e = transferFunction.e,
        f = transferFunction.f,
    )
    private val rawNonlinearFormula = ColorTransferFunction.parametric(
        g = transferFunction.g,
        a = transferFunction.a,
        b = transferFunction.b,
        c = 0f,
        d = Float.NaN,
        e = transferFunction.e,
        f = 0f,
    )

    fun encode(linear: Float): Float {
        val y = if (linear.isFinite()) linear.coerceIn(0f, 1f) else 0f
        val value = when {
            y < lowerLimit && transferFunction.c > 0f -> unitBoundaryTransfer.toEncoded(y)
            y < upperLimit -> boundary
            // NaN branch parameters force the raw helper's nonlinear formula without duplicating it.
            else -> rawNonlinearFormula.toEncoded(y.coerceAtLeast(transferFunction.e))
        }
        return if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    }
}

private fun <T : Any> assertNotNull(value: T?): T = checkNotNull(value)

private const val SDR_REFERENCE_WHITE_NITS: Float = 100f
