@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.skia

import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRuntimeFactory
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.surface.PixelFormat
import org.graphiks.kanvas.surface.RenderConfig
import org.graphiks.kanvas.surface.RenderResult
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.skia.gm.image.TinyBitmapGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.MessageDigest

/** Native pixel witnesses for TinyBitmap source bytes and its declared background. */
class W7TinyBitmapSourceSurfacePixelTest {
    companion object {
        @AfterAll
        @JvmStatic
        fun disposeW7TinyBitmapSourceSurfacePixelTestGpu() = GPUBackendRuntimeFactory.dispose()
    }

    @Test
    // Mutation: the registered GM now has to match the independent encoded-domain pixel model.
    fun registeredTinyBitmapUsesPinnedEncodedPixelAndFreshSurfaceRepeatability() {
        val first = SkiaGmRenderer.render(TinyBitmapGm())
        logRegistered("registered-tinybitmap-first", first)
        val second = SkiaGmRenderer.render(TinyBitmapGm())
        logRegistered("registered-tinybitmap-second", second)

        assertEquals(100, first.width)
        assertEquals(100, first.height)
        assertEquals(100, second.width)
        assertEquals(100, second.height)
        assertEquals(2, first.ops.size, "renderer background and image rectangle")
        assertEquals(2, second.ops.size, "second render retains the two source operations")
        assertEquals(2, first.dispatchedCount)
        assertEquals(2, second.dispatchedCount)
        assertEquals(0, first.refusedCount)
        assertEquals(0, second.refusedCount)
        assertTrue(first.diagnostics.isEmpty(), first.diagnostics.toString())
        assertTrue(second.diagnostics.isEmpty(), second.diagnostics.toString())
        assertArrayEquals(first.rgba, second.rgba, "independent fresh-surface full RGBA repeatability")
        assertUniformRgba(first.rgba, 100, 100, ENCODED_PIXEL, "registered tinybitmap")
    }

    @Test
    // Mutation: pin the existing literal witness to LINEAR so it remains an independent control.
    fun actualTinyBitmapReplaysOnOneLinearSurfaceOverDeclaredGray() {
        val gm = TinyBitmapGm()
        val surface = Surface(
            100,
            100,
            config = RenderConfig.DEFAULT.copy(compositionDomain = CompositionDomain.LINEAR),
        )
        val canvas = surface.canvas()
        canvas.drawRect(
            RectF32(0f, 0f, 100f, 100f),
            Paint(color = GRAY_221, antiAlias = false),
        )
        val gmCanvas = GmCanvas(canvas, 100, 100)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, 100, 100)
        val operationCount = surface.snapshotOps().size

        val first = captureSurface("same-surface-tinybitmap-first", surface, operationCount)
        val replay = captureSurface("same-surface-tinybitmap-replay", surface, operationCount)

        assertEquals(2, operationCount, "literal gray background and TinyBitmap image rectangle")
        for ((label, result) in listOf("first" to first.result, "replay" to replay.result)) {
            assertEquals(2, result.stats.opsDispatched, "$label native dispatch count")
            assertEquals(0, result.stats.opsRefused, "$label refusal count")
            assertEquals(0, result.diagnostics.fatalCount, "$label fatal diagnostics")
            assertTrue(result.diagnostics.isEmpty, "$label ${result.diagnostics.summary()}")
            assertTrue(result.stats.drawCallCount > 0, "$label issues a draw call")
            assertTrue(result.stats.pipelineCount > 0, "$label binds a pipeline")
        }
        assertArrayEquals(first.rgba, replay.rgba, "same-surface complete RGBA replay")
        assertUniformRgba(first.rgba, 100, 100, LINEAR_PIXEL, "same-surface first frame")
        assertUniformRgba(replay.rgba, 100, 100, LINEAR_PIXEL, "same-surface replay")
    }

    @Test
    // New witness: replay the real TinyBitmap recording on one encoded Surface and require completed native evidence.
    fun actualTinyBitmapReplaysOnOneEncodedSurfaceWithCompletedNativeEvidence() {
        val gm = TinyBitmapGm()
        val surface = Surface(100, 100, config = gm.compositionConfig())
        val canvas = surface.canvas()
        canvas.drawRect(
            RectF32(0f, 0f, 100f, 100f),
            Paint(color = GRAY_221, antiAlias = false),
        )
        val gmCanvas = GmCanvas(canvas, 100, 100)
        gm.onOnceBeforeDraw(gmCanvas)
        gm.draw(gmCanvas, 100, 100)
        val operationCount = surface.snapshotOps().size

        val first = captureSurface("same-surface-encoded-tinybitmap-first", surface, operationCount)
        val replay = captureSurface("same-surface-encoded-tinybitmap-replay", surface, operationCount)
        val replayOperationCount = surface.snapshotOps().size

        assertEquals(2, operationCount, "literal gray background and TinyBitmap image rectangle")
        assertEquals(operationCount, replayOperationCount, "recording remains stable across full-frame replay")
        for ((label, capture) in listOf("first" to first, "replay" to replay)) {
            assertEncodedNativeEvidence(label, capture)
            assertUniformRgba(capture.rgba, 100, 100, ENCODED_PIXEL, "same-surface encoded $label")
        }
        assertArrayEquals(first.rgba, replay.rgba, "same-surface complete encoded RGBA replay")
    }

    @Test
    fun correctedPremultipliedSourceMatchesIndependentCompositionDomains() {
        assertCorrectedSourceControl(CompositionDomain.LINEAR, LINEAR_PIXEL, "corrected-source-linear")
        assertCorrectedSourceControl(CompositionDomain.SRGB_ENCODED, ENCODED_PIXEL, "corrected-source-srgb-encoded")
    }

    private fun assertCorrectedSourceControl(domain: CompositionDomain, expected: IntArray, label: String) {
        val surface = Surface(100, 100, config = RenderConfig.DEFAULT.copy(compositionDomain = domain))
        val canvas = surface.canvas()
        canvas.drawRect(RectF32(0f, 0f, 100f, 100f), Paint(color = GRAY_221, antiAlias = false))
        val image = Image.fromPixels(
            width = 1,
            height = 1,
            pixels = PREMUL_RED_PIXEL,
            colorType = ColorType.RGBA_8888,
            sourceId = "w7-tinybitmap-corrected-source-control",
            alphaType = AlphaType.PREMUL,
        )
        val paint = Paint(
            color = BYTE_ALPHA_WHITE,
            shader = Shader.Image(image, TileMode.REPEAT, TileMode.MIRROR),
            antiAlias = false,
        )
        canvas.drawRect(RectF32(0f, 0f, 100f, 100f), paint)

        val operationCount = surface.snapshotOps().size
        val render = surface.render()
        val rgba = render.pixels.map { it.toByte() }.toByteArray()
        logSurface(label, operationCount, render, rgba)

        assertEquals(2, operationCount, "$label background and shader rectangle")
        assertEquals(2, render.stats.opsDispatched, "$label dispatch count")
        assertEquals(0, render.stats.opsRefused, "$label refusal count")
        assertEquals(0, render.diagnostics.fatalCount, "$label fatal diagnostics")
        assertTrue(render.diagnostics.isEmpty, "$label ${render.diagnostics.summary()}")
        assertTrue(render.stats.drawCallCount > 0, "$label issues a draw call")
        assertTrue(render.stats.pipelineCount > 0, "$label binds a pipeline")
        assertUniformRgba(rgba, 100, 100, expected, label)
    }

    private fun captureSurface(label: String, surface: Surface, operationCount: Int): SurfaceCapture {
        val result = surface.render()
        val rgba = result.pixels.map { it.toByte() }.toByteArray()
        logSurface(label, operationCount, result, rgba)
        return SurfaceCapture(result, rgba)
    }

    private fun logRegistered(label: String, result: SkiaRenderResult) {
        println(
            "W7_TINYBITMAP_SOURCE_NATIVE label=$label operations=${result.ops.size} " +
                "dispatched=${result.dispatchedCount} refused=${result.refusedCount} " +
                "diagnostics=${result.diagnostics} sha256=${sha256(result.rgba)}",
        )
    }

    private fun logSurface(label: String, operations: Int, result: RenderResult, rgba: ByteArray) {
        println(
            "W7_TINYBITMAP_SOURCE_NATIVE label=$label operations=$operations " +
                "stats=${result.stats} diagnostics=${result.diagnostics.summary()} sha256=${sha256(rgba)} " +
                "uniqueRGBA=${uniqueRgbaCount(rgba)} scopes=${result.nativeEvidenceScopeKinds} " +
                "nativeCounters=${result.nativeEvidenceCounters} structuralSteps=${result.structuralSteps}",
        )
    }

    private fun assertEncodedNativeEvidence(label: String, capture: SurfaceCapture) {
        val result = capture.result
        val counters = result.nativeEvidenceCounters
        assertEquals(100, result.width, "$label width")
        assertEquals(100, result.height, "$label height")
        assertEquals(PixelFormat.RGBA8, result.format, "$label RGBA8 readback format")
        assertEquals(40_000, capture.rgba.size, "$label complete 100x100 RGBA buffer")
        assertEquals(2, result.stats.opsDispatched, "$label dispatched operation count")
        assertEquals(0, result.stats.opsRefused, "$label refusal count")
        assertEquals(0, result.diagnostics.fatalCount, "$label fatal diagnostics")
        assertTrue(result.diagnostics.isEmpty, "$label ${result.diagnostics.summary()}")
        assertTrue(result.stats.drawCallCount > 0, "$label issues a draw call")
        assertTrue(result.stats.pipelineCount > 0, "$label binds a pipeline")
        assertTrue("Render" in result.nativeEvidenceScopeKinds, "$label native render scope")
        assertTrue("Readback" in result.nativeEvidenceScopeKinds, "$label native readback scope")
        for (counter in listOf("frameCoordinatorCreations", "encoders", "commandBuffers", "submits", "readbackCopies")) {
            assertEquals(1L, counters[counter], "$label native $counter")
        }
        val draws = requireNotNull(counters["draws"]) { "$label native draw counter is absent" }
        val drawIndexed = requireNotNull(counters["drawIndexed"]) { "$label native indexed draw counter is absent" }
        assertEquals(Math.addExact(draws, drawIndexed), result.stats.drawCallCount.toLong(), "$label native draw coherence")
        val pipelineBinds = requireNotNull(counters["pipelineBinds"]) { "$label native pipeline counter is absent" }
        assertEquals(result.stats.pipelineCount.toLong(), pipelineBinds, "$label native pipeline coherence")
        val queueSubmitted = result.structuralSteps.indexOf("QueueSubmitted")
        val completionSucceeded = result.structuralSteps.indexOf("CompletionSucceeded")
        assertTrue(queueSubmitted >= 0, "$label submitted queue evidence: ${result.structuralSteps}")
        assertTrue(completionSucceeded > queueSubmitted, "$label completed after queue submission: ${result.structuralSteps}")
    }

    private fun assertUniformRgba(rgba: ByteArray, width: Int, height: Int, expected: IntArray, label: String) {
        assertEquals(width * height * 4, rgba.size, "$label full RGBA buffer size")
        for (pixel in 0 until width * height) {
            val offset = pixel * 4
            for (channel in 0..3) {
                assertEquals(expected[channel].toByte(), rgba[offset + channel], "$label pixel $pixel channel $channel")
            }
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun uniqueRgbaCount(rgba: ByteArray): Int = rgba.asList().chunked(4).toSet().size

    private data class SurfaceCapture(val result: RenderResult, val rgba: ByteArray)
}

private val GRAY_221 = ColorARGB.of(0xFF, 221, 221, 221)
private val BYTE_ALPHA_WHITE = ColorARGB.of(128, 255, 255, 255)
private val PREMUL_RED_PIXEL = byteArrayOf(128.toByte(), 0, 0, 128.toByte())
private val LINEAR_PIXEL = intArrayOf(230, 194, 194, 255)
private val ENCODED_PIXEL = intArrayOf(230, 165, 165, 255)
