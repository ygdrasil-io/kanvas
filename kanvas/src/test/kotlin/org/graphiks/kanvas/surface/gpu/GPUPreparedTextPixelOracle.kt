package org.graphiks.kanvas.surface.gpu

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Test-only pixel oracle independent of the prepared shader and legacy renderer.
 *
 * RGB enters as straight sRGB, becomes straight linear, receives material and paint alpha
 * exactly once, is premultiplied, receives coverage, blends in linear premultiplied space,
 * then is encoded to the sRGB target. A8 coverage is never decoded as color.
 */
object GPUPreparedTextPixelOracle {
    data class IntRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        init {
            require(left <= right && top <= bottom)
        }
    }

    data class Layer(
        val bounds: IntRect,
        val color: StraightSrgb,
        val paintAlpha: Float,
        val coverage: Int = 255,
    ) {
        init {
            require(paintAlpha.isFinite() && paintAlpha in 0f..1f)
            require(coverage in 0..255)
        }
    }

    data class StraightSrgb(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int = 255,
    ) {
        init {
            require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255)
        }
    }

    data class EncodedPremulSrgb(
        val red: Int,
        val green: Int,
        val blue: Int,
        val alpha: Int,
    ) {
        init {
            require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255)
        }

        fun bytes(): ByteArray = byteArrayOf(
            red.toByte(),
            green.toByte(),
            blue.toByte(),
            alpha.toByte(),
        )
    }

    fun a8SourceOver(
        material: StraightSrgb,
        paintAlpha: Float,
        coverage: Int,
        destination: EncodedPremulSrgb = EncodedPremulSrgb(0, 0, 0, 0),
    ): EncodedPremulSrgb {
        require(paintAlpha.isFinite() && paintAlpha in 0f..1f)
        require(coverage in 0..255)
        return sourceOver(
            primitive = material,
            paintAlpha = paintAlpha,
            coverage = coverage / 255f,
            destination = destination,
        )
    }

    /**
     * COLRv0 starts from the resolved primitive CPAL/currentColor layer. There is no shader
     * paint color parameter here by construction.
     */
    fun colorGlyphSourceOver(
        resolvedPrimitiveLayer: StraightSrgb,
        paintAlpha: Float,
        coverage: Int,
        destination: EncodedPremulSrgb = EncodedPremulSrgb(0, 0, 0, 0),
    ): EncodedPremulSrgb {
        require(paintAlpha.isFinite() && paintAlpha in 0f..1f)
        require(coverage in 0..255)
        return sourceOver(
            primitive = resolvedPrimitiveLayer,
            paintAlpha = paintAlpha,
            coverage = coverage / 255f,
            destination = destination,
        )
    }

    fun maxChannelDelta(actual: ByteArray, expected: ByteArray): Int {
        require(actual.size == expected.size)
        return actual.indices.maxOfOrNull { index ->
            abs((actual[index].toInt() and 0xff) - (expected[index].toInt() and 0xff))
        } ?: 0
    }

    fun renderLayers(
        width: Int,
        height: Int,
        layers: List<Layer>,
    ): ByteArray {
        require(width > 0 && height > 0)
        val linearPremul = FloatArray(Math.multiplyExact(Math.multiplyExact(width, height), 4))
        layers.forEach { layer ->
            require(
                layer.bounds.left in 0..width &&
                    layer.bounds.right in 0..width &&
                    layer.bounds.top in 0..height &&
                    layer.bounds.bottom in 0..height,
            )
            val sourceAlpha =
                layer.color.alpha / 255f * layer.paintAlpha * (layer.coverage / 255f)
            val sourceRed = decodeSrgb(layer.color.red / 255f) * sourceAlpha
            val sourceGreen = decodeSrgb(layer.color.green / 255f) * sourceAlpha
            val sourceBlue = decodeSrgb(layer.color.blue / 255f) * sourceAlpha
            val inverseSourceAlpha = 1f - sourceAlpha
            for (y in layer.bounds.top until layer.bounds.bottom) {
                for (x in layer.bounds.left until layer.bounds.right) {
                    val pixel = (y * width + x) * 4
                    linearPremul[pixel] = sourceRed + linearPremul[pixel] * inverseSourceAlpha
                    linearPremul[pixel + 1] =
                        sourceGreen + linearPremul[pixel + 1] * inverseSourceAlpha
                    linearPremul[pixel + 2] =
                        sourceBlue + linearPremul[pixel + 2] * inverseSourceAlpha
                    linearPremul[pixel + 3] =
                        sourceAlpha + linearPremul[pixel + 3] * inverseSourceAlpha
                }
            }
        }
        return ByteArray(linearPremul.size) { index ->
            val encoded = if (index % 4 == 3) {
                linearPremul[index]
            } else {
                encodeSrgb(linearPremul[index])
            }
            quantize(encoded).toByte()
        }
    }

    private fun sourceOver(
        primitive: StraightSrgb,
        paintAlpha: Float,
        coverage: Float,
        destination: EncodedPremulSrgb,
    ): EncodedPremulSrgb {
        val materialAlpha = primitive.alpha / 255f
        val sourceAlpha = materialAlpha * paintAlpha * coverage
        val source = floatArrayOf(
            decodeSrgb(primitive.red / 255f) * sourceAlpha,
            decodeSrgb(primitive.green / 255f) * sourceAlpha,
            decodeSrgb(primitive.blue / 255f) * sourceAlpha,
            sourceAlpha,
        )
        val destinationLinear = floatArrayOf(
            decodeSrgb(destination.red / 255f),
            decodeSrgb(destination.green / 255f),
            decodeSrgb(destination.blue / 255f),
            destination.alpha / 255f,
        )
        val inverseSourceAlpha = 1f - sourceAlpha
        val blended = floatArrayOf(
            source[0] + destinationLinear[0] * inverseSourceAlpha,
            source[1] + destinationLinear[1] * inverseSourceAlpha,
            source[2] + destinationLinear[2] * inverseSourceAlpha,
            source[3] + destinationLinear[3] * inverseSourceAlpha,
        )
        return EncodedPremulSrgb(
            red = quantize(encodeSrgb(blended[0])),
            green = quantize(encodeSrgb(blended[1])),
            blue = quantize(encodeSrgb(blended[2])),
            alpha = quantize(blended[3]),
        )
    }

    private fun decodeSrgb(encoded: Float): Float =
        if (encoded <= 0.04045f) encoded / 12.92f
        else (((encoded + 0.055f) / 1.055f).toDouble().pow(2.4)).toFloat()

    private fun encodeSrgb(linear: Float): Float =
        if (linear <= 0.0031308f) linear * 12.92f
        else (1.055 * linear.toDouble().pow(1.0 / 2.4) - 0.055).toFloat()

    private fun quantize(value: Float): Int =
        (value.coerceIn(0f, 1f) * 255f).roundToInt()
}
