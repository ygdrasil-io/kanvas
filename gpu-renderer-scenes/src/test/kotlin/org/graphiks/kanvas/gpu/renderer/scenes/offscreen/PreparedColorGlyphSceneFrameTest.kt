package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.colrV0ColorGlyphScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

class PreparedColorGlyphSceneFrameTest {
    @Test
    fun `catalog COLRv0 scene records the real Skia font layers through the prepared route`() {
        val command = assertIs<SceneCommand.ColorTextRun>(
            colrV0ColorGlyphScene.commands.single { it.family == "color-text-run" },
        )

        val prepared = assertIs<PreparedColorGlyphSceneFrameResult.Recorded>(
            PreparedColorGlyphSceneFrameRecorder().record(
                scene = colrV0ColorGlyphScene,
                capabilities = capabilities(),
                deviceGeneration = GPUDeviceGenerationID(9),
                frameOrdinal = 1,
                withReadback = true,
            ),
        )

        assertTrue(command.hasFixturePayload)
        assertEquals(listOf(7u, 8u), prepared.semantic.layers.map { it.layerGlyphID })
        assertEquals(
            listOf(
                listOf(1f, 42f / 255f, 42f / 255f, 1f),
                listOf(0f, 0f, 0f, 1f),
            ),
            prepared.semantic.layers.map { it.premultipliedRgba },
        )
        assertEquals(784, prepared.semantic.uniformBytes.size)
        assertEquals("r8unorm", prepared.semantic.atlasFormat.gpuLabel)
        assertTrue(prepared.taskList.tasks.any { it::class.simpleName == "Readback" })
        assertTrue(prepared.diagnostics.any { it == "colorTextRun:fontResource=/fonts/skia/colr.ttf" })
        assertTrue(prepared.diagnostics.any { it == "colorTextRun:baseGlyph=2 layerGlyphs=7,8" })
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "prepared-color-glyph-scene")),
        snapshotId = "capabilities-scene-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
