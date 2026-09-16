package org.graphiks.kanvas.surface

import java.math.BigDecimal
import java.math.RoundingMode
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
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

    private fun shaderSource(shader: Shader,point: Point2F32,
        working: ColorSpaceInterpolation? = null): Array<Interval> =
        shaderSource(shader,Interval.input(point.x),Interval.input(point.y),working,emptyList())

    // Snapshot coefficients at each edge. Uninterrupted outer-to-inner segments
    // compose in F64 and project their inverse only once, at clamp or leaf.
    private fun matrixValues(matrix: Matrix3x3F32): List<Double> = listOf(matrix.sx,matrix.kx,matrix.tx,
        matrix.ky,matrix.sy,matrix.ty,matrix.persp0,matrix.persp1,matrix.persp2).map { it.toDouble() }

    private fun inverseSegment(segment: List<List<Double>>): List<Float> {
        var product = listOf(1.0,0.0,0.0,0.0,1.0,0.0,0.0,0.0,1.0)
        segment.forEach { right ->
            require(right.all { it.isFinite() })
            val left = product
            product = List(9) { index ->
                val row = index/3*3; val col = index%3
                (left[row]*right[col]+left[row+1]*right[col+3])+left[row+2]*right[col+6]
            }
            require(product.all { it.isFinite() })
        }
        val (a,b,c) = product
        val d=product[3]; val e=product[4]; val f=product[5]
        val g=product[6]; val h=product[7]; val i=product[8]
        val ca=e*i-f*h; val cb=f*g-d*i; val cc=d*h-e*g
        val determinant=a*ca+b*cb+c*cc
        require(determinant.isFinite() && determinant != 0.0)
        return listOf(ca,c*h-b*i,b*f-c*e,cb,a*i-c*g,c*d-a*f,cc,b*g-a*h,a*e-b*d).map {
            val projected=(it/determinant).toFloat()
            require(projected.isFinite())
            if(projected == 0f) 0f else projected
        }
    }

    private fun mapSegment(x: Interval,y: Interval,segment: List<List<Double>>): Pair<Interval,Interval> {
        if(segment.isEmpty()) return x to y
        val inverse=inverseSegment(segment)
        fun row(offset: Int): Interval = matrixRows(arrayOf(x,y,Interval.ZERO,Interval.ZERO),
            floatArrayOf(inverse[offset],inverse[offset+1],0f,0f,inverse[offset+2],
                0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f,0f))[0]
        val hx=row(0); val hy=row(3)
        if(inverse[6] == 0f && inverse[7] == 0f && inverse[8] == 1f) return hx to hy
        val hw=row(6)
        return projectiveDivide(hx,hw) to projectiveDivide(hy,hw)
    }

    private fun projectiveDivide(numerator: Interval,denominator: Interval): Interval {
        // Independent binary-parts schedule: exact exponent extraction/scaling,
        // only the normalized fraction division incurs the published DIV error.
        // A zero/FTZ denominator or an overflow-validity alternative cannot be
        // collapsed into a fabricated finite point; that fixture stays unbounded.
        val normal=BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
        require(denominator.lower >= normal || denominator.upper <= -normal) { "Unbounded coordinate validity predicate" }
        if(point(numerator,0)) return Interval.ZERO
        fun power(exponent: Int) = BigDecimal(Math.scalb(1.0,exponent))
        fun parts(value: Interval): List<Pair<Interval,Int>> = buildList {
            for(sign in listOf(-1,1)) for(exponent in -148..128) {
                val low=power(exponent-1); val high=power(exponent)
                val start=maxOf(value.lower,if(sign > 0) low else -high)
                val end=minOf(value.upper,if(sign > 0) high else -low)
                if(start <= end) add(Interval(start.divide(high),end.divide(high)) to exponent)
            }
        }
        val alternatives=mutableListOf<Interval>()
        if(numerator.lower < normal && numerator.upper > -normal) alternatives += Interval.ZERO
        for((a,ae) in parts(numerator)) for((b,be) in parts(denominator)) {
            val fraction=div(a,b)
            val factor=power(ae-be)
            val low=fraction.lower*factor; val high=fraction.upper*factor
            val maximum=BigDecimal(Float.MAX_VALUE.toDouble())
            require(low >= -maximum && high <= maximum) { "Unbounded projective overflow predicate" }
            // ldexp is exact for a representable normal result. Outward endpoint
            // projection plus optional FTZ includes all representable results.
            var lower=low.toFloat(); var upper=high.toFloat()
            if(BigDecimal(lower.toDouble()) > low) lower=Math.nextDown(lower)
            if(BigDecimal(upper.toDouble()) < high) upper=Math.nextUp(upper)
            var result=Interval(BigDecimal(lower.toDouble()),BigDecimal(upper.toDouble()))
            if(low < normal && high > -normal) result=hull(result,Interval.ZERO)
            alternatives += result
        }
        require(alternatives.isNotEmpty()) { "Unbounded projective inputs" }
        return hull(*alternatives.toTypedArray())
    }

    private fun shaderSource(shader: Shader,x: Interval,y: Interval,
        working: ColorSpaceInterpolation?,pending: List<List<Double>>): Array<Interval> = when (shader) {
        is Shader.SolidColor -> source(shader.color)
        is Shader.Opacity -> shaderSource(shader.shader,x,y,working,pending).map { mul(it,Interval.input(shader.alphaF32)) }.toTypedArray()
        is Shader.WithColorFilter -> applyFilter(shaderSource(shader.shader,x,y,working,pending),shader.filter)
        is Shader.WithWorkingColorSpace -> shaderSource(shader.shader,x,y,working ?: shader.interpolation,pending)
        is Shader.WithLocalMatrix -> shaderSource(shader.shader,x,y,working,pending+listOf(matrixValues(shader.matrix)))
        is Shader.CoordClamp -> {
            val (px,py)=mapSegment(x,y,pending)
            fun bound(value: Interval,low: Float,high: Float) = Interval(
                value.lower.coerceIn(BigDecimal(low.toDouble()),BigDecimal(high.toDouble())),
                value.upper.coerceIn(BigDecimal(low.toDouble()),BigDecimal(high.toDouble())))
            shaderSource(shader.shader,bound(px,shader.subset.left,shader.subset.right),
                bound(py,shader.subset.top,shader.subset.bottom),working,emptyList())
        }
        is Shader.Blend -> blend(shaderSource(shader.src,x,y,working,pending),shaderSource(shader.dst,x,y,working,pending),shader.mode)
        is Shader.Image -> {
            val (px,py)=mapSegment(x,y,pending)
            sampledImage(shader.image,shader.sampling,px,py,shader.tileModeX,shader.tileModeY)
        }
        is Shader.PerlinNoise, is Shader.FractalNoise -> {
            val (px,py)=mapSegment(x,y,pending)
            W5gNoiseCpuOracle.source(shader,arrayOf(px,py))
        }
        is Shader.LinearGradient -> {
            val (px,py)=mapSegment(x,y,pending)
            val dxF32=shader.end.x-shader.start.x; val dyF32=shader.end.y-shader.start.y
            val lengthF32=dxF32*dxF32+dyF32*dyF32
            val dx = Interval.input(dxF32); val dy = Interval.input(dyF32)
            val qx = sub(px,Interval.input(shader.start.x))
            val qy = sub(py,Interval.input(shader.start.y))
            val degenerate=kotlin.math.sqrt(lengthF32) <= .000030517578125f
            gradientStops(working ?: shader.interpolation,shader.stops,
                if(degenerate) Interval.ONE else dot(qx,qy,dx,dy),
                if(degenerate) Interval.ONE else Interval.input(lengthF32),shader.tileMode,degenerate)
        }
        is Shader.RadialGradient -> {
            val (px,py)=mapSegment(x,y,pending)
            val degenerate=shader.radius <= .000030517578125f
            val distance=distance(sub(px,Interval.input(shader.center.x)),sub(py,Interval.input(shader.center.y)))
            gradientStops(working ?: shader.interpolation,shader.stops,if(degenerate) Interval.ONE else distance,
                if(degenerate) Interval.ONE else Interval.input(shader.radius),shader.tileMode,degenerate)
        }
        is Shader.SweepGradient -> {
            val (px,py)=mapSegment(x,y,pending)
            val dx=canonicalNormal(sub(px,Interval.input(shader.center.x)))
            val dy=canonicalNormal(sub(py,Interval.input(shader.center.y)))
            val turns=when {
                point(dy,0) && dx.lower.signum() >= 0 -> Interval.ZERO
                point(dy,0) && dx.upper.signum() < 0 -> Interval.input(.5f)
                point(dx,0) && dy.lower.signum() > 0 -> Interval.input(.25f)
                point(dx,0) && dy.upper.signum() < 0 -> Interval.input(.75f)
                else -> {
                    val angle=WgslFloatEnvelopeV1Oracle.gradientAtan2(dy,dx,4096.0)
                    val raw=div(angle,Interval.input(6.2831855f))
                    sub(raw,WgslFloatEnvelopeV1Oracle.gradientFloor(raw))
                }
            }
            val span=shader.endAngle-shader.startAngle
            require(span >= 0f)
            val degenerate=span <= .000030517578125f
            val degrees=mul(turns,Interval.input(360f))
            val numerator=if(!degenerate) sub(degrees,Interval.input(shader.startAngle))
                else if(shader.endAngle <= .000030517578125f) Interval.ONE
                else when {
                    degrees.upper < BigDecimal(shader.endAngle.toDouble()) -> Interval.input(-1f)
                    degrees.lower >= BigDecimal(shader.endAngle.toDouble()) -> Interval.ONE
                    else -> hull(Interval.input(-1f),Interval.ONE)
                }
            gradientStops(working ?: shader.interpolation,shader.stops,
                numerator,if(degenerate) Interval.ONE else Interval.input(span),
                if(shader.startAngle <= 0f && shader.endAngle >= 360f) TileMode.CLAMP else shader.tileMode,degenerate)
        }
        is Shader.ConicalGradient -> {
            val (px,py)=mapSegment(x,y,pending)
            val dx=shader.end.x-shader.start.x; val dy=shader.end.y-shader.start.y
            val dr=shader.endRadius-shader.startRadius; val dd=dx*dx+dy*dy; val a=dd-dr*dr
            val concentric=kotlin.math.sqrt(dd) <= .000030517578125f
            val fully=concentric && kotlin.math.abs(dr) <= .000030517578125f
            val qx=sub(px,Interval.input(shader.start.x)); val qy=sub(py,Interval.input(shader.start.y))
            val r0=Interval.input(shader.startRadius); val delta=Interval.input(dr)
            val distance=distance(qx,qy)
            val stops=if(shader.stops.size == 1) listOf(shader.stops.single().copy(position=0f),
                shader.stops.single().copy(position=1f)) else shader.stops
            if(fully) {
                val parameter=if(shader.endRadius <= .000030517578125f) Interval.ONE else when {
                    distance.upper < BigDecimal(shader.endRadius.toDouble()) -> Interval.input(-1f)
                    distance.lower >= BigDecimal(shader.endRadius.toDouble()) -> Interval.ONE
                    else -> hull(Interval.input(-1f),Interval.ONE)
                }
                gradientStops(working ?: shader.interpolation,stops,parameter,Interval.ONE,shader.tileMode,true)
            } else {
                val candidates=if(concentric) listOf(div(sub(distance,r0),delta)) else {
                    val b=mul(Interval.input(-2f),add(dot(qx,qy,Interval.input(dx),Interval.input(dy)),mul(r0,delta)))
                    val c=sub(dot(qx,qy,qx,qy),mul(r0,r0))
                    if(kotlin.math.abs(a) <= .000030517578125f*maxOf(1f,dd,dr*dr)) {
                        val normal=canonicalNormal(b)
                        if(point(normal,0)) emptyList() else {
                            require(normal.lower.signum()*normal.upper.signum() > 0) { "Unbounded conical B predicate" }
                            listOf(div(sub(Interval.ZERO,c),normal))
                        }
                    } else {
                        val discriminant=sub(mul(b,b),mul(mul(Interval.input(4f),Interval.input(a)),c))
                        if(discriminant.upper.signum() < 0) emptyList() else {
                            require(discriminant.lower.signum() >= 0) { "Unbounded conical discriminant predicate" }
                            val root=WgslFloatEnvelopeV1Oracle.gradientSqrt(discriminant)
                            val denominator=mul(Interval.input(2f),Interval.input(a))
                            listOf(div(sub(sub(Interval.ZERO,b),root),denominator),div(add(sub(Interval.ZERO,b),root),denominator))
                        }
                    }
                }
                val valid=candidates.map(::canonicalNormal).filter { root ->
                    val radius=hull(add(mul(root,delta),r0),WgslFloatEnvelopeV1Oracle.gradientFma(root,delta,r0))
                    require(radius.lower.signum() > 0 || radius.upper.signum() <= 0) { "Unbounded conical radius predicate" }
                    radius.lower.signum() > 0
                }
                if(valid.isEmpty()) Array(4) { Interval.ZERO } else {
                    val root=valid.reduce { left,right -> minmax(left,right,false) }
                    gradientStops(working ?: shader.interpolation,stops,root,Interval.ONE,shader.tileMode)
                }
            }
        }
        else -> error("Independent source fixture requires an admitted scalar/gradient wrapper tree")
    }

    private fun canonicalNormal(value: Interval): Interval {
        val normal=BigDecimal(java.lang.Float.MIN_NORMAL.toDouble())
        if(value.lower > -normal && value.upper < normal) return Interval.ZERO
        return if(value.lower < normal && value.upper > -normal) hull(value,Interval.ZERO) else value
    }

    private fun dot(x: Interval,y: Interval,a: Interval,b: Interval): Interval = hull(
        add(mul(x,a),mul(y,b)),WgslFloatEnvelopeV1Oracle.gradientFma(x,a,mul(y,b)),
        WgslFloatEnvelopeV1Oracle.gradientFma(y,b,mul(x,a)))

    private fun distance(x: Interval,y: Interval): Interval = when {
        point(x,0) -> abs(y)
        point(y,0) -> abs(x)
        else -> WgslFloatEnvelopeV1Oracle.gradientSqrt(minmax(dot(x,y,x,y),Interval.ZERO,false))
    }

    private fun gradientStops(domain: ColorSpaceInterpolation,input: List<GradientStop>,
        numerator: Interval,scale: Interval,tile: TileMode,degenerate: Boolean = false): Array<Interval> {
        require(input.isNotEmpty() && input.all { it.position.isFinite() })
        if(input.size == 1) return source(input.single().color)
        if(degenerate && tile == TileMode.DECAL) return Array(4) { Interval.ZERO }
        val monotone=mutableListOf<GradientStop>()
        input.forEach { stop -> monotone += stop.copy(position=stop.position.coerceIn(monotone.lastOrNull()?.position ?: 0f,1f)) }
        if(monotone.first().position > 0f) monotone.add(0,monotone.first().copy(position=0f))
        if(monotone.last().position < 1f) monotone += monotone.last().copy(position=1f)
        // Interior runs retain their first/last values; periodic exterior runs
        // discard the inaccessible side, independently of physical slab layout.
        val stops=monotone.groupBy { it.position }.flatMap { (position,run) -> when {
            run.size == 1 -> run
            tile != TileMode.CLAMP && position == 0f -> listOf(run.last())
            tile != TileMode.CLAMP && position == 1f -> listOf(run.first())
            else -> listOf(run.first(),run.last())
        } }
        if(degenerate && tile in setOf(TileMode.REPEAT,TileMode.MIRROR)) {
            // Exact decimal arithmetic over binary F32 inputs, one final F32
            // conversion: integrate ORIGINAL straight-sRGB, never prepared tuples.
            val average=Array(4) { channel ->
                fun component(color: ColorARGB): BigDecimal = BigDecimal(when(channel) {
                    0 -> color.redNormalized; 1 -> color.greenNormalized
                    2 -> color.blueNormalized; else -> color.alphaNormalized
                }.toDouble())
                val integral=stops.zipWithNext().fold(BigDecimal.ZERO) { sum,(left,right) ->
                    sum+((BigDecimal(right.position.toDouble())-BigDecimal(left.position.toDouble()))*
                        (component(left.color)+component(right.color))).divide(BigDecimal(2))
                }
                Interval.input(integral.toFloat())
            }
            return Array(4) { if(it == 3) average[3] else mul(WgslFloatEnvelopeV1Oracle.imageSrgbToLinear(average[it]),average[3]) }
        }
        val raw=div(numerator,scale)
        val t=when(tile) {
            TileMode.CLAMP,TileMode.DECAL -> clamp(raw)
            TileMode.REPEAT -> sub(raw,WgslFloatEnvelopeV1Oracle.gradientFloor(raw))
            TileMode.MIRROR -> {
                val q=sub(raw,mul(Interval.input(2f),WgslFloatEnvelopeV1Oracle.gradientFloor(mul(raw,Interval.input(.5f)))))
                sub(Interval.ONE,abs(sub(q,Interval.ONE)))
            }
        }
        val alternatives=mutableListOf<Array<Interval>>()
        if(tile == TileMode.DECAL && (raw.lower.signum() < 0 || raw.upper > BigDecimal.ONE))
            alternatives += Array(4) { Interval.ZERO }
        if(tile == TileMode.DECAL && (raw.upper.signum() < 0 || raw.lower > BigDecimal.ONE)) return alternatives.single()
        val searched=if(tile == TileMode.CLAMP) numerator else t
        val searchScale=if(tile == TileMode.CLAMP) scale else Interval.ONE
        if(searched.lower.signum() < 0) alternatives += gradientSource(domain,stops.first().color,stops.first().color,Interval.ZERO)
        // Enumerate every possible upper-bound choice with independent rounded
        // scaled comparisons; equal positions skip to the last stop in the run.
        for(index in 1 until stops.size) {
            val left=stops[index-1]; val right=stops[index]
            if(left.position == right.position) continue
            val lo=mul(Interval.input(left.position),searchScale)
            val hi=mul(Interval.input(right.position),searchScale)
            if(searched.upper < lo.lower || searched.lower >= hi.upper) continue
            val ratio=div(sub(t,Interval.input(left.position)),sub(Interval.input(right.position),Interval.input(left.position)))
            alternatives += gradientSource(domain,left.color,right.color,ratio)
        }
        val last=mul(Interval.input(stops.last().position),searchScale)
        if(searched.upper >= last.lower) alternatives += gradientSource(domain,stops.last().color,stops.last().color,Interval.ONE)
        require(alternatives.isNotEmpty()) { "No bounded gradient segment" }
        return Array(4) { channel -> hull(*alternatives.map { it[channel] }.toTypedArray()) }
    }

    private fun sampledImage(image: org.graphiks.kanvas.image.Image,sampling: org.graphiks.kanvas.paint.SamplingOptions,
        pointF32: org.graphiks.math.geometry.Point2F32,tileX: org.graphiks.kanvas.paint.TileMode,
        tileY: org.graphiks.kanvas.paint.TileMode): Array<Interval> =
        sampledImage(image,sampling,Interval.input(pointF32.x),Interval.input(pointF32.y),tileX,tileY)

    private fun sampledImage(image: org.graphiks.kanvas.image.Image,sampling: org.graphiks.kanvas.paint.SamplingOptions,
        sourceX: Interval,sourceY: Interval,tileX: org.graphiks.kanvas.paint.TileMode,
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
        fun bases(value: Interval): IntRange {
            val first = value.lower.setScale(0,RoundingMode.FLOOR).intValueExact()
            val last = value.upper.setScale(0,RoundingMode.FLOOR).intValueExact()
            require(last.toLong()-first.toLong() <= 1L)
            return first..last
        }
        if (sampling == org.graphiks.kanvas.paint.SamplingOptions.NEAREST) {
            val alternatives=bases(sourceY).flatMap { y -> bases(sourceX).map { x -> texel(x,y) } }
            return Array(4) { channel -> hull(*alternatives.map { it[channel] }.toTypedArray()) }
        }
        val x = sub(sourceX,Interval.input(.5f))
        val y = sub(sourceY,Interval.input(.5f))
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
        destination: ColorARGB = ColorARGB.Transparent, finalBlend: BlendMode = BlendMode.SRC,
        devicePointF32: Point2F32 = Point2F32(.5f,.5f),
        canvasMatrixF32: Matrix3x3F32 = Matrix3x3F32(),
        destinationBlend: BlendMode = BlendMode.SRC_OVER): WgslFloatEnvelopeV1Oracle.DrawResult = try {
        capturedShaderTree(shader,paintAlphaF32,external,devicePointF32,canvasMatrixF32,destinationBlend)(destination,finalBlend)
    } catch(failure: IllegalArgumentException) {
        WgslFloatEnvelopeV1Oracle.DrawResult.FixtureUnbounded(failure.message ?: "Unbounded independent source fixture")
    }

    /** Reuse one immutable public-value source envelope while selecting a bounded destination. */
    fun capturedShaderTree(shader: Shader, paintAlphaF32: Float, external: ColorFilter?,
        devicePointF32: Point2F32 = Point2F32(.5f,.5f), canvasMatrixF32: Matrix3x3F32 = Matrix3x3F32(),
        destinationBlend: BlendMode = BlendMode.SRC_OVER): (ColorARGB,BlendMode) -> WgslFloatEnvelopeV1Oracle.DrawResult {
        val (x,y)=mapSegment(Interval.input(devicePointF32.x),Interval.input(devicePointF32.y),listOf(matrixValues(canvasMatrixF32)))
        var value = shaderSource(shader,x,y,null,emptyList())
            .map { mul(it,Interval.input(paintAlphaF32)) }.toTypedArray()
        if (external != null) value = applyFilter(value,external)
        return { destination, finalBlend -> finish(value,destination,finalBlend,1f,destinationBlend) }
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
