package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTexture
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUColorGlyphPreparedFrameSmokeTest {
    @Test
    fun `prepared frame renders canonical two layer color glyph in one submit and readback`() {
        withBackend { backend ->
            val requestId = GPUReadbackRequestID("readback.color-glyph.prepared")
            val session = backend.prepareSceneFrameSession(targetRequest())
            try {
                val terminal = session.renderFrame(
                    taskList(backend, canonicalAtlas(), requestId, frameId = 10_521L),
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    terminal.outcome,
                    "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
                )
                assertContentEquals(
                    byteArrayOf(
                        255.toByte(), 0, 0, 255.toByte(),
                        255.toByte(), 0, 0, 255.toByte(),
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 0, 0,
                        0, 0, 188.toByte(), 128.toByte(),
                        0, 0, 188.toByte(), 128.toByte(),
                    ),
                    assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes,
                )
                val counters = session.nativeCounters()
                assertEquals(1L, counters.encoders)
                assertEquals(1L, counters.submits)
                assertEquals(1L, counters.readbackCopies)
                assertEquals(1L, counters.nativePayloadRegistrations)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun `prepared completion-only color glyph performs no readback copy`() {
        withBackend { backend ->
            val session = backend.prepareSceneFrameSession(targetRequest())
            try {
                val terminal = session.renderFrame(
                    taskList(backend, canonicalAtlas(), requestId = null, frameId = 10_522L),
                    GPUSceneFrameOutputRequest.CurrentFrameCompletionOnly,
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    terminal.outcome,
                    "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
                )
                assertIs<GPUSceneFrameOutput.CurrentFrameCompletionOnly>(terminal.output)
                assertEquals(1L, session.nativeCounters().encoders)
                assertEquals(1L, session.nativeCounters().submits)
                assertEquals(0L, session.nativeCounters().readbackCopies)
            } finally {
                session.close()
            }
        }
    }

    @Test
    fun `prepared session reuses invariants while every color atlas stays frame local`() {
        withBackend { backend ->
            val session = backend.prepareSceneFrameSession(targetRequest())
            try {
                listOf(
                    canonicalAtlas(),
                    canonicalAtlas(byteArrayOf(64, 192.toByte())),
                ).forEachIndexed { frame, atlas ->
                    val terminal = session.renderFrame(
                        taskList(
                            backend,
                            atlas,
                            requestId = null,
                            frameId = 10_523L + frame,
                        ),
                    ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                    assertEquals(
                        GPUFrameStructuralOutcome.Succeeded,
                        terminal.outcome,
                        "frame=$frame ${terminal.diagnostic?.code?.value}: " +
                            terminal.diagnostic?.message,
                    )
                }

                val counters = session.nativeCounters()
                assertEquals(2L, counters.encoders)
                assertEquals(2L, counters.submits)
                assertEquals(1L, counters.colorGlyphInvariantCreations)
                assertEquals(0L, counters.colorGlyphAtlasCreations)
                assertEquals(0L, counters.colorGlyphAtlasUploads)
                assertEquals(0L, counters.colorGlyphAtlasReuses)
                assertEquals(0L, counters.colorGlyphAtlasInvalidations)
                assertEquals(0L, counters.colorGlyphCurrentAtlasBytes)
                assertEquals(0L, counters.colorGlyphPeakAtlasBytes)
            } finally {
                session.close()
            }
        }
    }

    private fun taskList(
        backend: GPUBackendSession,
        atlas: GlyphAtlasTexture,
        requestId: GPUReadbackRequestID?,
        frameId: Long,
    ) = buildPreparedColorGlyphTestTaskList(
        capabilities = requireNotNull(backend.capabilities),
        deviceGeneration = backend.deviceGeneration,
        atlas = atlas,
        layers = listOf(
            GPUPreparedColorGlyphTestLayer(
                placement = atlas.placements[0],
                deviceBounds = GPUPixelBounds(0, 0, 2, 1),
                premultipliedRgba = floatArrayOf(1f, 0f, 0f, 1f),
            ),
            GPUPreparedColorGlyphTestLayer(
                placement = atlas.placements[1],
                deviceBounds = GPUPixelBounds(2, 1, 4, 2),
                premultipliedRgba = floatArrayOf(0f, 0f, 1f, 1f),
            ),
        ),
        targetWidth = TARGET_WIDTH,
        targetHeight = TARGET_HEIGHT,
        frameId = frameId,
        commandId = 41,
        target = GPUFrameTargetRef("target.color-glyph.prepared"),
        requestId = requestId,
    )

    private fun canonicalAtlas(
        bytes: ByteArray = byteArrayOf(255.toByte(), 128.toByte()),
    ) = GlyphAtlasTexture(
        a8Bytes = bytes,
        width = 2,
        height = 1,
        glyphCount = 2,
        fontFamily = "synthetic-color-glyph",
        evidenceDumpLines = listOf("fixture=prepared-color-glyph-smoke"),
        placements = listOf(
            GlyphAtlasPlacement(GlyphStrikeKey(11, 48f, 0, 0), AtlasRegion(0, 0, 1, 1)),
            GlyphAtlasPlacement(GlyphStrikeKey(12, 48f, 0, 0), AtlasRegion(1, 0, 1, 1)),
        ),
    )

    private fun targetRequest() = GPUOffscreenTargetRequest(
        TARGET_WIDTH,
        TARGET_HEIGHT,
        GPUColorFormat.RGBA8UnormSrgb,
        GPUColorInterpretation.LinearPremul,
    )

    private inline fun withBackend(block: (GPUBackendSession) -> Unit) {
        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        try {
            block(requireNotNull(backend))
        } finally {
            GPUBackendRuntimeNativeFactory.dispose()
        }
    }

    private companion object {
        const val TARGET_WIDTH = 4
        const val TARGET_HEIGHT = 2
    }
}
