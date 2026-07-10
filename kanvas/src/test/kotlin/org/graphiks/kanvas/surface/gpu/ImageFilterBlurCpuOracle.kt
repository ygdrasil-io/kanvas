package org.graphiks.kanvas.surface.gpu

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import org.graphiks.kanvas.gpu.renderer.filters.SeparableBlurQualityTier

/** Test-only reference for the accepted 9x9 opaque-red impulse blur fixture. */
internal object ImageFilterBlurCpuOracle {
    fun clampBlurImpulseInSurface(
        surfaceSize: Int,
        originX: Int,
        originY: Int,
        sigmaX: Float,
        sigmaY: Float,
    ): ByteArray {
        val haloX = ceil(3f * sigmaX).toInt()
        val haloY = ceil(3f * sigmaY).toInt()
        val localWidth = 9 + 2 * haloX
        val localHeight = 9 + 2 * haloY
        val source = FloatArray(localWidth * localHeight * CHANNELS)
        val impulse = ((haloY + 4) * localWidth + haloX + 4) * CHANNELS
        source[impulse] = 1f
        source[impulse + 3] = 1f

        val horizontal = blurAxis(source, localWidth, localHeight, sigmaX, horizontal = true)
        val vertical = blurAxis(horizontal, localWidth, localHeight, sigmaY, horizontal = false)
        val surface = ByteArray(surfaceSize * surfaceSize * CHANNELS)
        for (y in 0 until localHeight) {
            val surfaceY = originY - haloY + y
            if (surfaceY !in 0 until surfaceSize) continue
            for (x in 0 until localWidth) {
                val surfaceX = originX - haloX + x
                if (surfaceX !in 0 until surfaceSize) continue
                val sourceOffset = (y * localWidth + x) * CHANNELS
                val destinationOffset = (surfaceY * surfaceSize + surfaceX) * CHANNELS
                surface[destinationOffset] = (linearToSrgb(vertical[sourceOffset]) * 255f).roundToInt().toByte()
                surface[destinationOffset + 3] =
                    (vertical[sourceOffset + 3].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
            }
        }
        return surface
    }

    private fun blurAxis(
        source: FloatArray,
        width: Int,
        height: Int,
        sigma: Float,
        horizontal: Boolean,
    ): FloatArray {
        val taps = SeparableBlurQualityTier.NORMAL.tapCount(sigma)
        if (taps <= 1) return source.copyOf()
        val weights = normalWeights(sigma, taps)
        val radius = taps / 2
        return FloatArray(source.size).also { destination ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val destinationOffset = (y * width + x) * CHANNELS
                    for (tap in weights.indices) {
                        val offset = tap - radius
                        val sampleX = (if (horizontal) x + offset else x).coerceIn(0, width - 1)
                        val sampleY = (if (horizontal) y else y + offset).coerceIn(0, height - 1)
                        val sourceOffset = (sampleY * width + sampleX) * CHANNELS
                        for (channel in 0 until CHANNELS) {
                            destination[destinationOffset + channel] += weights[tap] * source[sourceOffset + channel]
                        }
                    }
                }
            }
        }
    }

    private fun normalWeights(sigma: Float, taps: Int): FloatArray {
        val effectiveSigma = SeparableBlurQualityTier.NORMAL.effectiveSigma(sigma)
        val radius = taps / 2
        val weights = FloatArray(taps) { index ->
            val x = (index - radius).toFloat()
            exp(-(x * x) / (2f * effectiveSigma * effectiveSigma))
        }
        val sum = weights.sum()
        weights.indices.forEach { index -> weights[index] /= sum }
        return weights
    }

    private fun linearToSrgb(value: Float): Float =
        if (value <= 0.0031308f) 12.92f * value else 1.055f * value.pow(1f / 2.4f) - 0.055f

    private const val CHANNELS = 4
}
