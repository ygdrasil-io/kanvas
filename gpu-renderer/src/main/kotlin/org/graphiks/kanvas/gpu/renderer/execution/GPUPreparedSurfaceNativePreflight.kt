package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUTextureFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.IdentityHashMap
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedImageUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderProgram
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesShaderResult
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUPreparedVerticesUploadArtifact
import org.graphiks.kanvas.gpu.renderer.artifacts.PreparedVerticesShaderAssembler
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.collections.immutableMap
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageMaskProducerUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.validateCoverageMaskProducerUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatch
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.passes.GPUPreparedImageClipAuthorityValidation
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.passes.fromBatchPlan
import org.graphiks.kanvas.gpu.renderer.passes.validatePreparedImageClipAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageSampling
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameReadbackRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUFramePlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextCompositePreflight
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextCompositePreflightRefusalCodes
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextClipPlan
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextBindingPreflightSeal
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedColorGlyphAnalyticRectClipFacts
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedColorGlyphClipPreflightSeal
import org.graphiks.kanvas.gpu.renderer.recording.matchesPreparedColorGlyphClip
import org.graphiks.kanvas.gpu.renderer.recording.matchesPreparedColorGlyphCoverageMaskProducer
import org.graphiks.kanvas.gpu.renderer.recording.GPUPreparedTextRenderBinding
import org.graphiks.kanvas.gpu.renderer.recording.preparedTextNativeBlendDomainRefusal
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackLayout
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackPixelFormat
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputDescriptor
import org.graphiks.kanvas.gpu.renderer.recording.GPUSurfaceOutputRef
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskDependency
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskUseToken
import org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind
import org.graphiks.kanvas.gpu.renderer.recording.PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedConcreteResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUPreparedImageUniformAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUReadbackStagingLease
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.r8ArtifactIdentity
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseCacheResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceLeaseKind
import org.graphiks.kanvas.gpu.renderer.resources.GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.preparedImageDescriptorHash
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUVerticesStagingRange
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesStagingLayout

/**
 * Sole canonical authority for the prepared-text preflight refusal surface.
 *
 * Tests consume these constants instead of maintaining a parallel string
 * table. Later prepared-text work may reuse them but must not redefine them.
 */
internal object GPUPreparedTextPreflightRefusalCodes {
    const val PREPARED_TEXT_UNMATERIALIZED =
        "unsupported.preflight.prepared_text_unmaterialized"
    const val STALE_ATLAS_GENERATION = "stale.preflight.text.atlas_generation"
    const val PAGE_BYTES = "invalid.preflight.text.page_bytes"
    const val PAGE_DIMENSIONS = "invalid.preflight.text.page_dimensions"
    const val PAGE_ROW_BYTES = "invalid.preflight.text.page_row_bytes"
    const val R8UNORM = "unsupported.preflight.text.r8unorm"
    const val INSTANCE_UV = "invalid.preflight.text.instance_uv"
    const val INSTANCE_STRIDE = "invalid.preflight.text.instance_stride"
    const val INSTANCE_RANGE_OVERLAP = "invalid.preflight.text.instance_range_overlap"
    const val INSTANCE_BUFFER_RANGE = "invalid.preflight.text.instance_buffer_range"
    const val MATERIAL_ABI = "invalid.preflight.text.material_abi"
    const val WGSL_ENTRY_POINT = "invalid.preflight.text.wgsl_entry_point"
    const val BINDING_LAYOUT = "invalid.preflight.text.binding_layout"
    const val MATERIAL_UNIFORMS = "invalid.preflight.text.material_uniforms"
    const val MATERIAL_RESOURCES = "invalid.preflight.text.material_resources"
    const val UPLOAD_MISSING = "invalid.preflight.text.upload_missing"
    const val UPLOAD_DUPLICATE = "invalid.preflight.text.upload_duplicate"
    const val UPLOAD_ORDER = "invalid.preflight.text.upload_order"
    const val TARGET = "invalid.preflight.text.target"
    const val SCISSOR = "invalid.preflight.text.scissor"
    const val CLIP = "invalid.preflight.text.clip"
    const val BLEND = GPUPreparedTextCompositePreflightRefusalCodes.NATIVE_BLEND
    const val RESOURCE_LIFETIME = "invalid.preflight.text.resource_lifetime"
    const val DEPENDENCY = "invalid.preflight.text.dependency"
    const val OPERAND = "invalid.preflight.text.operand"
    const val OPERAND_OWNERSHIP = "invalid.preflight.text.operand_ownership"
    const val TEXTURE_LIMIT = "unsupported.preflight.text.texture_limit"
    const val INSTANCE_BUFFER_LIMIT = "unsupported.preflight.text.instance_buffer_limit"
    const val COPY_ALIGNMENT = "unsupported.preflight.text.copy_alignment"
}

/**
 * Sole canonical authority for the prepared-vertices preflight refusal surface.
 *
 * Tests consume these constants instead of maintaining a parallel string
 * table. Shader and layout boundaries reuse the canonical refusal code
 * carried by the Task 8 WGSL assembler result where the assembler already
 * names the same boundary.
 */
internal object GPUPreparedVerticesPreflightRefusalCodes {
    const val SEMANTIC = "invalid.prepared-surface.vertices-semantic"
    const val IDENTITY = "invalid.prepared-surface.vertices-identity"
    const val HASH = "invalid.prepared-surface.vertices-hash"
    const val ARTIFACT = "invalid.prepared-surface.vertices-artifact"
    const val TOPOLOGY = "unsupported.prepared-surface.vertices-topology"
    const val INDEX_FORMAT = "unsupported.prepared-surface.vertices-index-format"
    const val TRANSFORM = "invalid.prepared-surface.vertices-transform"
    const val BOUNDS = "invalid.prepared-surface.vertices-bounds"
    const val MATERIAL_ABI = "invalid.prepared-surface.vertices-material-abi"
    const val UPLOAD_MISSING = "invalid.prepared-surface.vertices-upload-missing"
    const val UPLOAD_DUPLICATE = "invalid.prepared-surface.vertices-upload-duplicate"
    const val UPLOAD_ORDER = "invalid.prepared-surface.vertices-upload-order"
    const val UPLOAD_LAYOUT = "invalid.prepared-surface.vertices-upload-layout"
    const val USAGE = "invalid.prepared-surface.vertices-usage"
    const val GENERATION = "stale.prepared-surface.vertices-generation"
    const val DEPENDENCY = "invalid.prepared-surface.vertices-dependency"
    const val BUDGET = "unsupported.prepared-surface.vertices-budget"
    const val TARGET = "invalid.prepared-surface.vertices-target"
}

private data class GPUPreparedSurfaceArtifactByteEvidence(
    val tightRgba8Bytes: ByteArray,
    val contentHash: String,
)

private data class GPUPreparedTextMaterialEvidence(
    val wgslSourceHash: String,
    val uniformBytes: ByteArray,
    val uniformContentHash: String,
)

private data class GPUPreparedTextMaterialEvidenceKey(
    val materialKey: String,
    val abiHash: String,
    val wgslSource: String,
    val uniformBytes: List<Int>,
)

private data class GPUPreparedTextUniformSlabEvidenceKey(
    val bufferRef: GPUFrameBufferRef,
    val contentHash: String,
    val byteSize: Long,
    val alignmentBytes: Long,
)

internal enum class GPUPreparedTextImmutableEvidenceKind {
    AtlasBytes,
    MaterialWgslHash,
    MaterialUniformBytes,
    UniformSlabBytes,
}

internal sealed interface GPUPreparedSurfaceNativeRunPlan {
    data class Core(val plan: GPUCorePrimitiveRenderRunPlan) :
        GPUPreparedSurfaceNativeRunPlan

    data class Image(val plan: GPUPreparedSurfaceImageRenderRunPlan) :
        GPUPreparedSurfaceNativeRunPlan

    data class Vertices(val plan: GPUPreparedVerticesRenderRunPlan) :
        GPUPreparedSurfaceNativeRunPlan
}

/**
 * Exact per-draw native facts derived from one prepared-vertices artifact.
 *
 * The materializer consumes these to issue one `draw` or `drawIndexed` call
 * with the exact vertex/index counts and formats; the preflight derives them
 * solely from immutable artifacts and their sealed frame resource plans.
 */
internal data class GPUPreparedVerticesDrawFacts(
    val packetId: GPUDrawPacketID,
    val artifactKey: String,
    val vertexCount: Int,
    val indexCount: Int?,
    val indexFormat: String?,
    val vertexByteCount: Long,
    val indexByteCount: Long?,
)

internal class GPUPreparedVerticesRenderRunPlan(
    val sourceScopeIndex: Int,
    val renderStep: GPUFrameStep.RenderPassStep,
    packets: List<GPUDrawSemanticPayload.Vertices>,
    resourcePlans: List<GPUVerticesFrameResourcePlan>,
    drawFacts: List<GPUPreparedVerticesDrawFacts>,
    shaderProgramByPacketId: Map<GPUDrawPacketID, GPUPreparedVerticesShaderProgram>,
    val exactScopeKey: GPUPreparedNativeScopeKey,
    uploadScopeKeys: List<GPUPreparedNativeScopeKey> = emptyList(),
) {
    val packets: List<GPUDrawSemanticPayload.Vertices> = immutableList(packets)
    val resourcePlans: List<GPUVerticesFrameResourcePlan> = immutableList(resourcePlans)
    val drawFacts: List<GPUPreparedVerticesDrawFacts> = immutableList(drawFacts)
    val shaderProgramByPacketId: Map<GPUDrawPacketID, GPUPreparedVerticesShaderProgram> =
        immutableMap(shaderProgramByPacketId)
    val uploadScopeKeys: List<GPUPreparedNativeScopeKey> = immutableList(uploadScopeKeys)

    val exactScopeKeys: List<GPUPreparedNativeScopeKey> =
        immutableList(uploadScopeKeys + exactScopeKey)

    init {
        require(sourceScopeIndex >= 0 &&
            exactScopeKey.sourceStepIndex == sourceScopeIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Render &&
            exactScopeKey.operandKeys.isNotEmpty()
        ) {
            "A prepared-vertices run must retain one exact render-only scope"
        }
        require(this.uploadScopeKeys.all { key ->
            key.operationKind == GPUEncoderOperationKind.Upload &&
                key.sourceStepIndex != exactScopeKey.sourceStepIndex
        }) {
            "A prepared-vertices run must retain only exact upload scopes before its render scope"
        }
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex)
            .distinct().size == this.exactScopeKeys.size
        ) {
            "A prepared-vertices run must retain one exact unique scope per step"
        }
        require(this.packets.isNotEmpty() &&
            renderStep.drawPackets.mapNotNull(GPUDrawPacket::semanticPayload) == this.packets &&
            this.drawFacts.map(GPUPreparedVerticesDrawFacts::packetId) ==
            renderStep.drawPackets.map(GPUDrawPacket::packetId) &&
            this.shaderProgramByPacketId.keys ==
            this.drawFacts.map(GPUPreparedVerticesDrawFacts::packetId).toSet() &&
            this.resourcePlans.map(GPUVerticesFrameResourcePlan::artifactKey)
                .distinct().size == this.resourcePlans.size &&
            this.packets.map { packet -> packet.artifact.key }.toSet() ==
            this.resourcePlans.map(GPUVerticesFrameResourcePlan::artifactKey).toSet() &&
            this.drawFacts.map(GPUPreparedVerticesDrawFacts::artifactKey) ==
            this.packets.map { packet -> packet.artifact.key } &&
            this.drawFacts.zip(this.packets).all { (fact, packet) ->
                fact.vertexCount == packet.artifact.vertexCount &&
                    fact.indexCount == packet.artifact.indexCount &&
                    fact.indexFormat == packet.artifact.indexFormat &&
                    fact.vertexByteCount ==
                    this.resourcePlans.single { plan -> plan.artifactKey == fact.artifactKey }
                        .vertexBuffer.byteCount &&
                    fact.indexByteCount ==
                    this.resourcePlans.single { plan -> plan.artifactKey == fact.artifactKey }
                        .indexBuffer?.byteCount
            }
        ) {
            "A prepared-vertices render run must retain exact packets, artifacts, and draw facts"
        }
    }
}

internal class GPUPreparedSurfaceImageFramePlan(
    val resourcePlan: GPUImageFrameResourcePlan,
    val uploadScopeKey: GPUPreparedNativeScopeKey,
    consumerRenderScopeIndices: List<Int>,
) {
    val consumerRenderScopeIndices: List<Int> =
        immutableList(consumerRenderScopeIndices)

    init {
        require(uploadScopeKey.operationKind == GPUEncoderOperationKind.Upload)
        require(this.consumerRenderScopeIndices.isNotEmpty() &&
            this.consumerRenderScopeIndices.distinct().size ==
            this.consumerRenderScopeIndices.size &&
            this.consumerRenderScopeIndices.all { it > uploadScopeKey.sourceStepIndex }
        ) {
            "One frame-global image upload must precede every unique consuming render"
        }
    }
}

internal class GPUPreparedSurfaceImageRenderRunPlan(
    val sourceScopeIndex: Int,
    val renderStep: GPUFrameStep.RenderPassStep,
    packets: List<GPUDrawSemanticPayload.SampledImage>,
    resourcePlans: List<GPUImageFrameResourcePlan>,
    orderedBindings: List<org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingRequest>,
    uniformAllocations: List<GPUPreparedImageUniformAllocation>,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    val sourceScopeIndices: List<Int> = listOf(sourceScopeIndex)
    val packets: List<GPUDrawSemanticPayload.SampledImage> = immutableList(packets)
    val resourcePlans: List<GPUImageFrameResourcePlan> = immutableList(resourcePlans)
    val artifactKeys = immutableList(resourcePlans.map { plan -> plan.artifactKey })
    val orderedBindings = immutableList(orderedBindings)
    val uniformAllocations: List<GPUPreparedImageUniformAllocation> =
        immutableList(uniformAllocations)

    init {
        require(sourceScopeIndex >= 0 &&
            exactScopeKey.sourceStepIndex == sourceScopeIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Render &&
            exactScopeKey.operandKeys.isNotEmpty()
        ) {
            "A prepared-image run must retain one exact render-only scope"
        }
        require(this.packets.isNotEmpty() &&
            renderStep.drawPackets.mapNotNull(GPUDrawPacket::semanticPayload) == this.packets &&
            this.orderedBindings.map { binding -> binding.packetId } ==
            renderStep.drawPackets.map { packet -> packet.packetId.value } &&
            this.packets.size == this.uniformAllocations.size &&
            this.orderedBindings.map { binding -> binding.uniformAllocation } ==
            this.uniformAllocations &&
            this.artifactKeys.isNotEmpty() &&
            this.artifactKeys.distinct().size == this.artifactKeys.size &&
            this.packets.map { packet -> packet.artifact.key.value }.toSet() ==
            this.artifactKeys.map { key -> key.value }.toSet()
        ) {
            "A prepared-image render run must retain exact packets, artifacts, and ABI allocations"
        }
    }
}

internal class GPUCorePrimitiveRenderRunPlan(
    sourceScopeIndices: List<Int>,
    packetIds: List<GPUDrawPacketID>,
    val renderStep: GPUFrameStep.RenderPassStep,
    preparationRequests: List<GPUResourcePreparationRequest>,
    resourceEvidences: List<GPUPreparedResourceEvidence>,
    val routeSeal: GPUCorePrimitiveNativeScopeRouteSeal,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    val sourceScopeIndices: List<Int> = immutableList(sourceScopeIndices)
    val packetIds: List<GPUDrawPacketID> = immutableList(packetIds)
    val preparationRequests: List<GPUResourcePreparationRequest> =
        immutableList(preparationRequests)
    val resourceEvidences: List<GPUPreparedResourceEvidence> =
        immutableList(resourceEvidences)
    val target: GPUFrameTargetRef = renderStep.target
    val loadStore: GPULoadStorePlan = renderStep.loadStore

    init {
        require(this.sourceScopeIndices.isNotEmpty() &&
            this.sourceScopeIndices.all { it >= 0 } &&
            this.sourceScopeIndices.distinct().size == this.sourceScopeIndices.size
        )
        require(this.packetIds.isNotEmpty() &&
            this.packetIds.distinct().size == this.packetIds.size &&
            renderStep.drawPackets.map(GPUDrawPacket::packetId) == this.packetIds &&
            renderStep.resourceUses.map { use -> use.resource } ==
            this.preparationRequests.map(GPUResourcePreparationRequest::resource) &&
            this.preparationRequests.map(GPUResourcePreparationRequest::resource) ==
            this.resourceEvidences.map(GPUPreparedResourceEvidence::logicalResource)
        )
        require(routeSeal is GPUCorePrimitiveNativeScopeRouteSeal.Routes &&
            routeSeal.flattenedPacketIds == this.packetIds &&
            exactScopeKey.sourceStepIndex == this.sourceScopeIndices.single() &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Render &&
            exactScopeKey.operandKeys.isNotEmpty()
        ) {
            "A mixed CorePrimitive run requires its exact route, target, load/store, and scope seals"
        }
    }
}

internal class GPUPreparedSurfaceReadbackSeal(
    val sourceStepIndex: Int,
    val source: GPUFrameTargetRef,
    val staging: GPUFrameBufferRef,
    val request: GPUFrameReadbackRequest,
    val layout: GPUReadbackLayout,
    val stagingLease: GPUReadbackStagingLease,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    init {
        require(sourceStepIndex >= 0 &&
            exactScopeKey.sourceStepIndex == sourceStepIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Readback &&
            exactScopeKey.operandKeys.isNotEmpty() &&
            stagingLease.resourceRef.value.isNotBlank()
        ) {
            "Prepared-surface readback must retain one exact scope and output-owned staging lease"
        }
    }
}

internal class GPUPreparedSurfaceChainSeal(
    val acquireStepIndex: Int,
    val blitStepIndex: Int,
    val presentStepIndex: Int,
    val descriptor: GPUSurfaceOutputDescriptor,
    val scene: GPUFrameTargetRef,
    val output: GPUSurfaceOutputRef,
    sourceTaskIds: List<GPUTaskID>,
    val exactBlitScopeKey: GPUPreparedNativeScopeKey,
) {
    val sourceTaskIds: List<GPUTaskID> = immutableList(sourceTaskIds)

    init {
        require(acquireStepIndex >= 0 &&
            blitStepIndex == acquireStepIndex + 1 &&
            presentStepIndex == blitStepIndex + 1 &&
            descriptor.output == output &&
            this.sourceTaskIds.isNotEmpty() &&
            exactBlitScopeKey.sourceStepIndex == blitStepIndex &&
            exactBlitScopeKey.operationKind == GPUEncoderOperationKind.SurfaceBlit
        ) {
            "Prepared-surface output must retain one exact acquire, blit, and present suffix"
        }
    }
}

internal class GPUPreparedSurfaceCoverageMaskRunPlan(
    val sourceScopeIndex: Int,
    val renderStep: GPUFrameStep.RenderPassStep,
    val slabSeal: GPUCoverageMaskProducerUniformSlabSeal,
    val preparation: GPUResourcePreparationRequest,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    init {
        require(sourceScopeIndex >= 0 &&
            renderStep.target == preparation.resource &&
            preparation.role == GPUFrameResourceRole.ClipMask &&
            preparation.usages == setOf(
                GPUFrameResourceUsage.RenderAttachment,
                GPUFrameResourceUsage.TextureBinding,
            ) &&
            preparation.lifetime == GPUFrameResourceLifetime.FrameLocal &&
            exactScopeKey.sourceStepIndex == sourceScopeIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.Render &&
            renderStep.drawPackets.isNotEmpty() &&
            renderStep.target == slabSeal.maskResource &&
            renderStep.drawPackets.map(GPUDrawPacket::packetId) ==
            slabSeal.producerPacketIds &&
            validateCoverageMaskProducerUniformSlabSeal(renderStep.drawPackets, slabSeal) &&
            renderStep.drawPackets.all { packet ->
                packet.role == GPUDrawPacketRole.ClipProducer &&
                    packet.coverageMaskProducerUniformSlabSeal === slabSeal
            }
        )
    }
}

/**
 * One declared offscreen layer target with its exact children render pass and composite bounds.
 *
 * The materializer allocates one frame-local RGBA8 attachment, renders the children
 * render pass into it, and feeds the sampled layer texture to the composite draw. The
 * coverage-mask frame-pool lease remains a documented follow-up.
 */
internal class GPUPreparedSurfaceLayerTargetPlan(
    val targetLabel: String,
    val prepareStepIndex: Int,
    val childrenRenderStepIndex: Int,
    val compositeStepIndex: Int,
    val bounds: GPUPixelBounds,
    /**
     * Backing extent actually allocated by the native materializer. Layer children use
     * scene-space coordinates, so this intentionally differs from [bounds].
     */
    val allocationBounds: GPUPixelBounds,
    val allocationByteEstimate: Long,
) {
    init {
        require(targetLabel.isNotBlank() &&
            prepareStepIndex >= 0 &&
            childrenRenderStepIndex >= 0 &&
            compositeStepIndex >= 0 &&
            childrenRenderStepIndex < compositeStepIndex &&
            !bounds.isEmpty &&
            !allocationBounds.isEmpty &&
            allocationByteEstimate >= 0L
        ) {
            "A prepared layer target must precede its children render and composite steps"
        }
    }
}

/**
 * One materialized layer composite draw: a textured quad sampling [layerTarget] onto the
 * parent scene target with the real [GPUBlendPlan] and alpha from the command.
 */
internal class GPUPreparedSurfaceLayerCompositeRunPlan(
    val sourceStepIndex: Int,
    val step: GPUFrameStep.LayerCompositeRenderStep,
    val layerTarget: GPUPreparedSurfaceLayerTargetPlan,
    val exactScopeKey: GPUPreparedNativeScopeKey,
) {
    init {
        require(sourceStepIndex >= 0 &&
            exactScopeKey.sourceStepIndex == sourceStepIndex &&
            exactScopeKey.operationKind == GPUEncoderOperationKind.LayerComposite &&
            step.sourceLabel == layerTarget.targetLabel &&
            step.blendPlan.mode in preparedLayerCompositeBlendModes
        ) {
            "A prepared layer composite must retain its exact layer target and one materialized fixed-function blend"
        }
    }
}

private data class GPUPreparedColorGlyphDestinationReadAuthority(
    val copySourceStepIndex: Int,
    val renderSourceStepIndex: Int,
    val copyStep: GPUFrameStep.CopyDestinationStep,
    val renderStep: GPUFrameStep.RenderPassStep,
    val packet: GPUDrawPacket,
    val semantic: GPUDrawSemanticPayload.ColorGlyph,
    val binding: GPUPreparedTextRenderBinding,
    val snapshotPreparation: GPUResourcePreparationRequest,
    val blendPlan: GPUBlendPlan.ShaderBlendWithDstRead,
    val clip: GPUPreparedColorGlyphDestinationClipAuthority,
)

internal sealed interface GPUPreparedColorGlyphDestinationClipAuthority {
    val variant: GPUColorGlyphDestinationClipVariant

    data class AnalyticRect(
        val facts: GPUPreparedColorGlyphAnalyticRectClipFacts,
    ) : GPUPreparedColorGlyphDestinationClipAuthority {
        override val variant = GPUColorGlyphDestinationClipVariant.AnalyticRect
    }

    data class CoverageMask(
        val seal: GPUPreparedColorGlyphClipPreflightSeal.CoverageMask,
        val resource: GPUFrameTargetRef,
        val producerSourceScopeIndex: Int,
        val preparation: GPUResourcePreparationRequest,
    ) : GPUPreparedColorGlyphDestinationClipAuthority {
        override val variant = GPUColorGlyphDestinationClipVariant.CoverageMask
    }
}

internal class GPUPreparedColorGlyphDestinationReadPlan(
    val copySourceStepIndex: Int,
    val renderSourceStepIndex: Int,
    val copyStep: GPUFrameStep.CopyDestinationStep,
    val renderStep: GPUFrameStep.RenderPassStep,
    val packet: GPUDrawPacket,
    val semantic: GPUDrawSemanticPayload.ColorGlyph,
    val binding: GPUPreparedTextRenderBinding,
    val snapshotPreparation: GPUResourcePreparationRequest,
    val snapshotEvidence: GPUPreparedResourceEvidence,
    val blendPlan: GPUBlendPlan.ShaderBlendWithDstRead,
    val clip: GPUPreparedColorGlyphDestinationClipAuthority,
    val coverageMaskEvidence: GPUPreparedResourceEvidence?,
    val programSeal: GPUColorGlyphDestinationProgramSeal,
    val exactCopyScopeKey: GPUPreparedNativeScopeKey,
    val exactRenderScopeKey: GPUPreparedNativeScopeKey,
) {
    init {
        require(
            copySourceStepIndex < renderSourceStepIndex &&
                copyStep.snapshot == snapshotPreparation.resource &&
                snapshotEvidence.logicalResource == snapshotPreparation.resource &&
                snapshotEvidence.role == GPUFrameResourceRole.DestinationSnapshot &&
                exactCopyScopeKey.sourceStepIndex == copySourceStepIndex &&
                exactCopyScopeKey.operationKind == GPUEncoderOperationKind.CopyDestination &&
                exactRenderScopeKey.sourceStepIndex == renderSourceStepIndex &&
                exactRenderScopeKey.operationKind == GPUEncoderOperationKind.Render &&
                blendPlan.mode == GPUBlendMode.COLOR_DODGE &&
                blendPlan.sourceCoverageEncoding ==
                GPUSourceCoverageEncoding.ScalarCoverageInShader &&
                programSeal.formulaId == blendPlan.formulaId &&
                programSeal.sourceCoverageEncoding == blendPlan.sourceCoverageEncoding &&
                programSeal.targetFormat == GPUColorFormat.RGBA8UnormSrgb &&
                programSeal.clipVariant == clip.variant.stableLabel &&
                when (clip) {
                    is GPUPreparedColorGlyphDestinationClipAuthority.AnalyticRect ->
                        coverageMaskEvidence == null
                    is GPUPreparedColorGlyphDestinationClipAuthority.CoverageMask ->
                        clip.producerSourceScopeIndex < copySourceStepIndex &&
                            clip.preparation.resource == clip.resource &&
                            coverageMaskEvidence?.logicalResource == clip.resource &&
                            coverageMaskEvidence.role == GPUFrameResourceRole.ClipMask
                } &&
                semantic.payloadRef.commandIdValue == packet.commandIdValue &&
                binding.packetId == packet.packetId,
        ) {
            "Prepared ColorGlyph destination-read plan must retain one exact sealed copy/formula consumer."
        }
    }
}

internal class GPUPreparedSurfaceNativePreflightPlan(
    val frameId: GPUFrameID,
    val encoderPlanId: String,
    val contextIdentity: String,
    val sceneTarget: GPUFrameTargetRef,
    val resources: GPUPreparedResourceSet,
    val readback: GPUPreparedSurfaceReadbackSeal?,
    val surfaceChain: GPUPreparedSurfaceChainSeal?,
    orderedRuns: List<GPUPreparedSurfaceNativeRunPlan>,
    imageFrames: List<GPUPreparedSurfaceImageFramePlan>,
    val textPlan: GPUPreparedTextRenderRunPlan?,
    val colorGlyphPlan: GPUPreparedColorGlyphRenderRunPlan?,
    colorGlyphDestinationReads: List<GPUPreparedColorGlyphDestinationReadPlan>,
    coverageMaskRuns: List<GPUPreparedSurfaceCoverageMaskRunPlan>,
    layerTargets: List<GPUPreparedSurfaceLayerTargetPlan>,
    compositeRuns: List<GPUPreparedSurfaceLayerCompositeRunPlan>,
    exactScopeKeys: List<GPUPreparedNativeScopeKey>,
    val generationSeal: GPUPreparedGenerationSeal,
) {
    val orderedRuns: List<GPUPreparedSurfaceNativeRunPlan> = immutableList(orderedRuns)
    val imageFrames: List<GPUPreparedSurfaceImageFramePlan> = immutableList(imageFrames)
    val exactScopeKeys: List<GPUPreparedNativeScopeKey> = immutableList(exactScopeKeys)
    val coverageMaskRuns: List<GPUPreparedSurfaceCoverageMaskRunPlan> =
        immutableList(coverageMaskRuns)
    val colorGlyphDestinationReads: List<GPUPreparedColorGlyphDestinationReadPlan> =
        immutableList(colorGlyphDestinationReads)
    val layerTargets: List<GPUPreparedSurfaceLayerTargetPlan> = immutableList(layerTargets)
    val compositeRuns: List<GPUPreparedSurfaceLayerCompositeRunPlan> =
        immutableList(compositeRuns)

    init {
        require(encoderPlanId.isNotBlank() && contextIdentity.isNotBlank())
        require(this.orderedRuns.isNotEmpty() || textPlan != null || colorGlyphPlan != null)
        require(
            this.imageFrames.isNotEmpty() ==
                this.orderedRuns.any { it is GPUPreparedSurfaceNativeRunPlan.Image },
        )
        require(this.imageFrames.map { frame -> frame.resourcePlan.artifactKey }
            .distinct().size == this.imageFrames.size
        ) { "Global image frame plans must be unique per artifact" }
        require(this.exactScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex)
            .distinct().size == this.exactScopeKeys.size
        ) { "Global mixed preflight scope identities must be unique" }
        this.colorGlyphDestinationReads.forEach { read ->
            val mask = read.clip as?
                GPUPreparedColorGlyphDestinationClipAuthority.CoverageMask
                ?: return@forEach
            require(this.coverageMaskRuns.singleOrNull { run ->
                    run.sourceScopeIndex == mask.producerSourceScopeIndex &&
                    run.preparation === mask.preparation &&
                    run.preparation.resource == mask.resource &&
                    run.slabSeal.planCanonicalIdentity ==
                    mask.seal.executionPlanIdentity
            } != null) {
                "ColorGlyph CoverageMask destination read lost its exact producer run authority"
            }
        }
        val expectedScopeKeysWithSharedUploads = buildList {
            addAll(this@GPUPreparedSurfaceNativePreflightPlan.imageFrames.map { it.uploadScopeKey })
            this@GPUPreparedSurfaceNativePreflightPlan.orderedRuns.forEach { run ->
                when (run) {
                    is GPUPreparedSurfaceNativeRunPlan.Core ->
                        add(run.plan.exactScopeKey)
                    is GPUPreparedSurfaceNativeRunPlan.Image ->
                        add(run.plan.exactScopeKey)
                    is GPUPreparedSurfaceNativeRunPlan.Vertices ->
                        addAll(run.plan.exactScopeKeys)
                }
            }
            textPlan?.let { addAll(it.exactScopeKeys) }
            colorGlyphPlan?.let { addAll(it.exactScopeKeys) }
            addAll(this@GPUPreparedSurfaceNativePreflightPlan.colorGlyphDestinationReads.map {
                it.exactCopyScopeKey
            })
            addAll(this@GPUPreparedSurfaceNativePreflightPlan.coverageMaskRuns.map {
                it.exactScopeKey
            })
            addAll(this@GPUPreparedSurfaceNativePreflightPlan.compositeRuns.map {
                it.exactScopeKey
            })
            readback?.let { add(it.exactScopeKey) }
            surfaceChain?.let { add(it.exactBlitScopeKey) }
        }
        val mixedSourceStepIndices = this@GPUPreparedSurfaceNativePreflightPlan.orderedRuns
            .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Core>()
            .map { run -> run.plan.exactScopeKey.sourceStepIndex }
            .toSet()
            .intersect(
                this@GPUPreparedSurfaceNativePreflightPlan.orderedRuns
                    .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Image>()
                    .map { run -> run.plan.exactScopeKey.sourceStepIndex }
                    .toSet(),
            )
        require(expectedScopeKeysWithSharedUploads
            .groupBy(GPUPreparedNativeScopeKey::sourceStepIndex)
            .all { (sourceStepIndex, sameStep) ->
                val distinct = sameStep.distinct()
                if (sourceStepIndex in mixedSourceStepIndices) {
                    distinct.size == 2 && distinct.any { candidate ->
                        candidate == this@GPUPreparedSurfaceNativePreflightPlan.exactScopeKeys
                            .single { exact -> exact.sourceStepIndex == sourceStepIndex }
                    }
                } else {
                    distinct.size == 1
                }
            }
        ) {
            "Shared prepared-text uploads must retain one identical global scope seal"
        }
        val expectedScopeKeys = expectedScopeKeysWithSharedUploads
            .distinctBy(GPUPreparedNativeScopeKey::sourceStepIndex)
            .sortedBy(GPUPreparedNativeScopeKey::sourceStepIndex)
        require(expectedScopeKeys == this.exactScopeKeys) {
            "Global mixed preflight scopes must be one exact, complete ordered partition"
        }
        this.imageFrames.forEach { imageFrame ->
            val exactConsumers = this.orderedRuns.mapNotNull { run ->
                (run as? GPUPreparedSurfaceNativeRunPlan.Image)
                    ?.plan
                    ?.takeIf { plan ->
                        plan.resourcePlans.any { it === imageFrame.resourcePlan }
                    }
                    ?.sourceScopeIndex
            }
            require(exactConsumers == imageFrame.consumerRenderScopeIndices) {
                "Global image consumers must be derived exactly from ordered image runs"
            }
        }
        val coreRoutes = this.orderedRuns
            .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Core>()
            .map { run -> run.plan.routeSeal as GPUCorePrimitiveNativeScopeRouteSeal.Routes }
        if (coreRoutes.isNotEmpty()) {
            val commandIds = coreRoutes.flatMap { route -> route.commandIds }
            require(commandIds.distinct().size == commandIds.size) {
                "Prepared CorePrimitive runs must retain unique sealed command authorities"
            }
        }
    }
}

internal sealed interface GPUPreparedSurfaceNativePreflightResult {
    data class Accepted(val plan: GPUPreparedSurfaceNativePreflightPlan) :
        GPUPreparedSurfaceNativePreflightResult

    data class Refused(
        val code: String,
        val message: String,
        val facts: Map<String, String> = emptyMap(),
    ) :
        GPUPreparedSurfaceNativePreflightResult {
        init {
            require(code.isNotBlank() && message.isNotBlank())
        }
    }
}

/**
 * Pure handle-free admission for the one closed mixed prepared-surface shape. It consumes only
 * evidence already produced by semantic/resource preflight and cannot create a frame, draft,
 * completion ticket, rollback owner, native handle, cache entry, or surface lease.
 */
internal class GPUPreparedSurfaceNativePreflight(
    private val shaderSource: String = GPU_PREPARED_IMAGE_WGSL,
    private val preparedTextEvidenceObserver:
        ((GPUPreparedTextImmutableEvidenceKind) -> Unit)? = null,
) {
    internal fun validateFramePlan(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext? = null,
        capabilities: GPUCapabilities? = null,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? =
        validateFramePlan(
            framePlan = framePlan,
            context = context,
            capabilities = capabilities,
            colorGlyphCanonicalAuthentication =
                GPUPreparedColorGlyphCanonicalPlanTable.authenticate(
                    framePlan.preparedTextBindingsForColorGlyphAuthentication(),
                ),
        )

    private fun validateFramePlan(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext?,
        capabilities: GPUCapabilities?,
        colorGlyphCanonicalAuthentication:
            GPUPreparedColorGlyphCanonicalPlanAuthentication,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val allRenders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val renders = allRenders.filterNot { render ->
            render.drawPackets.isNotEmpty() &&
                render.drawPackets.all { packet ->
                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                        .GPUDrawPacketRole.ClipProducer
                }
        }
        val packets = renders.flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        preparedTextNativeBlendDomainRefusal(
            packets
                .filter { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                }
                .map(GPUDrawPacket::blendPlan),
        )?.let { refusal ->
            return refused(refusal.code, refusal.message)
        }
        val semantics = packets.map(GPUDrawPacket::semanticPayload)
        if (packets.isEmpty() ||
            semantics.any { it == null } ||
            packets.map(GPUDrawPacket::packetId).distinct().size != packets.size
        ) {
            return refused(
                "unsupported.prepared-surface.semantic-shape",
                "Mixed prepared surfaces require one unique packet and one typed semantic per packet.",
            )
        }
        val semanticTypes = semantics.filterNotNull()
            .map(GPUDrawSemanticPayload::canonicalType)
            .toSet()
        val supportedMixedSemanticTypes = setOf("CorePrimitive", "SampledImage")
        if (semanticTypes.any {
                it != "CorePrimitive" &&
                    it != "SampledImage" &&
                    it != "TextA8" &&
                    it != "ColorGlyph" &&
                    it != "Vertices"
            } ||
            renders.any { render ->
                val renderSemanticTypes = render.drawPackets
                    .mapNotNull(GPUDrawPacket::semanticPayload)
                    .map(GPUDrawSemanticPayload::canonicalType)
                    .toSet()
                renderSemanticTypes.size != 1 &&
                    renderSemanticTypes != supportedMixedSemanticTypes
            }
        ) {
            return refused(
                "unsupported.prepared-surface.semantic-shape",
                "The prepared surface route accepts homogeneous typed runs or one " +
                    "CorePrimitive/SampledImage mixed run.",
            )
        }
        if (framePlan.steps.any { step ->
                step is GPUFrameStep.CopyAsDrawMaterializationStep
            }
        ) {
            return refused(
                "unsupported.prepared-surface.destination-copy",
                "The prepared ColorGlyph destination-read route does not admit CopyAsDraw.",
            )
        }
        authenticateColorGlyphDestinationReads(
            framePlan = framePlan,
            renders = renders,
            context = context,
            capabilities = capabilities,
        ).second?.let { return it }
        if (framePlan.steps.any { step -> !step.isPreparedSurfaceStep() }) {
            return refused(
                "unsupported.prepared-surface.encoder-shape",
                "The mixed prepared route contains an unsupported frame step.",
            )
        }
        val rendersByTarget = renders.groupBy(GPUFrameStep.RenderPassStep::target)
        if (rendersByTarget.any { (_, targetRenders) ->
                targetRenders.anyIndexed { index, render ->
                    render.loadStore.loadOp != (if (index == 0) "clear" else "load") ||
                        render.loadStore.clearColorLabel != null ||
                        render.loadStore.storePlan != GPUStorePlan.Store
                }
            }
        ) {
            return refused(
                "invalid.prepared-surface.render-load-store",
                "Per target, the first mixed render must clear and every later render must load; all must store.",
            )
        }
        if (renders.any { render ->
                render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                    render.sampleContinuation != null
            }
        ) {
            return refused(
                "unsupported.prepared-surface.sample-plan",
                "The mixed prepared route admits only single-sample render runs without continuation.",
            )
        }
        if (packets.filter { packet ->
                packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }.any { packet ->
                packet.role !in setOf(
                    GPUDrawPacketRole.Shading,
                    GPUDrawPacketRole.PathStencilProducer,
                    GPUDrawPacketRole.PathStencilCover,
                )
            }
        ) {
            return refused(
                "unsupported.prepared-surface.core-route",
                "Mixed CorePrimitive runs accept only shading and path stencil producer/cover roles.",
            )
        }
        if (packets.mapNotNull(GPUDrawPacket::semanticPayload)
                .filterIsInstance<GPUDrawSemanticPayload.CorePrimitive>()
                .any { semantic ->
                    val path = semantic.geometry as?
                        GPUCorePrimitiveGeometry.TriangulatedPath
                    path != null && path.geometryMode !in setOf(
                            GPUCorePrimitiveGeometryMode.DirectTriangles,
                            GPUCorePrimitiveGeometryMode.StencilEdgeFan,
                            GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
                        )
                }
        ) {
            return refused(
                "unsupported.prepared-surface.core-route",
                "Mixed CorePrimitive runs accept only Direct or IndexedPath geometry.",
            )
        }
        context?.let { expected ->
            val sceneTarget = framePlan.steps
                .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            if (framePlan.capabilitySeal.deviceGeneration != expected.deviceGeneration ||
                sceneTarget?.resource?.value != expected.targetId ||
                renders.any { render ->
                    render.drawPackets.any { packet ->
                        packet.resourceGeneration != PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION &&
                            packet.resourceGeneration != expected.targetGeneration
                    }
                }
            ) {
                return refused(
                    "stale.prepared-surface.frame-context",
                    "Frame identity, target, and generation must match the active preflight context.",
                )
            }
        }
        validateColorAuthority(framePlan, renders)?.let { return it }
        validateReadbackAndSurface(framePlan, context)?.let { return it }
        validatePreparedTextAuthority(
            framePlan,
            context,
            capabilities,
            colorGlyphCanonicalAuthentication,
        )?.let { return it }
        validatePreparedVerticesAuthority(framePlan, context)?.let { return it }

        val imagePackets = packets.mapNotNull { packet ->
            (packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage)?.let {
                packet to it
            }
        }
        if (imagePackets.isEmpty()) return null

        val uploadSteps = framePlan.steps.withIndex()
            .filter { indexed -> indexed.value is GPUFrameStep.UploadResourceStep }
            .map { indexed ->
                indexed.index to indexed.value as GPUFrameStep.UploadResourceStep
            }
        val imageUploads = uploadSteps.mapNotNull { (index, step) ->
            step.imageResourcePlan?.let { plan -> Triple(index, step, plan) }
        }
        if (imageUploads.map { (_, _, plan) -> plan.artifactKey }.distinct().size !=
            imageUploads.size
        ) {
            return refused(
                "unsupported.prepared_image.plan_identity",
                "Every mixed upload scope must retain one unique prepared-image artifact plan.",
            )
        }
        validateImageScissorAuthority(framePlan, imagePackets)?.let { return it }
        val shaderContract = when (
            val validation = validatePreparedImageShader(shaderSource)
        ) {
            is GPUPreparedImageShaderValidationResult.Ready -> validation.shaderContract
            is GPUPreparedImageShaderValidationResult.Refused ->
                return refused(
                    validation.code,
                    "Prepared-image WGSL validation refused before native preflight.",
                    validation.facts,
                )
        }
        return validateImageAuthority(
            framePlan,
            imagePackets,
            imageUploads,
            shaderContract,
        )
    }

    private fun authenticateColorGlyphDestinationReads(
        framePlan: GPUFramePlan,
        renders: List<GPUFrameStep.RenderPassStep>,
        context: GPUFramePreflightContext?,
        capabilities: GPUCapabilities?,
    ): Pair<
        List<GPUPreparedColorGlyphDestinationReadAuthority>,
        GPUPreparedSurfaceNativePreflightResult.Refused?,
        > {
        val indexedCopies = framePlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is GPUFrameStep.CopyDestinationStep -> index to step
                else -> null
            }
        }
        val destinationPackets = renders.flatMap { render ->
            render.drawPackets.filter { packet ->
                packet.blendPlan?.destinationReadRequirement ==
                    org.graphiks.kanvas.gpu.renderer.passes
                        .GPUBlendDestinationReadRequirement.DestinationTextureRequired
            }
        }
        if (indexedCopies.isEmpty() && destinationPackets.isEmpty()) {
            return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to null
        }
        if (indexedCopies.size != destinationPackets.size ||
            indexedCopies.isEmpty()
        ) {
            return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                "unsupported.prepared-surface.destination-copy",
                "Every destination-reading ColorGlyph packet requires one exact copy scope.",
            )
        }
        val preparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val scenePreparation = preparations.singleOrNull { request ->
            request.role == GPUFrameResourceRole.SceneTarget
        }
        val sceneDescriptor = scenePreparation?.descriptor as? GPUFrameTextureDescriptor
        if (scenePreparation == null || sceneDescriptor == null) {
            return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                "unsupported.prepared-surface.destination-copy",
                "Prepared ColorGlyph destination reads require one exact scene texture.",
            )
        }
        val renderEvidence = framePlan.steps.mapIndexedNotNull { index, step ->
            when (step) {
                is GPUFrameStep.RenderPassStep -> index to step
                else -> null
            }
        }
        val authorities = mutableListOf<GPUPreparedColorGlyphDestinationReadAuthority>()
        val consumedPacketIds = mutableSetOf<GPUDrawPacketID>()
        indexedCopies.forEach { (copyIndex, copy) ->
            val consumer = copy.consumers.singleOrNull()
                ?: return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                    "unsupported.prepared-surface.destination-copy",
                    "Prepared ColorGlyph destination copies require one exact consumer.",
                )
            val renderEntry = renderEvidence.singleOrNull { (_, render) ->
                render.drawPackets.any { packet -> packet.packetId == consumer.packetId }
            }
                ?: return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                    "unsupported.prepared-surface.destination-copy",
                    "Prepared ColorGlyph destination copy lost its exact render consumer.",
                )
            val renderIndex = renderEntry.first
            val render = renderEntry.second
            val packet = render.drawPackets.single { candidate ->
                candidate.packetId == consumer.packetId
            }
            val semantic = when (val payload = packet.semanticPayload) {
                is GPUDrawSemanticPayload.ColorGlyph -> payload
                else -> return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to
                    refused(
                        "unsupported.prepared-surface.destination-copy",
                        "Only ColorGlyph packets may consume prepared-surface destination copies.",
                    )
            }
            val blend = when (val plan = packet.blendPlan) {
                is GPUBlendPlan.ShaderBlendWithDstRead -> plan
                else -> return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to
                    refused(
                        "unsupported.prepared-surface.destination-copy",
                        "Prepared ColorGlyph destination reads require one shader destination blend.",
                    )
            }
            if (blend.mode != GPUBlendMode.COLOR_DODGE ||
                blend.sourceCoverageEncoding !=
                GPUSourceCoverageEncoding.ScalarCoverageInShader ||
                blend.formulaId != "color_dodge@v1" ||
                semantic.blendPlanIdentity != blend.canonicalIdentity()
            ) {
                return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                    "unsupported.prepared-surface.destination-copy",
                    "Prepared ColorGlyph destination reads require canonical scalar color_dodge@v1.",
                )
            }
            val binding = render.preparedTextBindingsByPacketId[packet.packetId]
            val clipAuthority = when (
                val clipSeal = binding?.preflightSeal?.colorGlyphClip
            ) {
                is GPUPreparedColorGlyphClipPreflightSeal.NonMask -> if (
                    clipSeal.matchesPreparedColorGlyphClip(packet)
                ) {
                    clipSeal.analyticRect?.let(
                        GPUPreparedColorGlyphDestinationClipAuthority::AnalyticRect,
                    )
                } else {
                    null
                }
                is GPUPreparedColorGlyphClipPreflightSeal.CoverageMask ->
                    binding.coverageMaskResource?.takeIf {
                        clipSeal.matchesPreparedColorGlyphClip(packet)
                    }?.let { resource ->
                    if (clipSeal.resource != resource) return@let null
                    val producer = framePlan.steps.mapIndexedNotNull { index, step ->
                        (step as? GPUFrameStep.RenderPassStep)
                            ?.takeIf { candidate ->
                                    candidate.target == resource &&
                                    candidate.drawPackets.isNotEmpty() &&
                                    candidate.drawPackets.all { producerPacket ->
                                        clipSeal
                                            .matchesPreparedColorGlyphCoverageMaskProducer(
                                                producerPacket,
                                            )
                                    }
                            }
                            ?.let { index to it }
                    }.singleOrNull() ?: return@let null
                    val preparation = preparations.singleOrNull { request ->
                        request.resource == resource &&
                            request.role == GPUFrameResourceRole.ClipMask
                    } ?: return@let null
                    if (producer.first >= copyIndex) return@let null
                    GPUPreparedColorGlyphDestinationClipAuthority.CoverageMask(
                        clipSeal,
                        resource,
                        producer.first,
                        preparation,
                    )
                }
                null -> null
            } ?: return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                "unsupported.prepared-surface.destination-copy",
                "Prepared ColorGlyph destination reads require an analytic rectangle or shared CoverageMask.",
            )
            val snapshotPreparation = preparations.singleOrNull { request ->
                request.resource == copy.snapshot
            }
            val snapshotDescriptor =
                snapshotPreparation?.descriptor as? GPUFrameTextureDescriptor
            val expectedTextureBytes = try {
                Math.multiplyExact(
                    Math.multiplyExact(copy.logicalBounds.width.toLong(), 4L),
                    copy.logicalBounds.height.toLong(),
                )
            } catch (_: ArithmeticException) {
                return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                    "unsupported.prepared-surface.destination-copy",
                    "Prepared ColorGlyph destination snapshot byte accounting overflowed.",
                )
            }
            val minimumBytesPerRow = try {
                Math.multiplyExact(copy.logicalBounds.width.toLong(), 4L)
            } catch (_: ArithmeticException) {
                return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                    "unsupported.prepared-surface.destination-copy",
                    "Prepared ColorGlyph destination snapshot row accounting overflowed.",
                )
            }
            val copyAlignment = capabilities?.limits?.copyBytesPerRowAlignment
            if (copyIndex >= renderIndex ||
                !consumedPacketIds.add(packet.packetId) ||
                consumer.commandId.value != packet.commandIdValue ||
                consumer.groupingCommandId != packet.commandIdValue.toString() ||
                consumer.renderTaskId !in render.sourceTaskIds ||
                render.sourceTaskIds.singleOrNull() != consumer.renderTaskId ||
                copy.source != scenePreparation.resource ||
                copy.logicalBounds != sceneDescriptor.logicalBounds ||
                copy.sourceKey.target.value != scenePreparation.resource.value ||
                copy.sourceKey.deviceGeneration != framePlan.capabilitySeal.deviceGeneration ||
                copy.sourceKey.targetGeneration != packet.resourceGeneration &&
                copy.sourceKey.targetGeneration !=
                PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION ||
                context?.targetGeneration?.let { current ->
                    copy.sourceKey.targetGeneration == current ||
                        copy.sourceKey.targetGeneration ==
                        PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION
                } == false ||
                copy.sourceKey.format != sceneDescriptor.format ||
                sceneDescriptor.format != GPUColorFormat.RGBA8UnormSrgb ||
                copy.sourceKey.colorInterpretation != GPUColorInterpretation.LinearPremul ||
                copy.sourceKey.sampleContinuation != null ||
                copy.sourceKey.sourceIntermediate != null ||
                snapshotPreparation == null ||
                snapshotDescriptor?.logicalBounds != copy.logicalBounds ||
                snapshotDescriptor.format != sceneDescriptor.format ||
                snapshotDescriptor.sampleCount != 1 ||
                snapshotPreparation.role != GPUFrameResourceRole.DestinationSnapshot ||
                snapshotPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.TextureBinding,
                ) ||
                snapshotPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
                snapshotPreparation.byteSize != expectedTextureBytes ||
                copy.copyLayout.bytesPerRow < minimumBytesPerRow ||
                copy.copyLayout.rowsPerImage != copy.logicalBounds.height ||
                copyAlignment?.let { alignment ->
                    copy.copyLayout.bytesPerRow % alignment != 0L
                } == true ||
                render.target != scenePreparation.resource ||
                render.samplePlan != GPUSamplePlan.SingleSampleFrame ||
                binding == null ||
                !binding.hasColorGlyphBufferPlan ||
                (clipAuthority as? GPUPreparedColorGlyphDestinationClipAuthority.CoverageMask)
                    ?.let { mask ->
                        render.resourceUses.none { use ->
                            use.resource == mask.resource &&
                                use.role == GPUFrameResourceRole.ClipMask &&
                                use.usage == GPUFrameResourceUsage.TextureBinding &&
                                use.lifetime == GPUFrameResourceLifetime.FrameLocal &&
                                !use.write
                        }
                    } == true ||
                render.resourceUses.none { use ->
                    use.resource == copy.snapshot &&
                        use.role == GPUFrameResourceRole.DestinationSnapshot &&
                        use.usage == GPUFrameResourceUsage.TextureBinding &&
                        use.lifetime == GPUFrameResourceLifetime.FrameLocal &&
                        !use.write
                }
            ) {
                return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                    "unsupported.prepared-surface.destination-copy",
                    "Prepared ColorGlyph copy, snapshot, formula consumer, and resource facts are not exact.",
                )
            }
            authorities += GPUPreparedColorGlyphDestinationReadAuthority(
                copySourceStepIndex = copyIndex,
                renderSourceStepIndex = renderIndex,
                copyStep = copy,
                renderStep = render,
                packet = packet,
                semantic = semantic,
                binding = binding,
                snapshotPreparation = snapshotPreparation,
                blendPlan = blend,
                clip = clipAuthority,
            )
        }
        if (consumedPacketIds != destinationPackets.map(GPUDrawPacket::packetId).toSet()) {
            return emptyList<GPUPreparedColorGlyphDestinationReadAuthority>() to refused(
                "unsupported.prepared-surface.destination-copy",
                "Prepared ColorGlyph copy consumers do not exactly cover destination-reading packets.",
            )
        }
        return authorities.sortedBy(
            GPUPreparedColorGlyphDestinationReadAuthority::copySourceStepIndex,
        ) to null
    }

    private fun validatePreparedTextAuthority(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext?,
        capabilities: GPUCapabilities?,
        colorGlyphCanonicalAuthentication:
            GPUPreparedColorGlyphCanonicalPlanAuthentication,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val textPackets = framePlan.steps
            .mapIndexedNotNull { index, step ->
                (step as? GPUFrameStep.RenderPassStep)?.let { index to it }
            }
            .flatMap { (renderIndex, render) ->
                render.drawPackets.mapNotNull { packet ->
                    val semantic = packet.semanticPayload
                    if (semantic is GPUDrawSemanticPayload.TextA8 ||
                        semantic is GPUDrawSemanticPayload.ColorGlyph
                    ) {
                        PreparedTextPacketEvidence(renderIndex, render, packet, semantic)
                    } else {
                        null
                    }
                }
            }
        if (textPackets.isEmpty()) return null
        val bindings = textPackets.map { evidence ->
            evidence.render.preparedTextBindingsByPacketId[evidence.packet.packetId]
                ?: return refused(
                    GPUPreparedTextPreflightRefusalCodes.OPERAND,
                    "Every prepared-text packet requires one exact immutable binding.",
                )
        }
        when (colorGlyphCanonicalAuthentication) {
            is GPUPreparedColorGlyphCanonicalPlanAuthentication.Refused ->
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.OPERAND,
                    colorGlyphCanonicalAuthentication.message,
                )
            is GPUPreparedColorGlyphCanonicalPlanAuthentication.Accepted -> Unit
        }
        val atlasBytesByIdentity =
            HashMap<org.graphiks.kanvas.gpu.renderer.resources.GPUR8ArtifactIdentity, ByteArray>()
        val materialEvidenceByIdentity =
            HashMap<GPUPreparedTextMaterialEvidenceKey, GPUPreparedTextMaterialEvidence>()
        val uniformSlabBytesByIdentity =
            HashMap<GPUPreparedTextUniformSlabEvidenceKey, ByteArray>()
        textPackets.zip(bindings).firstOrNull { (evidence, binding) ->
            val packet = evidence.packet
            val semantic = evidence.semantic
            val authority = binding.preflightSeal.packetAuthority
            authority == null ||
                semantic.payloadRef.commandIdValue != packet.commandIdValue ||
                semantic.payloadRef.renderStepIdentity != packet.renderStepId.value ||
                authority.commandIdValue != packet.commandIdValue ||
                authority.renderStepIdentity != packet.renderStepId.value ||
                authority.renderPipelineKey != packet.renderPipelineKey?.value ||
                authority.bindingLayoutHash != packet.bindingLayoutHash ||
                authority.uniformSlot != packet.uniformSlot ||
                semantic.payloadRef.uniformSlot != packet.uniformSlot ||
                authority.vertexSourceLabel != packet.vertexSourceLabel ||
                authority.targetStateHash != packet.targetStateHash ||
                authority.scissorBoundsHash != packet.scissorBoundsHash
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND,
                "Prepared-text packet and semantic identities changed after recording.",
            )
        }
        textPackets.zip(bindings).firstOrNull { (evidence, binding) ->
            val semanticGeneration = evidence.semantic.preparedTextAtlasGeneration()
            semanticGeneration != evidence.semantic.preparedTextAtlas().generation ||
                binding.atlasResourcePlan.artifactGeneration != semanticGeneration ||
                binding.preflightSeal.atlasGeneration != semanticGeneration
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.STALE_ATLAS_GENERATION,
                "Prepared-text atlas generation must match artifact, payload, resource, and frame evidence.",
            )
        }
        context?.let { current ->
            if (bindings.any { binding ->
                    val generation = binding.atlasResourcePlan.artifactGeneration
                    current.resourceGenerations[binding.atlasResourcePlan.frameTextureRef] !=
                        generation ||
                        current.resourceGenerations[binding.atlasResourcePlan.stagingRef] !=
                        generation ||
                        binding.hasColorGlyphBufferPlan &&
                        listOf(
                            binding.colorGlyphBufferPlan.vertexBufferRef,
                            binding.colorGlyphBufferPlan.indexBufferRef,
                            binding.colorGlyphBufferPlan.uniformBufferRef,
                        ).any { resource ->
                            current.resourceGenerations[resource] !=
                                binding.colorGlyphBufferPlan.resourceGeneration
                        }
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.STALE_ATLAS_GENERATION,
                    "Every prepared-text resource requires exact current generation evidence.",
                )
            }
        }

        val targetPreparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        textPackets.zip(bindings).forEach { (evidence, binding) ->
            val semantic = evidence.semantic
            val atlas = semantic.preparedTextAtlas()
            val plan = binding.atlasResourcePlan
            val seal = binding.preflightSeal
            if (atlas.width != plan.artifactWidth ||
                atlas.height != plan.artifactHeight ||
                atlas.width != seal.atlasWidth ||
                atlas.height != seal.atlasHeight
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.PAGE_DIMENSIONS,
                    "Prepared-text page dimensions changed after recording.",
                )
            }
            if (atlas.rowBytes != plan.artifactRowBytes ||
                atlas.rowBytes != seal.atlasRowBytes ||
                atlas.rowBytes < atlas.width
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.PAGE_ROW_BYTES,
                    "Prepared-text source row bytes changed after recording.",
                )
            }
            val atlasEvidenceKey = atlas.r8ArtifactIdentity
            val tightBytes = atlasBytesByIdentity.getOrPut(atlasEvidenceKey) {
                preparedTextEvidenceObserver?.invoke(
                    GPUPreparedTextImmutableEvidenceKind.AtlasBytes,
                )
                atlas.tightBytesForUpload()
            }
            if (atlas.contentHash != plan.artifactContentHash ||
                atlas.contentHash != seal.atlasContentHash ||
                tightBytes.size.toLong() !=
                    atlas.rowBytes.toLong() * atlas.height.toLong()
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.PAGE_BYTES,
                    "Prepared-text page bytes or content hash changed after recording.",
                )
            }
            if (atlas.key != plan.artifactKey ||
                atlas.key != seal.atlasKey ||
                semantic.preparedTextPageIndex() != seal.pageIndex
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.PAGE_BYTES,
                    "Prepared-text page identity changed after recording.",
                )
            }
        }

        capabilities?.let { observed ->
            if (GPUTextureFormat.R8Unorm !in observed.supportedTextureFormats) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.R8UNORM,
                    "Prepared text requires observed R8Unorm sampling support.",
                )
            }
        }

        textPackets.forEach { evidence ->
            if (evidence.semantic.preparedTextInstances().any { instance ->
                    val uv = instance.uvRect
                    !uv.left.isFinite() || !uv.top.isFinite() ||
                        !uv.right.isFinite() || !uv.bottom.isFinite() ||
                        uv.left < 0f || uv.top < 0f ||
                        uv.right > 1f || uv.bottom > 1f ||
                        uv.left >= uv.right || uv.top >= uv.bottom ||
                        instance.pageIndex != evidence.semantic.preparedTextPageIndex()
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.INSTANCE_UV,
                    "Prepared-text instance UVs must be finite, ordered, and inside their page.",
                )
            }
        }
        textPackets.zip(bindings).firstOrNull { (evidence, binding) ->
            val semantic = evidence.semantic as? GPUDrawSemanticPayload.ColorGlyph
                ?: return@firstOrNull false
            !binding.hasColorGlyphBufferPlan ||
                !binding.matchesPreparedColorGlyphBufferPlan(semantic)
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND,
                "ColorGlyph packet bytes and slices must match one exact sealed artifact buffer plan.",
            )
        }
        if (bindings.any { binding ->
                val seal = binding.preflightSeal
                binding.instanceBufferPlan.strideBytes !=
                    org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance.ENCODED_BYTE_SIZE ||
                    seal.instanceStrideBytes != binding.instanceBufferPlan.strideBytes
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.INSTANCE_STRIDE,
                "Prepared-text instance stride must match the canonical A8 record.",
            )
        }
        val ranges = bindings.map { binding ->
            val seal = binding.preflightSeal
            seal.firstInstance.toLong() to
                seal.firstInstance.toLong() + seal.instanceCount.toLong()
        }
        if (ranges.indices.any { left ->
                ((left + 1) until ranges.size).any { right ->
                    ranges[left].first < ranges[right].second &&
                        ranges[right].first < ranges[left].second
                }
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.INSTANCE_RANGE_OVERLAP,
                "Prepared-text instance ranges must form a non-overlapping partition.",
            )
        }
        if (bindings.any { binding ->
                val seal = binding.preflightSeal
                seal.firstInstance != binding.firstInstance ||
                    seal.instanceCount != binding.instanceCount ||
                    seal.firstInstance < 0 ||
                    seal.instanceCount <= 0 ||
                    seal.firstInstance.toLong() + seal.instanceCount.toLong() >
                    binding.instanceBufferPlan.instanceCount.toLong() ||
                    seal.instanceBufferByteSize != binding.instanceBufferPlan.byteSize ||
                    seal.instanceBufferContentHash != binding.instanceBufferPlan.contentHash
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.INSTANCE_BUFFER_RANGE,
                "Prepared-text instance ranges must lie inside one exact frame buffer.",
            )
        }
        if (bindings.any { binding ->
                val seal = binding.preflightSeal
                seal.materialUniformOffsetBytes != binding.materialUniformOffsetBytes ||
                    seal.materialUniformSizeBytes != binding.materialUniformSizeBytes
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND,
                "Prepared-text material operand ranges changed after recording.",
            )
        }

        textPackets.zip(bindings).forEach { (evidence, binding) ->
            val semantic = evidence.semantic
            val material = semantic.preparedTextMaterial()
            val seal = binding.preflightSeal
            if (seal.materialKey != material.materialKey ||
                seal.materialAbiHash != material.abiHash
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.MATERIAL_ABI,
                    "Prepared-text material identity and ABI changed after compilation.",
                )
            }
            if (seal.materialEntryPoint != material.entryPoint) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.WGSL_ENTRY_POINT,
                    "Prepared-text WGSL entry point must match the Task 3 compiled program.",
                )
            }
            val materialEvidenceKey = GPUPreparedTextMaterialEvidenceKey(
                materialKey = material.materialKey,
                abiHash = material.abiHash,
                wgslSource = material.wgslSource,
                uniformBytes = material.uniformBytes,
            )
            val materialEvidence = materialEvidenceByIdentity.getOrPut(
                materialEvidenceKey,
            ) {
                preparedTextEvidenceObserver?.invoke(
                    GPUPreparedTextImmutableEvidenceKind.MaterialWgslHash,
                )
                preparedTextEvidenceObserver?.invoke(
                    GPUPreparedTextImmutableEvidenceKind.MaterialUniformBytes,
                )
                val uniformBytes = ByteArray(material.uniformBytes.size) { index ->
                    material.uniformBytes[index].toByte()
                }
                GPUPreparedTextMaterialEvidence(
                    wgslSourceHash = material.wgslSource.utf8Sha256(),
                    uniformBytes = uniformBytes,
                    uniformContentHash = uniformBytes.byteHash(),
                )
            }
            if (seal.materialWgslSourceHash != materialEvidence.wgslSourceHash) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.BINDING_LAYOUT,
                    "Prepared-text WGSL binding topology changed after compilation.",
                )
            }
            if (seal.materialUniformContentHash !=
                materialEvidence.uniformContentHash
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.MATERIAL_UNIFORMS,
                    "Prepared-text uniform bytes changed after compilation.",
                )
            }
            if (seal.materialSampledResourceFacts !=
                material.sampledResources.flatMap { resource -> resource.identityFacts() }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.MATERIAL_RESOURCES,
                    "Prepared-text sampled resources changed after compilation.",
                )
            }
            val uniformBytes = materialEvidence.uniformBytes
            val uniformPlan = binding.materialUniformBufferPlan
            val uniformEnd = binding.materialUniformOffsetBytes +
                binding.materialUniformSizeBytes
            val uniformSlabBytes = uniformPlan?.let { plan ->
                val slabEvidenceKey = GPUPreparedTextUniformSlabEvidenceKey(
                    bufferRef = plan.bufferRef,
                    contentHash = plan.contentHash,
                    byteSize = plan.byteSize,
                    alignmentBytes = plan.alignmentBytes,
                )
                uniformSlabBytesByIdentity.getOrPut(slabEvidenceKey) {
                    preparedTextEvidenceObserver?.invoke(
                        GPUPreparedTextImmutableEvidenceKind.UniformSlabBytes,
                    )
                    plan.bytesForUpload()
                }
            }
            if (uniformBytes.isEmpty()) {
                if (uniformPlan != null ||
                    binding.materialUniformOffsetBytes != 0L ||
                    binding.materialUniformSizeBytes != 0L
                ) {
                    return refused(
                        GPUPreparedTextPreflightRefusalCodes.MATERIAL_UNIFORMS,
                        "A material without uniforms must not retain a uniform-buffer slice.",
                    )
                }
            } else if (uniformPlan == null ||
                binding.materialUniformSizeBytes != uniformBytes.size.toLong() ||
                uniformEnd < binding.materialUniformOffsetBytes ||
                uniformEnd > uniformPlan.byteSize ||
                !requireNotNull(uniformSlabBytes)
                    .contentEqualsPreparedTextUniformRange(
                        offsetBytes = binding.materialUniformOffsetBytes,
                        endBytes = uniformEnd,
                        expected = uniformBytes,
                    )
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.MATERIAL_UNIFORMS,
                    "Prepared-text uniform bytes must match their exact frame-global slice.",
                )
            }
            if (binding.materialSampledResourcePlans.size != material.sampledResources.size ||
                binding.materialSampledResourcePlans.zip(material.sampledResources)
                    .any { (plan, resource) ->
                        plan.resourceKey != resource.resourceKey ||
                            plan.width != resource.width ||
                            plan.height != resource.height ||
                            plan.samplingFilterMode != resource.samplingFilterMode ||
                            plan.alphaOnly != resource.alphaOnly ||
                            plan.contentHash != resource.contentHash
                    }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.MATERIAL_RESOURCES,
                    "Prepared-text sampled-resource plans must match the compiled program exactly.",
                )
            }
        }
        context?.let { current ->
            if (bindings.flatMap(GPUPreparedTextRenderBinding::preparedTextResourceRefs)
                    .distinct()
                    .any { resource -> current.resourceGenerations[resource] == null }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.STALE_ATLAS_GENERATION,
                    "Every prepared-text resource requires exact current generation evidence.",
                )
            }
        }

        val r8Uploads = framePlan.steps.mapIndexedNotNull { index, step ->
            (step as? GPUFrameStep.UploadResourceStep)?.r8ResourcePlan?.let {
                PreparedTextUploadEvidence(index, step, it)
            }
        }
        val materialUploads = framePlan.steps.mapIndexedNotNull { index, step ->
            (step as? GPUFrameStep.UploadResourceStep)?.materialResourcePlan?.let {
                PreparedTextMaterialUploadEvidence(index, step, it)
            }
        }
        val requiredPages = bindings.map { it.atlasResourcePlan }
            .distinctBy(GPUR8FrameResourcePlan::r8ArtifactIdentity)
        requiredPages.firstOrNull { required ->
            r8Uploads.none { upload ->
                upload.plan.samePreparedTextPageAs(required)
            }
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.UPLOAD_MISSING,
                "Every prepared-text page requires one upload.",
            )
        }
        requiredPages.firstOrNull { required ->
            r8Uploads.count { upload ->
                upload.plan.samePreparedTextPageAs(required)
            } != 1
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.UPLOAD_DUPLICATE,
                "Each prepared-text page must be uploaded exactly once.",
            )
        }
        textPackets.zip(bindings).firstOrNull { (evidence, binding) ->
            val uploadIndex = r8Uploads.single { upload ->
                upload.plan.samePreparedTextPageAs(binding.atlasResourcePlan)
            }.sourceStepIndex
            uploadIndex >= evidence.renderIndex
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.UPLOAD_ORDER,
                "Prepared-text uploads must precede every consuming render.",
            )
        }
        val requiredMaterialResources = bindings
            .flatMap(GPUPreparedTextRenderBinding::materialSampledResourcePlans)
            .distinctBy { plan -> plan.resourceKey }
        requiredMaterialResources.firstOrNull { required ->
            materialUploads.none { upload ->
                upload.plan.samePreparedMaterialResourceAs(required)
            }
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.UPLOAD_MISSING,
                "Every prepared-text sampled material resource requires one upload.",
            )
        }
        requiredMaterialResources.firstOrNull { required ->
            materialUploads.count { upload ->
                upload.plan.samePreparedMaterialResourceAs(required)
            } != 1
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.UPLOAD_DUPLICATE,
                "Each prepared-text sampled material resource must be uploaded exactly once.",
            )
        }
        textPackets.zip(bindings).firstOrNull { (evidence, binding) ->
            binding.materialSampledResourcePlans.any { required ->
                materialUploads.single { upload ->
                    upload.plan.samePreparedMaterialResourceAs(required)
                }.sourceStepIndex >= evidence.renderIndex
            }
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.UPLOAD_ORDER,
                "Prepared-text sampled material uploads must precede every consumer.",
            )
        }

        capabilities?.limits?.let { limits ->
            if (requiredPages.any { page ->
                    page.artifactWidth.toLong() > limits.maxTextureDimension2D ||
                        page.artifactHeight.toLong() > limits.maxTextureDimension2D
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.TEXTURE_LIMIT,
                    "Prepared-text atlas dimensions exceed the observed device limit.",
                )
            }
            if (limits.maxBufferSize?.let { max ->
                    bindings.any { binding ->
                        binding.instanceBufferPlan.byteSize > max ||
                            binding.hasColorGlyphBufferPlan &&
                            listOf(
                                binding.colorGlyphBufferPlan.vertexByteSize,
                                binding.colorGlyphBufferPlan.indexByteSize,
                                binding.colorGlyphBufferPlan.uniformByteSize,
                            ).any { size -> size > max }
                    }
                } == true
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.INSTANCE_BUFFER_LIMIT,
                    "Prepared-text instance buffer exceeds the observed device limit.",
                )
            }
            if (bindings.any { binding ->
                    binding.hasColorGlyphBufferPlan &&
                        binding.colorGlyphBufferPlan.uniformAlignmentBytes !=
                        limits.minUniformBufferOffsetAlignment
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.OPERAND,
                    "ColorGlyph uniform slab alignment must match the observed device limit.",
                )
            }
            if (r8Uploads.any { upload ->
                    val requiredAlignment = leastCommonMultipleTextOrNull(
                        256L,
                        limits.copyBytesPerRowAlignment,
                    )
                    requiredAlignment == null ||
                        upload.plan.uploadTaskLayout.bytesPerRow % requiredAlignment != 0L
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.COPY_ALIGNMENT,
                    "Prepared-text copy rows violate the observed WebGPU alignment.",
                )
            }
        }
        textPackets.zip(bindings).forEach { (evidence, binding) ->
            val semantic = evidence.semantic
            val seal = binding.preflightSeal
            val targetDescriptor = targetPreparations[evidence.render.target]?.descriptor as?
                GPUFrameTextureDescriptor
            if (targetDescriptor?.logicalBounds != semantic.preparedTextTargetBounds() ||
                seal.targetBounds != semantic.preparedTextTargetBounds()
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.TARGET,
                    "Prepared-text target bounds changed after recording.",
                )
            }
            if (seal.scissorBounds != semantic.preparedTextScissorBounds() ||
                !semantic.preparedTextScissorBounds()
                    .isContainedBy(semantic.preparedTextTargetBounds())
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.SCISSOR,
                    "Prepared-text scissor changed after recording.",
                )
            }
            if (seal.clipIdentity != semantic.preparedTextClipIdentity()) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.CLIP,
                    "Prepared-text clip authority changed after recording.",
                )
            }
            if (seal.blendPlanIdentity != semantic.preparedTextBlendIdentity() ||
                evidence.packet.blendPlan?.canonicalIdentity() !=
                semantic.preparedTextBlendIdentity()
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.BLEND,
                    "Prepared-text blend authority changed after recording.",
                )
            }
            if (seal.capabilitySnapshotHash != semantic.preparedTextCapabilityHash()) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.OPERAND,
                    "Prepared-text capability identity changed after recording.",
                )
            }
        }
        capabilities?.let { observed ->
            textPackets.zip(bindings).forEach { (evidence, binding) ->
                val semantic = evidence.semantic as? GPUDrawSemanticPayload.TextA8
                    ?: return@forEach
                GPUPreparedTextCompositePreflight.validate(
                    binding = binding,
                    semantic = semantic,
                    capabilities = observed,
                    framePlan = framePlan,
                    renderSourceStepIndex = evidence.renderIndex,
                )?.let { refusal ->
                    return refused(refusal.code, refusal.message)
                }
            }
        }

        val expectedTextAllocations = buildList {
            requiredPages.forEach { page -> addAll(page.memoryAllocations) }
            requiredMaterialResources.forEach { resource ->
                addAll(resource.memoryAllocations)
            }
            bindings.map(GPUPreparedTextRenderBinding::instanceBufferPlan)
                .distinctBy { plan -> plan.bufferRef }
                .forEach { plan ->
                    add(plan.memoryAllocation)
                }
            bindings.filter(GPUPreparedTextRenderBinding::hasColorGlyphBufferPlan)
                .map(GPUPreparedTextRenderBinding::colorGlyphBufferPlan)
                .distinctBy { plan -> plan.planArtifactKey }
                .forEach { plan ->
                    addAll(plan.memoryAllocations)
                }
            bindings.mapNotNull(GPUPreparedTextRenderBinding::materialUniformBufferPlan)
                .distinctBy { plan -> plan.bufferRef }
                .forEach { plan ->
                    add(plan.memoryAllocation)
                }
            bindings.filter(GPUPreparedTextRenderBinding::hasTextA8Composite)
                .map(GPUPreparedTextRenderBinding::drawUniformBufferPlan)
                .distinctBy { plan -> plan.bufferRef }
                .forEach { plan ->
                    add(plan.memoryAllocation)
                }
            targetPreparations.values
                .filter { request ->
                    request.role == GPUFrameResourceRole.DestinationSnapshot
                }
                .forEach { request ->
                    val descriptor = when (val value = request.descriptor) {
                        is GPUFrameTextureDescriptor -> value
                        else -> return refused(
                            GPUPreparedTextPreflightRefusalCodes.OPERAND,
                            "Prepared ColorGlyph destination snapshot must be a texture allocation.",
                        )
                    }
                    add(
                        GPUFrameMemoryAllocation(
                            label = request.diagnosticLabel,
                            category = GPUFrameMemoryCategory.DestinationSnapshot,
                            bytes = request.byteSize,
                            resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                            extent = descriptor.logicalBounds,
                        ),
                    )
                }
        }
        val expectedTextAllocationsByLabel =
            expectedTextAllocations.associateBy(GPUFrameMemoryAllocation::label)
        val actualTextAllocations = framePlan.memoryBudget.allocations.filter { allocation ->
            allocation.label.startsWith("prepared-text.") ||
                allocation.label.startsWith("prepared-color-glyph.") ||
                allocation.label.startsWith("prepared-r8.") ||
                allocation.label.startsWith("prepared-material.")
        }
        val actualTextAllocationsByLabel =
            actualTextAllocations.associateBy(GPUFrameMemoryAllocation::label)
        val configuredAggregateBudgetBytes =
            framePlan.memoryBudget.configuredAggregateBudgetBytes
        val exactAggregateBudget = if (configuredAggregateBudgetBytes > 0L) {
            capabilities?.limits?.let { limits ->
                GPUFrameMemoryBudgetPlanner.plan(
                    GPUFrameMemoryBudgetRequest(
                        allocations = framePlan.memoryBudget.allocations,
                        configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                        deviceLimits = limits,
                    ),
                )
            }
        } else {
            null
        }
        if (expectedTextAllocations.size != expectedTextAllocationsByLabel.size ||
            actualTextAllocations.size != actualTextAllocationsByLabel.size ||
            actualTextAllocationsByLabel != expectedTextAllocationsByLabel ||
            configuredAggregateBudgetBytes <= 0L ||
            !GPUFrameMemoryBudgetPlanner.hasExactLimitIndependentFacts(
                framePlan.memoryBudget,
            ) ||
            exactAggregateBudget != null &&
            exactAggregateBudget != framePlan.memoryBudget
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND,
                "Prepared-text allocations and aggregate frame memory budget must be exact.",
            )
        }

        val textResources = bindings
            .flatMap(GPUPreparedTextRenderBinding::preparedTextResourceRefs)
            .toSet()
        if (textResources.any { resource ->
                targetPreparations[resource]?.lifetime !=
                    GPUFrameResourceLifetime.FrameLocal
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.RESOURCE_LIFETIME,
                "Prepared-text atlas, instance, uniform, and material resources must be frame-local.",
            )
        }
        val exactTexturePlanRequests = (
            requiredPages.flatMap(GPUR8FrameResourcePlan::preparationRequests) +
                requiredMaterialResources.flatMap { plan -> plan.preparationRequests }
            )
        if (exactTexturePlanRequests.any { expected ->
                targetPreparations[expected.resource]?.samePreparationAs(expected) != true
            } ||
            bindings.filter(GPUPreparedTextRenderBinding::hasColorGlyphBufferPlan)
                .map(GPUPreparedTextRenderBinding::colorGlyphBufferPlan)
                .distinctBy { plan -> plan.planArtifactKey }
                .flatMap { plan -> plan.preparationRequests }
                .any { expected ->
                    targetPreparations[expected.resource]?.samePreparationAs(expected) != true
                } ||
            bindings.any { binding ->
                !targetPreparations[binding.instanceBufferPlan.bufferRef]
                    .matchesPreparedTextBufferPlan(
                        byteSize = binding.instanceBufferPlan.byteSize,
                        alignmentBytes = binding.instanceBufferPlan.alignmentBytes.toLong(),
                        role = GPUFrameResourceRole.VertexData,
                        usages = setOf(
                            GPUFrameResourceUsage.Vertex,
                            GPUFrameResourceUsage.CopyDestination,
                        ),
                    ) ||
                    binding.hasTextA8Composite &&
                    !targetPreparations[binding.drawUniformBufferPlan.bufferRef]
                        .matchesPreparedTextBufferPlan(
                            byteSize = binding.drawUniformBufferPlan.byteSize,
                            alignmentBytes = binding.drawUniformBufferPlan.alignmentBytes,
                            role = GPUFrameResourceRole.UniformData,
                            usages = setOf(
                                GPUFrameResourceUsage.Uniform,
                                GPUFrameResourceUsage.CopyDestination,
                            ),
                        ) ||
                    binding.materialUniformBufferPlan?.let { uniform ->
                        !targetPreparations[uniform.bufferRef]
                            .matchesPreparedTextBufferPlan(
                                byteSize = uniform.byteSize,
                                alignmentBytes = uniform.alignmentBytes,
                                role = GPUFrameResourceRole.UniformData,
                                usages = setOf(
                                    GPUFrameResourceUsage.Uniform,
                                    GPUFrameResourceUsage.CopyDestination,
                                ),
                            )
                    } == true
            } ||
            r8Uploads.any { upload ->
                !upload.step.matchesPreparedTextTextureUpload(upload.plan)
            } ||
            materialUploads.any { upload ->
                !upload.step.matchesPreparedTextTextureUpload(upload.plan)
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND_OWNERSHIP,
                "Prepared-text declarations and uploads must match their immutable plans exactly.",
            )
        }
        textPackets.groupBy(PreparedTextPacketEvidence::render).forEach { (render, packetsInRun) ->
            val runBindings = packetsInRun.map { packet ->
                render.preparedTextBindingsByPacketId.getValue(packet.packet.packetId)
            }
            val expectedUses = buildList {
                runBindings.map(GPUPreparedTextRenderBinding::atlasResourcePlan)
                    .distinctBy(GPUR8FrameResourcePlan::r8ArtifactIdentity)
                    .forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.frameTextureRef,
                                GPUFrameResourceRole.GlyphAtlas,
                                GPUFrameResourceUsage.TextureBinding,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                runBindings.map(GPUPreparedTextRenderBinding::instanceBufferPlan)
                    .distinctBy { plan -> plan.bufferRef }
                    .forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.bufferRef,
                                GPUFrameResourceRole.VertexData,
                                GPUFrameResourceUsage.Vertex,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                runBindings.mapNotNull(GPUPreparedTextRenderBinding::coverageMaskResource)
                    .distinct()
                    .forEach { resource ->
                        add(
                            GPUFrameResourceUse(
                                resource,
                                GPUFrameResourceRole.ClipMask,
                                GPUFrameResourceUsage.TextureBinding,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                runBindings.filter(GPUPreparedTextRenderBinding::hasColorGlyphBufferPlan)
                    .map(GPUPreparedTextRenderBinding::colorGlyphBufferPlan)
                    .distinctBy { plan -> plan.planArtifactKey }
                    .forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.vertexBufferRef,
                                GPUFrameResourceRole.VertexData,
                                GPUFrameResourceUsage.Vertex,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                        add(
                            GPUFrameResourceUse(
                                plan.indexBufferRef,
                                GPUFrameResourceRole.IndexData,
                                GPUFrameResourceUsage.Index,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                        add(
                            GPUFrameResourceUse(
                                plan.uniformBufferRef,
                                GPUFrameResourceRole.UniformData,
                                GPUFrameResourceUsage.Uniform,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                runBindings.filter(GPUPreparedTextRenderBinding::hasTextA8Composite)
                    .map(GPUPreparedTextRenderBinding::drawUniformBufferPlan)
                    .distinctBy { plan -> plan.bufferRef }
                    .forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.bufferRef,
                                GPUFrameResourceRole.UniformData,
                                GPUFrameResourceUsage.Uniform,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                runBindings.mapNotNull(GPUPreparedTextRenderBinding::materialUniformBufferPlan)
                    .distinctBy { plan -> plan.bufferRef }
                    .forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.bufferRef,
                                GPUFrameResourceRole.UniformData,
                                GPUFrameResourceUsage.Uniform,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                runBindings.flatMap(GPUPreparedTextRenderBinding::materialSampledResourcePlans)
                    .distinctBy { plan -> plan.resourceKey }
                    .forEach { plan ->
                        add(
                            GPUFrameResourceUse(
                                plan.frameTextureRef,
                                GPUFrameResourceRole.StorageData,
                                GPUFrameResourceUsage.TextureBinding,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
            }
            val drawUniformRefs = runBindings
                .filter(GPUPreparedTextRenderBinding::hasTextA8Composite)
                .map { binding -> binding.drawUniformBufferPlan.bufferRef }
                .toSet()
            val destinationSnapshotRefs = render.resourceUses
                .filter { use -> use.role == GPUFrameResourceRole.DestinationSnapshot }
                .map(GPUFrameResourceUse::resource)
                .toSet()
            val separatelyAuthenticatedRefs = drawUniformRefs + destinationSnapshotRefs
            if (render.resourceUses.filterNot { use ->
                    use.resource in separatelyAuthenticatedRefs
                } != expectedUses.filterNot { use ->
                    use.resource in separatelyAuthenticatedRefs
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.OPERAND,
                    "Prepared-text render operands must form the exact ordered Task 8 partition.",
                )
            }
        }
        if (bindings.any { binding ->
                !targetPreparations[binding.atlasResourcePlan.stagingRef]
                    .matchesPreparedTextOwnership(
                        GPUFrameResourceRole.UploadStaging,
                        setOf(GPUFrameResourceUsage.CopySource),
                    ) ||
                    !targetPreparations[binding.atlasResourcePlan.frameTextureRef]
                        .matchesPreparedTextOwnership(
                            GPUFrameResourceRole.GlyphAtlas,
                            setOf(
                                GPUFrameResourceUsage.CopyDestination,
                                GPUFrameResourceUsage.TextureBinding,
                            ),
                        ) ||
                    !targetPreparations[binding.instanceBufferPlan.bufferRef]
                        .matchesPreparedTextOwnership(
                            GPUFrameResourceRole.VertexData,
                            setOf(
                                GPUFrameResourceUsage.Vertex,
                                GPUFrameResourceUsage.CopyDestination,
                            ),
                        ) ||
                    binding.hasColorGlyphBufferPlan &&
                    (
                        !targetPreparations[binding.colorGlyphBufferPlan.vertexBufferRef]
                            .matchesPreparedTextOwnership(
                                GPUFrameResourceRole.VertexData,
                                setOf(
                                    GPUFrameResourceUsage.Vertex,
                                    GPUFrameResourceUsage.CopyDestination,
                                ),
                            ) ||
                            !targetPreparations[binding.colorGlyphBufferPlan.indexBufferRef]
                                .matchesPreparedTextOwnership(
                                    GPUFrameResourceRole.IndexData,
                                    setOf(
                                        GPUFrameResourceUsage.Index,
                                        GPUFrameResourceUsage.CopyDestination,
                                    ),
                                ) ||
                            !targetPreparations[binding.colorGlyphBufferPlan.uniformBufferRef]
                                .matchesPreparedTextOwnership(
                                    GPUFrameResourceRole.UniformData,
                                    setOf(
                                        GPUFrameResourceUsage.Uniform,
                                        GPUFrameResourceUsage.CopyDestination,
                                    ),
                                )
                        ) ||
                    binding.materialUniformBufferPlan?.let { uniform ->
                        !targetPreparations[uniform.bufferRef]
                            .matchesPreparedTextOwnership(
                                GPUFrameResourceRole.UniformData,
                                setOf(
                                    GPUFrameResourceUsage.Uniform,
                                    GPUFrameResourceUsage.CopyDestination,
                                ),
                            )
                    } == true ||
                    binding.materialSampledResourcePlans.any { resource ->
                        !targetPreparations[resource.stagingRef]
                            .matchesPreparedTextOwnership(
                                GPUFrameResourceRole.UploadStaging,
                                setOf(GPUFrameResourceUsage.CopySource),
                            ) ||
                            !targetPreparations[resource.frameTextureRef]
                                .matchesPreparedTextOwnership(
                                    GPUFrameResourceRole.StorageData,
                                    setOf(
                                        GPUFrameResourceUsage.CopyDestination,
                                        GPUFrameResourceUsage.TextureBinding,
                                    ),
                                )
                    }
            }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND_OWNERSHIP,
                "Prepared-text operands must retain their exact frame-local usage ownership.",
            )
        }

        validatePreparedSurfaceDependencies(framePlan)?.let { return it }
        textPackets.zip(bindings).firstOrNull { (evidence, binding) ->
            !evidence.semantic.hasPreparedTextCanonicalIntegrity() ||
                binding.preflightSeal.semanticCanonicalHash !=
                evidence.semantic.preparedTextCanonicalHash()
        }?.let {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.OPERAND,
                "Prepared-text semantic and binding seals must retain one canonical identity.",
            )
        }

        return null
    }

    /**
     * Pure handle-free admission for every prepared-vertices packet in the frame.
     *
     * The semantic gatherer already proved most artifact facts; this authority
     * re-verifies exactly what crosses the native seam: packet identity, canonical
     * hash, immutable upload bytes, canonical layout/topology, transform, bounds,
     * material frame identity, the Task 8 shader ABI, upload steps and ranges,
     * resource usages and generations, the upload-before-use graph, budgets, and
     * target compatibility. Refusals are terminal before any native call.
     */
    private fun validatePreparedVerticesAuthority(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext?,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val verticesEvidence = framePlan.steps.withIndex()
            .mapNotNull { (index, step) ->
                (step as? GPUFrameStep.RenderPassStep)?.let { index to it }
            }
            .flatMap { (renderIndex, render) ->
                render.drawPackets.mapNotNull { packet ->
                    (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)?.let { semantic ->
                        PreparedVerticesPacketEvidence(renderIndex, render, packet, semantic)
                    }
                }
            }
        if (verticesEvidence.isEmpty()) return null

        verticesEvidence.firstOrNull { evidence ->
            val packet = evidence.packet
            val semantic = evidence.semantic
            semantic.payloadRef.commandIdValue != packet.commandIdValue ||
                semantic.payloadRef.renderStepIdentity != packet.renderStepId.value
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.SEMANTIC,
                "Prepared-vertices packet and semantic identities changed after recording.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            val semantic = evidence.semantic
            semantic.clipIdentity.isBlank() ||
                semantic.clipCoverageIdentity.isBlank() ||
                semantic.finalBlendIdentity.isBlank() ||
                semantic.primitiveColorPresent != (semantic.primitiveBlendIdentity != null) ||
                semantic.primitiveBlendIdentity?.isBlank() == true
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.IDENTITY,
                "Prepared-vertices clip, coverage, blend, and primitive-color identities must be exact.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            val artifact = evidence.semantic.artifact
            artifact.indexCount != null &&
                artifact.indexFormat !in setOf("uint16", "uint32")
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.INDEX_FORMAT,
                "Prepared-vertices index format must be uint16 or uint32.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            !evidence.semantic.artifact.hasExactPreparedVerticesByteIntegrity()
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.ARTIFACT,
                "Prepared-vertices artifact bytes, counts, and content hashes must be exact.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            val semantic = evidence.semantic
            val artifact = semantic.artifact
            semantic.topologyIdentity.sourceLabel != artifact.topology.sourceLabel
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.TOPOLOGY,
                "Prepared-vertices semantic and artifact topologies must agree.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            val transform = evidence.semantic.transformBytes.map(Float::fromBits)
            transform.size != 9 ||
                transform.any { !it.isFinite() } ||
                transform[6] != 0f || transform[7] != 0f || transform[8] != 1f ||
                !(transform[0] * transform[4] - transform[1] * transform[3]).isFinite() ||
                transform[0] * transform[4] - transform[1] * transform[3] == 0f
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.TRANSFORM,
                "Prepared-vertices transform must be finite, affine, and invertible.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            val semantic = evidence.semantic
            semantic.targetBounds.isEmpty ||
                semantic.scissorBounds.isEmpty ||
                semantic.scissorBounds.left < semantic.targetBounds.left ||
                semantic.scissorBounds.top < semantic.targetBounds.top ||
                semantic.scissorBounds.right > semantic.targetBounds.right ||
                semantic.scissorBounds.bottom > semantic.targetBounds.bottom
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.BOUNDS,
                "Prepared-vertices target and scissor bounds must be exact and contained.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            val material = evidence.semantic.material
            material.abiHash.isBlank() ||
                !material.abiHash.matches(PREPARED_VERTICES_ABI_HASH_PATTERN) ||
                material.materialKey.isBlank() ||
                evidence.semantic.materialIdentity.isBlank()
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.MATERIAL_ABI,
                "Prepared-vertices material key and ABI facts must remain canonical.",
            )
        }
        val refusedVerticesShader = verticesEvidence.firstNotNullOfOrNull { evidence ->
            val semantic = evidence.semantic
            when (
                val result = PreparedVerticesShaderAssembler.assemble(
                    layout = semantic.artifact.layout,
                    topology = semantic.artifact.topology,
                    material = semantic.material,
                    hasPrimitiveColor = semantic.primitiveColorPresent,
                )
            ) {
                is GPUPreparedVerticesShaderResult.Ready -> null
                is GPUPreparedVerticesShaderResult.Refused -> result
            }
        }
        refusedVerticesShader?.let { result ->
            return refused(
                result.code,
                "Prepared-vertices WGSL assembly refused before native preflight: " +
                    result.message,
            )
        }

        val deviceGeneration = framePlan.capabilitySeal.deviceGeneration.value
        val plans = try {
            verticesEvidence.map { evidence -> evidence.semantic.artifact }
                .distinctBy { artifact -> artifact.key }
                .sortedBy { artifact -> artifact.key }
                .map { artifact -> buildVerticesFrameResourcePlan(artifact, deviceGeneration) }
        } catch (_: IllegalArgumentException) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.ARTIFACT,
                "Prepared-vertices resource planning rejected an artifact.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.ARTIFACT,
                "Prepared-vertices resource planning overflowed.",
            )
        }
        val stagingLayout = try {
            buildVerticesStagingLayout(plans)
        } catch (_: IllegalArgumentException) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_LAYOUT,
                "Prepared-vertices staging ranges are not exact.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_LAYOUT,
                "Prepared-vertices staging byte accounting overflowed.",
            )
        }
        val stagingRef = preparedVerticesStagingRef(framePlan.frameId)
        val uploadSteps = framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>()
        val uploadMatches = try {
            plans.map { plan ->
                val vertexRange = stagingLayout.ranges.singleOrNull { range ->
                    range.artifactKey == plan.artifactKey && range.bufferKind == "vertex"
                } ?: throw IllegalArgumentException(
                    "Prepared-vertices vertex staging range is missing",
                )
                val indexRange = plan.indexBuffer?.let { index ->
                    stagingLayout.ranges.singleOrNull { range ->
                        range.artifactKey == plan.artifactKey && range.bufferKind == "index"
                    } ?: throw IllegalArgumentException(
                        "Prepared-vertices index staging range is missing",
                    )
                }
                PreparedVerticesUploadMatch(
                    artifactKey = plan.artifactKey,
                    plan = plan,
                    vertexSteps = uploadSteps.filter { step ->
                        step.destinationKind == GPUUploadDestinationKind.Buffer &&
                            step.staging == stagingRef &&
                            step.destination == preparedVerticesVertexBufferRef(
                                framePlan.frameId,
                                plan.artifactKey,
                            )
                    },
                    indexSteps = uploadSteps.filter { step ->
                        step.destinationKind == GPUUploadDestinationKind.Buffer &&
                            step.staging == stagingRef &&
                            step.destination == preparedVerticesIndexBufferRef(
                                framePlan.frameId,
                                plan.artifactKey,
                            )
                    },
                    vertexRange = vertexRange,
                    indexRange = indexRange,
                )
            }
        } catch (failure: IllegalArgumentException) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_LAYOUT,
                "Prepared-vertices upload range derivation failed " +
                    "(${failure::class.simpleName}): ${failure.message ?: "unknown"}",
            )
        } catch (_: ArithmeticException) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_LAYOUT,
                "Prepared-vertices upload range accounting overflowed.",
            )
        }
        uploadMatches.firstOrNull { match ->
            match.vertexSteps.isEmpty() ||
                (match.plan.indexBuffer != null && match.indexSteps.isEmpty()) ||
                (match.plan.indexBuffer == null && match.indexSteps.isNotEmpty())
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_MISSING,
                "Every prepared-vertices artifact requires its exact vertex and index upload steps.",
            )
        }
        uploadMatches.firstOrNull { match ->
            match.vertexSteps.size != 1 ||
                if (match.plan.indexBuffer != null) {
                    match.indexSteps.size != 1
                } else {
                    match.indexSteps.isNotEmpty()
                }
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_DUPLICATE,
                "One prepared-vertices artifact range must be claimed by exactly one upload step.",
            )
        }
        uploadMatches.firstOrNull { match ->
            val consumerRenderIndex = verticesEvidence
                .filter { evidence -> evidence.semantic.artifact.key == match.artifactKey }
                .minOf { evidence -> evidence.renderIndex }
            framePlan.steps.indexOf(match.vertexSteps.single()) >= consumerRenderIndex ||
                (match.indexRange != null &&
                    framePlan.steps.indexOf(match.indexSteps.single()) >= consumerRenderIndex)
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_ORDER,
                "A prepared-vertices upload must never appear after its first consuming draw.",
            )
        }
        uploadMatches.firstOrNull { match ->
            !match.vertexSteps.single().matchesPreparedVerticesUploadRange(match.vertexRange) ||
                (match.indexRange != null &&
                    !match.indexSteps.single().matchesPreparedVerticesUploadRange(
                        match.indexRange,
                    ))
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.UPLOAD_LAYOUT,
                "Prepared-vertices upload layouts must exactly match the sealed staging ranges.",
            )
        }
        val preparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val stagingPreparation = preparations.singleOrNull { request ->
            request.resource == stagingRef
        }
        if (stagingPreparation == null ||
            stagingPreparation.role != GPUFrameResourceRole.UploadStaging ||
            stagingPreparation.usages != setOf(GPUFrameResourceUsage.CopySource) ||
            stagingPreparation.lifetime != GPUFrameResourceLifetime.FrameLocal ||
            stagingPreparation.byteSize != stagingLayout.totalBytes ||
            (stagingPreparation.descriptor as? GPUFrameBufferDescriptor)?.byteSize !=
            stagingLayout.totalBytes
        ) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.USAGE,
                "The prepared-vertices staging buffer must retain its exact upload ownership.",
            )
        }
        uploadMatches.firstOrNull { match ->
            val plan = match.plan
            val vertexPreparation = preparations.singleOrNull { request ->
                request.resource == preparedVerticesVertexBufferRef(
                    framePlan.frameId,
                    plan.artifactKey,
                )
            }
            val indexPreparation = plan.indexBuffer?.let { index ->
                preparations.singleOrNull { request ->
                    request.resource == preparedVerticesIndexBufferRef(
                        framePlan.frameId,
                        plan.artifactKey,
                    )
                }
            }
            !vertexPreparation.matchesPreparedVerticesBufferPlan(
                role = GPUFrameResourceRole.VertexData,
                usages = setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.Vertex,
                ),
                byteCount = plan.vertexBuffer.byteCount,
            ) ||
                (
                    plan.indexBuffer != null &&
                        !indexPreparation.matchesPreparedVerticesBufferPlan(
                            role = GPUFrameResourceRole.IndexData,
                            usages = setOf(
                                GPUFrameResourceUsage.CopyDestination,
                                GPUFrameResourceUsage.Index,
                            ),
                            byteCount = plan.indexBuffer.byteCount,
                        )
                    )
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.USAGE,
                "Prepared-vertices vertex and index buffers must retain exact copy-destination usages.",
            )
        }
        context?.let { current ->
            val verticesRefs = buildList {
                add(stagingRef)
                plans.forEach { plan ->
                    add(
                        preparedVerticesVertexBufferRef(framePlan.frameId, plan.artifactKey),
                    )
                    if (plan.indexBuffer != null) {
                        add(
                            preparedVerticesIndexBufferRef(framePlan.frameId, plan.artifactKey),
                        )
                    }
                }
            }
            if (verticesRefs.any { ref -> current.resourceGenerations[ref] == null }) {
                return refused(
                    GPUPreparedVerticesPreflightRefusalCodes.GENERATION,
                    "Every prepared-vertices resource requires exact current generation evidence.",
                )
            }
        }
        validatePreparedSurfaceDependencies(framePlan)?.let { return it }

        val expectedVerticesAllocations = buildList {
            add(
                GPUFrameMemoryAllocation(
                    label = "prepared-vertices.staging",
                    category = GPUFrameMemoryCategory.ReusableScratch,
                    bytes = stagingLayout.totalBytes,
                    resourceKind = GPUFrameMemoryResourceKind.Buffer,
                    extent = null,
                ),
            )
            plans.forEach { plan ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-vertices.vertex.${plan.artifactKey}",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = plan.vertexBuffer.byteCount,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
                plan.indexBuffer?.let { index ->
                    add(
                        GPUFrameMemoryAllocation(
                            label = "prepared-vertices.index.${plan.artifactKey}",
                            category = GPUFrameMemoryCategory.ReusableScratch,
                            bytes = index.byteCount,
                            resourceKind = GPUFrameMemoryResourceKind.Buffer,
                            extent = null,
                        ),
                    )
                }
            }
        }
        val expectedVerticesAllocationsByLabel =
            expectedVerticesAllocations.associateBy(GPUFrameMemoryAllocation::label)
        val actualVerticesAllocations = framePlan.memoryBudget.allocations.filter { allocation ->
            allocation.label.startsWith("prepared-vertices.")
        }
        val actualVerticesAllocationsByLabel =
            actualVerticesAllocations.associateBy(GPUFrameMemoryAllocation::label)
        var aggregateUploadBytes = 0L
        plans.forEach { plan ->
            aggregateUploadBytes = try {
                Math.addExact(aggregateUploadBytes, plan.totalByteCount)
            } catch (_: ArithmeticException) {
                return refused(
                    GPUPreparedVerticesPreflightRefusalCodes.BUDGET,
                    "Prepared-vertices aggregate upload byte accounting overflowed.",
                )
            }
        }
        if (expectedVerticesAllocations.size != expectedVerticesAllocationsByLabel.size ||
            actualVerticesAllocations.size != actualVerticesAllocationsByLabel.size ||
            actualVerticesAllocationsByLabel != expectedVerticesAllocationsByLabel ||
            aggregateUploadBytes !=
            actualVerticesAllocations
                .filter { allocation -> allocation.label != "prepared-vertices.staging" }
                .sumOf { allocation -> allocation.bytes } ||
            framePlan.memoryBudget.diagnostic != null
        ) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.BUDGET,
                "Prepared-vertices allocations and aggregate frame memory budget must be exact.",
            )
        }
        val sceneFormat = (
            framePlan.steps
                .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
                ?.descriptor as? GPUFrameTextureDescriptor
            )?.format
        if (sceneFormat == null ||
            verticesEvidence.any { evidence ->
                evidence.semantic.targetFormat != sceneFormat.value
            }
        ) {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.TARGET,
                "Prepared-vertices target format must match the scene target format.",
            )
        }
        verticesEvidence.firstOrNull { evidence ->
            !evidence.semantic.hasCanonicalHashIntegrity()
        }?.let {
            return refused(
                GPUPreparedVerticesPreflightRefusalCodes.HASH,
                "Prepared-vertices semantic canonical hash changed after recording.",
            )
        }
        return null
    }

    /**
     * Exact ordered producer-consumer graph for every prepared-surface semantic
     * family (image, text, material, vertices, clip, destination, readback).
     *
     * The vertices edges mirror the recording builder: every artifact upload
     * precedes its consuming render through the sealed upload token, and every
     * sampled material upload precedes the vertices draw that samples it.
     */
    private fun validatePreparedSurfaceDependencies(
        framePlan: GPUFramePlan,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val prepareTaskId = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .singleOrNull()
            ?.sourceTaskIds
            ?.singleOrNull()
            ?: return refused(
                GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                "Prepared-text preflight requires one exact resource-preparation task.",
            )
        val uploads = framePlan.steps.filterIsInstance<GPUFrameStep.UploadResourceStep>()
        val allRenders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        val renders = allRenders.filterNot { render ->
            render.drawPackets.isNotEmpty() &&
                render.drawPackets.all { packet ->
                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                        .GPUDrawPacketRole.ClipProducer
                }
        }
        if (uploads.any { it.sourceTaskIds.size != 1 } ||
            allRenders.any { it.sourceTaskIds.size != 1 }
        ) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                "Prepared-surface upload and render steps must retain one exact task identity.",
            )
        }
        val imageUploads = uploads.filter { it.imageResourcePlan != null }
        val r8Uploads = uploads.filter { it.r8ResourcePlan != null }
        val materialUploads = uploads.filter { it.materialResourcePlan != null }
        val expected = mutableListOf<GPUTaskDependency>()
        fun append(
            from: GPUTaskID,
            to: GPUTaskID,
            kind: String,
            reason: String,
            token: String,
        ) {
            expected += GPUTaskDependency(
                fromTaskId = from,
                toTaskId = to,
                dependencyKind = kind,
                useToken = GPUTaskUseToken(token),
                reasonCode = reason,
            )
        }

        val destinationCopies =
            framePlan.steps.filterIsInstance<GPUFrameStep.CopyDestinationStep>()
        if (destinationCopies.isNotEmpty()) {
            val destinationTaskIds = destinationCopies
                .flatMap(GPUFrameStep.CopyDestinationStep::sourceTaskIds)
                .distinct()
            val destinationTaskId = destinationTaskIds.singleOrNull()
                ?: return refused(
                    GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                    "Prepared ColorGlyph destination copies require one exact Task 5 owner.",
                )
            append(
                prepareTaskId,
                destinationTaskId,
                "prepared-color-glyph-destination-resource-order",
                "prepared.color-glyph.prepare-before-destination-snapshot",
                "prepared-color-glyph.destination.prepare",
            )
            destinationCopies.flatMap(GPUFrameStep.CopyDestinationStep::consumers)
                .map { consumer -> consumer.renderTaskId }
                .distinct()
                .forEach { renderTaskId ->
                    append(
                        destinationTaskId,
                        renderTaskId,
                        "prepared-color-glyph-destination-consumer-order",
                        "prepared.color-glyph.destination-snapshot-before-consumer",
                        "prepared-color-glyph.destination.consumer.$renderTaskId",
                    )
                }
        }
        imageUploads.forEachIndexed { index, upload ->
            append(
                prepareTaskId,
                upload.sourceTaskIds.single(),
                "prepared-image-resource-order",
                "prepared.image.prepare-before-upload",
                "prepared-image.prepare.$index",
            )
        }
        r8Uploads.forEachIndexed { index, upload ->
            append(
                prepareTaskId,
                upload.sourceTaskIds.single(),
                "prepared-text-resource-order",
                "prepared.text.prepare-before-upload",
                "prepared-text.prepare.$index",
            )
        }
        materialUploads.forEachIndexed { index, upload ->
            append(
                prepareTaskId,
                upload.sourceTaskIds.single(),
                "prepared-text-material-resource-order",
                "prepared.text.material-prepare-before-upload",
                "prepared-text.material-prepare.$index",
            )
        }
        val verticesUploads = uploads.filter { upload ->
            upload.staging == preparedVerticesStagingRef(framePlan.frameId)
        }
        val verticesPlanByArtifactKey = preparedVerticesPlansByArtifactKey(framePlan)
        val verticesVertexUploadByArtifactKey = verticesUploads
            .filter { upload -> upload.destination.value.contains("prepared-vertices.vertex") }
            .associateBy { upload ->
                preparedVerticesArtifactKeyOfUpload(upload)
            }
        val verticesIndexUploadByArtifactKey = verticesUploads
            .filter { upload -> upload.destination.value.contains("prepared-vertices.index") }
            .associateBy { upload ->
                preparedVerticesArtifactKeyOfUpload(upload)
            }
        val orderedVerticesUploads = buildList {
            verticesPlanByArtifactKey.keys.sorted().forEach { artifactKey ->
                verticesVertexUploadByArtifactKey[artifactKey]?.let(::add)
            }
            verticesPlanByArtifactKey.keys.sorted().forEach { artifactKey ->
                verticesIndexUploadByArtifactKey[artifactKey]?.let(::add)
            }
        }
        orderedVerticesUploads.forEachIndexed { index, upload ->
            append(
                prepareTaskId,
                upload.sourceTaskIds.single(),
                "prepared-vertices-resource-order",
                "prepared.vertices.prepare-before-upload",
                "prepared-vertices.prepare.$index",
            )
        }
        val coverageMaskProducerRenders = allRenders.filter { render ->
            render.drawPackets.isNotEmpty() &&
                render.drawPackets.all { packet ->
                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                        .GPUDrawPacketRole.ClipProducer
                }
        }
        coverageMaskProducerRenders.forEachIndexed { index, producer ->
            append(
                prepareTaskId,
                producer.sourceTaskIds.single(),
                "prepared-surface-resource-order",
                "prepared.surface.prepare-before-clip-producer",
                "prepared-surface.clip-producer.$index",
            )
        }
        renders.forEachIndexed { index, render ->
            val renderTaskId = render.sourceTaskIds.single()
            append(
                prepareTaskId,
                renderTaskId,
                "prepared-surface-resource-order",
                "prepared.surface.prepare-before-consumer",
                "prepared-surface.prepare.$index",
            )
            val imageProducerTaskIds = mutableListOf<GPUTaskID>()
            render.drawPackets.mapNotNull { packet ->
                packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage
            }.forEach { semantic ->
                imageProducerTaskIds += imageUploads.singleOrNull { upload ->
                    upload.imageResourcePlan?.artifactKey == semantic.artifact.key
                }?.sourceTaskIds?.singleOrNull()
                    ?: return refused(
                        GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                        "Every prepared-image consumer requires one exact upload producer.",
                    )
            }
            imageProducerTaskIds.distinct().forEach { uploadTaskId ->
                append(
                    uploadTaskId,
                    renderTaskId,
                    "prepared-image-resource-order",
                    "prepared.image.upload-before-consumer",
                    "prepared-image.consumer.${expected.size}",
                )
            }
            val textBindings = render.drawPackets.mapNotNull { packet ->
                if (packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                    packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph
                ) {
                    render.preparedTextBindingsByPacketId[packet.packetId]
                } else {
                    null
                }
            }
            if (textBindings.size != render.drawPackets.count { packet ->
                    packet.semanticPayload is GPUDrawSemanticPayload.TextA8 ||
                        packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph
                }
            ) {
                return refused(
                    GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                    "Every prepared-text consumer requires one exact binding dependency.",
                )
            }
            val atlasProducerTaskIds = textBindings.map { binding ->
                r8Uploads.singleOrNull { upload ->
                    upload.r8ResourcePlan?.samePreparedTextPageAs(
                        binding.atlasResourcePlan,
                    ) == true
                }?.sourceTaskIds?.singleOrNull()
                    ?: return refused(
                        GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                        "Every prepared-text atlas consumer requires one exact upload producer.",
                    )
            }.distinct()
            atlasProducerTaskIds.forEach { uploadTaskId ->
                append(
                    uploadTaskId,
                    renderTaskId,
                    "prepared-text-resource-order",
                    "prepared.text.upload-before-consumer",
                    "prepared-text.consumer.${expected.size}",
                )
            }
            val materialProducerTaskIds = textBindings
                .flatMap(GPUPreparedTextRenderBinding::materialSampledResourcePlans)
                .map { required ->
                    materialUploads.singleOrNull { upload ->
                        upload.materialResourcePlan?.samePreparedMaterialResourceAs(
                            required,
                        ) == true
                    }?.sourceTaskIds?.singleOrNull()
                        ?: return refused(
                            GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                            "Every sampled material consumer requires one exact upload producer.",
                        )
                }
                .distinct()
            materialProducerTaskIds.forEach { uploadTaskId ->
                append(
                    uploadTaskId,
                    renderTaskId,
                    "prepared-text-material-resource-order",
                    "prepared.text.material-upload-before-consumer",
                    "prepared-text.material-consumer.${expected.size}",
                )
            }
            val verticesArtifactKeys = render.drawPackets.mapNotNull { packet ->
                (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)?.artifact?.key
            }.distinct()
            verticesArtifactKeys.forEach { artifactKey ->
                val plan = verticesPlanByArtifactKey[artifactKey]
                    ?: return refused(
                        GPUPreparedVerticesPreflightRefusalCodes.DEPENDENCY,
                        "Every prepared-vertices consumer requires one exact artifact plan.",
                    )
                val vertexUpload = verticesVertexUploadByArtifactKey[artifactKey]
                    ?: return refused(
                        GPUPreparedVerticesPreflightRefusalCodes.DEPENDENCY,
                        "Every prepared-vertices consumer requires one exact vertex upload producer.",
                    )
                append(
                    vertexUpload.sourceTaskIds.single(),
                    renderTaskId,
                    "prepared-vertices-resource-order",
                    "prepared.vertices.upload-before-consumer",
                    plan.uploadBeforeUseToken,
                )
                verticesIndexUploadByArtifactKey[artifactKey]?.let { indexUpload ->
                    append(
                        indexUpload.sourceTaskIds.single(),
                        renderTaskId,
                        "prepared-vertices-resource-order",
                        "prepared.vertices.upload-before-consumer",
                        plan.uploadBeforeUseToken,
                    )
                }
            }
            val verticesMaterialProducerTaskIds = render.drawPackets
                .flatMap { packet ->
                    (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)
                        ?.material
                        ?.sampledResources
                        .orEmpty()
                }
                .map { resource ->
                    materialUploads.singleOrNull { upload ->
                        upload.materialResourcePlan?.resourceKey == resource.resourceKey
                    }?.sourceTaskIds?.singleOrNull()
                        ?: return refused(
                            GPUPreparedVerticesPreflightRefusalCodes.DEPENDENCY,
                            "Every sampled vertices material consumer requires one exact upload producer.",
                        )
                }
                .distinct()
            verticesMaterialProducerTaskIds.forEach { uploadTaskId ->
                append(
                    uploadTaskId,
                    renderTaskId,
                    "prepared-vertices-material-resource-order",
                    "prepared.vertices.material-upload-before-consumer",
                    "prepared-vertices.material-consumer.${expected.size}",
                )
            }
            textBindings.mapNotNull(GPUPreparedTextRenderBinding::coverageMaskResource)
                .distinct()
                .forEach { resource ->
                    val producer = coverageMaskProducerRenders.singleOrNull { candidate ->
                        candidate.target == resource &&
                            candidate.resourceUses.any { use ->
                                use.resource == resource &&
                                    use.role == GPUFrameResourceRole.ClipMask &&
                                    use.write
                            }
                    } ?: return refused(
                        GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                        "Every prepared-text mask consumer requires one exact ClipProducer.",
                    )
                    val orderingToken = textBindings.asSequence()
                        .filter { binding -> binding.coverageMaskResource == resource }
                        .mapNotNull { binding ->
                            val textA8 = binding.preflightSeal.textA8Composite?.clipPlan as?
                                GPUPreparedTextClipPlan.CoverageMask
                            val colorGlyph = binding.preflightSeal.colorGlyphClip as?
                                GPUPreparedColorGlyphClipPreflightSeal.CoverageMask
                            textA8?.orderingToken ?: colorGlyph?.orderingToken
                        }
                        .distinct()
                        .singleOrNull()
                        ?: return refused(
                            GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                            "Prepared-text ClipProducer ordering seal is missing.",
                        )
                    append(
                        producer.sourceTaskIds.single(),
                        renderTaskId,
                        "clip-producer-consumer",
                        "preserve.core-primitive.clip.producer-before-consumer",
                        orderingToken,
                    )
                }
        }
        renders.zipWithNext().forEachIndexed { index, (from, to) ->
            append(
                from.sourceTaskIds.single(),
                to.sourceTaskIds.single(),
                "prepared-scene-order",
                "preserve.prepared-scene.order",
                "prepared-surface.paint.$index",
            )
        }
        val readbacks = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        if (readbacks.size > 1 || readbacks.any { it.sourceTaskIds.size != 1 }) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                "Prepared-text preflight accepts at most one exact readback task.",
            )
        }
        readbacks.singleOrNull()?.let { readback ->
            append(
                renders.lastOrNull()?.sourceTaskIds?.single()
                    ?: return refused(
                        GPUPreparedTextPreflightRefusalCodes.DEPENDENCY,
                        "Prepared-text readback requires a final render producer.",
                    ),
                readback.sourceTaskIds.single(),
                "prepared-surface-readback-order",
                "prepared.surface.render-before-readback",
                "prepared-surface.readback",
            )
        }
        if (framePlan.dependencies != expected) {
            val hasVertices = framePlan.steps
                .filterIsInstance<GPUFrameStep.RenderPassStep>()
                .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
                .any { packet -> packet.semanticPayload is GPUDrawSemanticPayload.Vertices }
            return refused(
                if (hasVertices) {
                    GPUPreparedVerticesPreflightRefusalCodes.DEPENDENCY
                } else {
                    GPUPreparedTextPreflightRefusalCodes.DEPENDENCY
                },
                "Prepared-surface dependencies must be one exact ordered producer-consumer graph.",
            )
        }
        return null
    }

    private data class PreparedVerticesPacketEvidence(
        val renderIndex: Int,
        val render: GPUFrameStep.RenderPassStep,
        val packet: GPUDrawPacket,
        val semantic: GPUDrawSemanticPayload.Vertices,
    )

    private data class PreparedVerticesUploadMatch(
        val artifactKey: String,
        val plan: GPUVerticesFrameResourcePlan,
        val vertexSteps: List<GPUFrameStep.UploadResourceStep>,
        val indexSteps: List<GPUFrameStep.UploadResourceStep>,
        val vertexRange: GPUVerticesStagingRange,
        val indexRange: GPUVerticesStagingRange?,
    )

    fun validate(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        shaderContract: GPUPreparedImageShaderContract,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult {
        val colorGlyphCanonicalAuthentication =
            GPUPreparedColorGlyphCanonicalPlanTable.authenticate(
                framePlan.preparedTextBindingsForColorGlyphAuthentication(),
            )
        validateFramePlan(
            framePlan = framePlan,
            context = null,
            capabilities = null,
            colorGlyphCanonicalAuthentication = colorGlyphCanonicalAuthentication,
        )?.let { return it }
        val colorGlyphCanonicalPlanTable =
            (
                colorGlyphCanonicalAuthentication as
                    GPUPreparedColorGlyphCanonicalPlanAuthentication.Accepted
                ).table
        val renders = framePlan.steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
            .filterNot { render ->
                render.drawPackets.isNotEmpty() &&
                    render.drawPackets.all { packet ->
                        packet.role == org.graphiks.kanvas.gpu.renderer.passes
                            .GPUDrawPacketRole.ClipProducer
                    }
            }
        val packets = renders.flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        validatePreparedTextGenerationSeal(
            framePlan,
            resources,
            generationSeal,
        )?.let { return it }
        validateGeneration(
            framePlan,
            encoderPlan,
            resources,
            generationSeal,
        )?.let { return it }
        validateExactEncoderPlan(framePlan, encoderPlan, generationSeal)?.let { return it }
        validateResources(framePlan, encoderPlan, resources, generationSeal)?.let { return it }
        validateColorAuthority(framePlan, renders)?.let { return it }
        val validatedShaderContract = when (
            val validation = validatePreparedImageShader(shaderSource)
        ) {
            is GPUPreparedImageShaderValidationResult.Ready -> validation.shaderContract
            is GPUPreparedImageShaderValidationResult.Refused ->
                return refused(
                    validation.code,
                    "Prepared-image WGSL validation refused before native preflight.",
                    validation.facts,
                )
        }
        if (shaderContract != validatedShaderContract) {
            return refused(
                "invalid.prepared-surface.shader-contract",
                "Prepared-image WGSL source, reflection, and binding identities must be exact.",
            )
        }

        val uploadSteps = framePlan.steps.withIndex()
            .filter { indexed -> indexed.value is GPUFrameStep.UploadResourceStep }
            .map { indexed ->
                indexed.index to indexed.value as GPUFrameStep.UploadResourceStep
            }
        val imageUploads = uploadSteps.mapNotNull { (index, step) ->
            step.imageResourcePlan?.let { plan -> Triple(index, step, plan) }
        }
        if (imageUploads.map { (_, _, plan) -> plan.artifactKey }.distinct().size !=
            imageUploads.size
        ) {
            return refused(
                "unsupported.prepared_image.plan_identity",
                "Every mixed upload scope must retain one unique prepared-image artifact plan.",
            )
        }
        val imagePackets = packets.mapNotNull { packet ->
            (packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage)?.let {
                packet to it
            }
        }
        validateImageScissorAuthority(framePlan, imagePackets)?.let { return it }
        validateImageAuthority(
            framePlan,
            imagePackets,
            imageUploads,
            shaderContract,
        )?.let { return it }
        val exactScopeKeys = encoderPlan.scopes.map { scope ->
            GPUPreparedNativeScopeKey(
                scope.sourceStepIndex,
                scope.operationKind,
                scope.resourceGenerationLabels,
                scope.nativeOperandKeys,
            )
        }
        val scopeByStep = encoderPlan.scopes.associateBy(
            GPUCommandEncoderScopePlan::sourceStepIndex,
        )
        val preparationByResource = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .associateBy(GPUResourcePreparationRequest::resource)
        val evidenceByResource = resources.ordinaryResources.associateBy(
            GPUPreparedResourceEvidence::logicalResource,
        )
        val imageResourceRefs = imageUploads
            .flatMap { (_, _, plan) -> plan.preparationRequests }
            .map(GPUResourcePreparationRequest::resource)
            .toSet()
        val imageFrames = imageUploads.map { (uploadIndex, _, resourcePlan) ->
            val packetIds = resourcePlan.bindingRequests
                .map { binding -> binding.packetId }
                .toSet()
            val consumers = framePlan.steps.mapIndexedNotNull { index, step ->
                (step as? GPUFrameStep.RenderPassStep)
                    ?.takeIf { render ->
                        render.drawPackets.any { packet ->
                            packet.packetId.value in packetIds
                        }
                    }
                    ?.let { index }
            }
            GPUPreparedSurfaceImageFramePlan(
                resourcePlan = resourcePlan,
                uploadScopeKey = exactScopeKeys.single { scope ->
                    scope.sourceStepIndex == uploadIndex
                },
                consumerRenderScopeIndices = consumers,
            )
        }
        val orderedRuns = mutableListOf<GPUPreparedSurfaceNativeRunPlan>()
        framePlan.steps.forEachIndexed { sourceStepIndex, step ->
            val render = step as? GPUFrameStep.RenderPassStep ?: return@forEachIndexed
            val renderScope = scopeByStep[sourceStepIndex]
                ?: return refused(
                    "invalid.prepared-surface.encoder-plan",
                    "A mixed render run is absent from the full encoder plan.",
                )
            val renderScopeKey = exactScopeKeys.single { scope ->
                scope.sourceStepIndex == sourceStepIndex
            }
            val corePackets = render.drawPackets.filter {
                it.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            }
                val imagePackets = render.drawPackets.filter {
                    it.semanticPayload is GPUDrawSemanticPayload.SampledImage
                }
                val isMixedCoreImageRender = corePackets.isNotEmpty() && imagePackets.isNotEmpty()
                if (isMixedCoreImageRender && corePackets.any {
                        it.role == GPUDrawPacketRole.PathStencilProducer
                    }
                ) {
                    return refused(
                        "unsupported.prepared-surface.core-route",
                        "Mixed CorePrimitive/Image runs do not support PathStencilProducer packets.",
                    )
                }
                val coreResourceUses = if (isMixedCoreImageRender) {
                render.resourceUses.filter { use -> use.resource !in imageResourceRefs }
            } else {
                render.resourceUses
            }
            fun renderStepFor(
                packets: List<GPUDrawPacket>,
                resourceUses: List<GPUFrameResourceUse> = render.resourceUses,
                preparedImageBindingsByPacketId:
                    Map<GPUDrawPacketID, GPUImageBindingRequest> = emptyMap(),
            ) = GPUFrameStep.RenderPassStep(
                target = render.target,
                loadStore = render.loadStore,
                samplePlan = render.samplePlan,
                resourceUses = resourceUses,
                drawPackets = packets,
                sourceTaskIds = render.sourceTaskIds,
                sampleContinuation = render.sampleContinuation,
                depthStencilLoadStore = render.depthStencilLoadStore,
                preparedImageBindingsByPacketId = preparedImageBindingsByPacketId,
                preparedTextBindingsByPacketId = render.preparedTextBindingsByPacketId,
            )
            fun imageScopeKeyFor(
                packets: List<GPUDrawPacket>,
            ): GPUPreparedNativeScopeKey {
                val pipelineKeys = renderScopeKey.operandKeys.filter {
                    it.role == GPUPreparedNativeOperandRole.RenderPipeline
                }
                val pipelineKeyByPacketIndex = if (pipelineKeys.size == render.drawPackets.size) {
                    pipelineKeys
                } else {
                    val distinctRenderPipelines = render.drawPackets
                        .map(GPUDrawPacket::renderPipelineKey)
                        .distinct()
                    require(pipelineKeys.size == distinctRenderPipelines.size) {
                        "Prepared mixed-image pipeline keys must retain packet or identity order"
                    }
                    val pipelineKeyByIdentity = distinctRenderPipelines.zip(pipelineKeys).toMap()
                    render.drawPackets.map { packet ->
                        pipelineKeyByIdentity.getValue(packet.renderPipelineKey)
                    }
                }
                val bindGroupKeys = renderScopeKey.operandKeys.filter {
                    it.role == GPUPreparedNativeOperandRole.RenderBindGroup
                }
                val imagePacketIds = packets.map(GPUDrawPacket::packetId).toSet()
                val imagePacketIndexes = render.drawPackets.mapIndexedNotNull { index, packet ->
                    index.takeIf { packet.packetId in imagePacketIds }
                }
                val bindGroupPacketIndexes = render.drawPackets.indices.toList()
                require(bindGroupKeys.size == bindGroupPacketIndexes.size) {
                    "Prepared mixed-image bind-group keys must retain bind-bearing packet order"
                }
                val bindGroupKeyByPacketIndex = bindGroupPacketIndexes.zip(bindGroupKeys).toMap()
                return GPUPreparedNativeScopeKey(
                    sourceStepIndex = renderScopeKey.sourceStepIndex,
                    operationKind = renderScopeKey.operationKind,
                    resourceGenerationLabels = renderScopeKey.resourceGenerationLabels,
                    operandKeys = listOf(
                        renderScopeKey.operandKeys.first {
                            it.role == GPUPreparedNativeOperandRole.RenderColorTarget
                        },
                    ) + packets.map { packet ->
                        val packetIndex = render.drawPackets.indexOfFirst { candidate ->
                            candidate.packetId == packet.packetId
                        }
                        pipelineKeyByPacketIndex[packetIndex]
                    } + imagePacketIndexes.map { index ->
                        bindGroupKeyByPacketIndex.getValue(index)
                    },
                )
            }
            if (corePackets.isNotEmpty()) {
                val routes = renderScope.corePrimitiveNativeScopeRouteSeal as?
                    GPUCorePrimitiveNativeScopeRouteSeal.Routes
                    ?: return refused(
                        "invalid.prepared-surface.core-route",
                        "Every mixed CorePrimitive run requires its exact unified route seal.",
                    )
                if (routes.flattenedPacketIds != corePackets.map(GPUDrawPacket::packetId)) {
                    return refused(
                        "invalid.prepared-surface.core-route",
                        "The CorePrimitive route seal does not retain packet order exactly.",
                    )
                }
                orderedRuns += GPUPreparedSurfaceNativeRunPlan.Core(
                    GPUCorePrimitiveRenderRunPlan(
                        sourceScopeIndices = listOf(sourceStepIndex),
                        packetIds = corePackets.map(GPUDrawPacket::packetId),
                        renderStep = if (isMixedCoreImageRender) {
                            renderStepFor(corePackets, coreResourceUses)
                        } else {
                            render
                        },
                        preparationRequests = coreResourceUses.map { use ->
                            preparationByResource.getValue(use.resource)
                        },
                        resourceEvidences = coreResourceUses.map { use ->
                            evidenceByResource.getValue(use.resource)
                        },
                        routeSeal = routes,
                        exactScopeKey = exactScopeKeys.single { scope ->
                            scope.sourceStepIndex == sourceStepIndex
                        },
                    ),
                )
            }
            if (imagePackets.isNotEmpty()) {
                val runPackets = imagePackets.map { packet ->
                    packet.semanticPayload as GPUDrawSemanticPayload.SampledImage
                }
                val packetIds = imagePackets.map { packet -> packet.packetId.value }.toSet()
                val runResources = imageUploads.filter { (_, _, plan) ->
                    plan.bindingRequests.any { binding -> binding.packetId in packetIds }
                }
                if (runResources.isEmpty()) {
                    return refused(
                        "unsupported.prepared_image.native-binding",
                        "Every image run requires its exact uploaded artifact resources.",
                    )
                }
                val bindingByPacketId = runResources
                    .flatMap { (_, _, plan) -> plan.bindingRequests }
                    .associateBy { binding -> binding.packetId }
                val orderedBindings = imagePackets.map { packet ->
                    bindingByPacketId[packet.packetId.value]
                        ?: return refused(
                            "unsupported.prepared_image.native-binding",
                            "Prepared-image allocations must exactly cover every run packet.",
                        )
                }
                val allocations = orderedBindings.map { binding ->
                    binding.uniformAllocation
                }
                orderedRuns += GPUPreparedSurfaceNativeRunPlan.Image(
                    GPUPreparedSurfaceImageRenderRunPlan(
                        sourceScopeIndex = sourceStepIndex,
                        renderStep = if (isMixedCoreImageRender) {
                            renderStepFor(
                                packets = imagePackets,
                                preparedImageBindingsByPacketId =
                                    render.preparedImageBindingsByPacketId
                                        .filterKeys { packetId -> packetId.value in packetIds },
                            )
                        } else {
                            render
                        },
                        packets = runPackets,
                        resourcePlans = runResources.map { (_, _, plan) -> plan },
                        orderedBindings = orderedBindings,
                        uniformAllocations = allocations,
                        exactScopeKey = if (isMixedCoreImageRender) {
                            imageScopeKeyFor(imagePackets)
                        } else {
                            exactScopeKeys.single { scope ->
                                scope.sourceStepIndex == sourceStepIndex
                            }
                        },
                    ),
                )
            } else if (render.drawPackets.all {
                    it.semanticPayload is GPUDrawSemanticPayload.Vertices
                }
            ) {
                val verticesRun = try {
                    val runPackets = render.drawPackets.map { packet ->
                        packet.semanticPayload as GPUDrawSemanticPayload.Vertices
                    }
                    runPackets.firstOrNull { packet ->
                        packet.material.sampledResources.isNotEmpty()
                    }?.let { packet ->
                        return refused(
                            "unsupported.prepared-vertices.sampled-material",
                            "Prepared-vertices material ${packet.materialIdentity} samples " +
                                "frame resources that require the sampled-material materializer.",
                            mapOf("boundary" to "native"),
                        )
                    }
                    val deviceGeneration = framePlan.capabilitySeal.deviceGeneration.value
                    val runArtifacts = runPackets.map(GPUDrawSemanticPayload.Vertices::artifact)
                        .distinctBy { artifact -> artifact.key }
                        .sortedBy { artifact -> artifact.key }
                    val runPlans = runArtifacts.map { artifact ->
                        buildVerticesFrameResourcePlan(artifact, deviceGeneration)
                    }
                    val drawFacts = render.drawPackets.map { packet ->
                        val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
                        val plan = runPlans.singleOrNull { plan ->
                            plan.artifactKey == semantic.artifact.key
                        } ?: throw IllegalArgumentException(
                            "Prepared-vertices run artifact plan is missing",
                        )
                        GPUPreparedVerticesDrawFacts(
                            packetId = packet.packetId,
                            artifactKey = semantic.artifact.key,
                            vertexCount = semantic.artifact.vertexCount,
                            indexCount = semantic.artifact.indexCount,
                            indexFormat = semantic.artifact.indexFormat,
                            vertexByteCount = plan.vertexBuffer.byteCount,
                            indexByteCount = plan.indexBuffer?.byteCount,
                        )
                    }
                    val shaderProgramByPacketId = render.drawPackets.associate { packet ->
                        val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
                        when (
                            val result = PreparedVerticesShaderAssembler.assemble(
                                layout = semantic.artifact.layout,
                                topology = semantic.artifact.topology,
                                material = semantic.material,
                                hasPrimitiveColor = semantic.primitiveColorPresent,
                            )
                        ) {
                            is GPUPreparedVerticesShaderResult.Ready ->
                                packet.packetId to result.program
                            is GPUPreparedVerticesShaderResult.Refused ->
                                return refused(
                                    result.code,
                                    "Prepared-vertices WGSL assembly refused before native materialization.",
                                )
                        }
                    }
                    val exactScopeKey = exactScopeKeys.singleOrNull { scope ->
                        scope.sourceStepIndex == sourceStepIndex
                    } ?: throw IllegalArgumentException(
                        "Prepared-vertices run scope is missing",
                    )
                    val verticesUploadScopeKeys = framePlan.steps
                        .mapIndexedNotNull { index, step ->
                            (step as? GPUFrameStep.UploadResourceStep)?.takeIf { upload ->
                                upload.destination.value.contains("prepared-vertices.vertex") ||
                                    upload.destination.value.contains("prepared-vertices.index")
                            }?.let { exactScopeKeys.single { scope ->
                                scope.sourceStepIndex == index
                            } }
                        }
                    GPUPreparedSurfaceNativeRunPlan.Vertices(
                        GPUPreparedVerticesRenderRunPlan(
                            sourceScopeIndex = sourceStepIndex,
                            renderStep = render,
                            packets = runPackets,
                            resourcePlans = runPlans,
                            drawFacts = drawFacts,
                            shaderProgramByPacketId = shaderProgramByPacketId,
                            exactScopeKey = exactScopeKey,
                            uploadScopeKeys = verticesUploadScopeKeys,
                        ),
                    )
                } catch (failure: IllegalArgumentException) {
                    return refused(
                        GPUPreparedVerticesPreflightRefusalCodes.ARTIFACT,
                        "Prepared-vertices run plan construction failed " +
                            "(${failure::class.simpleName}): ${failure.message ?: "unknown"}",
                    )
                } catch (_: ArithmeticException) {
                    return refused(
                        GPUPreparedVerticesPreflightRefusalCodes.ARTIFACT,
                        "Prepared-vertices run plan byte accounting overflowed.",
                    )
                }
                orderedRuns += verticesRun
            }
        }
        if (imageFrames.isNotEmpty()) {
            GPUPreparedImagePlanValidator.validateFramePlans(
                imageFrames = imageFrames,
                runs = orderedRuns
                    .filterIsInstance<GPUPreparedSurfaceNativeRunPlan.Image>()
                    .map(GPUPreparedSurfaceNativeRunPlan.Image::plan),
            )?.let { (code, message) ->
                return refused(code, message)
            }
        }
        val textRenderEvidence = framePlan.steps.mapIndexedNotNull { index, step ->
            (step as? GPUFrameStep.RenderPassStep)
                ?.takeIf { render ->
                    render.drawPackets.all { packet ->
                        packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                    }
                }
                ?.let { index to it }
        }
        val textPlan = textRenderEvidence
            .takeIf { it.isNotEmpty() }
            ?.let { textRenders ->
                val textPackets = textRenders.flatMap { (_, render) ->
                    render.drawPackets.map { packet ->
                        packet.semanticPayload as GPUDrawSemanticPayload.TextA8
                    }
                }
                val textBindings = textRenders.flatMap { (_, render) ->
                    render.drawPackets.map { packet ->
                        render.preparedTextBindingsByPacketId.getValue(packet.packetId)
                    }
                }
                val requiredAtlasPlans = textBindings
                    .map(GPUPreparedTextRenderBinding::atlasResourcePlan)
                val requiredMaterialKeys = textBindings
                    .flatMap(GPUPreparedTextRenderBinding::materialSampledResourcePlans)
                    .map { resource -> resource.resourceKey }
                    .toSet()
                val textScopeIndices = buildSet {
                    uploadSteps.forEach { (index, upload) ->
                        if (upload.r8ResourcePlan?.let { candidate ->
                                requiredAtlasPlans.any { it === candidate }
                            } == true ||
                            upload.materialResourcePlan?.resourceKey in requiredMaterialKeys
                        ) {
                            add(index)
                        }
                    }
                    textRenders.forEach { (index, _) -> add(index) }
                }
                val textScopeKeys = exactScopeKeys.filter { scope ->
                    scope.sourceStepIndex in textScopeIndices
                }
                val textTextureUploads = uploadSteps.mapNotNull { (index, upload) ->
                    val scope = textScopeKeys.singleOrNull { key ->
                        key.sourceStepIndex == index
                    } ?: return@mapNotNull null
                    upload.r8ResourcePlan?.let { resource ->
                        GPUPreparedTextTextureUploadPlan.Atlas(scope, resource)
                    } ?: upload.materialResourcePlan?.let { resource ->
                        GPUPreparedTextTextureUploadPlan.Material(scope, resource)
                    }
                }
                GPUPreparedTextRenderRunPlan(
                    sourceScopeIndices =
                        textScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex),
                    packets = textPackets,
                    bindings = textBindings,
                    exactScopeKeys = textScopeKeys,
                    textureUploads = textTextureUploads,
                )
            }
        val colorGlyphRenderEvidence = framePlan.steps.mapIndexedNotNull { index, step ->
            (step as? GPUFrameStep.RenderPassStep)
                ?.takeIf { render ->
                    render.drawPackets.all { packet ->
                        packet.semanticPayload is GPUDrawSemanticPayload.ColorGlyph
                    }
                }
                ?.let { index to it }
        }
        val colorGlyphPlan = colorGlyphRenderEvidence
            .takeIf { it.isNotEmpty() }
            ?.let { colorRenders ->
                val colorPackets = colorRenders.flatMap { (_, render) ->
                    render.drawPackets.map { packet ->
                        packet.semanticPayload as GPUDrawSemanticPayload.ColorGlyph
                    }
                }
                val colorBindings = colorRenders.flatMap { (_, render) ->
                    render.drawPackets.map { packet ->
                        render.preparedTextBindingsByPacketId.getValue(packet.packetId)
                    }
                }
                val colorRenderRuns = colorRenders.map { (index, render) ->
                    GPUPreparedColorGlyphScopeRunPlan(
                        exactScopeKey = exactScopeKeys.single { key ->
                            key.sourceStepIndex == index
                        },
                        packets = render.drawPackets.map { packet ->
                            packet.semanticPayload as GPUDrawSemanticPayload.ColorGlyph
                        },
                        bindings = render.drawPackets.map { packet ->
                            render.preparedTextBindingsByPacketId.getValue(packet.packetId)
                        },
                    )
                }
                val requiredAtlasPlans = colorBindings
                    .map(GPUPreparedTextRenderBinding::atlasResourcePlan)
                val colorScopeIndices = buildSet {
                    uploadSteps.forEach { (index, upload) ->
                        if (upload.r8ResourcePlan?.let { candidate ->
                                requiredAtlasPlans.any { it === candidate }
                            } == true
                        ) {
                            add(index)
                        }
                    }
                    colorRenders.forEach { (index, _) -> add(index) }
                }
                val colorScopeKeys = exactScopeKeys.filter { scope ->
                    scope.sourceStepIndex in colorScopeIndices
                }
                val atlasUploads = uploadSteps.mapNotNull { (index, upload) ->
                    val resource = upload.r8ResourcePlan ?: return@mapNotNull null
                    if (requiredAtlasPlans.none { it === resource }) return@mapNotNull null
                    GPUPreparedTextTextureUploadPlan.Atlas(
                        exactScopeKey = colorScopeKeys.single { key ->
                            key.sourceStepIndex == index
                        },
                        resourcePlan = resource,
                    )
                }
                GPUPreparedColorGlyphRenderRunPlan(
                    sourceScopeIndices =
                        colorScopeKeys.map(GPUPreparedNativeScopeKey::sourceStepIndex),
                    renderRuns = colorRenderRuns,
                    exactScopeKeys = colorScopeKeys,
                    atlasUploads = atlasUploads,
                    canonicalPlanTable = colorGlyphCanonicalPlanTable,
                )
            }
        val destinationAuthorities = authenticateColorGlyphDestinationReads(
            framePlan = framePlan,
            renders = renders,
            context = null,
            capabilities = null,
        ).let { (authorities, refusal) ->
            if (refusal != null) return refusal
            authorities
        }
        val colorGlyphDestinationReads = destinationAuthorities.map { authority ->
            val programSeal = when (
                val shader = buildColorGlyphDestinationReadShader(
                    clipVariant = authority.clip.variant,
                )
            ) {
                is GPUColorGlyphCompositeShaderResult.Ready ->
                    shader.plan.destinationProgramSeal
                is GPUColorGlyphCompositeShaderResult.Rejected -> null
            } ?: return refused(
                "invalid.prepared-surface.destination-program",
                "Prepared ColorGlyph destination-read WGSL does not match the sealed ABI.",
            )
            val snapshotEvidence = evidenceByResource[authority.copyStep.snapshot]
                ?: return refused(
                    "invalid.prepared-surface.destination-copy-resource",
                    "Prepared ColorGlyph destination snapshot has no exact resource evidence.",
                )
            GPUPreparedColorGlyphDestinationReadPlan(
                copySourceStepIndex = authority.copySourceStepIndex,
                renderSourceStepIndex = authority.renderSourceStepIndex,
                copyStep = authority.copyStep,
                renderStep = authority.renderStep,
                packet = authority.packet,
                semantic = authority.semantic,
                binding = authority.binding,
                snapshotPreparation = authority.snapshotPreparation,
                snapshotEvidence = snapshotEvidence,
                blendPlan = authority.blendPlan,
                clip = authority.clip,
                coverageMaskEvidence =
                    (
                        authority.clip as?
                            GPUPreparedColorGlyphDestinationClipAuthority.CoverageMask
                        )
                        ?.resource
                        ?.let { resource -> evidenceByResource[resource] }
                        ?: if (
                            authority.clip is
                                GPUPreparedColorGlyphDestinationClipAuthority.CoverageMask
                        ) {
                            return refused(
                                "invalid.prepared-surface.coverage-mask-resource",
                                "ColorGlyph CoverageMask has no exact prepared resource evidence.",
                            )
                        } else {
                            null
                        },
                programSeal = programSeal,
                exactCopyScopeKey = exactScopeKeys.single { scope ->
                    scope.sourceStepIndex == authority.copySourceStepIndex
                },
                exactRenderScopeKey = exactScopeKeys.single { scope ->
                    scope.sourceStepIndex == authority.renderSourceStepIndex
                },
            )
        }
        val sceneTarget = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .single { request -> request.role == GPUFrameResourceRole.SceneTarget }
            .resource as GPUFrameTargetRef
        val readbackSeal = framePlan.steps.withIndex()
            .mapNotNull { indexed ->
                (indexed.value as? GPUFrameStep.ReadbackCopyStep)?.let {
                    indexed.index to it
                }
            }
            .singleOrNull()
            ?.let { (readbackIndex, readbackStep) ->
                val readbackOutput = resources.outputOwnedReadbacks.single()
                GPUPreparedSurfaceReadbackSeal(
                    sourceStepIndex = readbackIndex,
                    source = readbackStep.source,
                    staging = readbackStep.staging,
                    request = readbackStep.request,
                    layout = readbackOutput.layout,
                    stagingLease = readbackOutput.stagingLease,
                    exactScopeKey = exactScopeKeys.single { scope ->
                        scope.sourceStepIndex == readbackIndex
                    },
                )
            }
        val surfaceChain = framePlan.steps.withIndex()
            .filter { indexed -> indexed.value is GPUFrameStep.AcquireSurfaceOutput }
            .singleOrNull()
            ?.let { indexedAcquire ->
                val acquire = indexedAcquire.value as GPUFrameStep.AcquireSurfaceOutput
                val blitIndex = indexedAcquire.index + 1
                val presentIndex = indexedAcquire.index + 2
                val blit = framePlan.steps[blitIndex] as GPUFrameStep.SurfaceBlitRenderPassStep
                GPUPreparedSurfaceChainSeal(
                    acquireStepIndex = indexedAcquire.index,
                    blitStepIndex = blitIndex,
                    presentStepIndex = presentIndex,
                    descriptor = acquire.descriptor,
                    scene = blit.scene,
                    output = blit.output,
                    sourceTaskIds = acquire.sourceTaskIds,
                    exactBlitScopeKey = exactScopeKeys.single { scope ->
                        scope.sourceStepIndex == blitIndex
                    },
                )
            }
        val coverageMaskRuns = framePlan.steps.mapIndexedNotNull { index, step ->
            val render = step as? GPUFrameStep.RenderPassStep
                ?: return@mapIndexedNotNull null
            if (render.drawPackets.isEmpty() ||
                render.drawPackets.any { packet ->
                    packet.role != GPUDrawPacketRole.ClipProducer
                }
            ) {
                return@mapIndexedNotNull null
            }
            val slabSeal = render.drawPackets.mapNotNull { packet ->
                packet.coverageMaskProducerUniformSlabSeal
            }.distinct().singleOrNull()
                ?: return refused(
                    "invalid.prepared-surface.coverage-mask-plan",
                    "Prepared-surface ClipProducer lost its passive CoverageMask slab seal.",
                )
            GPUPreparedSurfaceCoverageMaskRunPlan(
                sourceScopeIndex = index,
                renderStep = render,
                slabSeal = slabSeal,
                preparation = preparationByResource[render.target]
                    ?: return refused(
                        "invalid.prepared-surface.coverage-mask-preparation",
                        "Prepared-surface CoverageMask preparation is missing.",
                    ),
                exactScopeKey = exactScopeKeys.single { scope ->
                    scope.sourceStepIndex == index
                },
            )
        }
        val layerTargets = framePlan.steps.mapIndexedNotNull { index, step ->
            val prepare = step as? GPUFrameStep.LayerTargetPrepareStep
                ?: return@mapIndexedNotNull null
            val childrenRenderIndex = framePlan.steps.indexOfFirst { candidate ->
                candidate is GPUFrameStep.RenderPassStep &&
                    candidate.target.value == prepare.targetLabel
            }
            if (childrenRenderIndex < 0) {
                return refused(
                    "invalid.prepared-surface.layer-target",
                    "Every prepared layer target must retain one exact children render pass.",
                    mapOf("targetLabel" to prepare.targetLabel),
                )
            }
            val compositeIndex = framePlan.steps.indexOfFirst { candidate ->
                candidate is GPUFrameStep.LayerCompositeRenderStep &&
                    candidate.sourceLabel == prepare.targetLabel
            }
            if (compositeIndex < 0) {
                return refused(
                    "invalid.prepared-surface.layer-target",
                    "Every prepared layer target must retain one exact composite step.",
                    mapOf("targetLabel" to prepare.targetLabel),
                )
            }
            val childrenRender = framePlan.steps[childrenRenderIndex] as GPUFrameStep.RenderPassStep
            val bounds = preparedSurfaceLayerChildrenBounds(childrenRender.drawPackets)
                ?: return refused(
                    "unsupported.prepared-surface.layer-children-geometry",
                    "Prepared layer children must resolve to one exact non-empty device bounds union.",
                    mapOf("targetLabel" to prepare.targetLabel),
                )
            val allocationBounds = preparedSurfaceSceneTargetAllocationBounds(framePlan)
                ?: return refused(
                    "invalid.prepared-surface.scene-target-descriptor",
                    "Prepared layer targets require one exact scene texture allocation extent.",
                )
            val allocationByteEstimate = preparedSurfaceTextureByteEstimate(allocationBounds)
                ?: return refused(
                    "invalid.prepared-surface.layer-target-budget",
                    "Prepared layer target backing allocation byte accounting overflowed.",
                    mapOf("targetLabel" to prepare.targetLabel),
                )
            if (prepare.byteEstimate != allocationByteEstimate) {
                return refused(
                    "invalid.prepared-surface.layer-target-byte-estimate",
                    "Prepared layer target byte estimate must match its scene-sized backing allocation.",
                    mapOf(
                        "targetLabel" to prepare.targetLabel,
                        "declaredBytes" to prepare.byteEstimate.toString(),
                        "allocationBytes" to allocationByteEstimate.toString(),
                    ),
                )
            }
            GPUPreparedSurfaceLayerTargetPlan(
                targetLabel = prepare.targetLabel,
                prepareStepIndex = index,
                childrenRenderStepIndex = childrenRenderIndex,
                compositeStepIndex = compositeIndex,
                bounds = bounds,
                allocationBounds = allocationBounds,
                allocationByteEstimate = allocationByteEstimate,
            )
        }
        if (layerTargets.map { it.targetLabel }.distinct().size != layerTargets.size) {
            return refused(
                "invalid.prepared-surface.layer-target",
                "Prepared layer targets must remain distinct.",
            )
        }
        validatePreparedSurfaceSceneSizedLayerTargetBudget(
            frameBudget = framePlan.memoryBudget,
            layerTargets = layerTargets,
        )?.let { return it }
        val compositeRuns = framePlan.steps.mapIndexedNotNull { index, step ->
            val composite = step as? GPUFrameStep.LayerCompositeRenderStep
                ?: return@mapIndexedNotNull null
            if (composite.blendPlan.mode !in preparedLayerCompositeBlendModes) {
                return refused(
                    "unsupported.prepared-surface.layer-composite-blend",
                    "Prepared layer composites admit only SRC_OVER or SRC until the remaining " +
                        "atlas source blends are materialized.",
                    mapOf("blendMode" to composite.blendPlan.mode.name),
                )
            }
            val layer = layerTargets.singleOrNull { it.targetLabel == composite.sourceLabel }
                ?: return refused(
                    "invalid.prepared-surface.layer-composite",
                    "Every prepared layer composite must name one exact declared layer target.",
                    mapOf("sourceLabel" to composite.sourceLabel),
                )
            if (composite.parentTargetLabel in layerTargets.map { it.targetLabel }) {
                return refused(
                    "unsupported.prepared-surface.layer-nesting",
                    "Nested prepared layer composites are not materialized yet; every composite " +
                        "must target the scene directly.",
                    mapOf(
                        "sourceLabel" to composite.sourceLabel,
                        "parentTargetLabel" to composite.parentTargetLabel,
                    ),
                )
            }
            GPUPreparedSurfaceLayerCompositeRunPlan(
                sourceStepIndex = index,
                step = composite,
                layerTarget = layer,
                exactScopeKey = exactScopeKeys.single { scope ->
                    scope.sourceStepIndex == index
                },
            )
        }
        return try {
            GPUPreparedSurfaceNativePreflightResult.Accepted(
                GPUPreparedSurfaceNativePreflightPlan(
                    frameId = framePlan.frameId,
                    encoderPlanId = encoderPlan.planId,
                    contextIdentity = encoderPlan.contextIdentity,
                    sceneTarget = sceneTarget,
                    resources = resources,
                    readback = readbackSeal,
                    surfaceChain = surfaceChain,
                    orderedRuns = orderedRuns,
                    imageFrames = imageFrames,
                    textPlan = textPlan,
                    colorGlyphPlan = colorGlyphPlan,
                    colorGlyphDestinationReads = colorGlyphDestinationReads,
                    coverageMaskRuns = coverageMaskRuns,
                    layerTargets = layerTargets,
                    compositeRuns = compositeRuns,
                    exactScopeKeys = exactScopeKeys,
                    generationSeal = generationSeal,
                ),
            )
        } catch (_: IllegalArgumentException) {
            refused(
                "invalid.prepared-surface.run-plan",
                "The sealed mixed run plan is internally inconsistent.",
            )
        }
    }

    private fun validatePreparedTextGenerationSeal(
        framePlan: GPUFramePlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val bindings = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { render -> render.preparedTextBindingsByPacketId.values }
        if (bindings.isEmpty()) return null
        val resourceEvidenceByRef = resources.ordinaryResources
            .associateBy(GPUPreparedResourceEvidence::logicalResource)
        val hasInvalidGeneration = bindings
            .flatMap(GPUPreparedTextRenderBinding::preparedTextResourceRefs)
            .distinct()
            .any { resource ->
                val sealedGeneration = generationSeal.resourceGenerations[resource]
                val resourceGeneration =
                    resourceEvidenceByRef[resource]?.resourceGeneration
                sealedGeneration == null ||
                    resourceGeneration == null ||
                    sealedGeneration != resourceGeneration
            } ||
            bindings.any { binding ->
                val artifactGeneration = binding.atlasResourcePlan.artifactGeneration
                generationSeal.resourceGenerations[
                    binding.atlasResourcePlan.stagingRef
                ] != artifactGeneration ||
                    generationSeal.resourceGenerations[
                        binding.atlasResourcePlan.frameTextureRef
                    ] != artifactGeneration
            }
        if (hasInvalidGeneration) {
            return refused(
                GPUPreparedTextPreflightRefusalCodes.STALE_ATLAS_GENERATION,
                "Every prepared-text resource requires exact sealed generation evidence.",
            )
        }
        return null
    }

    private fun validateGeneration(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        if (generationSeal.deviceGeneration != framePlan.capabilitySeal.deviceGeneration ||
            generationSeal.deviceGeneration != encoderPlan.deviceGeneration ||
            generationSeal.targetGeneration != encoderPlan.targetGeneration ||
            generationSeal.capabilitySealHash != framePlan.capabilitySeal.sealHash
        ) {
            return refused(
                "stale.prepared-surface.generation",
                "Frame, encoder, target, device, and capability generations must agree.",
            )
        }
        val preparedRefs = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .map { request -> request.resource }
            .toSet()
        val declaredLayerTargets = framePlan.steps
            .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
            .map { step -> GPUFrameTargetRef(step.targetLabel) }
            .toSet()
        if (generationSeal.resourceGenerations.keys != preparedRefs + declaredLayerTargets ||
            generationSeal.resourceGenerations.values.any { it < 0L } ||
            resources.ordinaryResources.any { evidence ->
                evidence.deviceGeneration != generationSeal.deviceGeneration ||
                    generationSeal.resourceGenerations[evidence.logicalResource] !=
                    evidence.resourceGeneration
            } ||
            resources.outputOwnedReadbacks.any { readback ->
                generationSeal.resourceGenerations[readback.stagingResource] !=
                    readback.resourceGeneration
            }
        ) {
            return refused(
                "stale.prepared-surface.generation",
                "Every prepared resource must retain the exact current generation.",
            )
        }
        return null
    }

    private fun validateExactEncoderPlan(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val sceneTarget = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?.resource as? GPUFrameTargetRef
        if (encoderPlan.planId != "frame.${framePlan.frameId.value}" ||
            sceneTarget == null ||
            encoderPlan.contextIdentity != sceneTarget.value ||
            encoderPlan.targetGeneration != generationSeal.targetGeneration ||
            generationSeal.resourceGenerations[sceneTarget] != encoderPlan.targetGeneration
        ) {
            return refused(
                "invalid.prepared-surface.encoder-plan",
                "Encoder plan, frame, scene target, and target generation identities must be exact.",
            )
        }
        val expected = framePlan.steps.mapIndexedNotNull { index, step ->
            step.preparedSurfaceOperationKindOrNull()?.let { operation -> index to operation }
        }
        if (encoderPlan.scopes.map { scope -> scope.sourceStepIndex to scope.operationKind } != expected ||
            encoderPlan.scopes.any { scope ->
                !GPUPreparedSurfaceEncoderScopeAuthority.matches(
                    framePlan,
                    framePlan.steps[scope.sourceStepIndex],
                    scope,
                    generationSeal,
                )
            }
        ) {
            return refused(
                "invalid.prepared-surface.encoder-plan",
                "The full encoder plan must retain every encodable frame step exactly once and in order.",
            )
        }
        return null
    }

    private fun validateResources(
        framePlan: GPUFramePlan,
        encoderPlan: GPUCommandEncoderPlan,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val requests = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val ordinary = requests.filter { it.role != GPUFrameResourceRole.ReadbackStaging }
        val readbacks = requests.filter { it.role == GPUFrameResourceRole.ReadbackStaging }
        val declaredResources = requests.map(GPUResourcePreparationRequest::resource)
        val declaredLayerTargets = framePlan.steps
            .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
            .map { step -> GPUFrameTargetRef(step.targetLabel) }
            .toSet()
        val resourceMismatch = when {
            declaredResources.distinct().size != declaredResources.size ->
                "Frame preparation declarations contain duplicate logical resources."
            ordinary.map(GPUResourcePreparationRequest::resource) !=
                resources.ordinaryResources.map(GPUPreparedResourceEvidence::logicalResource) ->
                "Ordinary prepared resources differ from declaration order."
            readbacks.map(GPUResourcePreparationRequest::resource) !=
                resources.outputOwnedReadbacks.map(GPUPreparedReadbackOutput::stagingResource) ->
                "Output-owned readback resources differ from declaration order."
            generationSeal.resourceGenerations.keys !=
                declaredResources.toSet() + declaredLayerTargets ->
                "The generation seal does not exactly cover resource declarations."
            !resources.ordinaryResources.zip(ordinary).all { (evidence, request) ->
                evidence.matchesExactPreparation(request, generationSeal)
            } ->
                "Ordinary prepared topology, roles, or generations differ from declarations."
            !resources.hasValidCommandEvidence(encoderPlan, generationSeal) ->
                "Command-resource identities, ownership, generation, or diagnostics are invalid."
            else -> null
        }
        if (resourceMismatch != null) {
            return refused(
                "invalid.prepared-surface.resources",
                resourceMismatch,
            )
        }
        val imagePreparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.UploadResourceStep>()
            .mapNotNull(GPUFrameStep.UploadResourceStep::imageResourcePlan)
            .flatMap(GPUImageFrameResourcePlan::preparationRequests)
        if (imagePreparations.any { imageRequest ->
                requests.singleOrNull { declared ->
                    declared.samePreparationAs(imageRequest)
                } == null
            }
        ) {
            return refused(
                "invalid.prepared-surface.resources",
                "Prepared-image resource plans must retain the exact frame preparation requests.",
            )
        }
        validateReadbackResources(framePlan, readbacks, resources, generationSeal)
            ?.let { return it }
        return null
    }

    private fun validateReadbackResources(
        framePlan: GPUFramePlan,
        readbackPreparations: List<GPUResourcePreparationRequest>,
        resources: GPUPreparedResourceSet,
        generationSeal: GPUPreparedGenerationSeal,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val steps = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val outputs = resources.outputOwnedReadbacks
        if (steps.isEmpty() && readbackPreparations.isEmpty() && outputs.isEmpty()) {
            return null
        }
        val step = steps.singleOrNull()
        val preparation = readbackPreparations.singleOrNull()
        val output = outputs.singleOrNull()
        if (step == null || preparation == null || output == null ||
            output.stagingResource != step.staging ||
            output.request != step.request ||
            output.concreteResource.ref != output.stagingLease.resourceRef ||
            output.resourceGeneration !=
            generationSeal.resourceGenerations[output.stagingResource] ||
            output.stagingLease.deviceGeneration != generationSeal.deviceGeneration ||
            output.stagingLease.logicalMinimumBytes != output.layout.totalBufferBytes ||
            output.stagingLease.backingBufferBytes <
            output.stagingLease.logicalMinimumBytes ||
            output.stagingLease.usages != preparation.usages
        ) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback request, staging ownership, generation, lease, and concrete buffer must remain exact.",
            )
        }
        val descriptor = preparation.descriptor as? GPUFrameBufferDescriptor
        val layout = output.layout
        val width = step.request.sourceBounds.width
        val height = step.request.sourceBounds.height
        val unpadded = try {
            Math.multiplyExact(width.toLong(), 4L)
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback row size overflowed.",
            )
        }
        val alignment = layout.copyBytesPerRowAlignment.toLong()
        val padded = if (alignment > 0L) {
            ((unpadded + alignment - 1L) / alignment) * alignment
        } else {
            -1L
        }
        val total = try {
            Math.addExact(
                step.request.bufferOffsetBytes,
                Math.addExact(
                    Math.multiplyExact(padded, (height - 1).toLong()),
                    unpadded,
                ),
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback staging size overflowed.",
            )
        }
        if (descriptor == null ||
            descriptor.byteSize != total ||
            output.stagingLease.logicalMinimumBytes != descriptor.byteSize ||
            output.stagingLease.backingBufferBytes < descriptor.byteSize ||
            layout.width != width ||
            layout.height != height ||
            layout.bytesPerPixel != 4 ||
            alignment <= 0L ||
            alignment and (alignment - 1L) != 0L ||
            layout.unpaddedBytesPerRow != unpadded ||
            layout.paddedBytesPerRow != padded ||
            layout.rowsPerImage != height ||
            layout.bufferOffset != step.request.bufferOffsetBytes ||
            layout.totalBufferBytes != total
        ) {
            return refused(
                "invalid.prepared-surface.readback",
                "Readback bounds, RGBA8 row padding, offset, and total staging bytes must be exact.",
            )
        }
        return null
    }

    private fun validateReadbackAndSurface(
        framePlan: GPUFramePlan,
        context: GPUFramePreflightContext?,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val preparations = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        val scene = preparations.singleOrNull { request ->
            request.role == GPUFrameResourceRole.SceneTarget
        }
        val sceneDescriptor = scene?.descriptor as? GPUFrameTextureDescriptor
        val readback = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        val readbackPreparations = preparations.filter { request ->
            request.role == GPUFrameResourceRole.ReadbackStaging
        }
        if (scene == null || sceneDescriptor == null) {
            return refused(
                "invalid.prepared-surface.readback",
                "The prepared surface requires one exact scene target.",
            )
        }
        if (readback.isEmpty() && readbackPreparations.isEmpty()) {
            // Readback is optional; an absent request must leave no staging declaration.
        } else {
            val exactReadback = readback.singleOrNull()
            val readbackPreparation = readbackPreparations.singleOrNull()
            if (exactReadback == null || readbackPreparation == null ||
                exactReadback.source != scene.resource ||
                exactReadback.staging != readbackPreparation.resource ||
                exactReadback.request.sourceBounds != sceneDescriptor.logicalBounds ||
                exactReadback.request.pixelFormat != GPUReadbackPixelFormat.Rgba8Unorm ||
                exactReadback.request.outputColorInterpretation !=
                GPUColorInterpretation.EncodedPremulSrgb ||
                exactReadback.request.bufferOffsetBytes != 0L ||
                readbackPreparation.descriptor !is GPUFrameBufferDescriptor ||
                readbackPreparation.usages != setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                )
            ) {
                return refused(
                    "invalid.prepared-surface.readback",
                    "The optional prepared-surface readback must be one exact full-scene RGBA8 sRGB request.",
                )
            }
        }

        val acquires = framePlan.steps.filterIsInstance<GPUFrameStep.AcquireSurfaceOutput>()
        val blits = framePlan.steps.filterIsInstance<GPUFrameStep.SurfaceBlitRenderPassStep>()
        val presents = framePlan.steps.filterIsInstance<GPUFrameStep.PostSubmitPresentAction>()
        if (acquires.isEmpty() && blits.isEmpty() && presents.isEmpty()) return null
        val acquire = acquires.singleOrNull()
        val blit = blits.singleOrNull()
        val present = presents.singleOrNull()
        val suffix = framePlan.steps.takeLast(3)
        if (acquire == null || blit == null || present == null ||
            suffix.getOrNull(0) !== acquire ||
            suffix.getOrNull(1) !== blit ||
            suffix.getOrNull(2) !== present ||
            acquire.descriptor.output != blit.output ||
            acquire.descriptor.output != present.output ||
            blit.scene != scene.resource ||
            acquire.descriptor.width != sceneDescriptor.logicalBounds.width ||
            acquire.descriptor.height != sceneDescriptor.logicalBounds.height ||
            acquire.descriptor.format != sceneDescriptor.format ||
            context?.surfaceGeneration?.let { generation ->
                acquire.descriptor.targetGeneration == generation
            } == false ||
            acquire.sourceTaskIds != blit.sourceTaskIds ||
            acquire.sourceTaskIds != present.sourceTaskIds
        ) {
            return refused(
                "invalid.prepared-surface.surface-chain",
                "Surface acquire, scene blit, and present must form one exact final chain.",
            )
        }
        return null
    }

    private fun validateColorAuthority(
        framePlan: GPUFramePlan,
        renders: List<GPUFrameStep.RenderPassStep>,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val sceneRequest = framePlan.steps
            .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
            .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
            .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
            ?: return refused(
                "unsupported.prepared-surface.target-color",
                "Mixed prepared surfaces require one exact scene target.",
            )
        val descriptor = sceneRequest.descriptor as? GPUFrameTextureDescriptor
        val declaredLayerTargetLabels = framePlan.steps
            .filterIsInstance<GPUFrameStep.LayerTargetPrepareStep>()
            .map(GPUFrameStep.LayerTargetPrepareStep::targetLabel)
            .toSet()
        if (descriptor?.format != GPUColorFormat.RGBA8UnormSrgb ||
            descriptor.sampleCount != 1 ||
            renders.any { render ->
                render.target != sceneRequest.resource &&
                    render.target.value !in declaredLayerTargetLabels
            }
        ) {
            return refused(
                "unsupported.prepared-surface.target-color",
                "The mixed scene target must be single-sample RGBA8UnormSrgb with LinearPremul shader authority.",
            )
        }
        val readbacks = framePlan.steps.filterIsInstance<GPUFrameStep.ReadbackCopyStep>()
        if (readbacks.any { readback ->
                readback.source != sceneRequest.resource ||
                    readback.request.outputColorInterpretation !=
                    GPUColorInterpretation.EncodedPremulSrgb
            }
        ) {
            return refused(
                "invalid.prepared-surface.readback-color",
                "Mixed readback must encode the exact sRGB scene target as EncodedPremulSrgb.",
            )
        }
        return null
    }

    private fun validateImageAuthority(
        framePlan: GPUFramePlan,
        imagePackets: List<Pair<GPUDrawPacket, GPUDrawSemanticPayload.SampledImage>>,
        uploads: List<Triple<Int, GPUFrameStep.UploadResourceStep, GPUImageFrameResourcePlan>>,
        shaderContract: GPUPreparedImageShaderContract,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val artifactEvidenceByIdentity =
            IdentityHashMap<GPUPreparedImageUploadArtifact, GPUPreparedSurfaceArtifactByteEvidence>()
        val renderBindingList = framePlan.steps
            .filterIsInstance<GPUFrameStep.RenderPassStep>()
            .flatMap { render -> render.preparedImageBindingsByPacketId.values }
        val plannedBindingList = uploads.flatMap { (_, _, plan) -> plan.bindingRequests }
        if (renderBindingList.map { binding -> binding.packetId }.distinct().size !=
            renderBindingList.size ||
            plannedBindingList.map { binding -> binding.packetId }.distinct().size !=
            plannedBindingList.size
        ) {
            return refused(
                "invalid.prepared-surface.image-binding-duplicates",
                "Prepared-image packet bindings must be globally unique before indexed lookup.",
            )
        }
        val packetById = imagePackets.associate { (packet, semantic) ->
            packet.packetId.value to semantic
        }
        val renderBindings = renderBindingList.associateBy { binding -> binding.packetId }
        if (renderBindings.keys != packetById.keys ||
            renderBindings.values.any { binding ->
                binding.bindingLayoutHash != shaderContract.bindingLayoutHash
            }
        ) {
            return refused(
                "unsupported.prepared_image.native-binding",
                "Image render bindings must exactly cover packet identities with the reflected ABI112 layout.",
            )
        }
        val plannedBindings = plannedBindingList.associateBy { binding -> binding.packetId }
        if (plannedBindings != renderBindings) {
            return refused(
                "unsupported.prepared_image.native-binding",
                "Upload plans and render runs must retain the same exact image bindings.",
            )
        }
        uploads.forEach { (_, step, plan) ->
            if (step.staging != plan.stagingRef ||
                step.destination != plan.frameTextureRef ||
                step.layout != plan.uploadTaskLayout ||
                plan.bindingRequests.isEmpty() ||
                plan.bindingRequests.any { binding ->
                    binding.artifactKey != plan.artifactKey ||
                        binding.uniformAllocation.packetId != binding.packetId ||
                        binding.uniformAllocation.size !=
                        GPU_PREPARED_IMAGE_UNIFORM_ALLOCATION_SIZE_BYTES ||
                        packetById[binding.packetId]?.artifact?.key != plan.artifactKey
                }
            ) {
                return refused(
                    "unsupported.prepared_image.plan_identity",
                    "Prepared-image upload and binding identities must be exact.",
                )
            }
            val expectedLogicalRowBytes = try {
                Math.multiplyExact(plan.artifactWidth.toLong(), 4L)
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.prepared_image.upload_layout",
                    "Prepared-image row layout overflowed.",
                )
            }
            val expectedUploadBytes = try {
                Math.multiplyExact(plan.uploadLayout.bytesPerRow, plan.artifactHeight.toLong())
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.prepared_image.upload_layout",
                    "Prepared-image upload byte size overflowed.",
                )
            }
            val logicalArtifactBytes = plan.uploadLayout.logicalBytesForHash()
            val uploadBytes = plan.uploadLayout.bytesForUpload()
            val planArtifacts = plan.bindingRequests.mapNotNull { binding ->
                packetById[binding.packetId]?.artifact
            }
            if (planArtifacts.size != plan.bindingRequests.size ||
                planArtifacts.any { artifact ->
                    val byteEvidence = artifactEvidenceByIdentity[artifact] ?: run {
                        val tightBytes = artifact.tightRgba8BytesForUpload()
                        GPUPreparedSurfaceArtifactByteEvidence(
                            tightRgba8Bytes = tightBytes,
                            contentHash = preparedSurfaceSha256(tightBytes),
                        ).also { evidence ->
                            artifactEvidenceByIdentity[artifact] = evidence
                        }
                    }
                    artifact.key != plan.artifactKey ||
                        artifact.width != plan.artifactWidth ||
                        artifact.height != plan.artifactHeight ||
                        artifact.pixelLayout.normalizedRgba8RowBytes !=
                        expectedLogicalRowBytes ||
                        artifact.pixelLayout.rowCount != plan.artifactHeight ||
                        artifact.contentHash != plan.artifactContentHash ||
                        byteEvidence.contentHash != artifact.contentHash ||
                        !byteEvidence.tightRgba8Bytes.contentEquals(logicalArtifactBytes) ||
                        artifact.colorInterpretation !=
                        GPUColorInterpretation.EncodedPremulSrgb.value ||
                        if (artifact.alphaOnly) {
                            artifact.colorUploadEncoding != null ||
                                artifact.colorUploadInterpretation !=
                                GPUColorInterpretation.LinearPremul.value
                        } else {
                            artifact.colorUploadEncoding?.name !=
                                "StraightEncodedSrgb" ||
                                artifact.colorUploadInterpretation !=
                                GPUColorInterpretation.StraightEncodedSrgb.value
                        }
                } ||
                planArtifacts.map { artifact -> artifact.alphaOnly }.distinct().size != 1
            ) {
                return refused(
                    "invalid.prepared-surface.image-artifact",
                    "Prepared-image key, dimensions, immutable RGBA8 bytes, hash, alpha, and color authority must remain exact.",
                )
            }
            if (plan.uploadLayout.logicalBytesPerRow != expectedLogicalRowBytes ||
                plan.uploadLayout.width != plan.artifactWidth ||
                plan.uploadLayout.height != plan.artifactHeight ||
                plan.uploadLayout.rowsPerImage != plan.artifactHeight ||
                plan.uploadTaskLayout.sourceOffsetBytes != 0L ||
                plan.uploadTaskLayout.bytesPerRow != plan.uploadLayout.bytesPerRow ||
                plan.uploadTaskLayout.rowsPerImage != plan.uploadLayout.rowsPerImage ||
                plan.uploadTaskLayout.byteSize != expectedUploadBytes ||
                uploadBytes.size.toLong() != expectedUploadBytes ||
                preparedSurfaceSha256(logicalArtifactBytes) !=
                plan.artifactContentHash
            ) {
                return refused(
                    "unsupported.prepared_image.upload_layout",
                    "Prepared-image upload dimensions, strides, bytes, and provenance must be exact.",
                )
            }
            val descriptor = plan.preparationRequests.singleOrNull { request ->
                request.resource == plan.frameTextureRef
            }?.descriptor as? GPUFrameTextureDescriptor
            val sourceFormat = if (
                packetById.getValue(plan.bindingRequests.first().packetId).artifact.alphaOnly
            ) {
                GPUColorFormat.RGBA8Unorm
            } else {
                GPUColorFormat.RGBA8UnormSrgb
            }
            val sourceTextureFormatLabel = when (sourceFormat) {
                GPUColorFormat.RGBA8Unorm -> "RGBA8Unorm"
                GPUColorFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
                else -> error("Prepared surface selected an unsupported image source format")
            }
            if (descriptor?.format != sourceFormat ||
                descriptor.sampleCount != 1 ||
                descriptor.logicalBounds.width != plan.artifactWidth ||
                descriptor.logicalBounds.height != plan.artifactHeight ||
                plan.textureDescriptor.sampleCount != 1 ||
                plan.textureDescriptor.usageLabels != setOf("copy_dst", "texture_binding")
            ) {
                return refused(
                    "unsupported.prepared_image.texture_usage",
                    "Prepared-image source texture format, extent, sample count, and usages must be exact.",
                )
            }
            if (plan.textureDescriptor.width != plan.artifactWidth ||
                plan.textureDescriptor.height != plan.artifactHeight ||
                plan.textureDescriptor.format != sourceTextureFormatLabel ||
                plan.bindingRequests.any { binding ->
                    val semantic = packetById[binding.packetId] ?: return@any true
                    val expectedFilter = when (semantic.sampling) {
                        GPUPreparedImageSampling.Nearest -> "nearest"
                        GPUPreparedImageSampling.Linear -> "linear"
                    }
                    binding.texture != plan.textureDescriptor ||
                        binding.view.textureDescriptorHash !=
                        plan.textureDescriptor.preparedImageDescriptorHash() ||
                        binding.view.viewDimension != "2d" ||
                        binding.view.mipRange != 0..0 ||
                        binding.view.arrayLayerRange != 0..0 ||
                        binding.sampler.addressModeU != "clamp-to-edge" ||
                        binding.sampler.addressModeV != "clamp-to-edge" ||
                        binding.sampler.magFilter != expectedFilter ||
                        binding.sampler.minFilter != expectedFilter ||
                        binding.sampler.mipmapFilter != "none" ||
                        binding.sampler.lodMinClamp != "0" ||
                        binding.sampler.lodMaxClamp != "0" ||
                        binding.sampler.compareMode != "none" ||
                        binding.sampler.maxAnisotropy != 1 ||
                        binding.sampler.capabilityRequirements.isNotEmpty()
                }
            ) {
                return refused(
                    "invalid.prepared-surface.image-physical",
                    "Prepared-image texture, view, and sampler facts must match their exact canonical physical plan.",
                )
            }
        }
        if (imagePackets.any { (packet, semantic) ->
                !semantic.hasCanonicalHashIntegrity() ||
                    semantic.pipelineKey.bindingLayoutHash !=
                    GPUPreparedImageBindingLayoutTopology.IDENTITY ||
                    packet.payloadIdentityMismatch(semantic)
            }
        ) {
            return refused(
                "invalid.prepared-surface.image-semantic",
                "Prepared-image packet and semantic identities must remain canonical.",
            )
        }
        return null
    }

    private fun validateImageScissorAuthority(
        framePlan: GPUFramePlan,
        imagePackets: List<Pair<GPUDrawPacket, GPUDrawSemanticPayload.SampledImage>>,
    ): GPUPreparedSurfaceNativePreflightResult.Refused? {
        val targetBounds = (
            framePlan.steps
                .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
                .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
                .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
                ?.descriptor as? GPUFrameTextureDescriptor
            )?.logicalBounds
            ?: return refused(
                "invalid.prepared-surface.image-scissor-authority",
                "Prepared-image scissor validation requires one exact scene target.",
            )
        val mismatch = imagePackets.any { (packet, semantic) ->
            semantic.targetBounds != targetBounds ||
                packet.validatePreparedImageClipAuthority(
                    semantic.targetBounds,
                    semantic.scissorBounds,
                ) != GPUPreparedImageClipAuthorityValidation.Accepted
        }
        return if (mismatch) {
            refused(
                "invalid.prepared-surface.image-scissor-authority",
                "Prepared-image packet, semantic, and scene-target clip authorities must be exact.",
            )
        } else {
            null
        }
    }
}

private val preparedLayerCompositeBlendModes = setOf(GPUBlendMode.SRC_OVER, GPUBlendMode.SRC)

internal object GPUPreparedSurfaceEncoderScopeAuthority {
    fun matches(
        framePlan: GPUFramePlan,
        step: GPUFrameStep,
        scope: GPUCommandEncoderScopePlan,
        generationSeal: GPUPreparedGenerationSeal,
    ): Boolean {
        val expectedLabels = step.preparedResourceRefs().map { resource ->
            val generation = generationSeal.resourceGenerations[resource] ?: return false
            "${resource::class.simpleName}:${resource.value}@$generation"
        }
        val render = step as? GPUFrameStep.RenderPassStep
        if (scope.scopeLabel != "step.${scope.sourceStepIndex}" ||
            scope.sourceTaskIds != step.sourceTaskIds ||
            scope.operationKind != step.expectedEncoderOperationKind() ||
            scope.targetGeneration != generationSeal.targetGeneration ||
            scope.resourceGenerationLabels != expectedLabels ||
            scope.sourcePacketIds != render?.drawPackets?.map(GPUDrawPacket::packetId).orEmpty() ||
            scope.targetResource != render?.target ||
            scope.facadeOperationClasses != step.expectedFacadeOperations(scope)
        ) {
            return false
        }
        if (render == null) {
            return scope.passCommandStream == null &&
                scope.nativeOperandKeys == expectedNonRenderOperandKeys(step, expectedLabels)
        }
        val stream = scope.passCommandStream ?: return false
        val expectedStream = runCatching {
            expectedRenderStream(framePlan, scope.sourceStepIndex, render, stream)
        }.getOrNull() ?: return false
        if (stream.streamId != expectedStream.streamId ||
            stream.packetStreamId != expectedStream.packetStreamId ||
            stream.passId != expectedStream.passId ||
            stream.commands != expectedStream.commands ||
            stream.diagnostics != expectedStream.diagnostics ||
            stream.operandBridge != expectedStream.operandBridge ||
            stream.sourcePassIds != expectedStream.sourcePassIds
        ) {
            return false
        }
        return scope.nativeOperandKeys ==
            expectedRenderOperandKeys(render, scope, expectedLabels, stream)
    }

    private fun expectedRenderStream(
        framePlan: GPUFramePlan,
        sourceStepIndex: Int,
        render: GPUFrameStep.RenderPassStep,
        actual: GPUPassCommandStream,
    ): GPUPassCommandStream {
        val passPlan = GPUPassBatchPlan(
            streamId = "frame.${framePlan.frameId.value}.step.$sourceStepIndex",
            passId = "frame.${framePlan.frameId.value}.render.$sourceStepIndex",
            batches = render.batches.map { batch ->
                GPUPassBatch(
                    batchId = batch.batchId,
                    packets = batch.packets,
                    kind = batch.kind,
                    targetStateHash = batch.packets.first().targetStateHash,
                    queueGuard = GPUPassBatchQueueGuard(
                        requiredRetainedRefs = emptyList(),
                        retainedRefs = emptyList(),
                    ),
                )
            },
            cuts = emptyList(),
            diagnostics = emptyList(),
            inputPacketCount = render.drawPackets.size,
        )
        return GPUPassCommandStream.fromBatchPlan(
            streamId = "frame.${framePlan.frameId.value}.commands.$sourceStepIndex",
            batchPlan = passPlan,
            loadStoreLabel = render.loadStore.dumpLabelForPreparedSurface(),
            operandBridge = actual.operandBridge,
        )
    }

    private fun expectedNonRenderOperandKeys(
        step: GPUFrameStep,
        labels: List<String>,
    ): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            binding: String,
            ownership: GPUPreparedNativeOperandOwnership =
                GPUPreparedNativeOperandOwnership.Borrowed,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey(binding),
            ownership,
        )
        return when (step) {
            is GPUFrameStep.UploadResourceStep -> when (step.destinationKind) {
                org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Buffer ->
                    listOf(
                        key(
                            GPUPreparedNativeOperandRole.UploadSource,
                            GPUPreparedNativeOperandKind.Buffer,
                            labels[0],
                        ),
                        key(
                            GPUPreparedNativeOperandRole.UploadDestination,
                            GPUPreparedNativeOperandKind.Buffer,
                            labels[1],
                        ),
                    )
                org.graphiks.kanvas.gpu.renderer.recording.GPUUploadDestinationKind.Texture ->
                    listOf(
                        key(
                            GPUPreparedNativeOperandRole.UploadSource,
                            GPUPreparedNativeOperandKind.Buffer,
                            if (step.r8ResourcePlan != null ||
                                step.materialResourcePlan != null
                            ) {
                                "prepared-text-upload-data:${step.staging.value}"
                            } else {
                                "prepared-image-upload-data:${step.staging.value}"
                            },
                        ),
                        key(
                            GPUPreparedNativeOperandRole.UploadDestination,
                            GPUPreparedNativeOperandKind.Texture,
                            labels[1],
                            if (step.r8ResourcePlan != null) {
                                GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
                            } else {
                                GPUPreparedNativeOperandOwnership.Borrowed
                            },
                        ),
                    )
            }
            is GPUFrameStep.ReadbackCopyStep -> listOf(
                key(
                    GPUPreparedNativeOperandRole.ReadbackSource,
                    GPUPreparedNativeOperandKind.Texture,
                    labels[0],
                ),
                key(
                    GPUPreparedNativeOperandRole.ReadbackDestination,
                    GPUPreparedNativeOperandKind.Buffer,
                    labels[1],
                    GPUPreparedNativeOperandOwnership.OutputOwnedReadback,
                ),
            )
            is GPUFrameStep.CopyDestinationStep -> listOf(
                key(
                    GPUPreparedNativeOperandRole.CopySource,
                    GPUPreparedNativeOperandKind.Texture,
                    labels[0],
                ),
                key(
                    GPUPreparedNativeOperandRole.CopyDestination,
                    GPUPreparedNativeOperandKind.Texture,
                    labels[1],
                ),
            )
            is GPUFrameStep.SurfaceBlitRenderPassStep -> listOf(
                key(
                    GPUPreparedNativeOperandRole.SurfaceSource,
                    GPUPreparedNativeOperandKind.TextureView,
                    labels.single(),
                ),
                key(
                    GPUPreparedNativeOperandRole.SurfaceTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    "surface:${step.output.value}:target",
                ),
                key(
                    GPUPreparedNativeOperandRole.SurfacePipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    "surface:${step.output.value}:pipeline",
                ),
                key(
                    GPUPreparedNativeOperandRole.SurfaceBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "surface:${step.output.value}:bind-group",
                ),
            )
            is GPUFrameStep.LayerCompositeRenderStep -> listOf(
                key(
                    GPUPreparedNativeOperandRole.RenderColorTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    labels[0],
                ),
                key(
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    "layer-composite:${step.tokenLabel}:pipeline",
                ),
                key(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "layer-composite:${step.tokenLabel}:bind-group",
                ),
            )
            else -> emptyList()
        }
    }

    private fun expectedRenderOperandKeys(
        render: GPUFrameStep.RenderPassStep,
        scope: GPUCommandEncoderScopePlan,
        labels: List<String>,
        stream: GPUPassCommandStream,
    ): List<GPUPreparedNativeOperandKey> {
        fun key(
            role: GPUPreparedNativeOperandRole,
            kind: GPUPreparedNativeOperandKind,
            binding: String,
            ownership: GPUPreparedNativeOperandOwnership =
                GPUPreparedNativeOperandOwnership.Borrowed,
        ) = GPUPreparedNativeOperandKey(
            role,
            kind,
            gpuPreparedNativeBindingKey(binding),
            ownership,
        )
        val drawOwnership = if (render.drawPackets.all {
                it.semanticPayload is GPUDrawSemanticPayload.ColorGlyph
            }
        ) {
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion
        } else {
            GPUPreparedNativeOperandOwnership.Borrowed
        }
        fun bridgeKey(
            bridge: org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge,
        ): GPUPreparedNativeOperandKey? = when (bridge.operand.kind) {
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderPipeline ->
                key(
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                )
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup ->
                key(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                    drawOwnership,
                )
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer ->
                key(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                    drawOwnership,
                )
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer ->
                key(
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "${bridge.commandLabel}:${bridge.operand.label}",
                    drawOwnership,
                )
            else -> null
        }
        val target = listOf(
            key(
                GPUPreparedNativeOperandRole.RenderColorTarget,
                GPUPreparedNativeOperandKind.TextureView,
                labels.first(),
            ),
        )
        if (render.drawPackets.all {
                it.semanticPayload is GPUDrawSemanticPayload.TextA8
            }
        ) {
            if (render.drawPackets.size != 1) return emptyList()
            val packet = render.drawPackets.single()
            return target + buildList {
                add(
                key(
                    GPUPreparedNativeOperandRole.RenderPipeline,
                    GPUPreparedNativeOperandKind.RenderPipeline,
                    "prepared-text:${packet.packetId.value}:pipeline",
                ),
                )
                add(
                key(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "prepared-text:${packet.packetId.value}:draw-group",
                ),
                )
                add(
                key(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "prepared-text:${packet.packetId.value}:material-group",
                ),
                )
                add(
                key(
                    GPUPreparedNativeOperandRole.RenderBindGroup,
                    GPUPreparedNativeOperandKind.BindGroup,
                    "prepared-text:${packet.packetId.value}:atlas-group",
                ),
                )
                if (render.preparedTextBindingsByPacketId[packet.packetId]
                        ?.coverageMaskResource != null
                ) {
                    add(
                        key(
                            GPUPreparedNativeOperandRole.RenderBindGroup,
                            GPUPreparedNativeOperandKind.BindGroup,
                            "prepared-text:${packet.packetId.value}:coverage-mask-group",
                        ),
                    )
                }
                add(
                key(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    "prepared-text:${packet.packetId.value}:instances",
                ),
                )
            }
        }
        val coverageMask = scope.corePrimitiveCoverageMaskPreparedRouteSeal
        val coverageUnits = coverageMask.units()
        if (coverageUnits.isNotEmpty()) {
            val isProducer = coverageUnits.all {
                it is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer
            }
            val isConsumer = coverageUnits.all {
                it is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer
            }
            if (!isProducer && !isConsumer ||
                coverageUnits.map { unit -> when (unit) {
                    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Producer -> unit.packetId
                    is GPUCorePrimitiveCoverageMaskPreparedScopeRouteSeal.Consumer -> unit.packetId
                    else -> error("Coverage-mask retained scope contains a non-unit seal")
                } } != render.drawPackets.map(GPUDrawPacket::packetId)
            ) return emptyList()
            val pipelines = stream.operandBridge.filter {
                it.operand.kind == org.graphiks.kanvas.gpu.renderer.resources
                    .GPUMaterializedCommandOperandKind.RenderPipeline
            }.mapNotNull(::bridgeKey)
            val bindGroups = stream.operandBridge.filter {
                it.operand.kind == org.graphiks.kanvas.gpu.renderer.resources
                    .GPUMaterializedCommandOperandKind.BindGroup
            }.mapNotNull(::bridgeKey)
            if (pipelines.size != coverageUnits.size || bindGroups.size != coverageUnits.size) {
                return emptyList()
            }
            if (isProducer) {
                val ordered = stream.operandBridge.mapNotNull(::bridgeKey)
                return if (ordered.size == coverageUnits.size * 2) target + ordered else emptyList()
            }
            val geometry = if (isProducer) emptyList() else {
                val vertexIndex = render.resourceUses.indexOfFirst { it.role == GPUFrameResourceRole.VertexData }
                val indexIndex = render.resourceUses.indexOfFirst { it.role == GPUFrameResourceRole.IndexData }
                if (vertexIndex < 0 || indexIndex < 0) return emptyList()
                listOf(
                    key(GPUPreparedNativeOperandRole.RenderVertexBuffer,
                        GPUPreparedNativeOperandKind.Buffer, labels[vertexIndex + 1]),
                    key(GPUPreparedNativeOperandRole.RenderIndexBuffer,
                        GPUPreparedNativeOperandKind.Buffer, labels[indexIndex + 1]),
                )
            }
            return target + pipelines + geometry + bindGroups
        }
        val unified = scope.corePrimitiveNativeScopeRouteSeal as?
            GPUCorePrimitiveNativeScopeRouteSeal.Routes
        val path = unified?.orderedUnits?.any {
            it is GPUCorePrimitiveNativeScopeRouteUnit.PathPair ||
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathProducer ||
                it is GPUCorePrimitiveNativeScopeRouteUnit.PathCover
        } == true
        val direct = scope.corePrimitiveDirectNativeRouteSeal is
            GPUCorePrimitiveDirectNativeRouteSeal.Routes
        if (path) {
            val depthIndex = render.resourceUses.indexOfFirst { use ->
                use.role == GPUFrameResourceRole.PathDepthStencil
            }
            val vertexIndex = render.resourceUses.indexOfFirst { use ->
                use.role == GPUFrameResourceRole.VertexData
            }
            val indexIndex = render.resourceUses.indexOfFirst { use ->
                use.role == GPUFrameResourceRole.IndexData
            }
            if (depthIndex < 0 || vertexIndex < 0 || indexIndex < 0) return emptyList()
            val pipelines = stream.operandBridge
                .filter {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.RenderPipeline
                }
                .zip(render.drawPackets)
                .distinctBy { (_, packet) -> packet.renderPipelineKey }
                .mapNotNull { (bridge, _) -> bridgeKey(bridge) }
            val bindGroups = stream.operandBridge
                .filter {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.BindGroup
                }
                .mapNotNull(::bridgeKey)
            return target + listOf(
                key(
                    GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                    GPUPreparedNativeOperandKind.TextureView,
                    labels[depthIndex + 1],
                ),
            ) + pipelines + listOf(
                key(
                    GPUPreparedNativeOperandRole.RenderVertexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    labels[vertexIndex + 1],
                ),
                key(
                    GPUPreparedNativeOperandRole.RenderIndexBuffer,
                    GPUPreparedNativeOperandKind.Buffer,
                    labels[indexIndex + 1],
                ),
            ) + bindGroups
        }
        val bridges = if (direct) {
            val pipelineBridges = stream.operandBridge.filter {
                it.operand.kind ==
                    org.graphiks.kanvas.gpu.renderer.resources
                        .GPUMaterializedCommandOperandKind.RenderPipeline
            }
            pipelineBridges.zip(render.drawPackets)
                .distinctBy { (_, packet) -> packet.renderPipelineKey }
                .map { (bridge, _) -> bridge } +
                listOfNotNull(
                    stream.operandBridge.firstOrNull {
                        it.operand.kind ==
                            org.graphiks.kanvas.gpu.renderer.resources
                                .GPUMaterializedCommandOperandKind.VertexBuffer
                    },
                    stream.operandBridge.firstOrNull {
                        it.operand.kind ==
                            org.graphiks.kanvas.gpu.renderer.resources
                                .GPUMaterializedCommandOperandKind.IndexBuffer
                    },
                ) + stream.operandBridge.filter {
                    it.operand.kind ==
                        org.graphiks.kanvas.gpu.renderer.resources
                            .GPUMaterializedCommandOperandKind.BindGroup
                }
        } else {
            stream.operandBridge
        }
        val keys = bridges.mapNotNull(::bridgeKey)
        return if (keys.size != bridges.size) emptyList() else target + keys
    }
}

private data class PreparedTextPacketEvidence(
    val renderIndex: Int,
    val render: GPUFrameStep.RenderPassStep,
    val packet: GPUDrawPacket,
    val semantic: GPUDrawSemanticPayload,
)

private data class PreparedTextUploadEvidence(
    val sourceStepIndex: Int,
    val step: GPUFrameStep.UploadResourceStep,
    val plan: GPUR8FrameResourcePlan,
)

private data class PreparedTextMaterialUploadEvidence(
    val sourceStepIndex: Int,
    val step: GPUFrameStep.UploadResourceStep,
    val plan:
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan,
)

private fun GPUResourcePreparationRequest?.matchesPreparedTextBufferPlan(
    byteSize: Long,
    alignmentBytes: Long,
    role: GPUFrameResourceRole,
    usages: Set<GPUFrameResourceUsage>,
): Boolean {
    val request = this ?: return false
    val descriptor = request.descriptor as? GPUFrameBufferDescriptor ?: return false
    return descriptor.byteSize == byteSize &&
        descriptor.alignmentBytes == alignmentBytes &&
        request.byteSize == byteSize &&
        request.role == role &&
        request.usages == usages &&
        request.lifetime == GPUFrameResourceLifetime.FrameLocal
}

private fun GPUPreparedTextRenderBinding.matchesPreparedColorGlyphBufferPlan(
    semantic: GPUDrawSemanticPayload.ColorGlyph,
): Boolean {
    val plan = colorGlyphBufferPlan
    val slice = colorGlyphBufferSlice
    if (plan.planArtifactKey != semantic.planArtifactKey ||
        slice.commandIdValue != semantic.payloadRef.commandIdValue ||
        slice.vertexSizeBytes != semantic.vertexData.size.toLong() * Float.SIZE_BYTES ||
        slice.indexSizeBytes != semantic.indexData.size.toLong() * Int.SIZE_BYTES ||
        slice.uniformSizeBytes != semantic.uniformBytes.size.toLong() ||
        slice.indexCount != semantic.indexData.size
    ) {
        return false
    }
    val vertexBytes = ByteBuffer.wrap(plan.vertexBytesForUpload()).order(ByteOrder.LITTLE_ENDIAN)
    val indexBytes = ByteBuffer.wrap(plan.indexBytesForUpload()).order(ByteOrder.LITTLE_ENDIAN)
    if (semantic.vertexData.indices.any { index ->
            val planned = vertexBytes.getFloat(
                Math.addExact(slice.vertexOffsetBytes.toInt(), index * Float.SIZE_BYTES),
            )
            planned.toRawBits() != semantic.vertexData[index].toRawBits()
        } ||
        semantic.indexData.indices.any { index ->
            indexBytes.getInt(
                Math.addExact(slice.indexOffsetBytes.toInt(), index * Int.SIZE_BYTES),
            ) != semantic.indexData[index]
        }
    ) {
        return false
    }
    val uniformBytes = plan.uniformBytesForUpload()
    return semantic.uniformBytes.indices.none { index ->
        uniformBytes[Math.addExact(slice.uniformOffsetBytes.toInt(), index)] !=
            semantic.uniformBytes[index].toByte()
    }
}

private fun GPUFramePlan.preparedTextBindingsForColorGlyphAuthentication():
    List<GPUPreparedTextRenderBinding> =
    steps.filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap { render ->
            render.drawPackets.mapNotNull { packet ->
                packet.semanticPayload
                    ?.takeIf { semantic ->
                        semantic is GPUDrawSemanticPayload.TextA8 ||
                            semantic is GPUDrawSemanticPayload.ColorGlyph
                    }
                    ?.let {
                        render.preparedTextBindingsByPacketId[packet.packetId]
                    }
            }
        }

private fun GPUFrameStep.UploadResourceStep.matchesPreparedTextTextureUpload(
    plan: org.graphiks.kanvas.gpu.renderer.resources.GPUTextureFrameResourcePlan,
): Boolean =
    staging == plan.stagingRef &&
        destination == plan.frameTextureRef &&
        layout.sourceOffsetBytes == plan.uploadTaskLayout.sourceOffsetBytes &&
        layout.bytesPerRow == plan.uploadTaskLayout.bytesPerRow &&
        layout.rowsPerImage == plan.uploadTaskLayout.rowsPerImage &&
        layout.byteSize == plan.uploadTaskLayout.byteSize &&
        plan.bytesForUpload().size.toLong() == layout.byteSize

private fun GPUPreparedTextRenderBinding.preparedTextResourceRefs() = buildList {
    add(atlasResourcePlan.stagingRef)
    add(atlasResourcePlan.frameTextureRef)
    add(instanceBufferPlan.bufferRef)
    if (hasColorGlyphBufferPlan) {
        add(colorGlyphBufferPlan.vertexBufferRef)
        add(colorGlyphBufferPlan.indexBufferRef)
        add(colorGlyphBufferPlan.uniformBufferRef)
    }
    if (hasTextA8Composite) add(drawUniformBufferPlan.bufferRef)
    materialUniformBufferPlan?.let { add(it.bufferRef) }
    materialSampledResourcePlans.forEach { resource ->
        add(resource.stagingRef)
        add(resource.frameTextureRef)
    }
}

private fun GPUDrawSemanticPayload.preparedTextAtlas(): GPUPreparedR8UploadArtifact = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> atlas
    is GPUDrawSemanticPayload.ColorGlyph -> atlas
    else -> error("Only prepared-text semantics own an R8 atlas")
}

private fun GPUDrawSemanticPayload.preparedTextAtlasGeneration(): Long = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> atlasGeneration.value.toLong()
    is GPUDrawSemanticPayload.ColorGlyph -> atlasGeneration
    else -> error("Only prepared-text semantics own an atlas generation")
}

private fun GPUDrawSemanticPayload.preparedTextPageIndex(): Int = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> pageIndex
    is GPUDrawSemanticPayload.ColorGlyph -> instances.first().pageIndex
    else -> error("Only prepared-text semantics own a page index")
}

private fun GPUDrawSemanticPayload.preparedTextInstances() = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> instances
    is GPUDrawSemanticPayload.ColorGlyph -> instances
    else -> error("Only prepared-text semantics own instance records")
}

private fun GPUDrawSemanticPayload.preparedTextMaterial() = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> material
    is GPUDrawSemanticPayload.ColorGlyph -> requireNotNull(material)
    else -> error("Only prepared-text semantics own prepared materials")
}

private fun GPUDrawSemanticPayload.preparedTextTargetBounds(): GPUPixelBounds = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> targetBounds
    is GPUDrawSemanticPayload.ColorGlyph -> targetBounds
    else -> error("Only prepared-text semantics own target bounds")
}

private fun GPUDrawSemanticPayload.preparedTextScissorBounds(): GPUPixelBounds = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> scissorBounds
    is GPUDrawSemanticPayload.ColorGlyph -> scissorBounds
    else -> error("Only prepared-text semantics own scissor bounds")
}

private fun GPUDrawSemanticPayload.preparedTextClipIdentity(): String = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> clipIdentity
    is GPUDrawSemanticPayload.ColorGlyph -> requireNotNull(clipIdentity)
    else -> error("Only prepared-text semantics own clip identity")
}

private fun GPUDrawSemanticPayload.preparedTextBlendIdentity(): String = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> blendPlanIdentity
    is GPUDrawSemanticPayload.ColorGlyph -> requireNotNull(blendPlanIdentity)
    else -> error("Only prepared-text semantics own blend identity")
}

private fun GPUDrawSemanticPayload.preparedTextCapabilityHash(): String = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> capabilitySnapshotHash
    is GPUDrawSemanticPayload.ColorGlyph -> requireNotNull(capabilitySnapshotHash)
    else -> error("Only prepared-text semantics own capability identity")
}

private fun GPUDrawSemanticPayload.preparedTextCanonicalHash(): String = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> canonicalHash
    is GPUDrawSemanticPayload.ColorGlyph -> canonicalHash
    else -> error("Only prepared-text semantics own a canonical hash")
}

private fun GPUDrawSemanticPayload.hasPreparedTextCanonicalIntegrity(): Boolean = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> hasCanonicalHashIntegrity()
    is GPUDrawSemanticPayload.ColorGlyph -> hasCanonicalHashIntegrity()
    else -> false
}

private fun GPUR8FrameResourcePlan.samePreparedTextPageAs(
    other: GPUR8FrameResourcePlan,
): Boolean =
    r8ArtifactIdentity == other.r8ArtifactIdentity

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan
    .samePreparedMaterialResourceAs(
        other:
            org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan,
    ): Boolean =
    resourceKey == other.resourceKey &&
        width == other.width &&
        height == other.height &&
        samplingFilterMode == other.samplingFilterMode &&
        alphaOnly == other.alphaOnly &&
        contentHash == other.contentHash &&
        stagingRef == other.stagingRef &&
        frameTextureRef == other.frameTextureRef &&
        uploadTaskLayout == other.uploadTaskLayout

private fun GPUResourcePreparationRequest?.matchesPreparedTextOwnership(
    expectedRole: GPUFrameResourceRole,
    expectedUsages: Set<GPUFrameResourceUsage>,
): Boolean =
    this != null &&
        role == expectedRole &&
        usages == expectedUsages &&
        lifetime == GPUFrameResourceLifetime.FrameLocal

private fun GPUPixelBounds.isContainedBy(outer: GPUPixelBounds): Boolean =
    !isEmpty &&
        left >= outer.left &&
        top >= outer.top &&
        right <= outer.right &&
        bottom <= outer.bottom

private fun String.utf8Sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .toHexString()

private fun ByteArray.byteHash(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .toHexString()

private fun ByteArray.toHexString(): String =
    buildString(size * 2) {
        for (byte in this@toHexString) {
            val value = byte.toInt() and 0xff
            append(PREPARED_TEXT_LOWER_HEX_DIGITS[value ushr 4])
            append(PREPARED_TEXT_LOWER_HEX_DIGITS[value and 0x0f])
        }
    }

private const val PREPARED_TEXT_LOWER_HEX_DIGITS = "0123456789abcdef"

private fun leastCommonMultipleTextOrNull(left: Long, right: Long): Long? {
    require(left > 0L && right > 0L)
    fun greatestCommonDivisor(first: Long, second: Long): Long {
        var a = first
        var b = second
        while (b != 0L) {
            val remainder = a % b
            a = b
            b = remainder
        }
        return a
    }
    val reduced = left / greatestCommonDivisor(left, right)
    return if (reduced > Long.MAX_VALUE / right) {
        null
    } else {
        reduced * right
    }
}

private fun GPULoadStorePlan.dumpLabelForPreparedSurface(): String =
    "$loadOp:${storePlan.name}:${clearColorLabel ?: "none"}"

private fun GPUDrawPacket.payloadIdentityMismatch(
    semantic: GPUDrawSemanticPayload.SampledImage,
): Boolean =
    semantic.payloadRef.commandIdValue != commandIdValue ||
        semantic.payloadRef.renderStepIdentity != renderStepId.value ||
        semantic.blendPlanIdentity != blendPlan?.canonicalIdentity()

private fun GPUPreparedResourceEvidence.matchesExactPreparation(
    request: GPUResourcePreparationRequest,
    generationSeal: GPUPreparedGenerationSeal,
): Boolean {
    if (logicalResource != request.resource ||
        role != request.role ||
        deviceGeneration != generationSeal.deviceGeneration ||
        resourceGeneration != generationSeal.resourceGenerations[request.resource]
    ) {
        return false
    }
    return when (val descriptor = request.descriptor) {
        is GPUFrameTextureDescriptor -> {
            concreteResource is GPUPreparedConcreteResourceRef.Texture &&
                textureAllocation?.let { allocation ->
                    allocation.logicalBounds == descriptor.logicalBounds &&
                        allocation.backingWidth >= descriptor.logicalBounds.width &&
                        allocation.backingHeight >= descriptor.logicalBounds.height &&
                        allocation.format == descriptor.format &&
                        allocation.sampleCount == descriptor.sampleCount &&
                        allocation.usages == request.usages
                } != false
        }
        is GPUFrameBufferDescriptor ->
            concreteResource is GPUPreparedConcreteResourceRef.Buffer &&
                textureAllocation == null &&
                descriptor.byteSize == request.byteSize
    }
}

private fun GPUResourcePreparationRequest.samePreparationAs(
    other: GPUResourcePreparationRequest,
): Boolean =
    resource == other.resource &&
        descriptor == other.descriptor &&
        role == other.role &&
        usages == other.usages &&
        lifetime == other.lifetime &&
        byteSize == other.byteSize &&
        diagnosticLabel == other.diagnosticLabel

private fun GPUPreparedResourceSet.hasValidCommandEvidence(
    encoderPlan: GPUCommandEncoderPlan,
    generationSeal: GPUPreparedGenerationSeal,
): Boolean {
    val operandReferences = encoderPlan.scopes
        .flatMap { scope -> scope.passCommandStream?.operandBridge.orEmpty() }
        .map { bridge -> bridge.operand }
    val leaseIds = commandResourceLeases.map(GPUPreparedCommandResourceLease::leaseId)
    if (leaseIds.distinct().size != leaseIds.size ||
        commandResourceLeases.any { lease ->
            lease.deviceGeneration != generationSeal.deviceGeneration.value ||
                lease.cacheResult !in setOf(
                    GPUResourceLeaseCacheResult.Create,
                    GPUResourceLeaseCacheResult.Reuse,
                ) ||
                lease.usageLabels.distinct().size != lease.usageLabels.size ||
                operandReferences.count { operand ->
                    lease.matchesExactOperandReference(operand)
                } != 1
        }
    ) {
        return false
    }
    val textureRefs = commandTextureResources.map { resource -> resource.value }
    val bufferRefs = commandBufferResources.map { resource -> resource.value }
    val ordinaryConcreteRefs = ordinaryResources.map { evidence ->
        evidence.concreteResource.value
    } + outputOwnedReadbacks.map { readback -> readback.concreteResource.value }
    if (textureRefs.distinct().size != textureRefs.size ||
        bufferRefs.distinct().size != bufferRefs.size ||
        (textureRefs.toSet() intersect bufferRefs.toSet()).isNotEmpty() ||
        ((textureRefs + bufferRefs).toSet() intersect ordinaryConcreteRefs.toSet()).isNotEmpty()
    ) {
        return false
    }
    val expectedTextureLabels = operandReferences
        .filter { operand -> operand.kind.isPreparedSurfaceTextureKind() }
        .map { operand -> operand.label }
        .toSet()
    val expectedBufferLabels = operandReferences
        .filter { operand -> operand.kind.isPreparedSurfaceBufferKind() }
        .map { operand -> operand.label }
        .toSet()
    if (textureRefs.any { it !in expectedTextureLabels } ||
        bufferRefs.any { it !in expectedBufferLabels }
    ) {
        return false
    }
    val knownDiagnosticLabels = buildSet {
        addAll(textureRefs)
        addAll(bufferRefs)
        addAll(leaseIds)
        addAll(commandResourceLeases.map(GPUPreparedCommandResourceLease::descriptorHash))
        encoderPlan.scopes.forEach { scope ->
            scope.passCommandStream?.operandBridge.orEmpty().forEach { bridge ->
                add(bridge.operand.label)
            }
        }
    }
    val diagnosticIdentities = commandDiagnostics.map { diagnostic ->
        listOf(
            diagnostic.code,
            diagnostic.resourceLabel,
            diagnostic.message,
            diagnostic.facts.entries.sortedBy { entry -> entry.key }.toString(),
        )
    }
    return diagnosticIdentities.distinct().size == diagnosticIdentities.size &&
        commandDiagnostics.all { diagnostic ->
            diagnostic.resourceLabel in knownDiagnosticLabels
        }
}

private fun GPUPreparedCommandResourceLease.matchesExactOperandReference(
    operand: org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference,
): Boolean =
    deviceGeneration == operand.deviceGeneration &&
        descriptorHash == operand.descriptorHash &&
        ownerScope == operand.ownerScope &&
        usageLabels == operand.usageLabels &&
        resourceKind.matchesPreparedSurfaceOperandKind(operand.kind)

private fun GPUResourceLeaseKind.matchesPreparedSurfaceOperandKind(
    operandKind:
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind,
): Boolean = when (this) {
    GPUResourceLeaseKind.UniformSlab ->
        operandKind ==
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.UniformBuffer
    GPUResourceLeaseKind.NullBuffer -> operandKind in setOf(
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.UniformBuffer,
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.StorageBuffer,
    )
    GPUResourceLeaseKind.BindGroup ->
        operandKind ==
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.BindGroup
    GPUResourceLeaseKind.Texture -> operandKind.isPreparedSurfaceTextureKind()
    GPUResourceLeaseKind.TextureView -> operandKind in setOf(
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.TextureView,
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderTarget,
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.DepthStencilAttachment,
    )
    GPUResourceLeaseKind.Sampler ->
        operandKind ==
        org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.Sampler
}

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
    .isPreparedSurfaceTextureKind(): Boolean = this in setOf(
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.Texture,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.TextureView,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.RenderTarget,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.DepthStencilAttachment,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.DestinationCopyTexture,
)

private fun org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
    .isPreparedSurfaceBufferKind(): Boolean = this in setOf(
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.UniformBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.StorageBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.VertexBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.IndexBuffer,
    org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind.ReadbackResource,
)

private fun GPUFrameStep.isPreparedSurfaceStep(): Boolean = when (this) {
    is GPUFrameStep.PrepareResourcesStep,
    is GPUFrameStep.UploadResourceStep,
    is GPUFrameStep.RenderPassStep,
    is GPUFrameStep.CopyDestinationStep,
    is GPUFrameStep.DependencyBarrierStep,
    is GPUFrameStep.ReadbackCopyStep,
    is GPUFrameStep.AcquireSurfaceOutput,
    is GPUFrameStep.SurfaceBlitRenderPassStep,
    is GPUFrameStep.PostSubmitPresentAction,
    is GPUFrameStep.LayerTargetPrepareStep,
    is GPUFrameStep.LayerChildrenRenderStep,
    is GPUFrameStep.LayerCompositeRenderStep,
    -> true
    else -> false
}

private fun GPUFrameStep.preparedSurfaceOperationKindOrNull(): GPUEncoderOperationKind? =
    when (this) {
        is GPUFrameStep.UploadResourceStep -> GPUEncoderOperationKind.Upload
        is GPUFrameStep.RenderPassStep -> GPUEncoderOperationKind.Render
        is GPUFrameStep.CopyDestinationStep -> GPUEncoderOperationKind.CopyDestination
        is GPUFrameStep.ReadbackCopyStep -> GPUEncoderOperationKind.Readback
        is GPUFrameStep.SurfaceBlitRenderPassStep -> GPUEncoderOperationKind.SurfaceBlit
        is GPUFrameStep.LayerCompositeRenderStep -> GPUEncoderOperationKind.LayerComposite
        else -> null
    }

/**
 * Resolves the exact device-space layer texture bounds from the children draws.
 *
 * Every child must carry a CorePrimitive semantic with either rect geometry or a sealed
 * triangulated-path cover bounds; the layer texture covers their union, and the composite
 * quad samples that texture at the same device rect in the parent target.
 */
private fun preparedSurfaceLayerChildrenBounds(
    packets: List<GPUDrawPacket>,
): GPUPixelBounds? {
    var union: GPUPixelBounds? = null
    for (packet in packets) {
        val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive
            ?: return null
        val bounds = when (val geometry = semantic.geometry) {
            is GPUCorePrimitiveGeometry.Rect -> GPUPixelBounds(
                geometry.left.toInt(),
                geometry.top.toInt(),
                geometry.right.toInt(),
                geometry.bottom.toInt(),
            )
            is GPUCorePrimitiveGeometry.TriangulatedPath -> geometry.coverBounds
            else -> return null
        }
        if (bounds.isEmpty) return null
        union = if (union == null) {
            bounds
        } else {
            GPUPixelBounds(
                minOf(union.left, bounds.left),
                minOf(union.top, bounds.top),
                maxOf(union.right, bounds.right),
                maxOf(union.bottom, bounds.bottom),
            )
        }
    }
    return union
}

/**
 * The closed native saveLayer route currently retains scene-space child coordinates. Its backing
 * texture is therefore scene-sized even when the composite quad is tightly bounded. Keep this
 * accounting beside preflight so materialization cannot allocate more than the frame admitted.
 */
internal fun validatePreparedSurfaceSceneSizedLayerTargetBudget(
    frameBudget: org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan,
    layerTargets: List<GPUPreparedSurfaceLayerTargetPlan>,
): GPUPreparedSurfaceNativePreflightResult.Refused? {
    if (layerTargets.isEmpty()) return null
    if (frameBudget.configuredAggregateBudgetBytes <= 0L ||
        !GPUFrameMemoryBudgetPlanner.hasExactLimitIndependentFacts(frameBudget)
    ) {
        return GPUPreparedSurfaceNativePreflightResult.Refused(
            code = "invalid.prepared-surface.layer-target-budget",
            message = "Prepared layer targets require exact positive aggregate frame budget facts.",
        )
    }
    val layerAllocationBytes = try {
        layerTargets.fold(0L) { total, layer ->
            val expectedBytes = preparedSurfaceTextureByteEstimate(layer.allocationBounds)
                ?: throw ArithmeticException("layer target byte estimate overflow")
            if (layer.allocationByteEstimate != expectedBytes) {
                throw IllegalArgumentException("layer target byte estimate mismatch")
            }
            Math.addExact(total, expectedBytes)
        }
    } catch (_: ArithmeticException) {
        return GPUPreparedSurfaceNativePreflightResult.Refused(
            code = "invalid.prepared-surface.layer-target-budget",
            message = "Prepared layer target backing allocation byte accounting overflowed.",
        )
    } catch (_: IllegalArgumentException) {
        return GPUPreparedSurfaceNativePreflightResult.Refused(
            code = "invalid.prepared-surface.layer-target-budget",
            message = "Prepared layer target backing allocation bytes must match the allocation extent.",
        )
    }
    val aggregateBytes = try {
        Math.addExact(
            Math.addExact(frameBudget.targetResidentBytes, frameBudget.peakFrameTransientBytes),
            layerAllocationBytes,
        )
    } catch (_: ArithmeticException) {
        return GPUPreparedSurfaceNativePreflightResult.Refused(
            code = "invalid.prepared-surface.layer-target-budget",
            message = "Prepared layer target aggregate byte accounting overflowed.",
        )
    }
    if (aggregateBytes <= frameBudget.configuredAggregateBudgetBytes) return null

    val firstLayer = layerTargets.first()
    return GPUPreparedSurfaceNativePreflightResult.Refused(
        code = "unsupported.prepared-surface.layer-target-budget",
        message = "Scene-sized prepared layer target allocation exceeds the configured aggregate frame budget.",
        facts = mapOf(
            "layerAllocationBytes" to firstLayer.allocationByteEstimate.toString(),
            "layerAllocationTotalBytes" to layerAllocationBytes.toString(),
            "aggregateBytes" to aggregateBytes.toString(),
            "configuredAggregateBudgetBytes" to frameBudget.configuredAggregateBudgetBytes.toString(),
            "layerBounds" to firstLayer.bounds.preparedSurfaceBoundsLabel(),
            "allocationBounds" to firstLayer.allocationBounds.preparedSurfaceBoundsLabel(),
        ),
    )
}

private fun preparedSurfaceSceneTargetAllocationBounds(framePlan: GPUFramePlan): GPUPixelBounds? =
    framePlan.steps
        .filterIsInstance<GPUFrameStep.PrepareResourcesStep>()
        .flatMap(GPUFrameStep.PrepareResourcesStep::requests)
        .singleOrNull { request -> request.role == GPUFrameResourceRole.SceneTarget }
        ?.descriptor
        ?.let { descriptor -> descriptor as? GPUFrameTextureDescriptor }
        ?.logicalBounds

private fun preparedSurfaceTextureByteEstimate(bounds: GPUPixelBounds): Long? =
    try {
        bounds.checkedByteSize(bytesPerPixel = 4, sampleCount = 1)
    } catch (_: ArithmeticException) {
        null
    }

private fun GPUPixelBounds.preparedSurfaceBoundsLabel(): String =
    "$left,$top,$right,$bottom"

private fun preparedSurfaceSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

internal fun ByteArray.contentEqualsPreparedTextUniformRange(
    offsetBytes: Long,
    endBytes: Long,
    expected: ByteArray,
): Boolean {
    if (offsetBytes < 0L ||
        endBytes < offsetBytes ||
        endBytes > size.toLong() ||
        endBytes - offsetBytes != expected.size.toLong()
    ) {
        return false
    }
    val startIndex = offsetBytes.toInt()
    expected.indices.forEach { index ->
        if (this[startIndex + index] != expected[index]) {
            return false
        }
    }
    return true
}

private fun refused(
    code: String,
    message: String,
    facts: Map<String, String> = emptyMap(),
) = GPUPreparedSurfaceNativePreflightResult.Refused(code, message, facts)

/** Exact recording-builder staging ref for the shared frame vertices staging buffer. */
private fun preparedVerticesStagingRef(frameId: GPUFrameID): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.staging.${frameId.value}")

/** Exact recording-builder vertex buffer ref for one prepared-vertices artifact. */
private fun preparedVerticesVertexBufferRef(
    frameId: GPUFrameID,
    artifactKey: String,
): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.vertex.${frameId.value}.$artifactKey")

/** Exact recording-builder index buffer ref for one prepared-vertices artifact. */
private fun preparedVerticesIndexBufferRef(
    frameId: GPUFrameID,
    artifactKey: String,
): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.index.${frameId.value}.$artifactKey")

/** Re-verifies exactly what crosses the native seam: counts, bytes, and content hashes. */
private fun GPUPreparedVerticesUploadArtifact.hasExactPreparedVerticesByteIntegrity(): Boolean {
    val vertexBytes = vertexBytesForUpload()
    val strideBytes = layout.strideBytes
    val vertexBytesMatch = try {
        Math.multiplyExact(vertexCount, strideBytes) == vertexBytes.size
    } catch (_: ArithmeticException) {
        false
    }
    if (!vertexBytesMatch || preparedSurfaceSha256(vertexBytes) != vertexContentHash) {
        return false
    }
    val indexBytes = indexBytesForUpload()
    if (indexBytes == null) {
        return indexCount == null && indexFormat == null
    }
    if (indexCount == null ||
        indexFormat !in setOf("uint16", "uint32")
    ) {
        return false
    }
    val elementBytes = if (indexFormat == "uint16") 2 else 4
    val indexBytesMatch = try {
        Math.multiplyExact(indexCount, elementBytes) == indexBytes.size
    } catch (_: ArithmeticException) {
        false
    }
    return indexBytesMatch && indexContentHash != null &&
        preparedSurfaceSha256(indexBytes) == indexContentHash
}

/** One vertices upload step must exactly match one derived staging range. */
private fun GPUFrameStep.UploadResourceStep.matchesPreparedVerticesUploadRange(
    range: GPUVerticesStagingRange,
): Boolean =
    layout == GPUUploadLayout(
        sourceOffsetBytes = range.offsetBytes,
        bytesPerRow = range.byteCount,
        rowsPerImage = 1,
        byteSize = range.byteCount,
    )

/** One vertices buffer preparation must retain its exact frame-local ownership. */
private fun GPUResourcePreparationRequest?.matchesPreparedVerticesBufferPlan(
    role: GPUFrameResourceRole,
    usages: Set<GPUFrameResourceUsage>,
    byteCount: Long,
): Boolean =
    this != null &&
        this.role == role &&
        this.usages == usages &&
        this.lifetime == GPUFrameResourceLifetime.FrameLocal &&
        this.byteSize == byteCount &&
        (this.descriptor as? GPUFrameBufferDescriptor)?.byteSize == byteCount

/** Frame-global artifact plans derived deterministically from every vertices packet. */
private fun preparedVerticesPlansByArtifactKey(
    framePlan: GPUFramePlan,
): Map<String, GPUVerticesFrameResourcePlan> {
    val deviceGeneration = framePlan.capabilitySeal.deviceGeneration.value
    return framePlan.steps
        .filterIsInstance<GPUFrameStep.RenderPassStep>()
        .flatMap(GPUFrameStep.RenderPassStep::drawPackets)
        .mapNotNull { packet ->
            (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)?.artifact
        }
        .distinctBy { artifact -> artifact.key }
        .sortedBy { artifact -> artifact.key }
        .associate { artifact ->
            artifact.key to buildVerticesFrameResourcePlan(artifact, deviceGeneration)
        }
}

/** Recovers the artifact key from the exact recording-builder destination ref. */
private fun preparedVerticesArtifactKeyOfUpload(upload: GPUFrameStep.UploadResourceStep): String {
    val segments = upload.destination.value.split('.')
    return segments.last()
}

private val PREPARED_VERTICES_ABI_HASH_PATTERN = Regex("sha256:[0-9a-f]{64}")

private inline fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
    forEachIndexed { index, value ->
        if (predicate(index, value)) return true
    }
    return false
}
