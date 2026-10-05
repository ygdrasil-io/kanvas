@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class W5gNoiseSurfacePixelTest {
    @Test fun onePhysicalNoiseSlabSurvivesRectDirectAndStencilLaneJoining() {
        val leaves=listOf(shader(false,2),shader(true,2),shader(false,2,1))
        val expected=leaves.mapIndexed { index,leaf -> W5gNoiseCpuOracle.expected(leaf,Point2F32(index+.5f,.5f))
            .also(W5fSurfacePixelFixtures::requireBounded) }
        val surface=Surface(3,1)
        surface.canvas { leaves.forEachIndexed { route,leaf ->
            save()
            clipRect(RectF32.ofLTRB(route.toFloat(),0f,route+1f,1f),antiAlias=false)
            val paint=Paint(shader=leaf,blendMode=BlendMode.SRC,antiAlias=false)
            if(route == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(fillPath(route),paint)
            restore()
        } }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected) }
    }

    @Test fun mixedMagnitudeOverflowAndUnpromotedStrokeRefuseThenRecover() {
        val valid=shader(false,2)
        val wanted=W5gNoiseCpuOracle.expected(valid,Point2F32(.5f,.5f))
        W5fSurfacePixelFixtures.requireBounded(wanted)
        val healthy=Surface(1,1).also { it.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=valid,blendMode=BlendMode.SRC,antiAlias=false))
        } }
        for(fractal in listOf(false,true)) {
            val mixed=if(fractal) Shader.FractalNoise(1e30f,1e-30f,255,7,null)
                else Shader.PerlinNoise(1e30f,1e-30f,255,7,null)
            val unbounded=Surface(1,1).also { it.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=mixed,blendMode=BlendMode.SRC,antiAlias=false))
            } }
            val stroke=Surface(1,1).also { it.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=shader(fractal,2),style=PaintStyle.STROKE,
                    blendMode=BlendMode.SRC,antiAlias=false))
            } }
            // The common composed selection boundary rejects Stroke before Noise source construction.
            for ((surface,code) in listOf(unbounded to "unsupported.material.noise.numeric-domain-unbounded",
                stroke to "unsupported.material.composed.slice")) repeat(2) {
                val failure=assertFailsWith<IllegalStateException> { surface.render() }
                assertEquals(code,failure.message.orEmpty().substringBefore(':'))
                W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(wanted))
            }
        }
    }

    @Test fun physicalSeedSlabBudgetSharesBytesButNotNoiseEvaluations() {
        val perlin=shader(false,2)
        val shared=Shader.Blend(BlendMode.SRC_OVER,perlin,shader(true,2))
        val separate=Shader.Blend(BlendMode.SRC_OVER,perlin,shader(true,2,1))
        val wanted=W5gNoiseCpuOracle.expected(shared,Point2F32(.5f,.5f))
        val changed=W5gNoiseCpuOracle.expected(separate,Point2F32(.5f,.5f))
        boundedDisjoint(wanted,changed)
        // Public 32KB budget fits one 4352-byte seed range plus this 1x1 frame,
        // not two ranges; equal seed bytes do not collapse the two evaluations.
        fun frame(tree: Shader)=Surface(1,1,config=RenderConfig(frameLocalBudgetBytes=32_000L,
            maxNoiseOctaveEvaluationsI64=16L)).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=tree,blendMode=BlendMode.SRC,antiAlias=false))
        } }
        val healthy=frame(shared)
        val refused=frame(separate)
        repeat(2) {
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(wanted))
            val failure=assertFailsWith<IllegalStateException> { refused.render() }
            assertEquals("budget.material.noise.storage",failure.message.orEmpty().substringBefore(':'))
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(wanted))
        }
    }

    @ParameterizedTest(name = "independent Noise/gradient/image resources: fill route={0}")
    @CsvSource("0", "1", "2")
    fun noiseGradientImageAndDestinationResourcesRemainDistinct(route: Int) {
        val noise=shader(false,2)
        val gradient=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(2f,0f),listOf(
            GradientStop(0f,ColorARGB.Red.withAlpha(128)),GradientStop(1f,ColorARGB.Green.withAlpha(128))))
        val image=Shader.Image(Image.fromPixels(1,1,byteArrayOf(0,0,-1,-128)))
        fun tree(n: Shader,g: Shader=gradient,i: Shader=image)=Shader.Blend(BlendMode.SRC_OVER,g,
            Shader.Blend(BlendMode.SRC_OVER,Shader.Opacity(n,.5f),Shader.Opacity(i,.25f)))
        val original=tree(noise)
        val expected=W5gNoiseCpuOracle.expected(original,Point2F32(.5f,.5f))
        for (wrong in listOf(tree(shader(false,2,1)),tree(noise,Shader.SolidColor(ColorARGB.Transparent)),
            tree(noise,i=Shader.SolidColor(ColorARGB.Transparent))))
            boundedDisjoint(expected,W5gNoiseCpuOracle.expected(wrong,Point2F32(.5f,.5f)))
        val destinationExpected=W5gNoiseCpuOracle.expected(original,Point2F32(.5f,.5f),
            destination=ColorARGB.Blue,finalBlend=BlendMode.SRC_IN)
        W5fSurfacePixelFixtures.requireBounded(destinationExpected)
        render(original,route,expected)
        val surface=Surface(1,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=ColorARGB.Blue,blendMode=BlendMode.SRC,antiAlias=false))
            val paint=Paint(shader=original,blendMode=BlendMode.SRC_IN,antiAlias=false)
            if(route == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(fillPath(route),paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(destinationExpected)) }
    }

    @ParameterizedTest(name = "requested work accounting and recovery: fill route={0}")
    @CsvSource("0", "1", "2")
    fun requestedWorkChargesTargetClippedContextsAndEveryDraw(route: Int) {
        val leaf=shader(false,255)
        val shared=Shader.Blend(BlendMode.SRC_OVER,leaf,leaf)
        val different=Shader.Blend(BlendMode.SRC_OVER,leaf,Shader.WithLocalMatrix(leaf,Matrix3x3F32(tx=-.25f)))
        val zero=shader(true,0)
        val expected=listOf(leaf,shared,different,zero).associateWith {
            W5gNoiseCpuOracle.expected(it,Point2F32(.5f,.5f)).also(W5fSurfacePixelFixtures::requireBounded)
        }
        boundedDisjoint(expected.getValue(shared),expected.getValue(different))
        fun frame(shader: Shader,limit: Long,draws: Int=1)=Surface(1,1,
            config=RenderConfig(maxNoiseOctaveEvaluationsI64=limit)).also { surface ->
            surface.canvas { repeat(draws) {
                val paint=Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)
                // All routes cover the target; no unclipped 900-pixel charge is permitted.
                if(route == 0) drawRect(RectF32.ofLTRB(-10f,-10f,20f,20f),paint) else drawPath(fillPath(route),paint)
            } }
        }
        val healthy=frame(leaf,1020)
        val accepted=listOf(frame(zero,0) to expected.getValue(zero),healthy to expected.getValue(leaf),
            frame(shared,1020) to expected.getValue(shared),frame(different,2040) to expected.getValue(different),
            frame(leaf,2040,2) to expected.getValue(leaf))
        val refused=listOf(frame(leaf,0),frame(leaf,1019),frame(shared,1019),frame(different,2039),frame(leaf,2039,2))
        repeat(2) {
            accepted.forEach { (surface,wanted) -> W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
            refused.forEach { surface ->
                val failure=assertFailsWith<IllegalStateException> { surface.render() }
                assertEquals("budget.material.noise.octave-evaluations",failure.message.orEmpty().substringBefore(':'))
                W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected.getValue(leaf)))
            }
        }
    }

    @ParameterizedTest(name = "noise capture validation and same-owner recovery: fractal={0}")
    @CsvSource("false", "true")
    fun invalidParametersRejectBeforeRecordingAndValidCaptureRecovers(fractal: Boolean) {
        fun invalid(x: Float=.125f,y: Float=.25f,octaves: Int=1,tile: SizeI32?=null): Shader = if(fractal)
            Shader.FractalNoise(x,y,octaves,7,tile) else Shader.PerlinNoise(x,y,octaves,7,tile)
        val valid=shader(fractal,1)
        val expected=W5gNoiseCpuOracle.expected(valid,Point2F32(.5f,.5f))
        W5fSurfacePixelFixtures.requireBounded(expected)
        val invalids=listOf(invalid(x=-.125f),invalid(x=Float.NaN),invalid(x=Float.POSITIVE_INFINITY),
            invalid(y=-.25f),invalid(y=Float.NaN),invalid(y=Float.POSITIVE_INFINITY),invalid(octaves=-1),invalid(octaves=256))
            .map { it to "invalid.material.noise.parameters" }+
            listOf(invalid(tile=SizeI32(-1,4)),invalid(tile=SizeI32(8,-1))).map { it to "invalid.material.noise.tile" }
        invalids.forEach { (bad,code) ->
            val surface=Surface(1,1)
            val failure=assertFailsWith<IllegalArgumentException> {
                surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=bad,blendMode=BlendMode.SRC,antiAlias=false)) }
            }
            assertTrue(failure.message.orEmpty().contains(code),failure.message)
            surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=valid,blendMode=BlendMode.SRC,antiAlias=false)) }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
    }

    @ParameterizedTest(name = "noise blend-filter-alpha order: reverse={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun compositionExternalFilterAndPaintAlphaKeepTheirOrder(reverse: Boolean,route: Int) {
        val perlin=shader(false,2); val fractal=shader(true,2)
        val dst=if(reverse) fractal else perlin; val src=if(reverse) perlin else fractal
        val blend=Shader.Blend(BlendMode.SRC_OVER,dst,src)
        val tree=Shader.Opacity(blend,.5f)
        // Observe the unchanged green noise channel; red/blue become exact endpoints.
        // Child order, paint alpha and duplicate alpha restoration remain distinct in green.
        val restoring=ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,0f, 0f,1f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,1f,.25f)))
        val cases=listOf(BlendMode.SRC_IN,BlendMode.DIFFERENCE).map { mode ->
            fun expected(shader: Shader,alpha: Float=127f/255f,filter: ColorFilter=restoring)=
                W5gNoiseCpuOracle.expected(shader,Point2F32(.5f,.5f),alpha,ColorARGB.Blue,mode,filter)
            val wanted=expected(tree)
            boundedDisjoint(wanted,expected(Shader.Opacity(Shader.Blend(BlendMode.SRC_OVER,src,dst),.5f)))
            boundedDisjoint(wanted,expected(tree,1f))
            boundedDisjoint(wanted,expected(tree,filter=ColorFilter.Compose(restoring,restoring)))
            mode to wanted
        }
        cases.forEach { (mode,wanted) ->
            val surface=Surface(1,1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=ColorARGB.Blue,blendMode=BlendMode.SRC,antiAlias=false))
                val paint=Paint(color=ColorARGB.of(127,255,255,255),shader=tree,colorFilter=restoring,blendMode=mode,antiAlias=false)
                if(route == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(fillPath(route),paint)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
        }
    }

    @ParameterizedTest(name = "second device center: fractal={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun bothDeviceCentersCoverTheEntireOctaveDomain(fractal: Boolean, route: Int) {
        val cases = listOf(0,1,2,8,255).map { octaves ->
            val leaf = shader(fractal,octaves)
            val wanted = listOf(.5f,1.5f).map { x ->
                W5gNoiseCpuOracle.expected(leaf,Point2F32(x,.5f)).also(W5fSurfacePixelFixtures::requireBounded)
            }
            if (octaves > 0) wanted.forEachIndexed { index,value ->
                boundedDisjoint(value,W5gNoiseCpuOracle.expected(shader(fractal,0),Point2F32(index+.5f,.5f)))
            }
            println("Noise two centers octaves=$octaves fractal=$fractal route=$route expected=${wanted.map(::channels)}")
            leaf to wanted
        }
        cases.forEach { (leaf,wanted) ->
            val surface=Surface(2,1)
            surface.canvas {
                val paint=Paint(shader=leaf,blendMode=BlendMode.SRC,antiAlias=false)
                if(route == 0) drawRect(RectF32.ofLTRB(0f,0f,2f,1f),paint) else drawPath(fillPath(route),paint)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),wanted) }
        }
    }

    @ParameterizedTest(name = "I32 normalized seeds: fractal={0}")
    @CsvSource("false", "true")
    fun normalizedSeedsRetainTheirIndependentTables(fractal: Boolean) {
        val seeds=listOf(Int.MIN_VALUE,-7,0,1,7,Int.MAX_VALUE,3,8,2147483646)
        val cases=seeds.associateWith { seed ->
            shader(fractal,2,seed).let { it to W5gNoiseCpuOracle.expected(it,Point2F32(.5f,.5f)) }
                .also { W5fSurfacePixelFixtures.requireBounded(it.second) }
        }
        for ((a,b) in listOf(Int.MIN_VALUE to 3,-7 to 8,0 to 1,Int.MAX_VALUE to 2147483646))
            assertEquals(channels(cases.getValue(a).second),channels(cases.getValue(b).second),"Normalized seeds $a,$b")
        boundedDisjoint(cases.getValue(1).second,cases.getValue(7).second)
        cases.forEach { (seed,pair) -> println("Noise seed=$seed fractal=$fractal expected=${channels(pair.second)}") }
        cases.values.forEach { (leaf,wanted) -> render(leaf,0,wanted) }
    }

    @ParameterizedTest(name = "stitch branch decisions: fractal={0}")
    @CsvSource("false", "true")
    fun disabledTilesAndStrictRoundedFrequencyDecisions(fractal: Boolean) {
        fun leaf(x: Float,y: Float,tile: SizeI32?): Shader = if(fractal)
            Shader.FractalNoise(x,y,2,7,tile) else Shader.PerlinNoise(x,y,2,7,tile)
        val absent=W5gNoiseCpuOracle.expected(leaf(.125f,.25f,null),Point2F32(.5f,.5f))
        W5fSurfacePixelFixtures.requireBounded(absent)
        val disabled=listOf(null,SizeI32(0,4),SizeI32(8,0),SizeI32(0,0)).map { tile ->
            leaf(.125f,.25f,tile).let { it to W5gNoiseCpuOracle.expected(it,Point2F32(.5f,.5f)) }
                .also { W5fSurfacePixelFixtures.requireBounded(it.second); assertEquals(channels(absent),channels(it.second)) }
        }
        // sqrt((1/8)*(2/8)) rounded to F32: both strict ratio operands have bits 3fb504f3.
        val tie=Float.fromBits(0x3e3504f3)
        assertEquals((tie/.125f).toRawBits(),(.25f/tie).toRawBits())
        val decisions=listOf(.03125f to .125f,.25f to .25f,tie to .25f).map { (base,adjusted) ->
            val original=leaf(base,.25f,SizeI32(8,4))
            val explicit=leaf(adjusted,.25f,SizeI32(8,4))
            val wanted=W5gNoiseCpuOracle.expected(original,Point2F32(.5f,.5f))
            val equivalent=W5gNoiseCpuOracle.expected(explicit,Point2F32(.5f,.5f))
            W5fSurfacePixelFixtures.requireBounded(wanted); W5fSurfacePixelFixtures.requireBounded(equivalent)
            assertEquals(channels(equivalent),channels(wanted))
            if(base != .25f) boundedDisjoint(wanted,W5gNoiseCpuOracle.expected(
                leaf(if(base == tie) .125f else 0f,.25f,SizeI32(8,4)),Point2F32(.5f,.5f)))
            println("Noise stitch branch baseBits=${base.toRawBits()} adjusted=$adjusted fractal=$fractal expected=${channels(wanted)}")
            original to wanted
        }
        (disabled+decisions).forEach { (leaf,wanted) -> render(leaf,0,wanted) }
    }

    @ParameterizedTest(name = "local context and negative lattice: fractal={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun contextualMatricesClampsAndNegativeLatticesStayIndependent(fractal: Boolean,route: Int) {
        val shared=shader(fractal,2)
        val left=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=.25f))
        val right=Shader.WithLocalMatrix(shared,Matrix3x3F32(tx=-.25f))
        fun blend(a: Shader,b: Shader)=Shader.Blend(BlendMode.SRC_OVER,a,b)
        val sibling=blend(left,right)
        val expected=W5gNoiseCpuOracle.expected(sibling,Point2F32(.5f,.5f))
        boundedDisjoint(expected,W5gNoiseCpuOracle.expected(blend(left,left),Point2F32(.5f,.5f)))
        boundedDisjoint(expected,W5gNoiseCpuOracle.expected(blend(right,right),Point2F32(.5f,.5f)))
        val negative=listOf(-1.5f to -.5f,-.5f to -.5f,-8.5f to -4.5f).map { (x,y) ->
            val leaf=Shader.CoordClamp(shared,RectF32.ofLTRB(x,y,x,y))
            leaf to W5gNoiseCpuOracle.expected(leaf,Point2F32(.5f,.5f)).also(W5fSurfacePixelFixtures::requireBounded)
        }
        val translation=Matrix3x3F32(tx=-.5f)
        val rotation=Matrix3x3F32(sx=0f,kx=-1f,ky=1f,sy=0f)
        val ordered=Shader.WithLocalMatrix(Shader.WithLocalMatrix(shared,translation),rotation)
        val reverse=Shader.WithLocalMatrix(Shader.WithLocalMatrix(shared,rotation),translation)
        val canvas=Matrix3x3F32(sx=2f,sy=2f)
        val transformed=W5gNoiseCpuOracle.expected(ordered,Point2F32(.5f,.5f),canvasMatrixF32=canvas)
        boundedDisjoint(transformed,W5gNoiseCpuOracle.expected(reverse,Point2F32(.5f,.5f),canvasMatrixF32=canvas))
        boundedDisjoint(transformed,W5gNoiseCpuOracle.expected(ordered,Point2F32(.5f,.5f)))
        val clamp=RectF32.ofLTRB(.25f,.5f,.25f,.5f)
        val clampFirst=Shader.CoordClamp(Shader.WithLocalMatrix(shared,translation),clamp)
        val transformFirst=Shader.WithLocalMatrix(Shader.CoordClamp(shared,clamp),translation)
        val clamped=W5gNoiseCpuOracle.expected(clampFirst,Point2F32(.5f,.5f))
        boundedDisjoint(clamped,W5gNoiseCpuOracle.expected(transformFirst,Point2F32(.5f,.5f)))
        (listOf(sibling to expected,clampFirst to clamped)+negative).forEach { (leaf,wanted) -> render(leaf,route,wanted) }
        render(ordered,route,transformed,canvasMatrix=canvas)
    }

    @ParameterizedTest(name = "nonzero requested octave domain: fractal={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun allRequestedOctavesRetainNonzeroContributions(fractal: Boolean,route: Int) {
        val cases=listOf(2,8,255).map { octaves ->
            val shader=shader(fractal,octaves)
            val expected=W5gNoiseCpuOracle.expected(shader,Point2F32(.5f,.5f))
            val zero=W5gNoiseCpuOracle.expected(shader(fractal,0),Point2F32(.5f,.5f))
            val single=W5gNoiseCpuOracle.expected(shader(fractal,1),Point2F32(.5f,.5f))
            boundedDisjoint(expected,zero); boundedDisjoint(expected,single)
            println("Noise requested=$octaves fractal=$fractal route=$route expected=${channels(expected)} zero=${channels(zero)} single=${channels(single)}")
            shader to expected
        }
        cases.forEach { (shader,expected) -> render(shader,route,expected) }
    }

    @ParameterizedTest(name = "integral stitch recurrence: fractal={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun stitchWrapsEveryCornerAndDoublesPeriods(fractal: Boolean,route: Int) {
        val cases=listOf(2,8,255).flatMap { octave ->
            val leaf=if(fractal) Shader.FractalNoise(.2f,.3f,octave,7,SizeI32(8,4))
                else Shader.PerlinNoise(.2f,.3f,octave,7,SizeI32(8,4))
            // P=(7.25,3.25): both upper corners cross the first-octave periods.
            listOf(0f to 0f,8f to 0f,0f to 4f).map { (dx,dy) ->
                val shader=Shader.CoordClamp(leaf,RectF32.ofLTRB(7.25f+dx,3.25f+dy,7.25f+dx,3.25f+dy))
                val expected=W5gNoiseCpuOracle.expected(shader,Point2F32(.5f,.5f))
                val noStitch=if(fractal) Shader.FractalNoise(.25f,.25f,octave,7,null) else Shader.PerlinNoise(.25f,.25f,octave,7,null)
                val wrong=W5gNoiseCpuOracle.expected(Shader.CoordClamp(noStitch,
                    RectF32.ofLTRB(7.25f+dx,3.25f+dy,7.25f+dx,3.25f+dy)),Point2F32(.5f,.5f))
                boundedDisjoint(expected,wrong)
                println("Noise stitch requested=$octave fractal=$fractal route=$route shift=$dx,$dy expected=${channels(expected)} noStitch=${channels(wrong)}")
                shader to expected
            }.also { period ->
                assertTrue(period.drop(1).all { channels(it.second) == channels(period.first().second) },"Independent period identity")
            }
        }
        cases.forEach { (shader,expected) -> render(shader,route,expected) }
    }
    @ParameterizedTest(name = "zero octaves: fractal={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun zeroOctavesAreDifferentMaterialsOnAllFillRoutes(fractal: Boolean, route: Int) {
        // A shared constant implementation, swapped family, or sRGB .5 solid must fail.
        val shader = shader(fractal, 0)
        val expected = W5gNoiseCpuOracle.expected(shader, Point2F32(.5f, .5f))
        val other = W5gNoiseCpuOracle.expected(shader(!fractal, 0), Point2F32(.5f, .5f))
        boundedDisjoint(expected, other)
        println("Noise fixture zero fractal=$fractal route=$route expected=${channels(expected)} opposite=${channels(other)}")
        render(shader, route, expected)
    }

    @ParameterizedTest(name = "seeded nonzero: fractal={0}, fill route={1}")
    @CsvSource("false,0", "false,1", "false,2", "true,0", "true,1", "true,2")
    fun seededNonzeroOctaveUsesIndependentGradientBytesOnAllFillRoutes(fractal: Boolean, route: Int) {
        // Ignoring the seed or octave work must produce a disjoint public byte set.
        val shader = shader(fractal, 1)
        val expected = W5gNoiseCpuOracle.expected(shader, Point2F32(.5f, .5f))
        val zero = W5gNoiseCpuOracle.expected(shader(fractal, 0), Point2F32(.5f, .5f))
        val otherSeed = W5gNoiseCpuOracle.expected(shader(fractal, 1, 1), Point2F32(.5f, .5f))
        boundedDisjoint(expected, zero)
        boundedDisjoint(expected, otherSeed)
        println("Noise fixture nonzero fractal=$fractal route=$route seed=7 expected=${channels(expected)} zero=${channels(zero)} seed1=${channels(otherSeed)}")
        render(shader, route, expected)
    }

    private fun shader(fractal: Boolean, octaves: Int, seed: Int = 7): Shader = if (fractal)
        Shader.FractalNoise(baseX = .125f, baseY = .25f, numOctaves = octaves, seed = seed, tileSize = null)
        else Shader.PerlinNoise(.125f, .25f, octaves, seed, null)

    private fun channels(value: WgslFloatEnvelopeV1Oracle.DrawResult) =
        (value as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded).channels

    private fun boundedDisjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult, b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a)
        W5fSurfacePixelFixtures.requireBounded(b)
        assertTrue(channels(a).zip(channels(b)).any { (left, right) -> left.intersect(right).isEmpty() },
            "Expected disjoint independent byte sets: ${channels(a)} versus ${channels(b)}")
    }

    private fun render(shader: Shader, route: Int, expected: WgslFloatEnvelopeV1Oracle.DrawResult,
        canvasMatrix: Matrix3x3F32 = Matrix3x3F32()) {
        // Every expectation and discriminant is computed before the first Surface.
        val surface = Surface(1, 1)
        surface.canvas {
            concat(canvasMatrix)
            val paint = Paint(shader = shader, blendMode = BlendMode.SRC, antiAlias = false)
            if (route == 0) drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), paint)
            else drawPath(fillPath(route), paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected)) }
    }
    private fun fillPath(route: Int) = Path().apply {
        if (route == 1) {
            moveTo(-10f,-10f); lineTo(20f,-10f); lineTo(-10f,20f); close()
        } else {
            moveTo(-1f,-1f); lineTo(5f,-1f); lineTo(5f,5f); lineTo(2f,2f); lineTo(-1f,5f); close()
        }
    }
}
