package org.graphiks.kanvas.gpu.renderer.state

/** Stable logical target identity; generation remains an explicit key axis. */
@JvmInline
value class GPUTargetIdentity(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUTargetIdentity.value must not be blank" }
    }
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

/** One independently configured color or alpha attachment blend component. */
data class GPUFixedFunctionBlendComponent(
    val sourceFactor: String,
    val destinationFactor: String,
    val operation: String,
)

/** Backend-neutral fixed-function attachment state selected by a pass plan. */
data class GPUFixedFunctionBlendState(
    val stateId: String,
    val color: GPUFixedFunctionBlendComponent,
    val alpha: GPUFixedFunctionBlendComponent,
    val writeMask: String,
) {
    init {
        require(stateId.isNotBlank()) { "GPUFixedFunctionBlendState.stateId must not be blank" }
    }

    /** Returns the canonical attachment-state dump. */
    fun dumpLine(): String =
        "blend:fixed-function state=$stateId " +
            "color=src=${color.sourceFactor},dst=${color.destinationFactor},op=${color.operation} " +
            "alpha=src=${alpha.sourceFactor},dst=${alpha.destinationFactor},op=${alpha.operation} " +
            "writeMask=$writeMask destinationRead=FixedFunctionAttachmentBlend"
}

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

/** Foundation target attachment and store assumptions. */
data class GPUTargetState(
    val target: GPUTargetTextureDescriptor,
    val loadStore: GPULoadStorePlan,
    val alpha: GPUAlphaPlan,
    val sampleState: GPUSampleState,
)
