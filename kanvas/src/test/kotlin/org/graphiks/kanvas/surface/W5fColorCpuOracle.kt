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
        is ColorFilter.HSLAMatrix -> {
            val u = unpremultiply(input)
            // Retain the disjoint hue intervals through the matrix and inverse;
            // h≈0 and h≈1 must not become a spurious full-circle hue box.
            val alternatives = rgbToHsl(u).map { hsl ->
                val transformed = matrixRows(arrayOf(hsl[0],hsl[1],hsl[2],u[3]),filter.values)
                val rgb = hslToRgb(transformed)
                premultiply(Array(4) { clamp(if (it == 3) transformed[3] else rgb[it]) })
            }
            Array(4) { c -> hull(*alternatives.map { it[c] }.toTypedArray()) }
        }
        ColorFilter.HighContrast -> {
            val u = unpremultiply(input)
            premultiply(Array(4) { if (it == 3) u[3] else {
                val centered = sub(u[it],Interval.input(.5f))
                clamp(hull(add(Interval.input(.5f),mul(Interval.input(3f),centered)),
                    WgslFloatEnvelopeV1Oracle.gradientFma(Interval.input(3f),centered,Interval.input(.5f))))
            } })
        }
        ColorFilter.Luma -> {
            val u = unpremultiply(input)
            val luma = matrixRows(u,floatArrayOf(.2126f,.7152f,.0722f,0f,0f,
                0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f, 0f,0f,0f,0f,0f))[0]
            arrayOf(Interval.ZERO,Interval.ZERO,Interval.ZERO,mul(u[3],luma))
        }
        ColorFilter.Overdraw -> {
            val scaled = mul(clamp(input[3]),Interval.input(255f))
            val first = scaled.lower.subtract(BigDecimal("0.5")).setScale(0,RoundingMode.CEILING).toInt().coerceIn(0,5)
            val last = scaled.upper.add(BigDecimal("0.5")).setScale(0,RoundingMode.FLOOR).toInt().coerceIn(0,5)
            val palette = listOf(ColorARGB.of(128,255,0,0),ColorARGB.of(128,0,255,0),ColorARGB.of(128,0,0,255),
                ColorARGB.of(128,255,255,0),ColorARGB.of(128,0,255,255),ColorARGB.of(128,255,0,255))
            val choices = (first..last).map { source(palette[it]) }
            Array(4) { c -> hull(*choices.map { it[c] }.toTypedArray()) }
        }
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
        val transformed = matrixRows(straight,filter.matrix.toFloatArray()).map(::clamp).toTypedArray()
        return premultiply(transformed)
    }
    private fun matrixRows(straight: Array<Interval>, coefficients: FloatArray): Array<Interval> = Array(4) { row ->
            val left = Array(5) { Interval.input(coefficients[row * 5 + it]) }
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
            sum(31)
        }
    private fun add(a: Interval,b: Interval) = WgslFloatEnvelopeV1Oracle.gradientAdd(a,b)
    private fun sub(a: Interval,b: Interval) = WgslFloatEnvelopeV1Oracle.gradientSubtract(a,b)
    private fun div(a: Interval,b: Interval) = WgslFloatEnvelopeV1Oracle.gradientDivide(a,b)
    private fun hull(vararg a: Interval) = WgslFloatEnvelopeV1Oracle.gradientHull(*a)
    private fun abs(a: Interval) = Interval(if (a.lower.signum() <= 0 && a.upper.signum() >= 0) BigDecimal.ZERO
        else minOf(a.lower.abs(),a.upper.abs()),maxOf(a.lower.abs(),a.upper.abs()))
    private fun point(a: Interval, value: Int) = a.lower.compareTo(BigDecimal(value)) == 0 && a.upper.compareTo(BigDecimal(value)) == 0
    private fun floors(a: Interval): IntRange {
        val normal = BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
        val upper = if (a.lower < normal && a.upper > normal.negate()) maxOf(a.upper,BigDecimal.ZERO) else a.upper
        return a.lower.setScale(0,RoundingMode.FLOOR).intValueExact()..upper.setScale(0,RoundingMode.FLOOR).intValueExact()
    }
    private fun minmax(a: Interval,b: Interval,minimum: Boolean): Interval {
        val normal = BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
        if (a.lower < normal && a.upper > normal.negate() && b.lower < normal && b.upper > normal.negate()) return hull(a,b)
        return if (minimum) Interval(minOf(a.lower,b.lower),minOf(a.upper,b.upper))
            else Interval(maxOf(a.lower,b.lower),maxOf(a.upper,b.upper))
    }
    private fun modulo(a: Interval, modulus: Int): List<Interval> {
        val m = Interval.input(modulus.toFloat())
        require(modulus == 1 || modulus == 2)
        val quotient = if (modulus == 1) a else mul(a,Interval.input(.5f))
        val range = floors(quotient)
        val first = range.first; val last = range.last
        require(last-first <= 8) { "Hue interval spans too many modulo branches" }
        return (first..last).map { sub(a,mul(Interval.input(it.toFloat()),m)) }
    }
    private fun rgbToHsl(u: Array<Interval>): List<Array<Interval>> {
        val max = minmax(u[0],minmax(u[1],u[2],false),false)
        val min = minmax(u[0],minmax(u[1],u[2],true),true)
        val delta = sub(max,min)
        val light = div(add(max,min),Interval.input(2f))
        if (point(delta,0)) return listOf(arrayOf(Interval.ZERO,Interval.ZERO,light))
        require(delta.lower.signum() > 0) { "RGB delta crosses zero" }
        val saturation = div(delta,sub(Interval.ONE,abs(sub(mul(Interval.input(2f),light),Interval.ONE))))
        val hues = mutableListOf<Interval>()
        for (c in 0..2) if (u[c].upper >= max.lower) {
            val numerator = sub(u[(c+1)%3],u[(c+2)%3])
            val raw = div(add(div(numerator,delta),Interval.input((2*c).toFloat())),Interval.input(6f))
            hues += modulo(raw,1)
        }
        return hues.map { arrayOf(it,saturation,light) }
    }
    private fun hslToRgb(hsla: Array<Interval>): Array<Interval> {
        val chroma = mul(sub(Interval.ONE,abs(sub(mul(Interval.input(2f),hsla[2]),Interval.ONE))),hsla[1])
        val m = sub(hsla[2],div(chroma,Interval.input(2f)))
        val choices = mutableListOf<Array<Interval>>()
        for (h in modulo(hsla[0],1)) {
            val sixH = mul(Interval.input(6f),h)
            for (mod2 in modulo(sixH,2)) {
                val x = mul(chroma,sub(Interval.ONE,abs(sub(mod2,Interval.ONE))))
                val sectors = floors(sixH)
                val first = sectors.first; val last = sectors.last
                require(last-first <= 8)
                for (sector in first..last) {
                    val values = when (Math.floorMod(sector,6)) {
                        0 -> arrayOf(chroma,x,Interval.ZERO); 1 -> arrayOf(x,chroma,Interval.ZERO)
                        2 -> arrayOf(Interval.ZERO,chroma,x); 3 -> arrayOf(Interval.ZERO,x,chroma)
                        4 -> arrayOf(x,Interval.ZERO,chroma); else -> arrayOf(chroma,Interval.ZERO,x)
                    }
                    choices += Array(3) { add(values[it],m) }
                }
            }
        }
        return Array(3) { c -> hull(*choices.map { it[c] }.toTypedArray()) }
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
