package org.graphiks.kanvas.gpu.renderer.runtimeeffects

data class GPUCustomRuntimeEffectExecutionResult(
    val descriptorId: String,
    val outcome: String,
    val reason: String,
    val validationStatus: String,
    val sourceProvenance: String,
    val moduleHash: String,
    val entryPoint: String,
) {
    fun dumpLines(): List<String> {
        return listOf(
            "runtime-effect:custom descriptor=$descriptorId outcome=$outcome " +
                "validation=$validationStatus provenance=$sourceProvenance " +
                "module=$moduleHash entry=$entryPoint reason=$reason",
            "runtime-effect:nonclaim customEffect=true promoted=false productActivation=false",
        )
    }
}

class GPUCustomRuntimeEffectExecutor(
    private val registry: GPUCustomRuntimeEffectRegistry,
) {
    fun execute(id: GPUCustomRuntimeEffectID): GPUCustomRuntimeEffectExecutionResult {
        val descriptor = registry.getDescriptor(id)
        if (descriptor == null) {
            return GPUCustomRuntimeEffectExecutionResult(
                descriptorId = id.value,
                outcome = "refused",
                reason = "unsupported.runtime_effect.custom_wgsl_not_registered",
                validationStatus = "UNKNOWN",
                sourceProvenance = "none",
                moduleHash = "none",
                entryPoint = "none",
            )
        }

        return when (descriptor.validationStatus) {
            GPUCustomRuntimeEffectValidationStatus.PENDING -> GPUCustomRuntimeEffectExecutionResult(
                descriptorId = id.value,
                outcome = "refused",
                reason = "unsupported.runtime_effect.custom_wgsl_pending_validation",
                validationStatus = "PENDING",
                sourceProvenance = descriptor.sourceProvenance,
                moduleHash = descriptor.wgslPlan.moduleHash,
                entryPoint = descriptor.wgslPlan.entryPoint,
            )
            GPUCustomRuntimeEffectValidationStatus.INVALID -> GPUCustomRuntimeEffectExecutionResult(
                descriptorId = id.value,
                outcome = "refused",
                reason = "unsupported.runtime_effect.custom_wgsl_invalid",
                validationStatus = "INVALID",
                sourceProvenance = descriptor.sourceProvenance,
                moduleHash = descriptor.wgslPlan.moduleHash,
                entryPoint = descriptor.wgslPlan.entryPoint,
            )
            GPUCustomRuntimeEffectValidationStatus.VALID -> GPUCustomRuntimeEffectExecutionResult(
                descriptorId = id.value,
                outcome = "accepted",
                reason = "none",
                validationStatus = "VALID",
                sourceProvenance = descriptor.sourceProvenance,
                moduleHash = descriptor.wgslPlan.moduleHash,
                entryPoint = descriptor.wgslPlan.entryPoint,
            )
        }
    }
}
