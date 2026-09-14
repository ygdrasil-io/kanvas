package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.geometry.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(ExperimentalUnsignedTypes::class)
class W5fColorFilterSurfacePixelTest {
    @Test fun tableRectPathAlphaMutationAndFinalBlend() {
        primitiveRectPath(ColorFilter.Table(UByteArray(256) { (255-it).toUByte() }))
        exerciseKind(ColorFilter.Table(UByteArray(256) { (255-it).toUByte() }.apply { this[255] = 64u }))
        tablePayloadAndRoundedSelection()
    }
    @Test fun lightingRectPathAlphaMutationAndFinalBlend() {
        exerciseKind(ColorFilter.Lighting(ColorARGB.of(0,128,255,0),ColorARGB.of(19,64,255,255)))
    }
    @Test fun transferRectPathAlphaMutationAndFinalBlend() {
        exerciseKind(ColorFilter.SRGBToLinear)
        exerciseKind(ColorFilter.LinearToSRGB)
        for ((filter,point) in listOf(ColorFilter.SRGBToLinear to .04045f,ColorFilter.LinearToSRGB to .0031308f))
            for (value in listOf(Math.nextDown(point),point,Math.nextUp(point))) {
                val input = ColorFilter.Matrix(constantInput(value,0f,0f,1f))
                for (lane in 0..2) renderFilter(ColorFilter.Compose(filter,input),lane)
            }
    }
    @ParameterizedTest(name = "{0}")
    @EnumSource(BlendMode::class)
    fun blendModesRectPathAlphaMutationAndFinalBlend(mode: BlendMode) {
        exerciseKind(ColorFilter.Blend(ColorARGB.of(if (mode == BlendMode.DST_IN) 127 else 255,0,255,0),mode))
    }
    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult,b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a); W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded; b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() },"Counterfactual not distinct: ${a.channels}/${b.channels}")
    }
    private fun constantInput(r: Float,g: Float,b: Float,a: Float) = ColorMatrixF32.of(floatArrayOf(
        0f,0f,0f,0f,r, 0f,0f,0f,0f,g, 0f,0f,0f,0f,b, 0f,0f,0f,0f,a))
    private fun path(lane: Int): Path = Path().apply {
        if (lane == 1) { moveTo(-10f,-10f); lineTo(20f,-10f); lineTo(-10f,20f); close() }
        else { moveTo(-1f,-1f); lineTo(5f,-1f); lineTo(5f,5f); lineTo(2f,2f); lineTo(-1f,5f); close() }
    }
    private fun renderFilter(filter: ColorFilter,lane: Int,color: ColorARGB = ColorARGB.Black) {
        val expected = W5fColorCpuOracle.expectedPaintSource(color,filter,finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        surface.canvas {
            val paint = Paint(color = color,colorFilter = filter,blendMode = BlendMode.SRC,antiAlias = false)
            if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
        }
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
    }
    private fun exerciseKind(kind: ColorFilter) {
        primitiveRectPath(kind)
        val destination = ColorARGB.of(127,72,0,0)
        // DST is the exact identity, so no invented noncommutation assertion.
        if (kind !is ColorFilter.Blend || kind.mode != BlendMode.DST) {
            val matrix = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(.25f,0f,0f,0f,0f, 0f,.25f,0f,0f,.125f,
                0f,0f,.25f,0f,.125f, 0f,0f,0f,0f,.5f)))
            val after = ColorFilter.Compose(matrix,kind)
            val before = ColorFilter.Compose(kind,matrix)
            val color = ColorARGB.of(255,255,0,0)
            disjoint(W5fColorCpuOracle.expectedPaintSource(color,after,finalBlend = BlendMode.SRC),
                W5fColorCpuOracle.expectedPaintSource(color,before,finalBlend = BlendMode.SRC))
            for (lane in 0..2) { renderFilter(after,lane,color); renderFilter(before,lane,color) }
        }
        for (lane in 0..2) for (alpha in listOf(0f,1f,.25f)) {
            // A clamp-needed source transform retains actual alpha0/1/nonunit.
            val preparation = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
                .25f,0f,0f,0f,0f, 0f,0f,0f,0f,1.125f, 0f,0f,0f,0f,.0625f, 0f,0f,0f,1f,0f)))
            // HUE's zero-destination saturation with a positive source has no
            // bounded independent clip-luminosity envelope here. Exercise its
            // actual lazy source-alpha0 guard, retaining genuine input alpha0.
            val alphaKind = if (alpha == 0f && kind is ColorFilter.Blend && kind.mode == BlendMode.HUE)
                ColorFilter.Blend(ColorARGB.of(0,0,255,0),kind.mode) else kind
            val child = ColorFilter.Compose(alphaKind,preparation)
            val marker = ColorMatrixF32.of(floatArrayOf(.25f,0f,0f,0f,0f, 0f,.25f,0f,0f,0f,
                0f,0f,.25f,0f,.125f, 0f,0f,0f,0f,.5f))
            val scale = ColorFilter.Matrix(ColorMatrixF32.ofIdentity().apply { setScale(1f,1f,.5f,1f) })
            val filter = ColorFilter.Compose(scale,ColorFilter.Compose(ColorFilter.Matrix(marker),child))
            val reversed = ColorFilter.Compose(ColorFilter.Matrix(marker),ColorFilter.Compose(scale,child))
            val changed = ColorMatrixF32.of(marker.toFloatArray()).apply { postTranslate(.5f,0f,0f,0f) }
            val mutated = ColorFilter.Compose(scale,ColorFilter.Compose(ColorFilter.Matrix(changed),child))
            fun expected(f: ColorFilter) = try {
                W5fColorCpuOracle.expectedShaderSource(ColorARGB.of(255,255,0,0),alpha,1f,null,f,destination,BlendMode.SRC)
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("kind=$kind lane=$lane alpha=$alpha filter=$f",error)
            }
            val expected = expected(filter)
            disjoint(expected,expected(reversed)); disjoint(expected,expected(mutated))
            val surface = Surface(1,1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = destination,antiAlias = false))
                val paint = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.of(255,255,0,0)),alpha),
                    colorFilter = filter,blendMode = BlendMode.SRC,antiAlias = false)
                if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            }
            marker.postTranslate(.5f,0f,0f,0f)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
        // Destination dependence is observed for every kind/mode and each lane.
        // Filter red remains observable in green; stable other channels constrain
        // the independently decoded translucent destination's full uncertainty.
        val projection = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,1f, .125f,0f,0f,0f,.125f, 0f,0f,0f,0f,.125f, 0f,0f,0f,0f,1f)))
        val finalFilter = ColorFilter.Compose(projection,kind)
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.of(255,255,0,0),finalFilter,destination,BlendMode.DIFFERENCE,destinationBlend = BlendMode.SRC)
        disjoint(expected,W5fColorCpuOracle.expectedPaintSource(ColorARGB.of(255,255,0,0),finalFilter,destination,BlendMode.SRC))
        for (lane in 0..2) {
            val surface = Surface(1,1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = destination,blendMode = BlendMode.SRC,antiAlias = false))
                val paint = Paint(color = ColorARGB.of(255,255,0,0),colorFilter = finalFilter,blendMode = BlendMode.DIFFERENCE,antiAlias = false)
                if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
    }
    private fun tablePayloadAndRoundedSelection() {
        for (lane in 0..2) for (alpha in listOf(0f,1f,.5f)) {
            val payload = UByteArray(256) { 64u }
            val filter = ColorFilter.Table(payload)
            val expected = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black,alpha,1f,null,filter,finalBlend = BlendMode.SRC)
            val mutated = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black,alpha,1f,null,ColorFilter.Table(UByteArray(256)),finalBlend = BlendMode.SRC)
            disjoint(expected,mutated)
            val surface = Surface(1,1)
            surface.canvas {
                val paint = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Black),alpha),colorFilter = filter,blendMode = BlendMode.SRC,antiAlias = false)
                if (lane == 0) drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint) else drawPath(path(lane),paint)
            }
            payload.fill(0u)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
        val midpoint = (128f+.5f)/255f
        val table = UByteArray(256) { it.toUByte() }.apply { this[255] = 64u }
        for (value in listOf(Math.nextDown(midpoint),midpoint,Math.nextUp(midpoint),.25f)) for (lane in 0..2) {
            val payload = table.copyOf().apply { this[64] = 96u }
            renderFilter(ColorFilter.Compose(ColorFilter.Table(payload),ColorFilter.Matrix(constantInput(value,0f,0f,1f))),lane)
        }
        // FF FF FF FF, 01 00 00 00 and a NaN-looking mixed word exercise genuine
        // U32 upload/extraction. No byte/handle/private-plan assertion participates.
        for (pattern in listOf(listOf(255,255,255,255),listOf(1,0,0,0),listOf(69,35,193,127))) for (lane in 0..2) {
            val payload = UByteArray(256).apply { pattern.forEachIndexed { i,v -> this[i] = v.toUByte() }; this[255] = 64u }
            renderFilter(ColorFilter.Compose(ColorFilter.Table(payload),ColorFilter.Matrix(constantInput(0f,1f/255f,2f/255f,1f))),lane)
        }
    }
    private fun primitiveRectPath(filter: ColorFilter) {
        val source = ColorARGB.of(255,255,0,0)
        for (path in listOf(false,true)) {
            val expected = W5fColorCpuOracle.expectedPaintSource(source,filter,finalBlend = BlendMode.SRC)
            W5fSurfacePixelFixtures.requireBounded(expected)
            val surface = Surface(1,1)
            surface.canvas {
                val paint = Paint(color = source,colorFilter = filter,blendMode = BlendMode.SRC,antiAlias = false)
                if (path) drawPath(Path().apply { moveTo(-10f,-10f); lineTo(20f,-10f); lineTo(-10f,20f); close() },paint)
                else drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
            }
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected)) }
        }
    }
    @Test fun tableInvalidLengthsRefuseBeforeCaptureAndRecover() {
        val healthy = ColorFilter.Matrix(ColorMatrixF32.ofIdentity())
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,healthy,finalBlend = BlendMode.SRC)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        for (size in listOf(255,257)) {
            val error = assertFailsWith<IllegalArgumentException> { surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(colorFilter = ColorFilter.Table(UByteArray(size)),antiAlias = false))
            } }
            assertTrue(error.message.orEmpty().contains("invalid.material.filter.table"))
            surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = ColorARGB.Black,
                colorFilter = healthy,blendMode = BlendMode.SRC,antiAlias = false)) }
            W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected))
        }
    }
    @Test fun matrixFractionalRectCoverage() {
        val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            1f,0f,0f,0f,1f, 0f,1f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)))
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,filter,coverageF32 = 0.5f)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(2,1)
        surface.canvas { drawRect(RectF32.ofLTRB(0.5f,0f,1.5f,1f),
            Paint(color = ColorARGB.Black,colorFilter = filter,antiAlias = true)) }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected,expected))
    }
    @Test fun matrixRectDestinationReadDifference() {
        val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            1f,0f,0f,0f,1f, 0f,1f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)))
        val destination = ColorARGB.of(255,0,0,255)
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,filter,destination,BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = destination,antiAlias = false))
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = ColorARGB.Black,colorFilter = filter,
                blendMode = BlendMode.DIFFERENCE,antiAlias = false))
        }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected))
    }
    @Test fun matrixNonFiniteRecordingRecoversOnSameSurface() {
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,
            ColorFilter.Matrix(ColorMatrixF32.ofIdentity()))
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1, 1)
        val invalid = ColorMatrixF32.ofIdentity().apply { postTranslate(Float.NaN, 0f, 0f, 0f) }
        val error = assertFailsWith<IllegalArgumentException> {
            surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),
                Paint(colorFilter = ColorFilter.Matrix(invalid), antiAlias = false)) }
        }
        assertTrue(error.message.orEmpty().contains("non-finite-value"))
        surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f), Paint(color = ColorARGB.Black, antiAlias = false)) }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected))
    }
    @Test fun matrixTranslationUsesNormalizedUnitsAndIsPlanOwned() {
        val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            1f,0f,0f,0f,1f, 0f,1f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,1f,0f)))
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black, filter)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1, 1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),
            Paint(color = ColorARGB.Black, colorFilter = filter, antiAlias = false)) }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected))
    }

    @Test fun matrixMixesChannelsAndClampsBeforePremultiplying() {
        val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0.25f,0.125f,0f,0.125f, 0f,2f,0f,0f,0.5f,
            -2f,0f,0f,0f,-0.25f, 0f,0f,0f,0f,1f)))
        val color = ColorARGB.White
        val expected = W5fColorCpuOracle.expectedPaintSource(color, filter)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1, 1)
        surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f),
            Paint(color = color, colorFilter = filter, antiAlias = false)) }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected))
    }

    @Test fun matrixRectAlphaMutationAndFinalBlend() {
        val matrix = ColorMatrixF32.of(floatArrayOf(
            0f,0f,1f,0f,0f, 1f,0f,0f,0f,0f,
            0f,1f,0f,0f,0f, 0f,0f,0f,0f,0.5f))
        val filter = ColorFilter.Matrix(matrix)
        val color = ColorARGB.of(255,255,0,0)
        val destination = ColorARGB.of(253,0,0,255)
        val alphas = listOf(0f, 1f, 0.5f)
        val expected = alphas.map { W5fColorCpuOracle.expectedShaderSource(color, it, 1f,
            null, filter, destination, BlendMode.SRC) }
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        val surface = Surface(3, 1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,3f,1f), Paint(color = destination, antiAlias = false))
            alphas.forEachIndexed { x, alpha -> drawRect(RectF32.ofLTRB(x.toFloat(),0f,x+1f,1f),
                Paint(shader = Shader.Opacity(Shader.SolidColor(color), alpha), colorFilter = filter,
                    blendMode = BlendMode.SRC, antiAlias = false)) }
        }
        matrix.setIdentity()
        repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), expected) }
    }
}
