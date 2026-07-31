package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan

/** Closed consumer set for one shared CoverageMask producer topology. */
internal sealed interface GPUCoverageMaskConsumerDescriptor {
    val packet: GPUDrawPacket

    data class Core(override val packet: GPUDrawPacket) : GPUCoverageMaskConsumerDescriptor

    data class TextA8(override val packet: GPUDrawPacket) : GPUCoverageMaskConsumerDescriptor

    data class ColorGlyph(override val packet: GPUDrawPacket) : GPUCoverageMaskConsumerDescriptor
}

internal class GPUCoverageMaskProducerAttachment(
    val resource: GPUFrameTargetRef,
    val diagnosticLabel: String,
    val recordingId: GPURecordingID,
    producerTaskIds: List<GPUTaskID>,
    producerPacketPartitions: List<List<GPUDrawPacket>>,
    additionalProducerUses: List<GPUFrameResourceUse> = emptyList(),
) {
    val producerTaskIds = producerTaskIds.toList()
    val producerPacketPartitions = producerPacketPartitions.map(List<GPUDrawPacket>::toList)
    val additionalProducerUses = additionalProducerUses.toList()
}

internal data class GPUCoverageMaskProducerTopology(
    val planIdentity: String,
    val preparation: GPUResourcePreparationRequest,
    val allocation: GPUFrameMemoryAllocation,
    val producerRenders: List<GPUTask.Render>,
    val producerDependencies: List<GPUTaskDependency>,
    val consumerUse: GPUFrameResourceUse,
)

/**
 * Pure common CoverageMask producer topology for Core-only, Text-only, and Core|Text consumers.
 * Consumer-specific seals remain on their packets; this factory never inspects semantic payloads.
 */
internal fun buildCoverageMaskProducerTopology(
    plan: GPUClipExecutionPlan.CoverageMask,
    attachment: GPUCoverageMaskProducerAttachment,
    consumers: List<GPUCoverageMaskConsumerDescriptor>,
): GPUCoverageMaskProducerTopology {
    val identity = plan.canonicalIdentity()
    require(consumers.isNotEmpty() && consumers.all { consumer ->
        consumer.packet.clipExecutionPlan?.canonicalIdentity() == identity
    }) { "CoverageMask topology requires exact Core, TextA8, or ColorGlyph consumers of one plan" }
    require(
        attachment.producerTaskIds.size == attachment.producerPacketPartitions.size &&
            attachment.producerPacketPartitions.isNotEmpty() &&
            attachment.producerPacketPartitions.all(List<GPUDrawPacket>::isNotEmpty),
    ) { "CoverageMask topology requires one task identity per non-empty producer partition" }
    val producerPackets = attachment.producerPacketPartitions.flatten()
    require(producerPackets.size == plan.producers.size &&
        producerPackets.map { packet ->
            (packet.clipProducerAuthority as? GPUClipProducerAuthority.Mask)?.producer
        } == plan.producers &&
        producerPackets.all { packet ->
            packet.role == GPUDrawPacketRole.ClipProducer &&
                packet.clipExecutionPlan?.canonicalIdentity() == identity
        }
    ) { "CoverageMask topology requires the exact ordered producer packet authority" }
    require(attachment.diagnosticLabel.isNotBlank() &&
        attachment.additionalProducerUses.none { use ->
            use.role == GPUFrameResourceRole.ClipMask || use.resource == attachment.resource
        }
    ) { "CoverageMask topology attachment and additional producer uses must remain disjoint" }

    val writeUse = GPUFrameResourceUse(
        attachment.resource,
        GPUFrameResourceRole.ClipMask,
        GPUFrameResourceUsage.RenderAttachment,
        GPUFrameResourceLifetime.FrameLocal,
        true,
    )
    val producerUses = listOf(writeUse) + attachment.additionalProducerUses
    val producerRenders = attachment.producerPacketPartitions.mapIndexed { index, packets ->
        val taskId = attachment.producerTaskIds[index]
        GPUTask.Render(
            taskId = taskId,
            recordingId = attachment.recordingId,
            phase = GPUTaskPhase.Render,
            target = attachment.resource,
            loadStore = GPULoadStorePlan(
                loadOp = if (index == 0) "clear" else "load",
                storePlan = GPUStorePlan.Store,
                clearColorLabel = if (index == 0) {
                    CORE_PRIMITIVE_MASK_CLEAR_COLOR_LABEL
                } else {
                    null
                },
            ),
            samplePlan = GPUSamplePlan.SingleSampleFrame,
            resourceUses = producerUses,
            provisionalSegmentKey = GPUProvisionalRenderSegmentKey(
                "clip.mask.${taskId.value}",
            ),
            drawPackets = packets,
            batchEligibilityByPacketId = packets.associate { packet ->
                packet.packetId to GPUPassBatchEligibility(
                    kind = GPUPassBatchKind.SolidFill,
                    queueGuard = GPUPassBatchQueueGuard(emptyList(), emptyList()),
                )
            },
        )
    }
    val dependencies = producerRenders.zipWithNext().mapIndexed { index, (from, to) ->
        GPUTaskDependency(
            from.taskId,
            to.taskId,
            "clip-producer-consumer",
            GPUTaskUseToken(plan.orderingToken.value),
            "preserve.core-primitive.clip.mask-producer.$index",
        )
    }
    return GPUCoverageMaskProducerTopology(
        planIdentity = identity,
        preparation = GPUResourcePreparationRequest(
            resource = attachment.resource,
            descriptor = GPUFrameTextureDescriptor(
                logicalBounds = plan.bounds,
                format = GPUColorFormat.RGBA8Unorm,
                sampleCount = 1,
            ),
            role = GPUFrameResourceRole.ClipMask,
            usages = setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.TextureBinding,
            ),
            lifetime = GPUFrameResourceLifetime.FrameLocal,
            byteSize = plan.resolvedBytes,
            diagnosticLabel = attachment.diagnosticLabel,
        ),
        allocation = GPUFrameMemoryAllocation(
            label = attachment.diagnosticLabel,
            category = GPUFrameMemoryCategory.ReusableScratch,
            bytes = plan.resolvedBytes,
            resourceKind = GPUFrameMemoryResourceKind.Texture2D,
            extent = plan.bounds,
        ),
        producerRenders = producerRenders,
        producerDependencies = dependencies,
        consumerUse = writeUse.copy(
            usage = GPUFrameResourceUsage.TextureBinding,
            write = false,
        ),
    )
}
