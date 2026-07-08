package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateDrawRequest
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanner
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlannerRequest
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerSaveRecord
import org.graphiks.kanvas.gpu.renderer.layers.GPULayerScopeID
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

internal class SceneIntermediatePlanAdapter(
    private val planner: GPUIntermediatePlanner = GPUIntermediatePlanner(),
) {
    fun plan(
        sceneId: String,
        drawPlan: RectOnlyDrawPlan,
        width: Int,
        height: Int,
    ): GPUIntermediatePlan {
        val saveLayerFills = drawPlan.fills.filter { it.family == "save-layer" }
        val drawRequests = if (saveLayerFills.isEmpty()) {
            listOf(
                GPUIntermediateDrawRequest(
                    commandId = "scene:$sceneId:direct",
                    targetLabel = "surface:$sceneId",
                    targetGeneration = 1,
                    bounds = sceneBounds(commandId = "scene:$sceneId:direct", width = width, height = height),
                    blendMode = GPUBlendMode.SrcOver,
                    materialKeyHash = "material:scene-direct",
                    renderStepIdentity = "scene-direct",
                ),
            )
        } else {
            saveLayerFills.mapIndexed { index, fill ->
                val nextPaintOrder = saveLayerFills.getOrNull(index + 1)?.paintOrder ?: Int.MAX_VALUE
                val childCommandIds = drawPlan.fills
                    .filter {
                        it.paintOrder > fill.paintOrder &&
                            it.paintOrder < nextPaintOrder &&
                            it.family != "save-layer"
                    }
                    .map { it.label }
                GPUIntermediateDrawRequest(
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
                        childCommandIds = childCommandIds,
                        restoreBlendMode = "srcOver",
                    ),
                )
            }
        }

        return planner.plan(
            GPUIntermediatePlannerRequest(
                planId = "scene-intermediate:$sceneId",
                targetId = "target:$sceneId",
                targetFormatClass = RectOnlyOffscreenRenderer.OFFSCREEN_COLOR_FORMAT,
                targetUsageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
                deviceGeneration = 1,
                drawRequests = drawRequests,
            ),
        )
    }

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
