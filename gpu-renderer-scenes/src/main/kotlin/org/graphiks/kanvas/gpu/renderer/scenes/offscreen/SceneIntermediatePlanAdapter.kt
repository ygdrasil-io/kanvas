package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateDrawRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanner
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTelemetry
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerScopeID
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

private val TRANSITIONAL_LAYER_CHILD_FAMILIES = setOf("fill-rect")

internal class SceneIntermediatePlanAdapter(
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
                    blendMode = fill.intermediateBlendMode(sceneId),
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
                    blendMode = GPUBlendMode.SrcOver,
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

        return planIntermediate(
            GPUIntermediatePlannerRequest(
                planId = "scene-intermediate:$sceneId",
                targetId = "target:$sceneId",
                targetFormatClass = OFFSCREEN_COLOR_FORMAT,
                targetUsageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
                deviceGeneration = 1,
                drawRequests = drawRequests,
            ),
        )
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

private fun RectOnlyFillDraw.intermediateBlendMode(sceneId: String): GPUBlendMode =
    if (sceneId == "dst-read-strategy" && label == "dst-foreground") {
        GPUBlendMode.Screen
    } else {
        GPUBlendMode.SrcOver
    }
