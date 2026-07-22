package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTexture

class GPUColorGlyphPreparedTestSupportTest {
    @Test
    fun `synthetic atlas builds a prepared task list without font or native GPU`() {
        val strikeKey = GlyphStrikeKey(glyphId = 65, size = 16f, subpixelX = 0, subpixelY = 0)
        val placement = GlyphAtlasPlacement(strikeKey, AtlasRegion(x = 0, y = 0, width = 1, height = 1))
        val atlas = GlyphAtlasTexture(
            a8Bytes = byteArrayOf(0x7f),
            width = 1,
            height = 1,
            glyphCount = 1,
            fontFamily = "synthetic",
            evidenceDumpLines = listOf("synthetic-test-atlas"),
            placements = listOf(placement),
        )

        val taskList: GPUTaskList = buildPreparedColorGlyphTestTaskList(
            capabilities = capabilities(),
            deviceGeneration = GPUDeviceGenerationID(9L),
            atlas = atlas,
            layers = listOf(
                GPUPreparedColorGlyphTestLayer(
                    placement,
                    GPUPixelBounds(0, 0, 1, 1),
                    floatArrayOf(1f, 0f, 0f, 1f),
                ),
            ),
            targetWidth = 1,
            targetHeight = 1,
            frameId = 10_533L,
            commandId = 53,
            target = GPUFrameTargetRef("target.color-glyph.synthetic-test"),
            requestId = GPUReadbackRequestID("readback.color-glyph.synthetic-test"),
        )

        assertEquals(3, taskList.tasks.size)
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "synthetic-color-glyph")),
        snapshotId = "capabilities-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        supportedTextureFormats = setOf(GPUTextureFormat.RGBA8Unorm, GPUTextureFormat.R8Unorm),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
