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
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.filters.GPUSimpleFilterRenderNodePlanner
import org.graphiks.kanvas.gpu.renderer.geometry.GPUShapeDescriptor
import org.graphiks.kanvas.gpu.renderer.geometry.GPUPathDescriptor
import org.graphiks.kanvas.gpu.renderer.geometry.GPUStrokeDescriptor
import org.graphiks.kanvas.gpu.renderer.geometry.strokeRefusalCode
import org.graphiks.kanvas.gpu.renderer.geometry.strokePathRefusalCode
import org.graphiks.kanvas.gpu.renderer.geometry.refusalCode
import org.graphiks.kanvas.gpu.renderer.filters.GPUSimpleFilterRenderNodeRequest
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodePlan
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterNodeRoute
import org.graphiks.kanvas.gpu.renderer.filters.GPUFilterRenderNodePlan
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImagePixelsDescriptor
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageSamplingPlan
import org.graphiks.kanvas.gpu.renderer.images.GPUDecodedImageShaderPreparedPlanner
import org.graphiks.kanvas.gpu.renderer.images.GPUImageDecodePlanner
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPass
import org.graphiks.kanvas.gpu.renderer.passes.GPUFirstRoutePassBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPUFirstRouteDecisionBuilder
import org.graphiks.kanvas.gpu.renderer.routing.GPURouteDecision
import org.graphiks.kanvas.gpu.renderer.stroke.DashVertexExpansion

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

        command.maskFilter?.let {
            return blurMaskFillRectRouteDecision(command)
        }

        val isLinearGradient = command.material is GPUMaterialDescriptor.LinearGradient
        val recordId = "analysis.fill_rect.${command.commandId.value}"
        val pipelineKey: String
        val renderStep: String
        val routeLabel: String
        val materialKeyHash: String
        val capabilityName: String

        if (isLinearGradient) {
            pipelineKey = "pending.pipeline.fill_rect.linear_gradient.rgba8unorm.src_over"
            renderStep = linearGradientRenderStep
            routeLabel = "native.fill_rect.linear_gradient"
            materialKeyHash = "pending.material.linear_gradient"
            capabilityName = firstLinearGradientCapabilityName
        } else {
            pipelineKey = "pending.pipeline.fill_rect.solid.rgba8unorm.src_over"
            renderStep = "rect.fill.coverage"
            routeLabel = "native.fill_rect.solid"
            materialKeyHash = "pending.material.solid"
            capabilityName = firstRouteCapabilityName
        }

        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = routeLabel,
            materialKeyHash = materialKeyHash,
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision: GPURouteDecision.Native = if (isLinearGradient) {
            GPUFirstRouteDecisionBuilder.nativeLinearGradientRect(
                commandIdValue = command.commandId.value,
                pipelinePreimageHash = pipelineKey,
                renderStepIdentity = renderStep,
                requirements = listOf(capabilityName),
            )
        } else {
            GPUFirstRouteDecisionBuilder.nativeFillRect(
                commandIdValue = command.commandId.value,
                pipelinePreimageHash = pipelineKey,
                renderStepIdentity = renderStep,
                requirements = listOf(capabilityName),
            )
        }
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = routeLabel,
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

    /** Builds a blur-mask FillRect prepared GPU route that signals offscreen blur processing. */
    private fun blurMaskFillRectRouteDecision(command: NormalizedDrawCommand.FillRect): GPUFirstRoutePlan {
        val recordId = "analysis.fill_rect.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_rect.blur_mask.rgba8unorm.src_over"
        val renderStep = blurMaskFilterRenderStep
        val consumerKind = "blur-mask.sample.rect-fill"
        val mf = requireNotNull(command.maskFilter)
        val blurDesc = when (mf) {
            is NormalizedMaskFilter.Blur -> "blur:${mf.style.name.lowercase()}_sigma=${mf.sigma}"
        }
        val artifactKey = "blur-mask.rect-fill.rect${command.commandId.value}.${blurDesc}"
        val invalidationFacts = listOf("rect-bounds", "blur-sigma", "blur-style", "material-kind", "transform-class")
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.fill_rect.blur_mask",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedFillPath(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.fill_rect.blur_mask",
            resourceDeclarations = listOf("blur_mask_artifacts:blur_rect.${command.commandId.value}"),
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

        command.maskFilter?.let {
            return blurMaskFillRRectRouteDecision(command)
        }

        val isLinearGradient = command.material is GPUMaterialDescriptor.LinearGradient
        val isSolid = command.material.kind == GPUMaterialKind.SolidColor
        if (!isSolid && !isLinearGradient) {
            return refusedPlan(command = command, code = "unsupported.material.source_unimplemented")
        }

        val recordId = "analysis.fill_rrect.${command.commandId.value}"
        val pipelineKey: String
        val renderStep: String
        val routeLabel: String
        val materialKeyHash: String
        val capabilityName: String

        if (isLinearGradient) {
            pipelineKey = "pending.pipeline.fill_rrect.linear_gradient.rgba8unorm.src_over"
            renderStep = linearGradientRenderStep
            routeLabel = "native.fill_rrect.linear_gradient"
            materialKeyHash = "pending.material.linear_gradient"
            capabilityName = firstLinearGradientCapabilityName
        } else {
            pipelineKey = "pending.pipeline.fill_rrect.solid.rgba8unorm.src_over"
            renderStep = "rrect.fill.coverage"
            routeLabel = "native.fill_rrect.solid"
            materialKeyHash = "pending.material.solid"
            capabilityName = firstRRectRouteCapabilityName
        }

        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = routeLabel,
            materialKeyHash = materialKeyHash,
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.rrect.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision: GPURouteDecision.Native = if (isLinearGradient) {
            GPUFirstRouteDecisionBuilder.nativeLinearGradientRRect(
                commandIdValue = command.commandId.value,
                pipelinePreimageHash = pipelineKey,
                renderStepIdentity = renderStep,
                requirements = listOf(capabilityName),
            )
        } else {
            GPUFirstRouteDecisionBuilder.nativeFillRRect(
                commandIdValue = command.commandId.value,
                pipelinePreimageHash = pipelineKey,
                renderStepIdentity = renderStep,
                requirements = listOf(capabilityName),
            )
        }
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = routeLabel,
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

    /** Builds a blur-mask FillRRect prepared GPU route that signals offscreen blur processing. */
    private fun blurMaskFillRRectRouteDecision(command: NormalizedDrawCommand.FillRRect): GPUFirstRoutePlan {
        val recordId = "analysis.fill_rrect.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_rrect.blur_mask.rgba8unorm.src_over"
        val renderStep = blurMaskFilterRenderStep
        val consumerKind = "blur-mask.sample.rrect-fill"
        val mf = requireNotNull(command.maskFilter)
        val blurDesc = when (mf) {
            is NormalizedMaskFilter.Blur -> "blur:${mf.style.name.lowercase()}_sigma=${mf.sigma}"
        }
        val artifactKey = "blur-mask.rrect-fill.rrect${command.commandId.value}.${blurDesc}"
        val invalidationFacts = listOf("rrect-geometry", "blur-sigma", "blur-style", "material-kind", "transform-class")
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillRRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.fill_rrect.blur_mask",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.rrect.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedFillPath(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.fill_rrect.blur_mask",
            resourceDeclarations = listOf("blur_mask_artifacts:blur_rrect.${command.commandId.value}"),
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

    /**
     * Plans FillPath as CPU-prepared GPU route by default, or native stencil-cover
     * when capability and product facts promote it.
     *
     * The planner consumes command-owned immutable facts and produces analysis,
     * route, and pass records; it does not lower materials, materialize resources,
     * or submit backend work. Unsupported transforms, clips, layers, materials,
     * empty path data, missing capabilities, and invalid bounds facts become
     * terminal refusal diagnostics with empty executable pass work.
     */
    fun plan(command: NormalizedDrawCommand.FillPath): GPUFirstRoutePlan {
        require(command.drawKind == GPUDrawKind.FillPath) { "GPUFirstRoutePlanner accepts only FillPath commands" }

        command.refusalCode()?.let { code ->
            return refusedPlan(command = command, code = code)
        }

        return when {
            command.maskFilter != null -> blurMaskFillPathRouteDecision(command)
            command.stroke -> preparedStrokeRouteDecision(command)
            capabilities.hasFact(firstStencilCoverCapabilityName) ->
                nativeFillPathRouteDecision(command)
            else ->
                preparedFillPathRouteDecision(command)
        }
    }

    /** Builds a prepared FillStroke CPUPreparedGPU route and pass for stroked paths. */
    private fun preparedStrokeRouteDecision(command: NormalizedDrawCommand.FillPath): GPUFirstRoutePlan {
        val recordId = "analysis.fill_path.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_stroke.tessellated.rgba8unorm.src_over"
        val renderStep = "path.stroke.tessellated"
        val consumerKind = "stroke-strip.render-step"

        val dashResult = command.dashIntervals?.let { intervals ->
            DashVertexExpansion.expandVertices(
                tessellatedVertices = command.tessellatedVertices,
                dashIntervals = intervals,
                dashPhase = command.dashPhase,
                strokeWidth = command.strokeWidth,
            )
        }
        val expandedEdgeCount = dashResult?.edgeCount ?: command.edgeCount
        val dashSuffix = command.dashIntervals?.let { "d${it.joinToString("_")}." } ?: ""
        val artifactKey = "prepared.stroke.${command.pathKey.sanitizeForAnalysisKey()}.w${command.strokeWidth}.${command.strokeCap.lowercase()}.${command.strokeJoin.lowercase()}.${dashSuffix}e$expandedEdgeCount"
        val invalidationFacts = listOf("path-content-hash", "stroke-width", "cap", "join", "miter", "transform-class", "bounds-proof", "dash-intervals")
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillPath",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.path_stroke.tessellated",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.pathFactsDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedFillStroke(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.path_stroke.tessellated",
            resourceDeclarations = listOf("tessellated_vertices:path_stroke.${command.commandId.value}"),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedFillPath(
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

    /** Builds a prepared FillPath CPUPreparedGPU route and pass. */
    private fun preparedFillPathRouteDecision(command: NormalizedDrawCommand.FillPath): GPUFirstRoutePlan {
        val recordId = "analysis.fill_path.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_path.tessellated.rgba8unorm.src_over"
        val renderStep = "path.fill.coverage_mask"
        val consumerKind = "coverage-mask.sample.path-fill"
        val artifactKey = "prepared.path-fill.${command.pathKey.sanitizeForAnalysisKey()}.${command.pathDescriptor.fillRule.lowercase()}.${command.pathDescriptor.transformClass}.edges${command.edgeCount}"
        val invalidationFacts = listOf("path-content-hash", "fill-rule", "transform-class", "bounds-proof", "tessellation-hash")
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillPath",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.path_fill.tessellated",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.pathFactsDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedFillPath(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.path_fill.tessellated",
            resourceDeclarations = listOf("tessellated_vertices:path_fill.${command.commandId.value}"),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedFillPath(
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

    /** Builds a blur-mask FillPath prepared GPU route that signals offscreen blur processing. */
    private fun blurMaskFillPathRouteDecision(command: NormalizedDrawCommand.FillPath): GPUFirstRoutePlan {
        val recordId = "analysis.fill_path.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_path.blur_mask.rgba8unorm.src_over"
        val renderStep = blurMaskFilterRenderStep
        val consumerKind = "blur-mask.sample.path-fill"
        val mf = requireNotNull(command.maskFilter)
        val blurDesc = when (mf) {
            is NormalizedMaskFilter.Blur -> "blur:${mf.style.name.lowercase()}_sigma=${mf.sigma}"
        }
        val artifactKey = "blur-mask.path-fill.${command.pathKey.sanitizeForAnalysisKey()}.${blurDesc}"
        val invalidationFacts = listOf("path-content-hash", "blur-sigma", "blur-style", "fill-rule", "transform-class", "bounds-proof", "tessellation-hash")
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillPath",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.path_fill.blur_mask",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.pathFactsDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedFillPath(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.path_fill.blur_mask",
            resourceDeclarations = listOf("blur_mask_artifacts:blur_path.${command.commandId.value}"),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedFillPath(
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

    /** Builds a native FillPath stencil-cover GPU route when capability and product facts promote it. */
    private fun nativeFillPathRouteDecision(command: NormalizedDrawCommand.FillPath): GPUFirstRoutePlan {
        val recordId = "analysis.fill_path.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.fill_path.stencil_cover.rgba8unorm.src_over"
        val renderStep = "path.fill.stencil_cover"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillPath",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "native.path_fill.stencil_cover",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId) +
                command.pathFactsDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeFillPath(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf(firstStencilCoverCapabilityName),
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.path_fill.stencil_cover",
            resourceDeclarations = emptyList(),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedFillPath(
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
     * Plans DrawImageRect as CPU-prepared GPU route by default for decoded
     * image upload, or native when bitmap WGSL capability promotes it.
     *
     * The planner consumes command-owned immutable facts and produces analysis,
     * route, and pass records; it does not decode images, upload textures,
     * create sampler resources, or submit backend work. Unsupported transforms,
     * clips, layers, materials, missing codecs, invalid pixel descriptors,
     * and missing capabilities become terminal refusal diagnostics.
     */
    fun plan(command: NormalizedDrawCommand.DrawImageRect): GPUFirstRoutePlan {
        require(command.drawKind == GPUDrawKind.DrawImageRect) {
            "GPUFirstRoutePlanner accepts only DrawImageRect commands"
        }

        command.refusalCode()?.let { code ->
            return refusedPlan(command = command, code = code)
        }

        return when {
            capabilities.hasFact(firstImageDrawNativeCapabilityName) ->
                nativeDrawImageRectRouteDecision(command)
            else ->
                preparedDrawImageRectRouteDecision(command)
        }
    }

    /**
     * Builds a prepared DrawImageRect CPUPreparedGPU route that uploads
     * decoded pixels before the sampler consumes them.
     */
    private fun preparedDrawImageRectRouteDecision(
        command: NormalizedDrawCommand.DrawImageRect,
    ): GPUFirstRoutePlan {
        val recordId = "analysis.draw_image_rect.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.draw_image_rect.decoded_pixels.rgba8unorm.src_over"
        val renderStep = imageDrawRenderStep
        val consumerKind = "sampled-image.draw_image_rect"
        val artifactKey = "prepared.draw_image_rect.${command.imageSourceId.sanitizeForAnalysisKey()}.${command.pixelsWidth}x${command.pixelsHeight}.${command.pixelsFormat.lowercase()}"
        val invalidationFacts = listOf("image-source-id", "pixel-content-hash", "generation", "pixel-format", "alpha-type", "color-profile")

        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawImageRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.draw_image_rect.decoded_pixels",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedDrawImageRect(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.draw_image_rect.decoded_pixels",
            resourceDeclarations = listOf("decoded_pixels:draw_image_rect.${command.commandId.value}"),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedDrawImageRect(
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
     * Builds a native DrawImageRect GPU route (future, when bitmap WGSL
     * capability promotes it past CPU-prepared).
     */
    private fun nativeDrawImageRectRouteDecision(
        command: NormalizedDrawCommand.DrawImageRect,
    ): GPUFirstRoutePlan {
        val recordId = "analysis.draw_image_rect.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.draw_image_rect.native_bitmap.rgba8unorm.src_over"
        val renderStep = "image.draw.bitmap_shader"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawImageRect",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "native.draw_image_rect.decoded_pixels",
            materialKeyHash = "pending.material.${command.material.kind.name.lowercase()}",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeDrawImageRect(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf(firstImageDrawNativeCapabilityName),
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.draw_image_rect.decoded_pixels",
            resourceDeclarations = emptyList(),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedDrawImageRect(
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

    /** Builds refused analysis, route, and pass descriptors for DrawImageRect. */
    private fun refusedPlan(command: NormalizedDrawCommand.DrawImageRect, code: String): GPUFirstRoutePlan {
        val recordId = "analysis.draw_image_rect.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.draw_image_rect.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawImageRect",
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
                subject = "DrawImageRect first expansion route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedDrawImageRect(
                commandIdValue = command.commandId.value,
                targetStateHash = command.targetStateHash(),
                code = code,
            ),
        )
    }

    /** Returns the canonical DrawImageRect refusal code, or null when analysis may keep a candidate. */
    private fun NormalizedDrawCommand.DrawImageRect.refusalCode(): String? =
        coordinateRefusalCode() ?: when {
            stroke -> "unsupported.stroke.unimplemented"
            imageSourceId.isBlank() -> "unsupported.image.source_id_empty"
            src.left.isNaN() || src.top.isNaN() || src.right.isNaN() || src.bottom.isNaN() ->
                "unsupported.image.src_rect_nan"
            dst.left.isNaN() || dst.top.isNaN() || dst.right.isNaN() || dst.bottom.isNaN() ->
                "unsupported.image.dst_rect_nan"
            !src.left.isFinite() || !src.top.isFinite() || !src.right.isFinite() || !src.bottom.isFinite() ->
                "unsupported.image.src_rect"
            !dst.left.isFinite() || !dst.top.isFinite() || !dst.right.isFinite() || !dst.bottom.isFinite() ->
                "unsupported.image.dst_rect"
            pixelsWidth <= 0 || pixelsHeight <= 0 ->
                "unsupported.image.pixels_descriptor_invalid"
            material.kind != GPUMaterialKind.ImageDraw -> "unsupported.material.source_unimplemented"
            transform.type == GPUTransformType.Perspective -> "unsupported.transform.perspective"
            transform.type == GPUTransformType.Singular -> "unsupported.transform.singular"
            transform.type !in acceptedDrawImageRectTransformTypes -> "unsupported.transform.class_downgrade"
            clip.kind == GPUClipKind.ComplexStack -> "unsupported.clip.complex_stack"
            clip.kind !in acceptedClipKinds -> "unsupported.clip.analytic_unsupported"
            clip.kind == GPUClipKind.DeviceRect && !capabilities.hasFact(firstScissorCapabilityName) ->
                "unsupported.clip.scissor_capability_missing"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination || blend.requiresDestinationRead ->
                "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            else -> null
        }

    /**
     * Plans DrawLayer as CPU-prepared GPU route by default for offscreen
     * target composite, or native when saveLayer isolation capability
     * promotes it.
     *
     * The planner consumes command-owned immutable facts and produces analysis,
     * route, and pass records; it does not allocate offscreen targets, render
     * children, or submit backend work. Unsupported transforms, clips, materials,
     * save/restore state, target format, and missing capabilities become
     * terminal refusal diagnostics with empty executable pass work.
     */
    fun plan(command: NormalizedDrawCommand.DrawLayer): GPUFirstRoutePlan {
        require(command.drawKind == GPUDrawKind.DrawLayer) { "GPUFirstRoutePlanner accepts only DrawLayer commands" }

        command.refusalCode()?.let { code ->
            return refusedPlan(command = command, code = code)
        }

        return when {
            capabilities.hasFact(firstDrawLayerNativeCapabilityName) ->
                nativeDrawLayerRouteDecision(command)
            else ->
                preparedDrawLayerRouteDecision(command)
        }
    }

    /** Builds a prepared DrawLayer CPUPreparedGPU route with filtered-compositor artifact. */
    private fun preparedDrawLayerRouteDecision(command: NormalizedDrawCommand.DrawLayer): GPUFirstRoutePlan {
        val recordId = "analysis.draw_layer.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.draw_layer.composite.rgba8unorm.src_over"
        val renderStep = drawLayerRenderStep
        val consumerKind = "composite-layer.draw_layer"
        val artifactKey = "prepared.draw_layer.${command.scopeId.sanitizeForAnalysisKey()}.children${command.childCommandIds.size}.filter${command.sourceFilterCount}.blend${command.restoreBlendMode.lowercase()}"
        val invalidationFacts = listOf("scope-id", "child-commands", "filter-count", "restore-blend", "bounds-proof", "backdrop")


        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawLayer",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "prepared.draw_layer.composite",
            materialKeyHash = "pending.material.draw_layer",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.preparedDrawLayer(
            commandIdValue = command.commandId.value,
            artifactKey = artifactKey,
            consumerKind = consumerKind,
            invalidationFacts = invalidationFacts,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "prepared.draw_layer.composite",
            resourceDeclarations = listOf("composite_plan:draw_layer.${command.commandId.value}"),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedDrawLayer(
            commandIdValue = command.commandId.value,
            analysisRecordId = recordId,
            sortKey = command.ordering.paintOrder.toLong(),
            renderStepIdentity = renderStep,
            pipelineKeyHash = pipelineKey,
            boundsHash = command.bounds.stableHash(),
            scissorBoundsHash = command.scissorBoundsHash(),
            originalPaintOrder = command.ordering.paintOrder,
            targetStateHash = command.targetStateHash(),
            layerScopeId = command.scopeId,
        )

        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = analysisDecision,
            routeDecision = routeDecision,
            pass = pass,
        )
    }

    /** Builds a native DrawLayer isolated-target GPU route when capability promotes it. */
    private fun nativeDrawLayerRouteDecision(command: NormalizedDrawCommand.DrawLayer): GPUFirstRoutePlan {
        val recordId = "analysis.draw_layer.${command.commandId.value}"
        val pipelineKey = "pending.pipeline.draw_layer.native_isolation.rgba8unorm.src_over"
        val renderStep = "layer.isolated_target"
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawLayer",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = "native.draw_layer.isolated_target",
            materialKeyHash = "pending.material.draw_layer",
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeDrawLayer(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = listOf(firstDrawLayerNativeCapabilityName),
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = "native.draw_layer.isolated_target",
            resourceDeclarations = emptyList(),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedDrawLayer(
            commandIdValue = command.commandId.value,
            analysisRecordId = recordId,
            sortKey = command.ordering.paintOrder.toLong(),
            renderStepIdentity = renderStep,
            pipelineKeyHash = pipelineKey,
            boundsHash = command.bounds.stableHash(),
            scissorBoundsHash = command.scissorBoundsHash(),
            originalPaintOrder = command.ordering.paintOrder,
            targetStateHash = command.targetStateHash(),
            layerScopeId = command.scopeId,
        )

        return GPUFirstRoutePlan(
            analysisRecord = analysisRecord,
            analysisDecision = analysisDecision,
            routeDecision = routeDecision,
            pass = pass,
        )
    }

    /** Builds refused analysis, route, and pass descriptors for DrawLayer. */
    private fun refusedPlan(command: NormalizedDrawCommand.DrawLayer, code: String): GPUFirstRoutePlan {
        val recordId = "analysis.draw_layer.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.draw_layer.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "DrawLayer",
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
                subject = "DrawLayer first expansion route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedDrawLayer(
                commandIdValue = command.commandId.value,
                targetStateHash = command.targetStateHash(),
                code = code,
            ),
        )
    }

    /** Returns the canonical DrawLayer refusal code, or null when analysis may keep a candidate. */
    private fun NormalizedDrawCommand.DrawLayer.refusalCode(): String? =
        coordinateRefusalCode() ?: when {
            stroke -> "unsupported.stroke.unimplemented"
            scopeId.isBlank() -> "unsupported.layer.scope_id_empty"
            bounds.hasNonFinite() -> "unsupported.bounds.non_finite"
            !bounds.left.isFinite() || !bounds.top.isFinite() || !bounds.right.isFinite() || !bounds.bottom.isFinite() ->
                "unsupported.layer.bounds_unbounded"
            (bounds.right - bounds.left) <= 0f || (bounds.bottom - bounds.top) <= 0f ->
                "unsupported.layer.bounds_invalid"
            transform.type == GPUTransformType.Perspective -> "unsupported.transform.perspective"
            transform.type == GPUTransformType.Singular -> "unsupported.transform.singular"
            transform.type !in acceptedDrawLayerTransformTypes -> "unsupported.transform.class_downgrade"
            clip.kind == GPUClipKind.ComplexStack -> "unsupported.clip.complex_stack"
            clip.kind !in acceptedClipKinds -> "unsupported.clip.analytic_unsupported"
            clip.kind == GPUClipKind.DeviceRect && !capabilities.hasFact(firstScissorCapabilityName) ->
                "unsupported.clip.scissor_capability_missing"
            material.kind !in acceptedMaterialKinds -> "unsupported.material.source_unimplemented"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            backdropRequired -> "unsupported.layer.backdrop_filter"
            initWithPrevious -> "unsupported.layer.init_previous_unaccepted"
            sourceFilterCount > 0 -> "unsupported.layer.filter_chain"
            !restoreBlendMode.equals("srcOver", ignoreCase = true) -> "unsupported.layer.restore_blend"
            cpuFallbackRequested -> "unsupported.layer.cpu_fallback_forbidden"
            preserveLCDText -> "unsupported.layer.preserve_lcd_text"
            f16Requested -> "unsupported.layer.f16_unavailable"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !capabilities.hasFact(firstDrawLayerCapabilityName) -> "unsupported.pipeline.capability_missing"
            else -> null
        }

    /** Returns the accepted simple scissor bounds hash for DrawLayer, or null for wide-open clips. */
    private fun NormalizedDrawCommand.DrawLayer.scissorBoundsHash(): String? =
        when (clip.kind) {
            GPUClipKind.DeviceRect -> clip.bounds.stableHash()
            GPUClipKind.WideOpen,
            GPUClipKind.ComplexStack,
            -> null
        }

    /** Returns a stable target-state hash for DrawLayer. */
    private fun NormalizedDrawCommand.DrawLayer.targetStateHash(): String =
        "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

    /** Returns a terminal coordinate or bounds refusal code for DrawLayer before route acceptance. */
    private fun NormalizedDrawCommand.DrawLayer.coordinateRefusalCode(): String? =
        when {
            transform.hasNonFiniteFacts() -> "unsupported.transform.non_finite"
            bounds.hasNaN() || clip.bounds.hasNaN() -> "unsupported.bounds.nan"
            bounds.hasNonFinite() || clip.bounds.hasNonFinite() -> "unsupported.bounds.non_finite"
            else -> null
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

    /** Builds refused FillPath analysis, route, and pass descriptors. */
    private fun refusedPlan(command: NormalizedDrawCommand.FillPath, code: String): GPUFirstRoutePlan {
        val recordId = "analysis.fill_path.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.fill_path.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "FillPath",
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
                subject = "FillPath route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedFillPath(
                commandIdValue = command.commandId.value,
                targetStateHash = command.targetStateHash(),
                code = code,
            ),
        )
    }

    /** Returns the canonical first-route refusal code, or null when analysis may keep a native candidate. */
    private fun NormalizedDrawCommand.FillRect.refusalCode(): String? =
        coordinateRefusalCode() ?: maskFilter?.let { mf ->
            when (mf) {
                is NormalizedMaskFilter.Blur -> mf.refusalCode()
            }
        } ?: when {
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
        coordinateRefusalCode() ?: maskFilter?.let { mf ->
            when (mf) {
                is NormalizedMaskFilter.Blur -> mf.refusalCode()
            }
        } ?: when {
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
            material.kind !in acceptedMaterialKinds -> "unsupported.material.source_unimplemented"
            material is GPUMaterialDescriptor.LinearGradient && material.refusalCode() != null ->
                material.refusalCode()
            material is GPUMaterialDescriptor.LinearGradient &&
                !capabilities.hasFact(firstLinearGradientCapabilityName) ->
                "unsupported.material.linear_gradient_capability_missing"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination || blend.requiresDestinationRead ->
                "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !capabilities.hasFact(firstRRectRouteCapabilityName) -> "unsupported.pipeline.capability_missing"
            else -> null
        }

    /** Returns the canonical FillPath refusal code, or null when analysis may keep a candidate. */
    private fun NormalizedDrawCommand.FillPath.refusalCode(): String? =
        coordinateRefusalCode() ?: maskFilter?.let { mf ->
            when (mf) {
                is NormalizedMaskFilter.Blur -> mf.refusalCode()
            }
        } ?: when {
            stroke -> {
                val aaMode = if (antiAlias) "coverage-aa" else "none"
                val shapeDesc = GPUShapeDescriptor(
                    shapeKind = "path-stroke",
                    boundsLabel = bounds.stableHash(),
                    antiAliasMode = aaMode,
                    provenance = "gpu-renderer",
                )
                val pathDesc = GPUPathDescriptor(
                    pathKey = pathKey,
                    verbCount = pathDescriptor.verbCount,
                    pointCount = pathDescriptor.pointCount,
                    fillRule = pathDescriptor.fillRule,
                    inverseFill = pathDescriptor.inverseFill,
                    finiteProof = pathDescriptor.finiteProof,
                    volatility = pathDescriptor.volatility,
                    transformClass = pathDescriptor.transformClass,
                    edgeCount = pathDescriptor.edgeCount,
                )
                val strokeDesc = GPUStrokeDescriptor(
                    width = strokeWidth,
                    cap = strokeCap.replaceFirstChar { it.uppercaseChar() },
                    join = strokeJoin.replaceFirstChar { it.uppercaseChar() },
                    miter = 4f,
                    dashOrPathEffectRef = dashIntervals?.let { "dash:${it.joinToString(",")}" },
                    transformClass = transform.type.name.lowercase(),
                    finiteWidth = strokeWidth > 0f && strokeWidth.isFinite(),
                    hairline = strokeWidth <= 0f,
                    edgeCount = edgeCount,
                )
                shapeDesc.strokeRefusalCode()
                    ?: pathDesc.strokePathRefusalCode()
                    ?: strokeDesc.refusalCode(maxEdges = 128)
            }
            pathDescriptor.edgeCount < 0 -> "unsupported.geometry.path_invalid_edges"
            transform.type == GPUTransformType.Perspective -> "unsupported.transform.perspective"
            transform.type == GPUTransformType.Singular -> "unsupported.transform.singular"
            transform.type !in acceptedFillPathTransformTypes -> "unsupported.transform.class_downgrade"
            clip.kind == GPUClipKind.ComplexStack -> "unsupported.clip.complex_stack"
            clip.kind !in acceptedClipKinds -> "unsupported.clip.analytic_unsupported"
            clip.kind == GPUClipKind.DeviceRect && !capabilities.hasFact(firstScissorCapabilityName) ->
                "unsupported.clip.scissor_capability_missing"
            material.kind !in acceptedFillPathMaterialKinds -> "unsupported.material.source_unimplemented"
            material is GPUMaterialDescriptor.LinearGradient && material.refusalCode() != null ->
                material.refusalCode()
            material is GPUMaterialDescriptor.LinearGradient &&
                !capabilities.hasFact(firstLinearGradientCapabilityName) ->
                "unsupported.material.linear_gradient_capability_missing"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination || blend.requiresDestinationRead ->
                "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !capabilities.hasFact(firstPathFillCapabilityName) -> "unsupported.pipeline.capability_missing"
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

    /**
     * Plans ApplyFilter as a native GPU filter render node route.
     *
     * The planner consumes command-owned immutable filter facts and produces
     * analysis, route, and pass records. Only single-node ColorFilter or
     * GaussianBlur DAGs with validated intermediate ownership, no
     * read-write aliasing, and supported capabilities are accepted.
     * Unsupported node kinds, multi-node DAGs, unbounded filters, missing
     * capabilities, and invalid bounds become terminal refusal diagnostics.
     */
    fun plan(command: NormalizedDrawCommand.ApplyFilter): GPUFirstRoutePlan {
        require(command.drawKind == GPUDrawKind.ApplyFilter) { "GPUFirstRoutePlanner accepts only ApplyFilter commands" }

        val refusalCode = command.refusalCode()
        if (refusalCode != null) {
            return refusedFilterPlan(command = command, code = refusalCode)
        }

        return nativeApplyFilterRouteDecision(command)
    }

    /** Builds a native ApplyFilter GPU render node route. */
    private fun nativeApplyFilterRouteDecision(
        command: NormalizedDrawCommand.ApplyFilter,
    ): GPUFirstRoutePlan {
        val filterRequest = command.toSimpleFilterRequest()
        val gatePlan = GPUSimpleFilterRenderNodePlanner().plan(filterRequest)

        val terminalDiagnostic = gatePlan.diagnostics.singleOrNull { it.terminal }
        if (terminalDiagnostic != null) {
            return refusedFilterPlan(command = command, code = terminalDiagnostic.code)
        }

        val nodePlan = requireNotNull(gatePlan.filterPlan.nodePlans.singleOrNull()) {
            "Simple filter gate must produce exactly one node plan"
        }
        val acceptedNode = nodePlan as GPUFilterNodePlan.Accepted
        val nativeRoute = acceptedNode.route as GPUFilterNodeRoute.NativeRender
        val renderNode = nativeRoute.renderNode

        val recordId = "analysis.apply_filter.${command.commandId.value}"
        val pipelineKey = renderNode.pipelineKeyHash
        val renderStep = renderNode.renderStepLabel
        val routeLabel = "native.apply_filter.simple_node"
        val materialKeyHash = "pending.material.filter"

        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "ApplyFilter",
            boundsHash = command.bounds.stableHash(),
            routeDecisionLabel = routeLabel,
            materialKeyHash = materialKeyHash,
            renderStepCandidates = listOf(renderStep),
            sortKey = SortKey(command.ordering.paintOrder.toLong()),
            diagnostics = command.transform.analysisDiagnostics(recordId = recordId),
        )
        val filterNodeKind = command.filterGraph.nodes.single().nodeKind
        val filterRequirements = when (filterNodeKind) {
            "GaussianBlur" -> listOf(firstBlurFilterCapabilityName)
            "ColorFilter" -> listOf(firstColorMatrixFilterCapabilityName)
            else -> listOf(firstBlurFilterCapabilityName, firstColorMatrixFilterCapabilityName)
        }
        val routeDecision = GPUFirstRouteDecisionBuilder.nativeApplyFilter(
            commandIdValue = command.commandId.value,
            pipelinePreimageHash = pipelineKey,
            renderStepIdentity = renderStep,
            requirements = filterRequirements,
        )
        val analysisDecision = GPUDrawAnalysisDecision.Candidate(
            recordId = recordId,
            routeDecisionLabel = routeLabel,
            resourceDeclarations = emptyList(),
            renderStepCandidates = listOf(renderStep),
        )
        val pass = GPUFirstRoutePassBuilder.acceptedApplyFilter(
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

    /** Builds a refused ApplyFilter analysis, route, and pass descriptor. */
    private fun refusedFilterPlan(
        command: NormalizedDrawCommand.ApplyFilter,
        code: String,
    ): GPUFirstRoutePlan {
        val recordId = "analysis.apply_filter.${command.commandId.value}"
        val diagnostic = GPUAnalysisDiagnostic(
            code = code,
            recordId = recordId,
            decisionId = "refused.apply_filter.${command.commandId.value}",
            terminal = true,
        )
        val analysisRecord = GPUDrawAnalysisRecord(
            recordId = recordId,
            commandIdValue = command.commandId.value,
            commandFamily = "ApplyFilter",
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
                subject = "ApplyFilter route",
            ),
            pass = GPUFirstRoutePassBuilder.refusedApplyFilter(
                commandIdValue = command.commandId.value,
                targetStateHash = command.targetStateHash(),
                code = code,
            ),
        )
    }

    /** Converts an ApplyFilter command into a simple filter render-node request. */
    private fun NormalizedDrawCommand.ApplyFilter.toSimpleFilterRequest(): GPUSimpleFilterRenderNodeRequest =
        GPUSimpleFilterRenderNodeRequest(
            label = "accepted",
            graph = filterGraph,
            source = filterSource,
            bounds = filterBounds,
            crop = filterCrop,
            sampling = filterSampling,
            targetFormatClass = layer.target.colorFormat,
            targetGeneration = ordering.paintOrder.toLong(),
            intermediateUsageLabels = SIMPLE_FILTER_REQUIRED_INTERMEDIATE_USAGE_LABELS,
            intermediateOwnershipValidated = true,
            activeAttachmentSampled = false,
            readWriteAliasing = false,
            renderNodeBindingValidated = true,
            cpuRenderedTextureFallbackRequested = false,
        )

    /** Returns the canonical ApplyFilter refusal code, or null when analysis may keep a candidate. */
    private fun NormalizedDrawCommand.ApplyFilter.refusalCode(): String? =
        when {
            !bounds.left.isFinite() || !bounds.top.isFinite() ||
                !bounds.right.isFinite() || !bounds.bottom.isFinite() -> "unsupported.bounds.non_finite"
            bounds.hasNaN() -> "unsupported.bounds.nan"
            transform.type == GPUTransformType.Perspective -> "unsupported.transform.perspective"
            transform.type == GPUTransformType.Singular -> "unsupported.transform.singular"
            transform.type !in acceptedApplyFilterTransformTypes -> "unsupported.transform.class_downgrade"
            clip.kind == GPUClipKind.ComplexStack -> "unsupported.clip.complex_stack"
            clip.kind !in acceptedClipKinds -> "unsupported.clip.analytic_unsupported"
            blend.kind != GPUBlendKind.SrcOver -> "unsupported.blend.mode_unimplemented"
            layer.scopeKind != GPULayerScopeKind.Root -> "unsupported.layer.elision_proof_missing"
            layer.requiresFilter -> "unsupported.layer.filter_chain"
            layer.requiresDestinationRead || ordering.dependsOnDestination ||
                blend.requiresDestinationRead -> "unsupported.destination_read.required"
            layer.target.colorFormat != firstRouteTargetFormat -> "unsupported.target.format_blend_incompatible"
            !filterBounds.finite -> "unsupported.filter.bounds_unbounded"
            filterBounds.width <= 0 || filterBounds.height <= 0 -> "unsupported.filter.bounds_invalid"
            filterGraph.nodes.size != 1 || filterGraph.edges.isNotEmpty() -> "unsupported.filter.graph_node_limit"
            filterGraph.nodes.single().nodeKind !in acceptedFilterNodeKinds -> "unsupported.filter.node_unimplemented"
            (!capabilities.hasFact(firstBlurFilterCapabilityName) &&
                !capabilities.hasFact(firstColorMatrixFilterCapabilityName)) -> "unsupported.pipeline.capability_missing"
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

        /** Render step identity for linear gradient fill routes. */
        const val linearGradientRenderStep = "linear.gradient.fill"

        /** Required capability fact for the radial gradient material route. */
        const val firstRadialGradientCapabilityName = "first_slice.radial_gradient.native"

        /** Required capability fact for the sweep gradient material route. */
        const val firstSweepGradientCapabilityName = "first_slice.sweep_gradient.native"

        /** Required capability fact for the scissor clip route. */
        const val firstScissorCapabilityName = "first_slice.scissor.native"

        /** Required capability fact for the path fill native route. */
        const val firstPathFillCapabilityName = "first_slice.path_fill.native"

        /** Required capability fact for the path fill stencil-cover native promotion. */
        const val firstStencilCoverCapabilityName = "first_slice.path_fill.stencil_cover"

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
            GPUMaterialKind.RuntimeEffect,
        )

        /** Transform classes accepted by the FillPath route. */
        val acceptedFillPathTransformTypes = setOf(GPUTransformType.Identity, GPUTransformType.Translate)

        /** Material kinds accepted by the FillPath prepared route. */
        val acceptedFillPathMaterialKinds = setOf(
            GPUMaterialKind.SolidColor,
            GPUMaterialKind.LinearGradient,
            GPUMaterialKind.RuntimeEffect,
        )

        /** Gradient tile modes accepted by the first expansion route. */
        val acceptedGradientTileModes = setOf("clamp")

        /** Required capability fact for the native DrawImageRect promotion route. */
        const val firstImageDrawNativeCapabilityName = "first_slice.bitmap_rect.native"

        /** Required capability fact for the DrawImageRect prepared route. */
        const val firstImageDrawCapabilityName = "first_slice.draw_image_rect.prepared"

        /** Render step identity for the DrawImageRect prepared upload route. */
        const val imageDrawRenderStep = "image.draw.texture_upload"

        /** Transform classes accepted by the DrawImageRect route. */
        val acceptedDrawImageRectTransformTypes = setOf(GPUTransformType.Identity, GPUTransformType.Translate)

        /** Required capability fact for the native DrawLayer promotion route. */
        const val firstDrawLayerNativeCapabilityName = "first_slice.draw_layer.native_isolation"

        /** Required capability fact for the DrawLayer prepared route. */
        const val firstDrawLayerCapabilityName = "first_slice.draw_layer.prepared"

        /** Render step identity for the DrawLayer composite route. */
        const val drawLayerRenderStep = "layer.composite"

        /** Transform classes accepted by the DrawLayer route. */
        val acceptedDrawLayerTransformTypes = setOf(GPUTransformType.Identity, GPUTransformType.Translate)

        /** Required capability fact for the blur filter native route. */
        const val firstBlurFilterCapabilityName = "first_slice.blur_filter.native"

        /** Required capability fact for the color matrix filter native route. */
        const val firstColorMatrixFilterCapabilityName = "first_slice.color_matrix_filter.native"

        /** Required intermediate usage label set for simple filter intermediate validation. */
        val SIMPLE_FILTER_REQUIRED_INTERMEDIATE_USAGE_LABELS = setOf("render_attachment", "texture_binding")

        /** Filter node kinds accepted by the simple filter route. */
        val acceptedFilterNodeKinds = setOf("ColorFilter", "GaussianBlur")

        /** Transform classes accepted by the ApplyFilter route. */
        val acceptedApplyFilterTransformTypes = setOf(GPUTransformType.Identity, GPUTransformType.Translate)

        /** Render step identity for blur mask filter route. */
        const val blurMaskFilterRenderStep = "path.fill.blur_mask"
    }
}

/** Returns a terminal refusal code for a blur mask filter, or null when blur is valid and may be accepted. */
private fun NormalizedMaskFilter.Blur.refusalCode(): String? = when {
    !sigma.isFinite() || sigma < 0f -> "unsupported.mask_filter.blur_sigma_invalid"
    style.name.isBlank() -> "unsupported.mask_filter.blur_style_invalid"
    else -> null
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

/** Returns a terminal coordinate or bounds refusal code for FillPath before route acceptance. */
private fun NormalizedDrawCommand.FillPath.coordinateRefusalCode(): String? =
    when {
        transform.hasNonFiniteFacts() -> "unsupported.transform.non_finite"
        bounds.hasNaN() || clip.bounds.hasNaN() -> "unsupported.bounds.nan"
        bounds.hasNonFinite() || clip.bounds.hasNonFinite() -> "unsupported.bounds.non_finite"
        else -> null
    }

/** Returns a terminal coordinate or bounds refusal code for DrawImageRect before route acceptance. */
private fun NormalizedDrawCommand.DrawImageRect.coordinateRefusalCode(): String? =
    when {
        transform.hasNonFiniteFacts() -> "unsupported.transform.non_finite"
        bounds.hasNaN() || clip.bounds.hasNaN() -> "unsupported.bounds.nan"
        bounds.hasNonFinite() || clip.bounds.hasNonFinite() -> "unsupported.bounds.non_finite"
        else -> null
    }

/** Returns the scissor bounds hash for DrawImageRect, or null for wide-open clips. */
private fun NormalizedDrawCommand.DrawImageRect.scissorBoundsHash(): String? =
    when (clip.kind) {
        GPUClipKind.DeviceRect -> clip.bounds.stableHash()
        GPUClipKind.WideOpen,
        GPUClipKind.ComplexStack,
        -> null
    }

/** Returns a stable target-state hash for DrawImageRect. */
private fun NormalizedDrawCommand.DrawImageRect.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

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

/** Returns the accepted simple scissor bounds hash for FillPath, or null for wide-open clips. */
private fun NormalizedDrawCommand.FillPath.scissorBoundsHash(): String? =
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

/** Returns a stable target-state placeholder for FillPath. */
private fun NormalizedDrawCommand.FillPath.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

/** Returns the accepted simple scissor bounds hash for ApplyFilter, or null for wide-open clips. */
private fun NormalizedDrawCommand.ApplyFilter.scissorBoundsHash(): String? =
    when (clip.kind) {
        GPUClipKind.DeviceRect -> clip.bounds.stableHash()
        GPUClipKind.WideOpen,
        GPUClipKind.ComplexStack,
        -> null
    }

/** Returns a stable target-state placeholder for ApplyFilter. */
private fun NormalizedDrawCommand.ApplyFilter.targetStateHash(): String =
    "target.${layer.target.colorFormat}.${layer.target.width}x${layer.target.height}"

/** Emits stable path-fact diagnostics for analysis dumps. */
private fun NormalizedDrawCommand.FillPath.pathFactsDiagnostics(recordId: String): List<GPUAnalysisDiagnostic> =
    listOf(
        GPUAnalysisDiagnostic(
            code = "geometry:path key=${pathKey} verbs=${pathDescriptor.verbCount} " +
                "points=${pathDescriptor.pointCount} fillRule=${pathDescriptor.fillRule} " +
                "inverse=${pathDescriptor.inverseFill} transform=${pathDescriptor.transformClass} " +
                "edges=${edgeCount} finite=${pathDescriptor.finiteProof} " +
                "volatility=${pathDescriptor.volatility}",
            recordId = recordId,
            terminal = false,
        ),
    )

/** Sanitizes a path key for use in analysis artifact keys. */
private fun String.sanitizeForAnalysisKey(): String =
    map { char ->
        when {
            char.isLetterOrDigit() -> char
            else -> '_'
        }
    }.joinToString("")
        .trim('_')

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
