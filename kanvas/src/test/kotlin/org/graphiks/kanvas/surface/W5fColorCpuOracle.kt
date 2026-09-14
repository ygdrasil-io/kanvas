package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.RoundingMode
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval

/** Independent published equations, with directed arithmetic supplied by the test envelope. */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W5fColorCpuOracle {
    fun expectedShaderTree(shader: Shader, paintAlphaF32: Float = 1f, external: ColorFilter? = null,
        destination: ColorARGB = ColorARGB.Transparent, finalBlend: BlendMode = BlendMode.SRC): WgslFloatEnvelopeV1Oracle.DrawResult {
        fun evaluate(node: Shader): Array<Interval> = when (node) {
            is Shader.SolidColor -> source(node.color)
            is Shader.Opacity -> evaluate(node.shader).map { mul(it,Interval.input(node.alphaF32)) }.toTypedArray()
            is Shader.WithColorFilter -> applyFilter(evaluate(node.shader),node.filter)
            else -> error("Shader is outside the independent ordered-source equations")
        }
        var value = evaluate(shader).map { mul(it,Interval.input(paintAlphaF32)) }.toTypedArray()
        if (external != null) value = applyFilter(value,external)
        return finish(value,destination,finalBlend,1f)
    }
    fun expectedPaintSource(color: ColorARGB, filter: ColorFilter,
        destination: ColorARGB = ColorARGB.Transparent, finalBlend: BlendMode = BlendMode.SRC_OVER,
        coverageF32: Float = 1f, destinationBlend: BlendMode = BlendMode.SRC_OVER): WgslFloatEnvelopeV1Oracle.DrawResult =
        finish(applyFilter(source(color), filter), destination, finalBlend, coverageF32,destinationBlend)

    fun expectedShaderSource(color: ColorARGB, shaderAlphaF32: Float, paintAlphaF32: Float,
        internal: ColorFilter?, external: ColorFilter?, destination: ColorARGB = ColorARGB.Transparent,
        finalBlend: BlendMode = BlendMode.SRC_OVER, coverageF32: Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult {
        var value = source(color).map { mul(it, Interval.input(shaderAlphaF32)) }.toTypedArray()
        if (internal != null) value = applyFilter(value, internal)
        value = value.map { mul(it, Interval.input(paintAlphaF32)) }.toTypedArray()
        if (external != null) value = applyFilter(value, external)
        return finish(value, destination, finalBlend, coverageF32)
    }

    private fun applyFilter(input: Array<Interval>, filter: ColorFilter): Array<Interval> = when (filter) {
        is ColorFilter.Matrix -> matrix(input, filter)
        is ColorFilter.Table -> {
            require(filter.table.size == 256)
            val straight = unpremultiply(input)
            val selected = Array(4) { channel ->
                val scaled = mul(clamp(straight[channel]),Interval.input(255f))
                // A rounded scalar can reach either integer at a tie. Enumerate the
                // complete discrete set, then fetch each actual bound table byte.
                val first = scaled.lower.subtract(BigDecimal("0.5")).setScale(0,RoundingMode.CEILING).toInt().coerceIn(0,255)
                val last = scaled.upper.add(BigDecimal("0.5")).setScale(0,RoundingMode.FLOOR).toInt().coerceIn(0,255)
                WgslFloatEnvelopeV1Oracle.gradientHull(*(first..last).map {
                    WgslFloatEnvelopeV1Oracle.gradientDivide(Interval.input(filter.table[it].toInt().toFloat()),Interval.input(255f))
                }.toTypedArray())
            }
            premultiply(selected)
        }
        is ColorFilter.Lighting -> {
            val straight = unpremultiply(input)
            val mulColor = source(ColorARGB.of(255,filter.mul.red,filter.mul.green,filter.mul.blue))
            val addColor = source(ColorARGB.of(255,filter.add.red,filter.add.green,filter.add.blue))
            premultiply(Array(4) { if (it == 3) input[3] else clamp(WgslFloatEnvelopeV1Oracle.gradientHull(
                WgslFloatEnvelopeV1Oracle.gradientAdd(mul(straight[it],mulColor[it]),addColor[it]),
                WgslFloatEnvelopeV1Oracle.gradientFma(straight[it],mulColor[it],addColor[it]))) })
        }
        ColorFilter.SRGBToLinear, ColorFilter.LinearToSRGB -> {
            val straight = unpremultiply(input)
            premultiply(Array(4) { if (it == 3) input[3] else if (filter == ColorFilter.SRGBToLinear)
                WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(straight[it])
                else WgslFloatEnvelopeV1Oracle.filterLinearToSrgb(straight[it]) })
        }
        is ColorFilter.Blend -> WgslFloatEnvelopeV1Oracle.filterBlend(source(filter.color),input,filter.mode)
        is ColorFilter.Compose -> applyFilter(applyFilter(input, filter.inner), filter.outer)
        is ColorFilter.Lerp -> {
            val dst = applyFilter(input, filter.dst)
            val src = applyFilter(input, filter.src)
            val t = Interval.input(filter.t)
            val inverse = WgslFloatEnvelopeV1Oracle.gradientSubtract(Interval.ONE, t)
            Array(4) { WgslFloatEnvelopeV1Oracle.gradientHull(
                WgslFloatEnvelopeV1Oracle.gradientAdd(mul(inverse, dst[it]), mul(t, src[it])),
                WgslFloatEnvelopeV1Oracle.gradientFma(inverse, dst[it], mul(t, src[it])),
                WgslFloatEnvelopeV1Oracle.gradientFma(t, src[it], mul(inverse, dst[it]))) }
        }
        else -> error("Filter is outside this task's oracle")
    }

    private fun finish(value: Array<Interval>, destination: ColorARGB, mode: BlendMode,
        coverageF32: Float, destinationBlend: BlendMode = BlendMode.SRC_OVER): WgslFloatEnvelopeV1Oracle.DrawResult {
        val back = if (destination == ColorARGB.Transparent || mode == BlendMode.SRC && coverageF32 == 1f)
            WgslFloatEnvelopeV1Oracle.clearAttachment()
        else WgslFloatEnvelopeV1Oracle.nextAttachment(WgslFloatEnvelopeV1Oracle.colorThenBlend(
            source(destination), WgslFloatEnvelopeV1Oracle.clearAttachment(), destinationBlend))
            ?: return WgslFloatEnvelopeV1Oracle.DrawResult.FixtureUnbounded("Destination fixture is unbounded")
        return WgslFloatEnvelopeV1Oracle.colorThenBlend(value, back, mode, coverageF32)
    }

    private fun source(color: ColorARGB): Array<Interval> {
        val alpha = Interval.input(color.alpha / 255f)
        val rgb = intArrayOf(color.red, color.green, color.blue)
        return Array(4) { if (it == 3) alpha else mul(
            WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(Interval.input(rgb[it] / 255f)), alpha) }
    }

    private fun matrix(input: Array<Interval>, filter: ColorFilter): Array<Interval> {
        require(filter is ColorFilter.Matrix)
        val alpha = input[3]
        val straight = Array(4) { if (it == 3) alpha else when {
            alpha.lower.signum() == 0 && alpha.upper.signum() == 0 -> Interval.ZERO
            alpha.lower.compareTo(BigDecimal.ONE) == 0 && alpha.upper.compareTo(BigDecimal.ONE) == 0 -> input[it]
            else -> WgslFloatEnvelopeV1Oracle.gradientDivide(input[it], alpha)
        } }
        val transformed = Array(4) { row ->
            val left = Array(5) { Interval.input(filter.matrix[row * 5 + it]) }
            val right = Array(5) { if (it == 4) Interval.ONE else straight[it] }
            // Enumerate every sum tree and every permitted product/add fusion.
            // The literal expression's intermediate `let`s are not rounding barriers.
            val cache = mutableMapOf<Int, Interval>()
            fun sum(mask: Int): Interval = cache.getOrPut(mask) {
                if (Integer.bitCount(mask) == 1) Integer.numberOfTrailingZeros(mask).let { mul(left[it], right[it]) }
                else {
                    val alternatives = mutableListOf<Interval>()
                    var part = (mask - 1) and mask
                    while (part > 0) {
                        val rest = mask xor part
                        alternatives += WgslFloatEnvelopeV1Oracle.gradientAdd(sum(part), sum(rest))
                        if (Integer.bitCount(part) == 1) Integer.numberOfTrailingZeros(part).let {
                            alternatives += WgslFloatEnvelopeV1Oracle.gradientFma(left[it], right[it], sum(rest))
                        }
                        part = (part - 1) and mask
                    }
                    WgslFloatEnvelopeV1Oracle.gradientHull(*alternatives.toTypedArray())
                }
            }
            sum(31).let { Interval(it.lower.coerceIn(BigDecimal.ZERO, BigDecimal.ONE),
                it.upper.coerceIn(BigDecimal.ZERO, BigDecimal.ONE)) }
        }
        return Array(4) { if (it == 3) transformed[3] else mul(transformed[it], transformed[3]) }
    }
    private fun mul(a: Interval, b: Interval) = WgslFloatEnvelopeV1Oracle.gradientMultiply(a, b)
    private fun clamp(value: Interval) = Interval(value.lower.coerceIn(BigDecimal.ZERO,BigDecimal.ONE),
        value.upper.coerceIn(BigDecimal.ZERO,BigDecimal.ONE))
    private fun unpremultiply(input: Array<Interval>) = Array(4) { if (it == 3) input[3] else when {
        input[3].lower.signum() == 0 && input[3].upper.signum() == 0 -> Interval.ZERO
        input[3].lower.compareTo(BigDecimal.ONE) == 0 && input[3].upper.compareTo(BigDecimal.ONE) == 0 -> input[it]
        else -> WgslFloatEnvelopeV1Oracle.gradientDivide(input[it],input[3])
    } }
    private fun premultiply(straight: Array<Interval>) = Array(4) { if (it == 3) straight[3] else mul(straight[it],straight[3]) }
}
