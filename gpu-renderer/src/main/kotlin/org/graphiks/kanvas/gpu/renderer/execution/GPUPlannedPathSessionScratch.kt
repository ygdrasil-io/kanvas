package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PlanPassId
import org.graphiks.kanvas.gpu.plan.PlanResourceId
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitivePreparedPacketAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.W4cSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.passes.W4dSessionScratchV1
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUCorePrimitiveFramePoolCapacities
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.math.geometry.PathFillGeometryF32

/** Execution-only view over the already sealed W4c/W4d path authority. */
internal class GPUPlannedPathSessionScratch private constructor(
    val lane: Lane,
    val planId: String,
    val capabilitySealHash: String,
    val deviceGeneration: Long,
    val target: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val targetBounds: GPUPixelBounds,
    val vertexResourceId: PlanResourceId,
    val indexResourceId: PlanResourceId,
    val uniformResourceId: PlanResourceId,
    val depthStencilResourceId: PlanResourceId?,
    val targetBytes: Long?,
    val stagingBytes: Long?,
    val capabilityId: String?,
    val draws: List<Draw>,
    val uniformPlan: GPUUniformSlabPlan,
    val uniformStrideBytes: Long,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformUsefulBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val depthStencilBytes: Long,
    val poolCapacities: GPUCorePrimitiveFramePoolCapacities,
    val maxBufferSize: Long,
    val maxDynamicUniformBuffersPerPipelineLayout: Long,
    val renderPassIds: List<PlanPassId>?,
    val readbackPassId: PlanPassId?,
    val resourceLastPassIndexExclusive: Int?,
    val depthStencilFirstPassIndex: Int?,
    private val exactEnvelope: (
        expectedPlanId: String,
        expectedCapabilityHash: String,
        expectedGeneration: Long,
        expectedTarget: GPUFrameTargetRef,
        expectedStaging: GPUFrameBufferRef,
        expectedBounds: GPUPixelBounds,
    ) -> Boolean,
    private val packetAuthority: (
        packet: GPUDrawPacket,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        renderPipelineKey: GPURenderPipelineKey,
    ) -> Boolean,
    private val authorityOwner: (GPUCorePrimitivePreparedPacketAuthority) -> Boolean,
) {
    enum class Lane(
        val label: String,
        val pathDrawLabel: String,
        val uniformPayloadBytes: Long,
    ) {
        W4c("w4c", "path-fill-draw", W4cSessionScratchV1.UNIFORM_PAYLOAD_BYTES),
        W4d("w4d", "path-draw", W4dSessionScratchV1.UNIFORM_PAYLOAD_BYTES),
    }

    internal data class Draw(
        val commandId: Int,
        val strategy: PathFillStrategy,
        private val geometryF32: PathFillGeometryF32,
        private val scissorBounds: GPUPixelBounds,
        val vertexOffsetBytes: Long,
        val vertexRangeBytes: Long,
        val indexOffsetBytes: Long,
        val indexRangeBytes: Long,
        val uniformSlotIndex: Int,
        val atomicGroupId: String?,
    ) {
        fun copyGeometryF32(): PathFillGeometryF32 = geometryF32
        fun copyScissorBounds(): GPUPixelBounds = scissorBounds.copy()
    }

    fun matches(
        expectedPlanId: String,
        expectedCapabilityHash: String,
        expectedGeneration: Long,
        expectedTarget: GPUFrameTargetRef,
        expectedStaging: GPUFrameBufferRef,
        expectedBounds: GPUPixelBounds,
    ): Boolean = exactEnvelope(
        expectedPlanId,
        expectedCapabilityHash,
        expectedGeneration,
        expectedTarget,
        expectedStaging,
        expectedBounds,
    )

    fun owns(authority: GPUCorePrimitivePreparedPacketAuthority): Boolean = authorityOwner(authority)

    fun matchesPreparedPacket(
        packet: GPUDrawPacket,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        renderPipelineKey: GPURenderPipelineKey,
    ): Boolean = packetAuthority(packet, structuralPipelineKey, renderPipelineKey)

    companion object {
        fun from(scratch: W4cSessionScratchV1): GPUPlannedPathSessionScratch =
            GPUPlannedPathSessionScratch(
                lane = Lane.W4c,
                planId = scratch.planId,
                capabilitySealHash = scratch.capabilitySealHash,
                deviceGeneration = scratch.deviceGeneration,
                target = scratch.target,
                staging = scratch.staging,
                targetBounds = scratch.targetBounds,
                vertexResourceId = scratch.vertexResourceId,
                indexResourceId = scratch.indexResourceId,
                uniformResourceId = scratch.uniformResourceId,
                depthStencilResourceId = scratch.depthStencilResourceId,
                targetBytes = null,
                stagingBytes = null,
                capabilityId = null,
                draws = scratch.draws.map { draw ->
                    Draw(
                        draw.commandId,
                        draw.strategy,
                        draw.copyGeometryF32(),
                        draw.copyScissorBounds(),
                        draw.vertexOffsetBytes,
                        draw.vertexRangeBytes,
                        draw.indexOffsetBytes,
                        draw.indexRangeBytes,
                        draw.uniformSlotIndex,
                        draw.atomicGroupId,
                    )
                },
                uniformPlan = scratch.uniformPlan,
                uniformStrideBytes = scratch.uniformStrideBytes,
                vertexUsefulBytes = scratch.vertexUsefulBytes,
                indexUsefulBytes = scratch.indexUsefulBytes,
                uniformUsefulBytes = scratch.uniformUsefulBytes,
                vertexCapacityBytes = scratch.vertexCapacityBytes,
                indexCapacityBytes = scratch.indexCapacityBytes,
                uniformCapacityBytes = scratch.uniformCapacityBytes,
                depthStencilBytes = scratch.depthStencilBytes,
                poolCapacities = scratch.poolCapacities,
                maxBufferSize = scratch.maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout =
                    scratch.maxDynamicUniformBuffersPerPipelineLayout,
                renderPassIds = null,
                readbackPassId = null,
                resourceLastPassIndexExclusive = null,
                depthStencilFirstPassIndex = null,
                exactEnvelope = scratch::matches,
                packetAuthority = { packet, structural, render ->
                    scratch.matchesPreparedPacket(
                        scratch.planId,
                        scratch.capabilitySealHash,
                        packet,
                        structural,
                        render,
                    )
                },
                authorityOwner = { authority ->
                    authority.w4cSessionScratch === scratch &&
                        authority.w3SessionScratch == null && authority.w4aSessionScratch == null &&
                        authority.w4bSessionScratch == null && authority.w4dSessionScratch == null
                },
            )

        fun from(scratch: W4dSessionScratchV1): GPUPlannedPathSessionScratch =
            GPUPlannedPathSessionScratch(
                lane = Lane.W4d,
                planId = scratch.planId,
                capabilitySealHash = scratch.capabilitySealHash,
                deviceGeneration = scratch.deviceGeneration,
                target = scratch.target,
                staging = scratch.staging,
                targetBounds = scratch.targetBounds,
                vertexResourceId = scratch.vertexResourceId,
                indexResourceId = scratch.indexResourceId,
                uniformResourceId = scratch.uniformResourceId,
                depthStencilResourceId = scratch.depthStencilResourceId,
                targetBytes = scratch.targetBytes,
                stagingBytes = scratch.stagingBytes,
                capabilityId = scratch.capabilityId,
                draws = scratch.draws.map { draw ->
                    Draw(
                        draw.commandId,
                        draw.strategy,
                        draw.copyGeometryF32(),
                        draw.copyScissorBounds(),
                        draw.vertexOffsetBytes,
                        draw.vertexRangeBytes,
                        draw.indexOffsetBytes,
                        draw.indexRangeBytes,
                        draw.uniformSlotIndex,
                        draw.atomicGroupId,
                    )
                },
                uniformPlan = scratch.uniformPlan,
                uniformStrideBytes = scratch.uniformStrideBytes,
                vertexUsefulBytes = scratch.vertexUsefulBytes,
                indexUsefulBytes = scratch.indexUsefulBytes,
                uniformCapacityBytes = scratch.uniformCapacityBytes,
                uniformUsefulBytes = scratch.uniformUsefulBytes,
                vertexCapacityBytes = scratch.vertexCapacityBytes,
                indexCapacityBytes = scratch.indexCapacityBytes,
                depthStencilBytes = scratch.depthStencilBytes,
                poolCapacities = scratch.poolCapacities,
                maxBufferSize = scratch.maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout =
                    scratch.maxDynamicUniformBuffersPerPipelineLayout,
                renderPassIds = scratch.renderPassIds,
                readbackPassId = scratch.readbackPassId,
                resourceLastPassIndexExclusive = scratch.resourceLastPassIndexExclusive,
                depthStencilFirstPassIndex = scratch.depthStencilFirstPassIndex,
                exactEnvelope = scratch::matches,
                packetAuthority = { packet, structural, render ->
                    scratch.matchesPreparedPacket(
                        scratch.planId,
                        scratch.capabilitySealHash,
                        packet,
                        structural,
                        render,
                    )
                },
                authorityOwner = { authority ->
                    authority.w4dSessionScratch === scratch &&
                        authority.w3SessionScratch == null && authority.w4aSessionScratch == null &&
                        authority.w4bSessionScratch == null && authority.w4cSessionScratch == null
                },
            )
    }
}
