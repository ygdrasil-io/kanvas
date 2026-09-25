@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.roundToInt

/**
 * Independent RGBA8 references for W6e advanced sampled filters.  These calculations deliberately
 * depend only on the public filter contracts.
 */
internal object W6eAdvancedSamplingCpuOracle {
    fun convolution3x1Clamp(source: UByteArray, kernel: FloatArray, kernelOffsetX: Int): UByteArray {
        require(source.size % 4 == 0 && kernel.size == 3)
        val width = source.size / 4
        return UByteArray(source.size).also { output ->
            for (x in 0 until width) for (channel in 0 until 4) {
                var value = 0f
                for (kernelX in kernel.indices) {
                    val sampleX = (x + kernelX - kernelOffsetX).coerceIn(0, width - 1)
                    value += source[sampleX * 4 + channel].toInt() * kernel[kernelX]
                }
                output[x * 4 + channel] = value.roundToInt().coerceIn(0, 255).toUByte()
            }
        }
    }

    fun displacementRedNearestClamp(source: UByteArray, scale: Float): UByteArray {
        require(source.size % 4 == 0)
        val width = source.size / 4
        return UByteArray(source.size).also { output ->
            for (x in 0 until width) {
                val normalizedRed = source[x * 4].toInt() / 255f
                val sampleX = (x + normalizedRed * scale).roundToInt().coerceIn(0, width - 1)
                source.copyInto(output, x * 4, sampleX * 4, sampleX * 4 + 4)
            }
        }
    }

    fun magnifierNearestClamp(
        source: UByteArray,
        lensLeft: Float,
        lensRight: Float,
        zoom: Float,
        inset: Float,
    ): UByteArray {
        require(source.size % 4 == 0 && lensLeft < lensRight && zoom > 0f && inset >= 0f)
        val width = source.size / 4
        val center = (lensLeft + lensRight) / 2f
        val innerLeft = lensLeft + inset
        val innerRight = lensRight - inset
        return UByteArray(source.size).also { output ->
            for (x in 0 until width) {
                val point = x + .5f
                val coordinate = if (point in innerLeft..innerRight) center + (point - center) / zoom else point
                val sampleX = (coordinate - .5f).roundToInt().coerceIn(0, width - 1)
                source.copyInto(output, x * 4, sampleX * 4, sampleX * 4 + 4)
            }
        }
    }

}
