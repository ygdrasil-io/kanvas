package org.graphiks.kanvas.gpu.renderer.pipelines

import java.security.MessageDigest

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

/**
 * Pipeline key preimage for deterministic executable cache identity.
 *
 * Preimages are owned by the `pipelines` package and may contain only axes that
 * affect generated code, bind-group layout, vertex layout, attachment state,
 * blend/sample state, or capability class. Per-draw payload values such as rect
 * bounds, radii, RGBA values, and concrete resource identities stay outside
 * this contract.
 */
sealed interface GPUPipelineKeyPreimage {
    /**
     * Render pipeline key preimage axes.
     *
     * `materialKeyHash` must be derived from material layout/code-shape identity
     * and must not include uniform-only payload values such as RGBA. Resource
     * axes describe topology or layout only; texture handles, surface leases,
     * bind-group instances, and residency facts are invalid key material.
     */
    data class Render(
        val renderStepIdentity: String,
        val renderStepVersion: String,
        val primitiveTopology: String,
        val materialKeyHash: String,
        val materialProgramId: String,
        val materialDictionaryVersion: String,
        val materialLayoutHash: String,
        val snippetIdentityHash: String,
        val moduleHash: String,
        val vertexLayoutHash: String,
        val targetFormatClass: String,
        val blendStateHash: String,
        val sampleStateHash: String,
        val bindGroupLayoutHash: String,
        val capabilityClass: String,
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

/**
 * Deterministic pipeline key derivation helpers.
 *
 * These helpers serialize preimages and derive cache keys without touching
 * backend resources or deciding route support. Invalid blank axes fail before a
 * key can be emitted so dumps stay reproducible.
 */
object GPUPipelineKeys {
    /** Serializes a render preimage with stable field and capability ordering. */
    fun canonicalRenderPreimage(preimage: GPUPipelineKeyPreimage.Render): String {
        preimage.requireNonBlankAxes()
        val capabilityFacts = preimage.capabilityFacts.sorted().joinToString(",")
        return listOf(
            "kind=render",
            "rendererSalt=${preimage.rendererSalt}",
            "renderStepIdentity=${preimage.renderStepIdentity}",
            "renderStepVersion=${preimage.renderStepVersion}",
            "primitiveTopology=${preimage.primitiveTopology}",
            "materialKeyHash=${preimage.materialKeyHash}",
            "materialProgramId=${preimage.materialProgramId}",
            "materialDictionaryVersion=${preimage.materialDictionaryVersion}",
            "materialLayoutHash=${preimage.materialLayoutHash}",
            "snippetIdentityHash=${preimage.snippetIdentityHash}",
            "moduleHash=${preimage.moduleHash}",
            "vertexLayoutHash=${preimage.vertexLayoutHash}",
            "targetFormatClass=${preimage.targetFormatClass}",
            "blendStateHash=${preimage.blendStateHash}",
            "sampleStateHash=${preimage.sampleStateHash}",
            "bindGroupLayoutHash=${preimage.bindGroupLayoutHash}",
            "capabilityClass=${preimage.capabilityClass}",
            "capabilityFacts=[$capabilityFacts]",
        ).joinToString("\n")
    }

    /** Derives the compact render pipeline key from executable preimage axes. */
    fun renderPipelineKey(preimage: GPUPipelineKeyPreimage.Render): GPURenderPipelineKey =
        GPURenderPipelineKey("render:${sha256Hex(canonicalRenderPreimage(preimage))}")

    /** Derives the generic cache key for a render pipeline key lookup. */
    fun pipelineCacheKey(preimage: GPUPipelineKeyPreimage.Render): GPUPipelineCacheKey =
        GPUPipelineCacheKey("render-pipeline:${renderPipelineKey(preimage).value}")

    /** Creates a render pipeline creation plan without touching backend resources. */
    fun renderCreationPlan(
        preimage: GPUPipelineKeyPreimage.Render,
        creationStage: String = "render-pipeline",
    ): GPUPipelineCreationPlan =
        GPUPipelineCreationPlan(
            cacheKey = pipelineCacheKey(preimage),
            preimage = preimage,
            moduleHash = preimage.moduleHash,
            bindingLayoutHash = preimage.bindGroupLayoutHash,
            requiredCapabilities = listOf(preimage.capabilityClass) + preimage.capabilityFacts.sorted(),
            creationStage = creationStage,
        )

    private fun GPUPipelineKeyPreimage.Render.requireNonBlankAxes() {
        val axes = mapOf(
            "renderStepIdentity" to renderStepIdentity,
            "renderStepVersion" to renderStepVersion,
            "primitiveTopology" to primitiveTopology,
            "materialKeyHash" to materialKeyHash,
            "materialProgramId" to materialProgramId,
            "materialDictionaryVersion" to materialDictionaryVersion,
            "materialLayoutHash" to materialLayoutHash,
            "snippetIdentityHash" to snippetIdentityHash,
            "moduleHash" to moduleHash,
            "vertexLayoutHash" to vertexLayoutHash,
            "targetFormatClass" to targetFormatClass,
            "blendStateHash" to blendStateHash,
            "sampleStateHash" to sampleStateHash,
            "bindGroupLayoutHash" to bindGroupLayoutHash,
            "capabilityClass" to capabilityClass,
            "rendererSalt" to rendererSalt,
        )
        val blankAxes = axes.filterValues { it.isBlank() }.keys
        require(blankAxes.isEmpty()) { "Render pipeline preimage axes must not be blank: $blankAxes" }
        require(capabilityFacts.none { it.isBlank() }) { "Render pipeline capability facts must not be blank" }
    }
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

private fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { byte -> "%02x".format(byte) }
}
