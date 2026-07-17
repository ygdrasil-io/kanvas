package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUFirstRoutePassBuilder
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.text.ColorGlyphRefusalKind
import org.graphiks.kanvas.gpu.renderer.text.GPUColorGlyphRouteDecision
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes
import org.graphiks.kanvas.gpu.renderer.text.GPUTextRoute

/**
 * Classifies a resolved COLRv0 color glyph plan into an accepted color route or a
 * stable refusal. COLRv0 plans within the layer budget are accepted; plans over
 * the budget are refused with a layer-count diagnostic. Non-COLRv0 color formats
 * (COLRv1, SVG OpenType, emoji sequences) are refused via [refuseUnsupportedColorFormat].
 */
class GPUColorGlyphRoutePlanner {

    /** Accepts a COLRv0 plan within the layer budget, else refuses with a layer-count diagnostic. */
    fun planColorGlyphRoute(plan: GPUColorGlyphLayerPlan): GPUColorGlyphRouteDecision {
        if (plan.layerCount > MAX_COLOR_LAYERS) {
            return GPUColorGlyphRouteDecision.Refused(
                diagnostic = GPUTextDiagnostic(
                    code = GPUTextDiagnosticCodes.COLOR_FONT_LAYER_COUNT_EXCEEDED,
                    message = "COLRv0 glyph ${plan.baseGlyphID} has ${plan.layerCount} " +
                        "layers (max $MAX_COLOR_LAYERS).",
                    terminal = true,
                ),
                refusalKind = ColorGlyphRefusalKind.LAYER_COUNT_EXCEEDED,
            )
        }
        return GPUColorGlyphRouteDecision.Accepted(
            route = GPUTextRoute.ColorGlyph(plan = plan),
        )
    }

    /**
     * Plans a DrawTextRun carrying COLRv0 color plans as a native composite color
     * route, or a stable refusal when the first plan exceeds the layer budget.
     */
    fun plan(command: NormalizedDrawCommand.DrawTextRun): GPUFirstRoutePlan {
        if (command.colorGlyphPlans.size != 1) {
            return refusedColorPlan(command, GPUTextDiagnosticCodes.COLOR_PLAN_UNSUPPORTED)
        }
        val colorPlan = command.colorGlyphPlans.singleOrNull()
            ?: return refusedColorPlan(command, GPUTextDiagnosticCodes.COLOR_PLAN_UNSUPPORTED)
        return when (val decision = planColorGlyphRoute(colorPlan)) {
            is GPUColorGlyphRouteDecision.Accepted -> acceptedColorPlan(command)
            is GPUColorGlyphRouteDecision.Refused -> refusedColorPlan(command, decision.diagnostic.code)
        }
    }

    private fun acceptedColorPlan(command: NormalizedDrawCommand.DrawTextRun): GPUFirstRoutePlan {
        val recordId = "analysis.draw_text_run.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.draw_text_run.colrv0_composite.rgba8unorm.src_over"
        val renderStep = COLOR_GLYPH_RENDER_STEP_IDENTITY
        val wgslModuleId = "text.colrv0-composite"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawTextRun",
            boundsHash = command.bounds.colorBoundsHash(),
            routeDecisionLabel = "native.draw_text_run.colrv0_composite",
            materialKeyHash = "pending.material.color_text",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = emptyList(),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeDrawTextRun(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf("first_slice.draw_text_run.colrv0_composite"),
            wgslModuleId = wgslModuleId,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.draw_text_run.colrv0_composite",
            resourceDeclarations = command.colorGlyphPlans.map { plan ->
                "color-glyph:${plan.artifactKey.contentFingerprint}:${plan.layerCount}"
            },
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedDrawTextRun(
            commandIdValue = command.commandId.value,
            analysisRecordId = recordId,
            sortKey = command.ordering.paintOrder.toLong(),
            renderStepIdentity = renderStep,
            pipelineKey = GPURenderPipelineKey(pipelineKey),
            blendPlan = command.blend.canonicalPlan(command.layer.target.colorFormat),
            boundsHash = command.bounds.colorBoundsHash(),
            scissorBoundsHash = null,
            originalPaintOrder = command.ordering.paintOrder,
            targetStateHash = command.colorTargetStateHash(),
        )
        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = analysisDecision,
            routeDecision = routeDecision,
            pass = pass,
        )
    }

    private fun refusedColorPlan(
        command: NormalizedDrawCommand.DrawTextRun,
        code: String,
    ): GPUFirstRoutePlan {
        val recordId = "analysis.draw_text_run.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.draw_text_run.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawTextRun",
            boundsHash = command.bounds.colorBoundsHash(),
            routeDecisionLabel = "refused.$code",
            materialKeyHash = "none",
            renderStepCandidates = emptyList(),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = listOf(diagnostic),
        )
        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = GPUDrawAnalysisDecision.Refuse(recordId = recordId, diagnostic = diagnostic),
            routeDecision = GPUFirstRouteDecisionBuilder.refused(
                code = code,
                stage = "analysis",
                subject = "DrawTextRun color glyph route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedDrawTextRun(
                commandIdValue = command.commandId.value,
                targetStateHash = command.colorTargetStateHash(),
                code = code,
            ),
        )
    }

    /** Refuses a non-COLRv0 color format (COLRv1, SVG, emoji) with a stable format diagnostic. */
    fun refuseUnsupportedColorFormat(formatLabel: String): GPUColorGlyphRouteDecision.Refused =
        GPUColorGlyphRouteDecision.Refused(
            diagnostic = GPUTextDiagnostic(
                code = GPUTextDiagnosticCodes.COLOR_FONT_FORMAT_UNAVAILABLE,
                message = "Color font format '$formatLabel' is not supported on the GPU color route.",
                terminal = true,
            ),
            refusalKind = ColorGlyphRefusalKind.FORMAT_UNAVAILABLE,
        )

    companion object {
        /** Maximum COLRv0 layers accepted on the single-pass color route. */
        const val MAX_COLOR_LAYERS: Int = 16
    }
}

private fun NormalizedDrawCommand.DrawTextRun.colorTargetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

private fun GPUBounds.colorBoundsHash(): String =
    "bounds:$left,$top,$right,$bottom"
