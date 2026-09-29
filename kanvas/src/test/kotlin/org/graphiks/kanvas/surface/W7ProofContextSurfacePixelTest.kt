@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

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


}
