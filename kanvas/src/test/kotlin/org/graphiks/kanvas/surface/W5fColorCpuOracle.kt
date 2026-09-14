package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.RoundingMode
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval

/** Independent published equations, with directed arithmetic supplied by the test envelope. */
@OptIn(ExperimentalUnsignedTypes::class)
internal object W5fColorCpuOracle {
    private val advancedBlends = setOf(BlendMode.MULTIPLY,BlendMode.SCREEN,BlendMode.OVERLAY,
        BlendMode.DARKEN,BlendMode.LIGHTEN,BlendMode.DIFFERENCE,BlendMode.EXCLUSION,
        BlendMode.COLOR_DODGE,BlendMode.COLOR_BURN,BlendMode.HARD_LIGHT,BlendMode.SOFT_LIGHT,
        BlendMode.HUE,BlendMode.SATURATION,BlendMode.COLOR,BlendMode.LUMINOSITY)
    private fun blend(src: Array<Interval>,dst: Array<Interval>,mode: BlendMode): Array<Interval> {
        if (mode in advancedBlends) {
            // R41 target algorithm has actual lazy branches in this priority.
            // This is not a claim that an executed zero-numerator divide is exact.
            if (point(src[3],0)) return dst.copyOf()
            if (dst.all { point(it,0) }) return src.copyOf()
        }
        return WgslFloatEnvelopeV1Oracle.filterBlend(src,dst,mode)
    }
    enum class ImageOrder { Correct, FilterBeforeMask, FilterBeforeAtlasEntry, FilterBeforeAtlasPaint, FilterTwice }

    /** Independent decoded bytes, discrete addressing, sampled source, and ordered color equations. */
    fun expectedImagePixel(image: org.graphiks.kanvas.image.Image,
        sampling: org.graphiks.kanvas.paint.SamplingOptions,
        sourcePointF32: org.graphiks.math.geometry.Point2F32,
        paint: org.graphiks.kanvas.paint.Paint, atlasEntryColor: ColorARGB? = null,
        atlasEntryBlend: BlendMode? = null, destination: ColorARGB = ColorARGB.Transparent,
        finalBlend: BlendMode = BlendMode.SRC_OVER, coverageF32: Float = 1f,
        tileX: org.graphiks.kanvas.paint.TileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
        tileY: org.graphiks.kanvas.paint.TileMode = org.graphiks.kanvas.paint.TileMode.CLAMP,
        childPointF32: org.graphiks.math.geometry.Point2F32 = sourcePointF32,
        order: ImageOrder = ImageOrder.Correct,
        destinationBlend: BlendMode = BlendMode.SRC_OVER): WgslFloatEnvelopeV1Oracle.DrawResult {
        val mask = image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8
        val sampled = sampledImage(image,sampling,sourcePointF32,tileX,tileY)
        fun filter(value: Array<Interval>) = paint.colorFilter?.let { applyFilter(value,it) } ?: value
        var value = if (mask) {
            val childPaint = if (atlasEntryColor == null) paint else paint.copy(color=paint.color.withAlpha(255))
            var child = childPaint.shader?.let { shaderSource(it,childPointF32) } ?: source(childPaint.color)
            if (childPaint.shader != null) child = child.map { mul(it,Interval.input(childPaint.color.alphaNormalized)) }.toTypedArray()
            if (order == ImageOrder.FilterBeforeMask) child = filter(child)
            child.map { mul(it,sampled[0]) }.toTypedArray()
        } else imageWrappers(paint.shader,sampled)
        if (order == ImageOrder.FilterBeforeAtlasEntry) value = filter(value)
        if (atlasEntryColor != null)
            value = blend(source(atlasEntryColor),value,requireNotNull(atlasEntryBlend))
        if (order == ImageOrder.FilterBeforeAtlasPaint) value = filter(value)
        if (!mask || atlasEntryColor != null) value = value.map { mul(it,Interval.input(paint.color.alphaNormalized)) }.toTypedArray()
        if (order in setOf(ImageOrder.Correct,ImageOrder.FilterTwice)) value = filter(value)
        if (order == ImageOrder.FilterTwice) value = filter(value)
        return finish(value,destination,finalBlend,coverageF32,destinationBlend)
    }

    private fun imageWrappers(shader: Shader?,sample: Array<Interval>): Array<Interval> = when (shader) {
        is Shader.Opacity -> imageWrappers(shader.shader,sample).map { mul(it,Interval.input(shader.alphaF32)) }.toTypedArray()
        is Shader.WithColorFilter -> applyFilter(imageWrappers(shader.shader,sample),shader.filter)
        is Shader.WithWorkingColorSpace -> imageWrappers(shader.shader,sample)
        is Shader.WithLocalMatrix -> imageWrappers(shader.shader,sample)
        else -> sample
    }

    private fun shaderSource(shader: Shader,point: org.graphiks.math.geometry.Point2F32,
        working: ColorSpaceInterpolation? = null): Array<Interval> = when (shader) {
        is Shader.SolidColor -> source(shader.color)
        is Shader.Opacity -> shaderSource(shader.shader,point,working).map { mul(it,Interval.input(shader.alphaF32)) }.toTypedArray()
        is Shader.WithColorFilter -> applyFilter(shaderSource(shader.shader,point,working),shader.filter)
        is Shader.WithWorkingColorSpace -> shaderSource(shader.shader,point,working ?: shader.interpolation)
        is Shader.LinearGradient -> {
            require(shader.stops.size == 2 && shader.stops[0].position == 0f && shader.stops[1].position == 1f)
            val dx = sub(Interval.input(shader.end.x),Interval.input(shader.start.x))
            val dy = sub(Interval.input(shader.end.y),Interval.input(shader.start.y))
            val x = sub(Interval.input(point.x),Interval.input(shader.start.x))
            val y = sub(Interval.input(point.y),Interval.input(shader.start.y))
            val parameter = div(add(mul(x,dx),mul(y,dy)),add(mul(dx,dx),mul(dy,dy)))
            require(shader.tileMode == org.graphiks.kanvas.paint.TileMode.CLAMP)
            gradientSource(working ?: shader.interpolation,shader.stops[0].color,shader.stops[1].color,parameter)
        }
        else -> error("Independent A8 child fixture requires a supported public solid/linear-gradient wrapper tree")
    }

    private fun sampledImage(image: org.graphiks.kanvas.image.Image,sampling: org.graphiks.kanvas.paint.SamplingOptions,
        pointF32: org.graphiks.math.geometry.Point2F32,tileX: org.graphiks.kanvas.paint.TileMode,
        tileY: org.graphiks.kanvas.paint.TileMode): Array<Interval> {
        val bytes = requireNotNull(image.pixels)
        val mask = image.colorType == org.graphiks.kanvas.image.ColorType.ALPHA_8
        fun address(index: Int,size: Int,tile: org.graphiks.kanvas.paint.TileMode): Int? = when (tile) {
            org.graphiks.kanvas.paint.TileMode.CLAMP -> index.coerceIn(0,size-1)
            org.graphiks.kanvas.paint.TileMode.REPEAT -> Math.floorMod(index,size)
            org.graphiks.kanvas.paint.TileMode.MIRROR -> Math.floorMod(index,Math.multiplyExact(size,2)).let { minOf(it,2*size-1-it) }
            org.graphiks.kanvas.paint.TileMode.DECAL -> index.takeIf { it in 0 until size }
        }
        fun texel(x: Int,y: Int): Array<Interval> {
        val xI32 = address(x,image.width,tileX) ?: return Array(4) { Interval.ZERO }
        val yI32 = address(y,image.height,tileY) ?: return Array(4) { Interval.ZERO }
        val offsetI32 = Math.addExact(Math.multiplyExact(yI32,image.rowBytesI32),Math.multiplyExact(xI32,if(mask) 1 else 4))
        fun channel(index: Int) = WgslFloatEnvelopeV1Oracle.imageUnorm8(bytes[offsetI32+index].toInt() and 255)
        val alpha = if (image.alphaType == org.graphiks.kanvas.image.AlphaType.OPAQUE) Interval.ONE
            else channel(if(mask) 0 else 3)
        return if (mask) Array(4) { alpha } else if (point(alpha,0)) Array(4) { Interval.ZERO } else {
            val rgb = if (image.colorType == org.graphiks.kanvas.image.ColorType.BGRA_8888)
                listOf(channel(2),channel(1),channel(0)) else List(3,::channel)
            val attachment = image.premultiplication == org.graphiks.kanvas.render.ir.ImagePremultiplicationV1.TRANSFER_ENCODED_LINEAR_PREMUL
            val straight = rgb.map { if (!attachment && image.alphaType == org.graphiks.kanvas.image.AlphaType.PREMUL && !point(alpha,1)) div(it,alpha) else it }
            val transferred = straight.map { if (image.colorSpace == org.graphiks.kanvas.color.ColorSpace.LINEAR_SRGB) it
                else WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(it) }.toTypedArray()
            val linear = if (image.colorSpace == org.graphiks.kanvas.color.ColorSpace.DISPLAY_P3)
                matrixRows(transferred+Interval.ZERO,floatArrayOf(1.2247455f,-.2249044f,0f,0f,0f,
                    -.0420581f,1.0420810f,0f,0f,0f, -.0196423f,-.0786549f,1.0985372f,0f,0f,
                    0f,0f,0f,1f,0f)) else transferred
            Array(4) { if(it==3) alpha else if(attachment) linear[it] else mul(linear[it],alpha) }
        }
        }
        if (sampling == org.graphiks.kanvas.paint.SamplingOptions.NEAREST)
            return texel(kotlin.math.floor(pointF32.x).toInt(),kotlin.math.floor(pointF32.y).toInt())
        val x = sub(Interval.input(pointF32.x),Interval.input(.5f))
        val y = sub(Interval.input(pointF32.y),Interval.input(.5f))
        fun bases(value: Interval): IntRange {
            val first = value.lower.setScale(0,RoundingMode.FLOOR).intValueExact()
            val last = value.upper.setScale(0,RoundingMode.FLOOR).intValueExact()
            require(last.toLong()-first.toLong() <= 1L)
            return first..last
        }
        fun cell(value: Interval,base: Int) = Interval(maxOf(value.lower,BigDecimal(base)),
            minOf(value.upper,BigDecimal(base.toLong()+1)))
        val alternatives = mutableListOf<Array<Interval>>()
        for (baseY in bases(y)) for (baseX in bases(x)) {
            val px = cell(x,baseX); val py = cell(y,baseY)
            val cubic = sampling as? org.graphiks.kanvas.paint.SamplingOptions.Cubic
            val offsets = if (cubic == null) 0..1 else -1..2
            val fx = sub(px,Interval.input(baseX.toFloat())); val fy = sub(py,Interval.input(baseY.toFloat()))
            val wx = offsets.map { if (cubic != null) cubicWeight(sub(px,Interval.input((baseX+it).toFloat())),cubic)
                else if (it == 0) sub(Interval.ONE,fx) else fx }
            val wy = offsets.map { if (cubic != null) cubicWeight(sub(py,Interval.input((baseY+it).toFloat())),cubic)
                else if (it == 0) sub(Interval.ONE,fy) else fy }
            val taps = offsets.flatMap { iy -> offsets.map { ix ->
                texel(baseX+ix,baseY+iy) to mul(wx[ix-offsets.first],wy[iy-offsets.first])
            } }
            alternatives += Array(4) { channel -> roundedSum(taps.map { (rgba,w) -> mul(rgba[channel],w) }) }
        }
        return Array(4) { c -> hull(*alternatives.map { it[c] }.toTypedArray()) }
    }

    /** Directed sum plus gamma(n) covers every F32 sum tree and fused product/sum choice. */
    private fun roundedSum(terms: List<Interval>): Interval {
        val low = terms.fold(BigDecimal.ZERO) { sum,it -> sum+it.lower }
        val high = terms.fold(BigDecimal.ZERO) { sum,it -> sum+it.upper }
        val magnitude = terms.fold(BigDecimal.ZERO) { sum,it -> sum+maxOf(it.lower.abs(),it.upper.abs()) }
        val nu = BigDecimal(terms.size).multiply(BigDecimal(Math.scalb(1.0,-23)))
        val gamma = nu.divide(BigDecimal.ONE-nu,java.math.MathContext(80,RoundingMode.CEILING))
        val ftz = BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())*BigDecimal(terms.size)*(BigDecimal.ONE+gamma)
        val error = magnitude*gamma+ftz
        return Interval(low-error,high+error)
    }

    private fun cubicWeight(distance: Interval,cubic: org.graphiks.kanvas.paint.SamplingOptions.Cubic): Interval {
        val x = abs(distance)
        val b = Interval.input(cubic.B); val c = Interval.input(cubic.C)
        fun scale(n: Float,value: Interval) = mul(Interval.input(n),value)
        fun polynomial(v: Interval,outer: Boolean): Interval {
            val c3 = if (outer) roundedSum(listOf(scale(-1f,b),scale(-6f,c)))
                else roundedSum(listOf(Interval.input(12f),scale(-9f,b),scale(-6f,c)))
            val c2 = if (outer) roundedSum(listOf(scale(6f,b),scale(30f,c)))
                else roundedSum(listOf(Interval.input(-18f),scale(12f,b),scale(6f,c)))
            val c0 = if (outer) roundedSum(listOf(scale(8f,b),scale(24f,c)))
                else roundedSum(listOf(Interval.input(6f),scale(-2f,b)))
            val terms = mutableListOf(mul(mul(mul(c3,v),v),v),mul(mul(c2,v),v),c0)
            if (outer) terms += mul(roundedSum(listOf(scale(-12f,b),scale(-48f,c))),v)
            return div(roundedSum(terms),Interval.input(6f))
        }
        val values = mutableListOf<Interval>()
        if (x.lower < BigDecimal.ONE) values += polynomial(Interval(x.lower,minOf(x.upper,BigDecimal.ONE)),false)
        if (x.upper >= BigDecimal.ONE && x.lower < BigDecimal(2))
            values += polynomial(Interval(maxOf(x.lower,BigDecimal.ONE),minOf(x.upper,BigDecimal(2))),true)
        if (x.upper >= BigDecimal(2)) values += Interval.ZERO
        return hull(*values.toTypedArray())
    }

    /** Independent host preparation followed by the rounded fragment schedule. */
    fun expectedGradientPixel(domain: ColorSpaceInterpolation, left: ColorARGB, right: ColorARGB,
        tF32: Float, external: ColorFilter? = null, destination: ColorARGB = ColorARGB.Transparent,
        finalBlend: BlendMode = BlendMode.SRC_OVER, coverageF32: Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult =
        expectedGradientPixel(domain,left,right,Interval.input(tF32),external,destination,finalBlend,coverageF32)

    fun expectedGradientPixel(domain: ColorSpaceInterpolation, left: ColorARGB, right: ColorARGB,
        parameter: Interval, external: ColorFilter? = null, destination: ColorARGB = ColorARGB.Transparent,
        finalBlend: BlendMode = BlendMode.SRC_OVER, coverageF32: Float = 1f,
        destinationBlend: BlendMode = BlendMode.SRC_OVER, shaderOpacityF32: Float = 1f,
        paintAlphaF32: Float = 1f): WgslFloatEnvelopeV1Oracle.DrawResult {
        var result = gradientSource(domain,left,right,parameter)
        if (shaderOpacityF32 != 1f) result = result.map { mul(it,Interval.input(shaderOpacityF32)) }.toTypedArray()
        if (paintAlphaF32 != 1f) result = result.map { mul(it,Interval.input(paintAlphaF32)) }.toTypedArray()
        if (external != null) result = applyFilter(result,external)
        return finish(result,destination,finalBlend,coverageF32,destinationBlend)
    }

    private fun gradientSource(domain: ColorSpaceInterpolation,left: ColorARGB,right: ColorARGB,
        parameter: Interval): Array<Interval> {
        val a = preparedStop(left,domain)
        val b = preparedStop(right,domain)
        val t = clamp(parameter)
        val inverse = sub(Interval.ONE,t)
        val interpolated = Array(4) { c -> hull(add(mul(inverse,a[c]),mul(t,b[c])),
            WgslFloatEnvelopeV1Oracle.gradientFma(inverse,a[c],mul(t,b[c])),
            WgslFloatEnvelopeV1Oracle.gradientFma(t,b[c],mul(inverse,a[c]))) }
        if (domain == ColorSpaceInterpolation.HSL || domain == ColorSpaceInterpolation.OKLCH) {
            val hue = if (domain == ColorSpaceInterpolation.HSL) 0 else 2
            val h0 = if (point(a[1],0)) b[hue] else a[hue]
            val h1 = if (point(b[1],0)) h0 else b[hue]
            val difference = sub(h1,h0)
            val half = BigDecimal("0.5")
            val deltas = mutableListOf<Interval>()
            if (difference.lower <= -half) deltas += add(Interval(difference.lower,minOf(difference.upper,-half)),Interval.ONE)
            if (difference.upper > half) deltas += sub(Interval(maxOf(difference.lower,half),difference.upper),Interval.ONE)
            if (difference.upper > -half && difference.lower <= half)
                deltas += Interval(maxOf(difference.lower,-half),minOf(difference.upper,half))
            interpolated[hue] = deltas.map { d -> hull(add(h0,mul(t,d)),
                WgslFloatEnvelopeV1Oracle.gradientFma(t,d,h0)) }.reduce { x,y -> hull(x,y) }
        }
        val linear = when (domain) {
            ColorSpaceInterpolation.SRGB -> Array(3) { WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(interpolated[it]) }
            ColorSpaceInterpolation.LINEAR -> interpolated.copyOfRange(0,3)
            ColorSpaceInterpolation.HSL -> hslToRgb(interpolated).map {
                WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(it) }.toTypedArray()
            ColorSpaceInterpolation.OKLAB, ColorSpaceInterpolation.OKLCH -> {
                val lab = if (domain == ColorSpaceInterpolation.OKLAB) interpolated else {
                    val pair = polarUnit(interpolated[2])
                    arrayOf(interpolated[0],mul(interpolated[1],pair[0]),mul(interpolated[1],pair[1]),interpolated[3])
                }
                val lms = matrixRows(lab,floatArrayOf(
                    1f,.3963377774f,.2158037573f,0f,0f,
                    1f,-.1055613458f,-.0638541728f,0f,0f,
                    1f,-.0894841775f,-1.2914855480f,0f,0f,
                    0f,0f,0f,0f,0f))
                val cubes = Array(4) { if (it == 3) Interval.ZERO else mul(mul(lms[it],lms[it]),lms[it]) }
                matrixRows(cubes,floatArrayOf(
                    4.0767416621f,-3.3077115913f,.2309699292f,0f,0f,
                    -1.2684380046f,2.6097574011f,-.3413193965f,0f,0f,
                    -.0041960863f,-.7034186147f,1.7076147010f,0f,0f,
                    0f,0f,0f,0f,0f)).copyOfRange(0,3)
            }
        }
        return Array(4) { if (it == 3) interpolated[3] else mul(linear[it],interpolated[3]) }
    }

    private fun preparedStop(color: ColorARGB, domain: ColorSpaceInterpolation): Array<Interval> {
        // JVM float arithmetic rounds each product/add before the next. The
        // transcendental reference has a two-F64-ULP enclosure: one for the
        // reference's specified error and one for any conforming host result.
        fun roundedHost(low: BigDecimal, high: BigDecimal) = Interval(
            BigDecimal(low.toFloat().toDouble()),BigDecimal(high.toFloat().toDouble()))
        fun hostAdd(a: Interval,b: Interval) = roundedHost(a.lower+b.lower,a.upper+b.upper)
        fun hostMul(a: Interval,b: Interval): Interval {
            val products = listOf(a.lower*b.lower,a.lower*b.upper,a.upper*b.lower,a.upper*b.upper)
            return roundedHost(products.min(),products.max())
        }
        fun transcendental(a: Interval, operation: (Double)->Double): Interval {
            fun bound(v: BigDecimal, upper: Boolean): BigDecimal {
                val computed = operation(v.toDouble())
                val error = 2.0*Math.ulp(computed)
                return BigDecimal(if (upper) Math.nextUp(computed+error) else Math.nextDown(computed-error))
            }
            return roundedHost(bound(a.lower,false),bound(a.upper,true))
        }
        fun hostEotf(v: Float): Interval {
            if (v == 0f || v == 1f) return Interval.input(v)
            if (v <= .04045f) return Interval.input(v/12.92f)
            return transcendental(Interval.input((v+.055f)/1.055f)) { StrictMath.pow(it,2.4f.toDouble()) }
        }
        val rgb = floatArrayOf(color.red/255f,color.green/255f,color.blue/255f)
        if (domain == ColorSpaceInterpolation.HSL) {
            // All elementary JVM F32 operations have one specified rounded result.
            val high = rgb.max(); val low = rgb.min(); val delta = high-low
            val light = (high+low)/2f
            val gray = color.red == color.green && color.green == color.blue
            val saturation = if (gray) 0f else delta/(1f-kotlin.math.abs(2f*light-1f))
            val rawHue = if (gray) 0f else (when (high) {
                rgb[0] -> (rgb[1]-rgb[2])/delta
                rgb[1] -> (rgb[2]-rgb[0])/delta+2f
                else -> (rgb[0]-rgb[1])/delta+4f
            })/6f
            return arrayOf(Interval.input(rawHue-StrictMath.floor(rawHue.toDouble()).toFloat()),
                Interval.input(saturation),Interval.input(if (gray) rgb[0] else light),Interval.input(color.alpha/255f))
        }
        val channels = if (domain == ColorSpaceInterpolation.SRGB) Array(3) { Interval.input(rgb[it]) }
            else Array(3) { hostEotf(rgb[it]) }
        fun row(values: Array<Interval>,r: Float,g: Float,b: Float) = hostAdd(
            hostAdd(hostMul(Interval.input(r),values[0]),hostMul(Interval.input(g),values[1])),
            hostMul(Interval.input(b),values[2]))
        val prepared = if (domain != ColorSpaceInterpolation.OKLAB && domain != ColorSpaceInterpolation.OKLCH) channels else {
            val lms = arrayOf(row(channels,.4122214708f,.5363325363f,.0514459929f),
                row(channels,.2119034982f,.6806995451f,.1073969566f),
                row(channels,.0883024619f,.2817188376f,.6299787005f))
            val roots = Array(3) { transcendental(lms[it],StrictMath::cbrt) }
            arrayOf(row(roots,.2104542553f,.7936177850f,-.0040720468f),
                row(roots,1.9779984951f,-2.4285922050f,.4505937099f),
                row(roots,.0259040371f,.7827717662f,-.8086757660f))
        }
        if (domain == ColorSpaceInterpolation.OKLCH) {
            if (color.red == color.green && color.green == color.blue)
                return arrayOf(prepared[0],Interval.ZERO,Interval.ZERO,Interval.input(color.alpha/255f))
            val chroma = transcendental(hostAdd(hostMul(prepared[1],prepared[1]),hostMul(prepared[2],prepared[2])),StrictMath::sqrt)
            val angles = listOf(prepared[2].lower,prepared[2].upper).flatMap { y ->
                listOf(prepared[1].lower,prepared[1].upper).map { x -> StrictMath.atan2(y.toDouble(),x.toDouble()) } }
            val lo = angles.min(); val hi = angles.max()
            require(hi-lo < Math.PI) { "Host atan2 branch-cut fixture must be split" }
            val angle = roundedHost(BigDecimal(Math.nextDown(lo-2*Math.ulp(lo))),BigDecimal(Math.nextUp(hi+2*Math.ulp(hi))))
            val divisor = BigDecimal(6.2831855f.toDouble())
            val turns = roundedHost(angle.lower.divide(divisor,80,RoundingMode.FLOOR),angle.upper.divide(divisor,80,RoundingMode.CEILING))
            val floor = StrictMath.floor(turns.lower.toDouble()).toFloat()
            require(StrictMath.floor(turns.upper.toDouble()).toFloat() == floor)
            val hue = roundedHost(turns.lower-BigDecimal(floor.toDouble()),turns.upper-BigDecimal(floor.toDouble()))
            return arrayOf(prepared[0],chroma,hue,Interval.input(color.alpha/255f))
        }
        return Array(4) { if (it == 3) Interval.input(color.alpha/255f) else prepared[it] }
    }

    /** Independent quarter-turn reduction; genuine WGSL sin/cos error is absolute 2^-11. */
    private fun polarUnit(hue: Interval): Array<Interval> {
        val alternatives = mutableListOf<Array<Interval>>()
        for (wrapped in modulo(hue,1)) {
            for (sector in 0..3) {
                val low = BigDecimal(sector).divide(BigDecimal(4))
                val high = BigDecimal(sector+1).divide(BigDecimal(4))
                if (wrapped.upper < low || wrapped.lower > high) continue
                val h = Interval(maxOf(low,wrapped.lower),minOf(high,wrapped.upper))
                val reduced = when (sector) {
                    0 -> h
                    1 -> sub(Interval.input(.5f),h)
                    2 -> sub(h,Interval.input(.5f))
                    else -> sub(Interval.ONE,h)
                }
                val radians = mul(reduced,Interval.input(6.2831855f))
                fun trig(cosine: Boolean): Interval {
                    require(radians.lower.toDouble() >= -Math.PI && radians.upper.toDouble() <= Math.PI)
                    val function: (Double)->Double = if (cosine) StrictMath::cos else StrictMath::sin
                    val values = listOf(function(radians.lower.toDouble()),function(radians.upper.toDouble()))
                    val error = Math.scalb(1.0,-11)+4*Math.ulp(1.0)
                    // Include an interior extremum when the rounded quarter turn crosses pi/2.
                    val max = if (!cosine && radians.lower.toDouble() <= Math.PI/2 && radians.upper.toDouble() >= Math.PI/2) 1.0 else values.max()
                    return Interval(BigDecimal(Math.nextDown(values.min()-error)),BigDecimal(Math.nextUp(max+error)))
                }
                val cosine = trig(true); val sine = trig(false)
                alternatives += arrayOf(if (sector == 1 || sector == 2) mul(Interval.input(-1f),cosine) else cosine,
                    if (sector >= 2) mul(Interval.input(-1f),sine) else sine)
            }
        }
        return Array(2) { c -> alternatives.map { it[c] }.reduce { a,b -> hull(a,b) } }
    }

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
        is ColorFilter.Blend -> blend(source(filter.color),input,filter.mode)
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
        if (mode in advancedBlends && coverageF32 == 1f) {
            // R41 final destination-read helper has the same real branch order.
            val selected = if (point(value[3],0)) back.linearPremul else
                if (back.linearPremul.all { point(it,0) }) value else null
            if (selected != null) return WgslFloatEnvelopeV1Oracle.colorThenBlend(selected,
                WgslFloatEnvelopeV1Oracle.clearAttachment(),BlendMode.SRC)
        }
        if (mode == BlendMode.SRC_IN && coverageF32 == 1f) {
            // The original destination-read SRC_IN shader evaluates source*Da.
            // Keep the actual stored destination-alpha enclosure and every F32
            // product error; this is neither source SRC nor filter DST.
            val composed = value.map { mul(it,back.linearPremul[3]) }.toTypedArray()
            return WgslFloatEnvelopeV1Oracle.colorThenBlend(composed,
                WgslFloatEnvelopeV1Oracle.clearAttachment(),BlendMode.SRC)
        }
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
