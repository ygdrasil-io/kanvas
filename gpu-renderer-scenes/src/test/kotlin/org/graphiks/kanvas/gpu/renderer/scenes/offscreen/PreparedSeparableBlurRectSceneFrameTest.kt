package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilityFact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUImplementationIdentity
import org.graphiks.kanvas.gpu.renderer.capabilities.GPULimits
import org.graphiks.kanvas.gpu.renderer.capabilities.GPURendererFeature
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class PreparedSeparableBlurRectSceneFrameTest {
    @Test
    fun `gaussian blur photo lowers into three prepared passes and two intermediates`() {
        val recorded = assertIs<PreparedSeparableBlurRectSceneFrameResult.Recorded>(
            PreparedSeparableBlurRectSceneFrameRecorder().record(
                GPURendererSceneRegistry.registry.requireScene("gaussian-blur-photo"),
                capabilities(),
                GPUDeviceGenerationID(9),
                frameOrdinal = 2L,
                withReadback = true,
            ),
        )

        assertEquals(3, recorded.taskList.tasks.filterIsInstance<GPUTask.Render>().size)
        val prepare = recorded.taskList.tasks.filterIsInstance<GPUTask.PrepareResources>().single()
        assertEquals(2, prepare.requests.count { it.role == GPUFrameResourceRole.FilterTarget })
        assertContains(recorded.diagnostics, "separableBlur:frameRoute=one-encoder-one-command-buffer-one-submit")
        assertEquals(320 * 200 * 4, recorded.cpuReferenceRgba.size)
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "prepared-separable-blur")),
        snapshotId = "capabilities-prepared-separable-blur-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
