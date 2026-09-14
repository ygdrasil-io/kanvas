@file:OptIn(ExperimentalUnsignedTypes::class)
package org.graphiks.kanvas.surface

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class W5gComposedMaterialSurfacePixelTest {
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
        // A later invalid sibling must not override an earlier valid but
        // unpromoted gradient's owned refusal.
        val pending = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),stops)
        val earlier = Surface(1,1)
        earlier.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=
            Shader.Blend(BlendMode.SRC_OVER,pending,invalid),antiAlias=false)) }
        val firstFailure = assertFailsWith<IllegalStateException> { earlier.render() }
        assertEquals("unsupported.material.composed.slice",firstFailure.message.orEmpty().substringBefore(':'),firstFailure.message)
        val expected = W5fColorCpuOracle.expectedShaderTree(solid)
        render(Shader.Blend(BlendMode.SRC,Shader.SolidColor(ColorARGB.Blue),solid),expected)
    }

    @Test fun finalDestinationIdentityKeepsItsNoOpSemantics() {
        val shader = Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red),Shader.SolidColor(ColorARGB.Blue))
        val expected = W5fColorCpuOracle.expectedShaderTree(Shader.SolidColor(ColorARGB.Blue))
        W5fSurfacePixelFixtures.requireBounded(expected)
        render(shader,expected,finalBlend=BlendMode.DST,background=ColorARGB.Blue)
    }

    @Test fun pendingGradientAndImageChildrenAreOwnedRefusals() {
        val gradient = Shader.LinearGradient(Point2F32(0f,0f),Point2F32(1f,0f),
            listOf(GradientStop(0f,ColorARGB.Red),GradientStop(1f,ColorARGB.Blue)))
        val image = Shader.Image(Image.fromPixels(1,1,byteArrayOf(-1,0,0,-1)))
        for (pending in listOf(gradient,image)) {
            val surface = Surface(1,1)
            surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(shader=
                Shader.Blend(BlendMode.SRC_OVER,Shader.SolidColor(ColorARGB.Red),pending),antiAlias=false)) }
            val failure = assertFailsWith<IllegalStateException> { surface.render() }
            assertEquals("unsupported.material.composed.slice",failure.message.orEmpty().substringBefore(':'),failure.message)
        }
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
        background: ColorARGB = ColorARGB.Transparent) {
        W5fSurfacePixelFixtures.requireBounded(expected)
        for (lane in 0..2) {
            val surface = Surface(1,1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color=background,blendMode=BlendMode.SRC,antiAlias=false))
                val paint = Paint(color=ColorARGB.of(paintAlpha,255,255,255),shader=shader,
                    colorFilter=external,blendMode=finalBlend,antiAlias=false)
                if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
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
