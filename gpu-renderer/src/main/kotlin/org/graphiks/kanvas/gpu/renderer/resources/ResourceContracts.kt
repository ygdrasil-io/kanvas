package org.graphiks.kanvas.gpu.renderer.resources

/** Target-scoped coordinator for resource preparation before submission. */
data class GPUTargetPreparationContext(
    val targetId: String,
    val frameId: String,
    val deviceGeneration: Long,
    val budgetClass: String,
)

/** Texture descriptor containing topology, usage, and format facts. */
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
)

/** Ownership plan for textures, imports, uploads, and surface leases. */
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
    /** Reuse an existing typed GPU texture reference. */
    data class ExistingGPUResource(val ref: GPUTextureResourceRef) : GPUTextureAllocationPlan

    /** Create a new texture from a descriptor. */
    data class CreateTexture(val descriptor: GPUTextureDescriptor, val ownership: GPUTextureOwnershipPlan) : GPUTextureAllocationPlan

    /** Upload pixels from a typed artifact into a texture. */
    data class UploadFromArtifact(val artifactKey: String, val descriptor: GPUTextureDescriptor) : GPUTextureAllocationPlan

    /** Import an external texture through the facade. */
    data class ImportExternalTexture(val descriptor: GPUImportedTextureDescriptor) : GPUTextureAllocationPlan

    /** Lease the current surface texture for this frame. */
    data class LeaseSurfaceTexture(val targetId: String) : GPUTextureAllocationPlan

    /** Refuse allocation before any backend work occurs. */
    data class Refuse(val diagnostic: GPUResourceDiagnostic) : GPUTextureAllocationPlan
}

/** Late materialization decision for resources, pipelines, atlases, and uploads. */
sealed interface GPUResourceMaterializationDecision {
    /** Resource materialization produced concrete typed references. */
    data class Materialized(
        val resources: List<GPUTextureResourceRef>,
        val diagnostics: List<GPUResourceDiagnostic> = emptyList(),
    ) : GPUResourceMaterializationDecision

    /** Resource materialization is deferred until later frame evidence exists. */
    data class Deferred(
        val reasonCode: String,
        val diagnostics: List<GPUResourceDiagnostic> = emptyList(),
    ) : GPUResourceMaterializationDecision

    /** Resource materialization is refused and must be reported. */
    data class Refused(val diagnostic: GPUResourceDiagnostic) : GPUResourceMaterializationDecision
}

/** Provider responsible for creating and looking up GPU resources. */
interface GPUResourceProvider {
    /** Materializes one allocation plan or refuses it explicitly. */
    fun materialize(
        plan: GPUTextureAllocationPlan,
        context: GPUTargetPreparationContext,
    ): GPUResourceMaterializationDecision = TODO("Wire GPUResourceProvider to the concrete GPU facade resource backend")
}

/** Frame-scoped surface texture lease. */
data class GPUSurfaceTextureLease(
    val targetId: String,
    val resourceRef: GPUTextureResourceRef,
    val useToken: GPUUseToken,
)

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

/** Diagnostic emitted by generic resource materialization. */
data class GPUResourceDiagnostic(
    val code: String,
    val resourceLabel: String,
    val message: String,
    val terminal: Boolean,
)
