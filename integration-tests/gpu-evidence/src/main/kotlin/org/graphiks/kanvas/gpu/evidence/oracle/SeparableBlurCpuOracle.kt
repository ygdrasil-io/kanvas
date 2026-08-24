package org.graphiks.kanvas.gpu.evidence.oracle

import kotlin.math.exp
import kotlin.math.roundToInt
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.filters.SeparableBlurQualityTier

/**
 * Validation-only Gaussian reference for the prepared blur route.
 *
 * It deliberately derives its weights locally rather than sharing the product cache.  Sampling
 * outside the target is transparent (decal), and bytes are quantized only after the vertical pass.
 */
class SeparableBlurCpuOracle(
    private val sourceBounds: GPUPixelBounds,
    sourcePremultipliedRgba: FloatArray,
    private val sigma: Float,
) : CpuOracle {
    private val sourceColor = sourcePremultipliedRgba.copyOf()
    private val tapCount = SeparableBlurQualityTier.NORMAL.tapCount(sigma)
    private val weights = gaussianWeights(sigma, tapCount)

    init {
        require(sigma.isFinite() && sigma > 0f) { "sigma must be finite and positive" }
        require(sourceColor.size == 4 && sourceColor.all { it.isFinite() && it in 0f..1f }) {
            "source color must be normalized premultiplied RGBA"
        }
        require(sourceColor[0] <= sourceColor[3] && sourceColor[1] <= sourceColor[3] && sourceColor[2] <= sourceColor[3]) {
            "source color must be premultiplied"
        }
    }

    /** Exposed for focused independent normalization and symmetry checks. */
    fun kernelWeights(): FloatArray = weights.copyOf()

    override fun render(width: Int, height: Int): ByteArray {
        require(width > 0 && height > 0) { "target dimensions must be positive" }
        require(sourceBounds.left >= 0 && sourceBounds.top >= 0 && sourceBounds.right <= width && sourceBounds.bottom <= height) {
            "source bounds must be contained by the target"
        }
        val source = FloatArray(width * height * 4)
        for (y in sourceBounds.top until sourceBounds.bottom) for (x in sourceBounds.left until sourceBounds.right) {
            sourceColor.copyInto(source, (y * width + x) * 4)
        }
        val horizontal = FloatArray(source.size)
        val radius = tapCount / 2
        for (y in 0 until height) for (x in 0 until width) for (channel in 0..3) {
            var value = 0f
            for (tap in weights.indices) value += transparentSample(source, width, height, x + tap - radius, y, channel) * weights[tap]
            horizontal[(y * width + x) * 4 + channel] = value
        }
        val output = ByteArray(source.size)
        for (y in 0 until height) for (x in 0 until width) for (channel in 0..3) {
            var value = 0f
            for (tap in weights.indices) value += transparentSample(horizontal, width, height, x, y + tap - radius, channel) * weights[tap]
            output[(y * width + x) * 4 + channel] = (value.coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }
        return output
    }

    private fun transparentSample(pixels: FloatArray, width: Int, height: Int, x: Int, y: Int, channel: Int): Float =
        if (x in 0 until width && y in 0 until height) pixels[(y * width + x) * 4 + channel] else 0f

    private fun gaussianWeights(sigma: Float, taps: Int): FloatArray {
        val radius = taps / 2
        val raw = FloatArray(taps) { index ->
            val distance = (index - radius).toFloat()
            exp(-(distance * distance) / (2f * sigma * sigma))
        }
        val sum = raw.sum()
        return raw.also { weights -> weights.indices.forEach { index -> weights[index] /= sum } }
    }
}
