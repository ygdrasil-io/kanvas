@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.math.geometry.RectF32
import kotlin.math.floor
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval as I

/** Independent discrete oracle: image coordinates use pixel centres i + 0.5. */
internal object W5eDecodedImageCpuOracle {
    fun colorPixel(colorType: org.graphiks.kanvas.image.ColorType, alphaType: org.graphiks.kanvas.image.AlphaType,
        colorSpace: org.graphiks.kanvas.color.ColorSpace, bytes: ByteArray, paintAlphaF32: Float = 1f,
        paintColor: org.graphiks.math.color.ColorARGB = org.graphiks.math.color.ColorARGB.Green): WgslFloatEnvelopeV1Oracle.DrawResult {
        val oracle = WgslFloatEnvelopeV1Oracle
        val raw = bytes.map { oracle.imageUnorm8(it.toInt() and 255) }
        val opaque = alphaType == org.graphiks.kanvas.image.AlphaType.OPAQUE
        val isMask = colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8
        val alpha = if (opaque) I.ONE else raw[if (isMask) 0 else 3]
        val paintAlpha = I.input(paintAlphaF32)
        if (alpha == I.ZERO) return oracle.imageSourceAttachment(Array(4) { I.ZERO })
        if (isMask) {
            val childAlpha = I.input(paintColor.alphaNormalized)
            val maskedAlpha = oracle.gradientMultiply(oracle.gradientMultiply(childAlpha, paintAlpha), alpha)
            val channels = listOf(paintColor.redNormalized, paintColor.greenNormalized, paintColor.blueNormalized)
            return oracle.imageSourceAttachment(Array(4) { channelI32 -> if (channelI32 == 3) maskedAlpha else
                oracle.gradientMultiply(oracle.gradientMultiply(oracle.gradientMultiply(
                    oracle.imageSrgbToLinear(I.input(channels[channelI32])), childAlpha), paintAlpha), alpha) })
        }
        val rgb = if (colorType == org.graphiks.kanvas.image.ColorType.BGRA_8888) listOf(raw[2], raw[1], raw[0]) else raw.take(3)
        val straight = rgb.map { if (alphaType == org.graphiks.kanvas.image.AlphaType.PREMUL)
            oracle.gradientDivide(it, alpha) else it }
        val linear = straight.map { if (colorSpace == org.graphiks.kanvas.color.ColorSpace.LINEAR_SRGB) it else oracle.imageSrgbToLinear(it) }
        fun gamutRow(coefficientsF32: List<Float>): I {
            val coefficients = coefficientsF32.map(I::input)
            val products = coefficients.indices.map { oracle.gradientMultiply(coefficients[it], linear[it]) }
            val alternatives = mutableListOf<I>()
            for (aI32 in products.indices) for (bI32 in products.indices.filter { it != aI32 }) {
                val cI32 = 3 - aI32 - bI32
                val pair = oracle.gradientHull(oracle.gradientAdd(products[aI32], products[bI32]),
                    oracle.gradientFma(coefficients[aI32], linear[aI32], products[bI32]),
                    oracle.gradientFma(coefficients[bI32], linear[bI32], products[aI32]))
                alternatives += oracle.gradientAdd(pair, products[cI32])
                alternatives += oracle.gradientFma(coefficients[cI32], linear[cI32], pair)
            }
            return oracle.gradientHull(*alternatives.toTypedArray())
        }
        val working = if (colorSpace == org.graphiks.kanvas.color.ColorSpace.DISPLAY_P3) listOf(
            gamutRow(listOf(1.2247455f, -.2249044f, 0f)),
            gamutRow(listOf(-.0420581f, 1.0420810f, 0f)),
            gamutRow(listOf(-.0196423f, -.0786549f, 1.0985372f))) else linear
        return oracle.imageSourceAttachment(Array(4) { channelI32 -> if (channelI32 == 3)
            oracle.gradientMultiply(alpha, paintAlpha) else oracle.gradientMultiply(
                oracle.gradientMultiply(working[channelI32], alpha), paintAlpha) })
    }
    // Opaque sRGB primaries keep transfer/attachment quantization exact.
    fun pixels(): ByteArray = byteArrayOf(
        -1, 0, 0, -1, 0, -1, 0, -1, 0, 0, -1, -1,
        -1, -1, 0, -1, 0, -1, -1, -1, -1, 0, -1, -1,
    )

    fun nearest(widthI32: Int, heightI32: Int, srcF32: RectF32, dstF32: RectF32): UByteArray {
        val source = pixels()
        val result = UByteArray(widthI32 * heightI32 * 4)
        for (yI32 in 0 until heightI32) for (xI32 in 0 until widthI32) {
            val xF64 = xI32 + .5
            val yF64 = yI32 + .5
            if (xF64 < minOf(dstF32.left, dstF32.right) || xF64 >= maxOf(dstF32.left, dstF32.right) ||
                yF64 < minOf(dstF32.top, dstF32.bottom) || yF64 >= maxOf(dstF32.top, dstF32.bottom)) continue
            val sXF64 = srcF32.left + (xF64 - dstF32.left) / (dstF32.right - dstF32.left) * (srcF32.right - srcF32.left)
            val sYF64 = srcF32.top + (yF64 - dstF32.top) / (dstF32.bottom - dstF32.top) * (srcF32.bottom - srcF32.top)
            val texelI32 = floor(sYF64).toInt().coerceIn(0, 1) * 3 + floor(sXF64).toInt().coerceIn(0, 2)
            repeat(4) { channelI32 -> result[(yI32 * widthI32 + xI32) * 4 + channelI32] = source[texelI32 * 4 + channelI32].toUByte() }
        }
        return result
    }
}
