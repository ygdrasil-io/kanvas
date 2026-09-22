@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.assertTrue
import org.graphiks.kanvas.paint.TileMode

/**
 * Small, deliberately test-local reference implementation for W6b image blur.
 *
 * It owns its kernel construction and tile addressing; it does not call renderer
 * filter helpers, plan code, or a Surface.  Pixels are straight 8-bit RGBA only
 * because the public tests below use opaque white impulses over transparent black.
 */
internal object W6bImageBlurCpuOracle {
    fun assertNear(expected: UByteArray, actual: UByteArray, tolerance: Int = 12) {
        require(expected.size == actual.size)
        val largestDifference = expected.indices.maxOf { index ->
            kotlin.math.abs(expected[index].toInt() - actual[index].toInt())
        }
        val firstMismatch = expected.indices.firstOrNull { index ->
            kotlin.math.abs(expected[index].toInt() - actual[index].toInt()) == largestDifference
        }
        assertTrue(
            largestDifference <= tolerance,
            "W6b image blur differs from the independent oracle by $largestDifference at $firstMismatch " +
                "(pixel=${firstMismatch?.div(4)}, channel=${firstMismatch?.rem(4)}, " +
                "expected=${firstMismatch?.let(expected::get)}, actual=${firstMismatch?.let(actual::get)}, tolerance=$tolerance).",
        )
    }

    fun blurredAlpha(
        width: Int,
        height: Int,
        sourceAlpha: UByteArray,
        sigmaX: Float,
        sigmaY: Float,
        tileMode: TileMode,
        knownLeft: Int = 0,
        knownTop: Int = 0,
        knownRight: Int = width,
        knownBottom: Int = height,
    ): UByteArray {
        require(sourceAlpha.size == width * height)
        require(knownLeft in 0 until knownRight && knownTop in 0 until knownBottom && knownRight <= width && knownBottom <= height)
        val source = Plane(width, height, 0, 0, knownLeft, knownTop, knownRight, knownBottom,
            sourceAlpha.map { it.toInt() / 255f }.toFloatArray())
        val horizontal = convolve(source, sigmaX, horizontal = true, tileMode)
        val vertical = convolve(horizontal, sigmaY, horizontal = false, tileMode)
        return UByteArray(width * height) { index ->
            val x = index % width
            val y = index / width
            (vertical.sampleGlobal(x, y).coerceIn(0f, 1f) * 255f).roundToInt().toUByte()
        }
    }

    fun toOpaqueWhiteRgba(alpha: UByteArray): UByteArray = UByteArray(alpha.size * 4).also { pixels ->
        alpha.forEachIndexed { pixel, value ->
            val offset = pixel * 4
            // The native attachment is encoded sRGB for RGB and linear for alpha.
            val encoded = (value.toInt() / 255f).pow(1f / 2.2f).times(255f).roundToInt().toUByte()
            pixels[offset] = encoded
            pixels[offset + 1] = encoded
            pixels[offset + 2] = encoded
            pixels[offset + 3] = value
        }
    }

    /** Encodes three independently blurred linear primary planes over opaque alpha. */
    fun toRgba(red: UByteArray, green: UByteArray, blue: UByteArray, alpha: UByteArray): UByteArray {
        require(red.size == green.size && green.size == blue.size && blue.size == alpha.size)
        fun encode(value: UByte): UByte = (value.toInt() / 255f).pow(1f / 2.2f).times(255f).roundToInt().toUByte()
        return UByteArray(red.size * 4).also { pixels ->
            red.indices.forEach { pixel ->
                val offset = pixel * 4
                pixels[offset] = encode(red[pixel])
                pixels[offset + 1] = encode(green[pixel])
                pixels[offset + 2] = encode(blue[pixel])
                pixels[offset + 3] = alpha[pixel]
            }
        }
    }

    private fun convolve(
        source: Plane,
        sigma: Float,
        horizontal: Boolean,
        tileMode: TileMode,
    ): Plane {
        if (sigma == 0f) return source.copy()
        val kernel = gaussianKernel(sigma)
        val radius = kernel.size / 2
        val outputWidth = source.width + if (horizontal) 2 * radius else 0
        val outputHeight = source.height + if (horizontal) 0 else 2 * radius
        val outputOriginX = source.originX - if (horizontal) radius else 0
        val outputOriginY = source.originY - if (horizontal) 0 else radius
        val output = FloatArray(outputWidth * outputHeight)
        for (localY in 0 until outputHeight) for (localX in 0 until outputWidth) {
            val globalX = localX + outputOriginX
            val globalY = localY + outputOriginY
            var result = 0f
            kernel.forEachIndexed { tap, weight ->
                val sampleX = globalX + if (horizontal) tap - radius else 0
                val sampleY = globalY + if (horizontal) 0 else tap - radius
                val mappedX = sampleCoordinate(sampleX, source.knownLeft, source.knownWidth, tileMode)
                val mappedY = sampleCoordinate(sampleY, source.knownTop, source.knownHeight, tileMode)
                if (mappedX != null && mappedY != null) {
                    result += source.sampleGlobal(mappedX, mappedY) * weight
                }
            }
            output[localY * outputWidth + localX] = result.coerceIn(0f, 1f)
        }
        return Plane(outputWidth, outputHeight, outputOriginX, outputOriginY,
            outputOriginX, outputOriginY, outputOriginX + outputWidth, outputOriginY + outputHeight, output)
    }

    private fun gaussianKernel(sigma: Float): FloatArray {
        require(sigma > 0f && sigma.isFinite())
        val radius = ceil(3f * sigma).toInt().coerceAtLeast(1)
        val weights = FloatArray(radius * 2 + 1) { index ->
            val distance = (index - radius).toFloat()
            exp(-(distance * distance) / (2f * sigma * sigma))
        }
        val sum = weights.sum()
        return weights.also { normalized -> normalized.indices.forEach { normalized[it] /= sum } }
    }

    private fun sampleCoordinate(value: Int, lower: Int, size: Int, tileMode: TileMode): Int? = when (tileMode) {
        TileMode.CLAMP -> value.coerceIn(lower, lower + size - 1)
        TileMode.DECAL -> value.takeIf { it in lower until lower + size }
        TileMode.REPEAT -> lower + Math.floorMod(value - lower, size)
        // W5e's frozen image address graph duplicates its edge texels: this is deliberately
        // expressed from the public tile-mode contract, not borrowed from the W6b shader.
        TileMode.MIRROR -> {
            val period = 2 * size
            val folded = Math.floorMod(value - lower, period)
            lower + minOf(folded, period - 1 - folded)
        }
    }

    private class Plane(
        val width: Int,
        val height: Int,
        val originX: Int,
        val originY: Int,
        val knownLeft: Int,
        val knownTop: Int,
        val knownRight: Int,
        val knownBottom: Int,
        val pixels: FloatArray,
    ) {
        val knownWidth: Int get() = knownRight - knownLeft
        val knownHeight: Int get() = knownBottom - knownTop
        fun copy(): Plane = Plane(width, height, originX, originY, knownLeft, knownTop, knownRight, knownBottom, pixels.copyOf())
        fun sampleGlobal(x: Int, y: Int): Float = pixels[(y - originY) * width + x - originX]
    }
}
