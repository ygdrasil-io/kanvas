@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.roundToInt

/** Independent RGBA8 reference calculations for the W6d sampling witnesses. */
internal object W6dAdvancedSamplingCpuOracle {
    fun convolution3x1Clamp(source: UByteArray, kernel: FloatArray, offsetX: Int): UByteArray {
        return convolution3x1(source, kernel, offsetX, tileMode = "CLAMP")
    }

    fun convolution3x1(source: UByteArray, kernel: FloatArray, offsetX: Int, tileMode: String): UByteArray {
        require(source.size % 4 == 0 && kernel.size == 3)
        val width = source.size / 4
        return UByteArray(source.size).also { output ->
            for (x in 0 until width) for (channel in 0 until 4) {
                var value = 0f
                for (kernelX in kernel.indices) {
                    val coordinate = x + kernelX - offsetX
                    val sampleX = when (tileMode) {
                        "CLAMP" -> coordinate.coerceIn(0, width - 1)
                        "REPEAT" -> ((coordinate % width) + width) % width
                        "MIRROR" -> {
                            val period = 2 * width
                            val repeated = ((coordinate % period) + period) % period
                            if (repeated < width) repeated else period - 1 - repeated
                        }
                        "DECAL" -> coordinate.takeIf { it in 0 until width }
                        else -> error("Unknown tile mode $tileMode")
                    }
                    if (sampleX != null) value += source[sampleX * 4 + channel].toInt() * kernel[kernelX]
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
                val offset = source[x * 4].toInt() / 255f * scale
                val sampleX = (x + offset).roundToInt().coerceIn(0, width - 1)
                source.copyInto(output, x * 4, sampleX * 4, sampleX * 4 + 4)
            }
        }
    }

    /**
     * The displacement image is shifted in its own device domain before both its nearest-clamp
     * lookup and the source lookup. This deliberately keeps the two input coordinate spaces
     * independent of the filtered output's target-local origin.
     */
    fun displacementOffsetMapRedNearestClamp(source: UByteArray, mapOffsetX: Int, scale: Float): UByteArray {
        require(source.size % 4 == 0)
        val width = source.size / 4
        return UByteArray(source.size).also { output ->
            for (x in 0 until width) {
                val mapX = (x - mapOffsetX).coerceIn(0, width - 1)
                val offset = source[mapX * 4].toInt() / 255f * scale
                val sampleX = (x + offset).roundToInt().coerceIn(0, width - 1)
                source.copyInto(output, x * 4, sampleX * 4, sampleX * 4 + 4)
            }
        }
    }

    fun magnifierNearestClamp(source: UByteArray, lensLeft: Float, lensRight: Float, zoom: Float, inset: Float): UByteArray {
        require(source.size % 4 == 0 && lensLeft < lensRight && zoom > 0f && inset >= 0f)
        val width = source.size / 4
        val center = (lensLeft + lensRight) / 2f
        val innerLeft = lensLeft + inset
        val innerRight = lensRight - inset
        return UByteArray(source.size).also { output ->
            for (x in 0 until width) {
                val point = x + .5f
                val sample = if (point in innerLeft..innerRight) center + (point - center) / zoom else point
                val sampleX = (sample - .5f).roundToInt().coerceIn(0, width - 1)
                source.copyInto(output, x * 4, sampleX * 4, sampleX * 4 + 4)
            }
        }
    }
}
