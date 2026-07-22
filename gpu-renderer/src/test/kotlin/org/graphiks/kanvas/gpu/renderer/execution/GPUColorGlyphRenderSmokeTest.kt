package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureBuilder
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureResult
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUColorGlyphRenderSmokeTest {
    @Test
    fun `prepared ColorGlyph frame renders two layer COLRv0 composite with red and blue layers`() {
        val atlasResult = GlyphAtlasTextureBuilder().build("AB", fontSize = 48f)
        assumeTrue(
            atlasResult is GlyphAtlasTextureResult.Built,
            "glyph atlas unavailable in current environment: ${(atlasResult as? GlyphAtlasTextureResult.Refused)?.reason}",
        )
        val atlas = assertIs<GlyphAtlasTextureResult.Built>(atlasResult).atlas
        assertTrue(atlas.placements.size >= 2, "need at least 2 glyph placements for 'A' and 'B'")

        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")
        runtime!!.use { session ->
            val capabilities = requireNotNull(session.capabilities)
            val generation = session.deviceGeneration
            val requestId = GPUReadbackRequestID("readback.color-glyph.render-smoke")
            val taskList: GPUTaskList = buildPreparedColorGlyphTestTaskList(
                capabilities = capabilities,
                deviceGeneration = generation,
                atlas = atlas,
                layers = listOf(
                    GPUPreparedColorGlyphTestLayer(
                        atlas.placements[0],
                        GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
                        floatArrayOf(1f, 0f, 0f, 1f),
                    ),
                    GPUPreparedColorGlyphTestLayer(
                        atlas.placements[1],
                        GPUPixelBounds(0, 0, TARGET_WIDTH, TARGET_HEIGHT),
                        floatArrayOf(0f, 0f, 1f, 1f),
                    ),
                ),
                targetWidth = TARGET_WIDTH,
                targetHeight = TARGET_HEIGHT,
                frameId = 10_531L,
                commandId = 51,
                target = GPUFrameTargetRef("target.color-glyph.render-smoke"),
                requestId = requestId,
            )

            session.prepareSceneFrameSession(
                GPUOffscreenTargetRequest(TARGET_WIDTH, TARGET_HEIGHT),
            ).use { prepared ->
                val terminal = prepared.renderFrame(
                    taskList,
                    GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
                ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)
                assertEquals(
                    GPUFrameStructuralOutcome.Succeeded,
                    terminal.outcome,
                    "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
                )
                val rgba = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
                assertEquals(TARGET_WIDTH * TARGET_HEIGHT * 4, rgba.size, "RGBA buffer size mismatch")

                var redCount = 0
                var blueCount = 0
                for (i in rgba.indices step 4) {
                    val red = rgba[i].toInt() and 0xFF
                    val blue = rgba[i + 2].toInt() and 0xFF
                    if (red > 0x80 && blue < 0x40) redCount++
                    if (blue > 0x80 && red < 0x40) blueCount++
                }
                assertTrue(redCount > 0, "expected some red-dominant pixels from layer 0 (A), found 0")
                assertTrue(blueCount > 0, "expected some blue-dominant pixels from layer 1 (B), found 0")
            }
        }
    }

    private companion object {
        const val TARGET_WIDTH = 64
        const val TARGET_HEIGHT = 64
    }
}
