package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

class GPUFirstRoutePassBuilderTest {

    @Test
    fun `acceptedDrawLayer produces pass with Shading role`() {
        val pass = GPUFirstRoutePassBuilder.acceptedDrawLayer(
            commandIdValue = 1,
            analysisRecordId = "analysis.draw_layer.1",
            renderStepIdentity = "layer.isolated_target",
            sortKey = 0L,
            pipelineKey = GPURenderPipelineKey("pending.pipeline.draw_layer.isolation.src_over"),
            blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.SRC_OVER, "destination is unchanged"),
            boundsHash = "bounds.layer.1",
            scissorBoundsHash = null,
            originalPaintOrder = 0,
            targetStateHash = "target.rgba8.layer.1",
            layerScopeId = "layer.1",
        )

        assertNotNull(pass)
        assertEquals("pass.draw_layer.1", pass.passId)
        assertEquals("target.rgba8.layer.1", pass.targetStateHash)
        assertEquals("layer.1", pass.layerScopeId)
        assertEquals("load.store", pass.loadStoreLabel)
        assertEquals(1, pass.invocations.size)
        assertEquals(1, pass.drawPackets.size)

        val packet = pass.drawPackets.single()
        assertEquals(GPUDrawPacketRole.Shading, packet.role)
        assertEquals("layer.1", packet.layerId)
        assertEquals(0L, packet.sortKey)
        assertEquals("pending.pipeline.draw_layer.isolation.src_over", packet.renderPipelineKey!!.value)

        val invocation = pass.invocations.single()
        assertEquals(1, invocation.commandIdValue)
        assertEquals("analysis.draw_layer.1", invocation.analysisRecordId)
    }

    @Test
    fun `acceptedDrawLayer with scissor preserves bounds hash in packet`() {
        val pass = GPUFirstRoutePassBuilder.acceptedDrawLayer(
            commandIdValue = 42,
            analysisRecordId = "analysis.draw_layer.42",
            renderStepIdentity = "layer.isolated_target",
            sortKey = 7L,
            pipelineKey = GPURenderPipelineKey("pipe.draw_layer.42"),
            blendPlan = GPUBlendPlan.NoOp(GPUBlendMode.SRC_OVER, "opaque source preserves destination"),
            boundsHash = "bounds.layer.42",
            scissorBoundsHash = "scissor.layer.42",
            originalPaintOrder = 3,
            targetStateHash = "target.rgba8.layer.42",
            layerScopeId = "layer.42",
        )

        val packet = pass.drawPackets.single()
        assertEquals("scissor.layer.42", packet.scissorBoundsHash)
        assertEquals("pipe.draw_layer.42", packet.renderPipelineKey!!.value)
    }

    @Test
    fun `refusedDrawLayer produces pass with diagnostic`() {
        val pass = GPUFirstRoutePassBuilder.refusedDrawLayer(
            commandIdValue = 99,
            targetStateHash = "target.rgba8.layer.99",
            code = "unsupported.composite.layer.bounds",
        )

        assertNotNull(pass)
        assertEquals("pass.refused.draw_layer.99", pass.passId)
        assertEquals("target.rgba8.layer.99", pass.targetStateHash)
        assertEquals("root", pass.layerScopeId)
        assertEquals("refused", pass.loadStoreLabel)
        assertTrue(pass.invocations.isEmpty())
        assertTrue(pass.pipelineKeys.isEmpty())
        assertTrue(pass.drawPackets.isEmpty())
        assertEquals(1, pass.diagnostics.size)

        val diagnostic = pass.diagnostics.single()
        assertEquals("unsupported.composite.layer.bounds", diagnostic.code)
        assertEquals("pass.refused.draw_layer.99", diagnostic.passId)
        assertTrue(diagnostic.terminal)
    }
}
