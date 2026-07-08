package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.math.ceil
import kotlin.math.floor
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTarget
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendOffscreenTexture
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRawUniformDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRectDraw
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendRenderRecorder
import org.graphiks.kanvas.gpu.renderer.execution.GPUClearColor
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlan
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediatePlanStep
import org.graphiks.kanvas.gpu.renderer.intermediates.dumpLines
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

internal data class SceneIntermediateExecutionResult(
    val childLabels: Set<String>,
    val layerTextureByFillLabel: Map<String, String>,
    val diagnostics: List<String>,
)

internal class SceneIntermediatePlanExecutor {
    fun executeSaveLayerPreparation(
        target: GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
        plan: GPUIntermediatePlan,
        renderSolidFills: GPUBackendRenderRecorder.(List<RectOnlyFillDraw>) -> Unit,
    ): SceneIntermediateExecutionResult {
        val childLabels = linkedSetOf<String>()
        val layerTextureByFillLabel = linkedMapOf<String, String>()
        val diagnostics = mutableListOf<String>()

        plan.steps.filterIsInstance<GPUIntermediatePlanStep.RenderLayerChildren>().forEach { layerStep ->
            val fillLabel = layerStep.scopeLabel.removePrefix("layer:")
            val fill = drawPlan.fills.firstOrNull { it.label == fillLabel } ?: return@forEach
            val children = layerChildren(drawPlan, layerStep.childrenLabel)
            childLabels += children.map { it.label }

            val textureLabel = target.createOffscreenTexture(
                GPUBackendOffscreenTexture(
                    width = layerStep.target.width,
                    height = layerStep.target.height,
                    format = layerStep.target.formatClass,
                ),
            )
            target.encodeOffscreenTexture(
                textureLabel,
                clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0),
            ) {
                renderSolidFills(saveLayerPreparationFills(fill, children))
            }

            layerTextureByFillLabel[fill.label] = textureLabel
            diagnostics +=
                "intermediate.scene.layer-prepared scope=${layerStep.scopeLabel} " +
                    "plannedTarget=${layerStep.target.label} texture=$textureLabel children=${layerStep.childrenLabel}"
        }

        diagnostics += plan.dumpLines()

        return SceneIntermediateExecutionResult(
            childLabels = childLabels,
            layerTextureByFillLabel = layerTextureByFillLabel,
            diagnostics = diagnostics,
        )
    }

    fun GPUBackendRenderRecorder.compositeSaveLayers(
        drawPlan: RectOnlyDrawPlan,
        execution: SceneIntermediateExecutionResult,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        drawPlan.fills
            .filter { it.family == "save-layer" }
            .forEach { fill ->
                val textureLabel = execution.layerTextureByFillLabel[fill.label] ?: return@forEach
                drawCompositePass(
                    wgsl = RectOnlyOffscreenRenderer.composeSaveLayerCompositeWgsl(),
                    colorFormat = RectOnlyOffscreenRenderer.OFFSCREEN_COLOR_FORMAT,
                    textureLabel = textureLabel,
                    draws = listOf(
                        GPUBackendRawUniformDraw(
                            uniformBytes = UniformPacker.layerCompositeBytes(
                                SceneColor(0f, 0f, 0f, 0f),
                                fill.groupAlpha,
                            ),
                            scissorX = 0,
                            scissorY = 0,
                            scissorWidth = viewportWidth,
                            scissorHeight = viewportHeight,
                        ),
                    ),
                )
            }
    }

    companion object {
        fun solidRectDraws(fills: List<RectOnlyFillDraw>): List<GPUBackendRectDraw> =
            fills.map { fill ->
                GPUBackendRectDraw(
                    rgbaPremul = floatArrayOf(
                        fill.startColor.r * fill.startColor.a,
                        fill.startColor.g * fill.startColor.a,
                        fill.startColor.b * fill.startColor.a,
                        fill.startColor.a,
                    ),
                    scissorX = fill.scissorX,
                    scissorY = fill.scissorY,
                    scissorWidth = fill.scissorWidth,
                    scissorHeight = fill.scissorHeight,
                )
            }
    }

    private fun layerChildren(
        drawPlan: RectOnlyDrawPlan,
        childrenLabel: String,
    ): List<RectOnlyFillDraw> =
        childrenLabel
            .split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "none" }
            .mapNotNull { label -> drawPlan.fills.firstOrNull { it.label == label } }
            .toList()

    private fun saveLayerPreparationFills(
        saveLayerFill: RectOnlyFillDraw,
        childFills: List<RectOnlyFillDraw>,
    ): List<RectOnlyFillDraw> {
        val offscreenFills = mutableListOf<RectOnlyFillDraw>()

        if (saveLayerFill.shadowColor != null) {
            val shadowRect = SceneRect(
                saveLayerFill.left + saveLayerFill.shadowOffsetX,
                saveLayerFill.top + saveLayerFill.shadowOffsetY,
                saveLayerFill.right + saveLayerFill.shadowOffsetX,
                saveLayerFill.bottom + saveLayerFill.shadowOffsetY,
            )
            val sl = floor(shadowRect.left).toInt()
            val st = floor(shadowRect.top).toInt()
            val sr = ceil(shadowRect.right).toInt()
            val sb = ceil(shadowRect.bottom).toInt()
            offscreenFills += RectOnlyFillDraw(
                label = "${saveLayerFill.label}-shadow",
                family = "fill-rect",
                startColor = saveLayerFill.shadowColor,
                endColor = saveLayerFill.shadowColor,
                bottomLeftColor = saveLayerFill.shadowColor,
                bottomRightColor = saveLayerFill.shadowColor,
                left = shadowRect.left,
                top = shadowRect.top,
                right = shadowRect.right,
                bottom = shadowRect.bottom,
                radius = saveLayerFill.radius,
                paintKind = 0f,
                filterKind = 0f,
                filterStrength = 0f,
                scissorX = sl,
                scissorY = st,
                scissorWidth = sr - sl,
                scissorHeight = sb - st,
                paintOrder = 0,
            )
        }

        offscreenFills += saveLayerFill
        offscreenFills += childFills
        return offscreenFills
    }
}
