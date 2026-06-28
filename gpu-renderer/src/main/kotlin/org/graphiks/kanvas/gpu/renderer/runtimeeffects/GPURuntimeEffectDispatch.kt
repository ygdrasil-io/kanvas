package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

class GPURuntimeEffectDispatch(
    private val customExecutor: GPUCustomRuntimeEffectExecutor,
    private val registeredExecutor: GPURuntimeEffectExecutor? = null,
) {
    fun dispatch(effectId: String): GPUCustomRuntimeEffectExecutionResult = when {
        effectId.startsWith("custom.") -> customExecutor.execute(GPUCustomRuntimeEffectID(effectId))
        else -> GPUCustomRuntimeEffectExecutionResult(
            descriptorId = effectId,
            outcome = "refused",
            reason = "unsupported.runtime_effect.unknown_effect_id",
            validationStatus = "UNKNOWN",
            sourceProvenance = "none",
            moduleHash = "none",
            entryPoint = "none",
        )
    }

    fun dispatchRegistered(
        request: GPURuntimeEffectExecutionRequest,
        context: GPUTargetPreparationContext,
    ): GPURuntimeEffectExecutionResult {
        val executor = requireNotNull(registeredExecutor) {
            "Cannot dispatch registered effect without a registered executor"
        }
        return executor.execute(request, context)
    }
}
