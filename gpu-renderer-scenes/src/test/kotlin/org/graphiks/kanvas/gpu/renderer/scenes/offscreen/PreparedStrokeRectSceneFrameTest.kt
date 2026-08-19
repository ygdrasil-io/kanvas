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
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererSceneRegistry

class PreparedStrokeRectSceneFrameTest {
    @Test
    fun `stroke rect outline lowers geometry into one prepared five packet batch`() {
        val recorded = assertIs<PreparedStrokeRectSceneFrameResult.Recorded>(
            PreparedStrokeRectSceneFrameRecorder().record(
                GPURendererSceneRegistry.registry.requireScene("stroke-rect-outline"),
                capabilities(),
                GPUDeviceGenerationID(9),
                frameOrdinal = 2L,
                withReadback = true,
            ),
        )

        assertEquals(5, recorded.semantics.size)
        val render = recorded.taskList.tasks.filterIsInstance<GPUTask.Render>().single()
        assertEquals(5, render.drawPackets.size)
        assertEquals(320 * 200 * 4, recorded.cpuReferenceRgba.size)
        assertContains(recorded.diagnostics, "strokeRect:geometryRoute=analytic-annular-rect.coverage")
        assertContains(recorded.diagnostics, "strokeRect:coverageBands=4")
        assertContains(recorded.diagnostics, "strokeRect:legacyStrokeWgsl=false")
    }

    private fun capabilities() = GPUCapabilities(
        implementation = GPUImplementationIdentity("GPU", "unit", "adapter", "device"),
        facts = listOf(GPUCapabilityFact("limits", "test", "observed", true, "prepared-stroke-rect")),
        snapshotId = "capabilities-prepared-stroke-rect-9",
        limits = GPULimits(8192, 256, 256, maxBufferSize = 1L shl 30),
        rendererFeatures = setOf(GPURendererFeature.RenderPass, GPURendererFeature.Readback),
    )
}
