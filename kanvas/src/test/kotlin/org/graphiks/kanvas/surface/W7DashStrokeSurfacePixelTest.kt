@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.surface

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.math.color.ColorARGB
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/** Public Surface witness that independently constructed dash values survive immutable snapshots. */
class W7DashStrokeSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            // GLFW/AppKit teardown must run on the test's first thread, not a JVM shutdown hook.
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun `dash phases and intervals retain their literal hard pixel matrices`() {
        // These x positions are hand-derived before any Surface exists. At y=2 on a 12x5
        // transparent Surface, every position not named below must remain transparent.
        val cases = listOf(
            DashCase(floatArrayOf(2f, 2f), -1f, listOf(2, 3, 6, 7, 10)),
            DashCase(floatArrayOf(2f, 2f), 1f, listOf(1, 4, 5, 8, 9)),
            DashCase(floatArrayOf(1f, 3f), -1f, listOf(2, 6, 10)),
        )
        val red = ColorARGB.Red
        val path = Path().apply {
            moveTo(1f, 2.5f)
            lineTo(11f, 2.5f)
        }

        cases.forEach { case ->
            // Each paint and its Dash object are freshly constructed, independently of the
            // snapshot constructed by the native render path.
            val paint = Paint.stroke(red, width = 1f).copy(
                strokeCap = StrokeCap.BUTT,
                pathEffect = PathEffect.Dash(case.intervals.copyOf(), phase = case.phase),
                antiAlias = false,
            )
            val surface = Surface(12, 5)
            surface.canvas { drawPath(path, paint) }

            val result = surface.render()

            assertTrue(
                result.nativeEvidenceScopeKinds.containsAll(listOf("Render", "Readback")),
                result.nativeEvidenceScopeKinds.toString(),
            )
            for (yI32 in 0 until 5) {
                for (xI32 in 0 until 12) {
                    val offsetI32 = (yI32 * 12 + xI32) * 4
                    val isRed = yI32 == 2 && xI32 in case.redXsI32
                    assertEquals(if (isRed) 255.toUByte() else 0.toUByte(), result.pixels[offsetI32], "red at ($xI32, $yI32), ${case.label}")
                    assertEquals(0.toUByte(), result.pixels[offsetI32 + 1], "green at ($xI32, $yI32), ${case.label}")
                    assertEquals(0.toUByte(), result.pixels[offsetI32 + 2], "blue at ($xI32, $yI32), ${case.label}")
                    assertEquals(if (isRed) 255.toUByte() else 0.toUByte(), result.pixels[offsetI32 + 3], "alpha at ($xI32, $yI32), ${case.label}")
                }
            }
        }
    }

    private data class DashCase(
        val intervals: FloatArray,
        val phase: Float,
        val redXsI32: List<Int>,
    ) {
        val label: String = "${intervals.joinToString(prefix = "[", postfix = "]")}, phase=$phase"
    }
}
