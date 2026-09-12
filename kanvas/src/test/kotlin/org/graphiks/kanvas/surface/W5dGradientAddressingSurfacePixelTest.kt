@file:OptIn(kotlin.ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.picture.PictureRecorder
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals

class W5dGradientAddressingSurfacePixelTest {
    private val bounds = RectF32.ofLTRB(0f, 0f, 13f, 1f)
    private fun linearGradient(): Shader.LinearGradient = Shader.LinearGradient(
        Point2F32(0f, 0f), Point2F32(8f, 0f),
        List(17) { indexI32 -> GradientStop(indexI32 / 16f, if (indexI32 < 8) ColorARGB.Red else ColorARGB.Blue) },
        tileMode = TileMode.CLAMP,
    )

    @Test
    fun linearLocalMatricesPreserveNonCommutativeOrder() {
        // Swapping the segment product changes inverse x from y + 8 to y.
        val linear17 = linearGradient()
        val rotationF32 = Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f)
        val translationF32 = Matrix3x3F32.translation(0f, -8f)
        val outerThenInner = Shader.WithLocalMatrix(
            Shader.WithLocalMatrix(linear17, rotationF32), translationF32)
        val reversed = Shader.WithLocalMatrix(
            Shader.WithLocalMatrix(linear17, translationF32), rotationF32)
        val first = renderPixel(outerThenInner)
        val second = renderPixel(reversed)
        assertContentEquals(W5dGradientAddressingCpuOracle.bluePixel(), first)
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), second)
        assertNotEquals(first.toList(), second.toList())
    }

    @Test
    fun linearDistinctLocalMatrixValuesSurvivePictureCapture() {
        // Reusing topology with another immutable value must not replace the first Picture's uniforms.
        val firstMatrixF32 = Matrix3x3F32.translation(0f, -8f)
        val secondMatrixF32 = firstMatrixF32.copy(ty = 0f)
        val rotationF32 = Matrix3x3F32(sx = 0f, kx = -1f, ky = 1f, sy = 0f)
        val pictures = listOf(firstMatrixF32, secondMatrixF32).map { matrixF32 ->
            val recorder = PictureRecorder()
            recorder.beginRecording(bounds).drawRect(bounds, Paint(shader = Shader.WithLocalMatrix(
                Shader.WithLocalMatrix(linearGradient(), rotationF32), matrixF32), antiAlias = false))
            recorder.finishRecordingAsPicture()
        }
        for (indexI32 in listOf(0, 1, 0)) {
            val surface = Surface(1, 1)
            surface.canvas { pictures[indexI32].playback(this) }
            assertContentEquals(if (indexI32 == 0) W5dGradientAddressingCpuOracle.bluePixel()
                else W5dGradientAddressingCpuOracle.redPixel(), surface.render().pixels)
        }
    }

    @Test
    fun nonFiniteAndSingularLocalMatricesRefusePrecisely() {
        // Capture must preserve invalid admitted matrices until the W5d planner classifies them.
        val cases = listOf(
            Matrix3x3F32(tx = Float.NaN) to "unsupported.material.gradient.local-matrix-non-finite",
            Matrix3x3F32(ky = Float.POSITIVE_INFINITY) to "unsupported.material.gradient.local-matrix-non-finite",
            Matrix3x3F32.scaling(0f, 1f) to "unsupported.material.gradient.local-matrix-singular",
        )
        for ((matrixF32, code) in cases) assertRefusesThenRecovers(matrixF32, code)
    }

    @Test
    fun unrepresentableInverseRefusesPrecisely() {
        // The F64 inverse exists, but its F32 coefficient exceeds Float.MAX_VALUE.
        assertRefusesThenRecovers(Matrix3x3F32.scaling(Float.MIN_VALUE, 1f),
            "unsupported.material.gradient.local-matrix-unrepresentable")
    }

    private fun assertRefusesThenRecovers(matrixF32: Matrix3x3F32, code: String) {
        // Establish the eligible public runtime before refusal; never dispose it between draws.
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
        val failure = assertThrows<IllegalStateException> {
            renderPixel(Shader.WithLocalMatrix(Shader.WithLocalMatrix(linearGradient(), matrixF32),
                Matrix3x3F32.translation(1f, 0f)))
        }
        assertEquals(code, failure.message.orEmpty().substringBefore(':'))
        assertContentEquals(W5dGradientAddressingCpuOracle.redPixel(), renderPixel(linearGradient()))
    }

    private fun renderPixel(shader: Shader): UByteArray {
        val surface = Surface(1, 1)
        surface.canvas { drawRect(bounds, Paint(shader = shader, antiAlias = false)) }
        return surface.render().pixels
    }

    @Test
    fun linearClampWithAffineLocalMatrixIsPlanOwned() {
        // Ignoring the local matrix or either opacity changes these public pixels.
        val leaf = linearGradient()
        val shader = Shader.Opacity(Shader.WithLocalMatrix(
            Shader.Opacity(leaf, .75f), Matrix3x3F32.translation(3f, 0f)), .5f)
        for (ctmScaleXF32 in listOf(1f, 2f)) for (antiAlias in listOf(false, true)) {
            val expected = listOf(0, 3, 5, 7, 10, 12).map { it * ctmScaleXF32.toInt() }
                .associateWith { W5dGradientAddressingCpuOracle.evaluate(it, leaf.stops, ctmScaleXF32) }
            val surface = Surface(13 * ctmScaleXF32.toInt(), 1)
            surface.canvas {
                scale(ctmScaleXF32, 1f)
                val rect = if (antiAlias) RectF32.ofLTRB(-.25f, -.25f, 13.25f, 1.25f) else bounds
                drawRect(rect, Paint(color = ColorARGB.of(191, 0, 0, 0), shader = shader, antiAlias = antiAlias))
            }
            val pixels = surface.render().pixels
            for ((pixelXI32, envelope) in expected) {
                WgslFloatEnvelopeV1Oracle.assertAdmits(envelope,
                    pixels.copyOfRange(pixelXI32 * 4, pixelXI32 * 4 + 4))
            }
        }
    }

    @Test
    fun unsupportedWrapperRemainsPreAdmission() {
        val surface = Surface(13, 1)
        val leaf = linearGradient().copy(stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        surface.canvas { drawRect(bounds, Paint(shader = Shader.WithColorFilter(leaf, ColorFilter.HighContrast), antiAlias = false)) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.w5a.kind", failure.message.orEmpty().substringBefore(':'))
    }

    @Test
    fun singularLocalMatrixRefusesAndRecoversOnTheSameRuntime() {
        val surface = Surface(13, 1)
        val leaf = linearGradient().copy(stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)))
        surface.canvas { drawRect(bounds, Paint(shader = Shader.WithLocalMatrix(leaf, Matrix3x3F32.scaling(0f, 1f)), antiAlias = false)) }
        val failure = assertThrows<IllegalStateException> { surface.render() }
        assertEquals("unsupported.material.gradient.local-matrix-singular", failure.message.orEmpty().substringBefore(':'))
        // No runtime disposal between refusal and a new public Surface.
        val recovery = Surface(1, 1)
        recovery.canvas { drawRect(RectF32.ofLTRB(0f, 0f, 1f, 1f), Paint(shader = linearGradient(), antiAlias = false)) }
        assertContentEquals(ubyteArrayOf(255u, 0u, 0u, 255u), recovery.render().pixels)
    }
}
