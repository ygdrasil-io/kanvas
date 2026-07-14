package org.graphiks.kanvas.gpu.renderer.resources

import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.collections.immutableSet
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadUploadPlan
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingFact
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingKind
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadBlock
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot

/** Handle-free logical resource identity used before resource preflight. */
sealed interface GPUFrameResourceRef {
    val value: String
}

/** Handle-free logical texture identity used before resource preflight. */
@JvmInline
value class GPUFrameTextureRef(override val value: String) : GPUFrameResourceRef {
    init {
        require(value.isNotBlank()) { "GPUFrameTextureRef.value must not be blank" }
    }
}

/** Handle-free logical buffer identity used before resource preflight. */
@JvmInline
value class GPUFrameBufferRef(override val value: String) : GPUFrameResourceRef {
    init {
        require(value.isNotBlank()) { "GPUFrameBufferRef.value must not be blank" }
    }
}

/** Handle-free logical render-target identity used before resource preflight. */
@JvmInline
value class GPUFrameTargetRef(override val value: String) : GPUFrameResourceRef {
    init {
        require(value.isNotBlank()) { "GPUFrameTargetRef.value must not be blank" }
    }
}

/** Semantic role assigned to one frame resource declaration. */
enum class GPUFrameResourceRole {
    SceneTarget,
    LayerTarget,
    FilterTarget,
    DestinationSnapshot,
    UploadStaging,
    ReadbackStaging,
    VertexData,
    IndexData,
    UniformData,
    StorageData,
    SurfaceOutput,
}

/** WebGPU-like logical usage required by one frame-plan operation. */
enum class GPUFrameResourceUsage {
    RenderAttachment,
    TextureBinding,
    StorageBinding,
    CopySource,
    CopyDestination,
    Vertex,
    Index,
    Uniform,
    Storage,
    MapRead,
}

/** Lifetime class requested before concrete resource allocation. */
enum class GPUFrameResourceLifetime {
    CommandLocal,
    PassLocal,
    LayerLocal,
    RecordingLocal,
    FrameLocal,
    SharedCache,
    ImportedExternal,
    SurfaceLease,
}

/** Discriminator for handle-free topology used by preflight and scratch-pool keying. */
enum class GPUFrameResourceDescriptorKind {
    Texture,
    Buffer,
}

/** Handle-free topology used by preflight and later scratch-pool keying. */
sealed interface GPUFrameResourceDescriptor {
    val kind: GPUFrameResourceDescriptorKind
}

/** Complete logical texture topology retained before allocation. */
data class GPUFrameTextureDescriptor(
    val logicalBounds: GPUPixelBounds,
    val format: GPUColorFormat,
    val sampleCount: Int,
) : GPUFrameResourceDescriptor {
    override val kind = GPUFrameResourceDescriptorKind.Texture

    init {
        require(!logicalBounds.isEmpty) {
            "GPUFrameTextureDescriptor.logicalBounds must not be empty"
        }
        require(sampleCount > 0) { "GPUFrameTextureDescriptor.sampleCount must be positive" }
    }
}

/** Complete logical buffer topology retained before allocation. */
data class GPUFrameBufferDescriptor(
    val byteSize: Long,
    val alignmentBytes: Long,
) : GPUFrameResourceDescriptor {
    override val kind = GPUFrameResourceDescriptorKind.Buffer

    init {
        require(byteSize >= 0L) { "GPUFrameBufferDescriptor.byteSize must be non-negative" }
        require(alignmentBytes > 0L) { "GPUFrameBufferDescriptor.alignmentBytes must be positive" }
    }
}

/** One immutable handle-free use of a logical frame resource. */
data class GPUFrameResourceUse(
    val resource: GPUFrameResourceRef,
    val role: GPUFrameResourceRole,
    val usage: GPUFrameResourceUsage,
    val lifetime: GPUFrameResourceLifetime,
    val write: Boolean,
)

/** One resource declaration that preflight must validate and materialize transactionally. */
class GPUResourcePreparationRequest(
    val resource: GPUFrameResourceRef,
    val descriptor: GPUFrameResourceDescriptor,
    val role: GPUFrameResourceRole,
    usages: Set<GPUFrameResourceUsage>,
    val lifetime: GPUFrameResourceLifetime,
    val byteSize: Long,
    val diagnosticLabel: String,
) {
    val usages: Set<GPUFrameResourceUsage> = immutableSet(usages)

    init {
        require(usages.isNotEmpty()) { "GPUResourcePreparationRequest.usages must not be empty" }
        require(byteSize >= 0L) { "GPUResourcePreparationRequest.byteSize must be non-negative" }
        require(diagnosticLabel.isNotBlank()) {
            "GPUResourcePreparationRequest.diagnosticLabel must not be blank"
        }
        when (resource) {
            is GPUFrameTextureRef, is GPUFrameTargetRef -> require(descriptor is GPUFrameTextureDescriptor) {
                "Texture and target preparation requires GPUFrameTextureDescriptor"
            }
            is GPUFrameBufferRef -> require(descriptor is GPUFrameBufferDescriptor) {
                "Buffer preparation requires GPUFrameBufferDescriptor"
            }
        }
        if (descriptor is GPUFrameBufferDescriptor) {
            require(byteSize == descriptor.byteSize) {
                "Buffer preparation byteSize must match GPUFrameBufferDescriptor.byteSize"
            }
        }
    }
}

/** Handle-free buffer-to-resource upload layout. */
data class GPUUploadLayout(
    val sourceOffsetBytes: Long,
    val bytesPerRow: Long,
    val rowsPerImage: Int,
    val byteSize: Long,
) {
    init {
        require(sourceOffsetBytes >= 0L) { "GPUUploadLayout.sourceOffsetBytes must be non-negative" }
        require(bytesPerRow > 0L) { "GPUUploadLayout.bytesPerRow must be positive" }
        require(rowsPerImage > 0) { "GPUUploadLayout.rowsPerImage must be positive" }
        require(byteSize >= 0L) { "GPUUploadLayout.byteSize must be non-negative" }
    }
}

/** Handle-free texture-copy layout for a bounded destination snapshot. */
data class GPUTextureCopyLayout(
    val bytesPerRow: Long,
    val rowsPerImage: Int,
) {
    init {
        require(bytesPerRow > 0L) { "GPUTextureCopyLayout.bytesPerRow must be positive" }
        require(rowsPerImage > 0) { "GPUTextureCopyLayout.rowsPerImage must be positive" }
    }
}

/** One immutable logical region copied between prepared resources. */
data class GPUResourceCopyRegion(
    val sourceOffsetBytes: Long,
    val destinationOffsetBytes: Long,
    val logicalBounds: GPUPixelBounds?,
    val byteSize: Long,
) {
    init {
        require(sourceOffsetBytes >= 0L) { "GPUResourceCopyRegion.sourceOffsetBytes must be non-negative" }
        require(destinationOffsetBytes >= 0L) {
            "GPUResourceCopyRegion.destinationOffsetBytes must be non-negative"
        }
        require(byteSize >= 0L) { "GPUResourceCopyRegion.byteSize must be non-negative" }
    }
}

/**
 * Target-scoped coordinator for resource preparation before submission.
 *
 * The context carries Kanvas-owned evidence labels, not backend handles. [targetId], [frameId],
 * [deviceGeneration], and [budgetClass] are not constructor-enforced beyond their Kotlin types.
 * Providers must refuse the stale, mismatched, or over-budget facts they validate before returning
 * a materialized decision; producers remain responsible for avoiding ambiguous blank labels in
 * evidence intended for promotion.
 */
data class GPUTargetPreparationContext(
    val targetId: String,
    val frameId: String,
    val deviceGeneration: Long,
    val budgetClass: String,
)

/**
 * Texture descriptor containing topology, usage, and format facts.
 *
 * Positive [width], positive [height], and non-blank [format] are constructor-enforced because they
 * define the texture topology. [sampleCount] and [usageLabels] remain evidence facts: missing
 * usage labels must be reported through [validateRequiredUsage], and invalid sample policies must
 * be refused by the producer or provider before materialization.
 */
data class GPUTextureDescriptor(
    val width: Int,
    val height: Int,
    val format: String,
    val usageLabels: Set<String>,
    val sampleCount: Int = 1,
) {
    init {
        require(width > 0) { "GPUTextureDescriptor.width must be positive" }
        require(height > 0) { "GPUTextureDescriptor.height must be positive" }
        require(format.isNotBlank()) { "GPUTextureDescriptor.format must not be blank" }
    }

    /** Returns a diagnostic when required usage labels are absent. */
    fun validateRequiredUsage(
        requiredUsageLabels: Set<String>,
        resourceLabel: String,
    ): GPUResourceDiagnostic? {
        val missing = requiredUsageLabels - usageLabels
        return if (missing.isEmpty()) {
            null
        } else {
            GPUResourceDiagnostic.textureUsageMissing(
                resourceLabel = resourceLabel,
                missingUsageLabels = missing,
                availableUsageLabels = usageLabels,
            )
        }
    }
}

/** Texture view descriptor consumed by binding and resource plans. */
data class GPUTextureViewDescriptor(
    val textureDescriptorHash: String,
    val viewDimension: String,
    val mipRange: IntRange,
    val arrayLayerRange: IntRange,
)

/** Sampler descriptor consumed by material and resource plans. */
data class GPUSamplerDescriptor(
    val addressModeU: String,
    val addressModeV: String,
    val magFilter: String,
    val minFilter: String,
    val mipmapFilter: String,
    val lodMinClamp: String = "0",
    val lodMaxClamp: String = "0",
    val compareMode: String = "none",
    val maxAnisotropy: Int = 1,
    val capabilityRequirements: Set<String> = emptySet(),
)

/**
 * Ownership plan for textures, imports, uploads, and surface leases.
 *
 * This is a Kanvas-owned lifetime contract, not Graphite ownership and not a proxy for a backend
 * handle. Producers and providers must reject blank labels, unsupported lifetime classes, or
 * incompatible release policies before materialization rather than laundering them into cache keys
 * or PM evidence.
 */
data class GPUTextureOwnershipPlan(
    val ownerLabel: String,
    val lifetimeClass: String,
    val releasePolicy: String,
    val canAliasScratch: Boolean,
)

/** Descriptor for an imported texture handle. */
data class GPUImportedTextureDescriptor(
    val externalId: String,
    val descriptor: GPUTextureDescriptor,
    val releasePolicy: String,
)

/** Concrete texture reference scoped by owner and device generation. */
@JvmInline
value class GPUTextureResourceRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUTextureResourceRef.value must not be blank" }
    }
}

/** Backend-neutral role for a resource named by materialization preimage evidence. */
enum class GPUMaterializedResourceRole {
    /** Texture sampled by a material or shader route. */
    SampledTexture,
    /** Sampler descriptor binding with no live sampler object. */
    Sampler,
    /** Pass-local copy used for destination-read sampling. */
    DestinationCopyTexture,
    /** Already validated intermediate texture used by a live route plan. */
    IntermediateTexture,
    /** Isolated saveLayer render target texture. */
    LayerTargetTexture,
    /** Depth/stencil attachment facts for a stencil-cover pass. */
    StencilAttachment,
    /** Generic pass resource evidence when a narrower role is unavailable. */
    PassResource,
}

/** Backend-neutral kind for a live command operand owned by resource materialization. */
enum class GPUMaterializedCommandOperandKind {
    /** Render pipeline selected before `setPipeline`. */
    RenderPipeline,
    /** Compute pipeline selected before a compute dispatch. */
    ComputePipeline,
    /** Uniform buffer bound through a bind group. */
    UniformBuffer,
    /** Storage buffer bound through a bind group. */
    StorageBuffer,
    /** Vertex buffer consumed by draw commands. */
    VertexBuffer,
    /** Index buffer consumed by indexed draw commands. */
    IndexBuffer,
    /** Backend bind group or descriptor set facade object. */
    BindGroup,
    /** Texture resource used by a pass or copy. */
    Texture,
    /** Texture view used by an attachment or sampled binding. */
    TextureView,
    /** Sampler object used by sampled texture bindings. */
    Sampler,
    /** Current render target attachment. */
    RenderTarget,
    /** Current depth/stencil attachment for stencil or depth-producing passes. */
    DepthStencilAttachment,
    /** Destination-copy texture used by read-before-write effects. */
    DestinationCopyTexture,
    /** Readback buffer or texture-copy destination. */
    ReadbackResource,
}

/**
 * Provider-owned command operand reference.
 *
 * This is the live-resource handoff boundary for command streams. It carries
 * scoped, dumpable facts that prove a provider validated generation, owner
 * scope, usage, and invalidation policy without exposing raw backend objects or
 * making the reference durable key material.
 */
data class GPUMaterializedCommandOperandReference(
    val label: String,
    val kind: GPUMaterializedCommandOperandKind,
    val descriptorHash: String,
    val deviceGeneration: Long,
    val ownerScope: String,
    val usageLabels: List<String>,
    val invalidationPolicy: String,
    val evidenceFacts: Map<String, String> = emptyMap(),
) {
    internal val dumpUsageLabelsSnapshot: List<String> = usageLabels.toList()
    internal val dumpEvidenceFactsSnapshot: Map<String, String> = evidenceFacts.toMap()

    init {
        require(label.isNotBlank()) { "GPUMaterializedCommandOperandReference.label must not be blank" }
        requireDumpSafeValue("GPUMaterializedCommandOperandReference.label", label)
        require(descriptorHash.isNotBlank()) {
            "GPUMaterializedCommandOperandReference.descriptorHash must not be blank"
        }
        requireDumpSafeValue("GPUMaterializedCommandOperandReference.descriptorHash", descriptorHash)
        require(deviceGeneration >= 0L) {
            "GPUMaterializedCommandOperandReference.deviceGeneration must be non-negative"
        }
        require(ownerScope.isNotBlank()) {
            "GPUMaterializedCommandOperandReference.ownerScope must not be blank"
        }
        requireDumpSafeValue("GPUMaterializedCommandOperandReference.ownerScope", ownerScope)
        require(usageLabels.none { label -> label.isBlank() }) {
            "GPUMaterializedCommandOperandReference.usageLabels must not contain blank labels"
        }
        usageLabels.forEach { label ->
            requireDumpSafeValue("GPUMaterializedCommandOperandReference.usageLabels", label)
        }
        require(invalidationPolicy.isNotBlank()) {
            "GPUMaterializedCommandOperandReference.invalidationPolicy must not be blank"
        }
        requireDumpSafeValue("GPUMaterializedCommandOperandReference.invalidationPolicy", invalidationPolicy)
        require(evidenceFacts.keys.none { key -> key.isBlank() }) {
            "GPUMaterializedCommandOperandReference.evidenceFacts must not contain blank keys"
        }
        require(evidenceFacts.values.none { value -> value.isBlank() }) {
            "GPUMaterializedCommandOperandReference.evidenceFacts must not contain blank values"
        }
        evidenceFacts.forEach { (key, value) ->
            requireDumpSafeValue("GPUMaterializedCommandOperandReference.evidenceFacts key", key)
            requireDumpSafeValue("GPUMaterializedCommandOperandReference.evidenceFacts value", value)
        }
    }
}

/** Provider output that binds a materialized operand to a pass command. */
data class GPUMaterializedCommandOperandBinding(
    val packetId: String? = null,
    val commandLabel: String,
    val operand: GPUMaterializedCommandOperandReference,
) {
    init {
        require(packetId == null || packetId.isNotBlank()) {
            "GPUMaterializedCommandOperandBinding.packetId must not be blank"
        }
        packetId?.let { value ->
            requireDumpSafeValue("GPUMaterializedCommandOperandBinding.packetId", value)
        }
        require(commandLabel.isNotBlank()) {
            "GPUMaterializedCommandOperandBinding.commandLabel must not be blank"
        }
        requireDumpSafeValue("GPUMaterializedCommandOperandBinding.commandLabel", commandLabel)
    }
}

/** Provider input for one command-stream operand that must be materialized. */
data class GPUCommandOperandMaterializationPlan(
    val packetId: String? = null,
    val commandLabel: String,
    val label: String,
    val kind: GPUMaterializedCommandOperandKind,
    val descriptorHash: String,
    val deviceGeneration: Long,
    val ownerScope: String,
    val requiredUsageLabels: Set<String>,
    val availableUsageLabels: Set<String>,
    val invalidationPolicy: String,
    val evidenceFacts: Map<String, String> = emptyMap(),
    val evictedReason: String? = null,
) {
    internal val dumpRequiredUsageLabelsSnapshot: Set<String> = requiredUsageLabels.toSet()
    internal val dumpAvailableUsageLabelsSnapshot: Set<String> = availableUsageLabels.toSet()
    internal val dumpEvidenceFactsSnapshot: Map<String, String> = evidenceFacts.toMap()

    init {
        require(packetId == null || packetId.isNotBlank()) {
            "GPUCommandOperandMaterializationPlan.packetId must not be blank"
        }
        packetId?.let { value ->
            requireDumpSafeValue("GPUCommandOperandMaterializationPlan.packetId", value)
        }
        require(commandLabel.isNotBlank()) {
            "GPUCommandOperandMaterializationPlan.commandLabel must not be blank"
        }
        requireDumpSafeValue("GPUCommandOperandMaterializationPlan.commandLabel", commandLabel)
        require(label.isNotBlank()) { "GPUCommandOperandMaterializationPlan.label must not be blank" }
        requireDumpSafeValue("GPUCommandOperandMaterializationPlan.label", label)
        require(descriptorHash.isNotBlank()) {
            "GPUCommandOperandMaterializationPlan.descriptorHash must not be blank"
        }
        requireDumpSafeValue("GPUCommandOperandMaterializationPlan.descriptorHash", descriptorHash)
        require(deviceGeneration >= 0L) {
            "GPUCommandOperandMaterializationPlan.deviceGeneration must be non-negative"
        }
        require(ownerScope.isNotBlank()) {
            "GPUCommandOperandMaterializationPlan.ownerScope must not be blank"
        }
        requireDumpSafeValue("GPUCommandOperandMaterializationPlan.ownerScope", ownerScope)
        require(requiredUsageLabels.none { label -> label.isBlank() }) {
            "GPUCommandOperandMaterializationPlan.requiredUsageLabels must not contain blank labels"
        }
        requiredUsageLabels.forEach { label ->
            requireDumpSafeValue("GPUCommandOperandMaterializationPlan.requiredUsageLabels", label)
        }
        require(availableUsageLabels.none { label -> label.isBlank() }) {
            "GPUCommandOperandMaterializationPlan.availableUsageLabels must not contain blank labels"
        }
        availableUsageLabels.forEach { label ->
            requireDumpSafeValue("GPUCommandOperandMaterializationPlan.availableUsageLabels", label)
        }
        require(invalidationPolicy.isNotBlank()) {
            "GPUCommandOperandMaterializationPlan.invalidationPolicy must not be blank"
        }
        requireDumpSafeValue("GPUCommandOperandMaterializationPlan.invalidationPolicy", invalidationPolicy)
        require(evidenceFacts.keys.none { key -> key.isBlank() }) {
            "GPUCommandOperandMaterializationPlan.evidenceFacts must not contain blank keys"
        }
        require(evidenceFacts.values.none { value -> value.isBlank() }) {
            "GPUCommandOperandMaterializationPlan.evidenceFacts must not contain blank values"
        }
        evidenceFacts.forEach { (key, value) ->
            requireDumpSafeValue("GPUCommandOperandMaterializationPlan.evidenceFacts key", key)
            requireDumpSafeValue("GPUCommandOperandMaterializationPlan.evidenceFacts value", value)
        }
        require(evictedReason == null || evictedReason.isNotBlank()) {
            "GPUCommandOperandMaterializationPlan.evictedReason must not be blank"
        }
        evictedReason?.let { reason ->
            requireDumpSafeValue("GPUCommandOperandMaterializationPlan.evictedReason", reason)
        }
    }
}

/** Provider request for all operands required by one command-stream bridge. */
data class GPUCommandOperandMaterializationRequest(
    val targetId: String,
    val taskIds: List<String> = emptyList(),
    val resourcePlanLabels: List<String> = emptyList(),
    val operands: List<GPUCommandOperandMaterializationPlan>,
) {
    internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
    internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
    internal val dumpOperandsSnapshot: List<GPUCommandOperandMaterializationPlan> = operands.toList()

    init {
        require(targetId.isNotBlank()) { "GPUCommandOperandMaterializationRequest.targetId must not be blank" }
        requireDumpSafeValue("GPUCommandOperandMaterializationRequest.targetId", targetId)
        require(taskIds.none { taskId -> taskId.isBlank() }) {
            "GPUCommandOperandMaterializationRequest.taskIds must not contain blank labels"
        }
        taskIds.forEach { taskId ->
            requireDumpSafeValue("GPUCommandOperandMaterializationRequest.taskIds", taskId)
        }
        require(resourcePlanLabels.none { label -> label.isBlank() }) {
            "GPUCommandOperandMaterializationRequest.resourcePlanLabels must not contain blank labels"
        }
        resourcePlanLabels.forEach { label ->
            requireDumpSafeValue("GPUCommandOperandMaterializationRequest.resourcePlanLabels", label)
        }
        require(operands.isNotEmpty()) {
            "GPUCommandOperandMaterializationRequest.operands must not be empty"
        }
    }
}

/**
 * Evidence-only resource reference used by materialization preimage scaffolds.
 *
 * This names the descriptor, role, generation, lifetime, and non-handle facts
 * that a future resource provider would need. It is not a backend object, does
 * not carry a live handle, and must remain dumpable without adapter access.
 */
data class GPUMaterializedResourceReference(
    val label: String,
    val role: GPUMaterializedResourceRole,
    val descriptorHash: String,
    val generation: Long,
    val lifetimeClass: String,
    val usageLabels: List<String> = emptyList(),
    val evidenceFacts: Map<String, String> = emptyMap(),
) {
    internal val dumpUsageLabelsSnapshot: List<String> = usageLabels.toList()
    internal val dumpEvidenceFactsSnapshot: Map<String, String> = evidenceFacts.toMap()

    init {
        require(label.isNotBlank()) { "GPUMaterializedResourceReference.label must not be blank" }
        require(descriptorHash.isNotBlank()) {
            "GPUMaterializedResourceReference.descriptorHash must not be blank"
        }
        require(generation >= 0) { "GPUMaterializedResourceReference.generation must not be negative" }
        require(lifetimeClass.isNotBlank()) {
            "GPUMaterializedResourceReference.lifetimeClass must not be blank"
        }
        require(usageLabels.none { label -> label.isBlank() }) {
            "GPUMaterializedResourceReference.usageLabels must not contain blank labels"
        }
        require(evidenceFacts.keys.none { key -> key.isBlank() }) {
            "GPUMaterializedResourceReference.evidenceFacts must not contain blank keys"
        }
        require(evidenceFacts.values.none { value -> value.isBlank() }) {
            "GPUMaterializedResourceReference.evidenceFacts must not contain blank values"
        }
    }
}

/**
 * Non-claim flags attached to resource materialization preimage scaffolds.
 *
 * The scaffold is intentionally evidence-only. These flags are constructor
 * guarded so a preimage cannot accidentally become an adapter-backed, live
 * handle, product-route, provider, or submit claim.
 */
data class GPUResourceMaterializationNonClaims(
    val adapterBacked: Boolean = false,
    val liveHandles: Boolean = false,
    val productRoute: Boolean = false,
    val providerCalled: Boolean = false,
    val submitCalled: Boolean = false,
) {
    init {
        require(!adapterBacked) { "GPUResourceMaterializationNonClaims.adapterBacked must stay false" }
        require(!liveHandles) { "GPUResourceMaterializationNonClaims.liveHandles must stay false" }
        require(!productRoute) { "GPUResourceMaterializationNonClaims.productRoute must stay false" }
        require(!providerCalled) { "GPUResourceMaterializationNonClaims.providerCalled must stay false" }
        require(!submitCalled) { "GPUResourceMaterializationNonClaims.submitCalled must stay false" }
    }
}

/**
 * Backend-neutral preimage for future resource materialization.
 *
 * Accepted plans list descriptor-backed references and optional binding labels;
 * refused plans carry a stable reason code and no resources. Both forms are
 * deterministic evidence and never invoke a provider, submit work, or expose
 * adapter-backed handles.
 */
data class GPUResourceMaterializationPreimagePlan(
    val planLabel: String,
    val sourceGate: String,
    val accepted: Boolean,
    val resources: List<GPUMaterializedResourceReference>,
    val bindingLabels: List<String> = emptyList(),
    val refusalCode: String? = null,
    val nonClaims: GPUResourceMaterializationNonClaims = GPUResourceMaterializationNonClaims(),
) {
    internal val dumpResourcesSnapshot: List<GPUMaterializedResourceReference> = resources.toList()
    internal val dumpBindingLabelsSnapshot: List<String> = bindingLabels.toList()

    init {
        require(planLabel.isNotBlank()) { "GPUResourceMaterializationPreimagePlan.planLabel must not be blank" }
        require(sourceGate.isNotBlank()) { "GPUResourceMaterializationPreimagePlan.sourceGate must not be blank" }
        require(bindingLabels.none { label -> label.isBlank() }) {
            "GPUResourceMaterializationPreimagePlan.bindingLabels must not contain blank labels"
        }
        if (accepted) {
            require(refusalCode == null) {
                "accepted GPUResourceMaterializationPreimagePlan must not carry refusalCode"
            }
            require(resources.isNotEmpty()) {
                "accepted GPUResourceMaterializationPreimagePlan must name at least one resource"
            }
        } else {
            require(!refusalCode.isNullOrBlank()) {
                "refused GPUResourceMaterializationPreimagePlan must carry refusalCode"
            }
            require(resources.isEmpty()) {
                "refused GPUResourceMaterializationPreimagePlan must not name resources"
            }
        }
    }
}

/** Token ordering reads, writes, uploads, copies, and mutations. */
@JvmInline
value class GPUUseToken(val value: Long)

/** Token preventing reuse while a resource has pending reads. */
@JvmInline
value class GPUPendingReadToken(val value: Long)

/** Token preventing reads before a pending write completes. */
@JvmInline
value class GPUPendingWriteToken(val value: Long)

/** Scratch allocation token scoped to a limited lifetime. */
@JvmInline
value class GPUScratchResourceToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUScratchResourceToken.value must not be blank" }
    }
}

/** Intermediate resource token for layer, filter, or destination work. */
@JvmInline
value class GPUIntermediateResourceToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUIntermediateResourceToken.value must not be blank" }
    }
}

/** Allocation plan for a new renderer-owned texture. */
sealed interface GPUTextureAllocationPlan {
    /**
     * Reuse an existing typed GPU texture reference.
     *
     * [ref] may identify a live backend object and is never dumped. [planLabel]
     * is the stable non-handle evidence label used in PM resource-plan dumps.
     */
    data class ExistingGPUResource(
        val ref: GPUTextureResourceRef,
        val planLabel: String = "existing-gpu-resource",
    ) : GPUTextureAllocationPlan {
        init {
            require(planLabel.isNotBlank()) { "GPUTextureAllocationPlan.ExistingGPUResource.planLabel must not be blank" }
        }
    }

    /** Create a new texture from a descriptor. */
    data class CreateTexture(val descriptor: GPUTextureDescriptor, val ownership: GPUTextureOwnershipPlan) : GPUTextureAllocationPlan

    /** Upload pixels from a typed artifact into a texture. */
    data class UploadFromArtifact(val artifactKey: String, val descriptor: GPUTextureDescriptor) : GPUTextureAllocationPlan {
        init {
            require(artifactKey.isNotBlank()) {
                "GPUTextureAllocationPlan.UploadFromArtifact.artifactKey must not be blank"
            }
            requireDumpSafeValue("GPUTextureAllocationPlan.UploadFromArtifact.artifactKey", artifactKey)
        }
    }

    /**
     * Import an external texture through the facade.
     *
     * [descriptor.externalId] may be a process-local handle and is never used
     * as a dump label. [planLabel] is the stable non-handle evidence label.
     */
    data class ImportExternalTexture(
        val descriptor: GPUImportedTextureDescriptor,
        val planLabel: String = "imported-external-texture",
    ) : GPUTextureAllocationPlan {
        init {
            require(planLabel.isNotBlank()) { "GPUTextureAllocationPlan.ImportExternalTexture.planLabel must not be blank" }
        }
    }

    /**
     * Lease the current surface texture for this frame.
     *
     * A lease is frame-scoped. Providers must revalidate target id, frame/device generation, usage
     * labels, and active-attachment sampling before returning `Materialized`; failures remain
     * explicit `Refused` resource decisions.
     */
    data class LeaseSurfaceTexture(val targetId: String) : GPUTextureAllocationPlan

    /** Refuse allocation before any backend work occurs. */
    data class Refuse(val diagnostic: GPUResourceDiagnostic) : GPUTextureAllocationPlan
}

/**
 * Late materialization decision for resources, pipelines, atlases, and uploads.
 *
 * The resource package owns these decisions after analysis and before command
 * submission. Decisions may report materialized typed resources, defer until
 * later frame facts are available, or refuse with a terminal diagnostic. Dump
 * evidence must stay deterministic and must not expose concrete backend handles.
 */
sealed interface GPUResourceMaterializationDecision {
    companion object {
        /** Builds a refused decision for upload or staging budget exhaustion. */
        fun refusedUploadBudgetExceeded(
            resourceLabel: String,
            requestedBytes: Long,
            budgetBytes: Long,
        ): GPUResourceMaterializationDecision =
            Refused(
                GPUResourceDiagnostic.uploadBudgetExceeded(
                    resourceLabel = resourceLabel,
                    requestedBytes = requestedBytes,
                    budgetBytes = budgetBytes,
                ),
            )

        /** Builds a refused decision for render pipeline creation failure. */
        fun refusedPipelineCreationFailure(
            resourceLabel: String,
            reason: String,
        ): GPUResourceMaterializationDecision =
            Refused(
                GPUResourceDiagnostic.pipelineCreationFailure(
                    resourceLabel = resourceLabel,
                    reason = reason,
                ),
            )
    }

    /**
     * Resource materialization produced concrete typed references.
     *
     * [resources] are live typed references and are intentionally summarized by
     * count in durable dumps. [targetId], [taskIds], and [resourcePlanLabels]
     * provide PM evidence context without becoming backend handles or product
     * route activation.
     */
    data class Materialized(
        val resources: List<GPUTextureResourceRef>,
        val diagnostics: List<GPUResourceDiagnostic> = emptyList(),
        val targetId: String = UNSPECIFIED_DUMP_VALUE,
        val taskIds: List<String> = emptyList(),
        val resourcePlanLabels: List<String> = emptyList(),
        val operandRefs: List<GPUMaterializedCommandOperandReference> = emptyList(),
        val operandBridge: List<GPUMaterializedCommandOperandBinding> = emptyList(),
        val payloadTelemetry: List<GPUPayloadMaterializationTelemetryEvent> = emptyList(),
        val resourceLeases: List<GPUResourceLease> = emptyList(),
    ) : GPUResourceMaterializationDecision {
        internal val dumpResourcesSnapshot: List<GPUTextureResourceRef> = resources.toList()
        internal val dumpDiagnosticsSnapshot: List<GPUResourceDiagnostic> = diagnostics.toList()
        internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
        internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
        internal val dumpOperandRefsSnapshot: List<GPUMaterializedCommandOperandReference> = operandRefs.toList()
        internal val dumpOperandBridgeSnapshot: List<GPUMaterializedCommandOperandBinding> = operandBridge.toList()
        internal val dumpPayloadTelemetrySnapshot: List<GPUPayloadMaterializationTelemetryEvent> =
            payloadTelemetry.toList()
        internal val dumpResourceLeaseSnapshot: List<GPUResourceLease> = resourceLeases.toList()
    }

    /**
     * Resource materialization is deferred until later frame evidence exists.
     *
     * Deferred decisions are neither success nor support claims. They retain a
     * stable [reasonCode] plus target, task, and resource-plan context so PM
     * bundles can distinguish explicit waiting from refusal.
     */
    data class Deferred(
        val reasonCode: String,
        val diagnostics: List<GPUResourceDiagnostic> = emptyList(),
        val targetId: String = UNSPECIFIED_DUMP_VALUE,
        val taskIds: List<String> = emptyList(),
        val resourcePlanLabels: List<String> = emptyList(),
    ) : GPUResourceMaterializationDecision {
        internal val dumpDiagnosticsSnapshot: List<GPUResourceDiagnostic> = diagnostics.toList()
        internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
        internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
    }

    /**
     * Resource materialization is refused and must be reported.
     *
     * Refusal is terminal for the materialization attempt and must not be
     * reinterpreted as CPU fallback or partial success. Context labels are
     * evidence only and must remain stable, non-handle facts. [diagnostic] is
     * the primary refusal code for callers that only need a single reason;
     * [diagnostics] preserves the complete ordered refusal set for PM dumps and
     * execution preflight evidence.
     */
    data class Refused(
        val diagnostic: GPUResourceDiagnostic,
        val targetId: String = UNSPECIFIED_DUMP_VALUE,
        val taskIds: List<String> = emptyList(),
        val resourcePlanLabels: List<String> = emptyList(),
        val diagnostics: List<GPUResourceDiagnostic> = listOf(diagnostic),
        val payloadTelemetry: List<GPUPayloadMaterializationTelemetryEvent> = emptyList(),
        val resourceLeases: List<GPUResourceLease> = emptyList(),
    ) : GPUResourceMaterializationDecision {
        internal val dumpDiagnosticsSnapshot: List<GPUResourceDiagnostic> = diagnostics.toList()
        internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
        internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
        internal val dumpPayloadTelemetrySnapshot: List<GPUPayloadMaterializationTelemetryEvent> =
            payloadTelemetry.toList()
        internal val dumpResourceLeaseSnapshot: List<GPUResourceLease> = resourceLeases.toList()

        init {
            require(diagnostics.isNotEmpty()) {
                "GPUResourceMaterializationDecision.Refused.diagnostics must not be empty"
            }
            require(diagnostics.first() == diagnostic) {
                "GPUResourceMaterializationDecision.Refused.diagnostics must start with diagnostic"
            }
        }
    }
}

/** Result of a payload upload or bind-group materialization attempt. */
enum class GPUPayloadMaterializationTelemetryResult(val dumpToken: String) {
    /** Provider created a new upload buffer or bind group facade. */
    Create("create"),
    /** Provider reused a matching upload buffer or bind group facade. */
    Reuse("reuse"),
    /** Provider refused before returning an upload buffer or bind group facade. */
    Failure("failure"),
}

/**
 * Non-promotional telemetry for payload materialization.
 *
 * The event records provider-owned create/reuse/failure facts only. It does not
 * carry route support fields, backend handles, or product activation.
 */
data class GPUPayloadMaterializationTelemetryEvent(
    val lane: String,
    val result: GPUPayloadMaterializationTelemetryResult,
    val keyHash: String,
    val subjectHash: String,
    val productRouteActivated: Boolean = false,
) {
    init {
        require(lane in payloadMaterializationTelemetryLanes) {
            "GPUPayloadMaterializationTelemetryEvent.lane must be one of $payloadMaterializationTelemetryLanes"
        }
        require(keyHash.isNotBlank()) { "GPUPayloadMaterializationTelemetryEvent.keyHash must not be blank" }
        require(subjectHash.isNotBlank()) { "GPUPayloadMaterializationTelemetryEvent.subjectHash must not be blank" }
        requireDumpSafeValue("GPUPayloadMaterializationTelemetryEvent.keyHash", keyHash)
        requireDumpSafeValue("GPUPayloadMaterializationTelemetryEvent.subjectHash", subjectHash)
        require(!productRouteActivated) {
            "GPUPayloadMaterializationTelemetryEvent.productRouteActivated must stay false"
        }
    }
}

/**
 * Provider input for one pass-local payload upload and bind-group materialization.
 *
 * Payload slot identifiers remain pass-local labels. The request contains only
 * dumpable layout, generation, usage, and budget facts; backend handles remain
 * provider-owned and out of durable keys.
 */
data class GPUPayloadMaterializationRequest(
    val targetId: String,
    val packetId: String,
    val taskIds: List<String> = emptyList(),
    val resourcePlanLabels: List<String> = emptyList(),
    val uniformBlock: GPUUniformPayloadBlock,
    val uniformSlot: GPUUniformPayloadSlot,
    val resourceBlock: GPUResourceBindingBlock,
    val resourceSlot: GPUResourceBindingSlot,
    val uploadPlan: GPUPayloadUploadPlan,
    val reflectedBindingLayoutHash: String,
    val deviceGeneration: Long,
    val payloadGeneration: Long,
    val alignmentBytes: Long,
    val uploadBudgetBytes: Long,
    val uploadCapabilityAvailable: Boolean,
    val maxDynamicOffsets: Int,
    val requiredUniformUsageLabels: Set<String>,
    val availableUniformUsageLabels: Set<String>,
) {
    internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
    internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
    internal val dumpRequiredUniformUsageLabelsSnapshot: Set<String> = requiredUniformUsageLabels.toSet()
    internal val dumpAvailableUniformUsageLabelsSnapshot: Set<String> = availableUniformUsageLabels.toSet()

    init {
        require(targetId.isNotBlank()) { "GPUPayloadMaterializationRequest.targetId must not be blank" }
        require(packetId.isNotBlank()) { "GPUPayloadMaterializationRequest.packetId must not be blank" }
        require(reflectedBindingLayoutHash.isNotBlank()) {
            "GPUPayloadMaterializationRequest.reflectedBindingLayoutHash must not be blank"
        }
        require(deviceGeneration >= 0L) { "GPUPayloadMaterializationRequest.deviceGeneration must be non-negative" }
        require(payloadGeneration >= 0L) { "GPUPayloadMaterializationRequest.payloadGeneration must be non-negative" }
        require(alignmentBytes > 0L) { "GPUPayloadMaterializationRequest.alignmentBytes must be positive" }
        require(uploadBudgetBytes >= 0L) { "GPUPayloadMaterializationRequest.uploadBudgetBytes must be non-negative" }
        require(maxDynamicOffsets >= 0) { "GPUPayloadMaterializationRequest.maxDynamicOffsets must be non-negative" }
        require(taskIds.none { taskId -> taskId.isBlank() }) {
            "GPUPayloadMaterializationRequest.taskIds must not contain blank labels"
        }
        require(resourcePlanLabels.none { label -> label.isBlank() }) {
            "GPUPayloadMaterializationRequest.resourcePlanLabels must not contain blank labels"
        }
        require(requiredUniformUsageLabels.none { label -> label.isBlank() }) {
            "GPUPayloadMaterializationRequest.requiredUniformUsageLabels must not contain blank labels"
        }
        require(availableUniformUsageLabels.none { label -> label.isBlank() }) {
            "GPUPayloadMaterializationRequest.availableUniformUsageLabels must not contain blank labels"
        }
        listOf(
            "GPUPayloadMaterializationRequest.targetId" to targetId,
            "GPUPayloadMaterializationRequest.packetId" to packetId,
            "GPUPayloadMaterializationRequest.reflectedBindingLayoutHash" to reflectedBindingLayoutHash,
            "GPUPayloadMaterializationRequest.uniformSlot" to uniformSlot.slotId.value,
            "GPUPayloadMaterializationRequest.resourceSlot" to resourceSlot.slotId.value,
            "GPUPayloadMaterializationRequest.uploadPlan" to uploadPlan.planHash,
            "GPUPayloadMaterializationRequest.uploadScope" to uploadPlan.stagingScope,
        ).forEach { (field, value) -> requireDumpSafeValue(field, value) }
        taskIds.forEach { taskId -> requireDumpSafeValue("GPUPayloadMaterializationRequest.taskIds", taskId) }
        resourcePlanLabels.forEach { label ->
            requireDumpSafeValue("GPUPayloadMaterializationRequest.resourcePlanLabels", label)
        }
        resourceBlock.resourceDescriptorLabels.forEach { label ->
            requireDumpSafeValue("GPUPayloadMaterializationRequest.resourceDescriptorLabels", label)
        }
        resourceBlock.bindingFacts.forEach { fact ->
            requireDumpSafeValue("GPUPayloadMaterializationRequest.bindingFact.label", fact.bindingLabel)
            requireDumpSafeValue("GPUPayloadMaterializationRequest.bindingFact.descriptorHash", fact.descriptorHash)
            fact.requiredUsageLabels.forEach { label ->
                requireDumpSafeValue("GPUPayloadMaterializationRequest.bindingFact.requiredUsageLabels", label)
            }
            fact.availableUsageLabels.forEach { label ->
                requireDumpSafeValue("GPUPayloadMaterializationRequest.bindingFact.availableUsageLabels", label)
            }
            fact.evictedReason?.let { reason ->
                requireDumpSafeValue("GPUPayloadMaterializationRequest.bindingFact.evictedReason", reason)
            }
        }
        requiredUniformUsageLabels.forEach { label ->
            requireDumpSafeValue("GPUPayloadMaterializationRequest.requiredUniformUsageLabels", label)
        }
        availableUniformUsageLabels.forEach { label ->
            requireDumpSafeValue("GPUPayloadMaterializationRequest.availableUniformUsageLabels", label)
        }
    }
}

/**
 * Provider input for one accepted sampled texture + sampler binding.
 *
 * This is the live materialization lane for KGPU-M11-004. It consumes the
 * descriptor and ownership facts accepted by the M4 sampler/upload gates and
 * asks the resource provider for texture, texture-view, and sampler operands.
 * The request contains no backend handles; upload and allocation failures stay
 * stable diagnostics instead of becoming hidden CPU-rendered textures.
 */
data class GPUTextureSamplerMaterializationRequest(
    val targetId: String,
    val packetId: String,
    val taskIds: List<String> = emptyList(),
    val resourcePlanLabels: List<String> = emptyList(),
    val allocation: GPUTextureAllocationPlan,
    val ownership: GPUTextureOwnershipPlan,
    val textureDescriptor: GPUTextureDescriptor,
    val viewDescriptor: GPUTextureViewDescriptor,
    val samplerDescriptor: GPUSamplerDescriptor,
    val binding: GPUSampledTextureBinding,
    val bindingLayoutHash: String,
    val deviceGeneration: Long,
    val expectedResourceGeneration: Long,
    val actualResourceGeneration: Long,
    val requiredTextureUsageLabels: Set<String>,
    val availableTextureUsageLabels: Set<String>,
    val requiredMipLevels: Int,
    val uploadBytes: Long,
    val uploadBudgetBytes: Long,
    val uploadCapabilityAvailable: Boolean,
    val swizzleRequired: Boolean = false,
    val unsupportedSamplingReason: String? = null,
    val uploadFailedReason: String? = null,
    val activeAttachmentSampled: Boolean = false,
) {
    internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
    internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
    internal val dumpRequiredTextureUsageLabelsSnapshot: Set<String> = requiredTextureUsageLabels.toSet()
    internal val dumpAvailableTextureUsageLabelsSnapshot: Set<String> = availableTextureUsageLabels.toSet()

    init {
        require(targetId.isNotBlank()) { "GPUTextureSamplerMaterializationRequest.targetId must not be blank" }
        require(packetId.isNotBlank()) { "GPUTextureSamplerMaterializationRequest.packetId must not be blank" }
        require(bindingLayoutHash.isNotBlank()) {
            "GPUTextureSamplerMaterializationRequest.bindingLayoutHash must not be blank"
        }
        require(deviceGeneration >= 0L) {
            "GPUTextureSamplerMaterializationRequest.deviceGeneration must be non-negative"
        }
        require(expectedResourceGeneration >= 0L) {
            "GPUTextureSamplerMaterializationRequest.expectedResourceGeneration must be non-negative"
        }
        require(actualResourceGeneration >= 0L) {
            "GPUTextureSamplerMaterializationRequest.actualResourceGeneration must be non-negative"
        }
        require(requiredMipLevels > 0) {
            "GPUTextureSamplerMaterializationRequest.requiredMipLevels must be positive"
        }
        require(uploadBytes >= 0L) { "GPUTextureSamplerMaterializationRequest.uploadBytes must be non-negative" }
        require(uploadBudgetBytes >= 0L) {
            "GPUTextureSamplerMaterializationRequest.uploadBudgetBytes must be non-negative"
        }
        require(taskIds.none { taskId -> taskId.isBlank() }) {
            "GPUTextureSamplerMaterializationRequest.taskIds must not contain blank labels"
        }
        require(resourcePlanLabels.none { label -> label.isBlank() }) {
            "GPUTextureSamplerMaterializationRequest.resourcePlanLabels must not contain blank labels"
        }
        require(requiredTextureUsageLabels.none { label -> label.isBlank() }) {
            "GPUTextureSamplerMaterializationRequest.requiredTextureUsageLabels must not contain blank labels"
        }
        require(availableTextureUsageLabels.none { label -> label.isBlank() }) {
            "GPUTextureSamplerMaterializationRequest.availableTextureUsageLabels must not contain blank labels"
        }
        require(unsupportedSamplingReason == null || unsupportedSamplingReason.isNotBlank()) {
            "GPUTextureSamplerMaterializationRequest.unsupportedSamplingReason must not be blank"
        }
        require(uploadFailedReason == null || uploadFailedReason.isNotBlank()) {
            "GPUTextureSamplerMaterializationRequest.uploadFailedReason must not be blank"
        }
        listOf(
            "GPUTextureSamplerMaterializationRequest.targetId" to targetId,
            "GPUTextureSamplerMaterializationRequest.packetId" to packetId,
            "GPUTextureSamplerMaterializationRequest.bindingLayoutHash" to bindingLayoutHash,
            "GPUTextureSamplerMaterializationRequest.ownerLabel" to ownership.ownerLabel,
            "GPUTextureSamplerMaterializationRequest.bindingLabel" to binding.bindingLabel,
        ).forEach { (field, value) -> requireDumpSafeValue(field, value) }
        taskIds.forEach { taskId -> requireDumpSafeValue("GPUTextureSamplerMaterializationRequest.taskIds", taskId) }
        resourcePlanLabels.forEach { label ->
            requireDumpSafeValue("GPUTextureSamplerMaterializationRequest.resourcePlanLabels", label)
        }
        requiredTextureUsageLabels.forEach { label ->
            requireDumpSafeValue("GPUTextureSamplerMaterializationRequest.requiredTextureUsageLabels", label)
        }
        availableTextureUsageLabels.forEach { label ->
            requireDumpSafeValue("GPUTextureSamplerMaterializationRequest.availableTextureUsageLabels", label)
        }
        unsupportedSamplingReason?.let { reason ->
            requireDumpSafeValue("GPUTextureSamplerMaterializationRequest.unsupportedSamplingReason", reason)
        }
        uploadFailedReason?.let { reason ->
            requireDumpSafeValue("GPUTextureSamplerMaterializationRequest.uploadFailedReason", reason)
        }
    }
}

/**
 * Provider responsible for resolving texture allocation plans inside the resource layer.
 *
 * The default contract is refusal-first: an unconfigured provider must not create
 * backend handles or report success. A plan that already carries `Refuse`
 * evidence preserves that diagnostic unchanged so late materialization failures
 * remain stable and dumpable.
 */
interface GPUResourceProvider {
    /** Materializes one allocation plan or refuses it explicitly. */
    fun materialize(
        plan: GPUTextureAllocationPlan,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        if (plan is GPUTextureAllocationPlan.Refuse) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = plan.diagnostic,
                targetId = context.targetId,
                resourcePlanLabels = listOf(plan.diagnostic.resourceLabel),
            )
        }

        val resourcePlanLabel = plan.resourcePlanLabel()
        return GPUResourceMaterializationDecision.Refused(
            diagnostic = GPUResourceDiagnostic.providerUnconfigured(
                resourceLabel = resourcePlanLabel,
                targetId = context.targetId,
            ),
            targetId = context.targetId,
            resourcePlanLabels = listOf(resourcePlanLabel),
        )
    }

    /** Materializes provider-owned command operands or refuses before encoding/submission. */
    fun materializeCommandOperands(
        request: GPUCommandOperandMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision =
        GPUResourceMaterializationDecision.Refused(
            diagnostic = GPUResourceDiagnostic.providerUnconfigured(
                resourceLabel = request.resourcePlanLabel(),
                targetId = context.targetId,
            ),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
        )

    /** Materializes payload upload buffers and bind groups or refuses before encoding/submission. */
    fun materializePayloadBindings(
        request: GPUPayloadMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision =
        GPUResourceMaterializationDecision.Refused(
            diagnostic = GPUResourceDiagnostic.providerUnconfigured(
                resourceLabel = request.resourcePlanLabel(),
                targetId = context.targetId,
            ),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
            payloadTelemetry = request.payloadTelemetry(GPUPayloadMaterializationTelemetryResult.Failure),
        )

    /** Materializes sampled texture/view/sampler operands or refuses before command encoding. */
    fun materializeTextureSamplerBinding(
        request: GPUTextureSamplerMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision =
        GPUResourceMaterializationDecision.Refused(
            diagnostic = GPUResourceDiagnostic.providerUnconfigured(
                resourceLabel = request.resourcePlanLabel(),
                targetId = context.targetId,
            ),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
        )
}

/** Resource-layer provider that validates command operands without exposing backend handles. */
class ValidatingCommandOperandResourceProvider : GPUResourceProvider {
    override fun materializeCommandOperands(
        request: GPUCommandOperandMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val diagnostics = buildList {
            if (request.targetId != context.targetId) {
                add(
                    GPUResourceDiagnostic.commandOperandTargetMismatch(
                        resourceLabel = request.resourcePlanLabel(),
                        requestTargetId = request.targetId,
                        contextTargetId = context.targetId,
                    ),
                )
            }
            request.dumpOperandsSnapshot.forEach { plan ->
                addAll(plan.validationDiagnostics(expectedDeviceGeneration = context.deviceGeneration))
            }
        }

        if (diagnostics.isNotEmpty()) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostics.first(),
                targetId = context.targetId,
                taskIds = request.dumpTaskIdsSnapshot,
                resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
                diagnostics = diagnostics,
            )
        }

        val bridge = request.dumpOperandsSnapshot.map { plan ->
            GPUMaterializedCommandOperandBinding(
                packetId = plan.packetId,
                commandLabel = plan.commandLabel,
                operand = plan.toMaterializedReference(),
            )
        }

        return GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
            operandBridge = bridge,
        )
    }
}

/** Resource-layer provider that validates pass-local payload uploads and bind groups. */
class ValidatingPayloadResourceProvider : GPUResourceProvider {
    private val uploadedPayloadKeys = linkedSetOf<String>()
    private val bindGroupKeys = linkedSetOf<String>()

    override fun materializePayloadBindings(
        request: GPUPayloadMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val diagnostics = request.validationDiagnostics(expectedDeviceGeneration = context.deviceGeneration)

        if (request.targetId != context.targetId) {
            val targetDiagnostic = GPUResourceDiagnostic.commandOperandTargetMismatch(
                resourceLabel = request.resourcePlanLabel(),
                requestTargetId = request.targetId,
                contextTargetId = context.targetId,
            )
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = targetDiagnostic,
                targetId = context.targetId,
                taskIds = request.dumpTaskIdsSnapshot,
                resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
                diagnostics = listOf(targetDiagnostic) + diagnostics,
                payloadTelemetry = request.payloadTelemetry(GPUPayloadMaterializationTelemetryResult.Failure),
            )
        }

        if (diagnostics.isNotEmpty()) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostics.first(),
                targetId = context.targetId,
                taskIds = request.dumpTaskIdsSnapshot,
                resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
                diagnostics = diagnostics,
                payloadTelemetry = request.payloadTelemetry(GPUPayloadMaterializationTelemetryResult.Failure),
            )
        }

        val uniformLabel = request.uniformBufferOperandLabel()
        val bindGroupLabel = request.bindGroupOperandLabel()
        val uniformResult = if (uploadedPayloadKeys.add(request.uniformCacheKey())) {
            GPUPayloadMaterializationTelemetryResult.Create
        } else {
            GPUPayloadMaterializationTelemetryResult.Reuse
        }
        val bindGroupResult = if (bindGroupKeys.add(request.bindGroupCacheKey())) {
            GPUPayloadMaterializationTelemetryResult.Create
        } else {
            GPUPayloadMaterializationTelemetryResult.Reuse
        }
        val uniformRef = GPUMaterializedCommandOperandReference(
            label = uniformLabel,
            kind = GPUMaterializedCommandOperandKind.UniformBuffer,
            descriptorHash = request.uniformBlock.fingerprint.value,
            deviceGeneration = request.deviceGeneration,
            ownerScope = request.payloadOwnerScope(),
            usageLabels = request.dumpRequiredUniformUsageLabelsSnapshot.sorted(),
            invalidationPolicy = "pass-end",
            evidenceFacts = request.uniformUploadEvidenceFacts(),
        )
        val bindGroupRef = GPUMaterializedCommandOperandReference(
            label = bindGroupLabel,
            kind = GPUMaterializedCommandOperandKind.BindGroup,
            descriptorHash = request.reflectedBindingLayoutHash,
            deviceGeneration = request.deviceGeneration,
            ownerScope = request.payloadOwnerScope(),
            usageLabels = request.bindGroupUsageLabels(),
            invalidationPolicy = "pass-end",
            evidenceFacts = request.bindGroupEvidenceFacts(uniformLabel),
        )
        val resourceBindings = request.resourceBlock.bindingFacts
            .filter { fact ->
                fact.kind == GPUResourceBindingKind.SampledTexture || fact.kind == GPUResourceBindingKind.Sampler
            }
            .map { fact ->
                GPUMaterializedCommandOperandBinding(
                    packetId = request.packetId,
                    commandLabel = "setBindGroup",
                    operand = fact.toPayloadResourceOperandReference(request),
                )
            }

        return GPUResourceMaterializationDecision.Materialized(
            resources = emptyList(),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
            operandBridge = listOf(
                GPUMaterializedCommandOperandBinding(
                    packetId = request.packetId,
                    commandLabel = "setBindGroup",
                    operand = uniformRef,
                ),
                GPUMaterializedCommandOperandBinding(
                    packetId = request.packetId,
                    commandLabel = "setBindGroup",
                    operand = bindGroupRef,
                ),
            ) + resourceBindings,
            payloadTelemetry = listOf(
                request.uniformTelemetry(uniformResult),
                request.bindGroupTelemetry(bindGroupResult),
            ),
        )
    }
}

/** Resource-layer provider that validates sampled texture/view/sampler materialization. */
class ValidatingTextureSamplerResourceProvider : GPUResourceProvider {
    override fun materializeTextureSamplerBinding(
        request: GPUTextureSamplerMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val diagnostics = request.textureSamplerDiagnostics(context)

        if (diagnostics.isNotEmpty()) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostics.first(),
                targetId = context.targetId,
                taskIds = request.dumpTaskIdsSnapshot,
                resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
                diagnostics = diagnostics,
            )
        }

        val textureRef = GPUTextureResourceRef("texture-ref:${request.ownership.ownerLabel}")
        val textureOperand = GPUMaterializedCommandOperandReference(
            label = request.textureOperandLabel(),
            kind = GPUMaterializedCommandOperandKind.Texture,
            descriptorHash = request.textureDescriptor.materializationDescriptorHash(),
            deviceGeneration = request.deviceGeneration,
            ownerScope = request.ownership.ownerLabel,
            usageLabels = request.dumpRequiredTextureUsageLabelsSnapshot.sorted(),
            invalidationPolicy = request.ownership.releasePolicy,
            evidenceFacts = request.textureEvidenceFacts(),
        )
        val viewOperand = GPUMaterializedCommandOperandReference(
            label = request.textureViewOperandLabel(),
            kind = GPUMaterializedCommandOperandKind.TextureView,
            descriptorHash = request.viewDescriptor.materializationViewHash(),
            deviceGeneration = request.deviceGeneration,
            ownerScope = request.ownership.ownerLabel,
            usageLabels = listOf("texture_binding"),
            invalidationPolicy = request.ownership.releasePolicy,
            evidenceFacts = request.textureViewEvidenceFacts(textureOperand.label),
        )
        val samplerOperand = GPUMaterializedCommandOperandReference(
            label = request.samplerOperandLabel(),
            kind = GPUMaterializedCommandOperandKind.Sampler,
            descriptorHash = request.samplerDescriptor.materializationSamplerHash(),
            deviceGeneration = request.deviceGeneration,
            ownerScope = "sampler-cache",
            usageLabels = listOf("sampler"),
            invalidationPolicy = "descriptor-cache",
            evidenceFacts = request.samplerEvidenceFacts(),
        )

        return GPUResourceMaterializationDecision.Materialized(
            resources = listOf(textureRef),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
            operandBridge = listOf(
                GPUMaterializedCommandOperandBinding(
                    packetId = request.packetId,
                    commandLabel = "setBindGroup",
                    operand = textureOperand,
                ),
                GPUMaterializedCommandOperandBinding(
                    packetId = request.packetId,
                    commandLabel = "setBindGroup",
                    operand = viewOperand,
                ),
                GPUMaterializedCommandOperandBinding(
                    packetId = request.packetId,
                    commandLabel = "setBindGroup",
                    operand = samplerOperand,
                ),
            ),
        )
    }
}

/** Resource-layer snapshot of uploaded texture artifact facts. */
data class GPUUploadedTextureArtifactFacts(
    val artifactKey: String,
    val artifactType: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val pixelFormat: String,
    val generation: Long,
    val lifetimeClass: String,
    val uploadBudgetClass: String,
)

/** Request to validate an uploaded image artifact before it can be sampled. */
data class GPUUploadedTextureArtifactOwnershipRequest(
    val artifact: GPUUploadedTextureArtifactFacts,
    val textureDescriptor: GPUTextureDescriptor,
    val viewDescriptor: GPUTextureViewDescriptor,
    val samplerDescriptor: GPUSamplerDescriptor,
    val ownerLabel: String,
    val ownerScope: String,
    val expectedArtifactGeneration: Long,
    val artifactDeviceGeneration: Long,
    val expectedDeviceGeneration: Long,
    val requiredUsageLabels: Set<String>,
    val activeAttachmentSampled: Boolean = false,
    val debugLiveResourceLabel: String? = null,
) {
    internal val requiredUsageLabelsSnapshot: Set<String> = requiredUsageLabels.toSet()
}

/** Accepted or refused ownership gate for an uploaded texture artifact. */
data class GPUUploadedTextureArtifactOwnershipGatePlan(
    val request: GPUUploadedTextureArtifactOwnershipRequest,
    val ownership: GPUTextureOwnershipPlan,
    val allocation: GPUTextureAllocationPlan,
    val routeKind: String,
    val requiredUsageLabels: Set<String>,
    val diagnostics: List<GPUResourceDiagnostic> = emptyList(),
)

/** Builds upload-artifact ownership evidence without materializing a backend texture. */
class GPUUploadedTextureArtifactOwnershipGate {
    /**
     * Validates typed upload artifact ownership before sampling.
     *
     * This is contract evidence only: accepted output names an
     * `UploadFromArtifact` allocation plan and resource ownership facts, but it
     * does not allocate a texture, upload bytes, inspect cache residency, or
     * expose live resource handles.
     */
    fun plan(request: GPUUploadedTextureArtifactOwnershipRequest): GPUUploadedTextureArtifactOwnershipGatePlan {
        val ownership = GPUTextureOwnershipPlan(
            ownerLabel = request.ownerLabel,
            lifetimeClass = request.artifact.lifetimeClass,
            releasePolicy = "recording-complete",
            canAliasScratch = false,
        )
        val diagnostics = request.refusalDiagnostics()

        if (diagnostics.isNotEmpty()) {
            val diagnostic = diagnostics.first()
            return GPUUploadedTextureArtifactOwnershipGatePlan(
                request = request,
                ownership = ownership,
                allocation = GPUTextureAllocationPlan.Refuse(diagnostic),
                routeKind = "RefuseDiagnostic",
                requiredUsageLabels = request.requiredUsageLabelsSnapshot,
                diagnostics = diagnostics,
            )
        }

        val diagnostic = GPUResourceDiagnostic(
            code = "texture:uploaded-artifact.ownership.accepted",
            resourceLabel = request.ownerLabel,
            message = "Uploaded texture artifact ${request.artifact.artifactKey} passed ownership gates.",
            terminal = false,
        )

        return GPUUploadedTextureArtifactOwnershipGatePlan(
            request = request,
            ownership = ownership,
            allocation = GPUTextureAllocationPlan.UploadFromArtifact(
                artifactKey = request.artifact.artifactKey,
                descriptor = request.textureDescriptor,
            ),
            routeKind = "CPUPreparedGPU",
            requiredUsageLabels = request.requiredUsageLabelsSnapshot,
            diagnostics = listOf(diagnostic),
        )
    }
}

/** Emits stable M4 uploaded-texture ownership evidence lines. */
fun GPUUploadedTextureArtifactOwnershipGatePlan.dumpLines(): List<String> {
    if (routeKind == "RefuseDiagnostic") {
        val diagnostic = diagnostics.firstOrNull()
        return listOf(
            "texture:uploaded-artifact.refused reason=${diagnostic?.code ?: "unknown"} " +
                "artifact=${request.artifact.artifactKey} routeKind=RefuseDiagnostic",
            uploadedTextureOwnershipNonClaimLine,
        )
    }

    return listOf(
        "texture:uploaded-artifact.accepted routeKind=CPUPreparedGPU provenance=${request.artifact.artifactType} " +
            "owner=${ownership.ownerLabel} lifetime=${ownership.lifetimeClass} allocation=UploadFromArtifact",
        "texture:artifact key=${request.artifact.artifactKey} " +
            "generation=${request.artifact.generation} expectedGeneration=${request.expectedArtifactGeneration} " +
            "budget=${request.artifact.uploadBudgetClass} uploadBeforeSample=true",
        "texture:usage required=${requiredUsageLabels.sorted().joinToString(",")} " +
            "available=${request.textureDescriptor.usageLabels.sorted().joinToString(",")} " +
            "deviceGeneration=${request.artifactDeviceGeneration} " +
            "expectedDeviceGeneration=${request.expectedDeviceGeneration}",
        "texture:view descriptor=${request.viewDescriptor.textureDescriptorHash} " +
            "view=${request.viewDescriptor.viewDimension} mipRange=${request.viewDescriptor.mipRange} " +
            "sampler=${request.samplerDescriptor.dumpToken()}",
        "texture:ownership owner=${ownership.ownerLabel} scope=${request.ownerScope} " +
            "release=${ownership.releasePolicy} materialization=upload-before-sample",
        uploadedTextureOwnershipNonClaimLine,
    )
}

private fun GPUUploadedTextureArtifactOwnershipRequest.refusalDiagnostics(): List<GPUResourceDiagnostic> =
    buildList {
        if (artifact.artifactType != "UploadedTextureArtifact") {
            add(
                GPUResourceDiagnostic.uploadArtifactMissing(
                    resourceLabel = ownerLabel,
                    artifactType = artifact.artifactType,
                ),
            )
        }
        if (artifact.generation != expectedArtifactGeneration) {
            add(
                GPUResourceDiagnostic.uploadArtifactGenerationStale(
                    resourceLabel = ownerLabel,
                    expectedGeneration = expectedArtifactGeneration,
                    actualGeneration = artifact.generation,
                ),
            )
        }
        textureDescriptor.validateRequiredUsage(
            requiredUsageLabels = requiredUsageLabelsSnapshot,
            resourceLabel = ownerLabel,
        )?.let(::add)
        if (artifactDeviceGeneration != expectedDeviceGeneration) {
            add(
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = ownerLabel,
                    expectedDeviceGeneration = expectedDeviceGeneration,
                    actualDeviceGeneration = artifactDeviceGeneration,
                ),
            )
        }
        if (activeAttachmentSampled) {
            add(GPUResourceDiagnostic.activeAttachmentSampled(resourceLabel = ownerLabel))
        }
        if (!textureDescriptor.matchesArtifactPixels(artifact)) {
            add(
                GPUResourceDiagnostic.textureDescriptorInvalid(
                    resourceLabel = ownerLabel,
                    reason = "texture descriptor does not match uploaded artifact pixel plan",
                ),
            )
        }
    }

private fun GPUTextureDescriptor.matchesArtifactPixels(artifact: GPUUploadedTextureArtifactFacts): Boolean =
    width == artifact.pixelWidth &&
        height == artifact.pixelHeight &&
        format == artifact.pixelFormat

private fun GPUSamplerDescriptor.dumpToken(): String =
    "$addressModeU/$addressModeV/$magFilter/$minFilter/$mipmapFilter"

private const val uploadedTextureOwnershipNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-live-resource-handle " +
        "no-cache-residency-claim no-codec-support no-cpu-rendered-compat-texture"

/**
 * Frame-scoped surface texture lease.
 *
 * Leases identify a target-owned texture only for the current frame and device
 * generation. They are never durable key material; every use must revalidate
 * generation, usage flags, and active-attachment sampling legality before
 * submission or readback evidence can be trusted.
 */
data class GPUSurfaceTextureLease(
    val targetId: String,
    val targetGeneration: Long = 0L,
    val frameGeneration: Long = 0L,
    val deviceGeneration: Long = 0L,
    val resourceRef: GPUTextureResourceRef,
    val useToken: GPUUseToken,
    val usageLabels: Set<String> = emptySet(),
    val expiredReason: String? = null,
    val evictedReason: String? = null,
) {
    /**
     * Returns all diagnostics that make this lease illegal for the requested use.
     *
     * [expectedFrameGeneration] rejects stale per-frame surface leases even when
     * target and device generations still match the selected surface target.
     */
    fun validateForUse(
        requiredUsageLabels: Set<String>,
        expectedTargetGeneration: Long,
        expectedFrameGeneration: Long,
        expectedDeviceGeneration: Long,
        sampled: Boolean = false,
        activeAttachmentRef: GPUTextureResourceRef? = null,
    ): List<GPUResourceDiagnostic> {
        val diagnostics = mutableListOf<GPUResourceDiagnostic>()
        val missingUsage = requiredUsageLabels - usageLabels

        if (expiredReason != null) {
            diagnostics += GPUResourceDiagnostic.surfaceLeaseStale(
                resourceLabel = targetId,
                reason = expiredReason,
            )
        }
        if (evictedReason != null) {
            diagnostics += GPUResourceDiagnostic.resourceEvicted(
                resourceLabel = targetId,
                reason = evictedReason,
            )
        }
        if (deviceGeneration != expectedDeviceGeneration) {
            diagnostics += GPUResourceDiagnostic.deviceGenerationStale(
                resourceLabel = targetId,
                expectedDeviceGeneration = expectedDeviceGeneration,
                actualDeviceGeneration = deviceGeneration,
            )
        }
        if (targetGeneration != expectedTargetGeneration) {
            diagnostics += GPUResourceDiagnostic.targetGenerationStale(
                resourceLabel = targetId,
                expectedTargetGeneration = expectedTargetGeneration,
                actualTargetGeneration = targetGeneration,
            )
        }
        if (frameGeneration != expectedFrameGeneration) {
            diagnostics += GPUResourceDiagnostic.frameGenerationStale(
                resourceLabel = targetId,
                expectedFrameGeneration = expectedFrameGeneration,
                actualFrameGeneration = frameGeneration,
            )
        }
        if (missingUsage.isNotEmpty()) {
            diagnostics += GPUResourceDiagnostic.textureUsageMissing(
                resourceLabel = targetId,
                missingUsageLabels = missingUsage,
                availableUsageLabels = usageLabels,
            )
        }
        if (sampled && activeAttachmentRef == resourceRef) {
            diagnostics += GPUResourceDiagnostic.activeAttachmentSampled(resourceLabel = targetId)
        }

        return diagnostics
    }
}

/**
 * Explicit opt-in request for materializing an already acquired surface lease.
 *
 * The request does not acquire, import, or submit a backend resource. It only
 * carries the lease and the target/frame/use facts required to revalidate that
 * lease during resource materialization. [taskIds] are deterministic evidence
 * labels for dumps and must not contain backend handles.
 */
internal data class GPUSurfaceTextureLeaseMaterializationRequest(
    val lease: GPUSurfaceTextureLease,
    val requiredUsageLabels: Set<String>,
    val expectedTargetGeneration: Long,
    val expectedFrameGeneration: Long,
    val sampled: Boolean = false,
    val activeAttachmentRef: GPUTextureResourceRef? = null,
    val taskIds: List<String> = emptyList(),
) {
    internal val requiredUsageLabelsSnapshot: Set<String> = requiredUsageLabels.toSet()
    internal val taskIdsSnapshot: List<String> = taskIds.toList()

    init {
        require(requiredUsageLabels.none { label -> label.isBlank() }) {
            "GPUSurfaceTextureLeaseMaterializationRequest.requiredUsageLabels must not contain blank labels"
        }
        require(taskIds.none { taskId -> taskId.isBlank() }) {
            "GPUSurfaceTextureLeaseMaterializationRequest.taskIds must not contain blank labels"
        }
    }
}

/**
 * Resource provider that can materialize only one explicitly supplied surface lease.
 *
 * This is an internal resource-layer test/evidence provider, not a product
 * route and not a command submission path. It returns [GPUResourceMaterializationDecision.Materialized]
 * only for [GPUTextureAllocationPlan.LeaseSurfaceTexture] when the plan,
 * context, and lease target match and [GPUSurfaceTextureLease.validateForUse]
 * reports no diagnostics. Every other plan or failed invariant is refused with
 * stable non-handle evidence.
 */
internal class RevalidatingSurfaceLeaseResourceProvider(
    private val request: GPUSurfaceTextureLeaseMaterializationRequest,
) : GPUResourceProvider {
    override fun materialize(
        plan: GPUTextureAllocationPlan,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision {
        val resourcePlanLabel = plan.resourcePlanLabel()

        if (plan !is GPUTextureAllocationPlan.LeaseSurfaceTexture) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = GPUResourceDiagnostic.resourcePlanNotSupported(
                    resourceLabel = resourcePlanLabel,
                    planKind = plan.planKindLabel(),
                    supportedPlanKind = "LeaseSurfaceTexture",
                ),
                targetId = context.targetId,
                taskIds = request.taskIdsSnapshot,
                resourcePlanLabels = listOf(resourcePlanLabel),
            )
        }

        if (plan.targetId != context.targetId || plan.targetId != request.lease.targetId) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = GPUResourceDiagnostic.surfaceLeaseTargetMismatch(
                    resourceLabel = resourcePlanLabel,
                    planTargetId = plan.targetId,
                    contextTargetId = context.targetId,
                    leaseTargetId = request.lease.targetId,
                ),
                targetId = context.targetId,
                taskIds = request.taskIdsSnapshot,
                resourcePlanLabels = listOf(resourcePlanLabel),
            )
        }

        val diagnostics = request.lease.validateForUse(
            requiredUsageLabels = request.requiredUsageLabelsSnapshot,
            expectedTargetGeneration = request.expectedTargetGeneration,
            expectedFrameGeneration = request.expectedFrameGeneration,
            expectedDeviceGeneration = context.deviceGeneration,
            sampled = request.sampled,
            activeAttachmentRef = request.activeAttachmentRef,
        )

        if (diagnostics.isNotEmpty()) {
            return GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostics.first(),
                targetId = context.targetId,
                taskIds = request.taskIdsSnapshot,
                resourcePlanLabels = listOf(resourcePlanLabel),
                diagnostics = diagnostics,
            )
        }

        return GPUResourceMaterializationDecision.Materialized(
            resources = listOf(request.lease.resourceRef),
            diagnostics = emptyList(),
            targetId = context.targetId,
            taskIds = request.taskIdsSnapshot,
            resourcePlanLabels = listOf(resourcePlanLabel),
        )
    }
}

/** Sampled texture binding contract. */
data class GPUSampledTextureBinding(
    val bindingLabel: String,
    val view: GPUTextureViewDescriptor,
    val sampler: GPUSamplerDescriptor,
    val useToken: GPUUseToken,
)

/** Deferred resource plan whose descriptor is known before allocation. */
data class GPULazyResourcePlan(
    val planId: String,
    val allocation: GPUTextureAllocationPlan,
    val invalidationFacts: List<String>,
)

/** Externally promised resource plan. */
data class GPUPromiseResourcePlan(
    val promiseId: String,
    val expectedDescriptor: GPUTextureDescriptor,
    val timeoutPolicy: String,
)

/** Imported resource plan with ownership and release facts. */
data class GPUImportedResourcePlan(
    val descriptor: GPUImportedTextureDescriptor,
    val ownership: GPUTextureOwnershipPlan,
)

/** Resource plan that must be revalidated on every replay. */
data class GPUVolatileResourcePlan(
    val planId: String,
    val reasonCode: String,
    val revalidationFacts: List<String>,
)

/** Diagnostic emitted by texture ownership or binding planning. */
data class GPUTextureDiagnostic(
    val code: String,
    val textureLabel: String,
    val message: String,
    val terminal: Boolean,
)

/**
 * Diagnostic emitted by generic resource materialization.
 *
 * Diagnostics own stable reason codes and non-handle evidence facts for late
 * materialization failures. [facts] are rendered in sorted key order by dump
 * helpers; they must not contain concrete backend handles, surface leases, or
 * other one-frame identities.
 */
data class GPUResourceDiagnostic(
    val code: String,
    val resourceLabel: String,
    val message: String,
    val terminal: Boolean,
    val facts: Map<String, String> = emptyMap(),
) {
    internal val dumpFactsSnapshot: Map<String, String> = facts.toMap()

    companion object {
        /** Builds the default refusal for an unconfigured resource provider. */
        fun providerUnconfigured(resourceLabel: String, targetId: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.provider_unconfigured",
                resourceLabel = resourceLabel,
                message = "GPUResourceProvider is not configured for target $targetId; refusing resource $resourceLabel.",
                terminal = true,
                facts = mapOf("targetId" to targetId),
            )

        /** Builds a refusal for a provider that intentionally supports only a narrower plan. */
        fun resourcePlanNotSupported(
            resourceLabel: String,
            planKind: String,
            supportedPlanKind: String,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.plan_not_supported",
                resourceLabel = resourceLabel,
                message = "Resource plan $resourceLabel has kind $planKind but this provider only supports $supportedPlanKind.",
                terminal = true,
                facts = mapOf(
                    "planKind" to planKind,
                    "supportedPlanKind" to supportedPlanKind,
                ),
            )

        /** Builds a refusal when a surface lease no longer belongs to the requested target. */
        fun surfaceLeaseTargetMismatch(
            resourceLabel: String,
            planTargetId: String,
            contextTargetId: String,
            leaseTargetId: String,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.surface_lease_target_mismatch",
                resourceLabel = resourceLabel,
                message = "Surface lease target mismatch for $resourceLabel: plan=$planTargetId context=$contextTargetId lease=$leaseTargetId.",
                terminal = true,
                facts = mapOf(
                    "contextTargetId" to contextTargetId,
                    "leaseTargetId" to leaseTargetId,
                    "planTargetId" to planTargetId,
                ),
            )

        /** Builds a refusal when command operand facts target a different surface. */
        fun commandOperandTargetMismatch(
            resourceLabel: String,
            requestTargetId: String,
            contextTargetId: String,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.command_operand_target_mismatch",
                resourceLabel = resourceLabel,
                message = "Command operand materialization target mismatch for $resourceLabel: request=$requestTargetId context=$contextTargetId.",
                terminal = true,
                facts = mapOf(
                    "contextTargetId" to contextTargetId,
                    "requestTargetId" to requestTargetId,
                ),
            )

        /** Builds a generic refusal when resource facts target a different surface. */
        fun resourceTargetMismatch(
            resourceLabel: String,
            requestTargetId: String,
            contextTargetId: String,
        ): GPUResourceDiagnostic {
            requireDumpSafeValue("GPUResourceDiagnostic.resourceTargetMismatch.resourceLabel", resourceLabel)
            requireDumpSafeValue("GPUResourceDiagnostic.resourceTargetMismatch.requestTargetId", requestTargetId)
            requireDumpSafeValue("GPUResourceDiagnostic.resourceTargetMismatch.contextTargetId", contextTargetId)
            return GPUResourceDiagnostic(
                code = "unsupported.resource.target_mismatch",
                resourceLabel = resourceLabel,
                message = "Resource target mismatch for $resourceLabel: request=$requestTargetId context=$contextTargetId.",
                terminal = true,
                facts = mapOf(
                    "contextTargetId" to contextTargetId,
                    "requestTargetId" to requestTargetId,
                ),
            )
        }

        /** Builds a missing usage diagnostic for a command-stream operand. */
        fun commandOperandUsageMissing(
            resourceLabel: String,
            missingUsageLabels: Set<String>,
            availableUsageLabels: Set<String>,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.command_operand_usage_missing",
                resourceLabel = resourceLabel,
                message = "Command operand $resourceLabel is missing usage ${missingUsageLabels.sorted()} from available ${availableUsageLabels.sorted()}.",
                terminal = true,
                facts = mapOf(
                    "availableUsageLabels" to availableUsageLabels.sorted().joinToString(","),
                    "missingUsageLabels" to missingUsageLabels.sorted().joinToString(","),
                ),
            )

        /** Builds a refusal when a payload bind group does not match reflected WGSL layout facts. */
        fun bindingLayoutMismatch(
            resourceLabel: String,
            reflectedLayoutHash: String,
            bindingPlanHash: String,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.binding_layout_mismatch",
                resourceLabel = resourceLabel,
                message = "Payload bind group $resourceLabel uses binding plan $bindingPlanHash but reflected layout is $reflectedLayoutHash.",
                terminal = true,
                facts = mapOf(
                    "bindingPlanHash" to bindingPlanHash,
                    "reflectedLayoutHash" to reflectedLayoutHash,
                ),
            )

        /** Builds a refusal when a payload bind group exceeds the accepted dynamic-offset count. */
        fun dynamicOffsetsExceeded(
            resourceLabel: String,
            dynamicOffsetCount: Int,
            maxDynamicOffsets: Int,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.dynamic_offsets_exceeded",
                resourceLabel = resourceLabel,
                message = "Payload bind group $resourceLabel requests $dynamicOffsetCount dynamic offsets but the limit is $maxDynamicOffsets.",
                terminal = true,
                facts = mapOf(
                    "dynamicOffsetCount" to dynamicOffsetCount.toString(),
                    "maxDynamicOffsets" to maxDynamicOffsets.toString(),
                ),
            )

        /** Builds a refusal when upload/staging capability is absent for a payload plan. */
        fun uploadCapabilityMissing(resourceLabel: String, uploadPlanHash: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.upload_capability_missing",
                resourceLabel = resourceLabel,
                message = "Payload upload plan $uploadPlanHash for $resourceLabel cannot run because upload capability is absent.",
                terminal = true,
                facts = mapOf("uploadPlanHash" to uploadPlanHash),
            )

        /** Builds a refusal when upload byte ranges do not cover the payload exactly once. */
        fun uploadRangeInvalid(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.upload_range_invalid",
                resourceLabel = resourceLabel,
                message = "Payload upload ranges for $resourceLabel are invalid: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )

        /** Builds a refusal when binding labels need structured resource facts. */
        fun bindingFactMissing(resourceLabel: String, bindingLabel: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.binding_fact_missing",
                resourceLabel = resourceLabel,
                message = "Payload bind group $resourceLabel needs structured binding facts for $bindingLabel.",
                terminal = true,
                facts = mapOf("bindingLabel" to bindingLabel),
            )

        /** Builds a refusal when a structured binding fact is not declared by the bind group descriptors. */
        fun bindingFactUnexpected(resourceLabel: String, bindingLabel: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.binding_fact_unexpected",
                resourceLabel = resourceLabel,
                message = "Payload bind group $resourceLabel has unexpected structured binding facts for $bindingLabel.",
                terminal = true,
                facts = mapOf("bindingLabel" to bindingLabel),
            )

        /** Builds a refusal when resource binding generation facts are stale. */
        fun bindingGenerationStale(
            resourceLabel: String,
            bindingLabel: String,
            expectedGeneration: Long,
            actualGeneration: Long,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.binding_generation_stale",
                resourceLabel = resourceLabel,
                message = "Payload binding $bindingLabel has generation $actualGeneration but expected $expectedGeneration.",
                terminal = true,
                facts = mapOf(
                    "actualGeneration" to actualGeneration.toString(),
                    "bindingLabel" to bindingLabel,
                    "expectedGeneration" to expectedGeneration.toString(),
                ),
            )

        /** Builds a refusal when a payload slot points at the wrong gathered block fingerprint. */
        fun payloadFingerprintMismatch(
            resourceLabel: String,
            slotFingerprint: String,
            blockFingerprint: String,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.payload_fingerprint_mismatch",
                resourceLabel = resourceLabel,
                message = "Payload slot for $resourceLabel points at $slotFingerprint but block fingerprint is $blockFingerprint.",
                terminal = true,
                facts = mapOf(
                    "blockFingerprint" to blockFingerprint,
                    "slotFingerprint" to slotFingerprint,
                ),
            )

        /** Builds a refusal when payload byte evidence is not uploadable. */
        fun payloadUploadBytesInvalid(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.payload_upload_bytes_invalid",
                resourceLabel = resourceLabel,
                message = "Payload upload bytes for $resourceLabel are invalid: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )

        /** Builds a missing texture usage diagnostic. */
        fun textureUsageMissing(
            resourceLabel: String,
            missingUsageLabels: Set<String>,
            availableUsageLabels: Set<String>,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.usage_missing",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel is missing usage ${missingUsageLabels.sorted()} from available ${availableUsageLabels.sorted()}.",
                terminal = true,
                facts = mapOf(
                    "availableUsageLabels" to availableUsageLabels.sorted().joinToString(","),
                    "missingUsageLabels" to missingUsageLabels.sorted().joinToString(","),
                ),
            )

        /** Builds the canonical expired or stale surface lease diagnostic. */
        fun surfaceLeaseStale(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "stale.texture.surface_lease",
                resourceLabel = resourceLabel,
                message = "Surface texture lease for $resourceLabel is stale: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )

        /** Builds a resource-residency eviction diagnostic. */
        fun resourceEvicted(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.evicted",
                resourceLabel = resourceLabel,
                message = "GPU resource $resourceLabel was evicted before materialization: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )

        /** Builds a stale device generation diagnostic. */
        fun deviceGenerationStale(
            resourceLabel: String,
            expectedDeviceGeneration: Long,
            actualDeviceGeneration: Long,
            resourceKind: String = "texture",
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.$resourceKind.device_generation_stale",
                resourceLabel = resourceLabel,
                message = "${resourceKind.replaceFirstChar { it.uppercase() }} resource $resourceLabel was created for device generation $actualDeviceGeneration but expected $expectedDeviceGeneration.",
                terminal = true,
                facts = mapOf(
                    "actualDeviceGeneration" to actualDeviceGeneration.toString(),
                    "expectedDeviceGeneration" to expectedDeviceGeneration.toString(),
                ),
            )

        /** Builds a stale target generation diagnostic. */
        fun targetGenerationStale(
            resourceLabel: String,
            expectedTargetGeneration: Long,
            actualTargetGeneration: Long,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.target_generation_stale",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel targets generation $actualTargetGeneration but expected $expectedTargetGeneration.",
                terminal = true,
                facts = mapOf(
                    "actualTargetGeneration" to actualTargetGeneration.toString(),
                    "expectedTargetGeneration" to expectedTargetGeneration.toString(),
                ),
            )

        /** Builds a stale frame generation diagnostic for a per-frame surface lease. */
        fun frameGenerationStale(
            resourceLabel: String,
            expectedFrameGeneration: Long,
            actualFrameGeneration: Long,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.frame_generation_stale",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel targets frame generation $actualFrameGeneration but expected $expectedFrameGeneration.",
                terminal = true,
                facts = mapOf(
                    "actualFrameGeneration" to actualFrameGeneration.toString(),
                    "expectedFrameGeneration" to expectedFrameGeneration.toString(),
                ),
            )

        /** Builds an active attachment sampling diagnostic. */
        fun activeAttachmentSampled(resourceLabel: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.active_attachment_sampled",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel is active as a render attachment and cannot be sampled without an accepted copy or intermediate route.",
                terminal = true,
                facts = mapOf("activeAttachmentSampled" to "true"),
            )

        /** Builds a stale uploaded-artifact generation diagnostic. */
        fun uploadArtifactGenerationStale(
            resourceLabel: String,
            expectedGeneration: Long,
            actualGeneration: Long,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.upload_artifact_generation_stale",
                resourceLabel = resourceLabel,
                message = "Uploaded texture artifact for $resourceLabel has generation $actualGeneration but expected $expectedGeneration.",
                terminal = true,
                facts = mapOf(
                    "actualGeneration" to actualGeneration.toString(),
                    "expectedGeneration" to expectedGeneration.toString(),
                ),
            )

        /** Builds a diagnostic for missing uploaded-artifact provenance. */
        fun uploadArtifactMissing(resourceLabel: String, artifactType: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.upload_artifact_missing",
                resourceLabel = resourceLabel,
                message = "Resource $resourceLabel requires UploadedTextureArtifact but found $artifactType.",
                terminal = true,
                facts = mapOf("artifactType" to artifactType),
            )

        /** Builds a texture descriptor mismatch diagnostic. */
        fun textureDescriptorInvalid(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.descriptor_invalid",
                resourceLabel = resourceLabel,
                message = "Texture descriptor for $resourceLabel is invalid: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )

        /** Builds a diagnostic for unavailable mip levels on a sampled texture route. */
        fun textureMipmapUnavailable(
            resourceLabel: String,
            requiredMipLevels: Int,
            availableMipLevels: Int,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.mipmap_unavailable",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel requires $requiredMipLevels mip levels but only $availableMipLevels are available.",
                terminal = true,
                facts = mapOf(
                    "availableMipLevels" to availableMipLevels.toString(),
                    "requiredMipLevels" to requiredMipLevels.toString(),
                ),
            )

        /** Builds a diagnostic for portable GPU texture swizzle gaps. */
        fun textureSwizzleUnimplemented(resourceLabel: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.swizzle_unimplemented",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel requires a swizzle route that is not implemented.",
                terminal = true,
                facts = mapOf("swizzleRequired" to "true"),
            )

        /** Builds a diagnostic for sampler/tile/filter facts refused by the boundary gate. */
        fun textureSamplingUnsupported(resourceLabel: String, reasonCode: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = reasonCode,
                resourceLabel = resourceLabel,
                message = "Texture sampler materialization for $resourceLabel is blocked by $reasonCode.",
                terminal = true,
                facts = mapOf("samplerBoundaryReason" to reasonCode),
            )

        /** Builds an allocation or upload failure diagnostic for texture materialization. */
        fun textureAllocationFailed(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.allocation_failed",
                resourceLabel = resourceLabel,
                message = "Texture materialization for $resourceLabel failed during allocation or upload: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )

        /** Builds a diagnostic when a sampled texture fact is not paired with a sampler fact. */
        fun sampledTextureSamplerMissing(resourceLabel: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.resource.sampled_texture_sampler_missing",
                resourceLabel = resourceLabel,
                message = "Sampled texture binding $resourceLabel must include a paired sampler binding fact.",
                terminal = true,
                facts = mapOf("samplerBindingMissing" to "true"),
            )

        /** Builds an upload or staging budget diagnostic. */
        fun uploadBudgetExceeded(
            resourceLabel: String,
            requestedBytes: Long,
            budgetBytes: Long,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "budget.resource.upload_exceeded",
                resourceLabel = resourceLabel,
                message = "Resource $resourceLabel requested $requestedBytes upload or staging bytes with budget $budgetBytes.",
                terminal = true,
                facts = mapOf(
                    "budgetBytes" to budgetBytes.toString(),
                    "requestedBytes" to requestedBytes.toString(),
                ),
            )

        /** Builds a diagnostic when GPU resource adapter creation fails. */
        fun adapterCreateFailed(
            resourceLabel: String,
            reason: String,
        ): GPUResourceDiagnostic {
            requireDumpSafeValue("GPUResourceDiagnostic.adapterCreateFailed.resourceLabel", resourceLabel)
            requireDumpSafeValue("GPUResourceDiagnostic.adapterCreateFailed.reason", reason)
            return GPUResourceDiagnostic(
                code = "unsupported.resource.adapter_create_failed",
                resourceLabel = resourceLabel,
                message = "GPU resource adapter failed to create $resourceLabel.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )
        }

        /** Builds a pipeline creation failure diagnostic recorded during materialization. */
        fun pipelineCreationFailure(resourceLabel: String, reason: String): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "capability.pipeline.missing_feature",
                resourceLabel = resourceLabel,
                message = "Pipeline creation for $resourceLabel failed: $reason.",
                terminal = true,
                facts = mapOf("reason" to reason),
            )
    }
}

/**
 * Emits deterministic PM evidence for a target resource-preparation context.
 *
 * The dump includes target, frame, device-generation, and budget-class facts
 * only. It intentionally omits resource references and backend handles.
 */
fun GPUTargetPreparationContext.dumpLines(): List<String> =
    listOf(
        "resource.target_preparation " +
            "target=$targetId " +
            "frame=$frameId " +
            "deviceGeneration=$deviceGeneration " +
            "budgetClass=$budgetClass",
    )

/**
 * Emits deterministic PM evidence lines for this materialization decision.
 *
 * The dump is evidence only: `Materialized` lines summarize live typed resource
 * references by count, `Deferred` lines preserve explicit wait reasons, and
 * `Refused` lines expose terminal diagnostic codes. No concrete backend handle
 * or durable surface lease identity is emitted.
 */
fun GPUResourceMaterializationDecision.dumpLines(): List<String> =
    when (this) {
        is GPUResourceMaterializationDecision.Materialized -> {
            val operandRefs = dumpCommandOperandRefs().sortedWith(
                compareBy<GPUMaterializedCommandOperandReference> { operand -> operand.label }
                    .thenBy { operand -> operand.kind.dumpLabel() },
            )
            val operandCount = if (operandRefs.isEmpty()) "" else " operands=${operandRefs.size}"
            listOf(
                "resource.materialization:materialized " +
                    "target=$targetId " +
                    "tasks=${dumpTaskIdsSnapshot.dumpList()} " +
                    "resourcePlans=${dumpResourcePlanLabelsSnapshot.dumpList()} " +
                    "resourceCount=${dumpResourcesSnapshot.size}$operandCount " +
                    "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
            ) +
                operandRefs.map { operand ->
                    "resource.materialization:operand ${operand.dumpCommandOperandFields()}"
                } +
                dumpDiagnosticsSnapshot.dumpLines() +
                dumpPayloadTelemetrySnapshot.dumpPayloadTelemetryLines() +
                dumpResourceLeaseSnapshot.dumpResourceLeaseLines()
        }
        is GPUResourceMaterializationDecision.Deferred ->
            listOf(
                "resource.materialization:deferred " +
                    "target=$targetId " +
                    "tasks=${dumpTaskIdsSnapshot.dumpList()} " +
                    "resourcePlans=${dumpResourcePlanLabelsSnapshot.dumpList()} " +
                    "reason=$reasonCode " +
                    "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
            ) + dumpDiagnosticsSnapshot.dumpLines()
        is GPUResourceMaterializationDecision.Refused ->
            listOf(
                "resource.materialization:refused " +
                    "target=$targetId " +
                    "tasks=${dumpTaskIdsSnapshot.dumpList()} " +
                    "resourcePlans=${dumpResourcePlanLabelsSnapshot.dumpList()} " +
                    "code=${diagnostic.code} " +
                    "terminal=${diagnostic.terminal}",
            ) +
                dumpDiagnosticsSnapshot.dumpLines() +
                dumpPayloadTelemetrySnapshot.dumpPayloadTelemetryLines() +
                dumpResourceLeaseSnapshot.dumpResourceLeaseLines()
    }

/** Emits deterministic evidence for a resource materialization preimage plan. */
fun GPUResourceMaterializationPreimagePlan.dumpLines(): List<String> {
    val sortedResources = dumpResourcesSnapshot.sortedWith(
        compareBy<GPUMaterializedResourceReference> { resource -> resource.label }
            .thenBy { resource -> resource.role.dumpLabel() },
    )
    val resourceLabels = sortedResources.map { resource -> resource.label }.dumpPreimageList()
    val bindingLabels = dumpBindingLabelsSnapshot.dumpPreimageList()
    val claimFlags = nonClaims.claimFlagDump()
    val head = if (accepted) {
        "resource-preimage:accepted plan=$planLabel source=$sourceGate " +
            "resources=$resourceLabels bindings=$bindingLabels $claimFlags"
    } else {
        "resource-preimage:refused plan=$planLabel source=$sourceGate " +
            "reason=$refusalCode resources=$resourceLabels bindings=$bindingLabels $claimFlags"
    }

    return listOf(head) +
        sortedResources.map { resource -> resource.dumpLine() } +
        nonClaims.dumpLine()
}

/** Emits scoped, non-handle fields for a materialized command operand reference. */
fun GPUMaterializedCommandOperandReference.dumpCommandOperandFields(): String =
    "operand=$label kind=${kind.dumpLabel()} " +
        "deviceGeneration=$deviceGeneration owner=$ownerScope " +
        "usage=${dumpUsageLabelsSnapshot.dumpList()} " +
        "invalidation=$invalidationPolicy descriptor=$descriptorHash " +
        "facts=${dumpEvidenceFactsSnapshot.dumpFacts()}"

private const val UNSPECIFIED_DUMP_VALUE = "unspecified"

private val payloadMaterializationTelemetryLanes = setOf("uniform-upload", "bind-group")

private val RAW_BACKEND_TOKEN = "w" + "gpu"
private val RAW_HANDLE_DUMP_PATTERN =
    Regex("(?i)($RAW_BACKEND_TOKEN|externaltexturehandle|gpu[a-z0-9]*handle|@0x[0-9a-f]+|0x[0-9a-f]{6,})")

private fun requireDumpSafeValue(fieldName: String, value: String) {
    require(!RAW_HANDLE_DUMP_PATTERN.containsMatchIn(value)) {
        "$fieldName must not contain raw backend handle evidence"
    }
}

private fun GPUResourceMaterializationDecision.Materialized.dumpCommandOperandRefs(): List<GPUMaterializedCommandOperandReference> {
    val bridgedRefs = dumpOperandBridgeSnapshot.map { binding -> binding.operand }
    val bridgedLabels = bridgedRefs.map { operand -> operand.label to operand.kind }.toSet()
    return bridgedRefs + dumpOperandRefsSnapshot.filter { operand -> operand.label to operand.kind !in bridgedLabels }
}

private fun GPUTextureAllocationPlan.resourcePlanLabel(): String =
    when (this) {
        is GPUTextureAllocationPlan.CreateTexture -> ownership.ownerLabel
        is GPUTextureAllocationPlan.ExistingGPUResource -> planLabel
        is GPUTextureAllocationPlan.ImportExternalTexture -> planLabel
        is GPUTextureAllocationPlan.LeaseSurfaceTexture -> targetId
        is GPUTextureAllocationPlan.UploadFromArtifact -> artifactKey
        is GPUTextureAllocationPlan.Refuse -> diagnostic.resourceLabel
    }

private fun GPUTextureAllocationPlan.planKindLabel(): String =
    when (this) {
        is GPUTextureAllocationPlan.CreateTexture -> "CreateTexture"
        is GPUTextureAllocationPlan.ExistingGPUResource -> "ExistingGPUResource"
        is GPUTextureAllocationPlan.ImportExternalTexture -> "ImportExternalTexture"
        is GPUTextureAllocationPlan.LeaseSurfaceTexture -> "LeaseSurfaceTexture"
        is GPUTextureAllocationPlan.UploadFromArtifact -> "UploadFromArtifact"
        is GPUTextureAllocationPlan.Refuse -> "Refuse"
    }

private fun GPUCommandOperandMaterializationRequest.resourcePlanLabel(): String =
    resourcePlanLabelsOrDefault().first()

private fun GPUCommandOperandMaterializationRequest.resourcePlanLabelsOrDefault(): List<String> =
    if (dumpResourcePlanLabelsSnapshot.isEmpty()) {
        listOf("command-operands")
    } else {
        dumpResourcePlanLabelsSnapshot
    }

private fun GPUPayloadMaterializationRequest.resourcePlanLabel(): String =
    resourcePlanLabelsOrDefault().first()

private fun GPUPayloadMaterializationRequest.resourcePlanLabelsOrDefault(): List<String> =
    if (dumpResourcePlanLabelsSnapshot.isEmpty()) {
        listOf("payload-materialization")
    } else {
        dumpResourcePlanLabelsSnapshot
    }

private fun GPUTextureSamplerMaterializationRequest.resourcePlanLabel(): String =
    resourcePlanLabelsOrDefault().first()

private fun GPUTextureSamplerMaterializationRequest.resourcePlanLabelsOrDefault(): List<String> =
    if (dumpResourcePlanLabelsSnapshot.isEmpty()) {
        listOf("texture-sampler-materialization")
    } else {
        dumpResourcePlanLabelsSnapshot
    }

private fun GPUPayloadMaterializationRequest.validationDiagnostics(
    expectedDeviceGeneration: Long,
): List<GPUResourceDiagnostic> =
    buildList {
        val uniformLabel = uniformBufferOperandLabel()
        val bindGroupLabel = bindGroupOperandLabel()
        if (!uploadCapabilityAvailable) {
            add(
                GPUResourceDiagnostic.uploadCapabilityMissing(
                    resourceLabel = uniformLabel,
                    uploadPlanHash = uploadPlan.planHash,
                ),
            )
        }
        if (deviceGeneration != expectedDeviceGeneration) {
            add(
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = uniformLabel,
                    expectedDeviceGeneration = expectedDeviceGeneration,
                    actualDeviceGeneration = deviceGeneration,
                ),
            )
        }
        val missingUniformUsage = dumpRequiredUniformUsageLabelsSnapshot - dumpAvailableUniformUsageLabelsSnapshot
        if (missingUniformUsage.isNotEmpty()) {
            add(
                GPUResourceDiagnostic.commandOperandUsageMissing(
                    resourceLabel = uniformLabel,
                    missingUsageLabels = missingUniformUsage,
                    availableUsageLabels = dumpAvailableUniformUsageLabelsSnapshot,
                ),
            )
        }
        if (uniformSlot.fingerprint != uniformBlock.fingerprint) {
            add(
                GPUResourceDiagnostic.payloadFingerprintMismatch(
                    resourceLabel = uniformLabel,
                    slotFingerprint = uniformSlot.fingerprint.value,
                    blockFingerprint = uniformBlock.fingerprint.value,
                ),
            )
        }
        if (resourceSlot.fingerprint != resourceBlock.fingerprint) {
            add(
                GPUResourceDiagnostic.payloadFingerprintMismatch(
                    resourceLabel = bindGroupLabel,
                    slotFingerprint = resourceSlot.fingerprint.value,
                    blockFingerprint = resourceBlock.fingerprint.value,
                ),
            )
        }
        if (resourceBlock.bindingPlanHash != reflectedBindingLayoutHash) {
            add(
                GPUResourceDiagnostic.bindingLayoutMismatch(
                    resourceLabel = bindGroupLabel,
                    reflectedLayoutHash = reflectedBindingLayoutHash,
                    bindingPlanHash = resourceBlock.bindingPlanHash,
                ),
            )
        }
        if (resourceBlock.dynamicOffsets.size > maxDynamicOffsets) {
            add(
                GPUResourceDiagnostic.dynamicOffsetsExceeded(
                    resourceLabel = bindGroupLabel,
                    dynamicOffsetCount = resourceBlock.dynamicOffsets.size,
                    maxDynamicOffsets = maxDynamicOffsets,
                ),
            )
        }
        if (resourceBlock.dynamicOffsets.any { offset -> offset < 0L }) {
            add(
                GPUResourceDiagnostic.payloadUploadBytesInvalid(
                    resourceLabel = bindGroupLabel,
                    reason = "dynamic offsets must be non-negative",
                ),
            )
        }
        if (uniformBlock.byteSize > uploadBudgetBytes) {
            add(
                GPUResourceDiagnostic.uploadBudgetExceeded(
                    resourceLabel = uniformLabel,
                    requestedBytes = uniformBlock.byteSize,
                    budgetBytes = uploadBudgetBytes,
                ),
            )
        }
        uploadPlanByteRangeDiagnostic(uniformLabel)?.let(::add)
        if (uniformBlock.bytes.isNotEmpty() && uniformBlock.bytes.size.toLong() != uniformBlock.byteSize) {
            add(
                GPUResourceDiagnostic.payloadUploadBytesInvalid(
                    resourceLabel = uniformLabel,
                    reason = "byte list size ${uniformBlock.bytes.size} does not match byteSize ${uniformBlock.byteSize}",
                ),
            )
        }
        if (uniformBlock.bytes.any { byte -> byte !in 0..255 }) {
            add(
                GPUResourceDiagnostic.payloadUploadBytesInvalid(
                    resourceLabel = uniformLabel,
                    reason = "byte values must be unsigned 0..255",
                ),
            )
        }
        if (!uniformBlock.zeroedPadding) {
            add(
                GPUResourceDiagnostic.payloadUploadBytesInvalid(
                    resourceLabel = uniformLabel,
                    reason = "padding bytes must be zeroed before upload",
                ),
            )
        }
        addAll(resourceBindingDiagnostics(bindGroupLabel))
    }

private fun GPUPayloadMaterializationRequest.uploadPlanByteRangeDiagnostic(
    uniformLabel: String,
): GPUResourceDiagnostic? {
    val ranges = uploadPlan.byteRanges
    if (ranges.isEmpty()) {
        return GPUResourceDiagnostic.uploadRangeInvalid(
            resourceLabel = uniformLabel,
            reason = "byte ranges must not be empty",
        )
    }
    val sortedRanges = ranges.sortedBy { range -> range.first }
    var expectedStart = 0L
    sortedRanges.forEach { range ->
        if (range.isEmpty()) {
            return GPUResourceDiagnostic.uploadRangeInvalid(
                resourceLabel = uniformLabel,
                reason = "byte ranges must not be empty ranges",
            )
        }
        if (range.first != expectedStart) {
            return GPUResourceDiagnostic.uploadRangeInvalid(
                resourceLabel = uniformLabel,
                reason = "byte ranges must cover contiguous bytes from 0",
            )
        }
        if (range.last < range.first) {
            return GPUResourceDiagnostic.uploadRangeInvalid(
                resourceLabel = uniformLabel,
                reason = "byte ranges must be ascending",
            )
        }
        expectedStart = range.last + 1L
    }
    return if (expectedStart != uniformBlock.byteSize) {
        GPUResourceDiagnostic.uploadRangeInvalid(
            resourceLabel = uniformLabel,
            reason = "byte ranges must cover exactly ${uniformBlock.byteSize} bytes",
        )
    } else {
        null
    }
}

private fun GPUPayloadMaterializationRequest.resourceBindingDiagnostics(
    bindGroupLabel: String,
): List<GPUResourceDiagnostic> =
    buildList {
        if (resourceBlock.bindingFacts.isEmpty()) {
            resourceBlock.resourceDescriptorLabels
                .filterNot { label -> label.startsWith("uniform:") }
                .forEach { label ->
                    add(
                        GPUResourceDiagnostic.bindingFactMissing(
                            resourceLabel = bindGroupLabel,
                            bindingLabel = label,
                        ),
                    )
                }
            return@buildList
        }

        val descriptorLabels = resourceBlock.resourceDescriptorLabels.toSet()
        val factLabels = resourceBlock.bindingFacts.map { fact -> fact.bindingLabel }.toSet()
        (descriptorLabels - factLabels)
            .filterNot { label -> label.startsWith("uniform:") }
            .forEach { label ->
                add(
                    GPUResourceDiagnostic.bindingFactMissing(
                        resourceLabel = bindGroupLabel,
                        bindingLabel = label,
                    ),
                )
            }
        (factLabels - descriptorLabels).forEach { label ->
            add(
                GPUResourceDiagnostic.bindingFactUnexpected(
                    resourceLabel = bindGroupLabel,
                    bindingLabel = label,
                ),
            )
        }
        resourceBlock.bindingFacts.forEach { fact ->
            addAll(fact.validationDiagnostics(resourceLabel = bindGroupLabel))
        }
        addAll(resourceBlock.bindingFacts.sampledTextureSamplerPairDiagnostics(bindGroupLabel))
    }

private fun GPUResourceBindingFact.validationDiagnostics(
    resourceLabel: String,
): List<GPUResourceDiagnostic> =
    buildList {
        val missingUsage = requiredUsageLabels - availableUsageLabels
        if (missingUsage.isNotEmpty()) {
            add(
                GPUResourceDiagnostic.commandOperandUsageMissing(
                    resourceLabel = bindingLabel,
                    missingUsageLabels = missingUsage,
                    availableUsageLabels = availableUsageLabels,
                ),
            )
        }
        if (actualResourceGeneration != expectedResourceGeneration) {
            add(
                GPUResourceDiagnostic.bindingGenerationStale(
                    resourceLabel = resourceLabel,
                    bindingLabel = bindingLabel,
                    expectedGeneration = expectedResourceGeneration,
                    actualGeneration = actualResourceGeneration,
                ),
            )
        }
        evictedReason?.let { reason ->
            add(GPUResourceDiagnostic.resourceEvicted(resourceLabel = bindingLabel, reason = reason))
        }
    }

private fun List<GPUResourceBindingFact>.sampledTextureSamplerPairDiagnostics(
    bindGroupLabel: String,
): List<GPUResourceDiagnostic> =
    buildList {
        val bindingFacts = this@sampledTextureSamplerPairDiagnostics
        val sampledTextureKeys = bindingFacts.filter { fact -> fact.kind == GPUResourceBindingKind.SampledTexture }
            .map { fact -> fact.textureSamplerPairKey() }
        val samplerKeys = bindingFacts.filter { fact -> fact.kind == GPUResourceBindingKind.Sampler }
            .map { fact -> fact.textureSamplerPairKey() }
        if (sampledTextureKeys.isEmpty() && samplerKeys.isEmpty()) {
            return@buildList
        }

        val hasDuplicateTexture = sampledTextureKeys.hasDuplicate()
        val hasDuplicateSampler = samplerKeys.hasDuplicate()
        if (
            hasDuplicateTexture ||
            hasDuplicateSampler ||
            sampledTextureKeys.sorted() != samplerKeys.sorted()
        ) {
            add(GPUResourceDiagnostic.sampledTextureSamplerMissing(resourceLabel = bindGroupLabel))
        }
    }

private fun GPUResourceBindingFact.textureSamplerPairKey(): String =
    bindingLabel.substringAfter(':', bindingLabel)

private fun List<String>.hasDuplicate(): Boolean =
    size != toSet().size

private fun GPUPayloadMaterializationRequest.uniformBufferOperandLabel(): String =
    "payload-upload:${uniformSlot.slotId.value}"

private fun GPUPayloadMaterializationRequest.bindGroupOperandLabel(): String =
    "bind-group:${resourceSlot.slotId.value}"

private fun GPUPayloadMaterializationRequest.payloadOwnerScope(): String =
    "payload-scope:${uniformBlock.scope}"

private fun GPUPayloadMaterializationRequest.uniformUploadEvidenceFacts(): Map<String, String> =
    mapOf(
        "alignment" to alignmentBytes.toString(),
        "bindingLayout" to reflectedBindingLayoutHash,
        "byteSize" to uniformBlock.byteSize.toString(),
        "generation" to payloadGeneration.toString(),
        "scope" to uniformBlock.scope,
        "uploadPlan" to uploadPlan.planHash,
        "uploadScope" to uploadPlan.stagingScope,
        "zeroedPadding" to uniformBlock.zeroedPadding.toString(),
    )

private fun GPUPayloadMaterializationRequest.bindGroupEvidenceFacts(uniformBufferLabel: String): Map<String, String> =
    buildMap {
        put("bindingCount", resourceBlock.bindingCount.toString())
        put("dynamicOffsets", resourceBlock.dynamicOffsets.dumpLongList())
        put("layoutHash", reflectedBindingLayoutHash)
        if (resourceBlock.bindingFacts.isNotEmpty()) {
            put("resourceBindingFacts", resourceBlock.bindingFacts.dumpBindingFacts())
        }
        put("resourceDescriptors", resourceBlock.resourceDescriptorLabels.dumpList())
        put("uniformBuffer", uniformBufferLabel)
    }

private fun GPUPayloadMaterializationRequest.bindGroupUsageLabels(): List<String> =
    (listOf("uniform") + resourceBlock.bindingFacts.flatMap { fact -> fact.requiredUsageLabels })
        .distinct()
        .sorted()

private fun GPUResourceBindingFact.toPayloadResourceOperandReference(
    request: GPUPayloadMaterializationRequest,
): GPUMaterializedCommandOperandReference =
    GPUMaterializedCommandOperandReference(
        label = when (kind) {
            GPUResourceBindingKind.SampledTexture -> "texture-view:$bindingLabel"
            GPUResourceBindingKind.Sampler -> "sampler:$bindingLabel"
            GPUResourceBindingKind.StorageBuffer -> "storage-buffer:$bindingLabel"
            GPUResourceBindingKind.UniformBuffer -> "uniform-buffer:$bindingLabel"
        },
        kind = when (kind) {
            GPUResourceBindingKind.SampledTexture -> GPUMaterializedCommandOperandKind.TextureView
            GPUResourceBindingKind.Sampler -> GPUMaterializedCommandOperandKind.Sampler
            GPUResourceBindingKind.StorageBuffer -> GPUMaterializedCommandOperandKind.StorageBuffer
            GPUResourceBindingKind.UniformBuffer -> GPUMaterializedCommandOperandKind.UniformBuffer
        },
        descriptorHash = descriptorHash,
        deviceGeneration = request.deviceGeneration,
        ownerScope = request.payloadOwnerScope(),
        usageLabels = requiredUsageLabels.sorted(),
        invalidationPolicy = "pass-end",
        evidenceFacts = mapOf(
            "bindingKind" to kind.dumpLabel(),
            "bindingLabel" to bindingLabel,
            "bindingLayout" to request.reflectedBindingLayoutHash,
            "resourceGeneration" to actualResourceGeneration.toString(),
            "resourceSlot" to request.resourceSlot.slotId.value,
        ),
    )

private fun List<GPUResourceBindingFact>.dumpBindingFacts(): String =
    if (isEmpty()) {
        "none"
    } else {
        sortedBy { fact -> fact.bindingLabel }
            .joinToString(",") { fact ->
                "${fact.bindingLabel}:${fact.kind.dumpLabel()}:" +
                    "descriptor=${fact.descriptorHash}:" +
                    "usage=${fact.requiredUsageLabels.sorted().joinToString(",")}:" +
                    "generation=${fact.actualResourceGeneration}"
            }
    }

private fun GPUResourceBindingKind.dumpLabel(): String =
    when (this) {
        GPUResourceBindingKind.UniformBuffer -> "uniform-buffer"
        GPUResourceBindingKind.StorageBuffer -> "storage-buffer"
        GPUResourceBindingKind.SampledTexture -> "sampled-texture"
        GPUResourceBindingKind.Sampler -> "sampler"
    }

private fun GPUPayloadMaterializationRequest.uniformCacheKey(): String =
    listOf(
        targetId,
        uniformBlock.scope,
        uniformSlot.slotId.value,
        uniformBlock.fingerprint.value,
        uploadPlan.planHash,
        deviceGeneration.toString(),
        payloadGeneration.toString(),
        uniformBlock.byteSize.toString(),
        "pass-end",
    ).joinToString("|")

private fun GPUPayloadMaterializationRequest.bindGroupCacheKey(): String =
    listOf(
        targetId,
        uniformBlock.scope,
        resourceSlot.slotId.value,
        resourceBlock.fingerprint.value,
        reflectedBindingLayoutHash,
        uniformBlock.fingerprint.value,
        deviceGeneration.toString(),
        payloadGeneration.toString(),
        "pass-end",
    ).joinToString("|")

private fun GPUTextureSamplerMaterializationRequest.textureSamplerDiagnostics(
    context: GPUTargetPreparationContext,
): List<GPUResourceDiagnostic> =
    buildList {
        val textureLabel = textureOperandLabel()
        if (targetId != context.targetId) {
            add(
                GPUResourceDiagnostic.commandOperandTargetMismatch(
                    resourceLabel = resourcePlanLabel(),
                    requestTargetId = targetId,
                    contextTargetId = context.targetId,
                ),
            )
        }
        if (allocation is GPUTextureAllocationPlan.Refuse) {
            add(allocation.diagnostic)
        }
        if (
            allocation is GPUTextureAllocationPlan.ImportExternalTexture ||
            allocation is GPUTextureAllocationPlan.LeaseSurfaceTexture
        ) {
            add(
                GPUResourceDiagnostic.resourcePlanNotSupported(
                    resourceLabel = textureLabel,
                    planKind = allocation.planKindLabel(),
                    supportedPlanKind = "UploadFromArtifact,CreateTexture,ExistingGPUResource",
                ),
            )
        }
        val missingUsage = dumpRequiredTextureUsageLabelsSnapshot - dumpAvailableTextureUsageLabelsSnapshot
        if (missingUsage.isNotEmpty()) {
            add(
                GPUResourceDiagnostic.textureUsageMissing(
                    resourceLabel = textureLabel,
                    missingUsageLabels = missingUsage,
                    availableUsageLabels = dumpAvailableTextureUsageLabelsSnapshot,
                ),
            )
        }
        if (deviceGeneration != context.deviceGeneration) {
            add(
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = textureLabel,
                    expectedDeviceGeneration = context.deviceGeneration,
                    actualDeviceGeneration = deviceGeneration,
                ),
            )
        }
        if (actualResourceGeneration != expectedResourceGeneration) {
            add(
                GPUResourceDiagnostic.bindingGenerationStale(
                    resourceLabel = textureLabel,
                    bindingLabel = binding.bindingLabel,
                    expectedGeneration = expectedResourceGeneration,
                    actualGeneration = actualResourceGeneration,
                ),
            )
        }
        val availableMipLevels = viewDescriptor.mipRange.count()
        if (requiredMipLevels > availableMipLevels) {
            add(
                GPUResourceDiagnostic.textureMipmapUnavailable(
                    resourceLabel = textureLabel,
                    requiredMipLevels = requiredMipLevels,
                    availableMipLevels = availableMipLevels,
                ),
            )
        }
        if (swizzleRequired) {
            add(GPUResourceDiagnostic.textureSwizzleUnimplemented(resourceLabel = textureLabel))
        }
        unsupportedSamplingReason?.let { reason ->
            add(GPUResourceDiagnostic.textureSamplingUnsupported(resourceLabel = textureLabel, reasonCode = reason))
        }
        if (allocation is GPUTextureAllocationPlan.UploadFromArtifact && !uploadCapabilityAvailable) {
            add(
                GPUResourceDiagnostic.uploadCapabilityMissing(
                    resourceLabel = textureLabel,
                    uploadPlanHash = allocation.artifactKey,
                ),
            )
        }
        if (uploadBytes > uploadBudgetBytes) {
            add(
                GPUResourceDiagnostic.uploadBudgetExceeded(
                    resourceLabel = textureLabel,
                    requestedBytes = uploadBytes,
                    budgetBytes = uploadBudgetBytes,
                ),
            )
        }
        uploadFailedReason?.let { reason ->
            add(GPUResourceDiagnostic.textureAllocationFailed(resourceLabel = textureLabel, reason = reason))
        }
        if (activeAttachmentSampled) {
            add(GPUResourceDiagnostic.activeAttachmentSampled(resourceLabel = textureLabel))
        }
    }

private fun GPUTextureSamplerMaterializationRequest.textureOperandLabel(): String =
    "texture:${ownership.ownerLabel}"

private fun GPUTextureSamplerMaterializationRequest.textureViewOperandLabel(): String =
    "texture-view:${binding.bindingLabel}"

private fun GPUTextureSamplerMaterializationRequest.samplerOperandLabel(): String =
    "sampler:${binding.bindingLabel}"

private fun GPUTextureSamplerMaterializationRequest.textureEvidenceFacts(): Map<String, String> =
    buildMap {
        put("allocation", allocation.planKindLabel())
        if (allocation is GPUTextureAllocationPlan.UploadFromArtifact) {
            put("artifact", allocation.artifactKey)
            put("provenance", "UploadedTextureArtifact")
            put("uploadBeforeSample", "true")
        } else {
            put("provenance", allocation.planKindLabel())
            put("uploadBeforeSample", "false")
        }
        put("binding", binding.bindingLabel)
        put("bindingLayout", bindingLayoutHash)
        put("cpuRenderedCompatTexture", "false")
        put("format", textureDescriptor.format)
        put("lifetime", ownership.lifetimeClass)
        put("owner", ownership.ownerLabel)
        put("release", ownership.releasePolicy)
        put("resourceGeneration", actualResourceGeneration.toString())
        put("sampleCount", textureDescriptor.sampleCount.toString())
        put("size", "${textureDescriptor.width}x${textureDescriptor.height}")
        put("uploadBytes", uploadBytes.toString())
    }

private fun GPUTextureSamplerMaterializationRequest.textureViewEvidenceFacts(
    textureOperandLabel: String,
): Map<String, String> =
    mapOf(
        "arrayLayerRange" to viewDescriptor.arrayLayerRange.toString(),
        "binding" to binding.bindingLabel,
        "bindingLayout" to bindingLayoutHash,
        "mipRange" to viewDescriptor.mipRange.toString(),
        "sampleType" to textureDescriptor.format,
        "texture" to textureOperandLabel,
        "viewDimension" to viewDescriptor.viewDimension,
    )

private fun GPUTextureSamplerMaterializationRequest.samplerEvidenceFacts(): Map<String, String> =
    mapOf(
        "address" to "${samplerDescriptor.addressModeU}/${samplerDescriptor.addressModeV}",
        "anisotropy" to samplerDescriptor.maxAnisotropy.toString(),
        "binding" to binding.bindingLabel,
        "bindingLayout" to bindingLayoutHash,
        "compare" to samplerDescriptor.compareMode,
        "filter" to "${samplerDescriptor.magFilter}/${samplerDescriptor.minFilter}",
        "lod" to "${samplerDescriptor.lodMinClamp}..${samplerDescriptor.lodMaxClamp}",
        "mipmap" to samplerDescriptor.mipmapFilter,
    )

private fun GPUTextureDescriptor.materializationDescriptorHash(): String =
    listOf(
        "texture",
        "${width}x$height",
        format,
        "samples=$sampleCount",
        "usage=${usageLabels.sorted().joinToString("+")}",
    ).joinToString(":")

private fun GPUTextureViewDescriptor.materializationViewHash(): String =
    listOf(
        "texture-view",
        textureDescriptorHash,
        viewDimension,
        mipRange.toString(),
        arrayLayerRange.toString(),
    ).joinToString(":")

private fun GPUSamplerDescriptor.materializationSamplerHash(): String =
    listOf(
        "sampler",
        addressModeU,
        addressModeV,
        magFilter,
        minFilter,
        mipmapFilter,
        lodMinClamp,
        lodMaxClamp,
        compareMode,
        maxAnisotropy.toString(),
        capabilityRequirements.sorted().joinToString("+"),
    ).joinToString(":")

private fun GPUPayloadMaterializationRequest.payloadTelemetry(
    result: GPUPayloadMaterializationTelemetryResult,
): List<GPUPayloadMaterializationTelemetryEvent> =
    listOf(
        uniformTelemetry(result),
        bindGroupTelemetry(result),
    )

private fun GPUPayloadMaterializationRequest.uniformTelemetry(
    result: GPUPayloadMaterializationTelemetryResult,
): GPUPayloadMaterializationTelemetryEvent =
    GPUPayloadMaterializationTelemetryEvent(
        lane = "uniform-upload",
        result = result,
        keyHash = uniformBlock.fingerprint.value,
        subjectHash = uploadPlan.planHash,
    )

private fun GPUPayloadMaterializationRequest.bindGroupTelemetry(
    result: GPUPayloadMaterializationTelemetryResult,
): GPUPayloadMaterializationTelemetryEvent =
    GPUPayloadMaterializationTelemetryEvent(
        lane = "bind-group",
        result = result,
        keyHash = resourceBlock.fingerprint.value,
        subjectHash = reflectedBindingLayoutHash,
    )

private fun GPUCommandOperandMaterializationPlan.validationDiagnostics(
    expectedDeviceGeneration: Long,
): List<GPUResourceDiagnostic> =
    buildList {
        if (deviceGeneration != expectedDeviceGeneration) {
            add(
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = label,
                    expectedDeviceGeneration = expectedDeviceGeneration,
                    actualDeviceGeneration = deviceGeneration,
                ),
            )
        }
        val missingUsage = dumpRequiredUsageLabelsSnapshot - dumpAvailableUsageLabelsSnapshot
        if (missingUsage.isNotEmpty()) {
            add(
                GPUResourceDiagnostic.commandOperandUsageMissing(
                    resourceLabel = label,
                    missingUsageLabels = missingUsage,
                    availableUsageLabels = dumpAvailableUsageLabelsSnapshot,
                ),
            )
        }
        evictedReason?.let { reason ->
            add(GPUResourceDiagnostic.resourceEvicted(resourceLabel = label, reason = reason))
        }
    }

private fun GPUCommandOperandMaterializationPlan.toMaterializedReference(): GPUMaterializedCommandOperandReference =
    GPUMaterializedCommandOperandReference(
        label = label,
        kind = kind,
        descriptorHash = descriptorHash,
        deviceGeneration = deviceGeneration,
        ownerScope = ownerScope,
        usageLabels = dumpRequiredUsageLabelsSnapshot.sorted(),
        invalidationPolicy = invalidationPolicy,
        evidenceFacts = dumpEvidenceFactsSnapshot,
    )

private fun List<GPUPayloadMaterializationTelemetryEvent>.dumpPayloadTelemetryLines(): List<String> =
    map { event ->
        "payload.materialization.telemetry " +
            "lane=${event.lane} " +
            "result=${event.result.dumpToken} " +
            "key=${event.keyHash} " +
            "subject=${event.subjectHash} " +
            "productRouteActivated=${event.productRouteActivated}"
    }

private fun List<String>.dumpList(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun List<Long>.dumpLongList(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun List<GPUResourceDiagnostic>.dumpCodes(): String =
    if (isEmpty()) "none" else map { diagnostic -> diagnostic.code }.sorted().joinToString(",")

private fun List<GPUResourceDiagnostic>.dumpLines(): List<String> =
    toList().sortedWith(
        compareBy<GPUResourceDiagnostic> { it.code }
            .thenBy { it.resourceLabel }
            .thenBy { it.terminal.toString() }
            .thenBy { it.dumpFactsSnapshot.dumpFacts() }
            .thenBy { it.message },
    )
        .map { diagnostic ->
            "resource.diagnostic " +
                "code=${diagnostic.code} " +
                "resource=${diagnostic.resourceLabel} " +
                "terminal=${diagnostic.terminal} " +
                "facts=${diagnostic.dumpFactsSnapshot.dumpFacts()}"
        }

private fun Map<String, String>.dumpFacts(): String =
    if (isEmpty()) {
        "none"
    } else {
        entries.sortedBy { entry -> entry.key }
            .joinToString(";") { entry -> "${entry.key}=${entry.value}" }
    }

private fun GPUMaterializedResourceReference.dumpLine(): String =
    "resource-preimage:resource label=$label role=${role.dumpLabel()} " +
        "generation=$generation lifetime=$lifetimeClass descriptor=$descriptorHash " +
        "usage=${dumpUsageLabelsSnapshot.dumpPreimageList()} " +
        "facts=${dumpEvidenceFactsSnapshot.dumpFacts()}"

private fun GPUMaterializedResourceRole.dumpLabel(): String =
    when (this) {
        GPUMaterializedResourceRole.SampledTexture -> "sampled-texture"
        GPUMaterializedResourceRole.Sampler -> "sampler"
        GPUMaterializedResourceRole.DestinationCopyTexture -> "destination-copy-texture"
        GPUMaterializedResourceRole.IntermediateTexture -> "intermediate-texture"
        GPUMaterializedResourceRole.LayerTargetTexture -> "layer-target-texture"
        GPUMaterializedResourceRole.StencilAttachment -> "stencil-attachment"
        GPUMaterializedResourceRole.PassResource -> "pass-resource"
    }

private fun GPUMaterializedCommandOperandKind.dumpLabel(): String =
    when (this) {
        GPUMaterializedCommandOperandKind.RenderPipeline -> "render-pipeline"
        GPUMaterializedCommandOperandKind.ComputePipeline -> "compute-pipeline"
        GPUMaterializedCommandOperandKind.UniformBuffer -> "uniform-buffer"
        GPUMaterializedCommandOperandKind.StorageBuffer -> "storage-buffer"
        GPUMaterializedCommandOperandKind.VertexBuffer -> "vertex-buffer"
        GPUMaterializedCommandOperandKind.IndexBuffer -> "index-buffer"
        GPUMaterializedCommandOperandKind.BindGroup -> "bind-group"
        GPUMaterializedCommandOperandKind.Texture -> "texture"
        GPUMaterializedCommandOperandKind.TextureView -> "texture-view"
        GPUMaterializedCommandOperandKind.Sampler -> "sampler"
        GPUMaterializedCommandOperandKind.RenderTarget -> "render-target"
        GPUMaterializedCommandOperandKind.DepthStencilAttachment -> "depth-stencil-attachment"
        GPUMaterializedCommandOperandKind.DestinationCopyTexture -> "destination-copy-texture"
        GPUMaterializedCommandOperandKind.ReadbackResource -> "readback-resource"
    }

private fun List<String>.dumpPreimageList(): String =
    if (isEmpty()) "none" else sorted().joinToString(",")

private fun GPUResourceMaterializationNonClaims.claimFlagDump(): String =
    "adapterBacked=$adapterBacked liveHandles=$liveHandles productRoute=$productRoute"

private fun GPUResourceMaterializationNonClaims.dumpLine(): String =
    "resource-preimage:nonclaim ${claimFlagDump()} " +
        "providerCalled=$providerCalled submitCalled=$submitCalled"
