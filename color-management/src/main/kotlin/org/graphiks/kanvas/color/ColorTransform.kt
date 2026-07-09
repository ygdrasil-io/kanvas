package org.graphiks.kanvas.color

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
) {
    /** Applies the compiled transform to RGBA float pixels in place. */
    public fun apply(pixels: FloatArray, pixelCount: Int) {
        require(pixelCount >= 0) { "pixelCount must not be negative" }
        require(pixels.size >= pixelCount * CHANNELS_PER_PIXEL) {
            "pixels must contain at least $pixelCount RGBA pixels"
        }
        // Task 1 only compiles identical profiles, so the transform is intentionally a no-op.
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
        request.source.unsupportedCode?.let { return ColorTransformCompileResult.Failure(it) }
        request.destination.unsupportedCode?.let { return ColorTransformCompileResult.Failure(it) }
        if (request.source != request.destination) {
            return ColorTransformCompileResult.Failure("color.transform.unsupported")
        }
        return ColorTransformCompileResult.Success(CompiledColorTransform(request))
    }
}
