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

internal sealed interface SceneIntermediateExecutionResult {
    val diagnostics: List<String>

    data class Prepared(
        val childLabels: Set<String>,
        val layerTextureByTargetLabel: Map<String, String>,
        val fillLabelByTargetLabel: Map<String, String>,
        val plannedComposites: List<GPUIntermediatePlanStep.CompositeIntermediate>,
        override val diagnostics: List<String>,
    ) : SceneIntermediateExecutionResult

    data class Refused(
        val scopeLabel: String,
        val reasonCode: String,
        override val diagnostics: List<String>,
    ) : SceneIntermediateExecutionResult
}

internal class SceneIntermediateExecutionRefused(
    val scopeLabel: String,
    val reasonCode: String,
    val diagnostics: List<String>,
) : IllegalStateException("scene intermediate execution refused: scope=$scopeLabel reason=$reasonCode")

internal class SceneIntermediatePlanExecutor {
    fun executeSaveLayerPreparation(
        target: GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
        plan: GPUIntermediatePlan,
        renderSolidFills: GPUBackendRenderRecorder.(List<RectOnlyFillDraw>) -> Unit,
    ): SceneIntermediateExecutionResult {
        val refusal = plan.steps.singleOrNull() as? GPUIntermediatePlanStep.Refuse
        if (refusal != null) {
            return SceneIntermediateExecutionResult.Refused(
                scopeLabel = refusal.scopeLabel,
                reasonCode = refusal.reasonCode,
                diagnostics = listOf(
                    "intermediate.scene.refused scope=${refusal.scopeLabel} reason=${refusal.reasonCode} stage=save-layer-preparation",
                ) + plan.dumpLines(),
            )
        }

        val childLabels = linkedSetOf<String>()
        val layerTextureByTargetLabel = linkedMapOf<String, String>()
        val fillLabelByTargetLabel = linkedMapOf<String, String>()
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

            layerTextureByTargetLabel[layerStep.target.label] = textureLabel
            fillLabelByTargetLabel[layerStep.target.label] = fill.label
            diagnostics +=
                "intermediate.scene.layer-prepared scope=${layerStep.scopeLabel} " +
                    "plannedTarget=${layerStep.target.label} texture=$textureLabel children=${layerStep.childrenLabel} token=${layerStep.tokenLabel}"
        }

        diagnostics += plan.dumpLines()

        return SceneIntermediateExecutionResult.Prepared(
            childLabels = childLabels,
            layerTextureByTargetLabel = layerTextureByTargetLabel,
            fillLabelByTargetLabel = fillLabelByTargetLabel,
            plannedComposites = plan.steps.filterIsInstance<GPUIntermediatePlanStep.CompositeIntermediate>(),
            diagnostics = diagnostics,
        )
    }

    fun GPUBackendRenderRecorder.compositeSaveLayers(
        drawPlan: RectOnlyDrawPlan,
        execution: SceneIntermediateExecutionResult.Prepared,
        viewportWidth: Int,
        viewportHeight: Int,
    ): List<String> {
        if (execution.layerTextureByTargetLabel.isNotEmpty() && execution.plannedComposites.isEmpty()) {
            throw refusal(
                scopeLabel = drawPlan.sceneId,
                reasonCode = "unsupported.layer.missing_composite_step",
                diagnostics = execution.diagnostics,
                stage = "save-layer-composite",
            )
        }

        val diagnostics = mutableListOf<String>()

        execution.plannedComposites.forEach { compositeStep ->
            val sourceLabel = compositeStep.source.label
            val fillLabel = execution.fillLabelByTargetLabel[sourceLabel]
                ?: throw refusal(
                    scopeLabel = sourceLabel,
                    reasonCode = "unsupported.layer.composite_missing_fill",
                    diagnostics = execution.diagnostics + diagnostics,
                    stage = "save-layer-composite",
                )
            val fill = drawPlan.fills.firstOrNull { it.label == fillLabel }
                ?: throw refusal(
                    scopeLabel = fillLabel,
                    reasonCode = "unsupported.layer.composite_fill_not_found",
                    diagnostics = execution.diagnostics + diagnostics,
                    stage = "save-layer-composite",
                )
            val textureLabel = execution.layerTextureByTargetLabel[sourceLabel]
                ?: throw refusal(
                    scopeLabel = sourceLabel,
                    reasonCode = "unsupported.layer.composite_missing_texture",
                    diagnostics = execution.diagnostics + diagnostics,
                    stage = "save-layer-composite",
                )
            diagnostics +=
                "intermediate.scene.composite source=${compositeStep.source.label} " +
                    "parent=${compositeStep.parentTargetLabel} blend=${compositeStep.blendModeLabel} " +
                    "route=${compositeStep.routeLabel} token=${compositeStep.tokenLabel} texture=$textureLabel fill=$fillLabel"
            drawCompositePass(
                wgsl = composeSaveLayerCompositeWgsl(),
                colorFormat = OFFSCREEN_COLOR_FORMAT,
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

        return diagnostics
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

    private fun refusal(
        scopeLabel: String,
        reasonCode: String,
        diagnostics: List<String>,
        stage: String,
    ): SceneIntermediateExecutionRefused =
        SceneIntermediateExecutionRefused(
            scopeLabel = scopeLabel,
            reasonCode = reasonCode,
            diagnostics = diagnostics + listOf(
                "intermediate.scene.refused scope=$scopeLabel reason=$reasonCode stage=$stage",
            ),
        )
}
