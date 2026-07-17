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
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class PreparedSceneFrameRecorderTest {
    @Test
    fun `one selector records every currently native prepared scene family`() {
        val cases = mapOf(
            "solid-card-stack" to "solid-rect",
            "linear-gradient-lanes" to "registered-uniform-rect",
            "stroke-rect-outline" to "stroke-rect",
            "gaussian-blur-photo" to "separable-blur-rect",
            "colr-v0-color-glyph" to "color-glyph",
        )

        cases.forEach { (sceneId, expectedRoute) ->
            val scene = GPURendererSceneRegistry.registry.requireScene(sceneId)
            val result = assertIs<PreparedSceneFrameResult.Recorded>(
                PreparedSceneFrameRecorder().record(
                    scene,
                    capabilities(),
                    GPUDeviceGenerationID(41),
                    frameOrdinal = 1,
                    withReadback = true,
                ),
                sceneId,
            )

            assertEquals(expectedRoute, result.route, sceneId)
            assertTrue(result.taskList.tasks.isNotEmpty(), sceneId)
            assertTrue(result.readbackRequestId != null, sceneId)
        }
    }

    @Test
    fun `selector refuses a scene whose family has no prepared semantic route`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("path-fill-stencil")

        val result = assertIs<PreparedSceneFrameResult.Refused>(
            PreparedSceneFrameRecorder().record(
                scene,
                capabilities(),
                GPUDeviceGenerationID(41),
                frameOrdinal = 1,
                withReadback = true,
            ),
        )

        assertEquals("unsupported.prepared-scene.family", result.code)
        assertTrue(result.message.contains("path-fill-stencil"))
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "prepared-scene")),
        snapshotId = "gpu-runtime-41",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(
            GPURendererFeature.RenderPass,
            GPURendererFeature.CopyUpload,
            GPURendererFeature.Readback,
            GPURendererFeature.UniformBuffer,
            GPURendererFeature.TextureSampling,
        ),
    )
}
