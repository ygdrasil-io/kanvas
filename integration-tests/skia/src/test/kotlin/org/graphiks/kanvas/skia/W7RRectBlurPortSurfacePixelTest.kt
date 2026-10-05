@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.SceneRecordingScope
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.skia.gm.blur.RRectBlurGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.RRectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Native pixel witnesses for actual RRectBlur GM geometry and separators. */
class W7RRectBlurPortSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun disposeW7RRectBlurPortSurfacePixelTestGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    fun explicitCornerControlKeepsPerCornerApiSemantics() {
        val explicitSurface = Surface(100, 100)
        val explicitCanvas = explicitSurface.canvas()
        val explicit = RRectF32.of(
            RectF32(25f, 25f, 75f, 75f),
            topLeft = CornerRadiiF32.of(10f, 15f),
            topRight = CornerRadiiF32.of(10f, 15f),
            bottomRight = CornerRadiiF32.of(10f, 15f),
            bottomLeft = CornerRadiiF32.of(10f, 15f),
        )
        SceneRecordingScope.recordingOnly {
            explicitCanvas.clear(ColorARGB.Transparent)
            explicitCanvas.drawPath(
                Path { }.apply { addRRect(explicit) },
                Paint(color = ColorARGB.White, antiAlias = true),
            )
        }
        val explicitResult = explicitSurface.render()
        assertNativeWork(explicitResult, "explicit-corner API control")
        assertRgba(explicitResult, 27, 27, 0, 0, 0, 0)
        assertRgba(explicitResult, 72, 27, 0, 0, 0, 0)
        assertRgba(explicitResult, 27, 72, 0, 0, 0, 0)
        assertRgba(explicitResult, 72, 72, 0, 0, 0, 0)
        assertRgba(explicitResult, 50, 50, 255, 255, 255, 255)

        val oneCornerSurface = Surface(100, 100)
        val oneCornerCanvas = oneCornerSurface.canvas()
        val oneCorner = RRectF32.of(
            RectF32(25f, 25f, 75f, 75f),
            CornerRadiiF32.of(10f, 15f),
        )
        SceneRecordingScope.recordingOnly {
            oneCornerCanvas.clear(ColorARGB.Transparent)
            oneCornerCanvas.drawPath(
                Path { }.apply { addRRect(oneCorner) },
                Paint(color = ColorARGB.White, antiAlias = true),
            )
        }
        val oneCornerResult = oneCornerSurface.render()
        assertNativeWork(oneCornerResult, "single-corner API control")
        assertRgba(oneCornerResult, 27, 27, 0, 0, 0, 0)
        assertRgba(oneCornerResult, 72, 27, 255, 255, 255, 255)
        assertRgba(oneCornerResult, 27, 72, 255, 255, 255, 255)
        assertRgba(oneCornerResult, 72, 72, 255, 255, 255, 255)
        assertRgba(oneCornerResult, 50, 50, 255, 255, 255, 255)

        val asymmetricSurface = Surface(90, 90)
        val asymmetricCanvas = asymmetricSurface.canvas()
        val asymmetric = RRectF32.of(
            RectF32(0f, 0f, 90f, 90f),
            topLeft = CornerRadiiF32.Zero,
            topRight = CornerRadiiF32.of(20f, 1f),
            bottomRight = CornerRadiiF32.of(10f, 30f),
            bottomLeft = CornerRadiiF32.of(30f, 30f),
        )
        SceneRecordingScope.recordingOnly {
            asymmetricCanvas.clear(ColorARGB.Transparent)
            asymmetricCanvas.drawPath(
                Path { }.apply { addRRect(asymmetric) },
                Paint(color = ColorARGB.White, antiAlias = true),
            )
        }
        val asymmetricResult = asymmetricSurface.render()
        assertNativeWork(asymmetricResult, "asymmetric-corner API control")
        assertRgba(asymmetricResult, 5, 80, 0, 0, 0, 0)
        assertRgba(asymmetricResult, 84, 80, 255, 255, 255, 255)
    }

    @Test
    fun actualRrectBlurKeepsAllFourFirstRowCorners() {
        val gm = RRectBlurGm()
        val result = renderActualGm(gm)

        assertEquals("rrect_blurs", gm.name)
        assertEquals("rrect_blurs", gm.referenceName)
        assertEquals(300, gm.width)
        assertEquals(400, gm.height)
        assertEquals("LINEAR", gm.compositionDomain.name)
        assertNativeWork(result, "actual RRectBlur GM")
        assertRecordedGmScopes(result, "actual RRectBlur GM")
        assertFullOpaque(result, "actual RRectBlur GM")

        // sigma=1 has ceil(3*sigma)=3 support. These cells are outside the
        // intended corner ellipses even at the nearest source-cell boundary.
        assertRgba(result, 24, 24, 68, 68, 68, 255)
        assertRgba(result, 75, 24, 68, 68, 68, 255)
        assertRgba(result, 24, 75, 68, 68, 68, 255)
        assertRgba(result, 75, 75, 68, 68, 68, 255)
        assertRgba(result, 224, 24, 68, 68, 68, 255)
        assertRgba(result, 275, 24, 68, 68, 68, 255)
        assertRgba(result, 224, 75, 68, 68, 68, 255)
        assertRgba(result, 275, 75, 68, 68, 68, 255)

        assertRgba(result, 50, 50, 255, 255, 255, 255)
        assertRgba(result, 250, 50, 255, 255, 255, 255)
        assertRgba(result, 10, 394, 68, 68, 68, 255)
        assertRgba(result, 50, 350, 35, 120, 220, 255)
        assertRgba(result, 250, 350, 35, 120, 220, 255)
    }

    @Test
    fun actualRrectBlurIncludesSecondVerticalHairline() {
        val gm = RRectBlurGm()
        val result = renderActualGm(gm)

        assertNativeWork(result, "actual RRectBlur GM second-separator witness")
        assertRecordedGmScopes(result, "actual RRectBlur GM second-separator witness")
        assertFullOpaque(result, "actual RRectBlur GM second-separator witness")
        assertRgbNear(result, 199, 50, 192, tolerance = 1)
        assertRgbNear(result, 200, 50, 192, tolerance = 1)
        assertRgba(result, 198, 50, 68, 68, 68, 255)
        assertRgba(result, 201, 50, 68, 68, 68, 255)
    }

    private fun renderActualGm(gm: RRectBlurGm): RenderResult {
        val surface = Surface(gm.width, gm.height, config = gm.compositionConfig())
        val canvas = GmCanvas(surface.canvas(), gm.width, gm.height)
        SceneRecordingScope.recordingOnly {
            canvas.drawRect(
                RectF32.ofLTRB(0f, 0f, gm.width.toFloat(), gm.height.toFloat()),
                Paint(color = ColorARGB.White, antiAlias = false),
            )
            gm.onOnceBeforeDraw(canvas)
            gm.draw(canvas, gm.width, gm.height)
        }
        return surface.render()
    }

    private fun assertNativeWork(result: RenderResult, label: String) {
        println(
            "W7_RRECT_BLUR_NATIVE label=$label stats=${result.stats} " +
                "scopes=${result.nativeEvidenceScopeKinds} counters=${result.nativeEvidenceCounters} " +
                "structuralSteps=${result.structuralSteps} diagnostics=${result.diagnostics.summary()}"
        )
        assertTrue(result.isClean, "$label ${result.diagnostics.summary()}")
        assertTrue(result.stats.opsDispatched > 0, "$label expected positive native dispatch")
        assertEquals(0, result.stats.opsRefused, "$label refused operations")
        assertTrue(result.stats.drawCallCount > 0, "$label expected positive native draw count")
        assertTrue(result.stats.pipelineCount > 0, "$label expected positive native pipeline count")
    }

    private fun assertRecordedGmScopes(result: RenderResult, label: String) {
        assertTrue(
            result.nativeEvidenceScopeKinds.containsAll(setOf("Render", "Readback")),
            "$label native scopes=${result.nativeEvidenceScopeKinds}",
        )
    }

    private fun assertFullOpaque(result: RenderResult, label: String) {
        assertEquals(result.width * result.height * 4, result.pixels.size, "$label RGBA byte count")
        for (pixel in 0 until result.width * result.height) {
            assertEquals(
                255,
                result.pixels[pixel * 4 + 3].toInt() and 0xff,
                "$label alpha at (${pixel % result.width},${pixel / result.width})",
            )
        }
    }

    private fun assertRgba(result: RenderResult, x: Int, y: Int, red: Int, green: Int, blue: Int, alpha: Int) {
        val offset = (y * result.width + x) * 4
        val actual = (0..3).map { result.pixels[offset + it].toInt() and 0xff }
        assertEquals(listOf(red, green, blue, alpha), actual, "pixel ($x,$y)")
    }

    private fun assertRgbNear(result: RenderResult, x: Int, y: Int, expected: Int, tolerance: Int) {
        val offset = (y * result.width + x) * 4
        for (channel in 0..2) {
            val actual = result.pixels[offset + channel].toInt() and 0xff
            assertTrue(actual in (expected - tolerance)..(expected + tolerance), "pixel ($x,$y) channel $channel=$actual")
        }
        assertEquals(255, result.pixels[offset + 3].toInt() and 0xff, "pixel ($x,$y) alpha")
    }
}
