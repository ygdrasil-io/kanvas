package org.graphiks.kanvas.gpu.renderer.resources

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
    data class UploadFromArtifact(val artifactKey: String, val descriptor: GPUTextureDescriptor) : GPUTextureAllocationPlan

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
    ) : GPUResourceMaterializationDecision {
        internal val dumpResourcesSnapshot: List<GPUTextureResourceRef> = resources.toList()
        internal val dumpDiagnosticsSnapshot: List<GPUResourceDiagnostic> = diagnostics.toList()
        internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
        internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
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
    ) : GPUResourceMaterializationDecision {
        internal val dumpDiagnosticsSnapshot: List<GPUResourceDiagnostic> = diagnostics.toList()
        internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
        internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()

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

        /** Builds a stale device generation diagnostic. */
        fun deviceGenerationStale(
            resourceLabel: String,
            expectedDeviceGeneration: Long,
            actualDeviceGeneration: Long,
        ): GPUResourceDiagnostic =
            GPUResourceDiagnostic(
                code = "unsupported.texture.device_generation_stale",
                resourceLabel = resourceLabel,
                message = "Texture resource $resourceLabel was created for device generation $actualDeviceGeneration but expected $expectedDeviceGeneration.",
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
        is GPUResourceMaterializationDecision.Materialized ->
            listOf(
                "resource.materialization:materialized " +
                    "target=$targetId " +
                    "tasks=${dumpTaskIdsSnapshot.dumpList()} " +
                    "resourcePlans=${dumpResourcePlanLabelsSnapshot.dumpList()} " +
                    "resourceCount=${dumpResourcesSnapshot.size} " +
                    "diagnostics=${dumpDiagnosticsSnapshot.dumpCodes()}",
            ) + dumpDiagnosticsSnapshot.dumpLines()
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
            ) + dumpDiagnosticsSnapshot.dumpLines()
    }

private const val UNSPECIFIED_DUMP_VALUE = "unspecified"

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

private fun List<String>.dumpList(): String =
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
