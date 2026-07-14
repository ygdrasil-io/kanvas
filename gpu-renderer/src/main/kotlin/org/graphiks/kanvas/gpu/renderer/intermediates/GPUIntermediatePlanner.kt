package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.layers.GPULayerBoundsPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerExecutionPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetPlanner
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendSpecializationRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaa
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaCoverageMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaRoute
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceAlphaClassification
import org.graphiks.kanvas.gpu.renderer.passes.GPUTargetBlendFacts

/** Bounds projection shared one-way with destination materialization. */
interface GPUIntermediateBoundsFacts {
    val requestedBoundsLabel: String
    val clippedBoundsLabel: String
    val copyBoundsLabel: String
    val conservative: Boolean
    val finite: Boolean
    val originX: Int
    val originY: Int
    val width: Int
    val height: Int
}

data class GPUIntermediateDrawRequest(
    val commandId: String,
    val targetLabel: String,
    val targetGeneration: Long,
    val bounds: GPUIntermediateBoundsFacts,
    val blendMode: GPUBlendMode,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val saveLayer: GPULayerSaveRecord? = null,
    val activeAttachmentSampled: Boolean = false,
    /** Optional typed existing-intermediate evidence; this planner never invents one. */
    val eligibleIntermediate: GPUIntermediateTextureDescriptor? = null,
) {
    init {
        require(commandId.isNotBlank()) { "GPUIntermediateDrawRequest.commandId must not be blank" }
        require(targetLabel.isNotBlank()) { "GPUIntermediateDrawRequest.targetLabel must not be blank" }
        require(targetGeneration >= 0L) { "GPUIntermediateDrawRequest.targetGeneration must be non-negative" }
        require(materialKeyHash.isNotBlank()) { "GPUIntermediateDrawRequest.materialKeyHash must not be blank" }
        require(renderStepIdentity.isNotBlank()) { "GPUIntermediateDrawRequest.renderStepIdentity must not be blank" }
    }
}

data class GPUIntermediatePlannerRequest(
    val planId: String,
    val targetId: String,
    val targetFormatClass: String,
    val targetUsageLabels: Set<String>,
    val deviceGeneration: Long,
    val samplePlan: GPUSamplePlan = GPUSamplePlan.SingleSampleFrame,
    val msaaAdapter: GPUMsaaAdapterCapability? = null,
    val drawRequests: List<GPUIntermediateDrawRequest>,
) {
    init {
        require(planId.isNotBlank()) { "GPUIntermediatePlannerRequest.planId must not be blank" }
        require(targetId.isNotBlank()) { "GPUIntermediatePlannerRequest.targetId must not be blank" }
        require(targetFormatClass.isNotBlank()) { "GPUIntermediatePlannerRequest.targetFormatClass must not be blank" }
        require(targetUsageLabels.none(String::isBlank)) {
            "GPUIntermediatePlannerRequest.targetUsageLabels must not contain blanks"
        }
        require(deviceGeneration >= 0L) { "GPUIntermediatePlannerRequest.deviceGeneration must be non-negative" }
        require(drawRequests.isNotEmpty()) { "GPUIntermediatePlannerRequest.drawRequests must not be empty" }
    }
}

class GPUIntermediatePlanner(
    private val saveLayerPlanner: GPUSaveLayerIsolatedTargetPlanner = GPUSaveLayerIsolatedTargetPlanner(),
    private val blendPlanner: GPUBlendPlanner = GPUBlendPlanner(),
) {
    fun plan(request: GPUIntermediatePlannerRequest): GPUIntermediatePlan {
        val msaaGate = request.msaaGate()
        if (msaaGate.capabilityRefusal != null) {
            return request.refused(request.targetId, msaaGate.capabilityRefusal)
        }

        val blendPlans = request.drawRequests.map { draw ->
            if (draw.saveLayer == null) planBlend(request, draw) else null
        }
        request.drawRequests.zip(blendPlans).firstOrNull { (_, blend) ->
            blend is GPUBlendPlan.UnsupportedBlend
        }?.let { (draw, blend) ->
            blend as GPUBlendPlan.UnsupportedBlend
            return request.refused(draw.commandId, blend.diagnostic.code)
        }

        if (msaaGate.runtimeRefusal != null) {
            return request.refused(request.targetId, msaaGate.runtimeRefusal)
        }

        val steps = mutableListOf<GPUIntermediatePlanStep>()
        val destinationReadEligibilities = mutableListOf<GPUIntermediateDestinationReadEligibility>()
        var telemetry = GPUIntermediateTelemetry()
        for ((index, draw) in request.drawRequests.withIndex()) {
            val saveLayer = draw.saveLayer
            if (saveLayer != null) {
                val layerSteps = planSaveLayer(request, draw, saveLayer)
                val refusal = layerSteps.singleOrNull() as? GPUIntermediatePlanStep.Refuse
                if (refusal != null) return request.refused(refusal.scopeLabel, refusal.reasonCode)
                steps += layerSteps
                telemetry = telemetry.copy(
                    intermediatesCreated = telemetry.intermediatesCreated + 1,
                    liveIntermediateBytes = telemetry.liveIntermediateBytes + layerSteps.layerBytes(),
                    layerTargets = telemetry.layerTargets + 1,
                    layerComposites = telemetry.layerComposites + 1,
                )
                continue
            }

            val blend = checkNotNull(blendPlans[index])

            if (blend.destinationReadRequirement ==
                org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDestinationReadRequirement.DestinationTextureRequired
            ) {
                destinationReadEligibilities += GPUIntermediateDestinationReadEligibility(
                    commandId = draw.commandId,
                    requirement = blend.destinationReadRequirement,
                    eligibleIntermediate = draw.eligibleIntermediate,
                )
            }
            steps += GPUIntermediatePlanStep.RenderToTarget(
                commandId = draw.commandId,
                targetLabel = draw.targetLabel,
                routeLabel = blend.routeLabel(),
                orderingToken = "order:${draw.commandId}",
            )
        }
        return GPUIntermediatePlan(
            planId = request.planId,
            targetId = request.targetId,
            steps = steps,
            telemetry = telemetry,
            destinationReadEligibilities = destinationReadEligibilities,
        )
    }

    private fun planBlend(
        request: GPUIntermediatePlannerRequest,
        draw: GPUIntermediateDrawRequest,
    ): GPUBlendPlan = blendPlanner.plan(
        GPUBlendSpecializationRequest(
            mode = draw.blendMode,
            coverage = GPUCoverageConsumption.FullOrScissor,
            sourceAlpha = GPUSourceAlphaClassification.Translucent,
            target = GPUTargetBlendFacts(
                formatClass = request.targetFormatClass,
                clampsNormalizedColorWrites = request.targetFormatClass.endsWith("unorm"),
                premultipliedAlpha = true,
            ),
            samplePlan = request.samplePlan,
            activeAttachmentSampled = draw.activeAttachmentSampled,
        ),
    )

    private fun planSaveLayer(
        request: GPUIntermediatePlannerRequest,
        draw: GPUIntermediateDrawRequest,
        saveLayer: GPULayerSaveRecord,
    ): List<GPUIntermediatePlanStep> {
        val layer = saveLayerPlanner.plan(
            GPUSaveLayerIsolatedTargetRequest(
                saveRecord = saveLayer,
                bounds = GPULayerBoundsPlan(
                    requestedBoundsLabel = draw.bounds.requestedBoundsLabel,
                    deviceBoundsLabel = draw.bounds.clippedBoundsLabel,
                    conservative = draw.bounds.conservative,
                    finite = draw.bounds.finite,
                    originX = draw.bounds.originX,
                    originY = draw.bounds.originY,
                    width = draw.bounds.width,
                    height = draw.bounds.height,
                ),
                parentTargetLabel = draw.targetLabel,
                targetFormatClass = request.targetFormatClass,
                sampleCount = 1,
                availableUsageLabels = request.targetUsageLabels,
                deviceGeneration = request.deviceGeneration,
            ),
        )
        val refusal = layer.diagnostics.firstOrNull { it.terminal }?.code
        if (refusal != null) return listOf(GPUIntermediatePlanStep.Refuse(saveLayer.scopeId.value, refusal))

        val isolated = layer.layerPlan.execution as GPULayerExecutionPlan.IsolatedTarget
        val descriptor = GPUIntermediateTextureDescriptor(
            label = isolated.target.targetLabel,
            purpose = GPUIntermediatePurpose.LayerTarget,
            descriptorHash = isolated.target.targetDescriptorHash,
            sourceTargetLabel = draw.targetLabel,
            boundsLabel = layer.layerPlan.bounds.deviceBoundsLabel,
            width = layer.layerPlan.bounds.width,
            height = layer.layerPlan.bounds.height,
            formatClass = isolated.target.formatClass,
            usageLabels = isolated.target.usageLabels,
            sampleCount = isolated.target.sampleCount,
            generation = request.deviceGeneration,
            lifetimeClass = isolated.target.lifetimeClass,
            ownerScope = isolated.target.ownerLabel,
            byteEstimate = isolated.target.byteEstimate,
        )
        return listOf(
            GPUIntermediatePlanStep.CreateIntermediate(descriptor),
            GPUIntermediatePlanStep.RenderLayerChildren(
                scopeLabel = saveLayer.scopeId.value,
                target = descriptor,
                childrenLabel = saveLayer.childCommandIds.joinToString(",").ifEmpty { "none" },
                tokenLabel = isolated.composite.orderingToken.value,
            ),
            GPUIntermediatePlanStep.CompositeIntermediate(
                source = descriptor,
                parentTargetLabel = draw.targetLabel,
                blendModeLabel = isolated.composite.blendModeLabel,
                routeLabel = isolated.composite.compositeRoute,
                tokenLabel = isolated.composite.orderingToken.value,
            ),
        )
    }
}

private data class GPUIntermediateMsaaGate(
    val capabilityRefusal: String? = null,
    val runtimeRefusal: String? = null,
)

private fun GPUIntermediatePlannerRequest.msaaGate(): GPUIntermediateMsaaGate {
    return when (val plan = samplePlan) {
        GPUSamplePlan.SingleSampleFrame,
        is GPUSamplePlan.LocalResolveApproximation,
        -> GPUIntermediateMsaaGate()
        is GPUSamplePlan.MultisampleFrame -> {
            val route = GPUMsaa.resolve(
                GPUMsaaRequest(plan.sampleCount, GPUMsaaCoverageMode.Standard, msaaAdapter),
            )
            when (route) {
                is GPUMsaaRoute.Refused -> GPUIntermediateMsaaGate(
                    capabilityRefusal = route.diagnostic.code,
                )
                is GPUMsaaRoute.Accepted -> GPUIntermediateMsaaGate(
                    runtimeRefusal = "unsupported.msaa.runtime_resolve_unwired",
                )
            }
        }
    }
}

private fun GPUBlendPlan.routeLabel(): String = when (this) {
    is GPUBlendPlan.FixedFunctionBlend -> "fixed-function:${mode.gpuLabel}:${state.stateId}"
    is GPUBlendPlan.ShaderBlendNoDstRead -> "shader:${mode.gpuLabel}:$formulaId"
    is GPUBlendPlan.ShaderBlendWithDstRead ->
        "destination-read-required:${mode.gpuLabel}:$formulaId"
    is GPUBlendPlan.LayerCompositeBlend -> "layer:${child.routeLabel()}"
    is GPUBlendPlan.NoOp -> "no-op:${mode.gpuLabel}"
    is GPUBlendPlan.UnsupportedBlend -> "refused:${diagnostic.code}"
}

private fun GPUIntermediatePlannerRequest.refused(scopeLabel: String, reasonCode: String): GPUIntermediatePlan =
    GPUIntermediatePlan(
        planId = planId,
        targetId = targetId,
        steps = listOf(GPUIntermediatePlanStep.Refuse(scopeLabel, reasonCode)),
        telemetry = GPUIntermediateTelemetry(intermediatesRefused = 1),
    )

private fun List<GPUIntermediatePlanStep>.layerBytes(): Long =
    filterIsInstance<GPUIntermediatePlanStep.CreateIntermediate>().sumOf { it.descriptor.byteEstimate }
