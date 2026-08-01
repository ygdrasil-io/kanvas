package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

class GPUSaveLayerNativeExecutorTest {

    @Test
    fun `executor delegates to materializer and produces result`() {
        val gatePlan = buildMinimalGatePlan()
        val request = GPUSaveLayerMaterializationRequest(
            targetId = "test-target",
            gatePlan = gatePlan,
            parentPassId = "parent-pass",
            childPassId = "child-pass",
            childTargetStateHash = "child-hash",
            parentTargetStateHash = "parent-hash",
            childLoadStoreLabel = "store",
            parentLoadStoreLabel = "load",
            deviceGeneration = 0L,
            expectedTargetGeneration = 0L,
            actualTargetGeneration = 0L,
            availableUsageLabels = setOf("render_attachment", "texture_binding"),
            allocationAvailable = true,
            targetBudgetBytes = 1024 * 1024,
            actualFormatClass = "rgba8unorm",
            actualSampleCount = 1,
        )
        val context = GPUTargetPreparationContext(
            targetId = "test-target",
            frameId = "test-frame",
            deviceGeneration = 0L,
            budgetClass = "default",
        )
        val executor = GPUSaveLayerNativeExecutor()
        val result = executor.execute(request, context)

        assertNotNull(result)
        assertFalse(result.adapterBacked)
        assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
    }

    @Test
    fun `executor threads a real blend plan into the composite layer command`() {
        val gatePlan = buildMinimalGatePlan()
        val request = GPUSaveLayerMaterializationRequest(
            targetId = "test-target",
            gatePlan = gatePlan,
            parentPassId = "parent-pass",
            childPassId = "child-pass",
            childTargetStateHash = "child-hash",
            parentTargetStateHash = "parent-hash",
            childLoadStoreLabel = "store",
            parentLoadStoreLabel = "load",
            deviceGeneration = 0L,
            expectedTargetGeneration = 0L,
            actualTargetGeneration = 0L,
            availableUsageLabels = setOf("render_attachment", "texture_binding"),
            allocationAvailable = true,
            targetBudgetBytes = 1024 * 1024,
            actualFormatClass = "rgba8unorm",
            actualSampleCount = 1,
        )
        val context = GPUTargetPreparationContext(
            targetId = "test-target",
            frameId = "test-frame",
            deviceGeneration = 0L,
            budgetClass = "default",
        )
        val executor = GPUSaveLayerNativeExecutor()
        val result = executor.execute(request, context)

        assertNotNull(result)
        assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        val composite = result.commandStream.commands
            .filterIsInstance<GPUPassCommand.CompositeLayer>()
            .single()
        assertFalse(
            composite.blendPlan is GPUBlendPlan.NoOp,
            "layer blend must be real, not a NoOp placeholder",
        )
        assertEquals(GPUBlendMode.SRC_OVER, composite.blendPlan.mode)
        assertEquals("src_over", composite.blendPlan.mode.gpuLabel)
    }

    @Test
    fun `executor produces refused decision for stale generation`() {
        val gatePlan = buildMinimalGatePlan()
        val request = GPUSaveLayerMaterializationRequest(
            targetId = "test-target",
            gatePlan = gatePlan,
            parentPassId = "parent-pass",
            childPassId = "child-pass",
            childTargetStateHash = "child-hash",
            parentTargetStateHash = "parent-hash",
            childLoadStoreLabel = "store",
            parentLoadStoreLabel = "load",
            deviceGeneration = 1L,
            expectedTargetGeneration = 1L,
            actualTargetGeneration = 99L,
            availableUsageLabels = setOf("render_attachment", "texture_binding"),
            allocationAvailable = true,
            targetBudgetBytes = 1024 * 1024,
            actualFormatClass = "rgba8unorm",
            actualSampleCount = 1,
        )
        val context = GPUTargetPreparationContext(
            targetId = "test-target",
            frameId = "test-frame",
            deviceGeneration = 1L,
            budgetClass = "default",
        )
        val executor = GPUSaveLayerNativeExecutor()
        val result = executor.execute(request, context)

        assertNotNull(result)
        assertFalse(result.adapterBacked)
        assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
    }
}

private fun buildMinimalGatePlan(): GPUSaveLayerIsolatedTargetGatePlan {
    val saveRecord = GPULayerSaveRecord(
        scopeId = GPULayerScopeID("layer:test"),
        boundsLabel = "test-local",
        childCommandIds = listOf("draw-test"),
        backdropRequired = false,
    )
    val bounds = GPULayerBoundsPlan(
        requestedBoundsLabel = "test-local",
        deviceBoundsLabel = "0,0,64,48",
        conservative = true,
        originX = 0,
        originY = 0,
        width = 64,
        height = 48,
    )
    val request = GPUSaveLayerIsolatedTargetRequest(
        saveRecord = saveRecord,
        bounds = bounds,
        parentTargetLabel = "test-target",
        deviceGeneration = 0L,
    )
    return GPUSaveLayerIsolatedTargetPlanner().plan(request)
}
