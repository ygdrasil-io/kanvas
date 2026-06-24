package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext

/** Executes a registered runtime effect via the descriptor registry. */
class GPURuntimeEffectExecutor(
    private val registry: GPURuntimeEffectRegistry,
) {
    /** Looks up the descriptor, plans the route, and materializes execution. */
    fun execute(
        request: GPURuntimeEffectExecutionRequest,
        context: GPUTargetPreparationContext = defaultContext(request),
    ): GPURuntimeEffectExecutionResult {
        val descriptor = registry.lookup(request.expectedDescriptorId)
            ?: return refusedExecution(request)

        val routeRequest = GPURuntimeEffectDescriptorRouteRequest(
            label = request.label,
            effectId = descriptor.id,
            requestedPlacement = request.expectedRoutePlacement,
            registrySnapshot = GPURuntimeEffectRegistrySnapshot(
                registryVersion = "runtime-registry-v1",
                generation = request.expectedRegistryGeneration,
                descriptors = listOf(descriptor),
                provenance = "executor",
            ),
            wgslEvidence = request.gatePlan.wgslEvidenceFromPlan(),
            cpuOracle = request.gatePlan.cpuOracleFromPlan(),
        )

        val gatePlan = GPURuntimeEffectDescriptorRoutePlanner().plan(routeRequest)
        val materializedRequest = request.copy(gatePlan = gatePlan)
        return ValidatingRuntimeEffectExecutionMaterializer().materialize(materializedRequest, context)
    }

    private fun refusedExecution(request: GPURuntimeEffectExecutionRequest): GPURuntimeEffectExecutionResult {
        val diagnostic = GPUResourceDiagnostic(
            code = "unsupported.runtime_effect.unregistered_descriptor",
            resourceLabel = "runtime-effect-execution:${request.expectedDescriptorId.value}",
            message = "No registered descriptor for ${request.expectedDescriptorId.value}",
            terminal = true,
        )
        return GPURuntimeEffectExecutionResult(
            evidenceRow = "gpu-renderer.runtime-effect.registered.execution",
            descriptorId = request.expectedDescriptorId,
            descriptorVersion = request.expectedDescriptorVersion,
            registryGeneration = request.expectedRegistryGeneration,
            routePlacement = request.expectedRoutePlacement,
            pipelineLabel = null,
            renderPipelineKey = null,
            pipelineCacheKey = request.pipelineCacheKey,
            payloadFingerprint = null,
            bindingLayoutHash = null,
            wgslModuleHash = null,
            reflectionHash = null,
            uniformSchemaHash = null,
            cpuOracleEvidenceHash = null,
            gpuReadbackStatus = "refused",
            gpuReadbackReason = diagnostic.code,
            resourceDecision = GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostic,
                targetId = request.payloadRequest.targetId,
                taskIds = request.payloadRequest.dumpTaskIdsSnapshot,
                resourcePlanLabels = listOf("runtime-effect-execution:${request.expectedDescriptorId.value}"),
                diagnostics = listOf(diagnostic),
            ),
            commandStream = null,
            uniformValuesInKey = false,
        )
    }

    companion object {
        private fun defaultContext(request: GPURuntimeEffectExecutionRequest): GPUTargetPreparationContext =
            GPUTargetPreparationContext(
                targetId = request.payloadRequest.targetId,
                frameId = "executor-frame",
                deviceGeneration = request.payloadRequest.deviceGeneration,
                budgetClass = "runtime-effect-executor",
            )

        private fun GPURuntimeEffectDescriptorGatePlan.wgslEvidenceFromPlan(): GPURuntimeEffectWGSLEvidence? =
            (routePlan as? GPURuntimeEffectRoutePlan.Accepted)?.wgslEvidence

        private fun GPURuntimeEffectDescriptorGatePlan.cpuOracleFromPlan(): GPURuntimeEffectOracleResult? =
            (routePlan as? GPURuntimeEffectRoutePlan.Accepted)?.cpuOracle
    }
}
