@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class W5gComposedMaterialSurfacePixelTest {
    @Test fun invalidImageChildrenKeepPublicDiagnosticsAndRecovery() {
        val image=Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1))
        val valid=Shader.Image(image)
        val other=Shader.Image(Image.fromPixels(1,1,byteArrayOf(0,0,-1,-128)))
        val healthyShader=Shader.Blend(BlendMode.SRC_OVER,valid,other)
        val expected=W5fColorCpuOracle.expectedShaderTree(healthyShader)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(valid))
        for(lane in 0..2) {
            fun record(surface: Surface,shader: Shader)=surface.canvas {
                val paint=Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
                if(lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            }
            val healthy=Surface(1,1).also { record(it,healthyShader) }
            for(sampling in listOf(SamplingOptions.Cubic(Float.NaN,.5f),SamplingOptions.Cubic(-.01f,.5f),
                SamplingOptions.Cubic(.5f,Float.POSITIVE_INFINITY),SamplingOptions.Cubic(.5f,1.01f))) {
                val invalid=Shader.Image(image,sampling=sampling)
                for(shader in listOf(Shader.Blend(BlendMode.SRC_OVER,invalid,other),Shader.Blend(BlendMode.SRC_OVER,valid,invalid))) {
                    val refused=Surface(1,1).also { record(it,shader) }
                    repeat(2) {
                        val failure=assertFailsWith<IllegalStateException> { refused.render() }
                        assertEquals("invalid.material.image.cubic-parameters",failure.message.orEmpty().substringBefore(':'),failure.message)
                        W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
                    }
                }
            }
            val same=Surface(1,1)
            val failure=assertFailsWith<IllegalArgumentException> { record(same,Shader.Blend(BlendMode.SRC_OVER,valid,
                Shader.WithColorFilter(other,ColorFilter.Table(UByteArray(255))))) }
            assertTrue(failure.message.orEmpty().contains("invalid.material.filter.table"))
            record(same,healthyShader)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(same.render(),listOf(expected)) }
        }
        for(alpha in listOf(-.25f,1.25f,Float.NaN,Float.POSITIVE_INFINITY)) {
            val failure=assertFailsWith<IllegalArgumentException> { Shader.Opacity(valid,alpha) }
            assertEquals("Shader opacity alpha must be finite and within 0..1",failure.message)
        }
        render(healthyShader,expected)
    }

    @Test fun aggregateImageReservationsRefuseWithoutDroppingSiblingAndRecover() {
        fun image(blue: Boolean,size: Int=512)=Shader.Image(Image.fromPixels(size,size,ByteArray(size*size*4) {
            when(it%4) { 0 -> if(blue) 0 else -1; 2 -> if(blue) -1 else 0; 3 -> -1; else -> 0 }
        }))
        val red=image(false); val blue=image(true)
        val single=Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Transparent),red)
        val pair=Shader.Blend(BlendMode.SRC_OVER,red,Shader.Opacity(blue,.5f))
        val small=Shader.Blend(BlendMode.SRC_OVER,image(false,1),Shader.Opacity(image(true,1),.5f))
        val expected=W5fColorCpuOracle.expectedShaderTree(pair)
        val singleExpected=W5fColorCpuOracle.expectedShaderTree(single)
        disjoint(expected,singleExpected)
        val smallExpected=W5fColorCpuOracle.expectedShaderTree(small)
        W5fSurfacePixelFixtures.requireBounded(smallExpected)
        // Each512x512 image reserves1048576 texture +1048576 aligned staging bytes.
        // The fixed public3MB budget leaves room for one plus geometry, but not two.
        fun frame(shader: Shader)=Surface(1,1,config=RenderConfig(frameLocalBudgetBytes=3_000_000L)).also {
            it.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)) }
        }
        val healthy=frame(small)
        repeat(2) {
            W5fSurfacePixelFixtures.assertNativePixels(frame(single).render(),listOf(singleExpected))
            val failure=assertFailsWith<IllegalStateException> { frame(pair).render() }
            assertEquals("resource-limit.w5g.composed-binding",failure.message.orEmpty().substringBefore(':'),failure.message)
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(smallExpected))
        }
    }

    @Test fun imageCanvasCoordinatesAreConsumedOnceAndInvalidBranchStaysTransparent() {
        val leaf=Shader.Image(Image.fromPixels(4,1,byteArrayOf(-1,0,0,-1,0,-1,0,-1,0,0,-1,-1,-1,-1,-1,-1)))
        val sampled=Shader.WithLocalMatrix(leaf,Matrix3x3F32(sx=.125f,tx=.03125f))
        val dst=Shader.SolidColor(ColorARGB.Blue.withAlpha(128))
        val shader=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.Opacity(sampled,.5f))
        val ctm=Matrix3x3F32(sx=2f,sy=2f)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader,canvasMatrixF32=ctm)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(shader))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(shader,canvasMatrixF32=Matrix3x3F32(sx=4f,sy=4f)))
        render(shader,expected,canvasMatrix=ctm,label="image CTM once")

        // The invertible x/w swap has inverse w=x. Outer clamp fixes x=0,
        // so every emitted product/add in its denominator is exactly zero.
        // The following inner clamp may reset the point but cannot restore validity.
        val point=RectF32.ofLTRB(.5f,.5f,.5f,.5f)
        val invalid=Shader.CoordClamp(Shader.WithLocalMatrix(Shader.CoordClamp(leaf,point),
            Matrix3x3F32(sx=0f,tx=1f,persp0=1f,persp2=0f)),RectF32.ofLTRB(0f,.5f,0f,.5f))
        val restoring=ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(.125f,0f,0f,.25f) })
        val guarded=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.WithColorFilter(invalid,restoring))
        val transparent=Shader.Blend(BlendMode.SRC_OVER,dst,
            Shader.WithColorFilter(Shader.SolidColor(ColorARGB.Transparent),restoring))
        val guardedExpected=W5fColorCpuOracle.expectedShaderTree(transparent)
        disjoint(guardedExpected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,dst,
            Shader.WithColorFilter(leaf,restoring))))
        val noFilter=Shader.Blend(BlendMode.SRC_OVER,dst,invalid)
        val noFilterExpected=W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,dst,
            Shader.SolidColor(ColorARGB.Transparent)))
        disjoint(noFilterExpected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,dst,leaf)))
        render(noFilter,noFilterExpected,label="same cumulative invalid projective image without filter")
        render(guarded,guardedExpected,label="cumulative invalid projective image before restoring wrapper")
    }

    @Test fun imageWorkingWrappersDoNotRetargetDecodedPixelsOrSiblingGradients() {
        val image=Shader.Image(Image.fromPixels(2,1,byteArrayOf(0,0,-1,-128,-1,0,0,-1)))
        val plain=Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red.withAlpha(128)),image)
        val plainExpected=W5fColorCpuOracle.expectedShaderTree(plain)
        W5fSurfacePixelFixtures.requireBounded(plainExpected)
        for(domain in ColorSpaceInterpolation.entries) {
            val wrapped=Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red.withAlpha(128)),
                Shader.WithWorkingColorSpace(image,domain))
            val wrappedExpected=W5fColorCpuOracle.expectedShaderTree(wrapped)
            W5fSurfacePixelFixtures.requireBounded(wrappedExpected)
            assertEquals((plainExpected as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels,
                (wrappedExpected as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels)
            render(wrapped,plainExpected,label="ignored image working wrapper $domain")
            val achromatic=domain in setOf(ColorSpaceInterpolation.LINEAR,ColorSpaceInterpolation.OKLAB,ColorSpaceInterpolation.SRGB)
            val stops=listOf(GradientStop(0f,if(achromatic) ColorARGB.Black.withAlpha(128) else ColorARGB.of(128,160,96,96)),
                GradientStop(1f,if(achromatic) ColorARGB.White.withAlpha(128) else ColorARGB.of(128,96,160,96)))
            val other=if(domain == ColorSpaceInterpolation.SRGB) ColorSpaceInterpolation.OKLCH else ColorSpaceInterpolation.SRGB
            val leaf=gradient(0,stops,other)
            val dst=Shader.WithWorkingColorSpace(Shader.WithWorkingColorSpace(leaf,other),domain)
            val src=Shader.Opacity(Shader.WithWorkingColorSpace(image,other),.25f)
            val mixed=Shader.Blend(BlendMode.SRC_OVER,dst,src)
            val wrong=Shader.Blend(BlendMode.SRC_OVER,Shader.WithWorkingColorSpace(dst,other),src)
            val witness=requireNotNull(discriminatingProjection(mixed,listOf(wrong))) { "mixed domain $domain" }
            render(mixed,witness.second,witness.first,127,BlendMode.SRC_OVER,ColorARGB.Blue,label="mixed image/gradient $domain")
        }
    }

    @ParameterizedTest(name="mixed image restoration, paint and external once before final {0}")
    @EnumSource(value=BlendMode::class,names=["SRC_OVER","SRC_IN","DIFFERENCE"])
    fun imageRestorationAndExternalFilterKeepFinalOrder(mode: BlendMode) {
        val image=Shader.Image(Image.fromPixels(2,1,byteArrayOf(-1,0,0,-1,0,0,-1,-128)))
        val restoring=ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(.125f,0f,0f,.25f) })
        val dst=Shader.Opacity(Shader.SolidColor(ColorARGB.Blue),.5f)
        for(alpha in listOf(0f,.5f)) {
            val child=Shader.WithColorFilter(Shader.Opacity(image,alpha),restoring)
            val shader=Shader.Blend(BlendMode.SRC_OVER,dst,child)
            val reordered=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.Opacity(Shader.WithColorFilter(image,restoring),alpha))
            // All five order boundaries have native witnesses in SRC_IN and DIFFERENCE.
            // SRC_OVER also covers restoration/external-once with useful bounded witnesses;
            // its fixed-function tail need not discriminate every redundant counterfactual.
            val boundaries=if(mode == BlendMode.SRC_OVER) 0..1 else 0..4
            for(boundary in boundaries) {
                var witness: Pair<ColorFilter,WgslFloatEnvelopeV1Oracle.DrawResult>?=null
                val projectionAlphas=if(mode == BlendMode.SRC_OVER) listOf(1f,.25f,.5f,.75f)
                    else listOf(if(mode == BlendMode.DIFFERENCE) .5f else 1f)
                search@ for(projectionAlpha in projectionAlphas) for(scale in listOf(.0625f,.125f,.25f,.5f,1f)) for(channel in 0..3)
                    for(bias in listOf(.125f,.25f,.375f,.5f,.625f,.75f)) {
                    val values=floatArrayOf(0f,0f,0f,0f,.125f,0f,0f,0f,0f,bias,
                        0f,0f,0f,0f,0f,0f,0f,0f,0f,projectionAlpha)
                    if(mode == BlendMode.SRC_OVER) { values[4]=0f; values[14]=1f }
                    values[5+channel]=scale
                    val projection=ColorFilter.Matrix(ColorMatrixF32.of(values))
                    fun expected(tree: Shader,external: ColorFilter?=projection,paint: Float=127f/255f,
                        final: BlendMode=mode)=W5fColorCpuOracle.expectedShaderTree(tree,paint,external,ColorARGB.Blue,final)
                    val wanted=expected(shader) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded ?: continue
                    val other=when(boundary) {
                        0 -> expected(reordered)
                        1 -> expected(shader,ColorFilter.Compose(projection,projection))
                        2 -> expected(Shader.Blend(BlendMode.SRC_OVER,Shader.WithColorFilter(dst,projection),
                            Shader.WithColorFilter(child,projection)),external=null)
                        3 -> expected(Shader.Blend(BlendMode.SRC_OVER,Shader.Opacity(dst,127f/255f),
                            Shader.Opacity(child,127f/255f)),paint=1f)
                        else -> expected(shader,final=if(mode == BlendMode.DIFFERENCE) BlendMode.SRC else BlendMode.DIFFERENCE)
                    }
                    if(other is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded && wanted.channels.indices.any {
                        wanted.channels[it].intersect(other.channels[it]).isEmpty() }) {
                        disjoint(wanted,other); witness=projection to wanted; break@search
                    }
                }
                val (projection,expected)=requireNotNull(witness) { "No image order witness $mode/$alpha/$boundary" }
                render(shader,expected,projection,127,mode,ColorARGB.Blue,label="image restoration alpha=$alpha boundary=$boundary")
            }
        }
    }

    @Test fun cubicFixedAlphaMixedControl() {
        val image=Image.fromPixels(2,1,byteArrayOf(-1,0,0,-1,0,0,-1,-1))
        val gradient=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val dst=Shader.CoordClamp(gradient,RectF32.ofLTRB(.25f,.5f,.25f,.5f))
        for(tileX in listOf(TileMode.CLAMP,TileMode.REPEAT,TileMode.MIRROR))
            for(tileY in listOf(TileMode.CLAMP,TileMode.REPEAT,TileMode.MIRROR)) {
            val sampled=Shader.WithLocalMatrix(Shader.Image(image,tileX,tileY,SamplingOptions.Cubic.CatmullRom),
                Matrix3x3F32(tx=.75f,ty=.75f))
            val shader=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.Opacity(sampled,.5f))
            val wrong=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.SolidColor(ColorARGB.Green))
            val witness=requireNotNull(discriminatingProjection(shader,listOf(wrong)))
            render(shader,witness.second,witness.first,127,BlendMode.SRC_OVER,ColorARGB.Blue,label="cubic fixed-alpha $tileX/$tileY")
        }
    }

    @Test fun cubicDecalManualTapsRemainUsefulInsideTheirBoundedDomain() {
        val image=Image.fromPixels(4,4,ByteArray(64) { byte ->
            val pixel=byte/4
            when(byte%4) { 0 -> if((pixel%4+pixel/4)%2 == 0) -1 else 0
                2 -> if((pixel%4+pixel/4)%2 == 1) -1 else 0; 3 -> -1; else -> 0 }
        })
        val wrongImage=Image.fromPixels(4,4,ByteArray(64) { if(it%4 == 1 || it%4 == 3) -1 else 0 })
        for(tileX in TileMode.entries) for(tileY in TileMode.entries) {
            if(tileX != TileMode.DECAL && tileY != TileMode.DECAL) continue
            fun tree(sampling: SamplingOptions,resource: Image=image,point: Float=2f)=Shader.Blend(BlendMode.SRC_OVER,
                Shader.SolidColor(ColorARGB.Green.withAlpha(128)),Shader.Opacity(Shader.CoordClamp(
                    Shader.Image(resource,tileX,tileY,sampling),RectF32.ofLTRB(point,point,point,point)),.5f))
            val shader=tree(SamplingOptions.Cubic.CatmullRom)
            val expected=W5fColorCpuOracle.expectedShaderTree(shader)
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(SamplingOptions.NEAREST)))
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(SamplingOptions.Cubic.CatmullRom,wrongImage)))
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(SamplingOptions.Cubic.CatmullRom,point=1.5f)))
            render(shader,expected,label="bounded cubic $tileX/$tileY sixteen taps")
        }
    }

    @Test fun varyingAlphaCubicMixedRefusalPreservesValidImageRecovery() {
        val image=Image.fromPixels(2,1,byteArrayOf(-1,0,0,-1,0,0,-1,-128))
        val gradient=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val dst=Shader.CoordClamp(gradient,RectF32.ofLTRB(.25f,.5f,.25f,.5f))
        fun tree(sampling: SamplingOptions)=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.Opacity(
            Shader.WithLocalMatrix(Shader.Image(image,TileMode.CLAMP,TileMode.CLAMP,sampling),Matrix3x3F32(tx=.75f,ty=.75f)),.5f))
        val original=tree(SamplingOptions.Cubic.CatmullRom)
        val valid=tree(SamplingOptions.NEAREST)
        val wrong=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.SolidColor(ColorARGB.Green))
        val refusedWitness=requireNotNull(discriminatingProjection(original,listOf(wrong)))
        val healthyWitness=requireNotNull(discriminatingProjection(valid,listOf(wrong)))
        // The original independently bounded expected pixels remain unchanged;
        // the production common proof conservatively refuses this useful graph.
        W5fSurfacePixelFixtures.requireBounded(refusedWitness.second)
        for(lane in 0..2) {
            fun frame(shader: Shader,external: ColorFilter)=Surface(1,1).also { surface -> surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=ColorARGB.Blue,blendMode=BlendMode.SRC,antiAlias=false))
                val paint=Paint(shader=shader,color=ColorARGB.of(127,255,255,255),colorFilter=external,blendMode=BlendMode.SRC_OVER,antiAlias=false)
                if(lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            } }
            val refused=frame(original,refusedWitness.first)
            val healthy=frame(valid,healthyWitness.first)
            repeat(2) {
                val failure=assertFailsWith<IllegalStateException> { refused.render() }
                assertEquals("unsupported.material.composed.numeric-domain-unbounded",failure.message.orEmpty().substringBefore(':'),failure.message)
                W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(healthyWitness.second))
            }
        }
    }

    @ParameterizedTest(name = "mixed image sampler {0} keeps both-axis tap addressing")
    @ValueSource(ints = [0,1])
    fun imageSamplerTilesAndMixedGradientKeepBranchContexts(kind: Int) {
        val image=Image.fromPixels(2,1,byteArrayOf(-1,0,0,-1,0,0,-1,-128))
        val sampling=when(kind) { 0 -> SamplingOptions.NEAREST; 1 -> SamplingOptions.LINEAR; else -> SamplingOptions.Cubic.CatmullRom }
        val gradient=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val dst=Shader.CoordClamp(gradient,RectF32.ofLTRB(.25f,.5f,.25f,.5f))
        for(tileX in TileMode.entries) for(tileY in TileMode.entries) {
            val sampled=Shader.WithLocalMatrix(Shader.Image(image,tileX,tileY,sampling),Matrix3x3F32(tx=.75f,ty=.75f))
            val shader=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.Opacity(sampled,.5f))
            val wrong=Shader.Blend(BlendMode.SRC_OVER,dst,Shader.SolidColor(ColorARGB.Green))
            val witness=requireNotNull(discriminatingProjection(shader,listOf(wrong))) {
                "No bounded image/tile witness $kind/$tileX/$tileY"
            }
            render(shader,witness.second,witness.first,127,BlendMode.SRC_OVER,ColorARGB.Blue,label="$kind/$tileX/$tileY")
        }
    }

    @Test fun sharedImageKeepsClampMatrixOrderAndMixedFrameStorage() {
        val shared=Shader.Image(Image.fromPixels(2,1,byteArrayOf(-1,0,0,-1,0,0,-1,-128)))
        val low=Shader.CoordClamp(shared,RectF32.ofLTRB(.5f,.5f,.5f,.5f))
        val high=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=-1f))
        val blend=Shader.Blend(BlendMode.SRC_OVER,low,high)
        val expected=W5fColorCpuOracle.expectedShaderTree(blend)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,high,low)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,low,low)))
        render(blend,expected)
        val gradient=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val mixed=Shader.Blend(BlendMode.SRC_OVER,gradient,Shader.Opacity(high,.5f))
        val expectedMixed=W5fColorCpuOracle.expectedShaderTree(mixed)
        W5fSurfacePixelFixtures.requireBounded(expectedMixed)
        val ordinary=W5fColorCpuOracle.expectedShaderTree(gradient)
        W5fSurfacePixelFixtures.requireBounded(ordinary)
        val surface=Surface(3,1)
        val trees=listOf(blend,mixed,gradient)
        surface.canvas {
            for(lane in 0..2) {
                save(); clipRect(RectF32.ofLTRB(lane.toFloat(),0f,lane+1f,1f),antiAlias=false); translate(lane.toFloat(),0f)
                val paint=Paint(shader=trees[lane],blendMode=BlendMode.SRC,antiAlias=false)
                if(lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
                restore()
            }
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected,expectedMixed,ordinary)) }
    }

    @Test fun twoNearestImageChildrenKeepTheirOwnPixelsAndOrder() {
        val original=byteArrayOf(-1,0,0,-1,0,0,-1,-128)
        val reversed=byteArrayOf(0,0,-1,-128,-1,0,0,-1)
        val dst=Shader.Image(Image.fromPixels(2,1,original))
        val src=Shader.Image(Image.fromPixels(2,1,reversed))
        val shader=Shader.Blend(BlendMode.SRC_OVER,dst,src)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader)
        // These independent alternatives catch swapped texture slots, an aliased
        // child resource, and loss of a branch-local sample before capture.
        for(wrong in listOf(Shader.Blend(BlendMode.SRC_OVER,src,dst),
            Shader.Blend(BlendMode.SRC_OVER,dst,dst),Shader.Blend(BlendMode.SRC_OVER,src,src),
            Shader.Blend(BlendMode.SRC_OVER,Shader.WithLocalMatrix(dst,Matrix3x3F32(tx=-1f)),src))) {
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(wrong))
        }
        render(shader,expected)
    }

    @Test fun mixedOrdinaryAndComposedGradientLanesKeepTheirFinalFrameRanges() {
        val shared=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val low=Shader.CoordClamp(shared,RectF32.ofLTRB(.25f,.5f,.25f,.5f))
        val high=Shader.CoordClamp(shared,RectF32.ofLTRB(.75f,.5f,.75f,.5f))
        val composed=Shader.Blend(BlendMode.SRC_OVER,low,high)
        val linear=Shader.WithWorkingColorSpace(composed,ColorSpaceInterpolation.LINEAR)
        val expected=listOf(low,composed,linear).map { W5fColorCpuOracle.expectedShaderTree(it) }
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        disjoint(expected[1],expected[2])
        val surface=Surface(3,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=low,blendMode=BlendMode.SRC,antiAlias=false))
            for(lane in 1..2) {
                save(); clipRect(RectF32.ofLTRB(lane.toFloat(),0f,lane+1f,1f),antiAlias=false); translate(lane.toFloat(),0f)
                drawPath(path(lane),Paint(shader=if(lane == 1) composed else linear,blendMode=BlendMode.SRC,antiAlias=false))
                restore()
            }
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected) }
    }

    @Test fun projectiveBranchUsesBoundedNormalizedDivision() {
        val stops=listOf(GradientStop(0f,ColorARGB.Red.withAlpha(128)),GradientStop(.55f,ColorARGB.Red.withAlpha(128)),
            GradientStop(.6f,ColorARGB.Blue.withAlpha(128)),GradientStop(1f,ColorARGB.Blue.withAlpha(128)))
        val shared=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),stops)
        val src=Shader.Opacity(shared,.25f)
        val shader=Shader.Blend(BlendMode.SRC_OVER,Shader.WithLocalMatrix(shared,Matrix3x3F32(persp0=.5f)),src)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,shared,src)))
        render(shader,expected)
    }

    @Test fun canvasCoordinatesAreConsumedOnceBeforeBranchLocalSegments() {
        val shared=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val shader=Shader.Blend(BlendMode.SRC_OVER,Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=.125f)),
            Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=-.25f)))
        val canvasMatrix=Matrix3x3F32(sx=2f,sy=2f)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader,canvasMatrixF32=canvasMatrix)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(shader))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(shader,canvasMatrixF32=Matrix3x3F32(sx=4f,sy=4f)))
        render(shader,expected,canvasMatrix=canvasMatrix)
    }

    @ParameterizedTest(name = "composed degeneracy and tile family {0}")
    @ValueSource(ints = [0,1,2,3])
    fun degenerateGradientChildrenKeepOriginalTileSemantics(family: Int) {
        val stops=listOf(GradientStop(0f,ColorARGB.Red.withAlpha(128)),GradientStop(1f,ColorARGB.Blue.withAlpha(128)))
        for(tile in TileMode.entries) {
            val leaf: Shader=when(family) {
                0 -> Shader.LinearGradient(Point2F32(0f,0f),Point2F32(0f,0f),stops,tile)
                1 -> Shader.RadialGradient(Point2F32(0f,0f),0f,stops,tile)
                2 -> Shader.SweepGradient(Point2F32(-.5f,.5f),90f,90f,stops,tile)
                else -> Shader.ConicalGradient(Point2F32(.5f,.5f),2f,Point2F32(.5f,.5f),2f,stops,tile)
            }
            val shader=Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Black.withAlpha(64)),
                Shader.CoordClamp(leaf,RectF32.ofLTRB(.5f,.5f,.5f,.5f)))
            val expected=W5fColorCpuOracle.expectedShaderTree(shader)
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,
                Shader.SolidColor(ColorARGB.Black.withAlpha(64)),Shader.SolidColor(ColorARGB.Green))))
            render(shader,expected,label="degenerate family=$family tile=$tile")
        }
    }

    @Test fun conicalEquationBranchesAndSingleStopChildrenRemainUseful() {
        val stops=listOf(GradientStop(0f,ColorARGB.Red.withAlpha(128)),GradientStop(1f,ColorARGB.Blue.withAlpha(128)))
        // Constant spans close independently while exercising quadratic, linear,
        // concentric and invalid-root selection, including a single stop's mask.
        val leaves=listOf(
            Shader.ConicalGradient(Point2F32(0f,.5f),1f,Point2F32(1f,.5f),1f,stops),
            Shader.ConicalGradient(Point2F32(0f,.5f),0f,Point2F32(1f,.5f),1f,stops),
            Shader.ConicalGradient(Point2F32(.5f,.5f),0f,Point2F32(.5f,.5f),1f,listOf(stops.first())),
        ) + (0..2).map { gradient(it,listOf(stops.first()),ColorSpaceInterpolation.OKLAB) }
        for((index,leaf) in leaves.withIndex()) {
            val shader=Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Black.withAlpha(64)),
                Shader.CoordClamp(leaf,RectF32.ofLTRB(.5f,.5f,.5f,.5f)))
            val expected=W5fColorCpuOracle.expectedShaderTree(shader)
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Green)))
            render(shader,expected,label="conical/single-stop cell=$index")
        }
    }

    private fun gradient(family: Int,stops: List<GradientStop>,domain: ColorSpaceInterpolation): Shader = when(family) {
        0 -> Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),stops,interpolation=domain)
        1 -> Shader.RadialGradient(Point2F32(-.5f,.5f),2f,stops,interpolation=domain)
        2 -> Shader.SweepGradient(Point2F32(-.5f,-.5f),-135f,225f,stops,interpolation=domain)
        else -> Shader.ConicalGradient(Point2F32(-.5f,.5f),0f,Point2F32(-.5f,.5f),2f,stops,interpolation=domain)
    }

    @ParameterizedTest(name = "contextual gradient family {0} all working domains")
    @ValueSource(ints = [0,1,2,3])
    fun everyGradientFamilyKeepsBranchWorkingPrecedence(family: Int) {
        for(domain in ColorSpaceInterpolation.entries) {
            val achromatic=domain in setOf(ColorSpaceInterpolation.LINEAR,ColorSpaceInterpolation.OKLAB) ||
                (family in setOf(0,2) && domain == ColorSpaceInterpolation.SRGB) ||
                (family == 2 && domain == ColorSpaceInterpolation.OKLCH)
            val stops=listOf(GradientStop(0f,if(achromatic) ColorARGB.Black.withAlpha(128) else ColorARGB.of(128,160,96,96)),
                GradientStop(1f,if(achromatic) ColorARGB.White.withAlpha(128) else ColorARGB.of(128,96,160,96)))
            val other=if(domain == ColorSpaceInterpolation.SRGB) ColorSpaceInterpolation.OKLCH else ColorSpaceInterpolation.SRGB
            val shared=gradient(family,stops,other)
            val dst=Shader.WithWorkingColorSpace(Shader.WithWorkingColorSpace(shared,other),domain)
            // Avoid coincident straight colors (linear .5 versus sRGB .75)
            // and give Sweep a cardinal, independently exact source direction.
            val sourceContext=if(family == 2) Shader.CoordClamp(shared,RectF32.ofLTRB(-1.5f,-.5f,-1.5f,-.5f))
                else Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=if(family == 0) .25f else -.25f))
            val src=Shader.Opacity(Shader.WithWorkingColorSpace(sourceContext,other),.25f)
            val shader=Shader.Blend(BlendMode.SRC_OVER,dst,src)
            val wrongDomain=Shader.Blend(BlendMode.SRC_OVER,Shader.WithWorkingColorSpace(dst,other),src)
            val reversed=Shader.Blend(BlendMode.SRC_OVER,src,dst)
            for(wrong in listOf(wrongDomain,reversed)) {
                val direct=W5fColorCpuOracle.expectedShaderTree(shader)
                val alternate=W5fColorCpuOracle.expectedShaderTree(wrong)
                if(direct is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded && alternate is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                    direct.channels.indices.any { direct.channels[it].intersect(alternate.channels[it]).isEmpty() }) {
                    disjoint(direct,alternate)
                    render(shader,direct)
                    continue
                }
                val (projection,expected)=requireNotNull(discriminatingProjection(shader,listOf(wrong))) {
                    "No independently bounded domain/order witness family=$family domain=$domain"
                }
                render(shader,expected,projection,127,BlendMode.SRC_OVER,ColorARGB.Blue)
            }
        }
    }

    @Test fun seventeenStopChildPreservesHardStopAndSharedContexts() {
        val stops=List(17) { index -> GradientStop(if(index == 8 || index == 9) .5f else index/16f,
            if(index <= 8) ColorARGB.Red.withAlpha(128) else ColorARGB.Blue.withAlpha(128)) }
        val shared=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),stops)
        val dst=Shader.CoordClamp(shared,RectF32.ofLTRB(.25f,.5f,.25f,.5f))
        val src=Shader.CoordClamp(shared,RectF32.ofLTRB(.5f,.5f,.5f,.5f))
        val shader=Shader.Blend(BlendMode.SRC_OVER,dst,src)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,src,dst)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,dst,dst)))
        render(shader,expected)
    }

    @Test fun sharedGradientKeepsBranchLocalSamplesAndOrder() {
        val shared = Shader.LinearGradient(Point2F32(.5f,0f),Point2F32(1.5f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val dst = Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=.25f))
        val src = Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=-.25f))
        fun tree(d: Shader,s: Shader) = Shader.WithLocalMatrix(Shader.Blend(BlendMode.SRC_OVER,d,s),Matrix3x3F32(tx=-.5f))
        val shader=tree(dst,src)
        val expected=W5fColorCpuOracle.expectedShaderTree(shader)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(src,dst)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(dst,dst)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(tree(src,src)))
        render(shader,expected)
    }

    @Test fun gradientLocalSegmentsAndClampKeepTheirOriginalOrder() {
        val shared=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val dst=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=.25f))
        val src=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=-.25f))
        val blend=Shader.Blend(BlendMode.SRC_OVER,dst,src)
        val rotation=Matrix3x3F32(sx=0f,kx=-1f,ky=1f,sy=0f)
        val translation=Matrix3x3F32(tx=-.5f)
        val ordered=Shader.WithLocalMatrix(Shader.WithLocalMatrix(blend,translation),rotation)
        val reversed=Shader.WithLocalMatrix(Shader.WithLocalMatrix(blend,rotation),translation)
        val expected=W5fColorCpuOracle.expectedShaderTree(ordered)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(reversed))
        render(ordered,expected)
        val subset=RectF32.ofLTRB(.25f,.5f,.25f,.5f)
        val clampFirst=Shader.CoordClamp(Shader.WithLocalMatrix(shared,translation),subset)
        val transformFirst=Shader.WithLocalMatrix(Shader.CoordClamp(shared,subset),translation)
        val shader=Shader.Blend(BlendMode.SRC_OVER,clampFirst,Shader.Opacity(src,.5f))
        val wanted=W5fColorCpuOracle.expectedShaderTree(shader)
        disjoint(wanted,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,transformFirst,Shader.Opacity(src,.5f))))
        render(shader,wanted)
    }

    @Test fun gradientWrappersPaintAndExternalFilterPrecedeFinalBlendOnce() {
        val shared=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Black.withAlpha(128)),GradientStop(1f,ColorARGB.White.withAlpha(128))))
        val restoring=ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(.125f,0f,0f,.25f) })
        val dst=Shader.WithColorFilter(Shader.Opacity(Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=.25f)),.5f),restoring)
        val src=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=-.25f))
        val shader=Shader.Blend(BlendMode.SRC_OVER,dst,src)
        val eachAlpha=Shader.Blend(BlendMode.SRC_OVER,Shader.Opacity(dst,127f/255f),Shader.Opacity(src,127f/255f))
        for(mode in listOf(BlendMode.SRC_OVER,BlendMode.SRC_IN,BlendMode.DIFFERENCE)) {
            // Separate observations: do not combine a double-filter mutation and
            // per-child paint alpha into one artificial counterfactual.
            for(duplicateFilter in listOf(true,false)) {
                var witness: Pair<ColorFilter,WgslFloatEnvelopeV1Oracle.DrawResult>? = null
                search@ for(scale in listOf(.0625f,.125f,.25f)) for(channel in 0..3)
                    for(bias in listOf(.125f,.25f,.375f,.5f,.625f,.75f)) {
                        val values=floatArrayOf(0f,0f,0f,0f,.125f,0f,0f,0f,0f,bias,
                            0f,0f,0f,0f,0f,0f,0f,0f,0f,1f)
                        values[5+channel]=scale
                        val projection=ColorFilter.Matrix(ColorMatrixF32.of(values))
                        val wanted=W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,projection,ColorARGB.Blue,mode)
                            as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded ?: continue
                        val wrong=if(duplicateFilter) W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,
                            ColorFilter.Compose(projection,projection),ColorARGB.Blue,mode)
                        else W5fColorCpuOracle.expectedShaderTree(eachAlpha,external=projection,destination=ColorARGB.Blue,finalBlend=mode)
                        if(wrong is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded && wanted.channels.indices.any {
                            wanted.channels[it].intersect(wrong.channels[it]).isEmpty() }) {
                            disjoint(wanted,wrong); witness=projection to wanted; break@search
                        }
                    }
                val (projection,expected)=requireNotNull(witness) { "No bounded $mode gradient filter/alpha witness: $duplicateFilter" }
                render(shader,expected,projection,127,mode,ColorARGB.Blue)
            }
        }
    }

    @ParameterizedTest(name = "invalid gradient {0} retains its own prefix diagnostic")
    @ValueSource(ints = [0,1,2])
    fun invalidGradientLeafKeepsHistoricalDiagnostic(kind: Int) {
        val stops = listOf(GradientStop(0f,ColorARGB.Red),GradientStop(1f,ColorARGB.Blue))
        val invalid = when (kind) {
            0 -> Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),emptyList())
            1 -> Shader.RadialGradient(Point2F32(0f,0f),-1f,stops)
            else -> Shader.ConicalGradient(Point2F32(0f,0f),0f,Point2F32(1f,0f),-1f,stops)
        }
        val code = if (kind == 0) "unsupported.material.gradient.empty_stops"
            else "unsupported.material.gradient.negative_radius"
        val solid = Shader.SolidColor(ColorARGB.Red)
        // The old direct source is the control; each composed leaf is first
        // invalid in declared dst/src order, never hidden by pending admission.
        for (shader in listOf(invalid,Shader.Blend(BlendMode.SRC_OVER,invalid,solid),
            Shader.Blend(BlendMode.SRC_OVER,solid,invalid))) {
            val surface = Surface(1,1)
            surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader,antiAlias=false)) }
            val failure = assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals(code,failure.message.orEmpty().substringBefore(':'),failure.message)
        }
        // A now-promoted earlier image must preserve the later invalid leaf's diagnostic.
        val pending = Shader.Image(Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1)))
        val earlier = Surface(1,1)
        earlier.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=
            Shader.Blend(BlendMode.SRC_OVER,pending,invalid),antiAlias=false)) }
        val firstFailure = assertFailsWith<IllegalStateException> { earlier.render() }
        assertEquals(code,firstFailure.message.orEmpty().substringBefore(':'),firstFailure.message)
        val expected = W5fColorCpuOracle.expectedShaderTree(solid)
        render(Shader.Blend(BlendMode.SRC,Shader.SolidColor(ColorARGB.Blue),solid),expected)
    }

    @Test fun finalDestinationIdentityKeepsItsNoOpSemantics() {
        val shader = Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red),Shader.SolidColor(ColorARGB.Blue))
        val expected = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Blue))
        W5fSurfacePixelFixtures.requireBounded(expected)
        render(shader,expected,finalBlend=BlendMode.DST,background=ColorARGB.Blue)
    }

    @Test fun admittedGradientAndImageChildrenKeepTheirOwnedResults() {
        val gradient = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Red),GradientStop(1f,ColorARGB.Blue)))
        val image = Shader.Image(Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1)))
        val admitted=Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red),gradient)
        val expected=W5fColorCpuOracle.expectedShaderTree(admitted)
        render(admitted,expected)
        val composed=Shader.Blend(BlendMode.SRC_OVER,gradient,image)
        val imageExpected=W5fColorCpuOracle.expectedShaderTree(composed)
        render(composed,imageExpected)
    }

    @ParameterizedTest(name = "composed H refusal {0}")
    @ValueSource(ints = [0,1,2,3,4])
    fun roundedStrokePointsAndMeshAreOwnedRefusals(kind: Int) {
        val surface = Surface(1,1)
        val shader = Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red),Shader.SolidColor(ColorARGB.Blue))
        surface.canvas {
            val paint = Paint(shader=shader,antiAlias=false,strokeWidth=2f)
            when (kind) {
                0 -> drawRRect(RRectF32.of(RectF32.ofLTRB(-2f,-2f,3f,3f),CornerRadiiF32.of(.5f)),paint)
                1 -> drawPath(Path().apply { moveTo(-2f,.5f); lineTo(3f,.5f) },paint.copy(style=PaintStyle.STROKE))
                2 -> drawPoint(.5f,.5f,paint)
                3 -> drawVertices(Vertices(VertexMode.TRIANGLES,listOf(Point2F32(-10f,-10f),
                    Point2F32(20f,-10f),Point2F32(-10f,20f))),paint)
                else -> drawMesh(org.graphiks.kanvas.types.Mesh(Vertices(VertexMode.TRIANGLES,
                    listOf(Point2F32(-10f,-10f),Point2F32(20f,-10f),Point2F32(-10f,20f))),
                    bounds=RectF32.ofLTRB(-10f,-10f,20f,20f)),paint)
            }
        }
        val failure = assertFailsWith<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.composed.slice",failure.message.orEmpty().substringBefore(':'),failure.message)
    }

    // Preserve child output in green while holding the other channels away from
    // zero/one cancellation in the final destination-read interval schedule.
    private fun boundedProjection(shader: Shader,mode: BlendMode): Pair<ColorFilter,WgslFloatEnvelopeV1Oracle.DrawResult> {
        // Finite independent fixture construction, before capture: quantization
        // cell boundaries differ per mode. Every candidate retains a nonzero
        // child-dependent green coefficient; no device observation participates.
        for (bias in listOf(.125f,.25f,.375f,.5f,.625f,.75f)) {
            val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
                0f,0f,0f,0f,.125f, .0625f,.03125f,0f,0f,bias,
                0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
            val result = W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,filter,ColorARGB.Blue,mode)
            if (result is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded) {
                W5fSurfacePixelFixtures.requireBounded(result)
                return filter to result
            }
        }
        error("No independently bounded projection for $mode and $shader")
    }
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult,b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() },
            "Counterfactual must be distinct: ${a.channels}/${b.channels}")
    }

    private fun linearColor(r: Float,g: Float,b: Float) = Shader.WithColorFilter(Shader.SolidColor(ColorARGB.Black),
        ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(0f,0f,0f,0f,r,0f,0f,0f,0f,g,
            0f,0f,0f,0f,b,0f,0f,0f,0f,1f))))

    // A finite independent search for a quantization cell, never device feedback.
    // Unlike boundedProjection, this can expose alpha in green and must separate
    // EVERY supplied semantic mutation before any Surface is constructed.
    private fun discriminatingProjection(shader: Shader,wrong: List<Shader>): Pair<ColorFilter,WgslFloatEnvelopeV1Oracle.DrawResult>? {
        for (scale in listOf(.0625f,.125f,.25f)) for (channel in 0..3)
            for (bias in listOf(.125f,.25f,.375f,.5f,.625f,.75f)) {
            val values = floatArrayOf(0f,0f,0f,0f,.125f,0f,0f,0f,0f,bias,
                0f,0f,0f,0f,0f,0f,0f,0f,0f,1f)
            values[5+channel] = scale
            val projection = ColorFilter.Matrix(ColorMatrixF32.of(values))
            fun expected(tree: Shader) = W5fColorCpuOracle.expectedShaderTree(tree,127f/255f,
                projection,ColorARGB.Blue,BlendMode.SRC_OVER)
            val wanted = expected(shader) as? WgslFloatEnvelopeV1Oracle.DrawResult.Bounded ?: continue
            val alternatives = wrong.map { expected(it) }
            if (alternatives.all { other -> other is WgslFloatEnvelopeV1Oracle.DrawResult.Bounded &&
                wanted.channels.indices.any { wanted.channels[it].intersect(other.channels[it]).isEmpty() } }) {
                alternatives.forEach { disjoint(wanted,it) }
                return projection to wanted
            }
        }
        return null
    }

    @ParameterizedTest(name = "child mode {0} rejects wrong mode, lost alpha and meaningful reversed order")
    @EnumSource(BlendMode::class)
    fun eachChildModeHasADisjointSemanticWitness(mode: BlendMode) {
        val wrongMode = when (mode) {
            BlendMode.SRC_IN -> BlendMode.SRC_OUT
            BlendMode.SRC_OUT -> BlendMode.SRC_IN
            BlendMode.SRC_OVER -> BlendMode.DST_OVER
            else -> BlendMode.SRC_OVER
        }
        val commutative = setOf(BlendMode.CLEAR,BlendMode.PLUS,BlendMode.MODULATE,BlendMode.SCREEN,
            BlendMode.DARKEN,BlendMode.LIGHTEN,BlendMode.DIFFERENCE,BlendMode.EXCLUSION,BlendMode.MULTIPLY,BlendMode.XOR)
        // HUE/COLOR must actually change luminosity: near-equal luminances
        // make their difference from SRC_OVER disappear at byte quantization.
        val colors = if (mode == BlendMode.HUE || mode == BlendMode.COLOR)
            listOf(linearColor(.75f,.875f,.625f) to linearColor(.125f,.25f,.375f))
        else listOf(
            linearColor(.75f,.25f,.5f) to linearColor(.25f,.5f,.75f),
            linearColor(.625f,.375f,.875f) to linearColor(.125f,.625f,.375f))
        for ((dstColor,srcColor) in colors) {
            val dst = Shader.Opacity(dstColor,.25f)
            val src = Shader.Opacity(srcColor,.75f)
            val shader = Shader.Blend(mode,dst,src)
            // Preserve straight RGB but corrupt the output alpha. PLUS is
            // already opaque here, so use .5 rather than an equivalent 1.
            val forcedAlpha = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
                1f,0f,0f,0f,0f,0f,1f,0f,0f,0f,0f,0f,1f,0f,0f,
                0f,0f,0f,0f,if (mode == BlendMode.PLUS) .5f else 1f)))
            val wrong = listOf(Shader.Blend(wrongMode,dst,src),Shader.WithColorFilter(shader,forcedAlpha)) +
                if (mode in commutative) emptyList() else listOf(Shader.Blend(mode,src,dst))
            // Alpha and straight color can require separate output observations
            // (notably SRC_IN vs SRC_OUT, then SRC_IN vs reversed SRC_IN).
            val witnesses = wrong.map { discriminatingProjection(shader,listOf(it)) }
            if (witnesses.any { it == null }) continue
            witnesses.forEach { witness ->
                val (projection,expected) = requireNotNull(witness)
                render(shader,expected,projection,127,BlendMode.SRC_OVER,ColorARGB.Blue)
            }
            return
        }
        error("No disjoint witness for $mode")
    }

    private fun render(shader: Shader,expected: WgslFloatEnvelopeV1Oracle.DrawResult,
        external: ColorFilter? = null,paintAlpha: Int = 255,finalBlend: BlendMode = BlendMode.SRC,
        background: ColorARGB = ColorARGB.Transparent,canvasMatrix: Matrix3x3F32 = Matrix3x3F32(),label: String = "") {
        W5fSurfacePixelFixtures.requireBounded(expected)
        for (lane in 0..2) {
            val surface = Surface(1,1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=background,blendMode=BlendMode.SRC,antiAlias=false))
                concat(canvasMatrix)
                val paint = Paint(color=ColorARGB.of(paintAlpha,255,255,255),shader=shader,
                    colorFilter=external,blendMode=finalBlend,antiAlias=false)
                if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            }
            repeat(2) { try { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
                catch(failure: Exception) { throw AssertionError("Public render lane=$lane final=$finalBlend $label",failure) } }
        }
    }

    @Test fun sharedDiamondKeepsIndependentOrderedBranches() {
        val shared = Shader.Opacity(Shader.SolidColor(ColorARGB.Red),.5f)
        val filtered = Shader.WithColorFilter(shared,ColorFilter.Matrix(
            ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) }))
        val shader = Shader.Blend(BlendMode.SRC_OVER,filtered,shared)
        val expected = W5fColorCpuOracle.expectedShaderTree(shader)
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,shared,filtered)))
        disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,filtered,filtered)))
        render(shader,expected)
    }

    @Test fun restoringFilterAndPaintAlphaStayOutsideOrderedChildren() {
        val restoring = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(.125f,0f,0f,.25f) })
        val leaf = Shader.SolidColor(ColorARGB.Red)
        for (alpha in listOf(0f,.5f)) {
            val dst = Shader.Opacity(Shader.SolidColor(ColorARGB.Blue),.5f)
            val child = Shader.WithColorFilter(Shader.Opacity(leaf,alpha),restoring)
            val shader = Shader.Blend(BlendMode.SRC_OVER,dst,child)
            val expected = W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,restoring)
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,dst,
                Shader.Opacity(Shader.WithColorFilter(leaf,restoring),alpha)),127f/255f,restoring))
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(shader,127f/255f,
                ColorFilter.Compose(restoring,restoring)))
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,
                Shader.Opacity(dst,127f/255f),Shader.Opacity(child,127f/255f)),external=restoring))
            disjoint(expected,W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,
                Shader.WithColorFilter(dst,restoring),Shader.WithColorFilter(child,restoring)),127f/255f))
            render(shader,expected,restoring,127)
        }
    }

    @ParameterizedTest(name = "composed filter kind {0}")
    @ValueSource(ints = [0,1,2,3,4,5,6,7,8,9,10,11])
    fun allFilterKinds(kind: Int) {
        val filter = filterRecipe(kind)
        for (alpha in listOf(0f,.25f,.5f,1f)) {
            val shader = Shader.Blend(BlendMode.SRC_OVER,
                Shader.Opacity(Shader.SolidColor(ColorARGB.Blue),.5f),
                Shader.WithColorFilter(Shader.Opacity(Shader.SolidColor(ColorARGB.Green),alpha),filter))
            for (mode in listOf(BlendMode.SRC_OVER,BlendMode.SRC_IN,BlendMode.DIFFERENCE)) {
                val (projection,expected) = boundedProjection(shader,mode)
                render(shader,expected,projection,paintAlpha=127,finalBlend=mode,background=ColorARGB.Blue)
            }
        }
    }

    @ParameterizedTest(name = "filter kind {0} rejects omission and wrong placement")
    @ValueSource(ints = [0,1,2,3,4,5,6,7,8,9,10,11])
    fun eachFilterHasADisjointPlacementWitness(kind: Int) {
        val filter = filterRecipe(kind)
        for (alpha in listOf(.5f,.25f,.75f)) {
            val dst = Shader.Opacity(linearColor(.75f,.25f,.5f),.25f)
            val src = Shader.Opacity(linearColor(.25f,.5f,.75f),alpha)
            val shader = Shader.Blend(BlendMode.SRC_OVER,dst,Shader.WithColorFilter(src,filter))
            val omitted = Shader.Blend(BlendMode.SRC_OVER,dst,src)
            // Opaque constant Blend filtering commutes with this outer SRC_OVER;
            // putting it on the wrong child is the meaningful placement error.
            val misplaced = if (kind == 7) Shader.Blend(BlendMode.SRC_OVER,Shader.WithColorFilter(dst,filter),src)
                else Shader.WithColorFilter(omitted,filter)
            val witness = discriminatingProjection(shader,listOf(omitted,misplaced)) ?: continue
            render(shader,witness.second,witness.first,127,BlendMode.SRC_OVER,ColorARGB.Blue)
            return
        }
        error("No disjoint omission/placement witness for filter $kind")
    }

    private fun filterRecipe(kind: Int): ColorFilter {
        val matrix = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.5f,1f,1f,1f) })
        val translate = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { postTranslate(.125f,0f,0f,.25f) })
        return when (kind) {
            0 -> matrix
            1 -> ColorFilter.Compose(matrix,translate)
            2 -> ColorFilter.Lerp(.5f,matrix,translate)
            3 -> ColorFilter.Table(UByteArray(256) { (255-it).toUByte() }.apply { this[255]=64u })
            4 -> ColorFilter.Lighting(ColorARGB.of(0,128,255,0),ColorARGB.of(19,64,255,255))
            5 -> ColorFilter.SRGBToLinear
            6 -> ColorFilter.LinearToSRGB
            7 -> ColorFilter.Blend(ColorARGB.of(255,0,255,0),BlendMode.SRC_OVER)
            8 -> ColorFilter.HSLAMatrix(floatArrayOf(1f,0f,0f,0f,.25f,0f,.5f,0f,0f,0f,
                0f,0f,1f,0f,0f,0f,0f,0f,.5f,0f))
            9 -> ColorFilter.HighContrast
            10 -> ColorFilter.Luma
            else -> ColorFilter.Overdraw
        }
    }

    private fun path(lane: Int) = Path().apply {
        if (lane == 1) { moveTo(-10f,-10f); lineTo(20f,-10f); lineTo(-10f,20f); close() }
        else { moveTo(-1f,-1f); lineTo(5f,-1f); lineTo(5f,5f); lineTo(2f,2f); lineTo(-1f,5f); close() }
    }

    @ParameterizedTest(name = "ordered child {0}: alpha, paint alpha, final blend and three fill routes")
    @EnumSource(BlendMode::class)
    fun allChildModes(mode: BlendMode) {
        for (alpha in listOf(0f,.25f,.5f,1f)) {
            fun color(r: Float,g: Float,b: Float) = Shader.WithColorFilter(Shader.SolidColor(ColorARGB.Black),
                ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(0f,0f,0f,0f,r,0f,0f,0f,0f,g,
                    0f,0f,0f,0f,b,0f,0f,0f,0f,1f))))
            val shader = Shader.Blend(mode,Shader.Opacity(color(.75f,.25f,.5f),.5f),
                Shader.Opacity(color(.25f,.5f,.75f),alpha))
            for (finalBlend in listOf(BlendMode.SRC_OVER,BlendMode.SRC_IN,BlendMode.DIFFERENCE)) {
                val (projection,expected) = boundedProjection(shader,finalBlend)
                for (lane in 0..2) {
                    val surface = Surface(1,1)
                    surface.canvas {
                        drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=ColorARGB.Blue,antiAlias=false))
                        val paint = Paint(color=ColorARGB.of(127,255,255,255),shader=shader,
                            colorFilter=projection,blendMode=finalBlend,antiAlias=false)
                        if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
                        else drawPath(path(lane),paint)
                    }
                    try { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
                    catch (failure: Throwable) { throw AssertionError("mode=$mode alpha=$alpha final=$finalBlend lane=$lane",failure) }
                }
            }
        }
    }

    @Test fun orderedBlendChildrenRenderOnRectAndPath() {
        val dst = Shader.Opacity(Shader.SolidColor(ColorARGB.Blue),.5f)
        val src = Shader.Opacity(Shader.SolidColor(ColorARGB.Red),.25f)
        val shader = Shader.Blend(BlendMode.SRC_OVER,dst,src)
        val expected = W5fColorCpuOracle.expectedShaderTree(shader)
        val reversed = W5fColorCpuOracle.expectedShaderTree(Shader.Blend(BlendMode.SRC_OVER,src,dst))
        W5fSurfacePixelFixtures.requireBounded(expected)
        W5fSurfacePixelFixtures.requireBounded(reversed)
        expected as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        reversed as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        // Linear-premul correct=(.25,0,.375,.625), reversed=(.125,0,.5,.625).
        for (channel in listOf(0,2)) assertTrue(
            expected.channels[channel].intersect(reversed.channels[channel]).isEmpty(),
            "Child order must have disjoint red and blue byte sets before capture")
        for (path in listOf(false,true)) {
            val surface = Surface(1,1)
            surface.canvas {
                val paint = Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
                if (path) drawPath(Path().apply {
                    moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close()
                },paint) else drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
    }
}
