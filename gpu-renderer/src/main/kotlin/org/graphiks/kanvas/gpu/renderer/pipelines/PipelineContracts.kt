package org.graphiks.kanvas.gpu.renderer.pipelines

/** Render pipeline cache key. */
@JvmInline
value class GPURenderPipelineKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPURenderPipelineKey.value must not be blank" }
    }
}

/** Compute program key before executable pipeline creation. */
@JvmInline
value class GPUComputeProgramKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUComputeProgramKey.value must not be blank" }
    }
}

/** Compute pipeline cache key. */
@JvmInline
value class GPUComputePipelineKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUComputePipelineKey.value must not be blank" }
    }
}

/** Generic pipeline cache key. */
@JvmInline
value class GPUPipelineCacheKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUPipelineCacheKey.value must not be blank" }
    }
}

/** Pipeline key preimage for deterministic cache identity. */
sealed interface GPUPipelineKeyPreimage {
    /** Render pipeline key preimage axes. */
    data class Render(
        val renderStepIdentity: String,
        val materialKeyHash: String,
        val moduleHash: String,
        val vertexLayoutHash: String,
        val targetFormatClass: String,
        val blendStateHash: String,
        val sampleStateHash: String,
        val bindGroupLayoutHash: String,
        val capabilityFacts: List<String>,
        val rendererSalt: String,
    ) : GPUPipelineKeyPreimage

    /** Compute pipeline key preimage axes. */
    data class Compute(
        val programHash: String,
        val moduleHash: String,
        val entryPoint: String,
        val workgroupPolicy: String,
        val resourceTopologyHash: String,
        val capabilityFacts: List<String>,
    ) : GPUPipelineKeyPreimage
}

/** Pipeline creation plan before facade calls. */
data class GPUPipelineCreationPlan(
    val cacheKey: GPUPipelineCacheKey,
    val preimage: GPUPipelineKeyPreimage,
    val moduleHash: String,
    val bindingLayoutHash: String,
    val requiredCapabilities: List<String>,
    val creationStage: String,
)

/** Pipeline diagnostic. */
data class GPUPipelineDiagnostic(
    val code: String,
    val pipelineKey: GPUPipelineCacheKey? = null,
    val preimageHash: String? = null,
    val capabilityFact: String? = null,
    val terminal: Boolean,
)
