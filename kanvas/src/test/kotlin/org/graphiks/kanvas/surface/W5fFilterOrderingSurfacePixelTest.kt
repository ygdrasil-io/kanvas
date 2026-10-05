package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.geometry.Path
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class W5fFilterOrderingSurfacePixelTest {
    private fun translate() = ColorMatrixF32.ofIdentity().apply { postTranslate(.125f, 2f, -.25f, 0f) }
    private fun scale() = ColorMatrixF32.ofIdentity().apply { setScale(.5f, 1f, 1f, 1f) }

    private fun disjoint(a: WgslFloatEnvelopeV1Oracle.DrawResult, b: WgslFloatEnvelopeV1Oracle.DrawResult) {
        W5fSurfacePixelFixtures.requireBounded(a)
        W5fSurfacePixelFixtures.requireBounded(b)
        a as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        b as WgslFloatEnvelopeV1Oracle.DrawResult.Bounded
        assertTrue(a.channels.indices.any { a.channels[it].intersect(b.channels[it]).isEmpty() },
            "A counterfactual must be observably distinct: ${a.channels} / ${b.channels}")
    }

    @Test fun compositeFilterBudgetUsesUniqueWholeFrameSourcesAndRecovers() {
        fun constant(redF32: Float) = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,redF32, 0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
        fun filter(indexI32: Int, expanded: Boolean): ColorFilter {
            var value: ColorFilter = constant(2f+indexI32/128f)
            if (expanded) repeat(3) { value = ColorFilter.Compose(constant(2f),value) }
            return value
        }
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,filter(63,true))
        W5fSurfacePixelFixtures.requireBounded(expected)
        // Actual alternating geometry raises this configured budget above resident
        // staging. Only distinct expanded filter payloads cross the causal window.
        fun frame(budgetI64: Long, mode: BlendMode, expanded: Boolean = true, distinct: Boolean = true) =
            Surface(29,1,config = RenderConfig(frameLocalBudgetBytes = budgetI64)).also { surface ->
                surface.canvas { repeat(64) { indexI32 ->
                    val paint = Paint(color = ColorARGB.Black,colorFilter = filter(if (distinct) indexI32 else 0,expanded),
                        blendMode = mode,antiAlias = false)
                    if (indexI32 % 2 == 0) drawRect(RectF32.ofLTRB(0f,0f,29f,1f),paint)
                    else drawPath(Path().apply { moveTo(-10f,-10f); lineTo(80f,-10f); lineTo(-10f,80f); close() },paint)
                } }
            }
        for (mode in listOf(BlendMode.SRC_OVER,BlendMode.SRC)) {
            val healthy = frame(1L shl 23,mode)
            fun check(surface: Surface) = W5fSurfacePixelFixtures.assertNativePixels(surface.render(),List(29) { expected })
            check(healthy)
            check(frame(1_585_000L,mode,expanded = false))
            check(frame(1_585_000L,mode,distinct = false))
            repeat(2) {
                val failure = assertFailsWith<IllegalStateException> { frame(1_585_000L,mode).render() }
                assertEquals("budget.w5f.filter-uniform",failure.message.orEmpty().substringBefore(':'),failure.message)
                check(healthy)
            }
        }
    }

    @Test fun nestedFiltersPreserveBothOpacityOrdersAndDirectPathSources() {
        val restoring = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,2f, 0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
        val source = Shader.SolidColor(ColorARGB.Black)
        for (alphaF32 in listOf(0f,.5f,1f)) {
            val before = Shader.WithColorFilter(Shader.Opacity(source,alphaF32),restoring)
            val after = Shader.Opacity(Shader.WithColorFilter(source,restoring),alphaF32)
            val nested = Shader.WithColorFilter(Shader.Opacity(
                Shader.WithColorFilter(Shader.Opacity(source,0f),restoring),.5f),
                ColorFilter.Compose(ColorFilter.Matrix(scale()),ColorFilter.Matrix(translate())))
            val expected = listOf(before,after,nested).map { W5fColorCpuOracle.expectedShaderTree(it) }
            expected.forEach(W5fSurfacePixelFixtures::requireBounded)
            if (alphaF32 != 1f) disjoint(expected[0],expected[1])
            listOf(before,after,nested).forEachIndexed { indexI32, shader ->
                val surface = Surface(1,1)
                surface.canvas {
                    drawPath(Path().apply { moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close() },
                        Paint(shader = shader,blendMode = BlendMode.SRC,antiAlias = false))
                }
                W5fSurfacePixelFixtures.assertNativePixels(surface.render(),listOf(expected[indexI32]))
            }
        }
    }

    @Test fun ordinaryCompositeRetainsFilteredStencilAndColoredDestination() {
        val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,2f, 0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,filter,ColorARGB.Blue)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(4,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,4f,1f),Paint(color = ColorARGB.Blue,antiAlias = false))
            drawPath(Path().apply { addRect(RectF32.ofLTRB(-10f,-10f,40f,40f)) },
                Paint(color = ColorARGB.Black,colorFilter = filter,antiAlias = false))
        }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(),List(4) { expected })
    }

    @Test fun everyOrderedFilterRetainsRectAndPathDestinationRead() {
        val inner = ColorFilter.Matrix(translate())
        val outer = ColorFilter.Matrix(scale())
        val ordered = ColorFilter.Compose(outer,inner)
        val filters = listOf(inner,ordered,ColorFilter.Lerp(.5f,ordered,ColorFilter.Compose(inner,outer)))
        val expected = filters.map { W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black,it,ColorARGB.Blue,BlendMode.DIFFERENCE) }
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        for (path in listOf(false,true)) filters.forEachIndexed { indexI32, filter ->
            val surface = Surface(1,1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = ColorARGB.Blue,antiAlias = false))
                val paint = Paint(color = ColorARGB.Black,colorFilter = filter,blendMode = BlendMode.DIFFERENCE,antiAlias = false)
                if (path) drawPath(Path().apply { addRect(RectF32.ofLTRB(-10f,-10f,40f,40f)) },paint)
                else drawRect(RectF32.ofLTRB(0f,0f,1f,1f),paint)
            }
            val rendered = try { surface.render() } catch (failure: IllegalStateException) {
                throw AssertionError("Destination-read fixture path=$path filter=$indexI32",failure)
            }
            W5fSurfacePixelFixtures.assertNativePixels(rendered,listOf(expected[indexI32]))
        }
    }

    // Reversing Compose or feeding Lerp's second branch the first branch's output
    // changes the independently derived red channel; alpha remains nontrivial.
    @Test fun composeAndLerpRectAlphaMutationAndFinalBlend() = filterCells(path = false)
    @Test fun matrixComposeAndLerpPathAlphaMutationAndFinalBlend() = filterCells(path = true)

    private fun filterCells(path: Boolean) {
        for (kindI32 in 0..4) for (alphaF32 in listOf(0f, 1f, .5f)) {
            val mutable = translate()
            val inner = ColorFilter.Matrix(mutable)
            val outer = ColorFilter.Matrix(scale())
            val ordered = ColorFilter.Compose(outer, inner)
            val reversed = ColorFilter.Compose(inner, outer)
            val filter = when (kindI32) {
                0 -> inner
                1 -> ordered
                else -> ColorFilter.Lerp(listOf(0f, 1f, .5f)[kindI32 - 2], ordered, reversed)
            }
            val destination = ColorARGB.of(253, 0, 0, 255)
            val expected = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black, alphaF32, 1f,
                null, filter, destination, BlendMode.SRC)
            W5fSurfacePixelFixtures.requireBounded(expected)
            if (kindI32 == 1 && alphaF32 > 0f) disjoint(expected,
                W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black,alphaF32,1f,null,reversed,destination,BlendMode.SRC))
            if (kindI32 == 4 && alphaF32 > 0f) disjoint(expected,
                W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black,alphaF32,1f,null,
                    ColorFilter.Lerp(.5f,ordered,ColorFilter.Compose(reversed,ordered)),destination,BlendMode.SRC))
            mutable.postTranslate(.5f, 0f, 0f, .5f)
            val mutated = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black, alphaF32, 1f,
                null, filter, destination, BlendMode.SRC)
            W5fSurfacePixelFixtures.requireBounded(mutated)
            disjoint(expected, mutated)
            mutable.postTranslate(-.5f, 0f, 0f, -.5f)
            val surface = Surface(1, 1)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f), Paint(color = destination, antiAlias = false))
                val paint = Paint(shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Black), alphaF32),
                    colorFilter = filter, blendMode = BlendMode.SRC, antiAlias = false)
                if (path) drawPath(Path().apply {
                    moveTo(-1f,-1f); lineTo(5f,-1f); lineTo(5f,5f); lineTo(2f,2f); lineTo(-1f,5f); close()
                }, paint) else drawRect(RectF32.ofLTRB(0f,0f,1f,1f), paint)
            }
            mutable.postTranslate(.5f, 0f, 0f, .5f)
            repeat(2) { W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected)) }
        }
    }

    @Test fun paintAlphaPrecedesExternalFilterAndZeroOpacityCanRevive() {
        val restoring = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            1f,0f,0f,0f,1f, 0f,1f,0f,0f,0f,
            0f,0f,1f,0f,0f, 0f,0f,0f,0f,.5f)))
        val opaque = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,1f, 0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
        val revived = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black, 0f, 1f, null, opaque)
        W5fSurfacePixelFixtures.requireBounded(revived)
        for (alphaF32 in listOf(0f, .5f, 1f)) {
            val external = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black, alphaF32, 191/255f, null, restoring,
                finalBlend = BlendMode.SRC)
            val internal = W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black, alphaF32, 191/255f, restoring, null,
                finalBlend = BlendMode.SRC)
            listOf(external, internal).forEach(W5fSurfacePixelFixtures::requireBounded)
            disjoint(external, internal)
            val surface = Surface(3,1)
            surface.canvas {
                val source = Shader.Opacity(Shader.SolidColor(ColorARGB.Black), alphaF32)
                drawRect(RectF32.ofLTRB(0f,0f,1f,1f), Paint(shader = source,
                    color = ColorARGB.of(191,0,0,0), colorFilter = restoring, blendMode = BlendMode.SRC, antiAlias = false))
                drawRect(RectF32.ofLTRB(1f,0f,2f,1f), Paint(shader = Shader.WithColorFilter(source, restoring),
                    color = ColorARGB.of(191,0,0,0), blendMode = BlendMode.SRC, antiAlias = false))
                drawRect(RectF32.ofLTRB(2f,0f,3f,1f), Paint(
                    shader = Shader.Opacity(Shader.SolidColor(ColorARGB.Black), 0f),
                    colorFilter = opaque, antiAlias = false))
            }
            W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(external, internal, revived))
        }
    }

    @Test fun zeroPaintAlphaAndColorAlphaDoNotElideRestoringFilter() {
        val filter = ColorFilter.Matrix(ColorMatrixF32.of(floatArrayOf(
            0f,0f,0f,0f,2f, 0f,0f,0f,0f,0f,
            0f,0f,0f,0f,0f, 0f,0f,0f,0f,1f)))
        val expected = listOf(
            W5fColorCpuOracle.expectedShaderSource(ColorARGB.Black,1f,0f,null,filter),
            W5fColorCpuOracle.expectedPaintSource(ColorARGB.Transparent,filter))
        expected.forEach(W5fSurfacePixelFixtures::requireBounded)
        val surface = Surface(2,1)
        surface.canvas {
            drawRect(RectF32.ofLTRB(0f,0f,1f,1f),Paint(color = ColorARGB.Transparent,
                shader = Shader.SolidColor(ColorARGB.Black),colorFilter = filter,antiAlias = false))
            drawRect(RectF32.ofLTRB(1f,0f,2f,1f),Paint(color = ColorARGB.Transparent,colorFilter = filter,antiAlias = false))
        }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(),expected)
    }

    @Test fun mixedRectGeneralDirectAndStencilRetainFiltersAndDestinationRead() {
        val filter = ColorFilter.Compose(ColorFilter.Matrix(scale()), ColorFilter.Matrix(translate()))
        val destination = ColorARGB.of(255,0,0,255)
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black, filter, destination, BlendMode.DIFFERENCE)
        W5fSurfacePixelFixtures.requireBounded(expected)
        for (stencil in listOf(false,true)) {
            val surface = Surface(18,2)
            surface.canvas {
                drawRect(RectF32.ofLTRB(0f,0f,18f,2f), Paint(color = destination, antiAlias = false))
                rotate(.25f, px = 2f, py = 2f)
                val path = Path().apply {
                    if (stencil) addRect(RectF32.ofLTRB(-10f,-10f,40f,40f))
                    else { moveTo(-10f,-10f); lineTo(40f,-10f); lineTo(-10f,40f); close() }
                }
                drawPath(path, Paint(color = ColorARGB.Black, colorFilter = filter,
                    blendMode = BlendMode.DIFFERENCE, antiAlias = false))
            }
            W5fSurfacePixelFixtures.assertNativePixels(surface.render(), List(36) { expected })
        }
    }

    @Test fun invalidLerpRecordingRecoversOnSameSurface() {
        val filter = ColorFilter.Matrix(translate())
        val expected = W5fColorCpuOracle.expectedPaintSource(ColorARGB.Black, filter)
        W5fSurfacePixelFixtures.requireBounded(expected)
        val surface = Surface(1,1)
        for (tF32 in listOf(Float.NaN, Float.POSITIVE_INFINITY, -.25f, 1.25f)) {
            val error = assertFailsWith<IllegalArgumentException> {
                surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f), Paint(
                    colorFilter = ColorFilter.Lerp(tF32, filter, filter), antiAlias = false)) }
            }
            assertTrue(error.message.orEmpty().contains(if (tF32.isFinite())
                "invalid.material.filter.lerp" else "non-finite-value"))
        }
        surface.canvas { drawRect(RectF32.ofLTRB(0f,0f,1f,1f), Paint(color = ColorARGB.Black,
            colorFilter = filter, antiAlias = false)) }
        W5fSurfacePixelFixtures.assertNativePixels(surface.render(), listOf(expected))
    }
}
