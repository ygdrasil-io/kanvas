package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

/** Unified facade routing runtime effect IDs to custom or registered executor lanes. */
class GPURuntimeEffectDispatch(
    private val customExecutor: GPUCustomRuntimeEffectExecutor,
    private val registeredExecutor: GPURuntimeEffectExecutor? = null,
) {
    /** Dispatches [effectId] to the custom executor when prefixed with `custom.`, otherwise refuses. */
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

    /** Dispatches a registered runtime effect execution through the registered executor. */
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
