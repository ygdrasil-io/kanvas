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
                // Independent published polynomials: constants are F32 inputs;
                // every runtime coefficient, power, sum and division is enveloped.
                fun constant(value: Float) = I.input(value)
                fun scaled(value: Float, parameter: I) = oracle.gradientMultiply(constant(value), parameter)
                val b = constant(cubic.B)
                val c = constant(cubic.C)
                fun weight(distance: I): I {
                    val x = when {
                        distance.lower.signum() >= 0 -> distance
                        distance.upper.signum() <= 0 -> I(distance.upper.negate(), distance.lower.negate())
                        else -> I(java.math.BigDecimal.ZERO, maxOf(distance.lower.abs(), distance.upper.abs()))
                    }
                    val alternatives = mutableListOf<I>()
                    fun polynomial(domain: I, outer: Boolean): I {
                        val coefficient3 = if (outer) oracle.gradientSubtract(oracle.gradientSubtract(I.ZERO, b), scaled(6f, c))
                            else oracle.gradientSubtract(oracle.gradientSubtract(constant(12f), scaled(9f, b)), scaled(6f, c))
                        val coefficient2 = if (outer) oracle.gradientAdd(scaled(6f, b), scaled(30f, c))
                            else oracle.gradientAdd(oracle.gradientAdd(constant(-18f), scaled(12f, b)), scaled(6f, c))
                        val cubicTerm = oracle.gradientMultiply(oracle.gradientMultiply(oracle.gradientMultiply(coefficient3, domain), domain), domain)
                        val squareTerm = oracle.gradientMultiply(oracle.gradientMultiply(coefficient2, domain), domain)
                        var numerator = oracle.gradientAdd(cubicTerm, squareTerm)
                        if (outer) {
                            val coefficient1 = oracle.gradientSubtract(scaled(-12f, b), scaled(48f, c))
                            numerator = oracle.gradientAdd(numerator, oracle.gradientMultiply(coefficient1, domain))
                        }
                        val coefficient0 = if (outer) oracle.gradientAdd(scaled(8f, b), scaled(24f, c))
                            else oracle.gradientSubtract(constant(6f), scaled(2f, b))
                        return oracle.gradientDivide(oracle.gradientAdd(numerator, coefficient0), constant(6f))
                    }
                    val one = java.math.BigDecimal.ONE
                    val two = java.math.BigDecimal(2)
                    if (x.lower < one) alternatives += polynomial(I(x.lower, minOf(x.upper, one)), false)
                    if (x.upper >= one && x.lower < two)
                        alternatives += polynomial(I(maxOf(x.lower, one), minOf(x.upper, two)), true)
                    if (x.upper >= two) alternatives += I.ZERO
                    return oracle.gradientHull(*alternatives.toTypedArray())
                }
                val uX = oracle.gradientSubtract(constant(sXF32), constant(.5f))
                val uY = oracle.gradientSubtract(constant(sYF32), constant(.5f))
                fun bases(coordinate: I): IntRange {
                    val low = coordinate.lower.setScale(0, java.math.RoundingMode.FLOOR).intValueExact()
                    val high = coordinate.upper.setScale(0, java.math.RoundingMode.FLOOR).intValueExact()
                    require(high.toLong() - low.toLong() <= 1L) { "Cubic fixture spans more than two floor alternatives" }
                    return low..high
                }
                fun forBase(coordinate: I, baseI32: Int): I = I(
                    maxOf(coordinate.lower, java.math.BigDecimal(baseI32)),
                    minOf(coordinate.upper, java.math.BigDecimal(baseI32.toLong() + 1L)))
                val colors = mutableMapOf<Pair<Int, Int>, Array<I>>()
                val alternatives = mutableListOf<Array<I>>()
                for (baseY in bases(uY)) for (baseX in bases(uX)) {
                    val coordinateX = forBase(uX, baseX)
                    val coordinateY = forBase(uY, baseY)
                    val weightsX = (-1..2).map { weight(oracle.gradientSubtract(coordinateX, constant((baseX + it).toFloat()))) }
                    val weightsY = (-1..2).map { weight(oracle.gradientSubtract(coordinateY, constant((baseY + it).toFloat()))) }
                    val terms = (-1..2).flatMap { offsetY -> (-1..2).map { offsetX ->
                        val value = colors.getOrPut((baseX + offsetX) to (baseY + offsetY)) { tap(baseX + offsetX, baseY + offsetY) }
                        value to oracle.gradientMultiply(weightsX[offsetX + 1], weightsY[offsetY + 1])
                    } }
                    alternatives += Array(4) { channelI32 ->
                        val weighted = terms.map { (value, kernel) -> oracle.gradientMultiply(value[channelI32], kernel) }
                        weighted.drop(1).fold(weighted.first(), oracle::gradientAdd)
                    }
                }
                Array(4) { channelI32 -> oracle.gradientHull(*alternatives.map { it[channelI32] }.toTypedArray()) }
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
