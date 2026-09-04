package org.graphiks.kanvas.surface

import kotlin.math.pow
import kotlin.math.sqrt
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectI32

/**
 * Independent CPU reference for the public W4b pixel contract.
 *
 * The oracle owns its edge/corner coverage equations, attachment transfer,
 * quantization, compositing, and channel packing. It deliberately does not
 * call renderer, planner, shader, or materializer code.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W4bAnalyticRRectCpuOracle {
    internal data class Draw(
        val color: ColorARGB,
        val shape: RRectF32,
        val scissor: RectI32,
    )

    internal fun render(
        width: Int,
        height: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray {
        require(width > 0 && height > 0)
        val pixels = Array(width * height) { LinearPremul.Transparent }
        draws.forEach { draw ->
            val source = LinearPremul.from(draw.color)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    if (x !in draw.scissor.left until draw.scissor.right ||
                        y !in draw.scissor.top until draw.scissor.bottom
                    ) continue

                    val coverage = coverageAt(draw.shape, x, y)
                    val pixelIndex = y * width + x
                    pixels[pixelIndex] = srcOver(source * coverage, pixels[pixelIndex])
                        .quantizedForAttachment()
                }
            }
        }
        return UByteArray(width * height * CHANNELS_PER_PIXEL).also { bytes ->
            pixels.forEachIndexed { pixelIndex, color ->
                val offset = pixelIndex * CHANNELS_PER_PIXEL
                val red = color.red.toSrgbByte()
                val green = color.green.toSrgbByte()
                val blue = color.blue.toSrgbByte()
                when (format) {
                    PixelFormat.RGBA8 -> {
                        bytes[offset] = red
                        bytes[offset + 1] = green
                        bytes[offset + 2] = blue
                    }
                    PixelFormat.BGRA8 -> {
                        bytes[offset] = blue
                        bytes[offset + 1] = green
                        bytes[offset + 2] = red
                    }
                }
                bytes[offset + 3] = color.alpha.toQuantizedUByte()
            }
        }
    }

    private fun coverageAt(shape: RRectF32, x: Int, y: Int): Float =
        if (shape.hasOnlyZeroRadii()) {
            exactRectOverlap(shape, x, y)
        } else {
            sdfCoverage(shape, x + 0.5f, y + 0.5f)
        }

    private fun exactRectOverlap(shape: RRectF32, x: Int, y: Int): Float {
        val overlapX = (minOf(x + 1f, shape.rect.right) - maxOf(x.toFloat(), shape.rect.left))
            .coerceIn(0f, 1f)
        val overlapY = (minOf(y + 1f, shape.rect.bottom) - maxOf(y.toFloat(), shape.rect.top))
            .coerceIn(0f, 1f)
        return overlapX * overlapY
    }

    private fun sdfCoverage(shape: RRectF32, positionX: Float, positionY: Float): Float {
        val leftDistance = positionX - shape.rect.left
        val topDistance = positionY - shape.rect.top
        val rightDistance = shape.rect.right - positionX
        val bottomDistance = shape.rect.bottom - positionY
        var distance = minOf(leftDistance, topDistance, rightDistance, bottomDistance)
        distance = cornerDistance(distance, leftDistance, topDistance, shape.topLeft.x, shape.topLeft.y)
        distance = cornerDistance(distance, rightDistance, topDistance, shape.topRight.x, shape.topRight.y)
        distance = cornerDistance(distance, rightDistance, bottomDistance, shape.bottomRight.x, shape.bottomRight.y)
        distance = cornerDistance(distance, leftDistance, bottomDistance, shape.bottomLeft.x, shape.bottomLeft.y)

        val shapeWidth = maxOf(shape.rect.right - shape.rect.left, 0f)
        val shapeHeight = maxOf(shape.rect.bottom - shape.rect.top, 0f)
        val scale = minOf(shapeWidth, shapeHeight).coerceIn(0f, 1f)
        val bias = 1f - 0.5f * scale
        return (scale * (distance + bias)).coerceIn(0f, 1f)
    }

    private fun cornerDistance(
        currentDistance: Float,
        edgeX: Float,
        edgeY: Float,
        radiusX: Float,
        radiusY: Float,
    ): Float {
        val uvX = radiusX - edgeX
        val uvY = radiusY - edgeY
        if (uvX <= 0f || uvY <= 0f || radiusX <= 0f || radiusY <= 0f) return currentDistance

        val normalizedX = uvX / (radiusX * radiusX)
        val normalizedY = uvY / (radiusY * radiusY)
        val normalizedLength = sqrt((normalizedX * normalizedX + normalizedY * normalizedY).toDouble()).toFloat()
        if (normalizedLength <= 0f) return currentDistance

        val ellipseInside = 0.5f * (1f - (uvX * normalizedX + uvY * normalizedY)) / normalizedLength
        return minOf(currentDistance, ellipseInside)
    }

    private fun RRectF32.hasOnlyZeroRadii(): Boolean =
        topLeft.x == 0f && topLeft.y == 0f &&
            topRight.x == 0f && topRight.y == 0f &&
            bottomRight.x == 0f && bottomRight.y == 0f &&
            bottomLeft.x == 0f && bottomLeft.y == 0f

    private fun srcOver(source: LinearPremul, destination: LinearPremul): LinearPremul {
        val inverseSourceAlpha = 1f - source.alpha
        return LinearPremul(
            red = source.red + destination.red * inverseSourceAlpha,
            green = source.green + destination.green * inverseSourceAlpha,
            blue = source.blue + destination.blue * inverseSourceAlpha,
            alpha = source.alpha + destination.alpha * inverseSourceAlpha,
        )
    }

    /** Models the sRGB attachment store that the next draw reads. */
    private fun LinearPremul.quantizedForAttachment(): LinearPremul = LinearPremul(
        red = red.toSrgbByte().toInt().srgbToLinear(),
        green = green.toSrgbByte().toInt().srgbToLinear(),
        blue = blue.toSrgbByte().toInt().srgbToLinear(),
        alpha = alpha.toQuantizedUByte().toInt() / 255f,
    )

    private data class LinearPremul(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        operator fun times(coverage: Float): LinearPremul = LinearPremul(
            red * coverage,
            green * coverage,
            blue * coverage,
            alpha * coverage,
        )

        companion object {
            val Transparent: LinearPremul = LinearPremul(0f, 0f, 0f, 0f)

            fun from(color: ColorARGB): LinearPremul {
                val alpha = color.alpha / 255f
                return LinearPremul(
                    color.red.srgbToLinear() * alpha,
                    color.green.srgbToLinear() * alpha,
                    color.blue.srgbToLinear() * alpha,
                    alpha,
                )
            }
        }
    }

    private fun Int.srgbToLinear(): Float {
        val encoded = this / 255f
        return if (encoded <= 0.04045f) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)
    }

    private fun Float.toSrgbByte(): UByte {
        val linear = coerceIn(0f, 1f)
        val encoded = if (linear <= 0.0031308f) linear * 12.92f else 1.055f * linear.pow(1f / 2.4f) - 0.055f
        return (encoded * 255f + 0.5f).toInt().coerceIn(0, 255).toUByte()
    }

    private fun Float.toQuantizedUByte(): UByte =
        (coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255).toUByte()

    private const val CHANNELS_PER_PIXEL = 4
}
