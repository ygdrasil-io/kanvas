@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Public Render+Readback witness for equivalent proof environments. */
class W7ProofContextSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // GLFW/AppKit teardown must run on the test's first thread, not a JVM shutdown hook.
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun twentyCoordinatePairsPreserveSweepPixels() {
        val bounds = RectF32.ofLTRB(0f, 0f, 17f, 1f)
        val red = UByteArray(17 * 4) { channel -> if (channel % 4 == 0 || channel % 4 == 3) 255u else 0u }

        fun source(mode: TileMode): Shader {
            var shader: Shader = Shader.SweepGradient(Point2F32(0f, 0f), 0f, 360f,
                listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Red)), tileMode = mode)
            repeat(20) { shader = Shader.WithLocalMatrix(Shader.CoordClamp(shader, bounds), Matrix3x3F32()) }
            return shader
        }
        for (mode in TileMode.entries) {
            val surface = Surface(17, 1).also { frame -> frame.canvas {
                drawRect(bounds, Paint(shader = source(mode), antiAlias = false))
            } }
            val first = surface.render()
            assertContentEquals(red, first.pixels, mode.toString())
            assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "$mode: ${first.nativeEvidenceScopeKinds}")
            assertContentEquals(red, surface.render().pixels, "$mode replay")
        }
    }

    @Test
    fun deepCoordinateOrderKeepsDistinctColors() {
        val output = RectF32.ofLTRB(0f, 0f, 4f, 1f)
        val initialClamp = RectF32.ofLTRB(0f, 0f, 3f, 1f)
        val leaf = Shader.LinearGradient(Point2F32(0f, 0f), Point2F32(8f, 0f), listOf(
            GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue),
        ), tileMode = TileMode.CLAMP)
        val red = ubyteArrayOf(255u, 0u, 0u, 255u, 255u, 0u, 0u, 255u,
            255u, 0u, 0u, 255u, 255u, 0u, 0u, 255u)
        val redRedBlueBlue = ubyteArrayOf(255u, 0u, 0u, 255u, 255u, 0u, 0u, 255u,
            0u, 0u, 255u, 255u, 0u, 0u, 255u, 255u)

        fun deepen(shader: Shader): Shader {
            var wrapped = shader
            repeat(20) { wrapped = Shader.WithLocalMatrix(Shader.CoordClamp(wrapped, output), Matrix3x3F32()) }
            return wrapped
        }
        fun surface(shader: Shader) = Surface(4, 1).also { surface -> surface.canvas {
            drawRect(output, Paint(shader = shader, antiAlias = false))
        } }
        fun assertRendered(shader: Shader, expected: UByteArray) {
            val frame = surface(shader)
            val first = frame.render()
            assertContentEquals(expected, first.pixels)
            assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                first.nativeEvidenceScopeKinds.toString())
            assertContentEquals(expected, frame.render().pixels)
        }

        assertRendered(deepen(Shader.WithLocalMatrix(Shader.CoordClamp(leaf, initialClamp),
            Matrix3x3F32.translation(-2f, 0f))), red)
        assertRendered(deepen(Shader.CoordClamp(Shader.WithLocalMatrix(leaf,
            Matrix3x3F32.translation(-2f, 0f)), initialClamp)), redRedBlueBlue)
    }

    @Test
    fun overflowingAaRRectWithNonuniformSweepRetainsAxisColors() {
        val surface = Surface(17, 1).also { frame -> frame.canvas {
            drawRRect(RRectF32.of(RectF32.ofLTRB(-1f, -1f, 18f, 2f), CornerRadiiF32.of(.5f)),
                Paint(shader = axisSweep(), antiAlias = true))
        } }
        val expected = axisPixels()
        val first = surface.render()
        assertContentEquals(expected, first.pixels)
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            first.nativeEvidenceScopeKinds.toString())
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun overflowingPathStrokeWithNonuniformSweepRetainsAxisColors() {
        val surface = Surface(17, 1).also { frame -> frame.canvas {
            drawPath(Path().apply { moveTo(-1f, .5f); lineTo(18f, .5f) },
                Paint(shader = axisSweep(), style = PaintStyle.STROKE, strokeWidth = 4f, antiAlias = false))
        } }
        val expected = axisPixels()
        val first = surface.render()
        assertContentEquals(expected, first.pixels)
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            first.nativeEvidenceScopeKinds.toString())
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun sweepQuadrantsAxesAndCutStayInTheirLiteralBands() {
        val bands = listOf(
            GradientStop(0f, ColorARGB.Red), GradientStop(.25f, ColorARGB.Red),
            GradientStop(.25f, ColorARGB.Green), GradientStop(.5f, ColorARGB.Green),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(.75f, ColorARGB.Blue),
            GradientStop(.75f, ColorARGB.White), GradientStop(1f, ColorARGB.White),
        )
        var shader: Shader = Shader.SweepGradient(Point2F32(1.5f, 1.5f), 0f, 360f, bands)
        repeat(20) { shader = Shader.WithLocalMatrix(Shader.CoordClamp(shader,
            RectF32.ofLTRB(0f, 0f, 3f, 3f)), Matrix3x3F32()) }
        val surface = Surface(3, 3).also { frame -> frame.canvas {
            drawRect(RectF32.ofLTRB(0f, 0f, 3f, 3f), Paint(shader = shader, antiAlias = false))
        } }
        fun pixel(color: ColorARGB) = ubyteArrayOf(color.red.toUByte(), color.green.toUByte(),
            color.blue.toUByte(), color.alpha.toUByte())
        val expected = listOf(ColorARGB.Blue, ColorARGB.White, ColorARGB.White,
            ColorARGB.Blue, ColorARGB.Red, ColorARGB.Red,
            ColorARGB.Green, ColorARGB.Green, ColorARGB.Red).flatMap(::pixel).toUByteArray()
        val first = surface.render()
        assertContentEquals(expected, first.pixels)
        assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
            first.nativeEvidenceScopeKinds.toString())
        assertContentEquals(expected, surface.render().pixels)
    }

    @Test
    fun sweepRefusalIsPreciseAndHealthyRRectRecovers() {
        val refused = Surface(17, 1).also { frame -> frame.canvas {
            drawRRect(RRectF32.of(RectF32.ofLTRB(-1f, -1f, 18f, 2f), CornerRadiiF32.of(.5f)),
                Paint(shader = Shader.WithLocalMatrix(axisSweep(), Matrix3x3F32(tx = Float.NaN)), antiAlias = true))
        } }
        val failure = assertThrows<IllegalStateException> { refused.render() }
        assertEquals("unsupported.material.gradient.local-matrix-non-finite", failure.message.orEmpty().substringBefore(':'))
        val healthy = Surface(17, 1).also { frame -> frame.canvas {
            drawRRect(RRectF32.of(RectF32.ofLTRB(-1f, -1f, 18f, 2f), CornerRadiiF32.of(.5f)),
                Paint(shader = axisSweep(), antiAlias = true))
        } }
        assertContentEquals(axisPixels(), healthy.render().pixels)
    }

    @Test
    fun sweepBoundaryLocalTransformsRefusePreciselyAndHealthyRRectRecovers() {
        val rrect = RRectF32.of(RectF32.ofLTRB(-1f, -1f, 18f, 2f), CornerRadiiF32.of(.5f))
        for ((matrix, expected) in listOf(
            Matrix3x3F32.scaling(Float.MIN_VALUE, 1f) to
                "unsupported.material.gradient.local-matrix-unrepresentable",
            Matrix3x3F32.scaling(java.lang.Float.MIN_NORMAL, 1f) to
                "unsupported.material.composed.numeric-domain-unbounded",
        )) {
            val refused = Surface(17, 1).also { frame -> frame.canvas {
                drawRRect(rrect, Paint(shader = Shader.WithLocalMatrix(axisSweep(), matrix), antiAlias = true))
            } }
            val failure = assertThrows<IllegalStateException> { refused.render() }
            assertEquals(expected, failure.message.orEmpty().substringBefore(':'))
        }
        val healthy = Surface(17, 1).also { frame -> frame.canvas {
            drawRRect(rrect, Paint(shader = axisSweep(), antiAlias = true))
        } }
        assertContentEquals(axisPixels(), healthy.render().pixels)
    }

    @Test
    fun pointClampsExerciseSweepZeroSubnormalAndNormalCoordinates() {
        val expected = ubyteArrayOf(255u, 0u, 0u, 255u)
        for (point in listOf(0f, Float.MIN_VALUE, java.lang.Float.MIN_NORMAL)) {
            val shader = Shader.CoordClamp(Shader.SweepGradient(Point2F32(0f, 0f), 0f, 360f, listOf(
                GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
                GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue),
            ), tileMode = TileMode.CLAMP), RectF32.ofLTRB(point, point, point, point))
            val surface = Surface(1, 1).also { frame -> frame.canvas {
                drawRRect(RRectF32.of(RectF32.ofLTRB(-1f, -1f, 2f, 2f), CornerRadiiF32.of(.5f)),
                    Paint(shader = shader, antiAlias = true))
            } }
            val first = surface.render()
            assertContentEquals(expected, first.pixels, point.toString())
            assertTrue(first.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                "$point: ${first.nativeEvidenceScopeKinds}")
            assertContentEquals(expected, surface.render().pixels, "$point replay")
        }
    }

    private fun axisSweep(): Shader {
        return Shader.SweepGradient(Point2F32(8.5f, .5f), 0f, 360f, listOf(
            GradientStop(0f, ColorARGB.Red), GradientStop(.5f, ColorARGB.Red),
            GradientStop(.5f, ColorARGB.Blue), GradientStop(1f, ColorARGB.Blue),
        ), tileMode = TileMode.CLAMP)
    }

    private fun axisPixels(): UByteArray {
        val blue = ubyteArrayOf(0u, 0u, 255u, 255u)
        val red = ubyteArrayOf(255u, 0u, 0u, 255u)
        return UByteArray(17 * 4) { channel -> (if (channel / 4 < 8) blue else red)[channel % 4] }
    }


}
