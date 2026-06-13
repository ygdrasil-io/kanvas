package org.graphiks.kanvas.gpu.renderer.geometry

/** Shape descriptor captured before geometry lowering. */
data class GPUShapeDescriptor(
    val shapeKind: String,
    val boundsLabel: String,
    val antiAliasMode: String,
    val provenance: String,
)

/** Path descriptor captured before route selection. */
data class GPUPathDescriptor(
    val pathKey: String,
    val verbCount: Int,
    val pointCount: Int,
    val fillRule: String,
    val inverseFill: Boolean,
    val finiteProof: String,
    val volatility: String,
)

/** Stroke descriptor captured before expansion. */
data class GPUStrokeDescriptor(
    val width: Float,
    val cap: String,
    val join: String,
    val miter: Float,
    val dashOrPathEffectRef: String? = null,
)

/** Geometry route selected for a shape. */
sealed interface GPUGeometryRoute {
    /** Analytic geometry route. */
    data class Analytic(val renderStepLabel: String) : GPUGeometryRoute

    /** Tessellation route. */
    data class Tessellation(val tessellationPlanHash: String) : GPUGeometryRoute

    /** Stencil-cover route. */
    data class StencilCover(val stencilPlan: GPUStencilCoverPlan) : GPUGeometryRoute

    /** Path atlas route. */
    data class PathAtlas(val atlasPlan: GPUPathAtlasPlan) : GPUGeometryRoute

    /** Coverage mask route. */
    data class CoverageMask(val atlasPlan: GPUCoverageAtlasPlan) : GPUGeometryRoute

    /** Prepared geometry route. */
    data class Prepared(val artifact: PrecomputedGeometryArtifact) : GPUGeometryRoute

    /** Refused geometry route. */
    data class Refused(val diagnostic: GPUGeometryDiagnostic) : GPUGeometryRoute
}

/** Geometry plan for one shape. */
data class GPUGeometryPlan(
    val descriptor: GPUShapeDescriptor,
    val path: GPUPathDescriptor? = null,
    val stroke: GPUStrokeDescriptor? = null,
    val route: GPUGeometryRoute,
    val diagnostics: List<GPUGeometryDiagnostic> = emptyList(),
)

/** Path bounds plan. */
data class GPUPathBoundsPlan(
    val pathKey: String,
    val boundsLabel: String,
    val conservative: Boolean,
    val proofHash: String,
)

/** Stroke expansion plan. */
data class GPUStrokeExpansionPlan(
    val strokeDescriptorHash: String,
    val expansionMode: String,
    val joinsRequireFallback: Boolean,
    val outputBoundsLabel: String,
)

/** Stencil-cover execution plan. */
data class GPUStencilCoverPlan(
    val stencilStepLabel: String,
    val coverStepLabel: String,
    val fillRule: String,
    val requiresMSAA: Boolean,
)

/** Prepared geometry artifact plan. */
data class GPUPreparedGeometryPlan(
    val artifact: PrecomputedGeometryArtifact,
    val consumerKind: String,
    val invalidationFacts: List<String>,
)

/** Geometry render-step plan. */
data class GPUGeometryRenderStepPlan(
    val renderStepLabel: String,
    val geometryClass: String,
    val coverageClass: String,
    val vertexLayoutHash: String,
)

/** Path atlas plan. */
data class GPUPathAtlasPlan(
    val atlasPolicy: GPUAtlasPolicy,
    val entryRef: GPUAtlasEntryRef,
    val mutationPlan: GPUAtlasMutationPlan,
)

/** Coverage atlas plan. */
data class GPUCoverageAtlasPlan(
    val atlasPolicy: GPUAtlasPolicy,
    val entryRef: GPUAtlasEntryRef,
    val maskArtifact: CoverageMaskArtifact,
)

/** Atlas storage policy. */
data class GPUAtlasPolicy(
    val atlasKind: String,
    val budget: GPUAtlasBudgetPolicy,
    val evictionPolicy: String,
)

/** Atlas budget policy. */
data class GPUAtlasBudgetPolicy(
    val maxBytes: Long,
    val maxEntries: Int,
    val pressureClass: String,
)

/** Atlas entry reference. */
@JvmInline
value class GPUAtlasEntryRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUAtlasEntryRef.value must not be blank" }
    }
}

/** Atlas mutation plan. */
data class GPUAtlasMutationPlan(
    val mutationId: String,
    val entryRef: GPUAtlasEntryRef,
    val operation: String,
    val useTokenLabel: String,
)

/** Path atlas artifact descriptor. */
data class PathAtlasArtifact(
    val artifactKey: String,
    val boundsLabel: String,
    val generation: Long,
    val lifetimeClass: String,
    val budgetClass: String,
)

/** Coverage mask artifact descriptor. */
data class CoverageMaskArtifact(
    val artifactKey: String,
    val boundsLabel: String,
    val generation: Long,
    val lifetimeClass: String,
    val budgetClass: String,
)

/** Precomputed geometry artifact descriptor. */
data class PrecomputedGeometryArtifact(
    val artifactKey: String,
    val boundsLabel: String,
    val generation: Long,
    val lifetimeClass: String,
    val budgetClass: String,
)

/** Geometry diagnostic. */
data class GPUGeometryDiagnostic(
    val code: String,
    val geometryLabel: String,
    val message: String,
    val terminal: Boolean,
)
