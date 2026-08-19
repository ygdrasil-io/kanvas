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

class PreparedSolidRectSceneFrameTest {
    @Test
    fun `solid card stack lowers clear and fills into one prepared batch`() {
        val scene = GPURendererSceneRegistry.registry.requireScene("solid-card-stack")
        val recorded = assertIs<PreparedSolidRectSceneFrameResult.Recorded>(
            PreparedSolidRectSceneFrameRecorder().record(
                scene,
                capabilities(),
                GPUDeviceGenerationID(9),
                frameOrdinal = 1,
                withReadback = true,
            ),
        )

        assertEquals(4, recorded.semantics.size)
        assertTrue(recorded.readbackRequestId != null)
        assertTrue(recorded.diagnostics.any { it == "solidRect:packets=4 clearAsPacket=true" })
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "prepared-solid-scene")),
        snapshotId = "capabilities-scene-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
