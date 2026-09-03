package org.graphiks.kanvas.surface

import kotlin.math.pow
import org.graphiks.math.color.ColorARGB

/**
 * Independent CPU reference for W3's closed sRGB, linear-premultiplied SrcOver contract.
 *
 * It deliberately depends only on ColorARGB and its own transfer/blend equations, never on
 * gpu-plan values or GPU payloads.
 */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W3SolidRectCpuOracle {
    data class Draw(
        val color: ColorARGB,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val clipLeft: Int = Int.MIN_VALUE,
        val clipTop: Int = Int.MIN_VALUE,
        val clipRight: Int = Int.MAX_VALUE,
        val clipBottom: Int = Int.MAX_VALUE,
    )

    fun render(
        width: Int,
        height: Int,
        draws: List<Draw>,
        format: PixelFormat = PixelFormat.RGBA8,
    ): UByteArray {
        require(width > 0 && height > 0)
        val pixels = Array(width * height) { LinearPremul.Transparent }
        draws.forEach { draw ->
            val source = LinearPremul.from(draw.color)
            val left = maxOf(0, draw.left, draw.clipLeft)
            val top = maxOf(0, draw.top, draw.clipTop)
            val right = minOf(width, draw.right, draw.clipRight)
            val bottom = minOf(height, draw.bottom, draw.clipBottom)
            for (y in top until bottom) {
                for (x in left until right) {
                    val offset = y * width + x
                    pixels[offset] = srcOver(source, pixels[offset]).quantizedForAttachment()
                }
            }
        }
        return UByteArray(width * height * CHANNELS_PER_PIXEL).also { bytes ->
            pixels.forEachIndexed { pixelIndex, color ->
                val offset = pixelIndex * CHANNELS_PER_PIXEL
                val red = color.red.toSrgbByte()
                val green = color.green.toSrgbByte()
                val blue = color.blue.toSrgbByte()
                val alpha = color.alpha.toQuantizedUByte()
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
                bytes[offset + 3] = alpha
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

    /** Models the rgba8unorm-srgb attachment store observed by the next draw. */
    private fun LinearPremul.quantizedForAttachment(): LinearPremul {
        val storedRed = red.toSrgbByte().toInt()
        val storedGreen = green.toSrgbByte().toInt()
        val storedBlue = blue.toSrgbByte().toInt()
        val storedAlpha = alpha.toQuantizedUByte().toInt()
        return LinearPremul(
            red = storedRed.srgbToLinear(),
            green = storedGreen.srgbToLinear(),
            blue = storedBlue.srgbToLinear(),
            alpha = storedAlpha / 255f,
        )
    }

    private data class LinearPremul(
        val red: Float,
        val green: Float,
        val blue: Float,
        val alpha: Float,
    ) {
        companion object {
            val Transparent = LinearPremul(0f, 0f, 0f, 0f)

            fun from(color: ColorARGB): LinearPremul {
                val alpha = color.alpha / 255f
                return LinearPremul(
                    red = color.red.srgbToLinear() * alpha,
                    green = color.green.srgbToLinear() * alpha,
                    blue = color.blue.srgbToLinear() * alpha,
                    alpha = alpha,
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
