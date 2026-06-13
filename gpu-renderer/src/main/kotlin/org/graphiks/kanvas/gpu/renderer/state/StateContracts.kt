package org.graphiks.kanvas.gpu.renderer.state

/** Stable blend-mode identity used by blend planning. */
enum class GPUBlendMode {
    /** Source replaces destination subject to alpha plan. */
    Src,
    /** Source-over Porter-Duff composition. */
    SrcOver,
    /** Destination-over Porter-Duff composition. */
    DstOver,
    /** Multiply blend. */
    Multiply,
    /** Screen blend. */
    Screen,
    /** Unsupported or deferred custom mode. */
    Custom,
}

/** Store behavior for target output. */
enum class GPUStorePlan {
    /** Preserve attachment contents after the pass. */
    Store,
    /** Discard attachment contents after the pass. */
    Discard,
    /** Resolve multisample content and store resolved output. */
    ResolveAndStore,
}

/** Descriptor for a render target texture. */
data class GPUTargetTextureDescriptor(
    val width: Int,
    val height: Int,
    val colorFormat: String,
    val usageLabels: Set<String>,
    val isSurfaceBacked: Boolean,
) {
    init {
        require(width > 0) { "GPUTargetTextureDescriptor.width must be positive" }
        require(height > 0) { "GPUTargetTextureDescriptor.height must be positive" }
        require(colorFormat.isNotBlank()) { "GPUTargetTextureDescriptor.colorFormat must not be blank" }
    }
}

/** Alpha-domain and premul handling plan. */
data class GPUAlphaPlan(
    val inputAlpha: String,
    val outputAlpha: String,
    val premultiply: Boolean,
    val clamp: Boolean,
)

/** Blend plan chosen before pipeline-key construction. */
data class GPUBlendPlan(
    val mode: GPUBlendMode,
    val requiresDestinationRead: Boolean,
    val pipelineBlendStateKey: String,
    val unsupportedReasonCode: String? = null,
)

/** Sample count or coverage sample-state contract. */
data class GPUSampleState(
    val sampleCount: Int,
    val coverageSampleCount: Int,
    val alphaToCoverage: Boolean,
) {
    init {
        require(sampleCount > 0) { "GPUSampleState.sampleCount must be positive" }
        require(coverageSampleCount > 0) { "GPUSampleState.coverageSampleCount must be positive" }
    }
}

/** Load/store behavior for a render or layer target. */
data class GPULoadStorePlan(
    val loadOp: String,
    val storePlan: GPUStorePlan,
    val clearColorLabel: String? = null,
)

/** Target attachment and store assumptions for a render route. */
data class GPUTargetState(
    val target: GPUTargetTextureDescriptor,
    val loadStore: GPULoadStorePlan,
    val blend: GPUBlendPlan,
    val alpha: GPUAlphaPlan,
    val sampleState: GPUSampleState,
)
