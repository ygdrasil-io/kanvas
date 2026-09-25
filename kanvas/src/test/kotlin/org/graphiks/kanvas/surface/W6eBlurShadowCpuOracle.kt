@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.DropShadowMode
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB

/**
 * Independent, test-local CPU references for this W6e shard.
 *
 * The oracle deliberately owns its Gaussian addressing, RGBA encoding and SrcOver path; it
 * does not call a renderer, planner, captured-filter helper, or [Surface].
 */
internal object W6eBlurShadowCpuOracle {
    fun blurOpaqueWhiteImpulse(width: Int, height: Int, x: Int, y: Int): UByteArray =
        encodeOpaqueWhite(blurredAlpha(width, height, UByteArray(width * height).also {
            it[x + y * width] = 255u
        }, 1f, 1f, TileMode.DECAL))

    fun dropShadow(
        width: Int,
        height: Int,
        sourceAlpha: UByteArray,
        sourceColor: ColorARGB,
        dx: Float,
        dy: Float,
        sigma: Float,
        shadowColor: ColorARGB,
        mode: DropShadowMode,
    ): UByteArray {
        val radius = ceil(3f * sigma).toInt()
        val paddedWidth = width + 2 * radius
        val paddedHeight = height + 2 * radius
        val padded = UByteArray(paddedWidth * paddedHeight).also { alpha ->
            sourceAlpha.indices.forEach { index ->
                val x = index % width
                val y = index / width
                alpha[x + radius + (y + radius) * paddedWidth] =
                    ((sourceAlpha[index].toInt() * sourceColor.alpha + 127) / 255).toUByte()
            }
        }
        val blurred = if (sigma == 0f) padded else blurredAlpha(
            paddedWidth, paddedHeight, padded, sigma, sigma, TileMode.DECAL,
        )
        return UByteArray(width * height * 4).also { result ->
            for (y in 0 until height) for (x in 0 until width) {
                val coverage = bilinearDecal(blurred, paddedWidth, paddedHeight,
                    x - dx + radius, y - dy + radius)
                if (coverage > 0f) srcOver(result, x + y * width, shadowColor, coverage)
            }
            if (mode == DropShadowMode.COMPOSITE) sourceAlpha.indices.forEach { index ->
                if (sourceAlpha[index] != 0.toUByte()) {
                    srcOver(result, index, sourceColor, sourceAlpha[index].toInt() / 255f)
                }
            }
        }
    }

    fun assertNear(expected: UByteArray, actual: UByteArray, maxDelta: Int) {
        require(expected.size == actual.size)
        val largest = expected.indices.maxOf { index ->
            kotlin.math.abs(expected[index].toInt() - actual[index].toInt())
        }
        val first = expected.indices.firstOrNull { index ->
            kotlin.math.abs(expected[index].toInt() - actual[index].toInt()) == largest
        }
        assertTrue(largest <= maxDelta, "W6e oracle delta=$largest at $first; expected=" +
            "${first?.let(expected::get)}, actual=${first?.let(actual::get)}, maxDelta=$maxDelta")
    }

    private fun blurredAlpha(
        width: Int,
        height: Int,
        source: UByteArray,
        sigmaX: Float,
        sigmaY: Float,
        tileMode: TileMode,
    ): UByteArray {
        fun convolve(input: FloatArray, sigma: Float, horizontal: Boolean): FloatArray {
            val radius = ceil(3f * sigma).toInt().coerceAtLeast(1)
            val weights = FloatArray(radius * 2 + 1) { tap ->
                val distance = (tap - radius).toFloat()
                exp(-(distance * distance) / (2f * sigma * sigma))
            }
            val total = weights.sum()
            weights.indices.forEach { weights[it] /= total }
            return FloatArray(width * height) { index ->
                val x = index % width
                val y = index / width
                weights.indices.sumOf { tap ->
                    val coordinate = tap - radius
                    val sampleX = if (horizontal) x + coordinate else x
                    val sampleY = if (horizontal) y else y + coordinate
                    val mappedX = map(sampleX, width, tileMode)
                    val mappedY = map(sampleY, height, tileMode)
                    if (mappedX == null || mappedY == null) 0.0
                    else input[mappedX + mappedY * width].toDouble() * weights[tap]
                }.toFloat().coerceIn(0f, 1f)
            }
        }
        val first = convolve(source.map { it.toInt() / 255f }.toFloatArray(), sigmaX, true)
        val second = convolve(first, sigmaY, false)
        return UByteArray(width * height) { index -> (second[index] * 255f).roundToInt().toUByte() }
    }

    private fun map(value: Int, size: Int, mode: TileMode): Int? = when (mode) {
        TileMode.CLAMP -> value.coerceIn(0, size - 1)
        TileMode.DECAL -> value.takeIf { it in 0 until size }
        TileMode.REPEAT -> Math.floorMod(value, size)
        TileMode.MIRROR -> {
            val folded = Math.floorMod(value, 2 * size)
            minOf(folded, 2 * size - 1 - folded)
        }
    }

    private fun encodeOpaqueWhite(alpha: UByteArray): UByteArray = UByteArray(alpha.size * 4).also { pixels ->
        alpha.forEachIndexed { index, value ->
            val encoded = (value.toInt() / 255f).pow(1f / 2.2f).times(255f).roundToInt().toUByte()
            val offset = index * 4
            pixels[offset] = encoded
            pixels[offset + 1] = encoded
            pixels[offset + 2] = encoded
            pixels[offset + 3] = value
        }
    }

    private fun bilinearDecal(alpha: UByteArray, width: Int, height: Int, x: Float, y: Float): Float {
        val left = floor(x).toInt()
        val top = floor(y).toInt()
        fun sample(sampleX: Int, sampleY: Int): Float = if (sampleX in 0 until width && sampleY in 0 until height)
            alpha[sampleX + sampleY * width].toInt() / 255f else 0f
        val fractionX = x - left
        val fractionY = y - top
        val upper = sample(left, top) * (1f - fractionX) + sample(left + 1, top) * fractionX
        val lower = sample(left, top + 1) * (1f - fractionX) + sample(left + 1, top + 1) * fractionX
        return upper * (1f - fractionY) + lower * fractionY
    }

    private fun srcOver(pixels: UByteArray, pixel: Int, color: ColorARGB, coverage: Float) {
        val offset = pixel * 4
        fun linear(channel: Int): Float {
            val srgb = channel / 255f
            return if (srgb <= .04045f) srgb / 12.92f else ((srgb + .055f) / 1.055f).pow(2.4f)
        }
        fun encoded(value: Float): UByte {
            val srgb = if (value <= .0031308f) value * 12.92f else 1.055f * value.pow(1f / 2.4f) - .055f
            return (srgb.coerceIn(0f, 1f) * 255f).roundToInt().toUByte()
        }
        fun destination(channel: Int): Float {
            val srgb = pixels[offset + channel].toInt() / 255f
            return if (srgb <= .04045f) srgb / 12.92f else ((srgb + .055f) / 1.055f).pow(2.4f)
        }
        val alpha = color.alpha / 255f * coverage
        val inverse = 1f - alpha
        pixels[offset] = encoded(linear(color.red) * alpha + destination(0) * inverse)
        pixels[offset + 1] = encoded(linear(color.green) * alpha + destination(1) * inverse)
        pixels[offset + 2] = encoded(linear(color.blue) * alpha + destination(2) * inverse)
        pixels[offset + 3] = ((alpha + pixels[offset + 3].toInt() / 255f * inverse) * 255f).roundToInt().toUByte()
    }
}
