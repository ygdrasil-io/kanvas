package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import java.security.MessageDigest

/** Unique identifier for a custom runtime effect, generated from SHA-256 of WGSL source + uniform schema + child slots. */
@JvmInline
value class GPUCustomRuntimeEffectID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUCustomRuntimeEffectID.value must not be blank" }
    }

    companion object {
        /** Generates a deterministic [GPUCustomRuntimeEffectID] from source, schema, and child slot hashes. */
        fun generate(source: String, schemaHash: String, childSlotHash: String): GPUCustomRuntimeEffectID {
            val input = "custom-runtime-effect-id-v1:$source:$schemaHash:$childSlotHash"
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            val hex = hash.joinToString("") { "%02x".format(it) }.take(12)
            return GPUCustomRuntimeEffectID("custom.$hex")
        }
    }
}

/** Validation status of a custom runtime effect. */
enum class GPUCustomRuntimeEffectValidationStatus {
    PENDING,
    VALID,
    INVALID,
}

/** WGSL plan for a custom runtime effect, carrying the source and validation/reflection hashes. */
data class GPUCustomRuntimeEffectWGSLPlan(
    val source: String,
    val entryPoint: String,
    val moduleHash: String,
    val reflectionHash: String,
    val validationReportHash: String,
) {
    init {
        require(source.isNotBlank()) { "GPUCustomRuntimeEffectWGSLPlan.source must not be blank" }
        require(entryPoint.isNotBlank()) { "GPUCustomRuntimeEffectWGSLPlan.entryPoint must not be blank" }
        require(moduleHash.isNotBlank()) { "GPUCustomRuntimeEffectWGSLPlan.moduleHash must not be blank" }
    }
}

/** Descriptor for a custom user-provided WGSL runtime effect. Separate from registered descriptors. */
data class GPUCustomRuntimeEffectDescriptor(
    val id: GPUCustomRuntimeEffectID,
    val uniformSchema: GPURuntimeEffectUniformSchema,
    val childSlots: List<GPURuntimeEffectChildSlotPlan>,
    val resources: GPURuntimeEffectResourcePlan,
    val wgslPlan: GPUCustomRuntimeEffectWGSLPlan,
    val sourceProvenance: String,
    val validationStatus: GPUCustomRuntimeEffectValidationStatus,
) {
    init {
        require(sourceProvenance.isNotBlank()) { "GPUCustomRuntimeEffectDescriptor.sourceProvenance must not be blank" }
    }
}

/** Validation error returned when custom WGSL registration fails. */
data class GPUCustomRuntimeEffectValidationError(
    val code: String,
    override val message: String,
) : RuntimeException(message)

/** Registry for custom runtime effects, isolated from GPURuntimeEffectRegistry. */
/** Registry for custom runtime effects, isolated from GPURuntimeEffectRegistry. */
interface GPUCustomRuntimeEffectRegistry {
    /** Registers a custom WGSL runtime effect with validation, security checks, and reflection. */
    fun registerCustomEffect(
        source: String,
        uniformSchema: GPURuntimeEffectUniformSchema,
        childSlots: List<GPURuntimeEffectChildSlotPlan>,
        sourceProvenance: String,
    ): Result<GPUCustomRuntimeEffectID>

    /** Returns the descriptor for [id], or null when not registered. */
    fun getDescriptor(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor?

    /** Removes the registered custom effect identified by [id]. */
    fun unregisterCustomEffect(id: GPUCustomRuntimeEffectID)

    /** Returns true when a custom effect is registered for [id]. */
    fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean
}
