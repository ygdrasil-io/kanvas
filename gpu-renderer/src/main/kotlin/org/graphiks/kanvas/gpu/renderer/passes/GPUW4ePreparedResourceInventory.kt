package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest

/**
 * Root-only, handle-free resource witness.  It carries the compiler-authenticated resource
 * declarations through lowering so native checkout cannot infer a different frame inventory from
 * the mix of clip consumers in a particular scene.
 */
internal class GPUW4ePreparedResourceInventory private constructor(
    private val preparations: List<PreparationFact>,
    private val renders: List<RenderFact>,
    private val readback: ReadbackFact,
) {
    private data class ResourceRefFact(val type: String, val value: String)

    private sealed interface DescriptorFact
    private data class TextureDescriptorFact(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val format: String,
        val sampleCount: Int,
    ) : DescriptorFact
    private data class BufferDescriptorFact(
        val byteSize: Long,
        val alignmentBytes: Long,
    ) : DescriptorFact

    private data class PreparationFact(
        val resource: ResourceRefFact,
        val descriptor: DescriptorFact,
        val role: String,
        val usages: Set<String>,
        val lifetime: String,
        val byteSize: Long,
        val diagnosticLabel: String,
    )

    private data class ResourceUseFact(
        val resource: ResourceRefFact,
        val role: String,
        val usage: String,
        val lifetime: String,
        val write: Boolean,
    )

    private data class RenderFact(
        val taskId: String,
        val passId: String,
        val commandIdValue: Int,
        val target: ResourceRefFact,
        val uses: List<ResourceUseFact>,
        val maskContinuation: GPUW4eMaskContinuationRequest?,
        val sceneContinuation: GPUW4eSceneContinuationRequest?,
    )

    private data class ReadbackFact(
        val source: ResourceRefFact,
        val staging: ResourceRefFact,
    )

    /** Exact resource, owner, continuation, and readback validation immediately before checkout. */
    internal fun validates(
        candidatePreparations: List<GPUResourcePreparationRequest>,
        candidateRenders: List<GPUFrameStep.RenderPassStep>,
        candidateReadback: GPUFrameStep.ReadbackCopyStep?,
    ): Boolean = candidatePreparations.map(::preparationFact) == preparations &&
        candidatePreparations.map { preparation -> preparation.resource }.toSet().size == candidatePreparations.size &&
        candidateRenders.map(::renderFact) == renders &&
        candidateReadback?.let(::readbackFact) == readback

    companion object {
        internal fun issue(
            graph: RenderGraph,
            refs: Map<String, GPUFrameResourceRef>,
            expectedPreparations: List<GPUResourcePreparationRequest>,
            expectedRenders: List<GPUTask.Render>,
        ): GPUW4ePreparedResourceInventory {
            val resources = graph.resources()
            require(refs.keys == resources.map { resource -> resource.id.value }.toSet()) {
                "W4e root inventory requires one exact reference for every graph resource"
            }
            require(refs.values.toSet().size == refs.size) {
                "W4e root inventory forbids logical resource aliasing"
            }
            require(expectedPreparations.map { preparation -> preparation.diagnosticLabel } ==
                resources.map { resource -> resource.id.value }) {
                "W4e root inventory preparations must retain compiler resource order"
            }
            require(expectedPreparations.map { preparation -> preparation.resource } ==
                resources.map { resource -> refs.getValue(resource.id.value) }) {
                "W4e root inventory preparations must retain exact compiler references"
            }
            val rootReadback = graph.passes().lastOrNull() as? PlanPass.ReadbackPass
                ?: throw IllegalArgumentException("W4e root inventory requires terminal readback")
            val source = refs.getValue(rootReadback.source.value) as? GPUFrameTargetRef
                ?: throw IllegalArgumentException("W4e root inventory readback source must be a target")
            val staging = refs.getValue(rootReadback.staging.value) as? GPUFrameBufferRef
                ?: throw IllegalArgumentException("W4e root inventory readback staging must be a buffer")
            return GPUW4ePreparedResourceInventory(
                expectedPreparations.map(::preparationFact),
                expectedRenders.map(::renderFact),
                ReadbackFact(resourceRefFact(source), resourceRefFact(staging)),
            )
        }

        private fun preparationFact(value: GPUResourcePreparationRequest): PreparationFact = PreparationFact(
            resourceRefFact(value.resource),
            descriptorFact(value),
            value.role.name,
            value.usages.map { usage -> usage.name }.toSet(),
            value.lifetime.name,
            value.byteSize,
            value.diagnosticLabel,
        )

        private fun descriptorFact(value: GPUResourcePreparationRequest): DescriptorFact = when (val descriptor = value.descriptor) {
            is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor -> TextureDescriptorFact(
                descriptor.logicalBounds.left,
                descriptor.logicalBounds.top,
                descriptor.logicalBounds.right,
                descriptor.logicalBounds.bottom,
                descriptor.format.value,
                descriptor.sampleCount,
            )
            is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor -> BufferDescriptorFact(
                descriptor.byteSize,
                descriptor.alignmentBytes,
            )
        }

        private fun renderFact(value: GPUTask.Render): RenderFact {
            val packet = requireNotNull(value.drawPackets.singleOrNull()) {
                "W4e root inventory requires one packet per render owner"
            }
            return RenderFact(
                value.taskId.value,
                packet.passId,
                packet.commandIdValue,
                resourceRefFact(value.target),
                value.resourceUses.map(::resourceUseFact),
                value.w4eMaskContinuation,
                value.w4eSceneContinuation,
            )
        }

        private fun renderFact(value: GPUFrameStep.RenderPassStep): RenderFact {
            val packet = value.drawPackets.singleOrNull() ?: return RenderFact(
                "", "", Int.MIN_VALUE, resourceRefFact(value.target), emptyList(), null, null,
            )
            return RenderFact(
                value.sourceTaskIds.singleOrNull()?.value.orEmpty(),
                packet.passId,
                packet.commandIdValue,
                resourceRefFact(value.target),
                value.resourceUses.map(::resourceUseFact),
                value.w4eMaskContinuation,
                value.w4eSceneContinuation,
            )
        }

        private fun readbackFact(value: GPUFrameStep.ReadbackCopyStep): ReadbackFact =
            ReadbackFact(resourceRefFact(value.source), resourceRefFact(value.staging))

        private fun resourceUseFact(value: GPUFrameResourceUse): ResourceUseFact = ResourceUseFact(
            resourceRefFact(value.resource), value.role.name, value.usage.name, value.lifetime.name, value.write,
        )

        private fun resourceRefFact(value: GPUFrameResourceRef): ResourceRefFact = ResourceRefFact(
            when (value) {
                is GPUFrameTargetRef -> "target"
                is GPUFrameBufferRef -> "buffer"
                is org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef -> "texture"
            },
            value.value,
        )
    }
}
