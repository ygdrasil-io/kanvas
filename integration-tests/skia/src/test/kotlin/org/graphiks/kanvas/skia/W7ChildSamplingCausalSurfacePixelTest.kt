@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.test.ComparisonUtils
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.security.MessageDigest

/** Native causal comparison of the historical open FILL and public drawLine routes. */
class W7ChildSamplingCausalSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun cleanupGpu() {
            GPUBackendRuntimeFactory.dispose()
        }
    }

    @Test
    fun drawLineExplainsHistoricalChildSamplingPixelDelta() {
        val width = 256
        val height = 256
        val linearConfig = RenderConfig.DEFAULT.copy(compositionDomain = CompositionDomain.LINEAR)
        val white = Paint(color = ColorARGB.fromRGBA(1f, 1f, 1f, 1f), antiAlias = false)
        val background = Paint(color = ColorARGB.fromRGBA(0.9f, 0.9f, 0.9f, 1f))
        val red = Paint(color = ColorARGB.fromRGBA(1f, 0f, 0f, 0.8f), antiAlias = true)

        fun newSurface(): Pair<Surface, GmCanvas> {
            val surface = Surface(width = width, height = height, config = linearConfig)
            val canvas = surface.canvas()
            canvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()), white)
            return surface to GmCanvas(canvas, width, height)
        }

        val (legacySurface, legacyCanvas) = newSurface()
        legacyCanvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()), background)
        legacyCanvas.drawPath(openLine(10f, 10f, 100f, 100f), red)
        legacyCanvas.drawPath(openLine(10f, 100f, 100f, 10f), red)

        val (publicDrawLineSurface, publicDrawLineCanvas) = newSurface()
        publicDrawLineCanvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()), background)
        publicDrawLineCanvas.drawLine(10f, 10f, 100f, 100f, red)
        publicDrawLineCanvas.drawLine(10f, 100f, 100f, 10f, red)

        val (strokeSurface, strokeCanvas) = newSurface()
        strokeCanvas.drawRect(RectF32(0f, 0f, width.toFloat(), height.toFloat()), background)
        strokeCanvas.drawPath(openLine(10f, 10f, 100f, 100f), red.copy(style = PaintStyle.STROKE))
        strokeCanvas.drawPath(openLine(10f, 100f, 100f, 10f), red.copy(style = PaintStyle.STROKE))

        fun capture(label: String, surface: Surface, frame: String): RenderResult {
            val result = surface.render()
            val rgba = result.pixels.map { it.toByte() }.toByteArray()
            val diagnostics = result.diagnostics.entries.map { "${it.code}: ${it.reason}" }
            println(
                "W7_CHILD_SAMPLING_NATIVE label=$label frame=$frame operationCount=${surface.snapshotOps().size} " +
                    "stats=${result.stats} scopes=${result.nativeEvidenceScopeKinds} " +
                    "counters=${result.nativeEvidenceCounters} diagnostics=$diagnostics sha256=${sha256(rgba)}",
            )

            assertTrue(result.isClean, "$label $frame diagnostics=${result.diagnostics.summary()}")
            assertTrue(result.diagnostics.isEmpty, "$label $frame diagnostics=$diagnostics")
            assertEquals(0, result.stats.opsRefused, "$label $frame refused operations")
            assertTrue(result.stats.opsDispatched > 0, "$label $frame dispatched operations: ${result.stats}")
            assertTrue(result.stats.drawCallCount > 0, "$label $frame native draw count: ${result.stats}")
            assertTrue(result.stats.pipelineCount > 0, "$label $frame native pipeline count: ${result.stats}")
            assertEquals(PixelFormat.RGBA8, result.format, "$label $frame format")
            assertEquals(width, result.width, "$label $frame width")
            assertEquals(height, result.height, "$label $frame height")
            return result
        }

        val legacyFrame = capture("LEGACY_OPEN_FILL", legacySurface, "frame")
        val legacyReplay = capture("LEGACY_OPEN_FILL", legacySurface, "replay")
        val publicDrawLineFrame = capture("PUBLIC_DRAW_LINE", publicDrawLineSurface, "frame")
        val publicDrawLineReplay = capture("PUBLIC_DRAW_LINE", publicDrawLineSurface, "replay")
        val strokeFrame = capture("EXPLICIT_STROKE", strokeSurface, "frame")
        val strokeReplay = capture("EXPLICIT_STROKE", strokeSurface, "replay")

        val legacy = legacyFrame.pixels.map { it.toByte() }.toByteArray()
        val legacyReplayBytes = legacyReplay.pixels.map { it.toByte() }.toByteArray()
        val publicDrawLine = publicDrawLineFrame.pixels.map { it.toByte() }.toByteArray()
        val publicDrawLineReplayBytes = publicDrawLineReplay.pixels.map { it.toByte() }.toByteArray()
        val stroke = strokeFrame.pixels.map { it.toByte() }.toByteArray()
        val strokeReplayBytes = strokeReplay.pixels.map { it.toByte() }.toByteArray()

        assertArrayEquals(legacy, legacyReplayBytes, "legacy full-buffer replay")
        assertArrayEquals(publicDrawLine, publicDrawLineReplayBytes, "public drawLine full-buffer replay")
        assertArrayEquals(stroke, strokeReplayBytes, "explicit stroke full-buffer replay")

        assertLiteralFlatGray(legacy, width, height)
        assertOpaque(legacy, "legacy")
        assertOpaque(publicDrawLine, "public drawLine")
        assertOpaque(stroke, "explicit stroke")
        assertBackgroundPixel(publicDrawLine, width, 200, 20)
        assertBackgroundPixel(publicDrawLine, width, 20, 200)
        assertBackgroundPixel(publicDrawLine, width, 200, 200)
        assertBackgroundPixel(stroke, width, 200, 20)
        assertBackgroundPixel(stroke, width, 20, 200)
        assertBackgroundPixel(stroke, width, 200, 200)
        assertTrue(visibleRedLinePixelCount(publicDrawLine, width) > 0, "public drawLine has visible red line pixels")
        assertTrue(visibleRedLinePixelCount(stroke, width) > 0, "explicit stroke has visible red line pixels")
        assertArrayEquals(stroke, publicDrawLine, "public drawLine must match explicit STROKE")
        assertTrue(!publicDrawLine.contentEquals(legacy), "public drawLine must differ from historical open FILL")

        val evidenceRoot = System.getProperty("w7.ordinaryAaEvidenceDir")
        if (evidenceRoot != null) {
            val outputs = listOf(
                File(evidenceRoot, "w7-child-sampling-legacy-open-fill.png") to legacy,
                File(evidenceRoot, "w7-child-sampling-public-draw-line.png") to publicDrawLine,
                File(evidenceRoot, "w7-child-sampling-explicit-stroke.png") to stroke,
            )
            outputs.forEach { (file, _) -> check(!file.exists()) { "Refusing to overwrite evidence image: $file" } }
            outputs.forEach { (file, rgba) -> ComparisonUtils.saveRgbaAsPng(rgba, width, height, file) }
        }
    }

    private fun openLine(x1: Float, y1: Float, x2: Float, y2: Float) = Path {
        moveTo(x1, y1)
        lineTo(x2, y2)
    }

    private fun assertLiteralFlatGray(rgba: ByteArray, width: Int, height: Int) {
        val expected = byteArrayOf(230.toByte(), 230.toByte(), 230.toByte(), 255.toByte())
        for (pixel in 0 until width * height) {
            val offset = pixel * 4
            if (rgba[offset] != expected[0] || rgba[offset + 1] != expected[1] ||
                rgba[offset + 2] != expected[2] || rgba[offset + 3] != expected[3]
            ) {
                val x = pixel % width
                val y = pixel / width
                assertArrayEquals(expected, rgba.copyOfRange(offset, offset + 4), "legacy pixel ($x,$y)")
            }
        }
    }

    private fun assertOpaque(rgba: ByteArray, label: String) {
        for (offset in 3 until rgba.size step 4) {
            assertEquals(255.toByte(), rgba[offset], "$label alpha at pixel ${offset / 4}")
        }
    }

    private fun assertBackgroundPixel(rgba: ByteArray, width: Int, x: Int, y: Int) {
        val offset = (y * width + x) * 4
        assertArrayEquals(
            byteArrayOf(230.toByte(), 230.toByte(), 230.toByte(), 255.toByte()),
            rgba.copyOfRange(offset, offset + 4),
            "background pixel ($x,$y)",
        )
    }

    private fun visibleRedLinePixelCount(rgba: ByteArray, width: Int): Int {
        var count = 0
        for (y in 10..100) for (x in 10..100) {
            val offset = (y * width + x) * 4
            val red = rgba[offset].toInt() and 0xFF
            val green = rgba[offset + 1].toInt() and 0xFF
            val blue = rgba[offset + 2].toInt() and 0xFF
            val alpha = rgba[offset + 3].toInt() and 0xFF
            if (red > 230 && green < 230 && blue < 230 && alpha == 255) count++
        }
        return count
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
