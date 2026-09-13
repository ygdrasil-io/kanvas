package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.color.ColorMatrixF32
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class W5fColorFilterSurfacePixelTest {
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
