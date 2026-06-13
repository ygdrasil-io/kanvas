package org.graphiks.kanvas.gpu.renderer.layers

/** Layer scope identity. */
@JvmInline
value class GPULayerScopeID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPULayerScopeID.value must not be blank" }
    }
}

/** Layer ordering token. */
@JvmInline
value class GPULayerOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPULayerOrderingToken.value must not be blank" }
    }
}

/** Save-layer record captured from input state. */
data class GPULayerSaveRecord(
    val scopeId: GPULayerScopeID,
    val boundsLabel: String,
    val paintLabel: String? = null,
    val backdropRequired: Boolean,
)

/** Restore plan for a layer scope. */
data class GPULayerRestorePlan(
    val scopeId: GPULayerScopeID,
    val compositePlanHash: String,
    val orderingToken: GPULayerOrderingToken,
)

/** Layer bounds plan. */
data class GPULayerBoundsPlan(
    val requestedBoundsLabel: String,
    val deviceBoundsLabel: String,
    val conservative: Boolean,
)

/** Layer target plan. */
data class GPULayerTargetPlan(
    val targetLabel: String,
    val formatClass: String,
    val sampleCount: Int,
    val lifetimeClass: String,
)

/** Layer initialization plan. */
data class GPULayerInitializationPlan(
    val clearPolicy: String,
    val loadPolicy: String,
    val requiresBackdropCopy: Boolean,
)

/** Layer backdrop plan. */
data class GPULayerBackdropPlan(
    val sourceLabel: String,
    val readBoundsLabel: String,
    val copyPolicy: String,
)

/** Layer source plan. */
data class GPULayerSourcePlan(
    val sourceLabel: String,
    val colorTreatment: String,
    val samplingPolicy: String,
)

/** Layer filter chain plan. */
data class GPULayerFilterChainPlan(
    val filterGraphHash: String,
    val intermediateCount: Int,
    val cropPolicy: String,
)

/** Layer composite plan. */
data class GPULayerCompositePlan(
    val sourcePlan: GPULayerSourcePlan,
    val blendModeLabel: String,
    val destinationReadLabel: String? = null,
)

/** Layer elision proof. */
data class GPULayerElisionPlan(
    val canElide: Boolean,
    val proofFacts: List<String>,
    val reasonCode: String,
)

/** Layer task plan. */
data class GPULayerTaskPlan(
    val taskLabels: List<String>,
    val dependencies: List<String>,
)

/** Layer resource plan. */
data class GPULayerResourcePlan(
    val targetPlan: GPULayerTargetPlan,
    val scratchLabels: List<String>,
    val budgetPolicy: GPULayerBudgetPolicy,
)

/** Layer cache plan. */
data class GPULayerCachePlan(
    val cacheKey: String,
    val invalidationFacts: List<String>,
    val reusable: Boolean,
)

/** Layer budget policy. */
data class GPULayerBudgetPolicy(
    val maxBytes: Long,
    val priorityClass: String,
    val refusalCode: String? = null,
)

/** Executable layer plan. */
sealed interface GPULayerExecutionPlan {
    /** Layer can be elided. */
    data class Elided(val elision: GPULayerElisionPlan) : GPULayerExecutionPlan

    /** Layer uses isolated target work. */
    data class IsolatedTarget(val target: GPULayerTargetPlan, val tasks: GPULayerTaskPlan) : GPULayerExecutionPlan

    /** Layer initializes from a backdrop. */
    data class Backdrop(val backdrop: GPULayerBackdropPlan, val initialization: GPULayerInitializationPlan) : GPULayerExecutionPlan

    /** Layer composites back into its parent. */
    data class Composite(val composite: GPULayerCompositePlan) : GPULayerExecutionPlan

    /** Layer planning was refused. */
    data class Refused(val diagnostic: GPULayerDiagnostic) : GPULayerExecutionPlan
}

/** Semantic layer plan. */
data class GPULayerPlan(
    val saveRecord: GPULayerSaveRecord,
    val bounds: GPULayerBoundsPlan,
    val execution: GPULayerExecutionPlan,
    val resources: GPULayerResourcePlan? = null,
    val cache: GPULayerCachePlan? = null,
    val diagnostics: List<GPULayerDiagnostic> = emptyList(),
)

/** Low-level draw-layer plan. */
data class GPUDrawLayer(
    val layerId: String,
    val scopeId: GPULayerScopeID,
    val orderBand: String,
    val insertionLabels: List<String>,
)

/** Draw-layer planner contract. */
interface GPUDrawLayerPlanner {
    /** Plans low-level draw layers from semantic layer labels. */
    fun plan(layerLabels: List<String>): List<GPUDrawLayer> = TODO("Wire GPUDrawLayerPlanner to layer ordering and pass construction")
}

/** Layer diagnostic. */
data class GPULayerDiagnostic(
    val code: String,
    val scopeId: GPULayerScopeID? = null,
    val message: String,
    val terminal: Boolean,
)
