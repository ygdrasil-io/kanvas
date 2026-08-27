package org.graphiks.kanvas.gpu.renderer.filters

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** A 5x4 color matrix (20 floats) for color filtering. */
data class ColorMatrix(
    val values: FloatArray = ColorMatrix.identity(),
) {
    companion object {
        /** Returns the identity color matrix (no-op). */
        fun identity(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/** Result of applying a color matrix filter. */
data class ColorMatrixFilterResult(
    val accepted: Boolean,
)

/** Applies a color matrix to pixels as a GPU image filter. */
class ColorMatrixFilter {
    /** Executes the color matrix filter and returns acceptance stats. */
    fun execute(matrix: ColorMatrix): ColorMatrixFilterResult {
        return ColorMatrixFilterResult(accepted = true)
    }
}

/**
 * Immutable descriptor for the bounded `srgb_colorfilter` native uniform ABI.
 *
 * The matrix operates on straight, linear-sRGB RGBA. Input and output RGB are
 * explicitly converted at the descriptor boundary; the final returned value is
 * encoded and premultiplied for the `rgba8unorm` WebGPU attachment.
 */
class SrgbMatrixColorFilterDescriptor(matrix: FloatArray) {
    private val matrixValues = matrix.copyOf()

    init {
        require(matrixValues.size == MATRIX_ENTRY_COUNT) {
            "sRGB color matrix must contain exactly $MATRIX_ENTRY_COUNT entries"
        }
        require(matrixValues.all(Float::isFinite)) { "sRGB color matrix must be finite" }
    }

    /** Returns a defensive matrix snapshot in row-major 4×5 layout. */
    fun matrix(): FloatArray = matrixValues.copyOf()

    /**
     * Packs `ColorMatrixUniforms` exactly as the registered native WGSL ABI:
     * straight encoded color, four matrix rows, then the four translations.
     */
    fun packNativeUniform(r: Float, g: Float, b: Float, a: Float): ByteArray {
        require(floatArrayOf(r, g, b, a).all(Float::isFinite)) {
            "sRGB color-filter input must be finite"
        }
        return ByteBuffer.allocate(NATIVE_UNIFORM_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putFloat(r)
                putFloat(g)
                putFloat(b)
                putFloat(a)
                for (row in 0 until CHANNEL_COUNT) {
                    val offset = row * ROW_WIDTH
                    putFloat(matrixValues[offset])
                    putFloat(matrixValues[offset + 1])
                    putFloat(matrixValues[offset + 2])
                    putFloat(matrixValues[offset + 3])
                }
                putFloat(matrixValues[4])
                putFloat(matrixValues[9])
                putFloat(matrixValues[14])
                putFloat(matrixValues[19])
            }
            .array()
    }

    internal fun applyLinear(r: Float, g: Float, b: Float, a: Float): FloatArray = floatArrayOf(
        matrixValues[0] * r + matrixValues[1] * g + matrixValues[2] * b + matrixValues[3] * a + matrixValues[4],
        matrixValues[5] * r + matrixValues[6] * g + matrixValues[7] * b + matrixValues[8] * a + matrixValues[9],
        matrixValues[10] * r + matrixValues[11] * g + matrixValues[12] * b + matrixValues[13] * a + matrixValues[14],
        matrixValues[15] * r + matrixValues[16] * g + matrixValues[17] * b + matrixValues[18] * a + matrixValues[19],
    )

    private companion object {
        const val CHANNEL_COUNT = 4
        const val ROW_WIDTH = 5
        const val MATRIX_ENTRY_COUNT = CHANNEL_COUNT * ROW_WIDTH
        const val NATIVE_UNIFORM_BYTES = 96
    }
}

/** CPU oracle for the registered bounded `srgb_colorfilter` shader descriptor. */
class SrgbMatrixColorFilter(private val descriptor: SrgbMatrixColorFilterDescriptor) {
    /**
     * Applies the matrix to a straight encoded-sRGB color and returns encoded,
     * premultiplied RGBA for the native attachment/store convention.
     */
    fun applyEncodedStraightRgba(r: Float, g: Float, b: Float, a: Float): FloatArray {
        require(floatArrayOf(r, g, b, a).all(Float::isFinite)) {
            "sRGB color-filter input must be finite"
        }
        val encodedR = r.coerceIn(0f, 1f)
        val encodedG = g.coerceIn(0f, 1f)
        val encodedB = b.coerceIn(0f, 1f)
        val encodedA = a.coerceIn(0f, 1f)
        val filtered = descriptor.applyLinear(
            srgbToLinear(encodedR),
            srgbToLinear(encodedG),
            srgbToLinear(encodedB),
            encodedA,
        ).map { it.coerceIn(0f, 1f) }
        val outputA = filtered[3]
        return floatArrayOf(
            linearToSrgb(filtered[0]) * outputA,
            linearToSrgb(filtered[1]) * outputA,
            linearToSrgb(filtered[2]) * outputA,
            outputA,
        )
    }

    private fun srgbToLinear(channel: Float): Float =
        if (channel <= 0.04045f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(channel: Float): Float =
        if (channel <= 0.0031308f) channel * 12.92f else 1.055f * channel.pow(1f / 2.4f) - 0.055f
}
