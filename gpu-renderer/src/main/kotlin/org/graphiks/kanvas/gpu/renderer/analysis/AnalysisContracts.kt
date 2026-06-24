package org.graphiks.kanvas.gpu.renderer.analysis

import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.commands.GPUBlendKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.commands.GPUClipKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUDrawKind
import org.graphiks.kanvas.gpu.renderer.commands.GPULayerScopeKind
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialKind
import org.graphiks.kanvas.gpu.renderer.commands.GPURect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRect
import org.graphiks.kanvas.gpu.renderer.commands.GPURRectCornerRadii
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

    /**
     * Plans FillRRect as native only when first-expansion facts are supported.
     *
     * This mirrors the FillRect planning boundary while keeping rrect-specific
     * geometry facts in command/analysis/pass evidence. It does not allocate
     * resources, submit backend work, or activate product routing.
     */
    fun plan(command: NormalizedDrawCommand.FillRRect): GPUFirstRoutePlan {
        require(command.drawKind == GPUDrawKind.FillRRect) { "GPUFirstRoutePlanner accepts only FillRRect commands" }

        command.refusalCode()?.let { code ->
            return refusedPlan(command = command, code = code)
        }

        val recordId = "analysis.fill_rrect.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_rrect.solid.rgba8unorm.src_over"
        val renderStep = "rrect.fill.coverage"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "native.fill_rrect.solid",
            materialKeyHash = "pending.material.solid",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.rrect.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeFillRRect(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf(firstRRectRouteCapabilityName),
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.fill_rrect.solid",
            resourceDeclarations = emptyList(),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedFillRRect(
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

    /** Builds refused rrect analysis, route, and pass descriptors without inventing executable fallback work. */
    private fun refusedPlan(command: NormalizedDrawCommand.FillRRect, code: String): GPUFirstRoutePlan {
        val recordId = "analysis.fill_rrect.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.fill_rrect.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRRect",
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
            routeDecision = GPUFirstRouteDecisionBuilder.refused(
                code = code,
                stage = "analysis",
                subject = "FillRRect first expansion route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedFillRRect(
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
            material.kind !in acceptedMaterialKinds -> "unsupported.material.source_unimplemented"
            material is GPUMaterialDescriptor.LinearGradient && material.refusalCode() != null ->
                material.refusalCode()
            material is GPUMaterialDescriptor.LinearGradient &&
                !capabilities.hasFact(firstLinearGradientCapabilityName) ->
                "unsupported.material.linear_gradient_capability_missing"
            material is GPUMaterialDescriptor.RadialGradient && material.refusalCode() != null ->
                material.refusalCode()
            material is GPUMaterialDescriptor.RadialGradient &&
                !capabilities.hasFact(firstRadialGradientCapabilityName) ->
                "unsupported.material.radial_gradient_capability_missing"
            material is GPUMaterialDescriptor.SweepGradient && material.refusalCode() != null ->
                material.refusalCode()
            material is GPUMaterialDescriptor.SweepGradient &&
                !capabilities.hasFact(firstSweepGradientCapabilityName) ->
                "unsupported.material.sweep_gradient_capability_missing"
            clip.kind == GPUClipKind.DeviceRect && !capabilities.hasFact(firstScissorCapabilityName) ->
                "unsupported.clip.scissor_capability_missing"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination || blend.requiresDestinationRead ->
                "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !capabilities.hasFact(firstRouteCapabilityName) -> "unsupported.pipeline.capability_missing"
            else -> null
        }

    /** Returns the canonical first-expansion rrect refusal code, or null when analysis may keep a native candidate. */
    private fun NormalizedDrawCommand.FillRRect.refusalCode(): String? =
        coordinateRefusalCode() ?: when {
            !rrect.hasAcceptedRadii() -> "unsupported.geometry.rrect_radii"
            transform.type == GPUTransformType.Perspective -> "unsupported.transform.perspective"
            transform.type == GPUTransformType.Singular -> "unsupported.transform.singular"
            transform.type == GPUTransformType.Scale -> "unsupported.transform.rrect_scale_unproven"
            transform.type == GPUTransformType.Affine -> "unsupported.transform.rrect_affine_unproven"
            transform.type !in acceptedTransformTypes -> "unsupported.transform.class_downgrade"
            clip.kind == GPUClipKind.ComplexStack -> "unsupported.clip.complex_stack"
            clip.kind !in acceptedClipKinds -> "unsupported.clip.analytic_unsupported"
            clip.kind == GPUClipKind.DeviceRect && !capabilities.hasFact(firstScissorCapabilityName) ->
                "unsupported.clip.scissor_capability_missing"
            material.kind != GPUMaterialKind.SolidColor -> "unsupported.material.source_unimplemented"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination || blend.requiresDestinationRead ->
                "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !capabilities.hasFact(firstRRectRouteCapabilityName) -> "unsupported.pipeline.capability_missing"
            else -> null
        }

    /** Returns true only for explicit validity-affecting capability facts in the immutable snapshot. */
    private fun GPUCapabilities.hasFact(name: String): Boolean =
        facts.any { fact ->
            fact.name == name && fact.value == "supported" && fact.affectsValidity
        }

    /** Returns a terminal gradient refusal code, or null when gradient facts are accepted. */
    private fun GPUMaterialDescriptor.LinearGradient.refusalCode(): String? =
        when {
            !startX.isFinite() || !startY.isFinite() || !endX.isFinite() || !endY.isFinite() ->
                "unsupported.material.gradient_non_finite_coords"
            (endX - startX).let { dx -> !dx.isFinite() } || (endY - startY).let { dy -> !dy.isFinite() } ->
                "unsupported.material.gradient_non_finite_coords"
            !startR.isFinite() || !startG.isFinite() || !startB.isFinite() || !startA.isFinite() ->
                "unsupported.material.gradient_non_finite_color"
            !endR.isFinite() || !endG.isFinite() || !endB.isFinite() || !endA.isFinite() ->
                "unsupported.material.gradient_non_finite_color"
            tileMode !in acceptedGradientTileModes ->
                "unsupported.material.gradient_tile_mode_unsupported"
            else -> null
        }

    /** Returns a terminal radial gradient refusal code, or null when facts are accepted. */
    private fun GPUMaterialDescriptor.RadialGradient.refusalCode(): String? =
        when {
            !centerX.isFinite() || !centerY.isFinite() ->
                "unsupported.material.gradient_non_finite_coords"
            !radius.isFinite() || radius <= 0f ->
                "unsupported.material.gradient_non_finite_radius"
            !startR.isFinite() || !startG.isFinite() || !startB.isFinite() || !startA.isFinite() ->
                "unsupported.material.gradient_non_finite_color"
            !endR.isFinite() || !endG.isFinite() || !endB.isFinite() || !endA.isFinite() ->
                "unsupported.material.gradient_non_finite_color"
            tileMode !in acceptedGradientTileModes ->
                "unsupported.material.gradient_tile_mode_unsupported"
            else -> null
        }

    /** Returns a terminal sweep gradient refusal code, or null when facts are accepted. */
    private fun GPUMaterialDescriptor.SweepGradient.refusalCode(): String? =
        when {
            !centerX.isFinite() || !centerY.isFinite() ->
                "unsupported.material.gradient_non_finite_coords"
            !startAngle.isFinite() || !endAngle.isFinite() ->
                "unsupported.material.gradient_non_finite_angle"
            (endAngle - startAngle).let { sweep -> !sweep.isFinite() || sweep <= 0f } ->
                "unsupported.material.gradient_non_finite_angle"
            !startR.isFinite() || !startG.isFinite() || !startB.isFinite() || !startA.isFinite() ->
                "unsupported.material.gradient_non_finite_color"
            !endR.isFinite() || !endG.isFinite() || !endB.isFinite() || !endA.isFinite() ->
                "unsupported.material.gradient_non_finite_color"
            tileMode !in acceptedGradientTileModes ->
                "unsupported.material.gradient_tile_mode_unsupported"
            else -> null
        }

    private companion object {
        /** Required target format for the first native FillRect route. */
        const val firstRouteTargetFormat = "rgba8unorm"

        /** Required capability fact for the first native FillRect route. */
        const val firstRouteCapabilityName = "first_slice.fill_rect.native"

        /** Required capability fact for the first native FillRRect expansion route. */
        const val firstRRectRouteCapabilityName = "first_slice.fill_rrect.native"

        /** Required capability fact for the linear gradient material route. */
        const val firstLinearGradientCapabilityName = "first_slice.linear_gradient.native"

        /** Required capability fact for the radial gradient material route. */
        const val firstRadialGradientCapabilityName = "first_slice.radial_gradient.native"

        /** Required capability fact for the sweep gradient material route. */
        const val firstSweepGradientCapabilityName = "first_slice.sweep_gradient.native"

        /** Required capability fact for the scissor clip route. */
        const val firstScissorCapabilityName = "first_slice.scissor.native"

        /** Required capability fact for the path fill native route. */
        const val firstPathFillCapabilityName = "first_slice.path_fill.native"

        /** Transform classes supported by the first native FillRect route. */
        val acceptedTransformTypes = setOf(GPUTransformType.Identity, GPUTransformType.Translate)

        /** Clip classes supported by the first native FillRect route. */
        val acceptedClipKinds = setOf(GPUClipKind.WideOpen, GPUClipKind.DeviceRect)

        /** Material kinds supported by the first native FillRect expansion route. */
        val acceptedMaterialKinds = setOf(
            GPUMaterialKind.SolidColor,
            GPUMaterialKind.LinearGradient,
            GPUMaterialKind.RadialGradient,
            GPUMaterialKind.SweepGradient,
        )

        /** Gradient tile modes accepted by the first expansion route. */
        val acceptedGradientTileModes = setOf("clamp")
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

/** Returns a terminal coordinate, radii, or bounds refusal code before rrect route acceptance. */
private fun NormalizedDrawCommand.FillRRect.coordinateRefusalCode(): String? =
    when {
        transform.hasNonFiniteFacts() -> "unsupported.transform.non_finite"
        rrect.rect.hasNaN() || bounds.hasNaN() || clip.bounds.hasNaN() -> "unsupported.bounds.nan"
        rrect.rect.hasNonFinite() || bounds.hasNonFinite() || clip.bounds.hasNonFinite() ->
            "unsupported.bounds.non_finite"
        rrect.hasNaN() || rrect.hasNonFinite() -> "unsupported.geometry.rrect_radii"
        else -> null
    }

/** Returns true when captured transform payload facts cannot be trusted by analysis. */
private fun GPUTransformFacts.hasNonFiniteFacts(): Boolean =
    !translateX.isFinite() || !translateY.isFinite() ||
        !scaleX.isFinite() || !scaleY.isFinite() ||
        !skewX.isFinite() || !skewY.isFinite()

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
        GPUTransformType.Scale,
        GPUTransformType.Affine,
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

/** Returns the accepted simple scissor bounds hash, or null for wide-open rrect clips. */
private fun NormalizedDrawCommand.FillRRect.scissorBoundsHash(): String? =
    when (clip.kind) {
        GPUClipKind.DeviceRect -> clip.bounds.stableHash()
        GPUClipKind.WideOpen,
        GPUClipKind.ComplexStack,
        -> null
    }

/** Returns a stable target-state placeholder before pipeline-key implementation lands. */
private fun NormalizedDrawCommand.FillRect.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

/** Returns a stable target-state placeholder before pipeline-key implementation lands. */
private fun NormalizedDrawCommand.FillRRect.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

/** Returns true when any rectangle coordinate is NaN. */
private fun GPURect.hasNaN(): Boolean =
    left.isNaN() || top.isNaN() || right.isNaN() || bottom.isNaN()

/** Returns true when any rectangle coordinate is infinite or NaN. */
private fun GPURect.hasNonFinite(): Boolean =
    !left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()

/** Returns true when any rounded rectangle coordinate or radius is NaN. */
private fun GPURRect.hasNaN(): Boolean =
    topLeft.hasNaN() || topRight.hasNaN() || bottomRight.hasNaN() || bottomLeft.hasNaN()

/** Returns true when any rounded rectangle coordinate or radius is infinite or NaN. */
private fun GPURRect.hasNonFinite(): Boolean =
    topLeft.hasNonFinite() || topRight.hasNonFinite() ||
        bottomRight.hasNonFinite() || bottomLeft.hasNonFinite()

/** Returns true when either radius component is NaN. */
private fun GPURRectCornerRadii.hasNaN(): Boolean =
    x.isNaN() || y.isNaN()

/** Returns true when either radius component is infinite or NaN. */
private fun GPURRectCornerRadii.hasNonFinite(): Boolean =
    !x.isFinite() || !y.isFinite()

/** Returns true when both radius components are finite and positive. */
private fun GPURRectCornerRadii.hasPositiveFiniteRadii(): Boolean =
    x.isFinite() && y.isFinite() && x > 0f && y > 0f

/** Returns true when rrect radii are finite, positive, and already normalized for the rect extent. */
private fun GPURRect.hasAcceptedRadii(): Boolean {
    val width = rect.right - rect.left
    val height = rect.bottom - rect.top
    if (width <= 0f || height <= 0f) return false
    if (!topLeft.hasPositiveFiniteRadii() || !topRight.hasPositiveFiniteRadii()) return false
    if (!bottomRight.hasPositiveFiniteRadii() || !bottomLeft.hasPositiveFiniteRadii()) return false
    return topLeft.x + topRight.x <= width &&
        bottomLeft.x + bottomRight.x <= width &&
        topLeft.y + bottomLeft.y <= height &&
        topRight.y + bottomRight.y <= height
}

/** Emits stable accepted rrect geometry facts for analysis dumps. */
private fun GPURRect.analysisDiagnostics(recordId: String): List<GPUAnalysisDiagnostic> =
    listOf(
        GPUAnalysisDiagnostic(
            code = "geometry:rrect.corner_radii=" +
                "tl(${topLeft.x},${topLeft.y});" +
                "tr(${topRight.x},${topRight.y});" +
                "br(${bottomRight.x},${bottomRight.y});" +
                "bl(${bottomLeft.x},${bottomLeft.y})",
            recordId = recordId,
            terminal = false,
        ),
    )

/** Returns true when any bounds coordinate is NaN. */
private fun GPUBounds.hasNaN(): Boolean =
    left.isNaN() || top.isNaN() || right.isNaN() || bottom.isNaN()

/** Returns true when any bounds coordinate is infinite or NaN. */
private fun GPUBounds.hasNonFinite(): Boolean =
    !left.isFinite() || !top.isFinite() || !right.isFinite() || !bottom.isFinite()
