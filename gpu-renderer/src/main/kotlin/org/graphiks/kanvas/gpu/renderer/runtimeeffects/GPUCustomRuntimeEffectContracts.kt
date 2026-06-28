package org.graphiks.kanvas.gpu.renderer.runtimeeffects

/** Unique identifier for a custom runtime effect, generated from WGSL source hash + uniform schema hash + child slot hash. */
@JvmInline
value class GPUCustomRuntimeEffectID(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUCustomRuntimeEffectID.value must not be blank" }
    }
}

/** Validation status of a custom runtime effect. */
enum class GPUCustomRuntimeEffectValidationStatus {
    PENDING,
    VALID,
    INVALID,
}

/** WGSL plan for a custom runtime effect, carrying the source and validation/reflection results. */
data class GPUCustomRuntimeEffectWGSLPlan(
    val source: String,
    val entryPoint: String,
    val moduleHash: String,
    val reflectionHash: String,
    val validationReportHash: String,
)

/** Descriptor for a custom user-provided WGSL runtime effect. Separate from registered descriptors. */
data class GPUCustomRuntimeEffectDescriptor(
    val id: GPUCustomRuntimeEffectID,
    val uniformSchema: GPURuntimeEffectUniformSchema,
    val childSlots: List<GPURuntimeEffectChildSlotPlan>,
    val wgslPlan: GPUCustomRuntimeEffectWGSLPlan,
    val sourceProvenance: String,
    val validationStatus: GPUCustomRuntimeEffectValidationStatus,
)

/** Registry for custom runtime effects, isolated from GPURuntimeEffectRegistry. */
interface GPUCustomRuntimeEffectRegistry {
    fun register(source: String, uniformSchema: GPURuntimeEffectUniformSchema, childSlots: List<GPURuntimeEffectChildSlotPlan>, sourceProvenance: String): Result<GPUCustomRuntimeEffectID>

    fun lookup(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectDescriptor?

    fun unregister(id: GPUCustomRuntimeEffectID)

    fun isRegistered(id: GPUCustomRuntimeEffectID): Boolean
}
