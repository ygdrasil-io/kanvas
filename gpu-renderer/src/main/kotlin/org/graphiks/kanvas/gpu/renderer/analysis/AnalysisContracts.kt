package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass
import org.graphiks.kanvas.gpu.renderer.passes.GPUFirstRoutePassBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision

/** Compact stable sort-key value. */
@JvmInline
value class SortKey(val value: Long)

/** Immutable draw analysis for one recording scope. */
data class GPUDrawAnalysis(
    val analysisId: String,
    val records: List<GPUDrawAnalysisRecord>,
    val dependencies: List<GPUAnalysisDependency>,
    val occlusionProofs: List<GPUOcclusionProof>,
    val diagnostics: List<GPUAnalysisDiagnostic>,
)

/** One analyzed draw command record. */
data class GPUDrawAnalysisRecord(
    val recordId: String,
    val commandIdValue: Int,
    val commandFamily: String,
    val boundsHash: String,
    val routeDecisionLabel: String,
    val materialKeyHash: String,
    val renderStepCandidates: List<String>,
    val sortKey: SortKey,
    val diagnostics: List<GPUAnalysisDiagnostic> = emptyList(),
)

/** Analysis-time decision for a draw record. */
sealed interface GPUDrawAnalysisDecision {
    /** Draw remains a route candidate. */
    data class Candidate(
        val recordId: String,
        val routeDecisionLabel: String,
        val resourceDeclarations: List<String>,
        val renderStepCandidates: List<String>,
    ) : GPUDrawAnalysisDecision

    /** Draw is culled by bounds or clip evidence. */
    data class Cull(val recordId: String, val reasonCode: String) : GPUDrawAnalysisDecision

    /** Draw is discarded by analysis as no-op. */
    data class Discard(val recordId: String, val reasonCode: String) : GPUDrawAnalysisDecision

    /** Draw is refused by analysis. */
    data class Refuse(val recordId: String, val diagnostic: GPUAnalysisDiagnostic) : GPUDrawAnalysisDecision
}

/** Occlusion analysis service contract. */
interface GPUOcclusionTracker {
    /** Proves occlusion without mutating recordings. */
    fun prove(records: List<GPUDrawAnalysisRecord>): List<GPUOcclusionProof> = TODO("Wire GPUOcclusionTracker to concrete occlusion analysis")
}

/** Proof that one analyzed draw is hidden or constrained. */
data class GPUOcclusionProof(
    val proofId: String,
    val hiddenRecordId: String,
    val coveringRecordId: String,
    val proofClass: String,
    val boundsProofHash: String,
    val reasonCode: String,
)

/** Analysis dependency between draw records. */
data class GPUAnalysisDependency(
    val fromRecordId: String,
    val toRecordId: String,
    val kind: String,
    val barrierGeneration: Long,
    val reasonCode: String,
)

/** Diagnostic emitted by draw analysis. */
data class GPUAnalysisDiagnostic(
    val code: String,
    val recordId: String? = null,
    val decisionId: String? = null,
    val terminal: Boolean,
)

/** Immutable first-route planning product before resource, pipeline, or backend materialization. */
data class GPUFirstRoutePlan(
    val analysisRecord: GPUDrawAnalysisRecord,
    val analysisDecision: GPUDrawAnalysisDecision,
    val routeDecision: GPURouteDecision,
    val pass: GPUDrawPass,
)

/** Owns analysis-time FillRect route selection and emits native or refused Kanvas planning records. */
class GPUFirstRoutePlanner(
    private val capabilities: GPUCapabilities,
) {
    /**
     * Plans FillRect as native only when first-slice facts are supported.
     *
     * The planner consumes command-owned immutable facts and produces analysis,
     * route, and pass records; it does not lower materials, materialize
     * resources, or submit backend work. Unsupported transforms, clips, layers,
     * target formats, missing capabilities, and invalid coordinate/bounds facts
     * become terminal refusal diagnostics with empty executable pass work.
     */
    fun plan(command: NormalizedDrawCommand.FillRect): GPUFirstRoutePlan {
        require(command.drawKind == GPUDrawKind.FillRect) { "GPUFirstRoutePlanner accepts only FillRect commands" }

        command.refusalCode()?.let { code ->
            return refusedPlan(command = command, code = code)
        }

        val recordId = "analysis.fill_rect.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_rect.solid.rgba8unorm.src_over"
        val renderStep = "rect.fill.coverage"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "native.fill_rect.solid",
            materialKeyHash = "pending.material.solid",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeFillRect(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf(firstRouteCapabilityName),
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.fill_rect.solid",
            resourceDeclarations = emptyList(),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedFillRect(
            commandIdValue = command.commandId.value,
            analysisRecordId = recordId,
            sortKey = command.ordering.paintOrder.toLong(),
            renderStepIdentity = renderStep,
            pipelineKeyHash = pipelineKey,
            boundsHash = command.bounds.stableHash(),
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

    /** Builds refused analysis, route, and pass descriptors without inventing executable fallback work. */
    private fun refusedPlan(command: NormalizedDrawCommand.FillRect, code: String): GPUFirstRoutePlan {
        val recordId = "analysis.fill_rect.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.fill_rect.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "refused.$code",
            materialKeyHash = "none",
            renderStepCandidates = emptyList(),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = listOf(diagnostic),
        )
        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = GPUDrawAnalysisDecision.Refuse(recordId = recordId, diagnostic = diagnostic),
            routeDecision = GPUFirstRouteDecisionBuilder.refused(code = code, stage = "analysis"),
            pass = GPUFirstRoutePassBuilder.refusedFillRect(
                commandIdValue = command.commandId.value,
                targetStateHash = command.targetStateHash(),
                code = code,
            ),
        )
    }

    /** Returns the canonical first-route refusal code, or null when analysis may keep a native candidate. */
    private fun NormalizedDrawCommand.FillRect.refusalCode(): String? =
        coordinateRefusalCode() ?: when {
            transform.type == GPUTransformType.Perspective -> "unsupported.transform.perspective"
            transform.type == GPUTransformType.Singular -> "unsupported.transform.singular"
            transform.type !in acceptedTransformTypes -> "unsupported.transform.class_downgrade"
            clip.kind == GPUClipKind.ComplexStack -> "unsupported.clip.complex_stack"
            clip.kind !in acceptedClipKinds -> "unsupported.clip.analytic_unsupported"
            material.kind != GPUMaterialKind.SolidColor -> "unsupported.material.source_unimplemented"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination || blend.requiresDestinationRead ->
                "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !capabilities.hasFact(firstRouteCapabilityName) -> "unsupported.pipeline.capability_missing"
            else -> null
        }

    /** Returns true only for explicit validity-affecting capability facts in the immutable snapshot. */
    private fun GPUCapabilities.hasFact(name: String): Boolean =
        facts.any { fact ->
            fact.name == name && fact.value == "supported" && fact.affectsValidity
        }

    private companion object {
        /** Required target format for the first native FillRect route. */
        const val firstRouteTargetFormat = "rgba8unorm"

        /** Required capability fact for the first native FillRect route. */
        const val firstRouteCapabilityName = "first_slice.fill_rect.native"

        /** Transform classes supported by the first native FillRect route. */
        val acceptedTransformTypes = setOf(GPUTransformType.Identity, GPUTransformType.Translate)

        /** Clip classes supported by the first native FillRect route. */
        val acceptedClipKinds = setOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)
    }
}

/** Returns a terminal coordinate or bounds refusal code before route acceptance. */
private fun NormalizedDrawCommand.FillRect.coordinateRefusalCode(): String? =
    when {
        transform.hasNonFiniteFacts() -> "unsupported.transform.non_finite"
        rect.hasNaN() || bounds.hasNaN() || clip.bounds.hasNaN() -> "unsupported.bounds.nan"
        rect.hasNonFinite() || bounds.hasNonFinite() || clip.bounds.hasNonFinite() -> "unsupported.bounds.non_finite"
        else -> null
    }

/** Returns true when captured transform payload facts cannot be trusted by analysis. */
private fun GPUTransformFacts.hasNonFiniteFacts(): Boolean =
    !translateX.isFinite() || !translateY.isFinite()

/** Emits stable analysis facts for accepted transform classifications. */
private fun GPUTransformFacts.analysisDiagnostics(
    recordId: String,
): List<GPUAnalysisDiagnostic> =
    when (type) {
        GPUTransformType.Identity -> emptyList()
        GPUTransformType.Translate -> listOf(
            GPUAnalysisDiagnostic(
                code = "transform:translate",
                recordId = recordId,
                terminal = false,
            ),
        )
        GPUTransformType.Perspective,
        GPUTransformType.Singular,
        -> emptyList()
    }

/** Returns a stable textual bounds hash placeholder before pipeline-key implementation lands. */
private fun GPUBounds.stableHash(): String =
    "bounds:$left,$top,$right,$bottom"

/** Returns the accepted simple scissor bounds hash, or null for wide-open clips. */
private fun NormalizedDrawCommand.FillRect.scissorBoundsHash(): String? =
    when (clip.kind) {
        GPUClipKind.DeviceRect -> clip.bounds.stableHash()
        GPUClipKind.WideOpen,
        GPUClipKind.ComplexStack,
        -> null
    }

/** Returns a stable target-state placeholder before pipeline-key implementation lands. */
private fun NormalizedDrawCommand.FillRect.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

/** Returns true when any rectangle coordinate is NaN. */
private fun GPURect.hasNaN(): Boolean =
    left.isNaN() || top.isNaN() || right.isNaN() || bottom.isNaN()

/** Returns true when any rectangle coordinate is infinite or NaN. */
private fun GPURect.hasNonFinite(): Boolean =
    !left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()

/** Returns true when any bounds coordinate is NaN. */
private fun GPUBounds.hasNaN(): Boolean =
    left.isNaN() || top.isNaN() || right.isNaN() || bottom.isNaN()

/** Returns true when any bounds coordinate is infinite or NaN. */
private fun GPUBounds.hasNonFinite(): Boolean =
    !left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()
