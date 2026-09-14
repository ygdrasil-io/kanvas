@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.paint.ColorSpaceInterpolation
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.geometry.Path
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.graphiks.kanvas.surface.WgslFloatEnvelopeV1Oracle.Interval
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class W5fGradientInterpolationSurfacePixelTest {
    enum class Family { Linear, Radial, Sweep, Conical }
    enum class Lane { Rect, RRect, DirectFill, StencilFill, Stroke }
    enum class FilterKind { Matrix, Table }
    companion object {
        @JvmStatic fun familyLanes(): List<Arguments> = Family.entries.flatMap { family ->
            Lane.entries.map { lane -> Arguments.of(family,lane) } }
        @JvmStatic fun domainStopCounts(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).flatMap { domain -> listOf(1,2,16,17).map { Arguments.of(domain,it) } }
        @JvmStatic fun filteredLanes(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).flatMap { domain -> listOf(Lane.Rect,Lane.DirectFill).flatMap { lane ->
            FilterKind.entries.flatMap { filter -> listOf(false,true).map { Arguments.of(domain,lane,filter,it) } } } }
        @JvmStatic fun domainTiles(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).flatMap { domain -> TileMode.entries.map { Arguments.of(domain,it) } }
        @JvmStatic fun domainRectPath(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).flatMap { domain -> listOf(Lane.Rect,Lane.DirectFill).map { Arguments.of(domain,it) } }
        @JvmStatic fun degeneratePeriodicSources(): List<Arguments> = listOf(ColorSpaceInterpolation.SRGB,
            ColorSpaceInterpolation.LINEAR,ColorSpaceInterpolation.OKLAB).flatMap { domain ->
            Family.entries.flatMap { family -> listOf(TileMode.REPEAT,TileMode.MIRROR).map { Arguments.of(domain,family,it) } } }
        @JvmStatic fun domainNoOpLanes(): List<Arguments> = listOf(ColorSpaceInterpolation.SRGB,
            ColorSpaceInterpolation.LINEAR,ColorSpaceInterpolation.OKLAB).flatMap { domain ->
            listOf(Lane.RRect,Lane.DirectFill,Lane.StencilFill,Lane.Stroke).map { Arguments.of(domain,it) } }
        @JvmStatic fun newDomains(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).map { Arguments.of(it) }
        @JvmStatic fun domainBudgetModes(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).flatMap { domain -> listOf(BlendMode.SRC_OVER,BlendMode.SRC).map { Arguments.of(domain,it) } }
        @JvmStatic fun filteredClosedLanes(): List<Arguments> = listOf(ColorSpaceInterpolation.LINEAR,
            ColorSpaceInterpolation.OKLAB).flatMap { domain -> listOf(Lane.RRect,Lane.Stroke).flatMap { lane ->
            FilterKind.entries.flatMap { filter -> listOf(false,true).map { Arguments.of(domain,lane,filter,it) } } } }
    }

    @ParameterizedTest(name = "{0} sweep cardinal guards and atan2 domain")
    @MethodSource("newDomains")
    fun sweepCardinalGuardsPreserveAxesAndRefuseOutsideAtan2AccuracyDomain(domain: ColorSpaceInterpolation) {
        val black = ColorARGB.of(128,0,0,0); val white = ColorARGB.of(128,255,255,255)
        val stops = listOf(GradientStop(0f,black),GradientStop(1f,white))
        fun surface(center: Point2F32) = Surface(1,1).also { surface -> surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=Shader.CoordClamp(
                Shader.SweepGradient(center,0f,360f,stops,interpolation=domain),
                RectF32.ofLTRB(.5f,.5f,.5f,.5f)),blendMode=BlendMode.SRC,antiAlias=false))
        } }
        // Independent cardinal geometry: center, +x, +y, -x, -y. The shader's
        // eager atan2 must receive safe nonzero operands even on these axes.
        val cardinal = listOf(Point2F32(.5f,.5f) to 0f,Point2F32(-.5f,.5f) to 0f,
            Point2F32(.5f,-.5f) to 90f,Point2F32(1.5f,.5f) to 180f,Point2F32(.5f,1.5f) to 270f)
        val expected = cardinal.map { (_,degrees) -> W5fColorCpuOracle.expectedGradientPixel(domain,black,white,
            WgslFloatEnvelopeV1Oracle.gradientDivide(Interval.input(degrees),Interval.input(360f)),finalBlend=BlendMode.SRC) }
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        val healthy = cardinal.map { surface(it.first) }
        repeat(2) { healthy.forEachIndexed { index,value ->
            W5fSurfacePixelFixtures.assertNativePixels(value.render(),listOf(expected[index]))
        } }
        // Finite, normal, but |x| > 2^126: WGSL's 4096-ULP atan2 guarantee
        // does not cover this actual eager call, even though its angle is tiny.
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> {
                surface(Point2F32(-Math.scalb(1f,127),-1f)).render()
            }
            assertEquals("unsupported.material.filter.numeric-domain-unbounded",
                failure.message.orEmpty().substringBefore(':'),failure.message)
            W5fSurfacePixelFixtures.assertNativePixels(healthy[3].render(),listOf(expected[3]))
        }
    }

    @ParameterizedTest(name = "{0} filtered H {1} {2} internal={3}")
    @MethodSource("filteredClosedLanes")
    fun filteredRrectAndStrokeRemainClosedWithHealthyControl(domain: ColorSpaceInterpolation,lane: Lane,kind: FilterKind,internal: Boolean) {
        val black = ColorARGB.of(128,0,0,0); val white = ColorARGB.of(128,255,255,255)
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,black,white,parameter(Family.Linear),finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val leaf = shader(Family.Linear,listOf(GradientStop(0f,black),GradientStop(1f,white)),domain)
        val healthy = Surface(1,1)
        healthy.canvas { drawLane(lane,Paint(shader=leaf,blendMode=BlendMode.SRC,antiAlias=false)) }
        val filter = when (kind) {
            FilterKind.Matrix -> ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.5f,.5f,.5f,1f) })
            FilterKind.Table -> ColorFilter.Table(UByteArray(256) { (255-it).toUByte() })
        }
        repeat(2) {
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
            val rejected = Surface(1,1)
            rejected.canvas { drawLane(lane,Paint(shader=if (internal) Shader.WithColorFilter(leaf,filter) else leaf,
                colorFilter=if (internal) null else filter,blendMode=BlendMode.SRC,antiAlias=false)) }
            val failure = assertFailsWith<IllegalStateException> { rejected.render() }
            assertTrue(failure.message.orEmpty().startsWith("unsupported."),failure.message)
            W5fSurfacePixelFixtures.assertNativePixels(healthy.render(),listOf(expected))
        }
    }

    @ParameterizedTest(name = "{0} changed captured values")
    @MethodSource("newDomains")
    fun changedValuesOnRetainedSurfaceDoNotReuseEarlierPreparedSource(domain: ColorSpaceInterpolation) {
        val black = ColorARGB.of(128,0,0,0); val white = ColorARGB.of(128,255,255,255)
        val red = ColorARGB.of(128,255,0,0)
        val initial = W5fColorCpuOracle.expectedGradientPixel(domain,black,white,parameter(Family.Linear),finalBlend=BlendMode.SRC)
        val changed = W5fColorCpuOracle.expectedGradientPixel(domain,black,red,parameter(Family.Linear),finalBlend=BlendMode.SRC)
        disjoint(initial,changed)
        val surface = Surface(1,1)
        val stops = mutableListOf(GradientStop(0f,black),GradientStop(1f,white))
        surface.canvas { drawLane(Lane.Rect,Paint(shader=shader(Family.Linear,stops,domain),blendMode=BlendMode.SRC,antiAlias=false)) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(initial)) }
        stops[1] = GradientStop(1f,red)
        surface.canvas { drawLane(Lane.Rect,Paint(shader=shader(Family.Linear,stops,domain),blendMode=BlendMode.SRC,antiAlias=false)) }
        stops[0] = GradientStop(0f,white); stops[1] = GradientStop(1f,white)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(changed)) }
    }

    @ParameterizedTest(name = "{0} causal shared stop budget {1}")
    @MethodSource("domainBudgetModes")
    fun shared17StopBudgetHasDistinctSmallControlAndRecovers(domain: ColorSpaceInterpolation,mode: BlendMode) {
        val white = ColorARGB.White
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,white,white,Interval.ZERO,finalBlend=mode)
        W5fSurfacePixelFixtures.requireBounded(expected)
        // Fixed public fixture budget, not a device-limit substitution or search.
        // Same 64 alternating lanes as the established Task2 budget controls;
        // 64 distinct 17*32B stop ranges add 30,720B over their 2-stop control.
        val budget = 1_600_000L
        fun frame(bytes: Long,count: Int=17,distinct: Boolean=true) = Surface(29,1,
            config=RenderConfig(frameLocalBudgetBytes=bytes)).also { surface -> surface.canvas {
            repeat(64) { index ->
                val stops = List(count) { stop -> GradientStop(stop.toFloat()/(count-1),
                    if (stop == count-1) white else ColorARGB.of(255,if (distinct) index else 0,0,0)) }
                val paint = Paint(shader=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(.25f,0f),stops,
                    interpolation=domain),blendMode=mode,antiAlias=false)
                if (index%2 == 0) drawRect(RectF32.ofLTRB(0f,0f,29f,1f),paint)
                else drawPath(Path().apply { moveTo(-10f,-10f); lineTo(80f,-10f); lineTo(-10f,80f); close() },paint)
            }
        } }
        fun check(surface: Surface) = W5fSurfacePixelFixtures.assertNativePixels(surface.render(),List(29) { expected })
        val healthy = frame(1L shl 23)
        check(healthy)
        check(frame(budget,count=2))
        check(frame(budget,distinct=false))
        repeat(2) {
            val failure = assertFailsWith<IllegalStateException> { frame(budget).render() }
            assertEquals("resource.material.gradient.stop-budget",failure.message.orEmpty().substringBefore(':'),failure.message)
            check(healthy)
        }
    }

    @ParameterizedTest(name = "{0} ordinary mixed source construction")
    @MethodSource("newDomains")
    fun ordinaryMixedRectGradientAndSolidPathKeepTheirPixels(domain: ColorSpaceInterpolation) {
        val black = ColorARGB.Black; val white = ColorARGB.White
        // This witnesses ordinary construction/retained old source ownership.
        // The endpoint closes the independent fixed-function precision envelope;
        // domain-vs-SRGB interpolation discrimination is covered separately.
        val gradient = W5fColorCpuOracle.expectedGradientPixel(domain,white,white,Interval.ZERO,finalBlend=BlendMode.SRC_OVER)
        val solid = W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,ColorARGB.Red,ColorARGB.Red,
            Interval.ZERO,finalBlend=BlendMode.SRC_OVER)
        disjoint(gradient,solid)
        disjoint(gradient,W5fColorCpuOracle.expectedGradientPixel(domain,black,black,
            Interval.ZERO,finalBlend=BlendMode.SRC_OVER))
        val surface = Surface(2,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(.25f,0f),
                listOf(GradientStop(0f,black),GradientStop(1f,white)),interpolation=domain),antiAlias=false))
            drawPath(Path().apply { addRect(RectF32.ofLTRB(1f,-1f,3f,2f)) },Paint(color=ColorARGB.Red,antiAlias=false))
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(gradient,solid)) }
    }

    @ParameterizedTest(name = "{0} retained no-op {1}")
    @MethodSource("domainNoOpLanes")
    fun mixedNoOpLaneDoesNotEraseCapturedGradient(domain: ColorSpaceInterpolation,lane: Lane) {
        val left = ColorARGB.of(128,0,0,0); val right = ColorARGB.of(128,255,255,255)
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,left,right,parameter(Family.Linear),
            finalBlend=BlendMode.SRC)
        disjoint(expected,W5fColorCpuOracle.expectedGradientPixel(domain,ColorARGB.White,ColorARGB.White,
            Interval.ZERO,finalBlend=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas {
            drawLane(Lane.Rect,Paint(shader=shader(Family.Linear,listOf(GradientStop(0f,left),GradientStop(1f,right)),domain),
                blendMode=BlendMode.SRC,antiAlias=false))
            drawLane(lane,Paint(color=ColorARGB.White,blendMode=BlendMode.DST,antiAlias=false))
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    @ParameterizedTest(name = "{0} projective division and zero mask")
    @MethodSource("newDomains")
    fun projectiveSourceDividesBeforeClampAndMasksZeroW(domain: ColorSpaceInterpolation) {
        val black = ColorARGB.of(128,0,0,0); val white = ColorARGB.of(128,255,255,255)
        val oracle = WgslFloatEnvelopeV1Oracle
        // Binary-parts normalization is exact; 2/1 -> frexp -> ldexp
        // has the same 3.5-ULP quotient enclosure (both outputs are normal).
        val midpoint = oracle.gradientDivide(oracle.gradientDivide(Interval.input(2f),Interval.ONE),Interval.input(4f))
        fun expected(t: Interval) = W5fColorCpuOracle.expectedGradientPixel(domain,black,white,t,finalBlend=BlendMode.SRC)
        val middle = expected(midpoint)
        val transparent = W5fColorCpuOracle.expectedGradientPixel(domain,ColorARGB.Transparent,ColorARGB.Transparent,
            Interval.ZERO,finalBlend=BlendMode.SRC)
        disjoint(middle,expected(Interval.ZERO))
        disjoint(middle,W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,black,white,midpoint,
            finalBlend=BlendMode.SRC))
        disjoint(transparent,middle)
        val matrix = Matrix3x3F32(sx=.75f,kx=0f,tx=1f,ky=0f,sy=1f,ty=0f,persp0=.5f,persp1=0f,persp2=0f)
        val leaf = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(4f,0f),
            listOf(GradientStop(0f,black),GradientStop(1f,white)),interpolation=domain)
        val surface = Surface(3,1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,3f,1f),Paint(shader=Shader.WithLocalMatrix(
            Shader.CoordClamp(leaf,RectF32.ofLTRB(0f,0f,2f,1f)),matrix),blendMode=BlendMode.SRC,antiAlias=false)) }
        // Inverse homogeneous rows: (2,y,x-1.5). At sample centers:
        // (-2,-.5), invalid w=0, (2,.5); omitting division would paint midpoint everywhere.
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected(Interval.ZERO),transparent,middle)) }
    }

    @ParameterizedTest(name = "{0} non-fixed affine {1}")
    @MethodSource("domainRectPath")
    fun nonFixedCoordinatesRemainInsideShaderFilter(domain: ColorSpaceInterpolation,lane: Lane) {
        val black = ColorARGB.of(128,0,0,0); val white = ColorARGB.of(128,255,255,255)
        val filter = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(.5f,.5f,.5f,1f) })
        val t = WgslFloatEnvelopeV1Oracle.gradientAdd(Interval.input(.5f),Interval.input(.25f))
        fun expected(parameter: Interval) = W5fColorCpuOracle.expectedGradientPixel(domain,black,white,parameter,
            external=filter,finalBlend=BlendMode.SRC)
        val wanted = expected(t)
        disjoint(wanted,expected(Interval.input(.5f)))
        disjoint(wanted,W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,black,white,t,
            external=filter,finalBlend=BlendMode.SRC))
        val surface = Surface(1,1)
        surface.canvas {
            if (lane == Lane.DirectFill) concat(Matrix3x3F32(sx=0f,kx=-1f,tx=1f,ky=1f,sy=0f,ty=-.25f))
            val leaf = shader(Family.Linear,listOf(GradientStop(0f,black),GradientStop(1f,white)),domain)
            val source = if (lane == Lane.Rect) Shader.WithLocalMatrix(leaf,Matrix3x3F32.translation(-.25f,0f)) else leaf
            drawLane(lane,Paint(shader=Shader.WithColorFilter(source,filter),blendMode=BlendMode.SRC,antiAlias=false))
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @ParameterizedTest(name = "{0} shared range average")
    @MethodSource("newDomains")
    fun degenerateAverageUsesOnlyItsOriginalRangeInMixedSlab(domain: ColorSpaceInterpolation) {
        val left = ColorARGB.of(128,0,0,0); val right = ColorARGB.of(128,255,255,255)
        val unrelated = ColorARGB.of(128,255,0,0)
        val expectedAverage = W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,left,right,
            Interval.input(.5f),finalBlend=BlendMode.SRC)
        val expectedOther = W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,unrelated,unrelated,
            Interval.ZERO,finalBlend=BlendMode.SRC)
        disjoint(expectedAverage,expectedOther)
        // An erroneous whole-slab trapezoid walk also integrates the backward
        // 1->0 join: red + (-red/2) + gray = (1,.5,.5), not gray.
        disjoint(expectedAverage,W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,unrelated,right,
            Interval.input(.5f),finalBlend=BlendMode.SRC))
        disjoint(expectedAverage,W5fColorCpuOracle.expectedGradientPixel(domain,left,right,Interval.input(.5f),
            finalBlend=BlendMode.SRC))
        // Identical requested degenerate source appears twice, after an unrelated
        // old-domain range; averaging the entire shared slab cannot yield its own integral.
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val surface = Surface(3,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=Shader.LinearGradient(Point2F32(0f,0f),
                Point2F32(1f,0f),listOf(GradientStop(0f,unrelated),GradientStop(1f,unrelated))),
                blendMode=BlendMode.SRC,antiAlias=false))
            for (x in 1..2) drawRect(RectF32.ofLTRB(x.toFloat(),0f,x+1f,1f),Paint(shader=Shader.LinearGradient(
                Point2F32(0f,0f),Point2F32(0f,0f),stops,TileMode.REPEAT,domain),blendMode=BlendMode.SRC,antiAlias=false))
        }
        stops[0] = GradientStop(0f,right)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expectedOther,expectedAverage,expectedAverage)) }
    }

    @ParameterizedTest(name = "{0} duplicate and DECAL selection")
    @MethodSource("newDomains")
    fun duplicateStopSelectionAndOutsideDecalAreNotClamped(domain: ColorSpaceInterpolation) {
        val black = ColorARGB.of(128,0,0,0); val white = ColorARGB.of(128,255,255,255)
        val selected = W5fColorCpuOracle.expectedGradientPixel(domain,white,white,Interval.ZERO,finalBlend=BlendMode.SRC)
        val rejected = W5fColorCpuOracle.expectedGradientPixel(domain,black,black,Interval.ZERO,finalBlend=BlendMode.SRC)
        val transparent = W5fColorCpuOracle.expectedGradientPixel(domain,ColorARGB.Transparent,ColorARGB.Transparent,
            Interval.ZERO,finalBlend=BlendMode.SRC)
        disjoint(selected,rejected); disjoint(selected,transparent)
        val surface = Surface(2,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
                listOf(GradientStop(0f,black),GradientStop(.25f,black),GradientStop(.25f,white),GradientStop(1f,white)),
                interpolation=domain),blendMode=BlendMode.SRC,antiAlias=false))
            drawRect(RectF32.ofLTRB(1f,0f,2f,1f),Paint(shader=Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
                listOf(GradientStop(0f,black),GradientStop(1f,white)),TileMode.DECAL,domain),
                blendMode=BlendMode.SRC,antiAlias=false))
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(selected,transparent)) }
    }

    @ParameterizedTest(name = "{0} degenerate {1} {2}")
    @MethodSource("degeneratePeriodicSources")
    fun degeneratePeriodicSourceAveragesOriginalSrgb(domain: ColorSpaceInterpolation,family: Family,mode: TileMode) {
        val left = ColorARGB.of(128,0,0,0); val right = ColorARGB.of(128,255,255,255)
        // The exact straight-SRGB integral of this linear stop segment is .5.
        // The ordinary rounded interpolation envelope contains that exact Host
        // value and the subsequent original-SRGB -> linear -> premul schedule.
        val expected = W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,left,right,
            Interval.input(.5f),finalBlend=BlendMode.SRC)
        val domainAverage = W5fColorCpuOracle.expectedGradientPixel(
            if (domain == ColorSpaceInterpolation.SRGB) ColorSpaceInterpolation.LINEAR else domain,left,right,
            Interval.input(.5f),finalBlend=BlendMode.SRC)
        disjoint(expected,domainAverage)
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val zero = Point2F32(0f,0f)
        val shader = when (family) {
            Family.Linear -> Shader.LinearGradient(zero,zero,stops,mode,domain)
            Family.Radial -> Shader.RadialGradient(zero,0f,stops,mode,domain)
            Family.Sweep -> Shader.SweepGradient(zero,20f,20f,stops,mode,domain)
            Family.Conical -> Shader.ConicalGradient(zero,0f,zero,0f,stops,mode,domain)
        }
        val surface = Surface(1,1)
        surface.canvas { drawLane(Lane.Rect,Paint(shader=shader,blendMode=BlendMode.SRC,antiAlias=false)) }
        stops[0] = GradientStop(0f,right)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    @ParameterizedTest(name = "{0} {1} {2} internal={3}")
    @MethodSource("filteredLanes")
    fun interpolatedOutputFeedsMatrixAndTable(domain: ColorSpaceInterpolation,lane: Lane,kind: FilterKind,internal: Boolean) {
        val left = ColorARGB.of(128,if (domain == ColorSpaceInterpolation.OKLAB) 255 else 0,0,0)
        val right = ColorARGB.of(128,if (domain == ColorSpaceInterpolation.OKLAB) 0 else 255,255,
            if (domain == ColorSpaceInterpolation.OKLAB) 0 else 255)
        val filter = when (kind) {
            FilterKind.Matrix -> ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
                .5f,0f,0f,0f,.125f, 0f,.5f,0f,0f,.125f, 0f,0f,.5f,0f,.125f, 0f,0f,0f,1f,0f)))
            // Plateaus retain actual source dependence without turning a rounded
            // input-code seam into an arbitrarily large lookup discontinuity.
            FilterKind.Table -> ColorFilter.Table(UByteArray(256) { (255-((it+4)/8)*8).coerceAtLeast(0).toUByte() })
        }
        fun expected(d: ColorSpaceInterpolation,f: ColorFilter?) = W5fColorCpuOracle.expectedGradientPixel(
            d,left,right,parameter(Family.Linear),external=f,finalBlend=BlendMode.SRC)
        val wanted = expected(domain,filter)
        disjoint(wanted,expected(domain,null))
        disjoint(wanted,expected(ColorSpaceInterpolation.SRGB,filter))
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val leaf = shader(Family.Linear,stops,domain)
        val surface = Surface(1,1)
        surface.canvas { drawLane(lane,Paint(shader=if (internal) Shader.WithColorFilter(leaf,filter) else leaf,
            colorFilter=if (internal) null else filter,blendMode=BlendMode.SRC,antiAlias=false)) }
        stops[0] = GradientStop(0f,ColorARGB.White); stops[1] = GradientStop(1f,ColorARGB.White)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @ParameterizedTest(name = "{0} tile {1}")
    @MethodSource("domainTiles")
    fun interpolationUsesActualTileBeforeStopSelection(domain: ColorSpaceInterpolation,mode: TileMode) {
        val left = ColorARGB.of(128,0,0,0); val right = ColorARGB.of(128,255,255,255)
        val oracle = WgslFloatEnvelopeV1Oracle
        // REPEAT/MIRROR execute outside the first period; CLAMP/DECAL execute
        // inside it. The independent floor/subtract schedule is included.
        val periodic = mode == TileMode.REPEAT || mode == TileMode.MIRROR
        val input = if (periodic) oracle.gradientDivide(oracle.gradientSubtract(Interval.input(.5f),
            Interval.input(-1f)),Interval.ONE) else parameter(Family.Linear)
        val t = when (mode) {
            TileMode.REPEAT -> oracle.gradientSubtract(input,oracle.gradientFloor(input))
            TileMode.MIRROR -> {
                val q = oracle.gradientSubtract(input,oracle.gradientMultiply(Interval.input(2f),
                    oracle.gradientFloor(oracle.gradientMultiply(input,Interval.input(.5f)))))
                val delta = oracle.gradientSubtract(q,Interval.ONE)
                require(delta.lower.signum() > 0) // abs is exact on this actual positive interval.
                oracle.gradientSubtract(Interval.ONE,delta)
            }
            else -> input
        }
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,left,right,t,finalBlend=BlendMode.SRC)
        disjoint(expected,W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,left,right,t,
            finalBlend=BlendMode.SRC))
        if (periodic) disjoint(expected,W5fColorCpuOracle.expectedGradientPixel(domain,right,right,Interval.ZERO,
            finalBlend=BlendMode.SRC))
        val start = if (periodic) -1f else 0f
        val surface = Surface(1,1)
        surface.canvas { drawLane(Lane.Rect,Paint(shader=Shader.LinearGradient(Point2F32(start,0f),
            Point2F32(start+1f,0f),listOf(GradientStop(0f,left),GradientStop(1f,right)),tileMode=mode,
            interpolation=domain),blendMode=BlendMode.SRC,antiAlias=false)) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    @ParameterizedTest(name = "{0} ordered coordinate/opacity wrappers {1}")
    @MethodSource("domainRectPath")
    fun orderedCoordinateAndOpacityWrappersRemainAttached(domain: ColorSpaceInterpolation,lane: Lane) {
        val left = ColorARGB.of(128,0,0,0); val right = ColorARGB.of(128,255,255,255)
        val oracle = WgslFloatEnvelopeV1Oracle
        val translated = oracle.gradientAdd(Interval.input(.25f),Interval.input(.5f))
        val t = oracle.gradientDivide(oracle.gradientSubtract(translated,Interval.input(.25f)),Interval.ONE)
        fun expected(parameter: Interval,opacity: Float=.5f) = W5fColorCpuOracle.expectedGradientPixel(
            domain,left,right,parameter,finalBlend=BlendMode.SRC,shaderOpacityF32=opacity,paintAlphaF32=191f/255f)
        val wanted = expected(t)
        disjoint(wanted,expected(Interval.ZERO)) // missing local matrix
        disjoint(wanted,expected(oracle.gradientDivide(oracle.gradientSubtract(
            oracle.gradientAdd(Interval.input(.5f),Interval.input(.5f)),Interval.input(.25f)),Interval.ONE))) // missing clamp
        disjoint(wanted,expected(t,1f)) // missing unary opacity
        val matrix = Matrix3x3F32.translation(-.5f,0f)
        val subset = RectF32.ofLTRB(0f,0f,.25f,1f)
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val leaf = Shader.LinearGradient(Point2F32(.25f,0f),Point2F32(1.25f,0f),stops,interpolation=domain)
        val source = Shader.Opacity(Shader.CoordClamp(Shader.WithLocalMatrix(leaf,matrix),subset),.5f)
        val surface = Surface(1,1)
        surface.canvas { drawLane(lane,Paint(color=ColorARGB.of(191,0,0,0),shader=source,
            blendMode=BlendMode.SRC,antiAlias=false)) }
        stops[0] = GradientStop(0f,right)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(wanted)) }
    }

    @ParameterizedTest(name = "LINEAR {0} {1}")
    @MethodSource("familyLanes")
    fun linearFamiliesLanesAlphaMutationAndFinalBlend(family: Family,lane: Lane) =
        familyLane(ColorSpaceInterpolation.LINEAR,family,lane)

    @ParameterizedTest(name = "OKLAB {0} {1}")
    @MethodSource("familyLanes")
    fun oklabFamiliesLanesAlphaMutationAndFinalBlend(family: Family,lane: Lane) =
        familyLane(ColorSpaceInterpolation.OKLAB,family,lane)

    @ParameterizedTest(name = "SRGB control {0} {1}")
    @MethodSource("familyLanes")
    fun srgbFamiliesLanesRemainPromoted(family: Family,lane: Lane) =
        familyLane(ColorSpaceInterpolation.SRGB,family,lane)

    @ParameterizedTest(name = "{0} captured {1} stops")
    @MethodSource("domainStopCounts")
    fun capturedStopCountsSelectTheActualFinalEntry(domain: ColorSpaceInterpolation,count: Int) {
        val white = ColorARGB.of(128,255,255,255)
        val black = ColorARGB.of(128,0,0,0)
        // The selected endpoint is beyond the historical inline16 boundary for17stops.
        // This is a stop inventory/selection witness; midpoint discrimination is separate.
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,white,white,Interval.ZERO,
            finalBlend=BlendMode.SRC)
        val changed = W5fColorCpuOracle.expectedGradientPixel(domain,black,black,Interval.ZERO,
            finalBlend=BlendMode.SRC)
        disjoint(expected,changed)
        val stops = MutableList(count) { index -> GradientStop(if (count == 1) 0f else index.toFloat()/(count-1),
            if (index == count-1) white else black) }
        val surface = Surface(1,1)
        surface.canvas { drawLane(Lane.Rect,Paint(shader=Shader.LinearGradient(Point2F32(0f,0f),
            Point2F32(.25f,0f),stops,interpolation=domain),blendMode=BlendMode.SRC,antiAlias=false)) }
        stops[count-1] = GradientStop(1f,black)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    @Test fun mixedDomainsDoNotAliasIdenticalOriginalStops() {
        val left = ColorARGB.of(128,0,0,0); val right = ColorARGB.of(128,255,255,255)
        val domains = listOf(ColorSpaceInterpolation.LINEAR,ColorSpaceInterpolation.OKLAB)
        val expected = domains.map { W5fColorCpuOracle.expectedGradientPixel(it,left,right,
            parameter(Family.Linear),finalBlend=BlendMode.SRC) }
        disjoint(expected[0],expected[1])
        expected.forEach { disjoint(it,W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,
            left,right,parameter(Family.Linear),finalBlend=BlendMode.SRC)) }
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val surface = Surface(2,1)
        surface.canvas { domains.forEachIndexed { index,domain ->
            val x = index.toFloat()
            drawRect(RectF32.ofLTRB(x,0f,x+1f,1f),Paint(shader=Shader.LinearGradient(Point2F32(x,0f),
                Point2F32(x+1f,0f),stops,interpolation=domain),blendMode=BlendMode.SRC,antiAlias=false))
        } }
        stops[0] = GradientStop(0f,right)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected) }
    }

    @Test fun linearTransparentStopRetainsItsStraightColor() = transparentStop(ColorSpaceInterpolation.LINEAR)
    @Test fun oklabTransparentStopRetainsItsStraightColor() = transparentStop(ColorSpaceInterpolation.OKLAB)

    private fun transparentStop(domain: ColorSpaceInterpolation) {
        val left = ColorARGB.of(0,255,0,0); val right = ColorARGB.of(255,0,255,0)
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,left,right,parameter(Family.Linear),
            finalBlend=BlendMode.SRC)
        val erased = W5fColorCpuOracle.expectedGradientPixel(domain,ColorARGB.of(0,0,0,0),right,
            parameter(Family.Linear),finalBlend=BlendMode.SRC)
        disjoint(expected,erased)
        val surface = Surface(1,1)
        surface.canvas { drawLane(Lane.Rect,Paint(shader=shader(Family.Linear,
            listOf(GradientStop(0f,left),GradientStop(1f,right)),domain),blendMode=BlendMode.SRC,antiAlias=false)) }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    @Test fun analyticRectLinearUsesCapturedSource() = familyLane(ColorSpaceInterpolation.LINEAR,Family.Linear,Lane.Rect,true)
    @Test fun analyticRectOklabUsesCapturedSource() = familyLane(ColorSpaceInterpolation.OKLAB,Family.Linear,Lane.Rect,true)
    @Test fun analyticRectSrgbMatchingControl() = familyLane(ColorSpaceInterpolation.SRGB,Family.Linear,Lane.Rect,true)
    @Test fun generalAffineLinearFillUsesCapturedSource() = familyLane(ColorSpaceInterpolation.LINEAR,Family.Linear,Lane.DirectFill,generalPath=true)
    @Test fun generalAffineOklabFillUsesCapturedSource() = familyLane(ColorSpaceInterpolation.OKLAB,Family.Linear,Lane.DirectFill,generalPath=true)
    @Test fun generalAffineSrgbFillMatchingControl() = familyLane(ColorSpaceInterpolation.SRGB,Family.Linear,Lane.DirectFill,generalPath=true)
    @Test fun generalAffineLinearStrokeUsesCapturedSource() = familyLane(ColorSpaceInterpolation.LINEAR,Family.Linear,Lane.Stroke,generalPath=true)
    @Test fun generalAffineOklabStrokeUsesCapturedSource() = familyLane(ColorSpaceInterpolation.OKLAB,Family.Linear,Lane.Stroke,generalPath=true)
    @Test fun generalAffineSrgbStrokeMatchingControl() = familyLane(ColorSpaceInterpolation.SRGB,Family.Linear,Lane.Stroke,generalPath=true)

    private fun familyLane(domain: ColorSpaceInterpolation,family: Family,lane: Lane,analyticRect: Boolean = false,generalPath: Boolean = false) {
        val left = ColorARGB.of(128,if (domain == ColorSpaceInterpolation.OKLAB) 255 else 0,0,0)
        val right = ColorARGB.of(128,if (domain == ColorSpaceInterpolation.OKLAB) 0 else 255,255,
            if (domain == ColorSpaceInterpolation.OKLAB) 0 else 255)
        val changed = ColorARGB.of(128,255,255,255)
        val destination = ColorARGB.of(127,72,0,0)
        val t = if (!generalPath) parameter(family) else {
            // Inverse of the exact quarter-turn CTM: local=(device.y,1-device.x).
            // Enclose the actual multiply/add coordinate schedule, not just its nominal fixed point.
            val half = Interval.input(.5f)
            val x = WgslFloatEnvelopeV1Oracle.gradientAdd(WgslFloatEnvelopeV1Oracle.gradientMultiply(Interval.ONE,half),Interval.ZERO)
            val y = WgslFloatEnvelopeV1Oracle.gradientAdd(WgslFloatEnvelopeV1Oracle.gradientMultiply(Interval.input(-1f),half),Interval.ONE)
            parameter(family,x,y)
        }
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,left,right,t,
            destination=destination,finalBlend=BlendMode.DIFFERENCE,destinationBlend=BlendMode.SRC)
        val mutation = W5fColorCpuOracle.expectedGradientPixel(domain,changed,changed,t,
            destination=destination,finalBlend=BlendMode.DIFFERENCE,destinationBlend=BlendMode.SRC)
        disjoint(expected,mutation)
        disjoint(expected,W5fColorCpuOracle.expectedGradientPixel(domain,left,right,t,
            finalBlend=BlendMode.DIFFERENCE))
        if (domain != ColorSpaceInterpolation.SRGB) disjoint(expected,
            W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,left,right,t,
                destination=destination,finalBlend=BlendMode.DIFFERENCE,destinationBlend=BlendMode.SRC))
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val surface = Surface(1,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=destination,blendMode=BlendMode.SRC,antiAlias=analyticRect))
            val paint = Paint(shader=shader(family,stops,domain),blendMode=BlendMode.DIFFERENCE,antiAlias=false)
            if (generalPath) concat(Matrix3x3F32(sx=0f,kx=-1f,tx=1f,ky=1f,sy=0f))
            if (analyticRect) drawRect(RectF32.ofLTRB(-.25f,-.25f,1.25f,1.25f),paint.copy(antiAlias=true))
            else drawLane(lane,paint)
        }
        stops[0] = GradientStop(0f,changed)
        stops[1] = GradientStop(1f,changed)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }

    private fun shader(family: Family,stops: List<GradientStop>,domain: ColorSpaceInterpolation): Shader = when (family) {
        Family.Linear -> Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),stops,interpolation=domain)
        Family.Radial -> Shader.RadialGradient(Point2F32(-.5f,.5f),2f,stops,interpolation=domain)
        Family.Sweep -> Shader.SweepGradient(Point2F32(-.5f,-.5f),-135f,225f,stops,interpolation=domain)
        Family.Conical -> Shader.ConicalGradient(Point2F32(-.5f,.5f),0f,Point2F32(-.5f,.5f),2f,stops,interpolation=domain)
    }

    private fun parameter(family: Family,pointX: Interval = Interval.input(.5f),pointY: Interval = Interval.input(.5f)): Interval {
        val oracle = WgslFloatEnvelopeV1Oracle
        fun input(value: Float) = Interval.input(value)
        fun sub(a: Interval,b: Interval) = oracle.gradientSubtract(a,b)
        fun dot(x: Interval,y: Interval,a: Interval,b: Interval): Interval = oracle.gradientHull(
            oracle.gradientAdd(oracle.gradientMultiply(x,a),oracle.gradientMultiply(y,b)),
            oracle.gradientFma(x,a,oracle.gradientMultiply(y,b)),
            oracle.gradientFma(y,b,oracle.gradientMultiply(x,a)))
        return when (family) {
            Family.Linear -> oracle.gradientDivide(dot(pointX,pointY,Interval.ONE,Interval.ZERO),Interval.ONE)
            Family.Radial, Family.Conical -> {
                val x = sub(pointX,input(-.5f)); val y = sub(pointY,input(.5f))
                val distance = oracle.gradientSqrt(dot(x,y,x,y))
                // Concentric Conical uses (length(q)-r0)/(r1-r0), with r0=0.
                oracle.gradientDivide(if (family == Family.Conical) sub(distance,Interval.ZERO) else distance,input(2f))
            }
            Family.Sweep -> {
                val x = sub(pointX,input(-.5f)); val y = sub(pointY,input(-.5f))
                val angle = oracle.gradientAtan2(y,x,4096.0)
                val turns = oracle.gradientDivide(angle,input(6.2831855f))
                val wrapped = sub(turns,oracle.gradientFloor(turns))
                val degrees = oracle.gradientMultiply(wrapped,input(360f))
                oracle.gradientDivide(sub(degrees,input(-135f)),input(360f))
            }
        }
    }

    private fun Canvas.drawLane(lane: Lane,paint: Paint) {
        when (lane) {
            Lane.Rect -> drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
            Lane.RRect -> drawRRect(RRectF32.of(RectF32.ofLTRB(-1f,-1f,2f,2f),CornerRadiiF32.of(.25f)),
                paint.copy(antiAlias=true))
            Lane.DirectFill -> drawPath(Path().apply {
                moveTo(-10f,-10f); lineTo(20f,-10f); lineTo(-10f,20f); close() },paint)
            Lane.StencilFill -> drawPath(Path().apply {
                moveTo(-1f,-1f); lineTo(5f,-1f); lineTo(5f,5f); lineTo(2f,2f); lineTo(-1f,5f); close() },paint)
            Lane.Stroke -> drawPath(Path().apply { moveTo(-2f,.5f); lineTo(3f,.5f) },
                paint.copy(style=PaintStyle.STROKE,strokeWidth=4f))
        }
    }

    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult,b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() },
            "Counterfactual not distinct: ${a.channels}/${b.channels}")
    }

    @Test fun linearMidpointUsesLinearLight() = midpoint(ColorSpaceInterpolation.LINEAR,
        ColorARGB.of(128,0,0,0),ColorARGB.of(128,255,255,255))

    @Test fun oklabMidpointUsesSignedLab() = midpoint(ColorSpaceInterpolation.OKLAB,
        ColorARGB.of(128,255,0,0),ColorARGB.of(128,0,255,0))

    private fun midpoint(domain: ColorSpaceInterpolation, left: ColorARGB, right: ColorARGB) {
        // At this pixel the rounded source schedule is dot((.5,.5),(1,0))/1.
        // Its division is bounded independently; .5/1 is not assumed exact.
        val t = WgslFloatEnvelopeV1Oracle.gradientDivide(
            WgslFloatEnvelopeV1Oracle.Interval.input(.5f),WgslFloatEnvelopeV1Oracle.Interval.ONE)
        val destination = ColorARGB.of(128,0,0,255)
        val expected = W5fColorCpuOracle.expectedGradientPixel(domain,left,right,t,
            destination=destination,finalBlend=BlendMode.SRC)
        val srgb = W5fColorCpuOracle.expectedGradientPixel(ColorSpaceInterpolation.SRGB,left,right,t,
            destination=destination,finalBlend=BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        W5fSurfacePixelFixtures.requireBounded(srgb)
        val selected = expected as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        val alternate = srgb as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(selected.channels.indices.any { selected.channels[it].intersect(alternate.channels[it].toSet()).isEmpty() })
        val stops = mutableListOf(GradientStop(0f,left),GradientStop(1f,right))
        val surface = Surface(1,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=destination,antiAlias=false))
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=Shader.LinearGradient(
                Point2F32(0f,0f),Point2F32(1f,0f),stops,interpolation=domain),
                blendMode=BlendMode.SRC,antiAlias=false))
        }
        stops[0] = GradientStop(0f,ColorARGB.Blue)
        stops[1] = GradientStop(1f,ColorARGB.Blue)
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }
}
