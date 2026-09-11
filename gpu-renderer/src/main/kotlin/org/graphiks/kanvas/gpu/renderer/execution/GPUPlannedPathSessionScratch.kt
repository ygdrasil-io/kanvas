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

/** One native byte range that must be safe before any host allocation or pool checkout. */
internal data class GPUPlannedPathNativeByteRange(
    val resource: Resource,
    val offsetBytes: Long,
    val sizeBytes: Long,
    val capacityBytes: Long,
    val alignmentBytes: Long,
    val deviceLimitBytes: Long? = null,
    val requiresHostI32Addressing: Boolean = true,
) {
    enum class Resource {
        Vertex,
        Index,
        Uniform,
        Readback,
        DepthStencil,
    }
}

internal sealed interface GPUPlannedPathNativeByteRangeValidation {
    data object Accepted : GPUPlannedPathNativeByteRangeValidation

    data class Refused(
        val resource: GPUPlannedPathNativeByteRange.Resource,
        val reason: Reason,
    ) : GPUPlannedPathNativeByteRangeValidation

    enum class Reason {
        NonPositive,
        Misaligned,
        I64Overflow,
        CapacityUndersized,
        DeviceLimitExceeded,
        I32HostOverflow,
    }
}

/**
 * Shared numeric gate for W4c/W4d native packing.  It is deliberately handle-free so every
 * byte/range boundary can be proved without allocating pathological multi-gigabyte arrays.
 */
internal fun validatePlannedPathNativeByteRanges(
    ranges: List<GPUPlannedPathNativeByteRange>,
): GPUPlannedPathNativeByteRangeValidation {
    for (range in ranges) {
        if (range.offsetBytes < 0L || range.sizeBytes <= 0L || range.capacityBytes <= 0L ||
            range.alignmentBytes <= 0L
        ) {
            return GPUPlannedPathNativeByteRangeValidation.Refused(
                range.resource,
                GPUPlannedPathNativeByteRangeValidation.Reason.NonPositive,
            )
        }
        if (range.offsetBytes % range.alignmentBytes != 0L ||
            range.sizeBytes % range.alignmentBytes != 0L
        ) {
            return GPUPlannedPathNativeByteRangeValidation.Refused(
                range.resource,
                GPUPlannedPathNativeByteRangeValidation.Reason.Misaligned,
            )
        }
        val end = try {
            Math.addExact(range.offsetBytes, range.sizeBytes)
        } catch (_: ArithmeticException) {
            return GPUPlannedPathNativeByteRangeValidation.Refused(
                range.resource,
                GPUPlannedPathNativeByteRangeValidation.Reason.I64Overflow,
            )
        }
        if (end > range.capacityBytes) {
            return GPUPlannedPathNativeByteRangeValidation.Refused(
                range.resource,
                GPUPlannedPathNativeByteRangeValidation.Reason.CapacityUndersized,
            )
        }
        if (range.deviceLimitBytes?.let { limit -> range.capacityBytes > limit } == true) {
            return GPUPlannedPathNativeByteRangeValidation.Refused(
                range.resource,
                GPUPlannedPathNativeByteRangeValidation.Reason.DeviceLimitExceeded,
            )
        }
        if (range.requiresHostI32Addressing && end > Int.MAX_VALUE.toLong()) {
            return GPUPlannedPathNativeByteRangeValidation.Refused(
                range.resource,
                GPUPlannedPathNativeByteRangeValidation.Reason.I32HostOverflow,
            )
        }
    }
    return GPUPlannedPathNativeByteRangeValidation.Accepted
}

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
        val producerUniformSlotIndex: Int?,
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
        fun from(scratch: org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.NativePath,
            w5b: org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3): GPUPlannedPathSessionScratch = when (scratch) {
                is org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.PathFill -> from(scratch.authority, w5b)
                is org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.PathStroke -> from(scratch.authority, w5b)
            }

        fun from(scratch: W4cSessionScratchV1,
            w5b: org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3? = null): GPUPlannedPathSessionScratch =
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
                        draw.producerUniformSlotIndex,
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
                    (if (w5b == null) authority.w4cSessionScratch === scratch else
                        authority.w5bFrameWitnessV3 === w5b && w5b.geometryLanes.any {
                            it is org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.PathFill && it.authority === scratch
                        }) &&
                        authority.w3SessionScratch == null && authority.w4aSessionScratch == null &&
                        authority.w4bSessionScratch == null && authority.w4dSessionScratch == null
                },
            )

        fun from(scratch: W4dSessionScratchV1,
            w5b: org.graphiks.kanvas.gpu.renderer.passes.W5bPreparedFrameWitnessV3? = null): GPUPlannedPathSessionScratch =
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
                        draw.producerUniformSlotIndex,
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
                    (if (w5b != null) authority.w5bFrameWitnessV3 === w5b &&
                        w5b.geometryLanes.any { it is org.graphiks.kanvas.gpu.renderer.passes.W5bGeometryScratchV3.PathStroke && it.authority === scratch }
                    else authority.w4dSessionScratch === scratch) &&
                        authority.w3SessionScratch == null && authority.w4aSessionScratch == null &&
                        authority.w4bSessionScratch == null && authority.w4cSessionScratch == null
                },
            )
    }
}
