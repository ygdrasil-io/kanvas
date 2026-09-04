package org.graphiks.kanvas.surface

import kotlin.math.pow
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RectI32

/**
 * Independent CPU reference for W4a's analytic axis-aligned rectangle coverage contract.
 *
 * This intentionally models only the public pixel equation and attachment quantization; it
 * does not use the renderer, scene compiler, or any W4a planning implementation.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W4aAnalyticRectCpuOracle {
    internal data class Draw(
        val color: ColorARGB,
        val bounds: RectF32,
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

                    val coverageX = (minOf(x + 1f, draw.bounds.right) -
                        maxOf(x.toFloat(), draw.bounds.left)).coerceIn(0f, 1f)
                    val coverageY = (minOf(y + 1f, draw.bounds.bottom) -
                        maxOf(y.toFloat(), draw.bounds.top)).coerceIn(0f, 1f)
                    val coveredSource = source * (coverageX * coverageY)
                    val offset = y * width + x
                    pixels[offset] = srcOver(coveredSource, pixels[offset]).quantizedForAttachment()
                }
            }
        }
        return UByteArray(width * height * CHANNELS_PER_PIXEL).also { bytes ->
            pixels.forEachIndexed { index, color ->
                val offset = index * CHANNELS_PER_PIXEL
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

    private fun srcOver(src: LinearPremul, dst: LinearPremul): LinearPremul {
        val inverseAlpha = 1f - src.alpha
        return LinearPremul(
            red = src.red + dst.red * inverseAlpha,
            green = src.green + dst.green * inverseAlpha,
            blue = src.blue + dst.blue * inverseAlpha,
            alpha = src.alpha + dst.alpha * inverseAlpha,
        )
    }

    /** Models one rgba8unorm-srgb attachment store before the following draw. */
    private fun LinearPremul.quantizedForAttachment(): LinearPremul {
        val red = red.toSrgbByte().toInt()
        val green = green.toSrgbByte().toInt()
        val blue = blue.toSrgbByte().toInt()
        val alpha = alpha.toQuantizedUByte().toInt()
        return LinearPremul(red.srgbToLinear(), green.srgbToLinear(), blue.srgbToLinear(), alpha / 255f)
    }

    private data class LinearPremul(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        operator fun times(coverage: Float) = LinearPremul(
            red * coverage,
            green * coverage,
            blue * coverage,
            alpha * coverage,
        )

        companion object {
            val Transparent = LinearPremul(0f, 0f, 0f, 0f)

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
