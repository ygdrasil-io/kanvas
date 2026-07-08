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
import org.graphiks.kanvas.gpu.renderer.intermediates.GPUIntermediateTextureDescriptor
import org.graphiks.kanvas.gpu.renderer.intermediates.dumpLines
import org.graphiks.kanvas.gpu.renderer.resources.GPUConcreteResourceProvider
import org.graphiks.kanvas.gpu.renderer.resources.GPUIntermediateTextureMaterializationRequest
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneColor
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneRect

private val TRANSITIONAL_LAYER_CHILD_FAMILIES = setOf("fill-rect")

internal sealed interface SceneIntermediateExecutionResult {
    val diagnostics: List<String>

    data class DestinationReadBlend(
        val commandId: String,
        val routeLabel: String,
        val sourceTextureLabel: String,
        val destinationTextureLabel: String,
        val sourceDrawLabel: String,
        val destinationDrawLabels: Set<String>,
        val drawLabels: Set<String>,
    )

    data class Prepared(
        val childLabels: Set<String>,
        val destinationReadDrawLabels: Set<String>,
        val destinationReadBlends: List<DestinationReadBlend>,
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

internal class SceneIntermediatePlanExecutor(
    private val resourceProvider: GPUConcreteResourceProvider = GPUConcreteResourceProvider(),
) {
    fun executeSaveLayerPreparation(
        target: GPUBackendOffscreenTarget,
        drawPlan: RectOnlyDrawPlan,
        plan: GPUIntermediatePlan,
        renderSolidFills: GPUBackendRenderRecorder.(List<RectOnlyFillDraw>) -> Unit,
    ): SceneIntermediateExecutionResult {
        val refusal = plan.steps.firstOrNull { it is GPUIntermediatePlanStep.Refuse } as? GPUIntermediatePlanStep.Refuse
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
        val destinationReadDrawLabels = linkedSetOf<String>()
        val destinationReadBlends = mutableListOf<SceneIntermediateExecutionResult.DestinationReadBlend>()
        val layerTextureByTargetLabel = linkedMapOf<String, String>()
        val fillLabelByTargetLabel = linkedMapOf<String, String>()
        val diagnostics = mutableListOf<String>()

        plan.steps.filterIsInstance<GPUIntermediatePlanStep.RenderToTarget>()
            .filter { renderStep -> renderStep.routeLabel.startsWith("shader-blend:") }
            .forEach { renderStep ->
                val copyStep = plan.steps.filterIsInstance<GPUIntermediatePlanStep.CopyDestination>()
                    .firstOrNull { copy -> copy.destination.label.endsWith(":${renderStep.commandId}") }
                    ?: return@forEach
                val bindStep = plan.steps.filterIsInstance<GPUIntermediatePlanStep.BindIntermediate>()
                    .firstOrNull { bind -> bind.descriptor.label == copyStep.destination.label }
                    ?: return@forEach
                val sourceFill = drawPlan.fills.firstOrNull { fill -> fill.label == renderStep.commandId }
                    ?: throw refusal(
                        scopeLabel = renderStep.commandId,
                        reasonCode = "unsupported.destination_read.source_fill_missing",
                        diagnostics = diagnostics + plan.dumpLines(),
                        stage = "destination-read-preparation",
                    )
                val destinationFills = drawPlan.fills.filter { fill ->
                    fill.paintOrder < sourceFill.paintOrder && fill.family == "fill-rect"
                }
                val unsupportedDestinationFill = drawPlan.fills.firstOrNull { fill ->
                    fill.paintOrder < sourceFill.paintOrder && fill.family != "fill-rect"
                }
                if (unsupportedDestinationFill != null) {
                    throw refusal(
                        scopeLabel = renderStep.commandId,
                        reasonCode = "unsupported.destination_read.prior_family.${unsupportedDestinationFill.family}",
                        diagnostics = diagnostics + plan.dumpLines(),
                        stage = "destination-read-preparation",
                    )
                }
                val followingFill = drawPlan.fills.firstOrNull { fill ->
                    fill.paintOrder > sourceFill.paintOrder
                }
                if (followingFill != null) {
                    throw refusal(
                        scopeLabel = renderStep.commandId,
                        reasonCode = "unsupported.destination_read.following_draw.${followingFill.family}",
                        diagnostics = diagnostics + plan.dumpLines(),
                        stage = "destination-read-preparation",
                    )
                }

                val destinationTextureLabel = materializeAndCreateTexture(
                    target = target,
                    descriptor = copyStep.destination,
                    requiredUsageLabels = setOf("texture_binding", "copy_dst"),
                    diagnostics = diagnostics,
                    stage = "destination-read-preparation",
                )

                val sourceTextureLabel = target.createOffscreenTexture(
                    GPUBackendOffscreenTexture(
                        label = "blend-src:${renderStep.commandId}",
                        width = copyStep.destination.width,
                        height = copyStep.destination.height,
                        format = copyStep.destination.formatClass,
                    ),
                )
                target.encodeOffscreenTexture(
                    textureLabel = sourceTextureLabel,
                    clearColor = GPUClearColor(0.0, 0.0, 0.0, 0.0),
                ) {
                    renderSolidFills(listOf(sourceFill))
                }

                destinationReadDrawLabels += sourceFill.label
                destinationReadBlends += SceneIntermediateExecutionResult.DestinationReadBlend(
                    commandId = renderStep.commandId,
                    routeLabel = renderStep.routeLabel,
                    sourceTextureLabel = sourceTextureLabel,
                    destinationTextureLabel = destinationTextureLabel,
                    sourceDrawLabel = sourceFill.label,
                    destinationDrawLabels = destinationFills.mapTo(linkedSetOf()) { fill -> fill.label },
                    drawLabels = linkedSetOf(sourceFill.label) + destinationFills.map { fill -> fill.label },
                )
                diagnostics +=
                    "intermediate.scene.destination-read-copy-deferred command=${renderStep.commandId} " +
                        "route=${renderStep.routeLabel} sourceTexture=$sourceTextureLabel " +
                        "destinationTexture=$destinationTextureLabel binding=${bindStep.bindingLabel} " +
                        "token=${copyStep.tokenLabel}"
            }

        plan.steps.filterIsInstance<GPUIntermediatePlanStep.RenderLayerChildren>().forEach { layerStep ->
            val fillLabel = layerStep.scopeLabel.removePrefix("layer:")
            val fill = drawPlan.fills.firstOrNull { it.label == fillLabel } ?: return@forEach
            val children = layerChildren(drawPlan, layerStep.childrenLabel)
            val unsupportedChild = children.firstOrNull { it.family !in TRANSITIONAL_LAYER_CHILD_FAMILIES }
            if (unsupportedChild != null) {
                return SceneIntermediateExecutionResult.Refused(
                    scopeLabel = layerStep.scopeLabel,
                    reasonCode = "unsupported.layer.child_family.${unsupportedChild.family}",
                    diagnostics = diagnostics + listOf(
                        "intermediate.scene.refused scope=${layerStep.scopeLabel} " +
                            "reason=unsupported.layer.child_family.${unsupportedChild.family} " +
                            "stage=save-layer-preparation child=${unsupportedChild.label}",
                    ) + plan.dumpLines(),
                )
            }
            childLabels += children.map { it.label }

            val textureLabel = materializeAndCreateTexture(
                target = target,
                descriptor = layerStep.target,
                requiredUsageLabels = setOf("render_attachment", "texture_binding"),
                diagnostics = diagnostics,
                stage = "save-layer-preparation",
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
            destinationReadDrawLabels = destinationReadDrawLabels,
            destinationReadBlends = destinationReadBlends,
            layerTextureByTargetLabel = layerTextureByTargetLabel,
            fillLabelByTargetLabel = fillLabelByTargetLabel,
            plannedComposites = plan.steps.filterIsInstance<GPUIntermediatePlanStep.CompositeIntermediate>(),
            diagnostics = diagnostics,
        )
    }

    private fun materializeAndCreateTexture(
        target: GPUBackendOffscreenTarget,
        descriptor: GPUIntermediateTextureDescriptor,
        requiredUsageLabels: Set<String>,
        diagnostics: MutableList<String>,
        stage: String,
    ): String {
        val decision = resourceProvider.materializeIntermediateTexture(
            GPUIntermediateTextureMaterializationRequest(
                targetId = target.target.targetId,
                descriptor = descriptor,
                deviceGeneration = target.target.deviceGeneration.value,
                actualResourceGeneration = target.target.deviceGeneration.value,
                requiredUsageLabels = requiredUsageLabels,
                activeAttachmentSampled = false,
            ),
            GPUTargetPreparationContext(
                targetId = target.target.targetId,
                frameId = "scene-intermediate:${descriptor.label}",
                deviceGeneration = target.target.deviceGeneration.value,
                budgetClass = "scene-intermediate",
            ),
        )
        diagnostics += resourceProvider.telemetry.dumpLines()
        return when (decision) {
            is GPUResourceMaterializationDecision.Materialized ->
                target.createOffscreenTexture(
                    GPUBackendOffscreenTexture(
                        label = descriptor.label,
                        width = descriptor.width,
                        height = descriptor.height,
                        format = descriptor.formatClass,
                    ),
                )
            is GPUResourceMaterializationDecision.Refused ->
                throw refusal(
                    scopeLabel = descriptor.label,
                    reasonCode = decision.diagnostic.code,
                    diagnostics = diagnostics,
                    stage = stage,
                )
            is GPUResourceMaterializationDecision.Deferred ->
                throw refusal(
                    scopeLabel = descriptor.label,
                    reasonCode = decision.reasonCode,
                    diagnostics = diagnostics,
                    stage = stage,
                )
        }
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
