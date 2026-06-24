package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.handoff.GlyphRunDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUFirstRoutePassBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.kanvas.gpu.renderer.text.GPUTextAtlasPlan
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnostic
import org.graphiks.kanvas.gpu.renderer.text.GPUTextDiagnosticCodes
import org.graphiks.kanvas.gpu.renderer.text.GPUTextRoute
import org.graphiks.kanvas.gpu.renderer.text.GPUTextRouteDecision

/**
 * Plans a DrawTextRun command as a native A8 atlas route when the command
 * carries valid A8 atlas artifact refs and the text resource contracts are met.
 */
class GPUTextA8RoutePlanner {
    /**
     * Plans DrawTextRun as native A8 only when the command carries A8-compatible
     * artifact refs and stable atlas generations.
     */
    fun plan(command: NormalizedDrawCommand.DrawTextRun): GPUFirstRoutePlan {
        command.refusalCode()?.let { code ->
            return refusedPlan(command = command, code = code)
        }

        val recordId = "analysis.draw_text_run.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.draw_text_run.a8_atlas.rgba8unorm.src_over"
        val renderStep = "text.a8_mask.sample"
        val wgslModuleId = "text.a8-mask"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawTextRun",
            boundsHash = command.bounds.stableBoundsHash(),
            routeDecisionLabel = "native.draw_text_run.a8_atlas",
            materialKeyHash = "pending.material.text",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.atlasGenerationTokens.map { token ->
                GPUAnalysisDiagnostic(
                    code = "text:atlas_gen=$token",
                    recordId = recordId,
                    terminal = false,
                )
            },
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeDrawTextRun(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf("first_slice.draw_text_run.a8_atlas"),
            wgslModuleId = wgslModuleId,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.draw_text_run.a8_atlas",
            resourceDeclarations = command.artifactRefs.map { ref ->
                "artifact:${ref.artifactType}:${ref.artifactKeyHash}"
            },
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedDrawTextRun(
            commandIdValue = command.commandId.value,
            analysisRecordId = recordId,
            sortKey = command.ordering.paintOrder.toLong(),
            renderStepIdentity = renderStep,
            pipelineKeyHash = pipelineKey,
            boundsHash = command.bounds.stableBoundsHash(),
            scissorBoundsHash = command.scissorBoundsHash(),
            originalPaintOrder = command.ordering.paintOrder,
            targetStateHash = command.targetStateHash(),
        )

        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = analysisDecision,
            routeDecision = routeDecision,
            pass = pass,
        )
    }

    /**
     * Plans the text route decision from a glyph run descriptor's atlas plan.
     *
     * Returns [GPUTextRouteDecision.Accepted] when the atlas plan is accepted,
     * wrapping the route in [GPUTextRoute.AtlasA8]. Returns
     * [GPUTextRouteDecision.Refused] when the atlas plan is refused.
     */
    fun planTextRoute(descriptor: GlyphRunDescriptor): GPUTextRouteDecision =
        when (val plan = descriptor.atlasPlan) {
            is GlyphAtlasUploadPlan.Accepted -> {
                val atlas = GPUTextAtlasPlan(
                    atlasKind = "A8",
                    atlasKey = "a8-atlas-${plan.atlasWidth}x${plan.atlasHeight}",
                    pageCount = 1,
                    budgetClass = "glyph-atlas-budget",
                )
                GPUTextRouteDecision.Accepted(
                    route = GPUTextRoute.AtlasA8(atlas = atlas),
                )
            }
            is GlyphAtlasUploadPlan.Refused -> {
                GPUTextRouteDecision.Refused(
                    diagnostic = GPUTextDiagnostic(
                        code = GPUTextDiagnosticCodes.ATLAS_DESCRIPTOR_UNACCEPTED,
                        message = "Glyph atlas upload refused: ${plan.reason}",
                        terminal = true,
                    ),
                )
            }
        }

    private fun refusedPlan(
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
            boundsHash = command.bounds.stableBoundsHash(),
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
                subject = "DrawTextRun A8 atlas route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedDrawTextRun(
                commandIdValue = command.commandId.value,
                targetStateHash = command.targetStateHash(),
                code = code,
            ),
        )
    }

    private fun NormalizedDrawCommand.DrawTextRun.refusalCode(): String? =
        when {
            artifactRefs.isEmpty() -> "unsupported.text.artifact_unregistered"
            artifactRefs.any { ref -> ref.routeHint != null && ref.routeHint !in acceptedRouteHints } ->
                "unsupported.text.a8_atlas_route_unavailable"
            atlasGenerationTokens.any { token -> !token.startsWith("atlas-generation-") } ->
                "unsupported.text.atlas_generation_stale"
            uploadDependencyFacts.isEmpty() -> "unsupported.text.upload_plan_missing"
            routeDiagnostics.any { diag -> diag.terminal } ->
                routeDiagnostics.first { diag -> diag.terminal }.code
            else -> null
        }

    private companion object {
        val acceptedRouteHints = setOf("AtlasMaskSample", null)
    }
}

private fun NormalizedDrawCommand.DrawTextRun.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

private fun NormalizedDrawCommand.DrawTextRun.scissorBoundsHash(): String? =
    null

private fun GPUBounds.stableBoundsHash(): String =
    "bounds:$left,$top,$right,$bottom"
