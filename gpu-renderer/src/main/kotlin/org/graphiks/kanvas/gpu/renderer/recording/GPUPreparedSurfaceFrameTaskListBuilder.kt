package org.graphiks.kanvas.gpu.renderer.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.collections.immutableList
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadMember
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroup
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupKey
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotGroupingResult
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationSnapshotMaterialization
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUPreparedImageRefusalCodes
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextAuthenticatedComposite
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeAdmissionToken
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgram
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramCache
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedTextCompositeProgramResult
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUClipProducerAuthority
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageMaskProducerUniformSlabSeal
import org.graphiks.kanvas.gpu.renderer.passes.CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_LAYOUT_KEY
import org.graphiks.kanvas.gpu.renderer.passes.CORE_PRIMITIVE_STRUCTURAL_PIPELINE_BASE_KEY
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey
import org.graphiks.kanvas.gpu.renderer.passes.corePrimitiveCoverageMaskProducerUniformBytes
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard
import org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey
import org.graphiks.kanvas.gpu.renderer.passes.GPURenderStepID
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedTextDeviceToLocalAffine
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageGeometryClass
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageBindingLayoutTopology
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedImageVertex
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.payloads.preparedImageScissorAuthority
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryAllocation
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryBudgetRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryCategory
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameMemoryResourceKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceLifetime
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUImageBindingInput
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUR8ArtifactIdentity
import org.graphiks.kanvas.gpu.renderer.artifacts.GPUVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.PREPARED_VERTICES_BUFFER_ALIGNMENT
import org.graphiks.kanvas.gpu.renderer.resources.GPUUploadLayout
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureCopyLayout
import org.graphiks.kanvas.gpu.renderer.resources.buildImageFrameResourcePlanFromBindings
import org.graphiks.kanvas.gpu.renderer.resources.buildMaterialTextureFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.resources.buildR8FrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesFrameResourcePlan
import org.graphiks.kanvas.gpu.renderer.artifacts.buildVerticesStagingLayout
import org.graphiks.kanvas.gpu.renderer.resources.r8ArtifactIdentity
import org.graphiks.kanvas.gpu.renderer.state.GPULoadStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUStorePlan
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeLowerer
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeLowering
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositePlan
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositePreflight
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeRefusalCodes
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScope
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreparedCompositeScopeId
import org.graphiks.kanvas.gpu.renderer.layers.GPUPreflightCapabilities
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerExecutionPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerScopeID
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetGatePlan
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerMaterializationResult
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerNativeExecutor
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextVertexLayout

data class GPUPreparedSurfaceFrameRequest(
    val baseTaskList: GPUTaskList,
    val capabilities: GPUCapabilities,
    val target: GPUFrameTargetRef,
    val targetBounds: GPUPixelBounds,
    val semanticsByCommandId: Map<Int, GPUDrawSemanticPayload>,
    val readbackRequestId: GPUReadbackRequestID?,
    val targetFormat: GPUColorFormat = GPUColorFormat.RGBA8Unorm,
)

/** Checked structural ceilings applied before one prepared task graph is published. */
data class GPUPreparedSurfaceTaskGraphLimits(
    val maxBufferAllocations: Int = Int.MAX_VALUE,
    val maxTextureAllocations: Int = Int.MAX_VALUE,
    val maxAllocations: Int = Int.MAX_VALUE,
    val maxTasks: Int = Int.MAX_VALUE,
    val maxDependencies: Int = Int.MAX_VALUE,
    val maxInstanceRanges: Int = Int.MAX_VALUE,
) {
    init {
        require(maxBufferAllocations >= 0)
        require(maxTextureAllocations >= 0)
        require(maxAllocations >= 0)
        require(maxTasks >= 0)
        require(maxDependencies >= 0)
        require(maxInstanceRanges >= 0)
    }
}

sealed interface GPUPreparedSurfaceFrameResult {
    data class Recorded(val taskList: GPUTaskList) : GPUPreparedSurfaceFrameResult
    data class Refused(val diagnostic: GPUDiagnostic) : GPUPreparedSurfaceFrameResult
}

/** Recording-owned association between task identity and a handle-free resource plan. */
internal data class GPURecordedImageUpload(
    val taskId: GPUTaskID,
    val resources: GPUImageFrameResourcePlan,
)

/** Recording-owned association between one exact immutable R8 artifact and its upload task. */
internal data class GPURecordedR8Upload(
    val taskId: GPUTaskID,
    val resources: GPUR8FrameResourcePlan,
)

/** Recording-owned association between one exact prepared-material texture and its upload. */
internal data class GPURecordedMaterialUpload(
    val taskId: GPUTaskID,
    val resources: GPUMaterialTextureFrameResourcePlan,
)

/** Recording-owned association between one exact prepared-vertices artifact and its upload. */
internal data class GPURecordedVerticesUpload(
    val resources: GPUVerticesFrameResourcePlan,
)

/** One frame-local Task 5 destination snapshot reserved for an exact ColorGlyph packet. */
private data class GPUPreparedColorGlyphDestinationSnapshotPlan(
    val groupIndex: Int,
    val packetId: GPUDrawPacketID,
    val commandIdValue: Int,
    val snapshot: GPUFrameTextureRef,
    val preparation: GPUResourcePreparationRequest,
    val allocation: GPUFrameMemoryAllocation,
    val copiedBytes: Long,
    val copyLayout: GPUTextureCopyLayout,
    val targetGeneration: Long,
)

/** One immutable, frame-global prepared-text instance buffer. */
class GPUPreparedTextInstanceBufferPlan(
    val bufferRef: GPUFrameBufferRef,
    val strideBytes: Int,
    val alignmentBytes: Int,
    val instanceCount: Int,
    val byteSize: Long,
    val contentHash: String,
    uploadBytes: ByteArray,
) {
    private val uploadSnapshot = uploadBytes.copyOf()
    val memoryAllocation = GPUFrameMemoryAllocation(
        label = "prepared-text.instances.$contentHash",
        category = GPUFrameMemoryCategory.ReusableScratch,
        bytes = byteSize,
        resourceKind = GPUFrameMemoryResourceKind.Buffer,
        extent = null,
    )

    init {
        require(strideBytes == GPUTextA8Instance.ENCODED_BYTE_SIZE)
        require(alignmentBytes == PREPARED_TEXT_INSTANCE_ALIGNMENT_BYTES)
        require(instanceCount > 0)
        require(byteSize == uploadSnapshot.size.toLong())
        require(byteSize == Math.multiplyExact(instanceCount.toLong(), strideBytes.toLong()))
        require(contentHash == uploadSnapshot.sha256Hex())
    }

    fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()
}

/** One immutable, aligned, frame-global prepared-material uniform buffer. */
class GPUPreparedTextMaterialUniformBufferPlan(
    val bufferRef: GPUFrameBufferRef,
    val alignmentBytes: Long,
    val byteSize: Long,
    val contentHash: String,
    uploadBytes: ByteArray,
) {
    private val uploadSnapshot = uploadBytes.copyOf()
    val memoryAllocation = GPUFrameMemoryAllocation(
        label = "prepared-text.material-uniforms.$contentHash",
        category = GPUFrameMemoryCategory.ReusableScratch,
        bytes = byteSize,
        resourceKind = GPUFrameMemoryResourceKind.Buffer,
        extent = null,
    )

    init {
        require(alignmentBytes > 0L && alignmentBytes and (alignmentBytes - 1L) == 0L)
        require(byteSize > 0L && byteSize == uploadSnapshot.size.toLong())
        require(contentHash == uploadSnapshot.sha256Hex())
    }

    fun bytesForUpload(): ByteArray = uploadSnapshot.copyOf()
}

/** Exact offsets for one ColorGlyph packet inside its artifact-key-owned frame buffers. */
data class GPUPreparedColorGlyphBufferSlice(
    val commandIdValue: Int,
    val vertexOffsetBytes: Long,
    val vertexSizeBytes: Long,
    val indexOffsetBytes: Long,
    val indexSizeBytes: Long,
    val uniformOffsetBytes: Long,
    val uniformSizeBytes: Long,
    val indexCount: Int,
) {
    init {
        require(commandIdValue >= 0)
        require(vertexOffsetBytes >= 0L && vertexSizeBytes > 0L)
        require(indexOffsetBytes >= 0L && indexSizeBytes > 0L)
        require(uniformOffsetBytes >= 0L && uniformSizeBytes > 0L)
        require(indexCount > 0)
    }
}

/**
 * Immutable handle-free ColorGlyph allocation and upload authority.
 *
 * One exact prepared plan artifact owns three frame buffers. Packet slices preserve source order,
 * while the artifact generation remains the sole generation authority; byte hashes authenticate
 * uploads but never synthesize generation semantics.
 */
class GPUPreparedColorGlyphBufferPlan(
    val planArtifactKey: GPUTextArtifactKey,
    val vertexBufferRef: GPUFrameBufferRef,
    val indexBufferRef: GPUFrameBufferRef,
    val uniformBufferRef: GPUFrameBufferRef,
    val uniformAlignmentBytes: Long,
    val vertexByteSize: Long,
    val indexByteSize: Long,
    val uniformByteSize: Long,
    val vertexContentHash: String,
    val indexContentHash: String,
    val uniformContentHash: String,
    slices: List<GPUPreparedColorGlyphBufferSlice>,
    vertexBytes: ByteArray,
    indexBytes: ByteArray,
    uniformBytes: ByteArray,
) {
    private val vertexUploadSnapshot = vertexBytes.copyOf()
    private val indexUploadSnapshot = indexBytes.copyOf()
    private val uniformUploadSnapshot = uniformBytes.copyOf()
    val slices: List<GPUPreparedColorGlyphBufferSlice> = immutableList(slices)
    val resourceGeneration: Long = planArtifactKey.generation.value.toLong()
    val memoryAllocations: List<GPUFrameMemoryAllocation> = immutableList(
        listOf(
            bufferAllocation("vertices", vertexByteSize),
            bufferAllocation("indices", indexByteSize),
            bufferAllocation("uniforms", uniformByteSize),
        ),
    )
    val preparationRequests: List<GPUResourcePreparationRequest> = immutableList(
        listOf(
            bufferPreparation(
                resource = vertexBufferRef,
                role = GPUFrameResourceRole.VertexData,
                usage = GPUFrameResourceUsage.Vertex,
                byteSize = vertexByteSize,
                alignmentBytes = 4L,
                label = "vertices",
                contentHash = vertexContentHash,
            ),
            bufferPreparation(
                resource = indexBufferRef,
                role = GPUFrameResourceRole.IndexData,
                usage = GPUFrameResourceUsage.Index,
                byteSize = indexByteSize,
                alignmentBytes = 4L,
                label = "indices",
                contentHash = indexContentHash,
            ),
            bufferPreparation(
                resource = uniformBufferRef,
                role = GPUFrameResourceRole.UniformData,
                usage = GPUFrameResourceUsage.Uniform,
                byteSize = uniformByteSize,
                alignmentBytes = uniformAlignmentBytes,
                label = "uniforms",
                contentHash = uniformContentHash,
            ),
        ),
    )

    init {
        require(resourceGeneration >= 0L)
        require(uniformAlignmentBytes > 0L &&
            uniformAlignmentBytes and (uniformAlignmentBytes - 1L) == 0L
        )
        require(vertexByteSize == vertexUploadSnapshot.size.toLong())
        require(indexByteSize == indexUploadSnapshot.size.toLong())
        require(uniformByteSize == uniformUploadSnapshot.size.toLong())
        require(vertexContentHash == vertexUploadSnapshot.sha256Hex())
        require(indexContentHash == indexUploadSnapshot.sha256Hex())
        require(uniformContentHash == uniformUploadSnapshot.sha256Hex())
        require(this.slices.isNotEmpty())
        require(this.slices.map(GPUPreparedColorGlyphBufferSlice::commandIdValue).distinct().size ==
            this.slices.size
        )
        require(this.slices.all { slice ->
            slice.uniformOffsetBytes % uniformAlignmentBytes == 0L &&
                Math.addExact(slice.vertexOffsetBytes, slice.vertexSizeBytes) <= vertexByteSize &&
                Math.addExact(slice.indexOffsetBytes, slice.indexSizeBytes) <= indexByteSize &&
                Math.addExact(slice.uniformOffsetBytes, slice.uniformSizeBytes) <= uniformByteSize
        })
    }

    fun vertexBytesForUpload(): ByteArray = vertexUploadSnapshot.copyOf()
    fun indexBytesForUpload(): ByteArray = indexUploadSnapshot.copyOf()
    fun uniformBytesForUpload(): ByteArray = uniformUploadSnapshot.copyOf()

    /**
     * Pure WebGPU packing validation used before any native allocation.
     *
     * Vertex/index slabs are exact contiguous 4-byte partitions. Uniform slices retain only the
     * padding mandated by the sealed device alignment and the final slice closes each slab.
     */
    internal fun hasCanonicalNativePacking(): Boolean {
        if (vertexByteSize <= 0L || vertexByteSize % 4L != 0L ||
            indexByteSize <= 0L || indexByteSize % 4L != 0L ||
            uniformByteSize <= 0L
        ) {
            return false
        }
        var expectedVertexOffset = 0L
        var expectedIndexOffset = 0L
        var expectedUniformOffset = 0L
        return try {
            slices.forEach { slice ->
                expectedUniformOffset =
                    alignUpPreparedText(expectedUniformOffset, uniformAlignmentBytes)
                if (slice.vertexOffsetBytes != expectedVertexOffset ||
                    slice.vertexOffsetBytes % 4L != 0L ||
                    slice.vertexSizeBytes % 4L != 0L ||
                    slice.indexOffsetBytes != expectedIndexOffset ||
                    slice.indexOffsetBytes % 4L != 0L ||
                    slice.indexSizeBytes % 4L != 0L ||
                    slice.uniformOffsetBytes != expectedUniformOffset ||
                    slice.uniformOffsetBytes % uniformAlignmentBytes != 0L ||
                    slice.indexSizeBytes !=
                    Math.multiplyExact(slice.indexCount.toLong(), Int.SIZE_BYTES.toLong())
                ) {
                    return false
                }
                expectedVertexOffset =
                    Math.addExact(slice.vertexOffsetBytes, slice.vertexSizeBytes)
                expectedIndexOffset =
                    Math.addExact(slice.indexOffsetBytes, slice.indexSizeBytes)
                expectedUniformOffset =
                    Math.addExact(slice.uniformOffsetBytes, slice.uniformSizeBytes)
            }
            expectedVertexOffset == vertexByteSize &&
                expectedIndexOffset == indexByteSize &&
                expectedUniformOffset == uniformByteSize &&
                vertexContentHash == vertexUploadSnapshot.sha256Hex() &&
                indexContentHash == indexUploadSnapshot.sha256Hex() &&
                uniformContentHash == uniformUploadSnapshot.sha256Hex()
        } catch (_: ArithmeticException) {
            false
        }
    }

    /** Exact immutable equality for two instances carrying the same artifact-key authority. */
    internal fun sameCanonicalNativePlanAs(
        other: GPUPreparedColorGlyphBufferPlan,
    ): Boolean =
        planArtifactKey == other.planArtifactKey &&
            vertexBufferRef == other.vertexBufferRef &&
            indexBufferRef == other.indexBufferRef &&
            uniformBufferRef == other.uniformBufferRef &&
            uniformAlignmentBytes == other.uniformAlignmentBytes &&
            vertexByteSize == other.vertexByteSize &&
            indexByteSize == other.indexByteSize &&
            uniformByteSize == other.uniformByteSize &&
            vertexContentHash == other.vertexContentHash &&
            indexContentHash == other.indexContentHash &&
            uniformContentHash == other.uniformContentHash &&
            slices == other.slices &&
            memoryAllocations == other.memoryAllocations &&
            preparationRequests.size == other.preparationRequests.size &&
            preparationRequests.zip(other.preparationRequests).all { (left, right) ->
                left.resource == right.resource &&
                    left.descriptor == right.descriptor &&
                    left.role == right.role &&
                    left.usages == right.usages &&
                    left.lifetime == right.lifetime &&
                    left.byteSize == right.byteSize &&
                    left.diagnosticLabel == right.diagnosticLabel
            } &&
            vertexUploadSnapshot.contentEquals(other.vertexUploadSnapshot) &&
            indexUploadSnapshot.contentEquals(other.indexUploadSnapshot) &&
            uniformUploadSnapshot.contentEquals(other.uniformUploadSnapshot)

    private fun bufferAllocation(label: String, byteSize: Long) = GPUFrameMemoryAllocation(
        label = "prepared-color-glyph.$label." +
            "${planArtifactKey.artifactID.value}.${planArtifactKey.generation.value}",
        category = GPUFrameMemoryCategory.ReusableScratch,
        bytes = byteSize,
        resourceKind = GPUFrameMemoryResourceKind.Buffer,
        extent = null,
    )

    private fun bufferPreparation(
        resource: GPUFrameBufferRef,
        role: GPUFrameResourceRole,
        usage: GPUFrameResourceUsage,
        byteSize: Long,
        alignmentBytes: Long,
        label: String,
        contentHash: String,
    ) = GPUResourcePreparationRequest(
        resource = resource,
        descriptor = GPUFrameBufferDescriptor(byteSize, alignmentBytes),
        role = role,
        usages = setOf(usage, GPUFrameResourceUsage.CopyDestination),
        lifetime = GPUFrameResourceLifetime.FrameLocal,
        byteSize = byteSize,
        diagnosticLabel = "prepared-color-glyph.$label.$contentHash",
    )
}

/** TextA8-only immutable facts duplicated into the Task 8/9 preflight seal. */
class GPUPreparedTextCompositePreflightSeal internal constructor(
    deviceToLocal: GPUPreparedTextDeviceToLocalAffine,
    val drawUniformBufferRef: GPUFrameBufferRef,
    val drawUniformAlignmentBytes: Long,
    val drawUniformLogicalSliceSizeBytes: Long,
    val drawUniformBufferByteSize: Long,
    val drawUniformBufferContentHash: String,
    drawUniformSlice: GPUPreparedTextDrawUniformSlice,
    val compositeSourceHash: String,
    val compositeAbiHash: String,
    val compositePipelineKey: String,
    val compositeSourceCoverageEncoding: GPUSourceCoverageEncoding,
    val clipPlan: GPUPreparedTextClipPlan,
    val coverageMaskResource: GPUFrameTargetRef? = null,
    val compositeVertexEntryPoint: String,
    val compositeFragmentEntryPoint: String,
    compositeVertexLayout: GPUPreparedTextVertexLayout,
    internal val compositeAdmissionToken: GPUPreparedTextCompositeAdmissionToken,
) {
    val deviceToLocal: GPUPreparedTextDeviceToLocalAffine = deviceToLocal.copy()
    val drawUniformSlice: GPUPreparedTextDrawUniformSlice = drawUniformSlice.copy()
    val compositeVertexLayout: GPUPreparedTextVertexLayout = GPUPreparedTextVertexLayout(
        arrayStrideBytes = compositeVertexLayout.arrayStrideBytes,
        stepMode = compositeVertexLayout.stepMode,
        attributes = compositeVertexLayout.attributes,
    )

    init {
        require(drawUniformAlignmentBytes > 0L)
        require(drawUniformLogicalSliceSizeBytes == PREPARED_TEXT_DRAW_UNIFORM_LOGICAL_BYTES)
        require(drawUniformBufferByteSize > 0L)
        require(drawUniformBufferContentHash.isNotBlank())
        require(compositeSourceHash.isNotBlank())
        require(compositeAbiHash.isNotBlank())
        require(compositePipelineKey.isNotBlank())
        require(compositeVertexEntryPoint.isNotBlank())
        require(compositeFragmentEntryPoint.isNotBlank())
        require(
            (clipPlan is GPUPreparedTextClipPlan.CoverageMask) ==
                (coverageMaskResource != null),
        )
    }
}

/** Exact handle-free packet facts shared by TextA8 and ColorGlyph preflight. */
class GPUPreparedTextPacketAuthoritySeal internal constructor(
    val commandIdValue: Int,
    val renderStepIdentity: String,
    val renderPipelineKey: String,
    val bindingLayoutHash: String,
    val uniformSlot: GPUUniformPayloadSlot?,
    val vertexSourceLabel: String,
    val targetStateHash: String,
    val scissorBoundsHash: String?,
) {
    init {
        require(commandIdValue >= 0)
        require(renderStepIdentity.isNotBlank())
        require(renderPipelineKey.isNotBlank())
        require(bindingLayoutHash.isNotBlank())
        require(vertexSourceLabel.isNotBlank())
        require(targetStateHash.isNotBlank())
        require(scissorBoundsHash == null || scissorBoundsHash.isNotBlank())
    }
}

/**
 * Passive immutable Task 8 handoff facts consumed by Task 9 preflight.
 *
 * This seal owns no validation and no native resource. It deliberately keeps
 * the gathered semantic and prepared-material facts independent from the
 * render binding so preflight can detect late substitution.
 */
class GPUPreparedTextBindingPreflightSeal(
    val semanticCanonicalHash: String,
    val atlasKey: String,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val atlasRowBytes: Int,
    val atlasGeneration: Long,
    val atlasContentHash: String,
    val pageIndex: Int,
    val instanceStrideBytes: Int,
    val firstInstance: Int,
    val instanceCount: Int,
    val instanceBufferByteSize: Long,
    val instanceBufferContentHash: String,
    val materialUniformOffsetBytes: Long,
    val materialUniformSizeBytes: Long,
    val materialKey: String,
    val materialWgslSourceHash: String,
    val materialEntryPoint: String,
    val materialAbiHash: String,
    val materialUniformContentHash: String,
    materialSampledResourceFacts: List<String>,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds,
    val clipIdentity: String,
    val blendPlanIdentity: String,
    val capabilitySnapshotHash: String,
    val textA8Composite: GPUPreparedTextCompositePreflightSeal? = null,
    val colorGlyphClip: GPUPreparedColorGlyphClipPreflightSeal? = null,
    val packetAuthority: GPUPreparedTextPacketAuthoritySeal? = null,
) {
    val materialSampledResourceFacts: List<String> =
        immutableList(materialSampledResourceFacts)

    init {
        require(semanticCanonicalHash.isNotBlank())
        require(atlasKey.isNotBlank())
        require(atlasWidth > 0 && atlasHeight > 0 && atlasRowBytes >= atlasWidth)
        require(atlasGeneration >= 0L && atlasContentHash.isNotBlank())
        require(pageIndex >= 0)
        require(instanceStrideBytes > 0)
        require(firstInstance >= 0 && instanceCount > 0)
        require(instanceBufferByteSize > 0L && instanceBufferContentHash.isNotBlank())
        require(materialUniformOffsetBytes >= 0L && materialUniformSizeBytes >= 0L)
        require(materialKey.isNotBlank())
        require(materialWgslSourceHash.isNotBlank())
        require(materialEntryPoint.isNotBlank())
        require(materialAbiHash.isNotBlank())
        require(materialUniformContentHash.isNotBlank())
        require(clipIdentity.isNotBlank())
        require(blendPlanIdentity.isNotBlank())
        require(capabilitySnapshotHash.isNotBlank())
        require(
            textA8Composite == null || colorGlyphClip == null,
        )
        require(colorGlyphClip == null || colorGlyphClip.semanticIdentity == clipIdentity)
    }
}

/** Closed ColorGlyph clip authority retained independently from the TextA8 composite ABI. */
sealed interface GPUPreparedColorGlyphClipPreflightSeal {
    val semanticIdentity: String
    val executionPlanIdentity: String

    data class NonMask(
        override val semanticIdentity: String,
        override val executionPlanIdentity: String,
        val analyticRect: GPUPreparedColorGlyphAnalyticRectClipFacts?,
    ) : GPUPreparedColorGlyphClipPreflightSeal

    data class CoverageMask(
        override val semanticIdentity: String,
        override val executionPlanIdentity: String,
        val resource: GPUFrameTargetRef,
        val orderingToken: String,
    ) : GPUPreparedColorGlyphClipPreflightSeal {
        init {
            require(orderingToken.isNotBlank())
        }
    }
}

/** Handle-free analytic rectangle values retained for native execution. */
data class GPUPreparedColorGlyphAnalyticRectClipFacts(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val scissor: GPUPixelBounds?,
    val antiAlias: Boolean,
) {
    init {
        require(listOf(left, top, right, bottom).all(Float::isFinite))
        require(right >= left && bottom >= top)
    }
}

/**
 * Semantic-owner authentication for the passive ColorGlyph clip seal. Execution must call this
 * predicate before consuming analytic rectangle facts.
 */
internal fun GPUPreparedColorGlyphClipPreflightSeal.matchesPreparedColorGlyphClip(
    packet: GPUDrawPacket,
): Boolean {
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.ColorGlyph ?: return false
    val plan = packet.clipExecutionPlan ?: return false
    if (semanticIdentity != semantic.clipIdentity ||
        executionPlanIdentity != plan.canonicalIdentity()
    ) return false
    return when (this) {
        is GPUPreparedColorGlyphClipPreflightSeal.NonMask -> when (plan) {
            is GPUClipExecutionPlan.AnalyticCoverage -> {
                val rect = plan.geometry as? GPUClipExecutionGeometry.Rect
                if (rect == null) {
                    analyticRect == null
                } else {
                    val facts = analyticRect ?: return false
                    facts.left.toRawBits() == rect.bounds.left.toRawBits() &&
                        facts.top.toRawBits() == rect.bounds.top.toRawBits() &&
                        facts.right.toRawBits() == rect.bounds.right.toRawBits() &&
                        facts.bottom.toRawBits() == rect.bounds.bottom.toRawBits() &&
                        facts.scissor == plan.scissor && facts.antiAlias == plan.antiAlias
                }
            }
            is GPUClipExecutionPlan.CoverageMask -> false
            else -> analyticRect == null
        }
        is GPUPreparedColorGlyphClipPreflightSeal.CoverageMask ->
            plan is GPUClipExecutionPlan.CoverageMask &&
                orderingToken == plan.orderingToken.value
    }
}

/** Semantic-owner predicate for one CoverageMask producer packet retained by a ColorGlyph seal. */
internal fun GPUPreparedColorGlyphClipPreflightSeal.CoverageMask
    .matchesPreparedColorGlyphCoverageMaskProducer(packet: GPUDrawPacket): Boolean {
    val plan = packet.clipExecutionPlan as? GPUClipExecutionPlan.CoverageMask ?: return false
    return packet.role == GPUDrawPacketRole.ClipProducer &&
        plan.canonicalIdentity() == executionPlanIdentity &&
        plan.orderingToken.value == orderingToken
}

/** Exact atlas and frame-global instance range consumed by one ordered prepared-text packet. */
class GPUPreparedTextRenderBinding(
    val packetId: org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID,
    val atlasResourcePlan: GPUR8FrameResourcePlan,
    val instanceBufferPlan: GPUPreparedTextInstanceBufferPlan,
    val firstInstance: Int,
    val instanceCount: Int,
    val materialUniformBufferPlan: GPUPreparedTextMaterialUniformBufferPlan?,
    val materialUniformOffsetBytes: Long,
    val materialUniformSizeBytes: Long,
    materialSampledResourcePlans: List<GPUMaterialTextureFrameResourcePlan>,
    val preflightSeal: GPUPreparedTextBindingPreflightSeal,
    val coverageMaskResource: GPUFrameTargetRef? = null,
    private val drawUniformBufferPlanOrNull: GPUPreparedTextDrawUniformBufferPlan? = null,
    private val drawUniformSliceOrNull: GPUPreparedTextDrawUniformSlice? = null,
    private val compositeProgramOrNull: GPUPreparedTextCompositeProgram? = null,
    private val colorGlyphBufferPlanOrNull: GPUPreparedColorGlyphBufferPlan? = null,
    private val colorGlyphBufferSliceOrNull: GPUPreparedColorGlyphBufferSlice? = null,
) {
    val materialSampledResourcePlans: List<GPUMaterialTextureFrameResourcePlan> =
        immutableList(materialSampledResourcePlans)
    internal val hasTextA8Composite: Boolean
        get() = compositeProgramOrNull != null
    val drawUniformBufferPlan: GPUPreparedTextDrawUniformBufferPlan
        get() = checkNotNull(drawUniformBufferPlanOrNull) {
            "ColorGlyph binding has no TextA8 draw-uniform plan before Task 11"
        }
    val drawUniformSlice: GPUPreparedTextDrawUniformSlice
        get() = checkNotNull(drawUniformSliceOrNull) {
            "ColorGlyph binding has no TextA8 draw-uniform slice before Task 11"
        }
    val compositeProgram: GPUPreparedTextCompositeProgram
        get() = checkNotNull(compositeProgramOrNull) {
            "ColorGlyph binding has no TextA8 composite program before Task 11"
        }
    val colorGlyphBufferPlan: GPUPreparedColorGlyphBufferPlan
        get() = checkNotNull(colorGlyphBufferPlanOrNull) {
            "TextA8 binding has no ColorGlyph native buffer plan"
        }
    val colorGlyphBufferSlice: GPUPreparedColorGlyphBufferSlice
        get() = checkNotNull(colorGlyphBufferSliceOrNull) {
            "TextA8 binding has no ColorGlyph native buffer slice"
        }
    internal val hasColorGlyphBufferPlan: Boolean
        get() = colorGlyphBufferPlanOrNull != null
    internal val nativeProgram: GPUPreparedTextNativeProgramHandoff
        get() {
            val compositeSeal = checkNotNull(preflightSeal.textA8Composite) {
                "Prepared TextA8 native handoff requires a composite preflight seal"
            }
            val authenticatedComposite = checkNotNull(
                compositeProgram.authenticatedSnapshot(
                    compositeSeal.compositeAdmissionToken,
                ),
            ) {
                "Prepared TextA8 native handoff requires an authenticated composite program"
            }
            return GPUPreparedTextNativeProgramHandoff.fromAuthenticated(
                authenticatedComposite,
            )
        }

    init {
        require(firstInstance >= 0 && instanceCount > 0)
        require(
            coverageMaskResource ==
                (
                    preflightSeal.textA8Composite?.coverageMaskResource
                        ?: (
                            preflightSeal.colorGlyphClip as?
                                GPUPreparedColorGlyphClipPreflightSeal.CoverageMask
                            )
                            ?.resource
                    ),
        )
        require(
            Math.addExact(firstInstance, instanceCount) <= instanceBufferPlan.instanceCount,
        )
        require(materialUniformOffsetBytes >= 0L && materialUniformSizeBytes >= 0L)
        if (materialUniformBufferPlan == null) {
            require(materialUniformOffsetBytes == 0L && materialUniformSizeBytes == 0L)
        } else {
            require(materialUniformSizeBytes > 0L)
            require(
                materialUniformOffsetBytes % materialUniformBufferPlan.alignmentBytes == 0L,
            )
            require(
                Math.addExact(materialUniformOffsetBytes, materialUniformSizeBytes) <=
                    materialUniformBufferPlan.byteSize,
            )
        }
        require(
            listOf(
                drawUniformBufferPlanOrNull,
                drawUniformSliceOrNull,
                compositeProgramOrNull,
                preflightSeal.textA8Composite,
            ).all { it == null } ||
                listOf(
                    drawUniformBufferPlanOrNull,
                    drawUniformSliceOrNull,
                    compositeProgramOrNull,
                    preflightSeal.textA8Composite,
                ).all { it != null },
        ) {
            "Prepared TextA8 composite binding facts must be published atomically"
        }
        if (drawUniformBufferPlanOrNull != null) {
            val slice = requireNotNull(drawUniformSliceOrNull)
            require(slice.packetId == packetId)
            require(drawUniformBufferPlanOrNull.slices.single { it.packetId == packetId } == slice)
            require(preflightSeal.textA8Composite?.drawUniformSlice == slice)
        }
        require(
            (colorGlyphBufferPlanOrNull == null) == (colorGlyphBufferSliceOrNull == null),
        ) {
            "Prepared ColorGlyph native buffer facts must be published atomically"
        }
        if (colorGlyphBufferPlanOrNull != null) {
            val slice = requireNotNull(colorGlyphBufferSliceOrNull)
            require(
                colorGlyphBufferPlanOrNull.slices.single {
                    it.commandIdValue == slice.commandIdValue
                } == slice,
            )
        }
    }
}

/** Passive Task 5 handoff; native execution consumes no materials-package semantic type. */
internal class GPUPreparedTextNativeProgramHandoff private constructor(
    val wgslSource: String,
    val vertexEntryPoint: String,
    val fragmentEntryPoint: String,
    val drawUniformBinding: Int,
    val materialUniformBinding: GPUPreparedTextNativeUniformBinding?,
    materialSampledBindings: List<GPUPreparedTextNativeSampledBinding>,
    val atlasTextureBinding: Int,
    val atlasSamplerBinding: Int,
    val coverageMaskTextureBinding: Int?,
    val sourceHash: String,
    val abiHash: String,
    val targetFormatClass: String,
    val blendPlanIdentity: String,
    val fixedFunctionBlendState:
        org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState,
    val sourceCoverageEncoding: GPUSourceCoverageEncoding,
    val clipVariant: org.graphiks.kanvas.gpu.renderer.wgsl.GPUPreparedTextClipVariant,
    val vertexLayout: GPUPreparedTextVertexLayout,
    val pipelineKey: String,
) {
    val materialSampledBindings: List<GPUPreparedTextNativeSampledBinding> =
        immutableList(materialSampledBindings)

    companion object {
        internal fun fromAuthenticated(
            program: GPUPreparedTextAuthenticatedComposite,
        ): GPUPreparedTextNativeProgramHandoff {
            val fragment = program.bindingPlan.materialFragment
            return GPUPreparedTextNativeProgramHandoff(
                wgslSource = program.wgslSource,
                vertexEntryPoint = program.vertexEntryPoint,
                fragmentEntryPoint = program.fragmentEntryPoint,
                drawUniformBinding = program.bindingPlan.drawUniformBinding,
                materialUniformBinding = fragment.uniformBinding?.let { binding ->
                    GPUPreparedTextNativeUniformBinding(
                        binding = binding.binding,
                        minBindingSizeBytes = binding.minBindingSizeBytes,
                    )
                },
                materialSampledBindings = fragment.sampledBindings.map { binding ->
                    GPUPreparedTextNativeSampledBinding(
                        textureBinding = binding.textureBinding,
                        samplerBinding = binding.samplerBinding,
                    )
                },
                atlasTextureBinding = program.bindingPlan.atlasTextureBinding,
                atlasSamplerBinding = program.bindingPlan.atlasSamplerBinding,
                coverageMaskTextureBinding =
                    program.bindingPlan.coverageMaskTextureBinding,
                sourceHash = program.sourceHash,
                abiHash = program.abiHash,
                targetFormatClass = program.targetFormatClass,
                blendPlanIdentity = program.blendPlanIdentity,
                fixedFunctionBlendState = checkNotNull(program.fixedFunctionBlendState) {
                    "Prepared TextA8 native handoff requires preflight-authenticated " +
                        "fixed-function blend state"
                },
                sourceCoverageEncoding = program.sourceCoverageEncoding,
                clipVariant = program.clipVariant,
                vertexLayout = GPUPreparedTextVertexLayout(
                    arrayStrideBytes = program.vertexLayout.arrayStrideBytes,
                    stepMode = program.vertexLayout.stepMode,
                    attributes = program.vertexLayout.attributes,
                ),
                pipelineKey = program.pipelineKey,
            )
        }
    }
}

internal data class GPUPreparedTextNativeUniformBinding(
    val binding: Int,
    val minBindingSizeBytes: Int,
)

internal data class GPUPreparedTextNativeSampledBinding(
    val textureBinding: Int,
    val samplerBinding: Int,
)

/**
 * Builds a handle-free prepared frame while keeping semantic/resource authorities immutable.
 *
 * Validation and all resource planning finish before any output task collection is constructed.
 */
class GPUPreparedSurfaceFrameTaskListBuilder(
    private val readbackLayoutPlanner: GPUReadbackLayoutPlanner = GPUReadbackLayoutPlanner(),
    private val preparedTextCompositeProgramCache:
        GPUPreparedTextCompositeProgramCache = GPUPreparedTextCompositeProgramCache(),
) {
    fun build(
        request: GPUPreparedSurfaceFrameRequest,
        configuredAggregateBudgetBytes: Long = 1L shl 30,
        taskGraphLimits: GPUPreparedSurfaceTaskGraphLimits =
            GPUPreparedSurfaceTaskGraphLimits(),
    ): GPUPreparedSurfaceFrameResult {
        request.baseTaskList.tasks.filterIsInstance<GPUTask.Refused>().firstOrNull()?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it.diagnostic.atRecordingBoundary())
        }
        request.baseTaskList.diagnostics.firstOrNull(GPUDiagnostic::isTerminal)?.let {
            return GPUPreparedSurfaceFrameResult.Refused(it.atRecordingBoundary())
        }
        val baseRenders = request.baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        val semanticOnlyVertices = request.baseTaskList.tasks.filterIsInstance<GPUTask.SemanticOnly>()
        if ((baseRenders.isEmpty() && semanticOnlyVertices.isEmpty()) ||
            request.baseTaskList.tasks.any { task ->
                task !is GPUTask.Render && task !is GPUTask.SemanticOnly
            }
        ) {
            return refused(
                "invalid.recording.prepared_surface_base_tasks",
                "Prepared surfaces require one accepted render-only base task list.",
            )
        }
        if (request.targetBounds.left != 0 || request.targetBounds.top != 0 ||
            request.targetBounds.width <= 0 || request.targetBounds.height <= 0
        ) {
            return refused(
                "invalid.recording.prepared_surface_target",
                "Prepared surfaces require one non-empty zero-origin target.",
            )
        }
        if (configuredAggregateBudgetBytes <= 0L) {
            return refused(
                "invalid.recording.prepared_surface_budget",
                "Prepared-surface aggregate budget must be positive.",
            )
        }
        val semanticOnlyVertexPackets = semanticOnlyVertices.map { task ->
            task.draw.packet.withPreparedVerticesRenderAuthority()
        }
        val combinedPackets = (
            baseRenders.flatMap(GPUTask.Render::drawPackets) +
                semanticOnlyVertexPackets
            )
        val packets = (
            if (semanticOnlyVertices.isNotEmpty()) {
                combinedPackets.sortedBy(GPUDrawPacket::originalPaintOrder)
            } else {
                combinedPackets
            }
            )
            .map { packet ->
                val semantic = request.semanticsByCommandId[packet.commandIdValue]
                if (semantic is GPUDrawSemanticPayload.ColorGlyph &&
                    packet.hasPendingColorGlyphRecordingAuthority()
                ) {
                    packet.withPreparedColorGlyphAuthority(semantic)
                } else {
                    packet
                }
            }
        val commandIds = packets.map(GPUDrawPacket::commandIdValue)
        val semanticRefs = request.semanticsByCommandId.values
            .map { semantic -> semantic.payloadRef.commandIdValue }
        if (commandIds.distinct().size != commandIds.size ||
            packets.map(GPUDrawPacket::originalPaintOrder).distinct().size != packets.size ||
            commandIds.toSet() != request.semanticsByCommandId.keys ||
            semanticRefs.distinct().size != semanticRefs.size ||
            semanticRefs.toSet() != request.semanticsByCommandId.keys ||
            request.semanticsByCommandId.any { (commandId, semantic) ->
                semantic.payloadRef.commandIdValue != commandId
            }
        ) {
            return refused(
                "invalid.recording.prepared_surface_semantics",
                "Every accepted packet requires one unique semantic with the identical command identity.",
            )
        }
        val unsupported = request.semanticsByCommandId.values.firstOrNull {
            it !is GPUDrawSemanticPayload.CorePrimitive &&
                it !is GPUDrawSemanticPayload.SampledImage &&
                it !is GPUDrawSemanticPayload.TextA8 &&
                it !is GPUDrawSemanticPayload.ColorGlyph &&
                it !is GPUDrawSemanticPayload.Vertices
        }
        if (unsupported != null) {
            return refused(
                "unsupported.recording.prepared_surface_semantic_type",
                "Prepared surfaces accept only CorePrimitive, SampledImage, TextA8, ColorGlyph, " +
                    "and Vertices semantics.",
            )
        }
        val hasPreparedText = request.semanticsByCommandId.values.any {
            it is GPUDrawSemanticPayload.TextA8 || it is GPUDrawSemanticPayload.ColorGlyph
        }
        if (hasPreparedText && request.capabilities.limits == null) {
            return refused(
                "unsupported.recording.prepared_surface_limits_unavailable",
                "Prepared text requires observed device limits.",
            )
        }
        val invalidPreparedText = request.semanticsByCommandId.values.firstOrNull { semantic ->
            when (semantic) {
                is GPUDrawSemanticPayload.TextA8 ->
                    !semantic.hasCanonicalHashIntegrity() ||
                        semantic.targetBounds != request.targetBounds ||
                        semantic.atlasGeneration.value.toLong() != semantic.atlas.generation ||
                        semantic.instances.isEmpty()
                is GPUDrawSemanticPayload.ColorGlyph ->
                    !semantic.hasCanonicalHashIntegrity() ||
                        semantic.targetBounds != request.targetBounds ||
                        semantic.instances.isEmpty() ||
                        semantic.material == null
                else -> false
            }
        }
        if (invalidPreparedText != null) {
            return refused(
                "invalid.recording.prepared_text_semantic",
                "Prepared text requires one canonical immutable payload with exact target and instances.",
            )
        }
        val invalidImage = request.semanticsByCommandId.values
            .filterIsInstance<GPUDrawSemanticPayload.SampledImage>()
            .firstOrNull { semantic ->
                semantic.artifact.colorInterpretation !=
                    GPUColorInterpretation.EncodedPremulSrgb.value ||
                    semantic.targetBounds != request.targetBounds ||
                    !semantic.hasCanonicalHashIntegrity()
            }
        if (invalidImage != null) {
            return refused(
                "invalid.recording.prepared_image_semantic",
                "Prepared images require canonical EncodedPremulSrgb artifact and target authority.",
            )
        }
        val invalidImageScissorAuthority = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.SampledImage ?: return@firstOrNull false
            val hasScissor = semantic.scissorBounds != semantic.targetBounds
            val expectedHash = if (hasScissor) {
                preparedImageScissorAuthority(semantic.scissorBounds)
            } else {
                null
            }
            val expectedCoverage = if (hasScissor) {
                GPUClipCoveragePlan.Scissor(
                    org.graphiks.kanvas.gpu.renderer.clips.GPUBounds(
                        semantic.scissorBounds.left.toFloat(),
                        semantic.scissorBounds.top.toFloat(),
                        semantic.scissorBounds.right.toFloat(),
                        semantic.scissorBounds.bottom.toFloat(),
                    ),
                )
            } else {
                GPUClipCoveragePlan.NoClip
            }
            val expectedExecution = if (hasScissor) {
                GPUClipExecutionPlan.ScissorOnly(semantic.scissorBounds)
            } else {
                GPUClipExecutionPlan.NoClip
            }
            packet.scissorBoundsHash != expectedHash ||
                packet.clipCoveragePlan != expectedCoverage ||
                packet.clipExecutionPlan != expectedExecution
        }
        if (invalidImageScissorAuthority != null) {
            return refused(
                "invalid.recording.prepared_image_scissor_authority",
                "Prepared-image packet clip authorities must exactly match the immutable semantic.",
            )
        }
        val invalidVertices = request.semanticsByCommandId.values
            .filterIsInstance<GPUDrawSemanticPayload.Vertices>()
            .firstOrNull { semantic ->
                !semantic.hasCanonicalHashIntegrity() ||
                    semantic.targetBounds != request.targetBounds
            }
        if (invalidVertices != null) {
            return refused(
                "invalid.recording.prepared_vertices_semantic",
                "Prepared vertices require one canonical immutable payload with the exact target.",
            )
        }

        val allCore = request.semanticsByCommandId.values
            .all { it is GPUDrawSemanticPayload.CorePrimitive }
        if (allCore) {
            @Suppress("UNCHECKED_CAST")
            val coreSemantics = request.semanticsByCommandId as
                Map<Int, GPUDrawSemanticPayload.CorePrimitive>
            val coreBase = when (
                val prepared = prepareCoreAuthorityBaseTaskList(
                    baseTaskList = request.baseTaskList,
                    packets = packets,
                    corePackets = packets,
                )
            ) {
                is CoreAuthorityBaseAssembly.Prepared -> prepared.taskList
                is CoreAuthorityBaseAssembly.Refused ->
                    return GPUPreparedSurfaceFrameResult.Refused(prepared.diagnostic)
            }
            return when (
                val core = GPUCorePrimitivePreparedFrameTaskListAssembler(readbackLayoutPlanner).build(
                    GPUCorePrimitivePreparedFrameRequest(
                        baseTaskList = coreBase,
                        capabilities = request.capabilities,
                        target = request.target,
                        targetBounds = request.targetBounds,
                        semanticsByCommandId = coreSemantics,
                        readbackRequestId = request.readbackRequestId,
                        configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                        targetFormat = request.targetFormat,
                    ),
                )
            ) {
                is GPUCorePrimitivePreparedFrameResult.Recorded ->
                    GPUPreparedSurfaceFrameResult.Recorded(core.taskList)
                is GPUCorePrimitivePreparedFrameResult.Refused ->
                    GPUPreparedSurfaceFrameResult.Refused(core.diagnostic)
            }
        }
        val invalidRoutePacket = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            when (semantic) {
                is GPUDrawSemanticPayload.SampledImage ->
                    packet.renderStepId.value != semantic.payloadRef.renderStepIdentity ||
                        semantic.payloadRef.renderStepIdentity != "image.draw.texture_upload"
                is GPUDrawSemanticPayload.TextA8,
                is GPUDrawSemanticPayload.ColorGlyph,
                is GPUDrawSemanticPayload.Vertices,
                -> packet.renderStepId.value != semantic.payloadRef.renderStepIdentity
                else -> false
            }
        }
        if (invalidRoutePacket != null) {
            return refused(
                "invalid.recording.prepared_surface_route_identity",
                "Prepared-surface packets and semantics must retain one identical closed render route.",
            )
        }
        val invalidColorGlyphAuthority = packets.firstNotNullOfOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.ColorGlyph ?: return@firstNotNullOfOrNull null
            preparedColorGlyphPacketAuthorityRefusal(packet, semantic)
        }
        if (invalidColorGlyphAuthority != null) {
            return refused(
                invalidColorGlyphAuthority.code,
                invalidColorGlyphAuthority.message,
            )
        }
        val invalidCoreAuthority = packets.firstOrNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.CorePrimitive ?: return@firstOrNull false
            val coverage = packet.clipCoveragePlan
            val execution = packet.clipExecutionPlan
            coverage == null || execution == null ||
                coverage != semantic.clipCoveragePlan ||
                semantic.clipExecutionPlanIdentity?.let { identity ->
                    execution.canonicalIdentity() != identity
                } == true
        }
        if (invalidCoreAuthority != null) {
            return refused(
                "invalid.recording.prepared_surface_core_authority",
                "Mixed prepared surfaces require exact packet clip coverage and execution authorities.",
            )
        }

        val imagePackets = packets.filter { packet ->
            request.semanticsByCommandId.getValue(packet.commandIdValue) is
                GPUDrawSemanticPayload.SampledImage
        }
        val imageSemantics = imagePackets.associate { packet ->
            packet.commandIdValue to
                request.semanticsByCommandId.getValue(packet.commandIdValue)
                    as GPUDrawSemanticPayload.SampledImage
        }
        val recordedImageUploads = imageSemantics.values
            .groupBy { semantic -> semantic.artifact.key }
            .toSortedMap(compareBy { key -> key.value })
            .values
            .mapIndexed { index, semantics ->
                val artifact = semantics.first().artifact
                if (semantics.any { it.artifact.contentHash != artifact.contentHash }) {
                    return refused(
                        "invalid.recording.prepared_image_artifact_identity",
                        "One prepared-image artifact key must identify one exact immutable byte artifact.",
                    )
                }
                GPURecordedImageUpload(
                    taskId = GPUTaskID(
                        "task.prepared-surface.image-upload.${request.baseTaskList.frameId.value}.$index",
                    ),
                    resources = buildImageFrameResourcePlanFromBindings(
                        artifact = artifact,
                        bindingInputs = semantics.map { semantic ->
                            GPUImageBindingInput(
                                packetId = packetForSemantic(packets, semantic).packetId.value,
                                sampling = semantic.sampling,
                            )
                        },
                        bindingLayoutHash = GPUPreparedImageBindingLayoutTopology.IDENTITY,
                        capabilities = request.capabilities,
                        frameIdentity = request.baseTaskList.frameId.value.toString(),
                    ),
                )
            }
        val imagePlans = recordedImageUploads.map(GPURecordedImageUpload::resources)
        val imagePlanByArtifactKey = imagePlans.associateBy { plan ->
            plan.bindingRequests.first().artifactKey
        }
        val imageUploadByArtifactKey = recordedImageUploads.associateBy { upload ->
            upload.resources.bindingRequests.first().artifactKey
        }
        val verticesSemantics = packets.mapNotNull { packet ->
            request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.Vertices
        }
        val recordedVerticesUploads = try {
            verticesSemantics
                .groupBy { semantic -> semantic.artifact.key }
                .toSortedMap()
                .values
                .mapIndexed { index, semantics ->
                    val artifact = semantics.first().artifact
                    if (semantics.any { semantic ->
                            semantic.artifact.vertexContentHash != artifact.vertexContentHash ||
                                semantic.artifact.indexContentHash != artifact.indexContentHash
                        }
                    ) {
                        return refused(
                            "invalid.recording.prepared_vertices_artifact_identity",
                            "One prepared-vertices artifact key must identify one exact immutable byte artifact.",
                        )
                    }
                    GPURecordedVerticesUpload(
                        resources = buildVerticesFrameResourcePlan(
                            artifact = artifact,
                            deviceGeneration =
                                request.baseTaskList.capabilitySeal.deviceGeneration.value,
                        ),
                    )
                }
        } catch (failure: IllegalArgumentException) {
            return refused(
                "unsupported.recording.prepared_vertices_resource",
                failure.message ?: "Prepared vertices resource planning failed.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.prepared_vertices_resource",
                "Prepared vertices resource planning overflowed.",
            )
        }
        val verticesPlans = recordedVerticesUploads.map(GPURecordedVerticesUpload::resources)
        val verticesStagingLayout = if (verticesPlans.isEmpty()) {
            null
        } else {
            try {
                buildVerticesStagingLayout(verticesPlans)
            } catch (failure: IllegalArgumentException) {
                return refused(
                    "unsupported.recording.prepared_vertices_staging",
                    failure.message ?: "Prepared vertices staging layout failed.",
                )
            } catch (_: ArithmeticException) {
                return refused(
                    "unsupported.recording.prepared_vertices_staging",
                    "Prepared vertices staging layout overflowed.",
                )
            }
        }
        val r8Semantics = packets.mapNotNull { packet ->
            when (val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)) {
                is GPUDrawSemanticPayload.TextA8 -> semantic
                is GPUDrawSemanticPayload.ColorGlyph -> semantic
                else -> null
            }
        }
        val textA8Inputs = mutableListOf<GPUPreparedTextDrawUniformInput>()
        packets.forEach { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.TextA8 ?: return@forEach
            val executionPlan = packet.clipExecutionPlan ?: return refused(
                "invalid.recording.prepared_text_clip_plan",
                "Prepared TextA8 requires one exact clip execution plan.",
            )
            val clipPlan = preparedTextClipPlan(executionPlan, request.targetBounds)
                ?: return refused(
                    "unsupported.recording.prepared_text_analytic_clip",
                    "Prepared TextA8 analytic clip values do not satisfy the sealed uniform ABI.",
                )
            textA8Inputs += GPUPreparedTextDrawUniformInput(
                packetId = packet.packetId,
                semantic = semantic,
                clipPlan = clipPlan,
            )
        }
        preparedTextNativeBlendDomainRefusal(
            textA8Inputs.map { input ->
                packets.single { packet -> packet.packetId == input.packetId }.blendPlan
            },
        )?.let { refusal ->
            return refused(refusal.code, refusal.message)
        }
        val compositeProgramsByPacketId =
            linkedMapOf<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID, GPUPreparedTextCompositeProgram>()
        textA8Inputs.forEach { input ->
            when (
                val composition = preparedTextCompositeProgramCache.getOrCompose(
                    material = input.semantic.material,
                    targetFormatClass = request.targetFormat.value,
                    blendPlanIdentity = input.semantic.blendPlanIdentity,
                    fixedFunctionBlendState = (
                        packets.single { packet -> packet.packetId == input.packetId }.blendPlan as?
                            org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan.FixedFunctionBlend
                        )?.state,
                    sourceCoverageEncoding =
                        checkNotNull(
                            packets.single { packet -> packet.packetId == input.packetId }
                                .blendPlan,
                        ).sourceCoverageEncoding,
                    clipVariant = input.clipPlan.variant,
                )
            ) {
                is GPUPreparedTextCompositeProgramResult.Ready ->
                    compositeProgramsByPacketId[input.packetId] = composition.program
                is GPUPreparedTextCompositeProgramResult.Refused ->
                    return refused(composition.code, composition.message)
            }
        }
        val textDrawUniformAssembly = if (textA8Inputs.isEmpty()) {
            null
        } else {
            when (
                val result = buildPreparedTextDrawUniformBufferPlan(
                    inputs = textA8Inputs,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    alignmentBytes =
                        requireNotNull(request.capabilities.limits)
                            .minUniformBufferOffsetAlignment,
                    maxBufferSize = requireNotNull(request.capabilities.limits).maxBufferSize,
                )
            ) {
                is GPUPreparedTextDrawUniformPlanResult.Prepared -> result
                is GPUPreparedTextDrawUniformPlanResult.Refused ->
                    return refused(result.code, result.message)
            }
        }
        val inconsistentR8Identity = r8Semantics
            .groupBy(GPUDrawSemanticPayload::exactR8ArtifactIdentity)
            .values
            .firstOrNull { group ->
                val expectedBytes = group.first().r8Artifact().tightBytesForUpload()
                group.drop(1).any { semantic ->
                    !semantic.r8Artifact().tightBytesForUpload().contentEquals(expectedBytes)
                }
            }
        if (inconsistentR8Identity != null) {
            return refused(
                "invalid.recording.prepared_text_r8_artifact_identity",
                "One exact prepared-text R8 identity must retain identical immutable bytes.",
            )
        }
        val recordedR8Uploads = try {
            r8Semantics
                .distinctBy(GPUDrawSemanticPayload::exactR8ArtifactIdentity)
                .mapIndexed { index, semantic ->
                    GPURecordedR8Upload(
                        taskId = GPUTaskID(
                            "task.prepared-surface.r8-upload.${request.baseTaskList.frameId.value}.$index",
                        ),
                        resources = buildR8FrameResourcePlan(
                            artifact = semantic.r8Artifact(),
                            capabilities = request.capabilities,
                            frameIdentity = request.baseTaskList.frameId.value.toString(),
                        ),
                    )
                }
        } catch (failure: IllegalArgumentException) {
            return refused(
                "unsupported.recording.prepared_text_r8_resource",
                failure.message ?: "Prepared text R8 resource planning failed.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.prepared_text_r8_resource",
                "Prepared text R8 resource planning overflowed.",
            )
        }
        val r8UploadByIdentity = recordedR8Uploads.associateBy { upload ->
            upload.resources.exactR8ArtifactIdentity()
        }
        val materialResources = r8Semantics.flatMap { semantic ->
            semantic.preparedTextMaterial().sampledResources
        } + verticesSemantics.flatMap { semantic ->
            semantic.material.sampledResources
        }
        val inconsistentMaterialResource = materialResources
            .groupBy { resource -> resource.resourceKey }
            .values
            .firstOrNull { group ->
                val first = group.first()
                group.drop(1).any { resource ->
                    resource.contentHash != first.contentHash ||
                        resource.width != first.width ||
                        resource.height != first.height ||
                        resource.samplingFilterMode != first.samplingFilterMode ||
                        resource.alphaOnly != first.alphaOnly ||
                        !resource.rgba8Bytes().contentEquals(first.rgba8Bytes())
                }
            }
        if (inconsistentMaterialResource != null) {
            return refused(
                "invalid.recording.prepared_text_material_resource_identity",
                "One prepared-material resource key must retain one exact immutable resource.",
            )
        }
        val recordedMaterialUploads = try {
            materialResources
                .distinctBy { resource -> resource.resourceKey }
                .mapIndexed { index, resource ->
                    GPURecordedMaterialUpload(
                        taskId = GPUTaskID(
                            "task.prepared-surface.material-upload." +
                                "${request.baseTaskList.frameId.value}.$index",
                        ),
                        resources = buildMaterialTextureFrameResourcePlan(
                            resourceKey = resource.resourceKey,
                            width = resource.width,
                            height = resource.height,
                            samplingFilterMode = resource.samplingFilterMode,
                            alphaOnly = resource.alphaOnly,
                            contentHash = resource.contentHash,
                            rgba8Bytes = resource.rgba8Bytes(),
                            capabilities = request.capabilities,
                            frameIdentity = request.baseTaskList.frameId.value.toString(),
                        ),
                    )
                }
        } catch (failure: IllegalArgumentException) {
            return refused(
                "unsupported.recording.prepared_text_material_resource",
                failure.message ?: "Prepared text material resource planning failed.",
            )
        } catch (_: ArithmeticException) {
            return refused(
                "unsupported.recording.prepared_text_material_resource",
                "Prepared text material resource planning overflowed.",
            )
        }
        val materialUploadByResourceKey = recordedMaterialUploads.associateBy { upload ->
            upload.resources.resourceKey
        }
        val textInstanceAssembly = if (r8Semantics.isEmpty()) {
            null
        } else {
            when (
                val assembly = buildPreparedTextInstanceAssembly(
                    semantics = r8Semantics,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    capabilities = request.capabilities,
                )
            ) {
                is PreparedTextInstanceAssemblyResult.Prepared -> assembly
                is PreparedTextInstanceAssemblyResult.Refused ->
                    return refused(assembly.code, assembly.message)
            }
        }
        val colorGlyphBufferPlans = when (
            val assembly = buildPreparedColorGlyphBufferPlans(
                semantics = r8Semantics.filterIsInstance<GPUDrawSemanticPayload.ColorGlyph>(),
                frameIdentity = request.baseTaskList.frameId.value.toString(),
                capabilities = request.capabilities,
            )
        ) {
            is PreparedColorGlyphBufferAssemblyResult.Prepared -> assembly.plansByArtifactKey
            is PreparedColorGlyphBufferAssemblyResult.Refused ->
                return refused(assembly.code, assembly.message)
        }
        val textMaterialUniformAssembly = if (r8Semantics.isEmpty()) {
            null
        } else {
            when (
                val assembly = buildPreparedTextMaterialUniformAssembly(
                    semantics = r8Semantics,
                    frameIdentity = request.baseTaskList.frameId.value.toString(),
                    capabilities = request.capabilities,
                )
            ) {
                is PreparedTextMaterialUniformAssemblyResult.Prepared -> assembly.assembly
                is PreparedTextMaterialUniformAssemblyResult.Refused ->
                    return refused(assembly.code, assembly.message)
            }
        }

        val readbackRequest = request.readbackRequestId?.let { requestId ->
            GPUFrameReadbackRequest(
                requestId = requestId,
                sourceBounds = request.targetBounds,
                pixelFormat = GPUReadbackPixelFormat.Rgba8Unorm,
                outputColorInterpretation = GPUColorInterpretation.EncodedPremulSrgb,
            )
        }
        val readbackPlan = readbackRequest?.let { frameReadback ->
            when (val plan = readbackLayoutPlanner.plan(frameReadback, request.capabilities)) {
                is GPUReadbackLayoutPlan.Planned -> plan
                is GPUReadbackLayoutPlan.Refused ->
                    return GPUPreparedSurfaceFrameResult.Refused(plan.diagnostic)
            }
        }
        val colorGlyphDestinationSnapshots = try {
            buildPreparedColorGlyphDestinationSnapshotPlans(request, packets)
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.recording.prepared_surface_destination_snapshot",
                "Prepared ColorGlyph destination-snapshot byte accounting overflowed.",
            )
        } catch (failure: IllegalArgumentException) {
            return refused(
                "invalid.recording.prepared_surface_destination_snapshot",
                failure.message ?: "Prepared ColorGlyph destination-snapshot planning failed.",
            )
        }
        val standaloneTextCoverageMaskResult = buildTextOnlyCoverageMaskProducerTopologies(
            request = request,
            packets = packets,
            baseRenders = baseRenders,
            configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
        )
        val standaloneTextCoverageMasks = when (standaloneTextCoverageMaskResult) {
            is TextCoverageMaskProducerTopologyResult.Accepted ->
                standaloneTextCoverageMaskResult.topologies
            is TextCoverageMaskProducerTopologyResult.Refused -> return refused(
                standaloneTextCoverageMaskResult.code,
                standaloneTextCoverageMaskResult.message,
            )
        }
        val enclosingAllocations = buildList {
            colorGlyphDestinationSnapshots.forEach { plan -> add(plan.allocation) }
            standaloneTextCoverageMasks.forEach { topology -> add(topology.allocation) }
            imagePlans.forEach { plan -> addAll(plan.memoryAllocations) }
            recordedR8Uploads.forEach { upload -> addAll(upload.resources.memoryAllocations) }
            recordedMaterialUploads.forEach { upload ->
                addAll(upload.resources.memoryAllocations)
            }
            if (verticesPlans.isNotEmpty()) {
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-vertices.staging",
                        category = GPUFrameMemoryCategory.ReusableScratch,
                        bytes = checkNotNull(verticesStagingLayout).totalBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
            verticesPlans.forEach { plan ->
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
            textInstanceAssembly?.let { assembly ->
                add(assembly.plan.memoryAllocation)
            }
            colorGlyphBufferPlans.values.forEach { plan ->
                addAll(plan.memoryAllocations)
            }
            textMaterialUniformAssembly?.plan?.let { plan ->
                add(plan.memoryAllocation)
            }
            textDrawUniformAssembly?.plan?.let { plan ->
                add(plan.memoryAllocation)
            }
            readbackPlan?.let { plan ->
                add(
                    GPUFrameMemoryAllocation(
                        label = "prepared-surface.readback",
                        category = GPUFrameMemoryCategory.ReadbackStaging,
                        bytes = plan.stagingDescriptor.minimumBufferBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Buffer,
                        extent = null,
                    ),
                )
            }
        }
        val conflictingEnclosingAllocation = enclosingAllocations
            .groupBy(GPUFrameMemoryAllocation::label)
            .values.firstOrNull { sameLabel -> sameLabel.distinct().size > 1 }
        if (conflictingEnclosingAllocation != null) {
            return refused(
                "invalid.recording.prepared_surface_resource_identity",
                "Prepared-surface memory allocation identities must be exact and unique.",
            )
        }
        val coreAssembly = prepareMixedCoreAuthority(
            request = request,
            packets = packets,
            configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
            additionalMemoryAllocations = enclosingAllocations.distinct(),
        )
        if (coreAssembly is MixedCoreAssembly.Refused) {
            return GPUPreparedSurfaceFrameResult.Refused(coreAssembly.diagnostic)
        }
        coreAssembly as MixedCoreAssembly.Prepared
        val sharedCoreCoverageMasks = buildSharedCoreCoverageMaskProducerTopologies(
            request = request,
            packets = packets,
            coreAssembly = coreAssembly,
        )
        val coverageMaskProducerRenders =
            sharedCoreCoverageMasks.flatMap(GPUCoverageMaskProducerTopology::producerRenders) +
                standaloneTextCoverageMasks.flatMap(GPUCoverageMaskProducerTopology::producerRenders)
        val coverageMaskConsumerUseByPlanIdentity =
            sharedCoreCoverageMasks.associate { topology ->
                topology.planIdentity to topology.consumerUse
            } +
                standaloneTextCoverageMasks.associate { topology ->
                    topology.planIdentity to topology.consumerUse
                }
        val targetBytes = try {
            Math.multiplyExact(
                Math.multiplyExact(request.targetBounds.width.toLong(), request.targetBounds.height.toLong()),
                4L,
            )
        } catch (_: ArithmeticException) {
            return refused(
                "invalid.recording.prepared_surface_budget",
                "Prepared-surface target byte size overflowed.",
            )
        }
        val memoryBudget = coreAssembly.memoryBudget ?: GPUFrameMemoryBudgetPlanner.plan(
            GPUFrameMemoryBudgetRequest(
                allocations = listOf(
                    GPUFrameMemoryAllocation(
                        label = "prepared-surface.scene-target",
                        category = GPUFrameMemoryCategory.CanonicalTarget,
                        bytes = targetBytes,
                        resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                        extent = request.targetBounds,
                    ),
                ) + enclosingAllocations,
                configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                deviceLimits = requireNotNull(request.capabilities.limits),
            ),
        )
        memoryBudget.diagnostic?.let { diagnostic ->
            return GPUPreparedSurfaceFrameResult.Refused(diagnostic)
        }

        val preparations = mutableListOf<GPUResourcePreparationRequest>()
        val sharedCoreMaskResources = sharedCoreCoverageMasks
            .map { topology -> topology.preparation.resource }
            .toSet()
        preparations += coreAssembly.preparations
            .filterNot { preparation ->
                preparation.resource == request.target ||
                    preparation.resource in sharedCoreMaskResources
            }
        preparations += sharedCoreCoverageMasks.map(
            GPUCoverageMaskProducerTopology::preparation,
        )
        preparations += standaloneTextCoverageMasks.map(
            GPUCoverageMaskProducerTopology::preparation,
        )
        preparations += corePrimitiveTargetPreparation(
            request.target,
            request.targetBounds,
            request.targetFormat,
        )
        preparations += colorGlyphDestinationSnapshots.map(
            GPUPreparedColorGlyphDestinationSnapshotPlan::preparation,
        )
        imagePlans.forEach { plan ->
            preparations += plan.preparationRequests
        }
        recordedR8Uploads.forEach { upload ->
            preparations += upload.resources.preparationRequests
        }
        recordedMaterialUploads.forEach { upload ->
            preparations += upload.resources.preparationRequests
        }
        if (verticesPlans.isNotEmpty()) {
            preparations += GPUResourcePreparationRequest(
                resource = verticesStagingRef(request.baseTaskList.frameId),
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = checkNotNull(verticesStagingLayout).totalBytes,
                    alignmentBytes = PREPARED_VERTICES_BUFFER_ALIGNMENT.toLong(),
                ),
                role = GPUFrameResourceRole.UploadStaging,
                usages = setOf(GPUFrameResourceUsage.CopySource),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = checkNotNull(verticesStagingLayout).totalBytes,
                diagnosticLabel = "prepared-vertices.staging",
            )
        }
        verticesPlans.forEach { plan ->
            preparations += GPUResourcePreparationRequest(
                resource = verticesVertexBufferRef(request.baseTaskList.frameId, plan.artifactKey),
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = plan.vertexBuffer.byteCount,
                    alignmentBytes = plan.vertexBuffer.alignment.toLong(),
                ),
                role = GPUFrameResourceRole.VertexData,
                usages = setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.Vertex,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = plan.vertexBuffer.byteCount,
                diagnosticLabel = "prepared-vertices.vertex.${plan.artifactKey}",
            )
            plan.indexBuffer?.let { index ->
                preparations += GPUResourcePreparationRequest(
                    resource = verticesIndexBufferRef(request.baseTaskList.frameId, plan.artifactKey),
                    descriptor = GPUFrameBufferDescriptor(
                        byteSize = index.byteCount,
                        alignmentBytes = index.alignment.toLong(),
                    ),
                    role = GPUFrameResourceRole.IndexData,
                    usages = setOf(
                        GPUFrameResourceUsage.CopyDestination,
                        GPUFrameResourceUsage.Index,
                    ),
                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                    byteSize = index.byteCount,
                    diagnosticLabel = "prepared-vertices.index.${plan.artifactKey}",
                )
            }
        }
        textInstanceAssembly?.let { assembly ->
            preparations += GPUResourcePreparationRequest(
                resource = assembly.plan.bufferRef,
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = assembly.plan.byteSize,
                    alignmentBytes = assembly.plan.alignmentBytes.toLong(),
                ),
                role = GPUFrameResourceRole.VertexData,
                usages = setOf(
                    GPUFrameResourceUsage.Vertex,
                    GPUFrameResourceUsage.CopyDestination,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = assembly.plan.byteSize,
                diagnosticLabel = "prepared-text.instances.${assembly.plan.contentHash}",
            )
        }
        colorGlyphBufferPlans.values.forEach { plan ->
            preparations += plan.preparationRequests
        }
        textMaterialUniformAssembly?.plan?.let { plan ->
            preparations += GPUResourcePreparationRequest(
                resource = plan.bufferRef,
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = plan.byteSize,
                    alignmentBytes = plan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(
                    GPUFrameResourceUsage.Uniform,
                    GPUFrameResourceUsage.CopyDestination,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = plan.byteSize,
                diagnosticLabel = "prepared-text.material-uniforms.${plan.contentHash}",
            )
        }
        textDrawUniformAssembly?.plan?.let { plan ->
            preparations += GPUResourcePreparationRequest(
                resource = plan.bufferRef,
                descriptor = GPUFrameBufferDescriptor(
                    byteSize = plan.byteSize,
                    alignmentBytes = plan.alignmentBytes,
                ),
                role = GPUFrameResourceRole.UniformData,
                usages = setOf(
                    GPUFrameResourceUsage.Uniform,
                    GPUFrameResourceUsage.CopyDestination,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = plan.byteSize,
                diagnosticLabel = "prepared-text.draw-uniforms.${plan.contentHash}",
            )
        }
        val readbackStaging = readbackPlan?.let {
            GPUFrameBufferRef("buffer.prepared-surface.readback.${request.baseTaskList.frameId.value}")
        }
        if (readbackPlan != null && readbackStaging != null) {
            preparations += GPUResourcePreparationRequest(
                resource = readbackStaging,
                descriptor = GPUFrameBufferDescriptor(
                    readbackPlan.stagingDescriptor.minimumBufferBytes,
                    4L,
                ),
                role = GPUFrameResourceRole.ReadbackStaging,
                usages = setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.MapRead,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = readbackPlan.stagingDescriptor.minimumBufferBytes,
                diagnosticLabel = "prepared-surface.readback",
            )
        }
        val duplicatePreparation = preparations.groupBy { it.resource }.values
            .firstOrNull { group -> group.size > 1 }
        if (duplicatePreparation != null) {
            return refused(
                "invalid.recording.prepared_surface_resource_identity",
                "Prepared-surface resource identities must be unique before task emission.",
            )
        }

        val recordingId = (baseRenders.firstOrNull() ?: semanticOnlyVertices.first()).recordingId
        val prepareTask = GPUTask.PrepareResources(
            taskId = GPUTaskID("task.prepared-surface.prepare.${request.baseTaskList.frameId.value}"),
            recordingId = recordingId,
            phase = GPUTaskPhase.Prepare,
            requests = preparations,
        )
        val uploads = recordedImageUploads.map { recordedUpload ->
            val plan = recordedUpload.resources
            GPUTask.Upload(
                taskId = recordedUpload.taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                textureResourcePlan = plan,
            )
        }
        val r8Uploads = recordedR8Uploads.map { recordedUpload ->
            val plan = recordedUpload.resources
            GPUTask.Upload(
                taskId = recordedUpload.taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                textureResourcePlan = plan,
            )
        }
        val materialUploads = recordedMaterialUploads.map { recordedUpload ->
            val plan = recordedUpload.resources
            GPUTask.Upload(
                taskId = recordedUpload.taskId,
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = plan.stagingRef,
                destination = plan.frameTextureRef,
                layout = plan.uploadTaskLayout,
                textureResourcePlan = plan,
            )
        }
        val verticesStaging = verticesStagingRef(request.baseTaskList.frameId)
        val verticesUploadByArtifactKey = linkedMapOf<String, GPUTask.Upload>()
        val verticesIndexUploadByArtifactKey = linkedMapOf<String, GPUTask.Upload>()
        recordedVerticesUploads.forEachIndexed { index, recordedUpload ->
            val plan = recordedUpload.resources
            val layout = checkNotNull(verticesStagingLayout)
            val vertexRange = layout.ranges.single { range ->
                range.artifactKey == plan.artifactKey && range.bufferKind == "vertex"
            }
            verticesUploadByArtifactKey[plan.artifactKey] = GPUTask.Upload(
                taskId = GPUTaskID(
                    "task.prepared-surface.vertices-vertex-upload." +
                        "${request.baseTaskList.frameId.value}.$index",
                ),
                recordingId = recordingId,
                phase = GPUTaskPhase.Upload,
                staging = verticesStaging,
                destination = verticesVertexBufferRef(
                    request.baseTaskList.frameId,
                    plan.artifactKey,
                ),
                layout = GPUUploadLayout(
                    sourceOffsetBytes = vertexRange.offsetBytes,
                    bytesPerRow = vertexRange.byteCount,
                    rowsPerImage = 1,
                    byteSize = vertexRange.byteCount,
                ),
            )
            plan.indexBuffer?.let { indexPlan ->
                val indexRange = layout.ranges.single { range ->
                    range.artifactKey == plan.artifactKey && range.bufferKind == "index"
                }
                verticesIndexUploadByArtifactKey[plan.artifactKey] = GPUTask.Upload(
                    taskId = GPUTaskID(
                        "task.prepared-surface.vertices-index-upload." +
                            "${request.baseTaskList.frameId.value}.$index",
                    ),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Upload,
                    staging = verticesStaging,
                    destination = verticesIndexBufferRef(
                        request.baseTaskList.frameId,
                        plan.artifactKey,
                    ),
                    layout = GPUUploadLayout(
                        sourceOffsetBytes = indexRange.offsetBytes,
                        bytesPerRow = indexRange.byteCount,
                        rowsPerImage = 1,
                        byteSize = indexRange.byteCount,
                    ),
                )
            }
        }
        val verticesUploadTasks =
            verticesUploadByArtifactKey.values + verticesIndexUploadByArtifactKey.values
        val verticesPlanByArtifactKey = recordedVerticesUploads.associate { upload ->
            upload.resources.artifactKey to upload.resources
        }
        val synthesizedSemanticOnlyRenders = semanticOnlyVertices.map { task ->
            val packet = task.draw.packet.withPreparedVerticesRenderAuthority()
            task to synthesizedSemanticOnlyBaseRender(request, task, packet)
        }
        val baseRenderByPacketId = (
            baseRenders.flatMap { render ->
                render.drawPackets.map { packet -> packet.packetId to render }
            } +
                synthesizedSemanticOnlyRenders.map { (_, render) ->
                    render.drawPackets.single().packetId to render
                }
            ).toMap()
        val preparedRenderByPacketId = baseRenderByPacketId +
            coreAssembly.renderByPacketId
        val orderedPreparedPackets = packets.flatMap { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue)
            if (semantic is GPUDrawSemanticPayload.CorePrimitive) {
                coreAssembly.packetByCommandId.getValue(packet.commandIdValue)
            } else {
                listOf(packet.withSemantic(semantic))
            }
        }
        val routeRuns = orderedPreparedPackets.contiguousRouteRuns(preparedRenderByPacketId)
        val predictedTaskCount = 1L +
            recordedImageUploads.size +
            recordedR8Uploads.size +
            recordedMaterialUploads.size +
            coverageMaskProducerRenders.size +
            routeRuns.size +
            (if (colorGlyphDestinationSnapshots.isNotEmpty()) 1L else 0L) +
            (if (readbackRequest != null) 1L else 0L) +
            verticesUploadTasks.size
        val predictedDependencyCount =
            recordedImageUploads.size.toLong() +
                recordedR8Uploads.size.toLong() +
                recordedMaterialUploads.size.toLong() +
                coverageMaskProducerRenders.size.toLong() +
                routeRuns.count { run ->
                    run.any { packet ->
                        packet.clipExecutionPlan is GPUClipExecutionPlan.CoverageMask
                    }
                }.toLong() +
                routeRuns.size.toLong() +
                if (colorGlyphDestinationSnapshots.isNotEmpty()) {
                    1L + colorGlyphDestinationSnapshots.map(
                        GPUPreparedColorGlyphDestinationSnapshotPlan::packetId,
                    ).map { packetId ->
                        routeRuns.indexOfFirst { run ->
                            run.any { packet -> packet.packetId == packetId }
                        }
                    }.distinct().size.toLong()
                } else {
                    0L
                } +
                routeRuns.sumOf { run ->
                    run.mapNotNull { packet ->
                        (packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage)
                            ?.artifact
                            ?.key
                    }.distinct().size.toLong()
                } +
                routeRuns.sumOf { run ->
                    run.mapNotNull { packet ->
                        packet.semanticPayload
                            ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                            ?.exactR8ArtifactIdentity()
                    }.distinct().size.toLong()
                } +
                routeRuns.sumOf { run ->
                    run.flatMap { packet ->
                        packet.semanticPayload
                            ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                            ?.preparedTextMaterial()
                            ?.sampledResources
                            .orEmpty()
                    }.map { resource -> resource.resourceKey }.distinct().size.toLong()
                } +
                (routeRuns.size - 1).coerceAtLeast(0).toLong() +
                (if (readbackRequest != null) 1L else 0L) +
                verticesUploadTasks.size.toLong() +
                routeRuns.sumOf { run ->
                    run.mapNotNull { packet ->
                        (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)
                            ?.artifact
                            ?.key
                    }.distinct().size.toLong()
                } +
                routeRuns.sumOf { run ->
                    run.mapNotNull { packet ->
                        (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)
                            ?.artifact
                    }.distinct().count { artifact -> artifact.indexFormat != null }.toLong()
                } +
                routeRuns.sumOf { run ->
                    run.flatMap { packet ->
                        (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)
                            ?.material
                            ?.sampledResources
                            .orEmpty()
                    }.map { resource -> resource.resourceKey }.distinct().size.toLong()
                }
        val taskGraphRefusal = taskGraphLimitRefusal(
            limits = taskGraphLimits,
            bufferAllocations = memoryBudget.allocations.count {
                it.resourceKind == GPUFrameMemoryResourceKind.Buffer
            },
            textureAllocations = memoryBudget.allocations.count {
                it.resourceKind == GPUFrameMemoryResourceKind.Texture2D
            },
            allocations = memoryBudget.allocations.size,
            tasks = predictedTaskCount,
            dependencies = predictedDependencyCount,
            instanceRanges = r8Semantics.size,
        )
        if (taskGraphRefusal != null) {
            return refused(taskGraphRefusal.code, taskGraphRefusal.message)
        }
        val renders = routeRuns.mapIndexed { index, run ->
            val original = preparedRenderByPacketId.getValue(run.first().packetId)
            val uses = if (run.first().semanticPayload is GPUDrawSemanticPayload.SampledImage) {
                run.flatMap { packet ->
                    val semantic = packet.semanticPayload as GPUDrawSemanticPayload.SampledImage
                    val plan = imagePlanByArtifactKey.getValue(semantic.artifact.key)
                    listOf(
                        GPUFrameResourceUse(
                            plan.frameTextureRef,
                            GPUFrameResourceRole.StorageData,
                            GPUFrameResourceUsage.TextureBinding,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                        GPUFrameResourceUse(
                            plan.uniformRef,
                            GPUFrameResourceRole.UniformData,
                            GPUFrameResourceUsage.Uniform,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                    )
                }.distinct()
            } else if (requireNotNull(run.first().semanticPayload).isPreparedTextSemantic()) {
                val atlasUses = run.map { packet ->
                    val plan = r8UploadByIdentity.getValue(
                        requireNotNull(packet.semanticPayload).exactR8ArtifactIdentity(),
                    ).resources
                    GPUFrameResourceUse(
                        plan.frameTextureRef,
                        GPUFrameResourceRole.GlyphAtlas,
                        GPUFrameResourceUsage.TextureBinding,
                        GPUFrameResourceLifetime.FrameLocal,
                        write = false,
                    )
                }.distinct()
                buildList {
                    addAll(atlasUses)
                    run.mapNotNull { packet ->
                        colorGlyphDestinationSnapshots.singleOrNull { plan ->
                            plan.packetId == packet.packetId
                        }
                    }.forEach { destination ->
                        add(
                            GPUFrameResourceUse(
                                destination.snapshot,
                                GPUFrameResourceRole.DestinationSnapshot,
                                GPUFrameResourceUsage.TextureBinding,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                    add(
                        GPUFrameResourceUse(
                            requireNotNull(textInstanceAssembly).plan.bufferRef,
                            GPUFrameResourceRole.VertexData,
                            GPUFrameResourceUsage.Vertex,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                    )
                    run.mapNotNull { packet ->
                        (packet.clipExecutionPlan as? GPUClipExecutionPlan.CoverageMask)
                            ?.canonicalIdentity()
                    }.distinct().forEach { planIdentity ->
                        coverageMaskConsumerUseByPlanIdentity[planIdentity]?.let(::add)
                    }
                    run.mapNotNull { packet ->
                        (packet.semanticPayload as? GPUDrawSemanticPayload.ColorGlyph)
                            ?.planArtifactKey
                    }.distinct().forEach { artifactKey ->
                        val colorPlan = colorGlyphBufferPlans.getValue(artifactKey)
                        add(
                            GPUFrameResourceUse(
                                colorPlan.vertexBufferRef,
                                GPUFrameResourceRole.VertexData,
                                GPUFrameResourceUsage.Vertex,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                        add(
                            GPUFrameResourceUse(
                                colorPlan.indexBufferRef,
                                GPUFrameResourceRole.IndexData,
                                GPUFrameResourceUsage.Index,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                        add(
                            GPUFrameResourceUse(
                                colorPlan.uniformBufferRef,
                                GPUFrameResourceRole.UniformData,
                                GPUFrameResourceUsage.Uniform,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            ),
                        )
                    }
                    textDrawUniformAssembly?.plan
                        ?.takeIf {
                            run.any { packet ->
                                packet.semanticPayload is GPUDrawSemanticPayload.TextA8
                            }
                        }
                        ?.let { plan ->
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
                    textMaterialUniformAssembly?.plan?.let { plan ->
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
                    run.flatMap { packet ->
                        requireNotNull(packet.semanticPayload)
                            .preparedTextMaterial()
                            .sampledResources
                    }.map { resource ->
                        materialUploadByResourceKey.getValue(resource.resourceKey).resources
                    }.distinctBy { plan -> plan.resourceKey }.forEach { plan ->
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
            } else if (run.first().semanticPayload is GPUDrawSemanticPayload.Vertices) {
                run.flatMap { packet ->
                    val semantic = packet.semanticPayload as GPUDrawSemanticPayload.Vertices
                    val plan = verticesPlanByArtifactKey.getValue(semantic.artifact.key)
                    listOfNotNull(
                        GPUFrameResourceUse(
                            verticesVertexBufferRef(request.baseTaskList.frameId, plan.artifactKey),
                            GPUFrameResourceRole.VertexData,
                            GPUFrameResourceUsage.Vertex,
                            GPUFrameResourceLifetime.FrameLocal,
                            write = false,
                        ),
                        plan.indexBuffer?.let {
                            GPUFrameResourceUse(
                                verticesIndexBufferRef(
                                    request.baseTaskList.frameId,
                                    plan.artifactKey,
                                ),
                                GPUFrameResourceRole.IndexData,
                                GPUFrameResourceUsage.Index,
                                GPUFrameResourceLifetime.FrameLocal,
                                write = false,
                            )
                        },
                    )
                }.distinct()
            } else {
                run.flatMap { packet ->
                    coreAssembly.resourceUsesByCommandId[packet.commandIdValue].orEmpty()
                }.distinct()
            }
            GPUTask.Render(
                taskId = GPUTaskID(
                    "task.prepared-surface.render.${request.baseTaskList.frameId.value}.$index",
                ),
                recordingId = recordingId,
                phase = GPUTaskPhase.Render,
                target = request.target,
                loadStore = GPULoadStorePlan(
                    loadOp = if (index == 0) "clear" else "load",
                    storePlan = GPUStorePlan.Store,
                ),
                samplePlan = original.samplePlan,
                resourceUses = uses,
                provisionalSegmentKey =
                    if (requireNotNull(run.first().semanticPayload).isPreparedTextSemantic()) {
                        GPUProvisionalRenderSegmentKey(
                            "segment.prepared-surface.text." +
                                "${request.baseTaskList.frameId.value}.$index",
                        )
                    } else if (run.first().semanticPayload is GPUDrawSemanticPayload.Vertices) {
                        GPUProvisionalRenderSegmentKey(
                            "segment.prepared-surface.vertices." +
                                "${request.baseTaskList.frameId.value}.$index",
                        )
                    } else {
                        original.provisionalSegmentKey
                    },
                drawPackets = run,
                batchEligibilityByPacketId = run.associate { packet ->
                    packet.packetId to
                        preparedRenderByPacketId.getValue(packet.packetId)
                            .batchEligibilityByPacketId.getValue(packet.packetId)
                },
                sampleContinuationKey = original.sampleContinuationKey,
                depthStencilLoadStore = original.depthStencilLoadStore?.takeIf {
                    run.any { packet ->
                        packet.role == org.graphiks.kanvas.gpu.renderer.passes
                            .GPUDrawPacketRole.PathStencilProducer ||
                            packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                .GPUDrawPacketRole.PathStencilCover
                    }
                },
                preparedImageBindingsByPacketId = run.mapNotNull { packet ->
                    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage
                        ?: return@mapNotNull null
                    val binding = imagePlanByArtifactKey.getValue(semantic.artifact.key)
                        .bindingRequests.single { request ->
                            request.packetId == packet.packetId.value
                        }
                    packet.packetId to binding
                }.toMap(),
                preparedTextBindingsByPacketId = run.mapNotNull { packet ->
                    val semantic = packet.semanticPayload
                        ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                        ?: return@mapNotNull null
                    val range = requireNotNull(textInstanceAssembly)
                        .rangesByCommandId
                        .getValue(packet.commandIdValue)
                    val materialUniformRange = requireNotNull(textMaterialUniformAssembly)
                        .rangesByCommandId
                        .getValue(packet.commandIdValue)
                    val material = semantic.preparedTextMaterial()
                    val drawUniformPlan = if (semantic is GPUDrawSemanticPayload.TextA8) {
                        requireNotNull(textDrawUniformAssembly).plan
                    } else {
                        null
                    }
                    val drawUniformSlice = if (semantic is GPUDrawSemanticPayload.TextA8) {
                        requireNotNull(textDrawUniformAssembly)
                            .slicesByPacketId
                            .getValue(packet.packetId)
                    } else {
                        null
                    }
                    val compositeProgram = if (semantic is GPUDrawSemanticPayload.TextA8) {
                        compositeProgramsByPacketId.getValue(packet.packetId)
                    } else {
                        null
                    }
                    val coverageMaskResource =
                        (packet.clipExecutionPlan as? GPUClipExecutionPlan.CoverageMask)
                            ?.canonicalIdentity()
                            ?.let { identity ->
                                coverageMaskConsumerUseByPlanIdentity.getValue(identity).resource
                                    as GPUFrameTargetRef
                            }
                    val colorGlyphBufferPlan =
                        (semantic as? GPUDrawSemanticPayload.ColorGlyph)?.let { color ->
                            colorGlyphBufferPlans.getValue(color.planArtifactKey)
                        }
                    val colorGlyphBufferSlice = colorGlyphBufferPlan?.slices?.single {
                        it.commandIdValue == packet.commandIdValue
                    }
                    packet.packetId to GPUPreparedTextRenderBinding(
                        packetId = packet.packetId,
                        atlasResourcePlan = r8UploadByIdentity
                            .getValue(semantic.exactR8ArtifactIdentity())
                            .resources,
                        instanceBufferPlan = textInstanceAssembly.plan,
                        firstInstance = range.firstInstance,
                        instanceCount = range.instanceCount,
                        materialUniformBufferPlan = textMaterialUniformAssembly.plan,
                        materialUniformOffsetBytes = materialUniformRange.offsetBytes,
                        materialUniformSizeBytes = materialUniformRange.sizeBytes,
                        materialSampledResourcePlans = material.sampledResources.map { resource ->
                            materialUploadByResourceKey.getValue(resource.resourceKey).resources
                        },
                        preflightSeal = semantic.preparedTextPreflightSeal(
                            packet = packet,
                            material = material,
                            atlasResourcePlan = r8UploadByIdentity
                                .getValue(semantic.exactR8ArtifactIdentity())
                                .resources,
                            instanceBufferPlan = textInstanceAssembly.plan,
                            firstInstance = range.firstInstance,
                            instanceCount = range.instanceCount,
                            materialUniformOffsetBytes = materialUniformRange.offsetBytes,
                            materialUniformSizeBytes = materialUniformRange.sizeBytes,
                            drawUniformBufferPlan = drawUniformPlan,
                            drawUniformSlice = drawUniformSlice,
                            compositeProgram = compositeProgram,
                            clipPlan = textA8Inputs.singleOrNull { input ->
                                input.packetId == packet.packetId
                            }?.clipPlan,
                            coverageMaskResource = coverageMaskResource,
                        ),
                        coverageMaskResource = coverageMaskResource,
                        drawUniformBufferPlanOrNull = drawUniformPlan,
                        drawUniformSliceOrNull = drawUniformSlice,
                        compositeProgramOrNull = compositeProgram,
                        colorGlyphBufferPlanOrNull = colorGlyphBufferPlan,
                        colorGlyphBufferSliceOrNull = colorGlyphBufferSlice,
                    )
                }.toMap(),
            )
        }

        val dependencies = mutableListOf<GPUTaskDependency>()
        val destinationTask = colorGlyphDestinationSnapshots
            .takeIf { plans -> plans.isNotEmpty() }
            ?.let { plans ->
                val renderByPacketId = renders.flatMap { render ->
                    render.drawPackets.map { packet -> packet.packetId to render }
                }.toMap()
                GPUTask.DestinationSnapshots(
                    taskId = GPUTaskID(
                        "task.prepared-surface.destination-snapshots." +
                            request.baseTaskList.frameId.value,
                    ),
                    recordingId = recordingId,
                    phase = GPUTaskPhase.Copy,
                    payload = GPUDestinationSnapshotTaskPayload(
                        grouping = GPUDestinationSnapshotGroupingResult(
                            groups = plans.map { plan ->
                                val packet = packets.single { candidate ->
                                    candidate.packetId == plan.packetId
                                }
                                val render = renderByPacketId.getValue(plan.packetId)
                                GPUDestinationSnapshotGroup(
                                    key = GPUDestinationSnapshotGroupKey(
                                        target = GPUTargetIdentity(request.target.value),
                                        targetGeneration = plan.targetGeneration,
                                        deviceGeneration =
                                            request.baseTaskList.capabilitySeal.deviceGeneration,
                                        format = request.targetFormat,
                                        colorInterpretation =
                                            preparedSurfaceTargetColorInterpretation(
                                                request.targetFormat,
                                            ),
                                        sampleContinuation = render.sampleContinuationKey,
                                        sourceIntermediate = null,
                                    ),
                                    logicalBounds = request.targetBounds,
                                    members = listOf(
                                        GPUDestinationReadMember(
                                            commandId = packet.commandIdValue.toString(),
                                            accessIndex = plan.groupIndex,
                                            logicalBounds = request.targetBounds,
                                        ),
                                    ),
                                    copiedBytes = plan.copiedBytes,
                                    decisionDump = listOf(
                                        "prepared-color-glyph:destination-snapshot " +
                                            "packet=${packet.packetId.value}",
                                    ),
                                )
                            },
                            materializations = plans.map { plan ->
                                GPUDestinationSnapshotMaterialization.TextureCopy(
                                    groupIndex = plan.groupIndex,
                                    logicalBounds = request.targetBounds,
                                )
                            },
                            totalCopiedBytes = plans.fold(0L) { total, plan ->
                                Math.addExact(total, plan.copiedBytes)
                            },
                            refusals = emptyList(),
                            decisionDump = listOf(
                                "prepared-color-glyph:destination-copy-then-formula",
                            ),
                        ),
                        operations = plans.map { plan ->
                            val packet = packets.single { candidate ->
                                candidate.packetId == plan.packetId
                            }
                            val render = renderByPacketId.getValue(plan.packetId)
                            GPUDestinationSnapshotOperation.TextureCopy(
                                groupIndex = plan.groupIndex,
                                source = request.target,
                                snapshot = plan.snapshot,
                                logicalBounds = request.targetBounds,
                                copyLayout = plan.copyLayout,
                                consumers = listOf(
                                    GPUDestinationSnapshotConsumerRef(
                                        groupingCommandId =
                                            packet.commandIdValue.toString(),
                                        renderTaskId = render.taskId,
                                        packetId = packet.packetId,
                                        commandId =
                                            org.graphiks.kanvas.gpu.renderer.commands
                                                .GPUDrawCommandID(packet.commandIdValue),
                                    ),
                                ),
                            )
                        },
                    ),
                )
            }
        destinationTask?.let { destination ->
            dependencies += dependency(
                prepareTask.taskId,
                destination.taskId,
                "prepared-color-glyph-destination-resource-order",
                "prepared.color-glyph.prepare-before-destination-snapshot",
                "prepared-color-glyph.destination.prepare",
            )
            destination.payload.operations
                .flatMap(GPUDestinationSnapshotOperation::consumers)
                .map(GPUDestinationSnapshotConsumerRef::renderTaskId)
                .distinct()
                .forEach { renderTaskId ->
                    dependencies += dependency(
                        destination.taskId,
                        renderTaskId,
                        "prepared-color-glyph-destination-consumer-order",
                        "prepared.color-glyph.destination-snapshot-before-consumer",
                        "prepared-color-glyph.destination.consumer.$renderTaskId",
                    )
                }
        }
        uploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-image-resource-order",
                "prepared.image.prepare-before-upload",
                "prepared-image.prepare.$index",
            )
        }
        r8Uploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-text-resource-order",
                "prepared.text.prepare-before-upload",
                "prepared-text.prepare.$index",
            )
        }
        materialUploads.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-text-material-resource-order",
                "prepared.text.material-prepare-before-upload",
                "prepared-text.material-prepare.$index",
            )
        }
        verticesUploadTasks.forEachIndexed { index, upload ->
            dependencies += dependency(
                prepareTask.taskId,
                upload.taskId,
                "prepared-vertices-resource-order",
                "prepared.vertices.prepare-before-upload",
                "prepared-vertices.prepare.$index",
            )
        }
        coverageMaskProducerRenders.forEachIndexed { index, producer ->
            dependencies += dependency(
                prepareTask.taskId,
                producer.taskId,
                "prepared-surface-resource-order",
                "prepared.surface.prepare-before-clip-producer",
                "prepared-surface.clip-producer.$index",
            )
        }
        renders.forEachIndexed { index, render ->
            dependencies += dependency(
                prepareTask.taskId,
                render.taskId,
                "prepared-surface-resource-order",
                "prepared.surface.prepare-before-consumer",
                "prepared-surface.prepare.$index",
            )
            render.drawPackets
                .mapNotNull { packet -> packet.semanticPayload as? GPUDrawSemanticPayload.SampledImage }
                .map { semantic -> imageUploadByArtifactKey.getValue(semantic.artifact.key).taskId }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-image-resource-order",
                        "prepared.image.upload-before-consumer",
                        "prepared-image.consumer.${dependencies.size}",
                    )
                }
            render.drawPackets
                .mapNotNull { packet ->
                    packet.semanticPayload?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                }
                .map { semantic ->
                    r8UploadByIdentity.getValue(semantic.exactR8ArtifactIdentity()).taskId
                }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-text-resource-order",
                        "prepared.text.upload-before-consumer",
                        "prepared-text.consumer.${dependencies.size}",
                    )
                }
            render.drawPackets
                .flatMap { packet ->
                    packet.semanticPayload
                        ?.takeIf(GPUDrawSemanticPayload::isPreparedTextSemantic)
                        ?.preparedTextMaterial()
                        ?.sampledResources
                        .orEmpty()
                }
                .map { resource ->
                    materialUploadByResourceKey.getValue(resource.resourceKey).taskId
                }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-text-material-resource-order",
                        "prepared.text.material-upload-before-consumer",
                        "prepared-text.material-consumer.${dependencies.size}",
                    )
                }
            render.drawPackets
                .mapNotNull { packet ->
                    packet.semanticPayload as? GPUDrawSemanticPayload.Vertices
                }
                .map { semantic -> semantic.artifact.key }
                .distinct()
                .forEach { artifactKey ->
                    val verticesPlan = verticesPlanByArtifactKey.getValue(artifactKey)
                    verticesUploadByArtifactKey.getValue(artifactKey).let { vertexUpload ->
                        dependencies += dependency(
                            vertexUpload.taskId,
                            render.taskId,
                            "prepared-vertices-resource-order",
                            "prepared.vertices.upload-before-consumer",
                            verticesPlan.uploadBeforeUseToken,
                        )
                    }
                    verticesIndexUploadByArtifactKey[artifactKey]?.let { indexUpload ->
                        dependencies += dependency(
                            indexUpload.taskId,
                            render.taskId,
                            "prepared-vertices-resource-order",
                            "prepared.vertices.upload-before-consumer",
                            verticesPlan.uploadBeforeUseToken,
                        )
                    }
                }
            render.drawPackets
                .flatMap { packet ->
                    (packet.semanticPayload as? GPUDrawSemanticPayload.Vertices)
                        ?.material
                        ?.sampledResources
                        .orEmpty()
                }
                .map { resource ->
                    materialUploadByResourceKey.getValue(resource.resourceKey).taskId
                }
                .distinct()
                .forEach { uploadTaskId ->
                    dependencies += dependency(
                        uploadTaskId,
                        render.taskId,
                        "prepared-vertices-material-resource-order",
                        "prepared.vertices.material-upload-before-consumer",
                        "prepared-vertices.material-consumer.${dependencies.size}",
                    )
                }
            val coverageMaskIdentities = render.drawPackets.mapNotNull { packet ->
                (packet.clipExecutionPlan as? GPUClipExecutionPlan.CoverageMask)
                    ?.canonicalIdentity()
            }.distinct()
            coverageMaskIdentities.forEach { identity ->
                val producer = coverageMaskProducerRenders.singleOrNull {
                    requireNotNull(it.drawPackets.firstOrNull()?.clipExecutionPlan)
                        .canonicalIdentity() == identity
                }
                if (producer != null) {
                    val plan = producer.drawPackets.first().clipExecutionPlan
                        as GPUClipExecutionPlan.CoverageMask
                    dependencies += dependency(
                        producer.taskId,
                        render.taskId,
                        "clip-producer-consumer",
                        "preserve.core-primitive.clip.producer-before-consumer",
                        plan.orderingToken.value,
                    )
                }
            }
        }
        renders.zipWithNext().forEachIndexed { index, (from, to) ->
            dependencies += dependency(
                from.taskId,
                to.taskId,
                "prepared-scene-order",
                "preserve.prepared-scene.order",
                "prepared-surface.paint.$index",
            )
        }
        val tasks = mutableListOf<GPUTask>(prepareTask)
        tasks += uploads
        tasks += r8Uploads
        tasks += materialUploads
        tasks += verticesUploadTasks
        tasks += coverageMaskProducerRenders
        destinationTask?.let(tasks::add)
        tasks += renders
        if (readbackRequest != null && readbackStaging != null) {
            val readbackTask = GPUTask.Readback(
                taskId = GPUTaskID("task.prepared-surface.readback.${request.baseTaskList.frameId.value}"),
                recordingId = recordingId,
                phase = GPUTaskPhase.Readback,
                source = request.target,
                staging = readbackStaging,
                request = readbackRequest,
            )
            tasks += readbackTask
            dependencies += dependency(
                renders.last().taskId,
                readbackTask.taskId,
                "prepared-surface-readback-order",
                "prepared.surface.render-before-readback",
                "prepared-surface.readback",
            )
        }
        val colorDiagnostic = GPUDiagnostic(
            code = GPUDiagnosticCode("info.recording.prepared_image_color_contract"),
            domain = GPUDiagnosticDomain.Color,
            severity = GPUDiagnosticSeverity.Info,
            message =
                "Prepared color images upload straight encoded sRGB bytes through an sRGB source " +
                    "texture and shade as linear-premultiplied values into the declared target.",
            facts = mapOf(
                "image.upload.format" to "RGBA8UnormSrgb",
                "image.upload.encoding" to "StraightEncodedSrgb",
                "image.upload.interpretation" to "StraightEncodedSrgb",
                "image.target.format" to request.targetFormat.value,
                "image.shader.interpretation" to "LinearPremul",
                "image.attachment.srgbConversion" to
                    (request.targetFormat == GPUColorFormat.RGBA8UnormSrgb).toString(),
            ),
        )
        val diagnostics = if (imageSemantics.isEmpty()) {
            request.baseTaskList.diagnostics
        } else {
            request.baseTaskList.diagnostics + colorDiagnostic
        }
        return GPUPreparedSurfaceFrameResult.Recorded(
            GPUTaskList(
                frameId = request.baseTaskList.frameId,
                capabilitySeal = request.baseTaskList.capabilitySeal,
                recordingSeals = request.baseTaskList.recordingSeals,
                expectedReplayKeyHash = request.baseTaskList.expectedReplayKeyHash,
                tasks = tasks,
                dependencies = dependencies.distinct(),
                phaseOrder = request.baseTaskList.phaseOrder,
                memoryBudget = memoryBudget,
                diagnostics = diagnostics,
            ),
        )
    }

    private fun prepareCoreAuthorityBaseTaskList(
        baseTaskList: GPUTaskList,
        packets: List<GPUDrawPacket>,
        corePackets: List<GPUDrawPacket>,
    ): CoreAuthorityBaseAssembly {
        val baseRenders = baseTaskList.tasks.filterIsInstance<GPUTask.Render>()
        val baseRenderByPacketId = baseRenders
            .flatMap { render -> render.drawPackets.map { packet -> packet.packetId to render } }
            .toMap()
        if (corePackets.any { packet ->
                baseRenderByPacketId.getValue(packet.packetId).compositeMembership != null
            }
        ) {
            return CoreAuthorityBaseAssembly.Refused(
                diagnostic(
                    "unsupported.recording.prepared_surface_core_composite_membership",
                    "Prepared Core authority does not erase composite render membership.",
                ),
            )
        }
        val corePacketById = corePackets.associateBy(GPUDrawPacket::packetId)
        val coreRuns = mutableListOf<
            MutableList<Pair<GPUDrawPacket, GPUTask.Render>>
            >()
        packets.forEach { packet ->
            val preparedPacket = corePacketById[packet.packetId]
            if (preparedPacket == null) {
                coreRuns.add(mutableListOf())
                return@forEach
            }
            val base = baseRenderByPacketId.getValue(packet.packetId)
            val current = coreRuns.lastOrNull()?.takeIf { run -> run.isNotEmpty() }
            val firstBase = current?.firstOrNull()?.second
            val previousBase = current?.lastOrNull()?.second
            val firstPacket = current?.firstOrNull()?.first
            val mayContinue = when {
                firstBase == null || previousBase == null || firstPacket == null -> false
                base === previousBase -> true
                else ->
                    base.recordingId == firstBase.recordingId &&
                        base.target == firstBase.target &&
                        base.samplePlan == firstBase.samplePlan &&
                        base.sampleContinuationKey == firstBase.sampleContinuationKey &&
                        base.depthStencilLoadStore == firstBase.depthStencilLoadStore &&
                        base.provisionalSegmentKey == firstBase.provisionalSegmentKey &&
                        base.compositeMembership == null &&
                        firstBase.compositeMembership == null &&
                        preparedPacket.targetStateHash == firstPacket.targetStateHash &&
                        previousBase.loadStore.storePlan == GPUStorePlan.Store &&
                        base.loadStore.loadOp == "load" &&
                        base.loadStore.clearColorLabel == null &&
                        base.resourceUses == firstBase.resourceUses
            }
            if (!mayContinue) {
                coreRuns.add(mutableListOf())
            }
            coreRuns.last().add(preparedPacket to base)
        }
        val coreRenders = coreRuns.filter { run -> run.isNotEmpty() }.mapIndexed { index, run ->
            val firstBase = run.first().second
            val lastBase = run.last().second
            val packetsForRender = run.map { (packet, _) -> packet }
            GPUTask.Render(
                taskId = GPUTaskID("task.prepared-surface.core-base.$index"),
                recordingId = firstBase.recordingId,
                phase = GPUTaskPhase.Render,
                target = firstBase.target,
                loadStore = firstBase.loadStore.copy(
                    storePlan = lastBase.loadStore.storePlan,
                ),
                samplePlan = firstBase.samplePlan,
                resourceUses = run.flatMap { (_, render) -> render.resourceUses }.distinct(),
                provisionalSegmentKey = firstBase.provisionalSegmentKey,
                drawPackets = packetsForRender,
                batchEligibilityByPacketId = packetsForRender.associate { packet ->
                    packet.packetId to
                        baseRenderByPacketId.getValue(packet.packetId)
                            .batchEligibilityByPacketId.getValue(packet.packetId)
                },
                sampleContinuationKey = firstBase.sampleContinuationKey,
                depthStencilLoadStore = firstBase.depthStencilLoadStore,
            )
        }
        return CoreAuthorityBaseAssembly.Prepared(
            GPUTaskList(
                frameId = baseTaskList.frameId,
                capabilitySeal = baseTaskList.capabilitySeal,
                recordingSeals = baseTaskList.recordingSeals,
                expectedReplayKeyHash = baseTaskList.expectedReplayKeyHash,
                tasks = coreRenders,
                dependencies = emptyList(),
                phaseOrder = baseTaskList.phaseOrder,
                memoryBudget = baseTaskList.memoryBudget,
                diagnostics = baseTaskList.diagnostics,
            ),
        )
    }

    private fun prepareMixedCoreAuthority(
        request: GPUPreparedSurfaceFrameRequest,
        packets: List<GPUDrawPacket>,
        configuredAggregateBudgetBytes: Long,
        additionalMemoryAllocations: List<GPUFrameMemoryAllocation>,
    ): MixedCoreAssembly {
        val corePackets = packets.mapNotNull { packet ->
            val semantic = request.semanticsByCommandId.getValue(packet.commandIdValue) as?
                GPUDrawSemanticPayload.CorePrimitive ?: return@mapNotNull null
            packet.withSemantic(semantic)
        }
        if (corePackets.isEmpty()) {
            return MixedCoreAssembly.Prepared(
                packetByCommandId = emptyMap(),
                resourceUsesByCommandId = emptyMap(),
                preparations = emptyList(),
                memoryBudget = null,
            )
        }
        val coreBase = when (
            val prepared = prepareCoreAuthorityBaseTaskList(
                baseTaskList = request.baseTaskList,
                packets = packets,
                corePackets = corePackets,
            )
        ) {
            is CoreAuthorityBaseAssembly.Prepared -> prepared.taskList
            is CoreAuthorityBaseAssembly.Refused ->
                return MixedCoreAssembly.Refused(prepared.diagnostic)
        }
        val coreSemantics = request.semanticsByCommandId.mapNotNull { (commandId, semantic) ->
            (semantic as? GPUDrawSemanticPayload.CorePrimitive)?.let { commandId to it }
        }.toMap()
        return when (
            val result = GPUCorePrimitivePreparedFrameTaskListAssembler(readbackLayoutPlanner).build(
                GPUCorePrimitivePreparedFrameRequest(
                    baseTaskList = coreBase,
                    capabilities = request.capabilities,
                    target = request.target,
                    targetBounds = request.targetBounds,
                    semanticsByCommandId = coreSemantics,
                    readbackRequestId = null,
                    configuredAggregateBudgetBytes = configuredAggregateBudgetBytes,
                    targetFormat = request.targetFormat,
                ),
                additionalMemoryAllocations = additionalMemoryAllocations,
            )
        ) {
            is GPUCorePrimitivePreparedFrameResult.Refused ->
                MixedCoreAssembly.Refused(result.diagnostic)
            is GPUCorePrimitivePreparedFrameResult.Recorded -> {
                val renders = result.taskList.tasks.filterIsInstance<GPUTask.Render>()
                val coverageMaskProducerRenders = renders.filter { render ->
                    render.drawPackets.isNotEmpty() &&
                        render.drawPackets.all { packet ->
                            packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                .GPUDrawPacketRole.ClipProducer
                        }
                }
                val visibleConsumerRenders = renders - coverageMaskProducerRenders.toSet()
                val consumerByCommandId = coreSemantics.keys.associateWith { commandId ->
                    visibleConsumerRenders.singleOrNull { render ->
                        render.drawPackets.any { packet -> packet.commandIdValue == commandId }
                    }
                }
                if (consumerByCommandId.values.any { it == null } ||
                    visibleConsumerRenders.any { render ->
                        render.drawPackets.none { packet -> packet.commandIdValue in coreSemantics }
                    }
                ) {
                    MixedCoreAssembly.Refused(
                        diagnostic(
                            "unsupported.recording.prepared_surface_core_producer_topology",
                            "Mixed prepared surfaces do not yet interleave core producer passes with image runs.",
                        ),
                    )
                } else {
                    MixedCoreAssembly.Prepared(
                        packetByCommandId = consumerByCommandId.mapValues { (commandId, render) ->
                            val packetsForCommand = requireNotNull(render).drawPackets.filter { packet ->
                                packet.commandIdValue == commandId
                            }
                            if (packetsForCommand.any { packet ->
                                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                                        .GPUDrawPacketRole.PathStencilProducer
                                }
                            ) {
                                packetsForCommand
                            } else {
                                packetsForCommand.map(
                                    GPUDrawPacket::withoutPreparedPathDepthStencil,
                                )
                            }
                        },
                        renderByPacketId = renders.flatMap { render ->
                            render.drawPackets.map { packet -> packet.packetId to render }
                        }.toMap(),
                        resourceUsesByCommandId = consumerByCommandId.mapValues { (commandId, render) ->
                            val exactRender = requireNotNull(render)
                            val hasPath = exactRender.drawPackets.any { packet ->
                                packet.commandIdValue == commandId &&
                                    packet.role in setOf(
                                        org.graphiks.kanvas.gpu.renderer.passes
                                            .GPUDrawPacketRole.PathStencilProducer,
                                        org.graphiks.kanvas.gpu.renderer.passes
                                            .GPUDrawPacketRole.PathStencilCover,
                                    )
                            }
                            exactRender.resourceUses.filter { use ->
                                hasPath ||
                                use.role != GPUFrameResourceRole.PathDepthStencil
                            }
                        },
                        coverageMaskProducerRenders =
                            coverageMaskProducerRenders.mergeCoverageMaskProducerPasses(),
                        coverageMaskConsumerUseByPlanIdentity =
                            coverageMaskProducerRenders.groupBy { render ->
                                requireNotNull(render.drawPackets.first().clipExecutionPlan)
                                    .canonicalIdentity()
                            }.mapValues { (_, producers) ->
                                val resources = producers.flatMap(GPUTask.Render::resourceUses)
                                    .filter { use ->
                                        use.role == GPUFrameResourceRole.ClipMask && use.write
                                    }
                                    .map(GPUFrameResourceUse::resource)
                                    .distinct()
                                require(resources.size == 1) {
                                    "One CoverageMask plan must retain one exact producer attachment"
                                }
                                GPUFrameResourceUse(
                                    resource = resources.single(),
                                    role = GPUFrameResourceRole.ClipMask,
                                    usage = GPUFrameResourceUsage.TextureBinding,
                                    lifetime = GPUFrameResourceLifetime.FrameLocal,
                                    write = false,
                                )
                            },
                        preparations = result.taskList.tasks
                            .filterIsInstance<GPUTask.PrepareResources>()
                            .flatMap(GPUTask.PrepareResources::requests),
                        memoryBudget = result.taskList.memoryBudget,
                    )
                }
            }
        }
    }

    /**
     * Handles a prepared composite frame through the real saveLayer pipeline:
     * lowerer → preflight → materialization request → native executor, aggregating the
     * materialized [GPUPassCommand] sequence (PrepareLayerTarget / RenderLayerChildren /
     * CompositeLayer) into the frame scheduling.
     */
    fun handleSaveLayer(
        scopes: Map<GPUPreparedCompositeScopeId, GPUPreparedCompositeScope>,
        rootScopeId: GPUPreparedCompositeScopeId,
        identity: String,
        capabilities: GPUPreflightCapabilities,
        context: GPUTargetPreparationContext,
        targetBudgetBytes: Long = DEFAULT_SAVE_LAYER_FRAME_BUDGET_BYTES,
    ): GPUPreparedSaveLayerFrameHandling {
        val lowering = GPUPreparedCompositeLowerer.lower(
            scopes = scopes,
            rootScopeId = rootScopeId,
            identity = identity,
            deviceGeneration = context.deviceGeneration,
        )
        if (lowering is GPUPreparedCompositeLowering.Refused) {
            return GPUPreparedSaveLayerFrameHandling.Refused(
                code = lowering.code,
                operationIndex = lowering.operationIndex,
                facts = lowering.facts,
            )
        }

        val compositePlan = (lowering as GPUPreparedCompositeLowering.Ready).plan
        val preflight = GPUPreparedCompositePreflight.preflight(compositePlan, capabilities)
        if (preflight is GPUPreparedCompositeLowering.Refused) {
            return GPUPreparedSaveLayerFrameHandling.Refused(
                code = preflight.code,
                operationIndex = preflight.operationIndex,
                facts = preflight.facts,
            )
        }

        val results = mutableListOf<GPUSaveLayerMaterializationResult>()
        val commands = mutableListOf<GPUPassCommand>()
        for (layer in compositePlan.layers) {
            val layerGatePlan = compositePlan.gatePlans[layer.saveRecord.scopeId.value]
                ?: return GPUPreparedSaveLayerFrameHandling.Refused(
                    code = GPUPreparedCompositeRefusalCodes.LAYER_GATE_MISSING,
                    operationIndex = null,
                    facts = mapOf("scopeId" to layer.saveRecord.scopeId.value),
                )
            val result = materializeSaveLayer(layer.saveRecord.scopeId, layerGatePlan, context, targetBudgetBytes)
            val refusedDecision = result.resourceDecision as? GPUResourceMaterializationDecision.Refused
            if (refusedDecision != null) {
                return GPUPreparedSaveLayerFrameHandling.Refused(
                    code = refusedDecision.diagnostic.code,
                    operationIndex = null,
                    facts = mapOf(
                        "scopeId" to layer.saveRecord.scopeId.value,
                        "targetId" to context.targetId,
                    ),
                )
            }
            results += result
            commands += result.commandStream.commands
        }

        return GPUPreparedSaveLayerFrameHandling.Ready(
            plan = compositePlan,
            results = results,
            commands = commands,
        )
    }

    private fun materializeSaveLayer(
        scopeId: GPULayerScopeID,
        gatePlan: GPUSaveLayerIsolatedTargetGatePlan,
        context: GPUTargetPreparationContext,
        targetBudgetBytes: Long,
    ): GPUSaveLayerMaterializationResult {
        val execution = gatePlan.layerPlan.execution as GPULayerExecutionPlan.IsolatedTarget
        val target = execution.target
        val generation = target.generationLabel.substringAfter(':').toLongOrNull() ?: 0L
        val request = GPUSaveLayerMaterializationRequest(
            targetId = context.targetId,
            gatePlan = gatePlan,
            parentPassId = "pass.save_layer.${scopeId.value}.composite",
            childPassId = "pass.save_layer.${scopeId.value}.children",
            childTargetStateHash = "state.child.${target.targetDescriptorHash}",
            parentTargetStateHash = "state.parent.${target.targetDescriptorHash}",
            childLoadStoreLabel = "clear:store",
            parentLoadStoreLabel = "load:store",
            deviceGeneration = context.deviceGeneration,
            expectedTargetGeneration = generation,
            actualTargetGeneration = generation,
            availableUsageLabels = target.usageLabels.toSet(),
            allocationAvailable = true,
            targetBudgetBytes = targetBudgetBytes,
            actualFormatClass = target.formatClass,
            actualSampleCount = target.sampleCount,
        )
        return GPUSaveLayerNativeExecutor().execute(request, context)
    }

    private fun refused(code: String, message: String) =
        GPUPreparedSurfaceFrameResult.Refused(diagnostic(code, message))
}

/** Frame-level budget used by saveLayer materialization when the caller provides none. */
private const val DEFAULT_SAVE_LAYER_FRAME_BUDGET_BYTES = 16L * 1024L * 1024L

/** Result of handling one prepared composite frame through the saveLayer pipeline. */
sealed interface GPUPreparedSaveLayerFrameHandling {
    /** All layers lowered, preflighted, and materialized with their command streams. */
    data class Ready(
        val plan: GPUPreparedCompositePlan,
        val results: List<GPUSaveLayerMaterializationResult>,
        val commands: List<GPUPassCommand>,
    ) : GPUPreparedSaveLayerFrameHandling

    /** Stable refusal from lowering, preflight, or materialization. */
    class Refused(
        val code: String,
        val operationIndex: Int?,
        facts: Map<String, String>,
    ) : GPUPreparedSaveLayerFrameHandling {
        val facts: Map<String, String> =
            java.util.Collections.unmodifiableMap(java.util.LinkedHashMap(facts))
    }
}

private fun buildPreparedColorGlyphDestinationSnapshotPlans(
    request: GPUPreparedSurfaceFrameRequest,
    packets: List<GPUDrawPacket>,
): List<GPUPreparedColorGlyphDestinationSnapshotPlan> {
    val limits = requireNotNull(request.capabilities.limits) {
        "Prepared ColorGlyph destination snapshots require observed device limits."
    }
    val logicalBytesPerRow = Math.multiplyExact(request.targetBounds.width.toLong(), 4L)
    val paddedBytesPerRow = alignUpPreparedText(
        logicalBytesPerRow,
        limits.copyBytesPerRowAlignment,
    )
    val copiedBytes = Math.multiplyExact(
        paddedBytesPerRow,
        request.targetBounds.height.toLong(),
    )
    val textureBytes = Math.multiplyExact(
        logicalBytesPerRow,
        request.targetBounds.height.toLong(),
    )
    return packets.mapNotNull { packet ->
        val semantic = request.semanticsByCommandId[packet.commandIdValue]
        if (semantic !is GPUDrawSemanticPayload.ColorGlyph ||
            packet.blendPlan?.destinationReadRequirement !=
            GPUBlendDestinationReadRequirement.DestinationTextureRequired
        ) {
            return@mapNotNull null
        }
        packet
    }.mapIndexed { index, packet ->
        val snapshot = GPUFrameTextureRef(
            "texture.prepared-surface.color-glyph-destination." +
                "${request.baseTaskList.frameId.value}.$index",
        )
        GPUPreparedColorGlyphDestinationSnapshotPlan(
            groupIndex = index,
            packetId = packet.packetId,
            commandIdValue = packet.commandIdValue,
            snapshot = snapshot,
            preparation = GPUResourcePreparationRequest(
                resource = snapshot,
                descriptor = GPUFrameTextureDescriptor(
                    logicalBounds = request.targetBounds,
                    format = request.targetFormat,
                    sampleCount = 1,
                ),
                role = GPUFrameResourceRole.DestinationSnapshot,
                usages = setOf(
                    GPUFrameResourceUsage.CopyDestination,
                    GPUFrameResourceUsage.TextureBinding,
                ),
                lifetime = GPUFrameResourceLifetime.FrameLocal,
                byteSize = textureBytes,
                diagnosticLabel =
                    "prepared-color-glyph.destination-snapshot.${packet.packetId.value}",
            ),
            allocation = GPUFrameMemoryAllocation(
                label = "prepared-color-glyph.destination-snapshot.${packet.packetId.value}",
                category = GPUFrameMemoryCategory.DestinationSnapshot,
                bytes = textureBytes,
                resourceKind = GPUFrameMemoryResourceKind.Texture2D,
                extent = request.targetBounds,
            ),
            copiedBytes = copiedBytes,
            copyLayout = GPUTextureCopyLayout(
                bytesPerRow = paddedBytesPerRow,
                rowsPerImage = request.targetBounds.height,
            ),
            targetGeneration = packet.resourceGeneration,
        )
    }
}

private fun preparedSurfaceTargetColorInterpretation(
    format: GPUColorFormat,
): GPUColorInterpretation = when (format) {
    GPUColorFormat.RGBA8Unorm -> GPUColorInterpretation.EncodedPremulSrgb
    GPUColorFormat.RGBA8UnormSrgb -> GPUColorInterpretation.LinearPremul
    else -> throw IllegalArgumentException(
        "Prepared ColorGlyph destination snapshots require RGBA8Unorm or RGBA8UnormSrgb.",
    )
}

private sealed interface CoreAuthorityBaseAssembly {
    data class Prepared(val taskList: GPUTaskList) : CoreAuthorityBaseAssembly

    data class Refused(val diagnostic: GPUDiagnostic) : CoreAuthorityBaseAssembly
}

private sealed interface MixedCoreAssembly {
    data class Prepared(
        val packetByCommandId: Map<Int, List<GPUDrawPacket>>,
        val renderByPacketId: Map<
            org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID,
            GPUTask.Render,
            > = emptyMap(),
        val resourceUsesByCommandId: Map<Int, List<GPUFrameResourceUse>>,
        val coverageMaskProducerRenders: List<GPUTask.Render> = emptyList(),
        val coverageMaskConsumerUseByPlanIdentity: Map<String, GPUFrameResourceUse> = emptyMap(),
        val preparations: List<GPUResourcePreparationRequest>,
        val memoryBudget: GPUFrameMemoryBudgetPlan?,
    ) : MixedCoreAssembly

    data class Refused(val diagnostic: GPUDiagnostic) : MixedCoreAssembly
}

private fun buildSharedCoreCoverageMaskProducerTopologies(
    request: GPUPreparedSurfaceFrameRequest,
    packets: List<GPUDrawPacket>,
    coreAssembly: MixedCoreAssembly.Prepared,
): List<GPUCoverageMaskProducerTopology> =
    coreAssembly.coverageMaskProducerRenders.groupBy { render ->
        requireNotNull(render.drawPackets.firstOrNull()?.clipExecutionPlan).canonicalIdentity()
    }.map { (identity, producerRenders) ->
        val plan = producerRenders.first().drawPackets.first().clipExecutionPlan as
            GPUClipExecutionPlan.CoverageMask
        require(producerRenders.all { render ->
            render.drawPackets.all { packet ->
                packet.clipExecutionPlan?.canonicalIdentity() == identity
            }
        }) { "One shared Core CoverageMask topology must retain one immutable plan" }
        val maskResources = producerRenders.flatMap(GPUTask.Render::resourceUses)
            .filter { use -> use.role == GPUFrameResourceRole.ClipMask && use.write }
            .map(GPUFrameResourceUse::resource)
            .distinct()
        require(maskResources.size == 1)
        val mask = maskResources.single() as GPUFrameTargetRef
        val preparation = coreAssembly.preparations.single { candidate ->
            candidate.resource == mask && candidate.role == GPUFrameResourceRole.ClipMask
        }
        val commonUses = producerRenders.first().resourceUses
            .filterNot { use -> use.role == GPUFrameResourceRole.ClipMask }
        require(producerRenders.all { render ->
            render.recordingId == producerRenders.first().recordingId &&
                render.resourceUses.filterNot { use ->
                    use.role == GPUFrameResourceRole.ClipMask
                } == commonUses
        })
        val consumers = packets.filter { packet ->
            packet.clipExecutionPlan?.canonicalIdentity() == identity
        }.map { packet ->
            when (request.semanticsByCommandId[packet.commandIdValue]) {
                is GPUDrawSemanticPayload.CorePrimitive ->
                    GPUCoverageMaskConsumerDescriptor.Core(packet)
                is GPUDrawSemanticPayload.TextA8 ->
                    GPUCoverageMaskConsumerDescriptor.TextA8(packet)
                is GPUDrawSemanticPayload.ColorGlyph ->
                    GPUCoverageMaskConsumerDescriptor.ColorGlyph(packet)
                else -> error(
                    "CoverageMask consumers are closed to Core, TextA8, or ColorGlyph",
                )
            }
        }
        buildCoverageMaskProducerTopology(
            plan = plan,
            attachment = GPUCoverageMaskProducerAttachment(
                resource = mask,
                diagnosticLabel = preparation.diagnosticLabel,
                recordingId = producerRenders.first().recordingId,
                producerTaskIds = producerRenders.map(GPUTask.Render::taskId),
                producerPacketPartitions = producerRenders.map(GPUTask.Render::drawPackets),
                additionalProducerUses = commonUses,
            ),
            consumers = consumers,
        )
    }

/**
 * Text-only masks use the same typed ClipProducer/ClipMask graph shape as Core masks. Mixed plans
 * containing at least one Core consumer are supplied by the Core authority assembler and are
 * deliberately excluded here, preventing a second producer or attachment.
 */
private sealed interface TextCoverageMaskProducerTopologyResult {
    data class Accepted(
        val topologies: List<GPUCoverageMaskProducerTopology>,
    ) : TextCoverageMaskProducerTopologyResult

    data class Refused(
        val code: String,
        val message: String,
    ) : TextCoverageMaskProducerTopologyResult
}

private fun buildTextOnlyCoverageMaskProducerTopologies(
    request: GPUPreparedSurfaceFrameRequest,
    packets: List<GPUDrawPacket>,
    baseRenders: List<GPUTask.Render>,
    configuredAggregateBudgetBytes: Long,
): TextCoverageMaskProducerTopologyResult {
    val renderByPacketId = baseRenders.flatMap { render ->
        render.drawPackets.map { packet -> packet.packetId to render }
    }.toMap()
    val corePlanIdentities = packets.mapNotNull { packet ->
        if (request.semanticsByCommandId[packet.commandIdValue] is
            GPUDrawSemanticPayload.CorePrimitive
        ) {
            (packet.clipExecutionPlan as? GPUClipExecutionPlan.CoverageMask)
                ?.canonicalIdentity()
        } else {
            null
        }
    }.toSet()
    val groupedConsumers = packets.mapNotNull { packet ->
        val semantic = request.semanticsByCommandId[packet.commandIdValue]
        if (semantic !is GPUDrawSemanticPayload.TextA8 &&
            semantic !is GPUDrawSemanticPayload.ColorGlyph
        ) {
            return@mapNotNull null
        }
        val plan = packet.clipExecutionPlan as? GPUClipExecutionPlan.CoverageMask
            ?: return@mapNotNull null
        plan.canonicalIdentity() to packet
    }.groupBy(Pair<String, GPUDrawPacket>::first)
        .filterKeys { identity -> identity !in corePlanIdentities }
    val topologies = groupedConsumers.map { (identity, entries) ->
            val representative = entries.first().second
            val plan = representative.clipExecutionPlan as GPUClipExecutionPlan.CoverageMask
            require(entries.all { (_, packet) ->
                packet.clipExecutionPlan?.canonicalIdentity() == identity
            }) { "One text CoverageMask identity must retain one exact immutable plan" }
            val key = MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
            val mask = GPUFrameTargetRef("target.prepared-surface.clip-mask.$key")
            val producerId = GPUTaskID("task.prepared-surface.clip-mask.$key")
            val producerStructuralKeys = plan.producers.map { producer ->
                corePrimitiveCoverageMaskProducerRenderPipelineStructuralKey(producer)
            }
            val renderPipelineKeys = producerStructuralKeys.distinct().associateWith { key ->
                key.stableRenderPipelineKey(CORE_PRIMITIVE_STRUCTURAL_PIPELINE_BASE_KEY)
            }
            val producerPackets = plan.producers.mapIndexed { index, producer ->
                val authority = GPUClipProducerAuthority.Mask(producer)
                GPUDrawPacket(
                    packetId = GPUDrawPacketID(
                        "packet.${producerId.value}.${producer.sourceOrder}",
                    ),
                    commandIdValue = representative.commandIdValue,
                    analysisRecordId = "analysis.${producerId.value}.${producer.sourceOrder}",
                    passId = "pass.${producerId.value}",
                    layerId = representative.layerId,
                    bindingListId = "bindings.${producerId.value}",
                    insertionReasonCode =
                        "clip.mask.producer.${producer.combine.name}.${producer.sourceOrder}",
                    sortKey = representative.sortKey,
                    sortKeyPreimage = representative.sortKeyPreimage,
                    renderStepId = GPURenderStepID("clip.mask.producer"),
                    renderStepVersion = 1,
                    role = GPUDrawPacketRole.ClipProducer,
                    blendPlan = corePrimitiveClipProducerBlendPlan(authority),
                    renderPipelineKey = requireNotNull(renderPipelineKeys[producerStructuralKeys[index]]),
                    bindingLayoutHash = CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_LAYOUT_KEY,
                    vertexSourceLabel = "clip-producer-authority",
                    targetStateHash = "target.clip.mask.producer.single-sample",
                    originalPaintOrder = representative.originalPaintOrder,
                    resourceGeneration = PREPARED_FRAME_LATE_BOUND_RESOURCE_GENERATION,
                    frameProvenance = representative.frameProvenance,
                    clipCoveragePlan = representative.clipCoveragePlan,
                    clipExecutionPlan = plan,
                    clipProducerAuthority = authority,
                )
            }
            val payloads = plan.producers.map { producer ->
                GPUUniformSlabPayload(
                    slotLabel = "coverage-mask-producer-${producer.sourceOrder}",
                    bytes = corePrimitiveCoverageMaskProducerUniformBytes(plan, producer),
                )
            }
            val limits = requireNotNull(request.capabilities.limits)
            val maxBufferSize = limits.maxBufferSize
                ?: return TextCoverageMaskProducerTopologyResult.Refused(
                    "unsupported.recording.prepared_text_coverage_mask_max_buffer_size",
                    "Prepared text CoverageMask producer planning requires maxBufferSize.",
                )
            val maxDynamicUniformBuffers = limits.maxDynamicUniformBuffersPerPipelineLayout
                ?: return TextCoverageMaskProducerTopologyResult.Refused(
                    "unsupported.recording.prepared_text_coverage_mask_dynamic_uniform_limit",
                    "Prepared text CoverageMask producer planning requires the dynamic-uniform limit.",
                )
            val slabPlan = when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = "prepared-text-coverage-mask-producer-uniform-pass",
                deviceGeneration = request.baseTaskList.capabilitySeal.deviceGeneration.value,
                alignmentBytes = limits.minUniformBufferOffsetAlignment,
                uploadBudgetBytes = minOf(configuredAggregateBudgetBytes, maxBufferSize),
                maxBufferSize = maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffers,
                payloads = payloads,
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return TextCoverageMaskProducerTopologyResult.Refused(
                    planned.diagnostic.code,
                    "Prepared text CoverageMask producer uniform64 slab planning was refused.",
                )
            }
            if (slabPlan.totalBytes > Int.MAX_VALUE.toLong()) {
                return TextCoverageMaskProducerTopologyResult.Refused(
                    "unsupported.recording.prepared_text_coverage_mask_uniform_slab_host_size",
                    "Prepared text CoverageMask producer uniform64 slab exceeds the host-addressable size.",
                )
            }
            val packedBytes = ByteArray(slabPlan.totalBytes.toInt())
            payloads.zip(slabPlan.slots).forEach { (payload, slot) ->
                payload.bytes.copyInto(packedBytes, slot.alignedOffset.toInt())
            }
            val producerSeal = GPUCoverageMaskProducerUniformSlabSeal(
                plan = slabPlan,
                contentKey = plan.contentKey,
                planCanonicalIdentity = identity,
                maskResource = mask,
                producerSlots = producerPackets.mapIndexed { index, producerPacket ->
                    GPUCorePrimitiveCoverageMaskProducerUniformSlotSeal(
                        slotIndex = index,
                        sourceOrder = plan.producers[index].sourceOrder,
                        packetId = producerPacket.packetId,
                        commandId = producerPacket.commandIdValue,
                        structuralPipelineKey = producerStructuralKeys[index],
                        renderPipelineKey = requireNotNull(
                            renderPipelineKeys[producerStructuralKeys[index]],
                        ),
                        bindingLayoutHash = CORE_PRIMITIVE_COVERAGE_MASK_PRODUCER_LAYOUT_KEY,
                    )
                },
                packedBytes = packedBytes,
                maskBounds = plan.bounds,
                orderingToken = plan.orderingToken.value,
            )
            producerPackets.forEach { packet ->
                packet.attachCoverageMaskProducerUniformSlabSeal(producerSeal)
            }
            val recordingId = renderByPacketId.getValue(representative.packetId).recordingId
            buildCoverageMaskProducerTopology(
                plan = plan,
                attachment = GPUCoverageMaskProducerAttachment(
                    resource = mask,
                    diagnosticLabel = "prepared-surface.clip-mask.$key",
                    recordingId = recordingId,
                    producerTaskIds = listOf(producerId),
                    producerPacketPartitions = listOf(producerPackets),
                ),
                consumers = entries.map { (_, packet) ->
                    when (request.semanticsByCommandId.getValue(packet.commandIdValue)) {
                        is GPUDrawSemanticPayload.TextA8 ->
                            GPUCoverageMaskConsumerDescriptor.TextA8(packet)
                        is GPUDrawSemanticPayload.ColorGlyph ->
                            GPUCoverageMaskConsumerDescriptor.ColorGlyph(packet)
                        else -> error("Unexpected prepared CoverageMask consumer")
                    }
                },
            )
        }
    return TextCoverageMaskProducerTopologyResult.Accepted(topologies)
}

/**
 * A mask plan owns one render-pass producer even when its ordered clip algebra contains several
 * draw commands. The attachment is cleared once and every producer packet remains in source order.
 */
private fun List<GPUTask.Render>.mergeCoverageMaskProducerPasses(): List<GPUTask.Render> =
    groupBy { render ->
        requireNotNull(render.drawPackets.firstOrNull()?.clipExecutionPlan).canonicalIdentity()
    }.values.map { ordered ->
        val first = ordered.first()
        val targets = ordered.map(GPUTask.Render::target).distinct()
        require(targets.size == 1 && ordered.all { render ->
            render.samplePlan == first.samplePlan &&
                render.recordingId == first.recordingId &&
                render.drawPackets.all { packet ->
                    packet.role == org.graphiks.kanvas.gpu.renderer.passes
                        .GPUDrawPacketRole.ClipProducer
                }
        }) { "CoverageMask producer passes must retain one exact attachment and sample authority" }
        GPUTask.Render(
            taskId = first.taskId,
            recordingId = first.recordingId,
            phase = first.phase,
            target = first.target,
            loadStore = first.loadStore,
            samplePlan = first.samplePlan,
            resourceUses = ordered.flatMap(GPUTask.Render::resourceUses).distinct(),
            provisionalSegmentKey = first.provisionalSegmentKey,
            drawPackets = ordered.flatMap(GPUTask.Render::drawPackets),
            batchEligibilityByPacketId = ordered
                .flatMap { render -> render.batchEligibilityByPacketId.entries }
                .associate { entry -> entry.key to entry.value },
            sampleContinuationKey = first.sampleContinuationKey,
            depthStencilLoadStore = first.depthStencilLoadStore,
        )
    }

/** Public module boundary for the validated four-corner prepared-image geometry value. */
fun buildPreparedImageGeometry(
    geometryClass: GPUPreparedImageGeometryClass,
    vertices: List<GPUPreparedImageVertex>,
): GPUPreparedImageGeometry = GPUPreparedImageGeometry(
    geometryClass = geometryClass,
    vertices = vertices,
    indices = listOf(0, 1, 2, 0, 2, 3),
)

private fun packetForSemantic(
    packets: List<GPUDrawPacket>,
    semantic: GPUDrawSemanticPayload.SampledImage,
): GPUDrawPacket = packets.single {
    it.commandIdValue == semantic.payloadRef.commandIdValue
}

private data class PreparedRouteRunKey(
    val semanticKind: String,
    val passId: String,
    val renderStepId: String,
    val renderStepVersion: Int,
    val renderPipelineKey: String?,
    val bindingLayoutHash: String,
    val samplePlanKey: String,
    val target: String,
    val loadStore: GPULoadStorePlan,
    val provisionalSegmentKey:
        org.graphiks.kanvas.gpu.renderer.passes.GPUProvisionalRenderSegmentKey,
    val depthStencilLoadStore: GPUDepthStencilLoadStorePlan?,
    val targetStateHash: String,
    val continuationKey: String?,
)

private fun List<GPUDrawPacket>.contiguousRouteRuns(
    baseRenderByPacketId: Map<org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID, GPUTask.Render>,
): List<List<GPUDrawPacket>> {
    val runs = mutableListOf<MutableList<GPUDrawPacket>>()
    forEach { packet ->
        val render = baseRenderByPacketId.getValue(packet.packetId)
        val coreRouteIdentity = if (
            packet.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
        ) {
            render.taskId.value
        } else {
            null
        }
        val key = PreparedRouteRunKey(
            semanticKind = when (packet.semanticPayload) {
                is GPUDrawSemanticPayload.SampledImage -> "sampled-image"
                is GPUDrawSemanticPayload.CorePrimitive -> "core-primitive"
                is GPUDrawSemanticPayload.TextA8 -> "text-a8"
                is GPUDrawSemanticPayload.ColorGlyph -> "color-glyph"
                is GPUDrawSemanticPayload.Vertices -> "vertices"
                else -> "unsupported"
            },
            passId = packet.passId,
            renderStepId = coreRouteIdentity ?: packet.renderStepId.value,
            renderStepVersion = if (coreRouteIdentity == null) packet.renderStepVersion else 0,
            renderPipelineKey = if (coreRouteIdentity == null) {
                packet.renderPipelineKey?.value
            } else {
                null
            },
            bindingLayoutHash = coreRouteIdentity ?: packet.bindingLayoutHash,
            samplePlanKey = render.samplePlan.specializationKey,
            target = render.target.value,
            loadStore = render.loadStore,
            provisionalSegmentKey = render.provisionalSegmentKey,
            depthStencilLoadStore = render.depthStencilLoadStore,
            targetStateHash = coreRouteIdentity ?: packet.targetStateHash,
            continuationKey = render.sampleContinuationKey?.toString(),
        )
        val current = runs.lastOrNull()
        val currentKey = current?.firstOrNull()?.let { first ->
            val firstRender = baseRenderByPacketId.getValue(first.packetId)
            val firstCoreRouteIdentity = if (
                first.semanticPayload is GPUDrawSemanticPayload.CorePrimitive
            ) {
                firstRender.taskId.value
            } else {
                null
            }
            PreparedRouteRunKey(
                semanticKind = when (first.semanticPayload) {
                    is GPUDrawSemanticPayload.SampledImage -> "sampled-image"
                    is GPUDrawSemanticPayload.CorePrimitive -> "core-primitive"
                    is GPUDrawSemanticPayload.TextA8 -> "text-a8"
                    is GPUDrawSemanticPayload.ColorGlyph -> "color-glyph"
                    is GPUDrawSemanticPayload.Vertices -> "vertices"
                    else -> "unsupported"
                },
                passId = first.passId,
                renderStepId = firstCoreRouteIdentity ?: first.renderStepId.value,
                renderStepVersion =
                    if (firstCoreRouteIdentity == null) first.renderStepVersion else 0,
                renderPipelineKey = if (firstCoreRouteIdentity == null) {
                    first.renderPipelineKey?.value
                } else {
                    null
                },
                bindingLayoutHash = firstCoreRouteIdentity ?: first.bindingLayoutHash,
                samplePlanKey = firstRender.samplePlan.specializationKey,
                target = firstRender.target.value,
                loadStore = firstRender.loadStore,
                provisionalSegmentKey = firstRender.provisionalSegmentKey,
                depthStencilLoadStore = firstRender.depthStencilLoadStore,
                targetStateHash = firstCoreRouteIdentity ?: first.targetStateHash,
                continuationKey = firstRender.sampleContinuationKey?.toString(),
            )
        }
        if (current == null || currentKey != key) {
            runs += mutableListOf(packet)
        } else {
            current += packet
        }
    }
    return runs
}

private fun verticesStagingRef(frameId: GPUFrameID): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.staging.${frameId.value}")

private fun verticesVertexBufferRef(frameId: GPUFrameID, artifactKey: String): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.vertex.${frameId.value}.$artifactKey")

private fun verticesIndexBufferRef(frameId: GPUFrameID, artifactKey: String): GPUFrameBufferRef =
    GPUFrameBufferRef("buffer.prepared-vertices.index.${frameId.value}.$artifactKey")

private fun GPUDrawPacket.withSemantic(
    semantic: GPUDrawSemanticPayload,
    clipCoverageOverride: GPUClipCoveragePlan? = clipCoveragePlan,
    clipExecutionOverride: GPUClipExecutionPlan? = clipExecutionPlan,
) = GPUDrawPacket(
    packetId = packetId,
    commandIdValue = commandIdValue,
    analysisRecordId = analysisRecordId,
    passId = passId,
    layerId = layerId,
    bindingListId = bindingListId,
    insertionReasonCode = insertionReasonCode,
    sortKey = sortKey,
    sortKeyPreimage = sortKeyPreimage,
    renderStepId = renderStepId,
    renderStepVersion = renderStepVersion,
    role = role,
    blendPlan = blendPlan,
    renderPipelineKey = renderPipelineKey,
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = bindingLayoutHash,
    uniformSlot = uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semantic,
    vertexSourceLabel = vertexSourceLabel,
    scissorBoundsHash = scissorBoundsHash,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoverageOverride,
    clipExecutionPlan = clipExecutionOverride,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

/**
 * Rewrites the recorder's unmaterialized semantic-only vertices packet into the exact
 * prepared-vertices render authority retained by the native route. The semantic-only packet
 * itself (null pipeline, Discard role) remains the recording evidence; only the base task-list
 * copy used to assemble prepared render runs is rewritten.
 */
private fun GPUDrawPacket.withPreparedVerticesRenderAuthority(): GPUDrawPacket = GPUDrawPacket(
    packetId = packetId,
    commandIdValue = commandIdValue,
    analysisRecordId = analysisRecordId,
    passId = passId,
    layerId = layerId,
    bindingListId = bindingListId,
    insertionReasonCode = insertionReasonCode,
    sortKey = sortKey,
    sortKeyPreimage = sortKeyPreimage,
    renderStepId = renderStepId,
    renderStepVersion = renderStepVersion,
    role = org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketRole.Shading,
    blendPlan = blendPlan,
    renderPipelineKey = org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_RENDER_PIPELINE_KEY,
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_BINDING_LAYOUT_HASH,
    uniformSlot = uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semanticPayload,
    vertexSourceLabel = org.graphiks.kanvas.gpu.renderer.payloads.PREPARED_VERTICES_VERTEX_SOURCE_LABEL,
    scissorBoundsHash = scissorBoundsHash,
    targetStateHash = targetStateHash,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoveragePlan,
    clipExecutionPlan = clipExecutionPlan,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun synthesizedSemanticOnlyBaseRender(
    request: GPUPreparedSurfaceFrameRequest,
    task: GPUTask.SemanticOnly,
    packet: GPUDrawPacket,
): GPUTask.Render = GPUTask.Render(
    taskId = GPUTaskID("task.base.prepared-vertices.${task.commandId.value}"),
    recordingId = task.recordingId,
    phase = GPUTaskPhase.Render,
    target = request.target,
    loadStore = GPULoadStorePlan("load", GPUStorePlan.Store),
    samplePlan = org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan.SingleSampleFrame,
    provisionalSegmentKey = GPUProvisionalRenderSegmentKey(
        "segment.prepared-surface.vertices.${task.commandId.value}",
    ),
    drawPackets = listOf(packet),
    batchEligibilityByPacketId = mapOf(
        packet.packetId to org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchEligibility(
            kind = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchKind.Isolated,
            queueGuard = org.graphiks.kanvas.gpu.renderer.passes.GPUPassBatchQueueGuard(
                emptyList(),
                emptyList(),
            ),
        ),
    ),
)

/**
 * The general recorder can seal only the resolved ColorGlyph route identity; atlas placement makes
 * the exact uniform slot available later at semantic gathering. Only that exact pending handoff may
 * be completed here. Any partially forged or already-sealed packet continues to strict validation.
 */
private fun GPUDrawPacket.hasPendingColorGlyphRecordingAuthority(): Boolean =
    renderStepId.value ==
        org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY &&
        renderPipelineKey?.value ==
        "pending.pipeline.draw_text_run.colrv0_composite.rgba8unorm.src_over" &&
        bindingLayoutHash == "preflight.pending" &&
        uniformSlot == null &&
        vertexSourceLabel == "preflight.pending"

private fun GPUDrawPacket.withPreparedColorGlyphAuthority(
    semantic: GPUDrawSemanticPayload.ColorGlyph,
): GPUDrawPacket = GPUDrawPacket(
    packetId = packetId,
    commandIdValue = commandIdValue,
    analysisRecordId = analysisRecordId,
    passId = passId,
    layerId = layerId,
    bindingListId = bindingListId,
    insertionReasonCode = insertionReasonCode,
    sortKey = sortKey,
    sortKeyPreimage = sortKeyPreimage,
    renderStepId = renderStepId,
    renderStepVersion = renderStepVersion,
    role = role,
    blendPlan = blendPlan,
    renderPipelineKey = COLOR_GLYPH_RENDER_PIPELINE_KEY,
    computePipelineKey = computePipelineKey,
    bindingLayoutHash = COLOR_GLYPH_BINDING_LAYOUT_HASH,
    uniformSlot = semantic.payloadRef.uniformSlot,
    resourceSlot = resourceSlot,
    semanticPayload = semanticPayload,
    vertexSourceLabel = COLOR_GLYPH_VERTEX_SOURCE_LABEL,
    scissorBoundsHash = colorGlyphScissorAuthority(semantic.scissorBounds),
    targetStateHash = COLOR_GLYPH_TARGET_STATE_HASH,
    originalPaintOrder = originalPaintOrder,
    resourceGeneration = resourceGeneration,
    frameProvenance = frameProvenance,
    clipCoveragePlan = clipCoveragePlan,
    clipExecutionPlan = clipExecutionPlan,
    diagnostics = diagnostics,
    clipProducerAuthority = clipProducerAuthority,
)

private fun GPUDrawPacket.withoutPreparedPathDepthStencil(): GPUDrawPacket {
    val authority = requireNotNull(corePrimitivePreparedAuthority)
    val structural = authority.structuralPipelineKey.copy(
        depthStencil = GPUCorePrimitiveRenderPipelineStructuralKey.DepthStencil.None,
    )
    val pipeline = structural.stableRenderPipelineKey(CORE_PRIMITIVE_RENDER_PIPELINE_KEY)
    val rebuilt = GPUDrawPacket(
        packetId = packetId,
        commandIdValue = commandIdValue,
        analysisRecordId = analysisRecordId,
        passId = passId,
        layerId = layerId,
        bindingListId = bindingListId,
        insertionReasonCode = insertionReasonCode,
        sortKey = sortKey,
        sortKeyPreimage = sortKeyPreimage,
        renderStepId = renderStepId,
        renderStepVersion = renderStepVersion,
        role = role,
        blendPlan = blendPlan,
        renderPipelineKey = pipeline,
        computePipelineKey = computePipelineKey,
        bindingLayoutHash = bindingLayoutHash,
        uniformSlot = uniformSlot,
        resourceSlot = resourceSlot,
        semanticPayload = semanticPayload,
        vertexSourceLabel = vertexSourceLabel,
        scissorBoundsHash = scissorBoundsHash,
        targetStateHash = targetStateHash,
        originalPaintOrder = originalPaintOrder,
        resourceGeneration = resourceGeneration,
        frameProvenance = frameProvenance,
        clipCoveragePlan = clipCoveragePlan,
        clipExecutionPlan = clipExecutionPlan,
        diagnostics = diagnostics,
        clipProducerAuthority = clipProducerAuthority,
    )
    return rebuilt.attachCorePrimitivePreparedAuthority(
        authority.copy(
            structuralPipelineKey = structural,
            renderPipelineKey = pipeline,
        ),
    )
}

private fun dependency(
    from: GPUTaskID,
    to: GPUTaskID,
    kind: String,
    reason: String,
    token: String,
) = GPUTaskDependency(
    fromTaskId = from,
    toTaskId = to,
    dependencyKind = kind,
    useToken = GPUTaskUseToken(token),
    reasonCode = reason,
)

private fun diagnostic(code: String, message: String) = GPUDiagnostic(
    code = GPUDiagnosticCode(code),
    domain = GPUDiagnosticDomain.Recording,
    severity = GPUDiagnosticSeverity.Error,
    message = message,
)

private fun GPUDiagnostic.atRecordingBoundary(): GPUDiagnostic =
    if (code.value in GPUPreparedImageRefusalCodes.ALL) {
        copy(facts = facts + ("boundary" to "recording"))
    } else {
        this
    }

private data class PreparedSurfaceTaskGraphRefusal(
    val code: String,
    val message: String,
)

private fun taskGraphLimitRefusal(
    limits: GPUPreparedSurfaceTaskGraphLimits,
    bufferAllocations: Int,
    textureAllocations: Int,
    allocations: Int,
    tasks: Long,
    dependencies: Long,
    instanceRanges: Int,
): PreparedSurfaceTaskGraphRefusal? {
    if (bufferAllocations > limits.maxBufferAllocations) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_buffer_allocation_budget",
            "Prepared-surface buffer allocation count exceeds its configured limit.",
        )
    }
    if (textureAllocations > limits.maxTextureAllocations) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_texture_allocation_budget",
            "Prepared-surface texture allocation count exceeds its configured limit.",
        )
    }
    if (allocations > limits.maxAllocations) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_allocation_budget",
            "Prepared-surface allocation count exceeds its configured limit.",
        )
    }
    if (tasks > limits.maxTasks.toLong()) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_task_budget",
            "Prepared-surface task count exceeds its configured limit.",
        )
    }
    if (dependencies > limits.maxDependencies.toLong()) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_surface_dependency_budget",
            "Prepared-surface dependency count exceeds its configured limit.",
        )
    }
    if (instanceRanges > limits.maxInstanceRanges) {
        return PreparedSurfaceTaskGraphRefusal(
            "unsupported.recording.prepared_text_instance_range_budget",
            "Prepared-text instance-range count exceeds its configured limit.",
        )
    }
    return null
}

private const val PREPARED_TEXT_INSTANCE_ALIGNMENT_BYTES = 16

private sealed interface PreparedColorGlyphBufferAssemblyResult {
    class Prepared(
        val plansByArtifactKey: Map<GPUTextArtifactKey, GPUPreparedColorGlyphBufferPlan>,
    ) : PreparedColorGlyphBufferAssemblyResult

    data class Refused(
        val code: String,
        val message: String,
    ) : PreparedColorGlyphBufferAssemblyResult
}

private fun buildPreparedColorGlyphBufferPlans(
    semantics: List<GPUDrawSemanticPayload.ColorGlyph>,
    frameIdentity: String,
    capabilities: GPUCapabilities,
): PreparedColorGlyphBufferAssemblyResult {
    if (semantics.isEmpty()) {
        return PreparedColorGlyphBufferAssemblyResult.Prepared(emptyMap())
    }
    val limits = capabilities.limits ?: return PreparedColorGlyphBufferAssemblyResult.Refused(
        "unsupported.recording.prepared_color_glyph_buffer",
        "Prepared ColorGlyph buffers require observed device limits.",
    )
    val alignment = limits.minUniformBufferOffsetAlignment
    if (alignment <= 0L || alignment and (alignment - 1L) != 0L) {
        return PreparedColorGlyphBufferAssemblyResult.Refused(
            "unsupported.recording.prepared_color_glyph_buffer",
            "Prepared ColorGlyph uniform alignment is invalid.",
        )
    }
    return try {
        val plans = linkedMapOf<GPUTextArtifactKey, GPUPreparedColorGlyphBufferPlan>()
        semantics.groupByTo(linkedMapOf(), GPUDrawSemanticPayload.ColorGlyph::planArtifactKey)
            .entries
            .forEachIndexed { planIndex, (artifactKey, packets) ->
                var vertexOffset = 0L
                var indexOffset = 0L
                var uniformEnd = 0L
                val slices = packets.map { semantic ->
                    val vertexBytes = Math.multiplyExact(
                        semantic.vertexData.size.toLong(),
                        Float.SIZE_BYTES.toLong(),
                    )
                    val indexBytes = Math.multiplyExact(
                        semantic.indexData.size.toLong(),
                        Int.SIZE_BYTES.toLong(),
                    )
                    val uniformBytes = semantic.uniformBytes.size.toLong()
                    val uniformOffset = alignUpPreparedText(uniformEnd, alignment)
                    GPUPreparedColorGlyphBufferSlice(
                        commandIdValue = semantic.payloadRef.commandIdValue,
                        vertexOffsetBytes = vertexOffset,
                        vertexSizeBytes = vertexBytes,
                        indexOffsetBytes = indexOffset,
                        indexSizeBytes = indexBytes,
                        uniformOffsetBytes = uniformOffset,
                        uniformSizeBytes = uniformBytes,
                        indexCount = semantic.indexData.size,
                    ).also {
                        vertexOffset = Math.addExact(vertexOffset, vertexBytes)
                        indexOffset = Math.addExact(indexOffset, indexBytes)
                        uniformEnd = Math.addExact(uniformOffset, uniformBytes)
                    }
                }
                val sizes = listOf(vertexOffset, indexOffset, uniformEnd)
                if (sizes.any { size ->
                        size <= 0L ||
                            size > Int.MAX_VALUE.toLong() ||
                            limits.maxBufferSize?.let { size > it } == true
                    }
                ) {
                    return PreparedColorGlyphBufferAssemblyResult.Refused(
                        "unsupported.recording.prepared_color_glyph_buffer",
                        "Prepared ColorGlyph buffer exceeds the observed allocation limit.",
                    )
                }
                val vertexBytes = ByteArray(vertexOffset.toInt())
                val indexBytes = ByteArray(indexOffset.toInt())
                val uniformBytes = ByteArray(uniformEnd.toInt())
                val vertexWriter = ByteBuffer.wrap(vertexBytes).order(ByteOrder.LITTLE_ENDIAN)
                val indexWriter = ByteBuffer.wrap(indexBytes).order(ByteOrder.LITTLE_ENDIAN)
                packets.zip(slices).forEach { (semantic, slice) ->
                    vertexWriter.position(slice.vertexOffsetBytes.toInt())
                    semantic.vertexData.forEach(vertexWriter::putFloat)
                    indexWriter.position(slice.indexOffsetBytes.toInt())
                    semantic.indexData.forEach(indexWriter::putInt)
                    semantic.uniformBytes.forEachIndexed { index, value ->
                        uniformBytes[Math.addExact(slice.uniformOffsetBytes.toInt(), index)] =
                            value.toByte()
                    }
                }
                val vertexHash = vertexBytes.sha256Hex()
                val indexHash = indexBytes.sha256Hex()
                val uniformHash = uniformBytes.sha256Hex()
                plans[artifactKey] = GPUPreparedColorGlyphBufferPlan(
                    planArtifactKey = artifactKey,
                    vertexBufferRef = GPUFrameBufferRef(
                        "buffer.prepared-color-glyph.vertices:$frameIdentity:$planIndex",
                    ),
                    indexBufferRef = GPUFrameBufferRef(
                        "buffer.prepared-color-glyph.indices:$frameIdentity:$planIndex",
                    ),
                    uniformBufferRef = GPUFrameBufferRef(
                        "buffer.prepared-color-glyph.uniforms:$frameIdentity:$planIndex",
                    ),
                    uniformAlignmentBytes = alignment,
                    vertexByteSize = vertexOffset,
                    indexByteSize = indexOffset,
                    uniformByteSize = uniformEnd,
                    vertexContentHash = vertexHash,
                    indexContentHash = indexHash,
                    uniformContentHash = uniformHash,
                    slices = slices,
                    vertexBytes = vertexBytes,
                    indexBytes = indexBytes,
                    uniformBytes = uniformBytes,
                )
            }
        PreparedColorGlyphBufferAssemblyResult.Prepared(plans)
    } catch (_: ArithmeticException) {
        PreparedColorGlyphBufferAssemblyResult.Refused(
            "unsupported.recording.prepared_color_glyph_buffer",
            "Prepared ColorGlyph buffer planning overflowed.",
        )
    }
}

private data class PreparedTextInstanceRange(
    val firstInstance: Int,
    val instanceCount: Int,
)

private sealed interface PreparedTextInstanceAssemblyResult {
    class Prepared(
        val plan: GPUPreparedTextInstanceBufferPlan,
        val rangesByCommandId: Map<Int, PreparedTextInstanceRange>,
    ) : PreparedTextInstanceAssemblyResult

    data class Refused(
        val code: String,
        val message: String,
    ) : PreparedTextInstanceAssemblyResult
}

private fun buildPreparedTextInstanceAssembly(
    semantics: List<GPUDrawSemanticPayload>,
    frameIdentity: String,
    capabilities: GPUCapabilities,
): PreparedTextInstanceAssemblyResult {
    val totalInstances = try {
        semantics.fold(0) { count, semantic ->
            Math.addExact(count, semantic.preparedTextInstances().size)
        }
    } catch (_: ArithmeticException) {
        return PreparedTextInstanceAssemblyResult.Refused(
            "unsupported.recording.prepared_text_instance_range",
            "Prepared text instance count overflowed.",
        )
    }
    val byteSize = try {
        Math.multiplyExact(
            totalInstances.toLong(),
            GPUTextA8Instance.ENCODED_BYTE_SIZE.toLong(),
        )
    } catch (_: ArithmeticException) {
        return PreparedTextInstanceAssemblyResult.Refused(
            "unsupported.recording.prepared_text_instance_buffer",
            "Prepared text instance byte size overflowed.",
        )
    }
    if (byteSize > Int.MAX_VALUE.toLong() ||
        capabilities.limits?.maxBufferSize?.let { byteSize > it } == true
    ) {
        return PreparedTextInstanceAssemblyResult.Refused(
            "unsupported.recording.prepared_text_instance_buffer",
            "Prepared text instance buffer exceeds the observed allocation limit.",
        )
    }
    val bytes = ByteBuffer.allocate(byteSize.toInt())
        .order(ByteOrder.LITTLE_ENDIAN)
        .also { target ->
            semantics.forEach { semantic ->
                semantic.preparedTextInstances().forEach { instance ->
                    instance.deviceQuad.forEach(target::putFloat)
                    target.putFloat(instance.uvRect.left)
                    target.putFloat(instance.uvRect.top)
                    target.putFloat(instance.uvRect.right)
                    target.putFloat(instance.uvRect.bottom)
                    target.putInt(instance.glyphId)
                    target.putInt(instance.sourceGlyphIndex.value)
                    target.putInt(instance.pageIndex)
                    target.putInt(instance.colorLayerIndex ?: -1)
                }
            }
        }
        .array()
    val contentHash = bytes.sha256Hex()
    val plan = GPUPreparedTextInstanceBufferPlan(
        bufferRef = GPUFrameBufferRef(
            "buffer.prepared-text.instances:$frameIdentity:$contentHash",
        ),
        strideBytes = GPUTextA8Instance.ENCODED_BYTE_SIZE,
        alignmentBytes = PREPARED_TEXT_INSTANCE_ALIGNMENT_BYTES,
        instanceCount = totalInstances,
        byteSize = byteSize,
        contentHash = contentHash,
        uploadBytes = bytes,
    )
    var firstInstance = 0
    val ranges = linkedMapOf<Int, PreparedTextInstanceRange>()
    semantics.forEach { semantic ->
        val count = semantic.preparedTextInstances().size
        ranges[semantic.payloadRef.commandIdValue] = PreparedTextInstanceRange(
            firstInstance = firstInstance,
            instanceCount = count,
        )
        firstInstance = Math.addExact(firstInstance, count)
    }
    return PreparedTextInstanceAssemblyResult.Prepared(plan, ranges)
}

private data class PreparedTextMaterialUniformRange(
    val offsetBytes: Long,
    val sizeBytes: Long,
)

private data class PreparedTextMaterialUniformIdentity(
    val materialKey: String,
    val abiHash: String,
    val sourceKind: String,
    val paintAlphaBits: Int,
    val bytes: List<Int>,
)

private data class PreparedTextMaterialUniformAssembly(
    val plan: GPUPreparedTextMaterialUniformBufferPlan?,
    val rangesByCommandId: Map<Int, PreparedTextMaterialUniformRange>,
)

private sealed interface PreparedTextMaterialUniformAssemblyResult {
    data class Prepared(val assembly: PreparedTextMaterialUniformAssembly) :
        PreparedTextMaterialUniformAssemblyResult

    data class Refused(val code: String, val message: String) :
        PreparedTextMaterialUniformAssemblyResult
}

private fun buildPreparedTextMaterialUniformAssembly(
    semantics: List<GPUDrawSemanticPayload>,
    frameIdentity: String,
    capabilities: GPUCapabilities,
): PreparedTextMaterialUniformAssemblyResult {
    val limits = requireNotNull(capabilities.limits)
    val alignment = limits.minUniformBufferOffsetAlignment
    val offsetByIdentity = linkedMapOf<PreparedTextMaterialUniformIdentity, Long>()
    val bytesByIdentity = linkedMapOf<PreparedTextMaterialUniformIdentity, ByteArray>()
    val ranges = linkedMapOf<Int, PreparedTextMaterialUniformRange>()
    var byteSize = 0L
    for (semantic in semantics) {
        val material = semantic.preparedTextMaterial()
        if (!material.paintAlpha.isFinite() || material.paintAlpha !in 0f..1f ||
            material.uniformBytes.any { it !in 0..255 }
        ) {
            return PreparedTextMaterialUniformAssemblyResult.Refused(
                "invalid.recording.prepared_text_material_uniform",
                "Prepared text material uniforms and paint alpha must retain validated values.",
            )
        }
        if (material.uniformBytes.isEmpty()) {
            ranges[semantic.payloadRef.commandIdValue] =
                PreparedTextMaterialUniformRange(0L, 0L)
            continue
        }
        val identity = PreparedTextMaterialUniformIdentity(
            materialKey = material.materialKey,
            abiHash = material.abiHash,
            sourceKind = material.sourceKind.name,
            paintAlphaBits = material.paintAlpha.toRawBits(),
            bytes = material.uniformBytes,
        )
        val offset = offsetByIdentity[identity] ?: try {
            alignUpPreparedText(byteSize, alignment).also { alignedOffset ->
                byteSize = Math.addExact(alignedOffset, material.uniformBytes.size.toLong())
                offsetByIdentity[identity] = alignedOffset
                bytesByIdentity[identity] =
                    ByteArray(material.uniformBytes.size) { index ->
                        material.uniformBytes[index].toByte()
                    }
            }
        } catch (_: ArithmeticException) {
            return PreparedTextMaterialUniformAssemblyResult.Refused(
                "unsupported.recording.prepared_text_material_uniform_buffer",
                "Prepared text material uniform offsets overflowed.",
            )
        }
        ranges[semantic.payloadRef.commandIdValue] = PreparedTextMaterialUniformRange(
            offsetBytes = offset,
            sizeBytes = material.uniformBytes.size.toLong(),
        )
    }
    if (byteSize == 0L) {
        return PreparedTextMaterialUniformAssemblyResult.Prepared(
            PreparedTextMaterialUniformAssembly(null, ranges),
        )
    }
    if (byteSize > Int.MAX_VALUE.toLong() ||
        limits.maxBufferSize?.let { byteSize > it } == true
    ) {
        return PreparedTextMaterialUniformAssemblyResult.Refused(
            "unsupported.recording.prepared_text_material_uniform_buffer",
            "Prepared text material uniform buffer exceeds the observed allocation limit.",
        )
    }
    val bytes = ByteArray(byteSize.toInt())
    bytesByIdentity.forEach { (identity, materialBytes) ->
        materialBytes.copyInto(bytes, offsetByIdentity.getValue(identity).toInt())
    }
    val contentHash = bytes.sha256Hex()
    return PreparedTextMaterialUniformAssemblyResult.Prepared(
        PreparedTextMaterialUniformAssembly(
            plan = GPUPreparedTextMaterialUniformBufferPlan(
                bufferRef = GPUFrameBufferRef(
                    "buffer.prepared-text.material-uniforms:$frameIdentity:$contentHash",
                ),
                alignmentBytes = alignment,
                byteSize = byteSize,
                contentHash = contentHash,
                uploadBytes = bytes,
            ),
            rangesByCommandId = ranges,
        ),
    )
}

private fun alignUpPreparedText(value: Long, alignment: Long): Long {
    val remainder = value % alignment
    return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
}

private fun GPUDrawSemanticPayload.preparedTextInstances(): List<GPUTextA8Instance> = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> instances
    is GPUDrawSemanticPayload.ColorGlyph -> instances
    else -> error("Only prepared text semantics own instance records")
}

private fun GPUDrawSemanticPayload.preparedTextMaterial() = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> material
    is GPUDrawSemanticPayload.ColorGlyph -> requireNotNull(material)
    else -> error("Only prepared text semantics own prepared material programs")
}

private fun GPUDrawSemanticPayload.preparedTextPreflightSeal(
    packet: GPUDrawPacket,
    material: org.graphiks.kanvas.gpu.renderer.materials.contracts.GPUPreparedMaterialProgram,
    atlasResourcePlan: GPUR8FrameResourcePlan,
    instanceBufferPlan: GPUPreparedTextInstanceBufferPlan,
    firstInstance: Int,
    instanceCount: Int,
    materialUniformOffsetBytes: Long,
    materialUniformSizeBytes: Long,
    drawUniformBufferPlan: GPUPreparedTextDrawUniformBufferPlan?,
    drawUniformSlice: GPUPreparedTextDrawUniformSlice?,
    compositeProgram: GPUPreparedTextCompositeProgram?,
    clipPlan: GPUPreparedTextClipPlan?,
    coverageMaskResource: GPUFrameTargetRef?,
): GPUPreparedTextBindingPreflightSeal {
    val targetBounds: GPUPixelBounds
    val scissorBounds: GPUPixelBounds
    val clipIdentity: String
    val blendPlanIdentity: String
    val capabilitySnapshotHash: String
    val canonicalHash: String
    val pageIndex: Int
    val textA8Composite: GPUPreparedTextCompositePreflightSeal?
    when (this) {
        is GPUDrawSemanticPayload.TextA8 -> {
            targetBounds = this.targetBounds
            scissorBounds = this.scissorBounds
            clipIdentity = this.clipIdentity
            blendPlanIdentity = this.blendPlanIdentity
            capabilitySnapshotHash = this.capabilitySnapshotHash
            canonicalHash = this.canonicalHash
            pageIndex = this.pageIndex
            val exactPlan = requireNotNull(drawUniformBufferPlan)
            val exactSlice = requireNotNull(drawUniformSlice)
            val exactProgram = requireNotNull(compositeProgram)
            textA8Composite = GPUPreparedTextCompositePreflightSeal(
                deviceToLocal = deviceToLocal,
                drawUniformBufferRef = exactPlan.bufferRef,
                drawUniformAlignmentBytes = exactPlan.alignmentBytes,
                drawUniformLogicalSliceSizeBytes = exactPlan.logicalSliceSizeBytes,
                drawUniformBufferByteSize = exactPlan.byteSize,
                drawUniformBufferContentHash = exactPlan.contentHash,
                drawUniformSlice = exactSlice,
                compositeSourceHash = exactProgram.sourceHash,
                compositeAbiHash = exactProgram.abiHash,
                compositePipelineKey = exactProgram.pipelineKey,
                compositeSourceCoverageEncoding = exactProgram.sourceCoverageEncoding,
                clipPlan = requireNotNull(clipPlan),
                coverageMaskResource = coverageMaskResource,
                compositeVertexEntryPoint = exactProgram.vertexEntryPoint,
                compositeFragmentEntryPoint = exactProgram.fragmentEntryPoint,
                compositeVertexLayout = exactProgram.vertexLayout,
                compositeAdmissionToken = exactProgram.admissionToken,
            )
        }
        is GPUDrawSemanticPayload.ColorGlyph -> {
            targetBounds = this.targetBounds
            scissorBounds = this.scissorBounds
            clipIdentity = requireNotNull(this.clipIdentity)
            blendPlanIdentity = requireNotNull(this.blendPlanIdentity)
            capabilitySnapshotHash = requireNotNull(this.capabilitySnapshotHash)
            canonicalHash = this.canonicalHash
            pageIndex = this.instances.first().pageIndex
            require(
                drawUniformBufferPlan == null &&
                    drawUniformSlice == null &&
                    compositeProgram == null &&
                    (
                        (packet.clipExecutionPlan is GPUClipExecutionPlan.CoverageMask) ==
                            (coverageMaskResource != null)
                        ),
            ) {
                "ColorGlyph may retain only its typed clip-mask resource, not TextA8 composite state"
            }
            textA8Composite = null
        }
        else -> error("Only prepared text semantics own preflight seals")
    }
    return GPUPreparedTextBindingPreflightSeal(
        semanticCanonicalHash = canonicalHash,
        atlasKey = atlasResourcePlan.artifactKey,
        atlasWidth = atlasResourcePlan.artifactWidth,
        atlasHeight = atlasResourcePlan.artifactHeight,
        atlasRowBytes = atlasResourcePlan.artifactRowBytes,
        atlasGeneration = atlasResourcePlan.artifactGeneration,
        atlasContentHash = atlasResourcePlan.artifactContentHash,
        pageIndex = pageIndex,
        instanceStrideBytes = instanceBufferPlan.strideBytes,
        firstInstance = firstInstance,
        instanceCount = instanceCount,
        instanceBufferByteSize = instanceBufferPlan.byteSize,
        instanceBufferContentHash = instanceBufferPlan.contentHash,
        materialUniformOffsetBytes = materialUniformOffsetBytes,
        materialUniformSizeBytes = materialUniformSizeBytes,
        materialKey = material.materialKey,
        materialWgslSourceHash = material.wgslSource.toByteArray().sha256Hex(),
        materialEntryPoint = material.entryPoint,
        materialAbiHash = material.abiHash,
        materialUniformContentHash = material.uniformBytes
            .map(Int::toByte)
            .toByteArray()
            .sha256Hex(),
        materialSampledResourceFacts = material.sampledResources.flatMap { resource ->
            resource.identityFacts()
        },
        targetBounds = targetBounds,
        scissorBounds = scissorBounds,
        clipIdentity = clipIdentity,
        blendPlanIdentity = blendPlanIdentity,
        capabilitySnapshotHash = capabilitySnapshotHash,
        textA8Composite = textA8Composite,
        colorGlyphClip = if (this is GPUDrawSemanticPayload.ColorGlyph) {
            when (val clip = requireNotNull(packet.clipExecutionPlan)) {
                is GPUClipExecutionPlan.CoverageMask ->
                    GPUPreparedColorGlyphClipPreflightSeal.CoverageMask(
                        semanticIdentity = clipIdentity,
                        executionPlanIdentity = clip.canonicalIdentity(),
                        resource = requireNotNull(coverageMaskResource),
                        orderingToken = clip.orderingToken.value,
                    )
                else -> GPUPreparedColorGlyphClipPreflightSeal.NonMask(
                    semanticIdentity = clipIdentity,
                    executionPlanIdentity = clip.canonicalIdentity(),
                    analyticRect = (clip as? GPUClipExecutionPlan.AnalyticCoverage)
                        ?.let { analytic ->
                            (analytic.geometry as? GPUClipExecutionGeometry.Rect)
                                ?.bounds
                                ?.let { bounds ->
                                    GPUPreparedColorGlyphAnalyticRectClipFacts(
                                        left = bounds.left,
                                        top = bounds.top,
                                        right = bounds.right,
                                        bottom = bounds.bottom,
                                        scissor = analytic.scissor,
                                        antiAlias = analytic.antiAlias,
                                    )
                                }
                        },
                )
            }
        } else {
            null
        },
        packetAuthority = GPUPreparedTextPacketAuthoritySeal(
            commandIdValue = packet.commandIdValue,
            renderStepIdentity = packet.renderStepId.value,
            renderPipelineKey = requireNotNull(packet.renderPipelineKey).value,
            bindingLayoutHash = packet.bindingLayoutHash,
            uniformSlot = packet.uniformSlot,
            vertexSourceLabel = packet.vertexSourceLabel,
            targetStateHash = packet.targetStateHash,
            scissorBoundsHash = packet.scissorBoundsHash,
        ),
    )
}

private fun GPUDrawSemanticPayload.isPreparedTextSemantic(): Boolean =
    this is GPUDrawSemanticPayload.TextA8 || this is GPUDrawSemanticPayload.ColorGlyph

private fun GPUDrawSemanticPayload.r8Artifact() = when (this) {
    is GPUDrawSemanticPayload.TextA8 -> atlas
    is GPUDrawSemanticPayload.ColorGlyph -> atlas
    else -> error("Only prepared text semantics own an R8 artifact")
}

private fun GPUDrawSemanticPayload.exactR8ArtifactIdentity(): GPUR8ArtifactIdentity =
    r8Artifact().r8ArtifactIdentity

private fun GPUR8FrameResourcePlan.exactR8ArtifactIdentity(): GPUR8ArtifactIdentity =
    r8ArtifactIdentity

private fun ByteArray.sha256Hex(): String =
    buildString(64) {
        MessageDigest.getInstance("SHA-256")
            .digest(this@sha256Hex)
            .forEach { byte ->
                val value = byte.toInt() and 0xff
                append(LOWER_HEX_DIGITS[value ushr 4])
                append(LOWER_HEX_DIGITS[value and 0x0f])
            }
    }

private const val LOWER_HEX_DIGITS = "0123456789abcdef"
