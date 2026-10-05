package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload

/** Common W4d phase check; each envelope separately authenticates ownership and pass order. */
internal fun w4dGeneralEntryMatches(packet: GPUDrawPacket, fact: W4dGeneralNativePathPassFact,
    structural: GPUCorePrimitiveRenderPipelineStructuralKey?, expected: GPUCorePrimitiveRenderPipelineStructuralKey?): Boolean =
    packet.passId == fact.pathPassId && packet.commandIdValue == fact.commandIdValue &&
        structural != null && structural == expected && structural.sampleCount == fact.sampleCountI32 &&
        packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive

/** Builds commands over already-bound resources. It never allocates a pool or chooses geometry. */
internal fun w4dGeneralRenderOperand(
    sourceStepIndex: Int,
    semantic: GPUDrawSemanticPayload.CorePrimitive,
    config: GPUPreparedNativeRenderPassConfig,
    pipeline: GPUPreparedNativeRenderPipelineOperand,
    bindGroup: GPUPreparedNativeBindGroupOperand,
    uniformOffset: Long,
    vertex: GPUPreparedNativeBufferOperand,
    vertexBytes: Long,
    index: GPUPreparedNativeBufferOperand,
    indexBytes: Long,
    slice: W4dGeneralNativeGeometrySlice,
    owner: PlanPass? = null,
): GPUPreparedNativeScopeOperand.Render = GPUPreparedNativeScopeOperand.Render(
    sourceStepIndex = sourceStepIndex, pass = config,
    commands = listOf(
        GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
        GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup, listOf(uniformOffset)),
        GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0L, vertexBytes, 8L),
        GPUPreparedNativeRenderCommand.SetIndexBuffer(index, GPUPreparedNativeIndexFormat.Uint32, 0L, indexBytes),
        GPUPreparedNativeRenderCommand.SetScissor(semantic.scissorBounds.left, semantic.scissorBounds.top,
            semantic.scissorBounds.width, semantic.scissorBounds.height),
        GPUPreparedNativeRenderCommand.DrawIndexed(GPUPreparedNativeDrawCall.DrawIndexed(
            indexCount = slice.indexCount, firstIndex = slice.firstIndex, baseVertex = slice.baseVertex,
            vertexCount = slice.vertexCount, maxLocalIndex = slice.maxLocalIndex)),
    ), semanticPayloads = listOf(semantic), operandLayout = GPUPreparedNativeRenderOperandLayout.CommandOrder,
    w6aPassV1 = owner,
)
