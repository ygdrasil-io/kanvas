package org.graphiks.kanvas.gpu.renderer.filters

/** Filter node identifier. */
@JvmInline
value class GPUFilterNodeID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFilterNodeID.value must not be blank" }
    }
}

/** Filter ordering token. */
@JvmInline
value class GPUFilterOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFilterOrderingToken.value must not be blank" }
    }
}

/** Filter graph descriptor. */
data class GPUFilterGraphDescriptor(
    val graphId: String,
    val version: Int,
    val sourceRole: String,
    val nodes: List<GPUFilterNodeDescriptor>,
    val edges: List<String>,
    val coordinateSpaces: List<String>,
    val cropLabel: String? = null,
    val provenance: String,
)

/** Filter node descriptor. */
data class GPUFilterNodeDescriptor(
    val nodeId: GPUFilterNodeID,
    val nodeKind: String,
    val inputLabels: List<String>,
    val parameterHash: String,
)

/** Filter node route. */
sealed interface GPUFilterNodeRoute {
    /** Native render route. */
    data class NativeRender(val renderNode: GPUFilterRenderNodePlan) : GPUFilterNodeRoute

    /** Native compute route. */
    data class NativeCompute(val computeNode: GPUFilterComputeNodePlan) : GPUFilterNodeRoute

    /** Copy-only route. */
    data class NativeCopy(val copyLabel: String) : GPUFilterNodeRoute

    /** CPU-prepared artifact route. */
    data class CPUPreparedArtifact(val artifactKey: String) : GPUFilterNodeRoute

    /** Folded material route. */
    data class FoldedMaterial(val materialKeyHash: String) : GPUFilterNodeRoute

    /** Reference-only route for evidence. */
    data class ReferenceOnly(val oracleLabel: String) : GPUFilterNodeRoute

    /** Refused filter route. */
    data class Refused(val diagnostic: GPUFilterDiagnostic) : GPUFilterNodeRoute
}

/** Filter node plan. */
sealed interface GPUFilterNodePlan {
    /** Accepted node plan. */
    data class Accepted(val descriptor: GPUFilterNodeDescriptor, val route: GPUFilterNodeRoute) : GPUFilterNodePlan

    /** Refused node plan. */
    data class Refused(val descriptor: GPUFilterNodeDescriptor, val diagnostic: GPUFilterDiagnostic) : GPUFilterNodePlan
}

/** Filter graph plan. */
data class GPUFilterPlan(
    val graph: GPUFilterGraphDescriptor,
    val nodePlans: List<GPUFilterNodePlan>,
    val intermediates: List<GPUFilterIntermediatePlan>,
    val orderingToken: GPUFilterOrderingToken,
    val diagnostics: List<GPUFilterDiagnostic> = emptyList(),
)

/** Filter input plan. */
data class GPUFilterInputPlan(
    val inputLabel: String,
    val sourcePlan: GPUFilterSourcePlan,
    val sampling: GPUFilterSamplingPlan,
)

/** Filter source plan. */
data class GPUFilterSourcePlan(
    val sourceLabel: String,
    val boundsLabel: String,
    val colorTreatment: String,
)

/** Filter backdrop plan. */
data class GPUFilterBackdropPlan(
    val backdropLabel: String,
    val readBoundsLabel: String,
    val required: Boolean,
)

/** Filter bounds plan. */
data class GPUFilterBoundsPlan(
    val inputBoundsLabel: String,
    val outputBoundsLabel: String,
    val conservative: Boolean,
)

/** Filter crop plan. */
data class GPUFilterCropPlan(
    val cropLabel: String,
    val tilePolicy: GPUFilterTilePlan,
)

/** Filter tile plan. */
data class GPUFilterTilePlan(
    val tileModeX: String,
    val tileModeY: String,
    val decalOutsideCrop: Boolean,
)

/** Filter sampling plan. */
data class GPUFilterSamplingPlan(
    val filterMode: String,
    val mipmapMode: String,
    val coordinateSpaceLabel: String,
)

/** Filter intermediate plan. */
data class GPUFilterIntermediatePlan(
    val intermediateLabel: String,
    val boundsLabel: String,
    val formatClass: String,
    val usageLabels: Set<String>,
    val lifetimeClass: String,
)

/** Filter render-node plan. */
data class GPUFilterRenderNodePlan(
    val renderStepLabel: String,
    val pipelineKeyHash: String,
    val payloadPlanHash: String,
)

/** Filter compute-node plan. */
data class GPUFilterComputeNodePlan(
    val programKeyHash: String,
    val dispatchShape: String,
    val bindingPlanHash: String,
)

/** Filter kernel plan. */
data class GPUFilterKernelPlan(
    val kernelHash: String,
    val radiusX: Float,
    val radiusY: Float,
    val separable: Boolean,
)

/** Filter runtime-effect plan. */
data class GPUFilterRuntimeEffectPlan(
    val effectId: String,
    val descriptorVersion: Int,
    val routeLabel: String,
)

/** Filter color plan. */
data class GPUFilterColorPlan(
    val workingColorSpaceLabel: String,
    val outputColorSpaceLabel: String,
    val conversionPolicy: String,
)

/** Filter cache plan. */
data class GPUFilterCachePlan(
    val cacheKey: String,
    val invalidationFacts: List<String>,
    val reusable: Boolean,
)

/** Filter budget policy. */
data class GPUFilterBudgetPolicy(
    val maxIntermediateBytes: Long,
    val maxPassCount: Int,
    val refusalCode: String? = null,
)

/** Filter intermediate artifact descriptor. */
data class FilterIntermediateArtifact(
    val artifactKey: String,
    val intermediateLabel: String,
    val generation: Long,
    val lifetimeClass: String,
)

/** Filter diagnostic. */
data class GPUFilterDiagnostic(
    val code: String,
    val nodeId: GPUFilterNodeID? = null,
    val message: String,
    val terminal: Boolean,
)
