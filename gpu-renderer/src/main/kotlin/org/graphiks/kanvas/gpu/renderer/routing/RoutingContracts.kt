package org.graphiks.kanvas.gpu.renderer.routing

/** High-level route family selected for a draw or artifact consumer. */
enum class GPURouteKind {
    /** Fully GPU-native route. */
    GPUNative,
    /** CPU prepares a typed artifact that is consumed by GPU work. */
    CPUPreparedGPU,
    /** CPU is used only as reference evidence, not product fallback. */
    CPUReferenceOnly,
    /** The command is refused with a visible diagnostic. */
    RefuseDiagnostic,
}

/** Native GPU route selected by routing. */
data class GPUNativeRoute(
    val routeId: String,
    val consumerKind: String,
    val renderStepIdentity: String,
    val pipelinePreimageHash: String,
    val requirements: List<String>,
)

/** CPU-prepared artifact route consumed by later GPU work. */
data class CPUPreparedGPURoute(
    val artifactKey: CPUPreparedGPUArtifactKey,
    val artifactType: String,
    val lifetimeClass: String,
    val budgetClass: String,
    val consumerKind: String,
    val invalidationFacts: List<String>,
)

/** CPU reference-only route used for validation evidence. */
data class CPUReferenceOnlyRoute(
    val oracleName: String,
    val evidenceKind: String,
    val diagnosticOnly: Boolean = true,
)

/** Visible refusal diagnostic selected by routing. */
data class RefuseDiagnostic(
    val code: String,
    val message: String,
    val stage: String,
    val terminal: Boolean = true,
)

/** Route decision produced before resource materialization. */
sealed interface GPURouteDecision {
    /** GPU-native route decision. */
    data class Native(val route: GPUNativeRoute) : GPURouteDecision

    /** CPU-prepared artifact route decision. */
    data class Prepared(val route: CPUPreparedGPURoute) : GPURouteDecision

    /** Reference-only decision for validation evidence. */
    data class ReferenceOnly(val route: CPUReferenceOnlyRoute) : GPURouteDecision

    /** Refused route decision. */
    data class Refused(val diagnostic: RefuseDiagnostic) : GPURouteDecision
}

/** Deterministic route-key preimage. */
data class GPURoutePreimage(
    val subjectId: String,
    val commandFamily: String,
    val geometryClass: String,
    val materialClass: String,
    val clipClass: String,
    val targetFormatClass: String,
    val capabilityFacts: List<String>,
    val artifactFacts: List<String> = emptyList(),
)

/** Opaque key for a CPU-prepared GPU artifact descriptor. */
@JvmInline
value class CPUPreparedGPUArtifactKey(val value: String) {
    init {
        require(value.isNotBlank()) { "CPUPreparedGPUArtifactKey.value must not be blank" }
    }
}

/** Descriptor for a typed CPU-prepared artifact that GPU routes may consume. */
data class CPUPreparedGPUArtifactDescriptor(
    val artifactType: String,
    val version: Int,
    val lifetimeClass: String,
    val consumerKind: String,
    val budgetClass: String,
    val invalidationPolicy: String,
    val descriptorHash: String,
)

/** Registry for typed CPU-prepared GPU artifact descriptors. */
interface CPUPreparedGPUArtifactRegistry {
    /** Looks up a descriptor without creating artifacts. */
    fun descriptor(key: CPUPreparedGPUArtifactKey): CPUPreparedGPUArtifactDescriptor?
}

/** Diagnostic emitted by routing. */
data class GPURouteDiagnostic(
    val code: String,
    val decisionKind: GPURouteKind,
    val preimageHash: String,
    val artifactKey: CPUPreparedGPUArtifactKey? = null,
    val terminal: Boolean,
)

/** Builds first-route routing decisions as immutable Kanvas contracts, not backend submission state. */
object GPUFirstRouteDecisionBuilder {
    /** Builds a native FillRect decision only after analysis has validated first-slice command facts. */
    fun nativeFillRect(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.fill_rect.$commandIdValue",
                consumerKind = "native.fill_rect.solid",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a native FillRRect decision only after analysis has validated first-expansion command facts. */
    fun nativeFillRRect(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.fill_rrect.$commandIdValue",
                consumerKind = "native.fill_rrect.solid",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /**
     * Builds a native DrawTextRun decision only after analysis has validated
     * A8 atlas route facts.
     */
    fun nativeDrawTextRun(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
        wgslModuleId: String,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.draw_text_run.$commandIdValue",
                consumerKind = "native.draw_text_run.a8_atlas",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements + listOf("wgsl_module=$wgslModuleId"),
            ),
        )

    /** Builds a native FillRect linear gradient decision only after analysis has validated gradient command facts. */
    fun nativeLinearGradientRect(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.fill_rect.$commandIdValue",
                consumerKind = "native.fill_rect.linear_gradient",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a native FillRRect linear gradient decision only after analysis has validated gradient command facts. */
    fun nativeLinearGradientRRect(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.fill_rrect.$commandIdValue",
                consumerKind = "native.fill_rrect.linear_gradient",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a native FillPath stencil-cover route decision. */
    fun nativeFillPath(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.path_fill.$commandIdValue",
                consumerKind = "native.path_fill.stencil_cover",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a prepared FillPath CPUPreparedGPU route decision. */
    fun preparedFillPath(
        commandIdValue: Int,
        artifactKey: String,
        consumerKind: String,
        invalidationFacts: List<String>,
    ): GPURouteDecision.Prepared =
        GPURouteDecision.Prepared(
            route = CPUPreparedGPURoute(
                artifactKey = CPUPreparedGPUArtifactKey(artifactKey),
                artifactType = "path-fill-tessellation",
                lifetimeClass = "recording-local",
                budgetClass = "path-fill-small",
                consumerKind = consumerKind,
                invalidationFacts = invalidationFacts,
            ),
        )

    /**
     * Builds a prepared DrawImageRect CPUPreparedGPU route decision for decoded
     * image upload before sampler consumption.
     */
    fun preparedDrawImageRect(
        commandIdValue: Int,
        artifactKey: String,
        consumerKind: String,
        invalidationFacts: List<String>,
    ): GPURouteDecision.Prepared =
        GPURouteDecision.Prepared(
            route = CPUPreparedGPURoute(
                artifactKey = CPUPreparedGPUArtifactKey(artifactKey),
                artifactType = "decoded-image-upload",
                lifetimeClass = "recording-local",
                budgetClass = "image-small",
                consumerKind = consumerKind,
                invalidationFacts = invalidationFacts,
            ),
        )

    /**
     * Builds a native DrawImageRect GPU route decision (future, when bitmap
     * WGSL is ready for first-slice promotion).
     */
    fun nativeDrawImageRect(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.draw_image_rect.$commandIdValue",
                consumerKind = "native.draw_image_rect.decoded_pixels",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a native DrawLayer route decision only after analysis has validated saveLayer isolation facts. */
    fun nativeDrawLayer(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.draw_layer.$commandIdValue",
                consumerKind = "native.draw_layer.isolated_target",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a prepared DrawLayer CPUPreparedGPU route decision. */
    fun preparedDrawLayer(
        commandIdValue: Int,
        artifactKey: String,
        consumerKind: String,
        invalidationFacts: List<String>,
    ): GPURouteDecision.Prepared =
        GPURouteDecision.Prepared(
            route = CPUPreparedGPURoute(
                artifactKey = CPUPreparedGPUArtifactKey(artifactKey),
                artifactType = "savelayer-filtered-compositor",
                lifetimeClass = "recording-local",
                budgetClass = "savelayer-small",
                consumerKind = consumerKind,
                invalidationFacts = invalidationFacts,
            ),
        )

    /** Builds a native ApplyFilter decision only after analysis has validated filter command facts. */
    fun nativeApplyFilter(
        commandIdValue: Int,
        pipelinePreimageHash: String,
        renderStepIdentity: String,
        requirements: List<String>,
    ): GPURouteDecision.Native =
        GPURouteDecision.Native(
            route = GPUNativeRoute(
                routeId = "route.apply_filter.$commandIdValue",
                consumerKind = "native.apply_filter.simple_node",
                renderStepIdentity = renderStepIdentity,
                pipelinePreimageHash = pipelinePreimageHash,
                requirements = requirements,
            ),
        )

    /** Builds a prepared ApplyFilter CPUPreparedGPU route decision. */
    fun preparedApplyFilter(
        commandIdValue: Int,
        artifactKey: String,
        consumerKind: String,
        invalidationFacts: List<String>,
    ): GPURouteDecision.Prepared =
        GPURouteDecision.Prepared(
            route = CPUPreparedGPURoute(
                artifactKey = CPUPreparedGPUArtifactKey(artifactKey),
                artifactType = "filter-render-artifact",
                lifetimeClass = "recording-local",
                budgetClass = "filter-small",
                consumerKind = consumerKind,
                invalidationFacts = invalidationFacts,
            ),
        )

    /** Builds a terminal route refusal with the canonical reason code preserved for dumps and gates. */
    fun refused(code: String, stage: String, subject: String = "FillRect first native route"): GPURouteDecision.Refused =
        GPURouteDecision.Refused(
            diagnostic = RefuseDiagnostic(
                code = code,
                message = "$subject refused: $code",
                stage = stage,
                terminal = true,
            ),
        )
}
