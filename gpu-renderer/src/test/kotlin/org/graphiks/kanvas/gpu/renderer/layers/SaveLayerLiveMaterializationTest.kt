package org.graphiks.kanvas.gpu.renderer.layers

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.execution.GPUExecutionDiagnostic
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackRequest
import org.graphiks.kanvas.gpu.renderer.execution.GPUReadbackResult
import org.graphiks.kanvas.gpu.renderer.execution.dumpLines
import org.graphiks.kanvas.gpu.renderer.passes.dumpLines
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

/** Verifies KGPU-M11-006 saveLayer isolated-target live materialization contracts. */
class SaveLayerLiveMaterializationTest {
    @Test
    fun `accepted isolated target materializes layer target and ordered composite commands`() {
        val gate = GPUSaveLayerIsolatedTargetPlanner().plan(saveLayerMaterializationGateRequest())

        val result = ValidatingSaveLayerMaterializer().materialize(
            request = saveLayerMaterializationRequest(gate),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        assertEquals(listOf(GPUTextureResourceRef("texture-ref:layer-target:card")), materialized.resources)
        assertEquals(
            listOf(
                "layer-target:card" to GPUMaterializedCommandOperandKind.Texture,
                "render-target:layer-target:card" to GPUMaterializedCommandOperandKind.RenderTarget,
                "render-target:layer-target:card" to GPUMaterializedCommandOperandKind.RenderTarget,
                "texture-view:layer-target:card" to GPUMaterializedCommandOperandKind.TextureView,
                "sampler:layer-target:card" to GPUMaterializedCommandOperandKind.Sampler,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label to binding.operand.kind },
        )
        assertContains(
            materialized.dumpLines(),
            "resource.materialization:operand operand=layer-target:card kind=texture " +
                "deviceGeneration=17 owner=GPURecorderScope usage=render_attachment,texture_binding " +
                "invalidation=layer-scope-end descriptor=${gate.targetDescriptorHash} " +
                "facts=allocation=create-texture;bounds=0,0,64,48;bytes=12288;format=rgba8unorm;" +
                "lifetime=layer-local;sampleCount=1;scope=layer:card",
        )

        assertEquals(
            listOf(
                "prepareLayerTarget",
                "beginRenderPass",
                "clearLayerTarget",
                "renderLayerChildren",
                "endRenderPass",
                "beginRenderPass",
                "compositeLayer",
                "endRenderPass",
            ),
            result.commandStream.commandLabels,
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command prepareLayerTarget target=layer-target:card descriptor=${gate.targetDescriptorHash} " +
                "usage=render_attachment,texture_binding bytes=12288",
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command-bridge packet=none command=clearLayerTarget operand=render-target:layer-target:card " +
                "kind=render-target deviceGeneration=17 owner=layer-target:card usage=render_attachment " +
                "invalidation=pass-end descriptor=${gate.targetDescriptorHash} " +
                "facts=clear=clear(transparent-black);load=clear;origin=device:0,0;scope=layer:card;store=store",
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command-bridge packet=none command=renderLayerChildren operand=render-target:layer-target:card " +
                "kind=render-target deviceGeneration=17 owner=layer-target:card usage=render_attachment " +
                "invalidation=pass-end descriptor=${gate.targetDescriptorHash} " +
                "facts=clear=clear(transparent-black);load=clear;origin=device:0,0;scope=layer:card;store=store",
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command compositeLayer source=layer-target:card parent=root-target " +
                "blend=srcOver route=fixed-function-srcOver token=layer-order:layer:card:restore",
        )
        assertContains(
            result.dumpLines(),
            "savelayer:materialization row=gpu-renderer.savelayer.live-materialization " +
                "scope=layer:card target=layer-target:card parent=root-target clear=clear(transparent-black) " +
                "children=draw-rect,draw-image composite=fixed-function-srcOver " +
                "adapterBacked=false productActivation=true",
        )
        assertFalse(result.dumpLines().joinToString("\n").contains("WGPU"))
        assertFalse(result.dumpLines().joinToString("\n").contains("@0x"))

        val skippedReadbackDump = skippedSaveLayerReadback().dumpLines().joinToString("\n")
        assertContains(skippedReadbackDump, "execution.readback:skipped")
        assertContains(skippedReadbackDump, "failureReason=kgpu-m11-006.adapter-readback-not-promoted")
        assertFalse(skippedReadbackDump.contains("execution.readback:completed"))
    }

    @Test
    fun `saveLayer live materialization refuses invalid resource and ordering facts`() {
        val gate = GPUSaveLayerIsolatedTargetPlanner().plan(saveLayerMaterializationGateRequest())
        val staleGate = GPUSaveLayerIsolatedTargetPlanner().plan(
            saveLayerMaterializationGateRequest(deviceGeneration = 16),
        )
        val cases = listOf(
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.allocation_unavailable",
                request = saveLayerMaterializationRequest(gate, allocationAvailable = false),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.target_usage_missing",
                request = saveLayerMaterializationRequest(gate, availableUsageLabels = setOf("render_attachment")),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.target_too_large",
                request = saveLayerMaterializationRequest(gate, targetBudgetBytes = 1024),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.target_generation_stale",
                request = saveLayerMaterializationRequest(gate, actualTargetGeneration = 16),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.target_generation_stale",
                request = saveLayerMaterializationRequest(staleGate),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.target_bounds_mismatch",
                request = saveLayerMaterializationRequest(gate, actualBoundsLabel = "0,0,32,48"),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.target_format_mismatch",
                request = saveLayerMaterializationRequest(gate, actualFormatClass = "bgra8unorm"),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.sample_count_mismatch",
                request = saveLayerMaterializationRequest(gate, actualSampleCount = 4),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.active_attachment_sampled",
                request = saveLayerMaterializationRequest(gate, activeParentAttachmentSampled = true),
            ),
            SaveLayerMaterializationRefusalCase(
                expectedCode = "unsupported.layer.parent_read_aliasing",
                request = saveLayerMaterializationRequest(gate, parentReadAliasing = true),
            ),
        )

        for (case in cases) {
            val result = ValidatingSaveLayerMaterializer().materialize(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode)
            assertContains(refused.dumpLines().joinToString("\n"), "resource.materialization:refused")
        }
    }

    @Test
    fun `saveLayer live materialization preserves gate refusals`() {
        val gate = GPUSaveLayerIsolatedTargetPlanner().plan(
            saveLayerMaterializationGateRequest(
                saveRecord = saveLayerMaterializationRecord(initWithPrevious = true),
            ),
        )

        val result = ValidatingSaveLayerMaterializer().materialize(
            request = saveLayerMaterializationRequest(gate),
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
        assertEquals("unsupported.layer.init_previous_unaccepted", refused.diagnostic.code)
        assertContains(
            result.dumpLines(),
            "savelayer:materialization.refused row=gpu-renderer.savelayer.live-materialization " +
                "scope=layer:card target=layer-target:card code=unsupported.layer.init_previous_unaccepted " +
                "adapterBacked=false productActivation=true",
        )
    }
}

private data class SaveLayerMaterializationRefusalCase(
    val expectedCode: String,
    val request: GPUSaveLayerMaterializationRequest,
)

private fun saveLayerMaterializationRequest(
    gate: GPUSaveLayerIsolatedTargetGatePlan,
    deviceGeneration: Long = 17,
    expectedTargetGeneration: Long = 17,
    actualTargetGeneration: Long = 17,
    availableUsageLabels: Set<String> = setOf("render_attachment", "texture_binding"),
    allocationAvailable: Boolean = true,
    targetBudgetBytes: Long = 16 * 1024 * 1024,
    activeParentAttachmentSampled: Boolean = false,
    parentReadAliasing: Boolean = false,
    actualBoundsLabel: String = gate.layerPlan.bounds.deviceBoundsLabel,
    actualFormatClass: String = "rgba8unorm",
    actualSampleCount: Int = 1,
): GPUSaveLayerMaterializationRequest =
    GPUSaveLayerMaterializationRequest(
        targetId = "root-target",
        taskIds = listOf("task-savelayer-card"),
        resourcePlanLabels = listOf("savelayer:layer-card"),
        gatePlan = gate,
        parentPassId = "parent-pass",
        childPassId = "layer-card-pass",
        childTargetStateHash = "layer-target-rgba8-premul-msaa1",
        parentTargetStateHash = "parent-target-rgba8-premul-msaa1",
        childLoadStoreLabel = "clear-store",
        parentLoadStoreLabel = "load-store",
        deviceGeneration = deviceGeneration,
        expectedTargetGeneration = expectedTargetGeneration,
        actualTargetGeneration = actualTargetGeneration,
        availableUsageLabels = availableUsageLabels,
        allocationAvailable = allocationAvailable,
        targetBudgetBytes = targetBudgetBytes,
        activeParentAttachmentSampled = activeParentAttachmentSampled,
        parentReadAliasing = parentReadAliasing,
        actualBoundsLabel = actualBoundsLabel,
        actualFormatClass = actualFormatClass,
        actualSampleCount = actualSampleCount,
    )

private fun saveLayerMaterializationGateRequest(
    saveRecord: GPULayerSaveRecord = saveLayerMaterializationRecord(),
    deviceGeneration: Long = 17,
): GPUSaveLayerIsolatedTargetRequest = GPUSaveLayerIsolatedTargetRequest(
    saveRecord = saveRecord,
    bounds = GPULayerBoundsPlan(
        requestedBoundsLabel = "card-local",
        deviceBoundsLabel = "0,0,64,48",
        conservative = true,
        originX = 0,
        originY = 0,
        width = 64,
        height = 48,
    ),
    parentTargetLabel = "root-target",
    deviceGeneration = deviceGeneration,
)

private fun saveLayerMaterializationRecord(
    initWithPrevious: Boolean = false,
): GPULayerSaveRecord = GPULayerSaveRecord(
    scopeId = GPULayerScopeID("layer:card"),
    parentScopeId = GPULayerScopeID("root"),
    boundsLabel = "card-local",
    childCommandIds = listOf("draw-rect", "draw-image"),
    initWithPrevious = initWithPrevious,
    backdropRequired = false,
)

private fun targetPreparationContext(): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-1",
        deviceGeneration = 17,
        budgetClass = "unit-test",
    )

private fun skippedSaveLayerReadback(): GPUReadbackResult.Skipped {
    val request = GPUReadbackRequest(
        requestId = "readback-savelayer-skipped",
        sourceLabel = "kgpu-m11-006-savelayer-materialization",
        boundsLabel = "0,0,64,48",
        format = "rgba8unorm",
        synchronizationLabel = "after-savelayer-composite",
        expectedArtifactLabel = "savelayer-card-composite.png",
        failureReason = "kgpu-m11-006.adapter-readback-not-promoted",
    )
    return GPUReadbackResult.Skipped(
        request = request,
        reasonCode = "unsupported.execution.readback_unavailable",
        diagnostics = listOf(GPUExecutionDiagnostic.readbackUnavailable(request, stage = "readback")),
    )
}
