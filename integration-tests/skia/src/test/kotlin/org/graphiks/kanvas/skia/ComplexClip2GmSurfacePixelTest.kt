@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.skia.gm.clip.ComplexClip2RectAaGm
import org.graphiks.kanvas.skia.gm.clip.ComplexClip2RectGm
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Retained native-pixel witnesses for the two rectangular ComplexClip2 variants. */
class ComplexClip2GmSurfacePixelTest {
    companion object {
        private const val surfaceSize = 370

        private val localSamples = listOf(
            5 to 5,
            15 to 15,
            25 to 5,
            5 to 25,
            25 to 25,
            45 to 5,
            5 to 45,
            45 to 45,
            45 to 25,
        )

        // Literal row-major cells: P is the purple background; G is the green fill.
        // These 225 samples are independently derived from the five clip geometries and
        // the fixed 125-operation SkiaRandom stream, never from a rendered image or runtime RNG.
        private val expectedCells = listOf(
            "PPPPPPPGP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP", "PPPPPPPPP",
            "PPPPPPPPG", "PPPPPPPPP", "PPPPPGPPP", "PPPPPPPPP", "PPPPPPPPP",
            "GPPPPPPPP", "PPPPPGPPP", "PPPPGPPPP", "GPPPPPPPP", "PPPPPPPPP",
            "PPPPPPPPP", "PPPPPPPPP", "PGPPPPPPP", "PPPPPPGPP", "PGPPPPPPP",
            "PPPPPPGPP", "PPPPPPPPP", "PPPPPPPPP", "GPPPPPPPP", "PPPPPPPPP",
        )

        @AfterAll
        @JvmStatic
        fun disposeGpuBackend() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun rectVariantKeepsLiteralPixelsOnNativeSurface() {
        assertComplexClip2Pixels(ComplexClip2RectGm())
    }

    @Test
    fun rectAaVariantKeepsLiteralPixelsOnNativeSurface() {
        assertComplexClip2Pixels(ComplexClip2RectAaGm())
    }

    private fun assertComplexClip2Pixels(gm: SkiaGm) {
        val surface = Surface(surfaceSize, surfaceSize, config = gm.compositionConfig())
        val canvas = GmCanvas(surface.canvas(), surfaceSize, surfaceSize)
        gm.onOnceBeforeDraw(canvas)
        gm.draw(canvas, surfaceSize, surfaceSize)

        val first = surface.render()
        assertNative(first)
        for (row in 0 until 5) {
            for (col in 0 until 5) {
                val expected = expectedCells[row * 5 + col]
                assertEquals(9, expected.length, "expected sample count for cell ($col,$row)")
                for (sampleIndex in localSamples.indices) {
                    val (localX, localY) = localSamples[sampleIndex]
                    val x = 20 + 70 * col + localX
                    val y = 20 + 70 * row + localY
                    when (expected[sampleIndex]) {
                        'P' -> assertPixel(first, x, y, 221, 160, 221, 255)
                        'G' -> assertPixel(first, x, y, 160, 221, 160, 255)
                        else -> error("invalid literal cell pattern at ($col,$row)")
                    }
                }

                val originX = 20 + 70 * col
                val originY = 20 + 70 * row
                assertPixel(first, originX + 50, originY + 5, 0, 0, 255, 255)
                assertPixel(first, originX + 51, originY + 5, 221, 160, 221, 255)
            }
        }

        val second = surface.render()
        assertNative(second)
        assertTrue(first.pixels.contentEquals(second.pixels), "second retained ${gm.name} frame must be byte-identical")
    }

    private fun assertNative(result: RenderResult) {
        val trace = "diagnostics=${result.diagnostics.summary()} dispatched=${result.stats.opsDispatched} " +
            "refused=${result.stats.opsRefused} evidence=${result.nativeEvidenceScopeKinds}"
        assertTrue(result.isClean, trace)
        assertTrue(result.diagnostics.isEmpty, trace)
        assertEquals(0, result.stats.opsRefused, trace)
        assertTrue(result.stats.opsDispatched > 0, trace)
        assertTrue(result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")), trace)
    }

    private fun assertPixel(result: RenderResult, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
        val offset = (y * result.width + x) * 4
        assertArrayEquals(
            byteArrayOf(red.toByte(), green.toByte(), blue.toByte(), alpha.toByte()),
            result.pixels.copyOfRange(offset, offset + 4).toByteArray(),
            "pixel ($x,$y)",
        )
    }
}
