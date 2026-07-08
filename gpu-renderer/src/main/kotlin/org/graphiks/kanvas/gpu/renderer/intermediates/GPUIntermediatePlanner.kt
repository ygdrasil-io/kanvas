package org.graphiks.kanvas.gpu.renderer.intermediates

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationCopyTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadAction
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadRequirement
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerBoundsPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerExecutionPlan
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetPlanner
import org.graphiks.kanvas.gpu.renderer.layers.GPUSaveLayerIsolatedTargetRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaa
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaCoverageMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaRequest
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaRoute
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistPlanner
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendAllowlistRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

data class GPUIntermediateDrawRequest(
    val commandId: String,
    val targetLabel: String,
    val targetGeneration: Long,
    val bounds: GPUDestinationReadBounds,
    val blendMode: GPUBlendMode,
    val materialKeyHash: String,
    val renderStepIdentity: String,
    val saveLayer: GPULayerSaveRecord? = null,
    val activeAttachmentSampled: Boolean = false,
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
    val requestedSampleCount: Int = 1,
    val msaaAdapter: GPUMsaaAdapterCapability? = null,
    val drawRequests: List<GPUIntermediateDrawRequest>,
) {
    init {
        require(planId.isNotBlank()) { "GPUIntermediatePlannerRequest.planId must not be blank" }
        require(targetId.isNotBlank()) { "GPUIntermediatePlannerRequest.targetId must not be blank" }
        require(targetFormatClass.isNotBlank()) {
            "GPUIntermediatePlannerRequest.targetFormatClass must not be blank"
        }
        require(targetUsageLabels.none { it.isBlank() }) {
            "GPUIntermediatePlannerRequest.targetUsageLabels must not contain blanks"
        }
        require(deviceGeneration >= 0L) { "GPUIntermediatePlannerRequest.deviceGeneration must be non-negative" }
        require(requestedSampleCount > 0) {
            "GPUIntermediatePlannerRequest.requestedSampleCount must be positive"
        }
        require(drawRequests.isNotEmpty()) { "GPUIntermediatePlannerRequest.drawRequests must not be empty" }
    }
}

class GPUIntermediatePlanner(
    private val destinationPlanner: GPUDestinationReadStrategyPlanner = GPUDestinationReadStrategyPlanner(),
    private val saveLayerPlanner: GPUSaveLayerIsolatedTargetPlanner = GPUSaveLayerIsolatedTargetPlanner(),
    private val blendPlanner: GPUBlendAllowlistPlanner = GPUBlendAllowlistPlanner(),
) {
    fun plan(request: GPUIntermediatePlannerRequest): GPUIntermediatePlan {
        val steps = mutableListOf<GPUIntermediatePlanStep>()
        var telemetry = GPUIntermediateTelemetry()
        val msaaRoute = if (request.requestedSampleCount > 1) {
            GPUMsaa.resolve(
                GPUMsaaRequest(
                    requestedSampleCount = request.requestedSampleCount,
                    coverageMode = GPUMsaaCoverageMode.Standard,
                    adapter = request.msaaAdapter,
                ),
            )
        } else {
            null
        }
        if (msaaRoute is GPUMsaaRoute.Refused) {
            return request.refused(request.targetId, msaaRoute.diagnostic.code)
        }
        val acceptedMsaaRoute = msaaRoute as? GPUMsaaRoute.Accepted

        for (draw in request.drawRequests) {
            if (draw.activeAttachmentSampled) {
                return request.refused(draw.commandId, "unsupported.destination_read.active_attachment_sampled")
            }

            val saveLayer = draw.saveLayer
            if (saveLayer != null) {
                val layerSteps = planSaveLayer(request, draw, saveLayer)
                if (layerSteps.singleOrNull() is GPUIntermediatePlanStep.Refuse) {
                    val refusal = layerSteps.single() as GPUIntermediatePlanStep.Refuse
                    return request.refused(refusal.scopeLabel, refusal.reasonCode)
                }
                steps += layerSteps
                telemetry = telemetry.copy(
                    intermediatesCreated = telemetry.intermediatesCreated + 1,
                    liveIntermediateBytes = telemetry.liveIntermediateBytes + layerSteps.layerBytes(),
                    layerTargets = telemetry.layerTargets + 1,
                    layerComposites = telemetry.layerComposites + 1,
                )
                continue
            }

            if (draw.blendMode == GPUBlendMode.Multiply || draw.blendMode == GPUBlendMode.Screen) {
                val destination = destinationPlanner.plan(draw.destinationReadRequest(request))
                val destinationRefusal = destination.diagnostics.firstOrNull { it.terminal }?.code
                if (destinationRefusal != null) {
                    return request.refused(draw.commandId, destinationRefusal)
                }

                val blend = blendPlanner.plan(
                    GPUBlendAllowlistRequest(
                        commandId = draw.commandId,
                        mode = draw.blendMode,
                        targetFormatClass = request.targetFormatClass,
                        materialKeyHash = draw.materialKeyHash,
                        renderStepIdentity = draw.renderStepIdentity,
                        destinationReadPlan = destination,
                        destinationReadCopyBoundsLabel = destination.plan.bounds.copyBoundsLabel,
                        destinationReadGeneration = draw.targetGeneration,
                    ),
                )
                val blendRefusal = blend.diagnostics.firstOrNull { it.terminal }?.code
                if (blendRefusal != null && blendRefusal != "unsupported.blend.shader_route_unvalidated") {
                    return request.refused(draw.commandId, blendRefusal)
                }

                val descriptor = requireNotNull(destination.copyDescriptor).toIntermediateDescriptor(draw)
                val binding = requireNotNull(destination.plan.binding)
                val copyPlan = requireNotNull(destination.copyPlan)
                steps += GPUIntermediatePlanStep.CreateIntermediate(descriptor)
                steps += GPUIntermediatePlanStep.CopyDestination(
                    sourceLabel = draw.targetLabel,
                    destination = descriptor,
                    boundsLabel = destination.plan.bounds.copyBoundsLabel,
                    tokenLabel = copyPlan.token.value,
                    passSplitRequired = true,
                    copyBeforeSample = true,
                )
                steps += GPUIntermediatePlanStep.BindIntermediate(
                    descriptor = descriptor,
                    bindingLabel = binding.bindingLabel,
                    layoutHash = binding.layoutHash,
                )
                steps += GPUIntermediatePlanStep.RenderToTarget(
                    commandId = draw.commandId,
                    targetLabel = draw.targetLabel,
                    routeLabel = "shader-blend:${draw.blendMode}",
                    orderingToken = "order:${draw.commandId}",
                )
                telemetry = telemetry.copy(
                    destinationReadCopies = telemetry.destinationReadCopies + 1,
                    copiedBytes = telemetry.copiedBytes + descriptor.byteEstimate,
                    passSplits = telemetry.passSplits + 1,
                    intermediatesCreated = telemetry.intermediatesCreated + 1,
                    liveIntermediateBytes = telemetry.liveIntermediateBytes + descriptor.byteEstimate,
                )
            } else {
                val blend = blendPlanner.plan(
                    GPUBlendAllowlistRequest(
                        commandId = draw.commandId,
                        mode = draw.blendMode,
                        targetFormatClass = request.targetFormatClass,
                        materialKeyHash = draw.materialKeyHash,
                        renderStepIdentity = draw.renderStepIdentity,
                    ),
                )
                val refusal = blend.diagnostics.firstOrNull { it.terminal }?.code
                if (refusal != null) {
                    return request.refused(draw.commandId, refusal)
                }
                steps += GPUIntermediatePlanStep.RenderToTarget(
                    commandId = draw.commandId,
                    targetLabel = draw.targetLabel,
                    routeLabel = "fixed-function:${draw.blendMode}",
                    orderingToken = "order:${draw.commandId}",
                )
            }
        }
        val finalSteps = if (acceptedMsaaRoute != null) {
            val msaaTarget = request.msaaTargetDescriptor()
            val resolvedTarget = request.msaaResolvedDescriptor(msaaTarget)
            listOf(
                GPUIntermediatePlanStep.CreateIntermediate(msaaTarget),
                GPUIntermediatePlanStep.CreateIntermediate(resolvedTarget),
            ) +
                steps +
                listOf(
                    GPUIntermediatePlanStep.ResolveMSAA(
                        source = msaaTarget,
                        destination = resolvedTarget,
                        strategyLabel = "WGPU_BUILTIN",
                        tokenLabel = "msaa-token:${request.targetId}",
                    ),
                )
        } else {
            steps
        }
        val finalTelemetry = if (acceptedMsaaRoute != null) {
            telemetry.copy(
                msaaTargets = telemetry.msaaTargets + 1,
                msaaResolves = telemetry.msaaResolves + 1,
            )
        } else {
            telemetry
        }

        return GPUIntermediatePlan(
            planId = request.planId,
            targetId = request.targetId,
            steps = finalSteps,
            telemetry = finalTelemetry,
        )
    }

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
        if (refusal != null) {
            return listOf(GPUIntermediatePlanStep.Refuse(saveLayer.scopeId.value, refusal))
        }

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

private fun GPUIntermediatePlannerRequest.refused(scopeLabel: String, reasonCode: String): GPUIntermediatePlan =
    GPUIntermediatePlan(
        planId = planId,
        targetId = targetId,
        steps = listOf(GPUIntermediatePlanStep.Refuse(scopeLabel = scopeLabel, reasonCode = reasonCode)),
        telemetry = GPUIntermediateTelemetry(intermediatesRefused = 1),
    )

private fun GPUIntermediatePlannerRequest.msaaTargetDescriptor(): GPUIntermediateTextureDescriptor {
    val bounds = drawRequests.first().bounds
    return GPUIntermediateTextureDescriptor(
        label = "intermediate:msaa:$targetId",
        purpose = GPUIntermediatePurpose.LayerTarget,
        descriptorHash = "msaa:$targetFormatClass:$requestedSampleCount:$targetId",
        sourceTargetLabel = targetId,
        boundsLabel = bounds.boundsLabel,
        width = bounds.targetWidth,
        height = bounds.targetHeight,
        formatClass = targetFormatClass,
        usageLabels = listOf("render_attachment"),
        sampleCount = requestedSampleCount,
        generation = deviceGeneration,
        lifetimeClass = "pass-local",
        ownerScope = targetId,
        byteEstimate = bounds.targetWidth.toLong() *
            bounds.targetHeight.toLong() *
            4L *
            requestedSampleCount.toLong(),
    )
}

private fun GPUIntermediatePlannerRequest.msaaResolvedDescriptor(
    msaaTarget: GPUIntermediateTextureDescriptor,
): GPUIntermediateTextureDescriptor =
    msaaTarget.copy(
        label = "intermediate:msaa-resolved:$targetId",
        purpose = GPUIntermediatePurpose.MsaaResolve,
        descriptorHash = "msaa-resolved:$targetFormatClass:1:$targetId",
        usageLabels = listOf("texture_binding", "copy_src"),
        sampleCount = 1,
        byteEstimate = msaaTarget.width.toLong() * msaaTarget.height.toLong() * 4L,
    )

private fun GPUIntermediateDrawRequest.destinationReadRequest(
    request: GPUIntermediatePlannerRequest,
): GPUDestinationReadStrategyRequest =
    GPUDestinationReadStrategyRequest(
        commandId = commandId,
        requirement = GPUDestinationReadRequirement.TargetCopy,
        strategy = GPUDestinationReadStrategy.CopyTarget,
        action = GPUDestinationReadAction.SplitPassAndCopyTarget,
        bounds = bounds,
        sourceTargetLabel = targetLabel,
        sourceUsageLabels = request.targetUsageLabels,
        copyUsageLabels = setOf("copy_dst", "texture_binding"),
        targetFormatClass = request.targetFormatClass,
        targetGeneration = targetGeneration,
        observedTargetGeneration = targetGeneration,
        activeAttachmentSampled = activeAttachmentSampled,
        passSplitAllowed = true,
    )

private fun GPUDestinationCopyTextureDescriptor.toIntermediateDescriptor(
    draw: GPUIntermediateDrawRequest,
): GPUIntermediateTextureDescriptor =
    GPUIntermediateTextureDescriptor(
        label = label,
        purpose = GPUIntermediatePurpose.DestinationCopy,
        descriptorHash = descriptorHash,
        sourceTargetLabel = sourceTargetLabel,
        boundsLabel = draw.bounds.copyBoundsLabel,
        width = width,
        height = height,
        formatClass = formatClass,
        usageLabels = usageLabels,
        sampleCount = sampleCount,
        generation = targetGeneration,
        lifetimeClass = lifetimeClass,
        ownerScope = ownerLabel,
        byteEstimate = byteEstimate,
    )

private fun List<GPUIntermediatePlanStep>.layerBytes(): Long =
    filterIsInstance<GPUIntermediatePlanStep.CreateIntermediate>()
        .sumOf { it.descriptor.byteEstimate }
