@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.math.geometry.RectF32
import kotlin.math.floor
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval as I

/** Independent discrete oracle: image coordinates use pixel centres i + 0.5. */
internal object W5eDecodedImageCpuOracle {
    /** Published CPU equations: address every tap before its independent texel conversion. */
    fun sampledColorPixel(
        widthI32: Int,
        heightI32: Int,
        bytes: ByteArray,
        sXF32: Float,
        sYF32: Float,
        linear: Boolean,
        tileX: TileMode,
        tileY: TileMode,
        paintAlphaF32: Float = 1f,
        cubic: SamplingOptions.Cubic? = null,
    ): WgslFloatEnvelopeV1Oracle.DrawResult {
        require(widthI32 > 0 && heightI32 > 0)
        val oracle = WgslFloatEnvelopeV1Oracle
        fun address(indexI32: Int, sizeI32: Int, mode: TileMode): Int? = when (mode) {
            TileMode.CLAMP -> indexI32.coerceIn(0, sizeI32 - 1)
            TileMode.REPEAT -> Math.floorMod(indexI32, sizeI32)
            TileMode.MIRROR -> {
                val periodI32 = Math.multiplyExact(sizeI32, 2)
                val phaseI32 = Math.floorMod(indexI32, periodI32)
                minOf(phaseI32, periodI32 - 1 - phaseI32)
            }
            TileMode.DECAL -> indexI32.takeIf { it in 0 until sizeI32 }
        }
        fun texel(xI32: Int, yI32: Int): Array<I> {
            val offsetI32 = (yI32 * widthI32 + xI32) * 4
            val raw = List(4) { channelI32 -> oracle.imageUnorm8(bytes[offsetI32 + channelI32].toInt() and 255) }
            val alpha = raw[3]
            // Published alpha-zero equation dominates unpremultiplication before a
            // transparent texel can participate in a filtered kernel.
            if (alpha == I.ZERO) return Array(4) { I.ZERO }
            val straight = raw.take(3).map { oracle.gradientDivide(it, alpha) }
            val working = straight.map(oracle::imageSrgbToLinear)
            return Array(4) { channelI32 -> if (channelI32 == 3) alpha else oracle.gradientMultiply(working[channelI32], alpha) }
        }
        fun tap(xI32: Int, yI32: Int): Array<I> {
            val x = address(xI32, widthI32, tileX)
            val y = address(yI32, heightI32, tileY)
            return if (x == null || y == null) Array(4) { I.ZERO } else texel(x, y)
        }
        val premul = when {
            cubic != null -> {
                fun weight(distanceF32: Float): I {
                    val x = kotlin.math.abs(distanceF32)
                    val b = cubic.B
                    val c = cubic.C
                    val value = when {
                        x < 1f -> ((12f - 9f * b - 6f * c) * x * x * x +
                            (-18f + 12f * b + 6f * c) * x * x + (6f - 2f * b)) / 6f
                        x < 2f -> ((-b - 6f * c) * x * x * x + (6f * b + 30f * c) * x * x +
                            (-12f * b - 48f * c) * x + (8f * b + 24f * c)) / 6f
                        else -> 0f
                    }
                    return I.input(value)
                }
                val uX = sXF32 - .5f
                val uY = sYF32 - .5f
                val baseX = floor(uX).toInt()
                val baseY = floor(uY).toInt()
                Array(4) { channelI32 ->
                    var sum = I.ZERO
                    for (offsetY in -1..2) for (offsetX in -1..2) {
                        val value = tap(baseX + offsetX, baseY + offsetY)[channelI32]
                        val kernel = oracle.gradientMultiply(weight(uX - (baseX + offsetX)), weight(uY - (baseY + offsetY)))
                        sum = oracle.gradientAdd(sum, oracle.gradientMultiply(value, kernel))
                    }
                    sum
                }
            }
            !linear -> tap(floor(sXF32).toInt(), floor(sYF32).toInt())
            else -> {
            val uX = sXF32 - .5f
            val uY = sYF32 - .5f
            val baseX = floor(uX).toInt()
            val baseY = floor(uY).toInt()
            val fractionX = I.input(uX - baseX)
            val fractionY = I.input(uY - baseY)
            val oneMinusX = oracle.gradientSubtract(I.ONE, fractionX)
            val oneMinusY = oracle.gradientSubtract(I.ONE, fractionY)
            val taps = listOf(
                Triple(tap(baseX, baseY), oneMinusX, oneMinusY),
                Triple(tap(baseX + 1, baseY), fractionX, oneMinusY),
                Triple(tap(baseX, baseY + 1), oneMinusX, fractionY),
                Triple(tap(baseX + 1, baseY + 1), fractionX, fractionY),
            )
            Array(4) { channelI32 -> taps.fold(I.ZERO) { sum, (value, weightX, weightY) ->
                oracle.gradientAdd(sum, oracle.gradientMultiply(value[channelI32], oracle.gradientMultiply(weightX, weightY)))
            } }
            }
        }
        return oracle.imageSourceAttachment(Array(4) { channelI32 ->
            oracle.gradientMultiply(premul[channelI32], I.input(paintAlphaF32))
        })
    }

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
