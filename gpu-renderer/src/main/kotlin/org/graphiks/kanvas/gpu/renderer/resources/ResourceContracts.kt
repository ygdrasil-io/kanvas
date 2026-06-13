package org.graphiks.kanvas.gpu.renderer.resources

/** Provider responsible for creating and looking up GPU resources. */
class GPUResourceProvider

/** Late materialization decision for resources, pipelines, atlases, and uploads. */
class GPUResourceMaterializationDecision

/** Target-scoped coordinator for resource preparation before submission. */
class GPUTargetPreparationContext

/** Texture descriptor containing topology, usage, and format facts. */
class GPUTextureDescriptor

/** Texture view descriptor consumed by binding and resource plans. */
class GPUTextureViewDescriptor

/** Sampler descriptor consumed by material and resource plans. */
class GPUSamplerDescriptor

/** Ownership plan for textures, imports, uploads, and surface leases. */
class GPUTextureOwnershipPlan

/** Allocation plan for a new renderer-owned texture. */
class GPUTextureAllocationPlan

/** Concrete texture reference scoped by owner and device generation. */
class GPUTextureResourceRef

/** Descriptor for an imported texture handle. */
class GPUImportedTextureDescriptor

/** Frame-scoped surface texture lease. */
class GPUSurfaceTextureLease

/** Sampled texture binding contract. */
class GPUSampledTextureBinding

/** Deferred resource plan whose descriptor is known before allocation. */
class GPULazyResourcePlan

/** Externally promised resource plan. */
class GPUPromiseResourcePlan

/** Imported resource plan with ownership and release facts. */
class GPUImportedResourcePlan

/** Resource plan that must be revalidated on every replay. */
class GPUVolatileResourcePlan

/** Token ordering reads, writes, uploads, copies, and mutations. */
class GPUUseToken

/** Token preventing reuse while a resource has pending reads. */
class GPUPendingReadToken

/** Token preventing reads before a pending write completes. */
class GPUPendingWriteToken

/** Scratch allocation token scoped to a limited lifetime. */
class GPUScratchResourceToken

/** Intermediate resource token for layer, filter, or destination work. */
class GPUIntermediateResourceToken

/** Diagnostic emitted by texture ownership or binding planning. */
class GPUTextureDiagnostic

/** Diagnostic emitted by generic resource materialization. */
class GPUResourceDiagnostic
