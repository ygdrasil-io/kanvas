package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadEligibleIntermediate
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategy
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyGatePlan
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyPlanner
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadStrategyRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateDrawRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanner
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePurpose
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTelemetry
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerScopeID
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneBlendMode

private val TRANSITIONAL_LAYER_CHILD_FAMILIES = setOf("fill-rect")

internal class SceneIntermediatePlanAdapter(
    private val planDestinationRead: (GPUDestinationReadStrategyRequest) -> GPUDestinationReadStrategyGatePlan =
        GPUDestinationReadStrategyPlanner()::plan,
    private val planIntermediate: (GPUIntermediatePlannerRequest) -> GPUIntermediatePlan = GPUIntermediatePlanner()::plan,
) {
    fun plan(
        sceneId: String,
        drawPlan: RectOnlyDrawPlan,
        width: Int,
        height: Int,
    ): GPUIntermediatePlan {
        val saveLayerFills = drawPlan.fills.filter { it.family == "save-layer" }
        val drawRequests = if (saveLayerFills.isEmpty()) {
            drawPlan.fills.map { fill ->
                GPUIntermediateDrawRequest(
                    commandId = fill.label,
                    targetLabel = "surface:$sceneId",
                    targetGeneration = 1,
                    bounds = sceneBounds(commandId = fill.label, width = width, height = height),
                    blendMode = fill.intermediateBlendMode(),
                    materialKeyHash = "material:${fill.family}:${fill.label}",
                    renderStepIdentity = fill.family,
                )
            }
        } else {
            val requests = mutableListOf<GPUIntermediateDrawRequest>()
            saveLayerFills.forEachIndexed { index, fill ->
                val nextPaintOrder = saveLayerFills.getOrNull(index + 1)?.paintOrder ?: Int.MAX_VALUE
                val children = drawPlan.fills
                    .filter {
                        it.paintOrder > fill.paintOrder &&
                            it.paintOrder < nextPaintOrder &&
                            it.family != "save-layer"
                    }
                val unsupportedChild = children.firstOrNull { it.family !in TRANSITIONAL_LAYER_CHILD_FAMILIES }
                if (unsupportedChild != null) {
                    return refused(
                        sceneId = sceneId,
                        scopeLabel = "layer:${fill.label}",
                        reasonCode = "unsupported.layer.child_family.${unsupportedChild.family}",
                    )
                }
                requests += GPUIntermediateDrawRequest(
                    commandId = fill.label,
                    targetLabel = "surface:$sceneId",
                    targetGeneration = 1,
                    bounds = sceneBounds(commandId = fill.label, width = width, height = height),
                    blendMode = GPUBlendMode.SRC_OVER,
                    materialKeyHash = "material:savelayer",
                    renderStepIdentity = "scene-savelayer",
                    saveLayer = GPULayerSaveRecord(
                        scopeId = GPULayerScopeID("layer:${fill.label}"),
                        boundsLabel = "bounds:${fill.label}",
                        paintLabel = fill.label,
                        backdropRequired = false,
                        childCommandIds = children.map { it.label },
                        restoreBlendMode = "srcOver",
                    ),
                )
            }
            requests
        }

        val request = GPUIntermediatePlannerRequest(
            planId = "scene-intermediate:$sceneId",
            targetId = "target:$sceneId",
            targetFormatClass = OFFSCREEN_COLOR_FORMAT,
            targetUsageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
            deviceGeneration = 1,
            drawRequests = drawRequests,
        )
        return materializeDestinationReadStrategies(planIntermediate(request), request)
    }

    private fun materializeDestinationReadStrategies(
        intermediate: GPUIntermediatePlan,
        request: GPUIntermediatePlannerRequest,
    ): GPUIntermediatePlan {
        var telemetry = intermediate.telemetry
        val drawsByCommand = request.drawRequests.associateBy(GPUIntermediateDrawRequest::commandId)
        val eligibilityByCommand = intermediate.destinationReadEligibilities.associateBy { it.commandId }
        val steps = intermediate.steps.flatMap { step ->
            if (step !is GPUIntermediatePlanStep.RenderToTarget ||
                !step.routeLabel.startsWith("destination-read-required:")
            ) {
                return@flatMap listOf(step)
            }

            val draw = requireNotNull(drawsByCommand[step.commandId])
            val eligibility = eligibilityByCommand[step.commandId]
                ?: return GPUIntermediatePlan(
                    planId = intermediate.planId,
                    targetId = intermediate.targetId,
                    steps = listOf(
                        GPUIntermediatePlanStep.Refuse(
                            draw.commandId,
                            "unsupported.destination_read.eligibility_missing",
                        ),
                    ),
                    telemetry = telemetry.copy(intermediatesRefused = telemetry.intermediatesRefused + 1),
                )
            val strategy = planDestinationRead(
                GPUDestinationReadStrategyRequest(
                    commandId = draw.commandId,
                    requirement = eligibility.requirement,
                    bounds = draw.bounds as GPUDestinationReadBounds,
                    sourceTargetLabel = draw.targetLabel,
                    sourceUsageLabels = request.targetUsageLabels,
                    copyUsageLabels = setOf("render_attachment", "copy_dst", "texture_binding"),
                    targetFormatClass = request.targetFormatClass,
                    targetGeneration = draw.targetGeneration,
                    eligibleIntermediate = eligibility.eligibleIntermediate?.let(
                        ::GPUDestinationReadEligibleIntermediate,
                    ),
                    targetCopyAvailable = "copy_src" in request.targetUsageLabels,
                ),
            )
            val refusal = strategy.diagnostics.singleOrNull { it.terminal }
            if (refusal != null) {
                return GPUIntermediatePlan(
                    planId = intermediate.planId,
                    targetId = intermediate.targetId,
                    steps = listOf(GPUIntermediatePlanStep.Refuse(draw.commandId, refusal.code)),
                    telemetry = telemetry.copy(intermediatesRefused = telemetry.intermediatesRefused + 1),
                )
            }
            when (strategy.plan.strategy) {
                GPUDestinationReadStrategy.BindIntermediate -> {
                    val descriptor = requireNotNull(eligibility.eligibleIntermediate)
                    val binding = requireNotNull(strategy.plan.binding)
                    telemetry = telemetry.copy(
                        destinationReadIntermediateBinds = telemetry.destinationReadIntermediateBinds + 1,
                        intermediatesReused = telemetry.intermediatesReused + 1,
                        liveIntermediateBytes = telemetry.liveIntermediateBytes + descriptor.byteEstimate,
                    )
                    listOf(
                        GPUIntermediatePlanStep.ReuseIntermediate(descriptor),
                        GPUIntermediatePlanStep.BindIntermediate(
                            descriptor = descriptor,
                            bindingLabel = binding.bindingLabel,
                            layoutHash = binding.layoutHash,
                        ),
                        step.copy(routeLabel = "shader-blend:${draw.blendMode.sceneLabel()}"),
                    )
                }
                GPUDestinationReadStrategy.CopyTarget -> {
                    val copy = requireNotNull(strategy.copyDescriptor)
                    val descriptor = GPUIntermediateTextureDescriptor(
                        label = copy.label,
                        purpose = GPUIntermediatePurpose.DestinationCopy,
                        descriptorHash = copy.descriptorHash,
                        sourceTargetLabel = copy.sourceTargetLabel,
                        boundsLabel = strategy.plan.bounds.copyBoundsLabel,
                        width = copy.width,
                        height = copy.height,
                        formatClass = copy.formatClass,
                        usageLabels = copy.usageLabels,
                        sampleCount = copy.sampleCount,
                        generation = copy.targetGeneration,
                        lifetimeClass = copy.lifetimeClass,
                        ownerScope = copy.ownerLabel,
                        byteEstimate = copy.byteEstimate,
                    )
                    val binding = requireNotNull(strategy.plan.binding)
                    val copyPlan = requireNotNull(strategy.copyPlan)
                    telemetry = telemetry.copy(
                        destinationReadCopies = telemetry.destinationReadCopies + 1,
                        destinationReadIntermediateBinds = telemetry.destinationReadIntermediateBinds + 1,
                        copiedBytes = telemetry.copiedBytes + descriptor.byteEstimate,
                        passSplits = telemetry.passSplits + 1,
                        intermediatesCreated = telemetry.intermediatesCreated + 1,
                        liveIntermediateBytes = telemetry.liveIntermediateBytes + descriptor.byteEstimate,
                    )
                    listOf(
                        GPUIntermediatePlanStep.CreateIntermediate(descriptor),
                        GPUIntermediatePlanStep.CopyDestination(
                            sourceLabel = draw.targetLabel,
                            destination = descriptor,
                            boundsLabel = strategy.plan.bounds.copyBoundsLabel,
                            tokenLabel = copyPlan.token.value,
                            passSplitRequired = copyPlan.passSplitRequired,
                            copyBeforeSample = copyPlan.copyBeforeSample,
                        ),
                        GPUIntermediatePlanStep.BindIntermediate(
                            descriptor = descriptor,
                            bindingLabel = binding.bindingLabel,
                            layoutHash = binding.layoutHash,
                        ),
                        step.copy(routeLabel = "shader-blend:${draw.blendMode.sceneLabel()}"),
                    )
                }
                GPUDestinationReadStrategy.None,
                GPUDestinationReadStrategy.FixedFunction,
                GPUDestinationReadStrategy.IsolateLayer,
                GPUDestinationReadStrategy.Refuse,
                -> return GPUIntermediatePlan(
                    planId = intermediate.planId,
                    targetId = intermediate.targetId,
                    steps = listOf(
                        GPUIntermediatePlanStep.Refuse(
                            draw.commandId,
                            "unsupported.destination_read.selected_strategy_unmaterialized",
                        ),
                    ),
                    telemetry = telemetry.copy(intermediatesRefused = telemetry.intermediatesRefused + 1),
                )
            }
        }
        return intermediate.copy(steps = steps, telemetry = telemetry)
    }

    private fun refused(sceneId: String, scopeLabel: String, reasonCode: String): GPUIntermediatePlan =
        GPUIntermediatePlan(
            planId = "scene-intermediate:$sceneId",
            targetId = "target:$sceneId",
            steps = listOf(GPUIntermediatePlanStep.Refuse(scopeLabel = scopeLabel, reasonCode = reasonCode)),
            telemetry = GPUIntermediateTelemetry(intermediatesRefused = 1),
        )

    private fun sceneBounds(
        commandId: String,
        width: Int,
        height: Int,
    ): GPUDestinationReadBounds =
        GPUDestinationReadBounds(
            boundsLabel = "bounds:$commandId",
            conservative = true,
            pixelAligned = true,
            requestedBoundsLabel = "requested:$commandId",
            unclippedBoundsLabel = "unclipped:$commandId",
            clippedBoundsLabel = "device:$width:x:$height",
            copyBoundsLabel = "copy:$commandId",
            originX = 0,
            originY = 0,
            width = width,
            height = height,
            targetWidth = width,
            targetHeight = height,
        )
}

private fun RectOnlyFillDraw.intermediateBlendMode(): GPUBlendMode =
    when (blendMode) {
        SceneBlendMode.Screen -> GPUBlendMode.SCREEN
        SceneBlendMode.Multiply -> GPUBlendMode.MULTIPLY
        SceneBlendMode.SrcOver -> GPUBlendMode.SRC_OVER
    }

private fun GPUBlendMode.sceneLabel(): String =
    name.lowercase().split('_').joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
