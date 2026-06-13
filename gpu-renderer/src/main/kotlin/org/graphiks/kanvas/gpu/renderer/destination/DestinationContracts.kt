package org.graphiks.kanvas.gpu.renderer.destination

/** Destination read token. */
@JvmInline
value class GPUDestinationReadToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUDestinationReadToken.value must not be blank" }
    }
}

/** Destination-read requirement. */
enum class GPUDestinationReadRequirement {
    /** No destination read is required. */
    None,
    /** Fixed-function blend is enough. */
    FixedFunctionBlend,
    /** A copy of the current target is required. */
    TargetCopy,
    /** Existing intermediate can satisfy the read. */
    ExistingIntermediate,
    /** Layer isolation is required. */
    LayerIsolation,
    /** Requirement cannot be represented and must refuse. */
    Refused,
}

/** Destination-read strategy. */
enum class GPUDestinationReadStrategy {
    /** No strategy needed. */
    None,
    /** Use fixed-function blend. */
    FixedFunction,
    /** Copy target to an intermediate. */
    CopyTarget,
    /** Bind an existing intermediate. */
    BindIntermediate,
    /** Force isolated layer composition. */
    IsolateLayer,
    /** Refuse the draw. */
    Refuse,
}

/** Destination read bounds. */
data class GPUDestinationReadBounds(
    val boundsLabel: String,
    val conservative: Boolean,
    val pixelAligned: Boolean,
)

/** Destination read binding. */
data class GPUDestinationReadBinding(
    val bindingLabel: String,
    val layoutHash: String,
    val bounds: GPUDestinationReadBounds,
    val generation: Long,
)

/** Destination read plan. */
data class GPUDestinationReadPlan(
    val requirement: GPUDestinationReadRequirement,
    val strategy: GPUDestinationReadStrategy,
    val bounds: GPUDestinationReadBounds,
    val sourceTargetFacts: List<String>,
    val copyDescriptorHash: String? = null,
    val binding: GPUDestinationReadBinding? = null,
    val barrierAction: String? = null,
    val budgetClass: String,
    val diagnostic: GPUDestinationReadDiagnostic? = null,
)

/** Destination read diagnostic. */
data class GPUDestinationReadDiagnostic(
    val code: String,
    val requirement: GPUDestinationReadRequirement,
    val message: String,
    val terminal: Boolean,
)
