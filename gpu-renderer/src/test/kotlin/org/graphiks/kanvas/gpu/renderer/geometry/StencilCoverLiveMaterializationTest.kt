package org.graphiks.kanvas.gpu.renderer.geometry

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

/** Verifies KGPU-M11-007 bounded stencil-cover live materialization contracts. */
class StencilCoverLiveMaterializationTest {
    @Test
    fun `accepted bounded path materializes stencil attachment and ordered producer cover commands`() {
        val gate = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilMaterializationShape,
            path = stencilMaterializationPath,
            evidence = completeStencilMaterializationEvidence,
        )

        val result = ValidatingStencilCoverMaterializer().materialize(
            request = stencilCoverMaterializationRequest(gate),
            context = targetPreparationContext(),
        )

        val materialized = assertIs<GPUResourceMaterializationDecision.Materialized>(result.resourceDecision)
        assertEquals(listOf(GPUTextureResourceRef("texture-ref:stencil-attachment:triangle")), materialized.resources)
        assertEquals(
            listOf(
                "stencil-attachment:triangle" to GPUMaterializedCommandOperandKind.Texture,
                "depth-stencil:stencil-attachment:triangle" to GPUMaterializedCommandOperandKind.DepthStencilAttachment,
                "pipeline:stencil-producer:path-triangle-v1" to GPUMaterializedCommandOperandKind.RenderPipeline,
                "depth-stencil:stencil-attachment:triangle" to GPUMaterializedCommandOperandKind.DepthStencilAttachment,
                "pipeline:stencil-cover:path-triangle-v1" to GPUMaterializedCommandOperandKind.RenderPipeline,
                "depth-stencil:stencil-attachment:triangle" to GPUMaterializedCommandOperandKind.DepthStencilAttachment,
            ),
            materialized.dumpOperandBridgeSnapshot.map { binding -> binding.operand.label to binding.operand.kind },
        )
        assertContains(
            materialized.dumpLines(),
            "resource.materialization:operand operand=depth-stencil:stencil-attachment:triangle " +
                "kind=depth-stencil-attachment deviceGeneration=17 owner=stencil-attachment:triangle " +
                "usage=render_attachment,stencil_attachment invalidation=pass-end " +
                "descriptor=depth-stencil:Depth24PlusStencil8:adapter-test-device " +
                "facts=clear=0;clearLoadStore=clear-stencil-store-color-discard-stencil;compare=equal;" +
                "format=Depth24PlusStencil8;load=clear;sampleCount=4;store=store;writeMask=0xff",
        )

        assertEquals(
            listOf(
                "prepareStencilAttachment",
                "beginRenderPass",
                "clearStencilAttachment",
                "stencilCoverProducer",
                "stencilCoverDraw",
                "endRenderPass",
            ),
            result.commandStream.commandLabels,
        )
        assertEquals(
            listOf("stencil-producer:path-triangle-v1", "stencil-cover:path-triangle-v1"),
            result.commandStream.sourcePacketIds.map { packetId -> packetId.value },
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command prepareStencilAttachment attachment=stencil-attachment:triangle " +
                "descriptor=depth-stencil:Depth24PlusStencil8:adapter-test-device " +
                "format=Depth24PlusStencil8 usage=render_attachment,stencil_attachment samples=4 bytes=4096",
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command stencilCoverProducer attachment=stencil-attachment:triangle " +
                "pipeline=pipeline:stencil-producer:path-triangle-v1 bounds=local[0,0,16,16] " +
                "state=write-increment-cover-equal token=producer-before-cover " +
                "packet=stencil-producer:path-triangle-v1",
        )
        assertContains(
            result.commandStream.dumpLines(),
            "passes.command stencilCoverDraw attachment=stencil-attachment:triangle " +
                "pipeline=pipeline:stencil-cover:path-triangle-v1 bounds=local[0,0,16,16] " +
                "compare=equal writeMask=0xff token=producer-before-cover " +
                "packet=stencil-cover:path-triangle-v1",
        )
        assertContains(
            result.dumpLines(),
            "stencil-cover:materialization row=gpu-renderer.path.stencil-cover.live " +
                "path=path:triangle:v1 attachment=stencil-attachment:triangle " +
                "producer=path-fill.stencil-producer cover=path-fill.cover-consumer " +
                "ordering=producer-before-cover clear=clear-stencil-store-color-discard-stencil " +
                "compare=equal writeMask=0xff sampleCount=4 adapterBacked=false productActivation=false",
        )
        assertFalse(result.dumpLines().joinToString("\n").contains("WGPU"))
        assertFalse(result.dumpLines().joinToString("\n").contains("@0x"))

        val skippedReadbackDump = skippedStencilCoverReadback().dumpLines().joinToString("\n")
        assertContains(skippedReadbackDump, "execution.readback:skipped")
        assertContains(skippedReadbackDump, "failureReason=kgpu-m11-007.adapter-readback-not-promoted")
        assertFalse(skippedReadbackDump.contains("execution.readback:completed"))
    }

    @Test
    fun `stencil cover live materialization refuses invalid resource and ordering facts`() {
        val gate = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilMaterializationShape,
            path = stencilMaterializationPath,
            evidence = completeStencilMaterializationEvidence,
        )
        val staleGate = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilMaterializationShape,
            path = stencilMaterializationPath,
            evidence = completeStencilMaterializationEvidence.copy(sampleCount = 1),
        )
        val cases = listOf(
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_unavailable",
                request = stencilCoverMaterializationRequest(gate, attachmentAvailable = false),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_pass_resources_missing",
                request = stencilCoverMaterializationRequest(gate, availableUsageLabels = setOf("render_attachment")),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_budget_exceeded",
                request = stencilCoverMaterializationRequest(gate, attachmentBudgetBytes = 1024),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_generation_stale",
                request = stencilCoverMaterializationRequest(gate, actualResourceGeneration = 16),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_sample_count_mismatch",
                request = stencilCoverMaterializationRequest(staleGate),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_bounds_mismatch",
                request = stencilCoverMaterializationRequest(gate, actualBoundsLabel = "local[0,0,32,32]"),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_depth_stencil_mismatch",
                request = stencilCoverMaterializationRequest(gate, actualDepthStencilFormat = "Stencil8"),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_compare_mismatch",
                request = stencilCoverMaterializationRequest(gate, stencilCompare = "always"),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_write_mask_mismatch",
                request = stencilCoverMaterializationRequest(gate, stencilWriteMask = "0x00"),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_clear_value_mismatch",
                request = stencilCoverMaterializationRequest(gate, stencilClearValue = 1),
            ),
            StencilCoverMaterializationRefusalCase(
                expectedCode = "unsupported.geometry.stencil_cover_ordering_illegal",
                request = stencilCoverMaterializationRequest(gate, producerBeforeCoverOrdering = false),
            ),
        )

        for (case in cases) {
            val result = ValidatingStencilCoverMaterializer().materialize(
                request = case.request,
                context = targetPreparationContext(),
            )

            val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
            assertContains(refused.diagnostics.map { diagnostic -> diagnostic.code }, case.expectedCode)
            assertContains(refused.dumpLines().joinToString("\n"), "resource.materialization:refused")
        }
    }

    @Test
    fun `stencil cover live materialization preserves gate refusals`() {
        val gate = GPUStencilCoverGatePlanner().plan(
            descriptor = stencilMaterializationShape,
            path = stencilMaterializationPath.copy(inverseFill = true),
            evidence = completeStencilMaterializationEvidence,
        )

        val result = ValidatingStencilCoverMaterializer().materialize(
            request = stencilCoverMaterializationRequest(gate),
            context = targetPreparationContext(),
        )

        val refused = assertIs<GPUResourceMaterializationDecision.Refused>(result.resourceDecision)
        assertEquals("unsupported.geometry.path_empty_inverse_unbounded", refused.diagnostic.code)
        assertContains(
            result.dumpLines(),
            "stencil-cover:materialization.refused row=gpu-renderer.path.stencil-cover.live " +
                "path=path:triangle:v1 attachment=stencil-attachment:triangle " +
                "code=unsupported.geometry.path_empty_inverse_unbounded " +
                "adapterBacked=false productActivation=false",
        )
    }
}

private data class StencilCoverMaterializationRefusalCase(
    val expectedCode: String,
    val request: GPUStencilCoverMaterializationRequest,
)

private fun stencilCoverMaterializationRequest(
    gate: GPUGeometryPlan,
    deviceGeneration: Long = 17,
    expectedResourceGeneration: Long = 17,
    actualResourceGeneration: Long = 17,
    availableUsageLabels: Set<String> = setOf("render_attachment", "stencil_attachment"),
    attachmentAvailable: Boolean = true,
    attachmentBudgetBytes: Long = 16 * 1024 * 1024,
    actualBoundsLabel: String = gate.descriptor.boundsLabel,
    actualDepthStencilFormat: String = "Depth24PlusStencil8",
    actualSampleCount: Int = 4,
    stencilCompare: String = "equal",
    stencilWriteMask: String = "0xff",
    stencilClearValue: Int = 0,
    producerBeforeCoverOrdering: Boolean = true,
): GPUStencilCoverMaterializationRequest =
    GPUStencilCoverMaterializationRequest(
        targetId = "root-target",
        taskIds = listOf("task-stencil-cover-triangle"),
        resourcePlanLabels = listOf("stencil-cover:path-triangle-v1"),
        geometryPlan = gate,
        passId = "stencil-cover-pass",
        targetStateHash = "target-rgba8-depth24plusstencil8-msaa4",
        loadStoreLabel = "clear-stencil-store-color-discard-stencil",
        deviceGeneration = deviceGeneration,
        expectedResourceGeneration = expectedResourceGeneration,
        actualResourceGeneration = actualResourceGeneration,
        availableUsageLabels = availableUsageLabels,
        attachmentAvailable = attachmentAvailable,
        attachmentByteEstimate = 4096,
        attachmentBudgetBytes = attachmentBudgetBytes,
        actualBoundsLabel = actualBoundsLabel,
        actualDepthStencilFormat = actualDepthStencilFormat,
        actualSampleCount = actualSampleCount,
        stencilCompare = stencilCompare,
        stencilWriteMask = stencilWriteMask,
        stencilClearValue = stencilClearValue,
        producerPipelineLabel = "pipeline:stencil-producer:path-triangle-v1",
        coverPipelineLabel = "pipeline:stencil-cover:path-triangle-v1",
        producerPacketId = "stencil-producer:path-triangle-v1",
        coverPacketId = "stencil-cover:path-triangle-v1",
        producerBeforeCoverOrdering = producerBeforeCoverOrdering,
    )

private val stencilMaterializationShape = GPUShapeDescriptor(
    shapeKind = "path-fill",
    boundsLabel = "local[0,0,16,16]",
    antiAliasMode = "coverage-aa",
    provenance = "unit-test",
)

private val stencilMaterializationPath = GPUPathDescriptor(
    pathKey = "path:triangle:v1",
    verbCount = 4,
    pointCount = 3,
    fillRule = "NonZero",
    inverseFill = false,
    finiteProof = "finite",
    volatility = "immutable",
    transformClass = "identity",
    edgeCount = 3,
)

private val completeStencilMaterializationEvidence = GPUStencilCoverEvidence(
    adapterEvidenceLabel = "adapter:wgpu4k:test-device",
    depthStencilCapability = true,
    depthStencilEvidenceLabel = "depth-stencil:Depth24PlusStencil8:adapter-test-device",
    sampleCount = 4,
    sampleCountEvidenceLabel = "sample-count:4x:adapter-test-device",
    stencilStateLabel = "write-increment-cover-equal",
    producerBeforeCoverOrdering = true,
    passResourceEvidenceLabel = "stencil-attachment:triangle",
    readbackEvidenceLabel = "readback:stencil-cover:triangle:v1",
    targetStateLabel = "offscreen-rgba8unorm-depth24plusstencil8",
    targetEvidenceLabel = "target:offscreen-rgba8unorm-depth24plusstencil8",
    targetSupportsStencilCover = true,
    clipStateLabel = "clip:device-rect:local[0,0,16,16]",
    clipSupportsStencilCover = true,
)

private fun targetPreparationContext(): GPUTargetPreparationContext =
    GPUTargetPreparationContext(
        targetId = "root-target",
        frameId = "frame-42",
        deviceGeneration = 17,
        budgetClass = "unit-test",
    )

private fun skippedStencilCoverReadback(): GPUReadbackResult.Skipped {
    val request = GPUReadbackRequest(
        requestId = "kgpu-m11-007-stencil-cover-readback",
        sourceLabel = "kgpu-m11-007-stencil-cover-materialization",
        boundsLabel = "local[0,0,16,16]",
        format = "rgba8unorm",
        synchronizationLabel = "after-stencil-cover",
        expectedArtifactLabel = "stencil-cover-triangle.png",
        failureReason = "kgpu-m11-007.adapter-readback-not-promoted",
    )
    return GPUReadbackResult.Skipped(
        request = request,
        reasonCode = "unsupported.execution.readback_unavailable",
        diagnostics = listOf(
            GPUExecutionDiagnostic.readbackUnavailable(
                request = request,
                stage = "readback",
            ),
        ),
    )
}
