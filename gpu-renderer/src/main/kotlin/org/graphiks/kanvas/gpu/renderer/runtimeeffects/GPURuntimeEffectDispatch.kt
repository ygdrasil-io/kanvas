package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

/** Unified facade routing runtime effect IDs to custom or registered executor lanes. */
class GPURuntimeEffectDispatch(
    private val customExecutor: GPUCustomRuntimeEffectExecutor,
    private val registeredExecutor: GPURuntimeEffectExecutor? = null,
    private val registry: GPURuntimeEffectRegistry? = null,
) {
    /** Dispatches [effectId] to the registered executor when known, custom executor when prefixed `custom.`, otherwise refuses. */
    fun dispatch(effectId: String): GPUCustomRuntimeEffectExecutionResult {
        val id = GPURuntimeEffectID(effectId)
        if (registry != null && registry.lookup(id) != null) {
            return GPUCustomRuntimeEffectExecutionResult(
                descriptorId = effectId,
                outcome = "accepted",
                reason = "none",
                validationStatus = "REGISTERED",
                sourceProvenance = "registered-descriptor",
                moduleHash = "pending",
                entryPoint = "pending",
            )
        }
        return when {
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
