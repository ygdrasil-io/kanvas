package org.graphiks.kanvas.gpu.renderer.geometry

import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandOperandBridge
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacketID
import org.graphiks.kanvas.gpu.renderer.passes.dumpLines
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandBinding
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandKind
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedCommandOperandReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceReference
import org.graphiks.kanvas.gpu.renderer.resources.GPUMaterializedResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceDiagnostic
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationDecision
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourceMaterializationPreimagePlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUTextureResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUTargetPreparationContext
import org.graphiks.kanvas.gpu.renderer.resources.dumpLines

/** Shape descriptor captured before geometry lowering. */
data class GPUShapeDescriptor(
    val shapeKind: String,
    val boundsLabel: String,
    val antiAliasMode: String,
    val provenance: String,
)

/** Path descriptor captured before route selection. */
data class GPUPathDescriptor(
    val pathKey: String,
    val verbCount: Int,
    val pointCount: Int,
    val fillRule: String,
    val inverseFill: Boolean,
    val finiteProof: String,
    val volatility: String,
    val transformClass: String = "identity",
    val edgeCount: Int = verbCount,
)

/** Stroke descriptor captured before expansion. */
data class GPUStrokeDescriptor(
    val width: Float,
    val cap: String,
    val join: String,
    val miter: Float,
    val dashOrPathEffectRef: String? = null,
    val transformClass: String = "identity",
    val finiteWidth: Boolean = true,
    val hairline: Boolean = false,
    val edgeCount: Int = 0,
)

/** DrawPoints descriptor captured before route selection. */
data class GPUDrawPointsDescriptor(
    val pointMode: String,
    val pointCount: Int,
    val strokeWidth: Float,
    val strokeCap: String,
    val localMatrixHash: String?,
    val transformClass: String = "identity",
    val finiteProof: String = "finite",
)

/** Geometry route selected for a shape. */
sealed interface GPUGeometryRoute {
    /** Analytic geometry route. */
    data class Analytic(val renderStepLabel: String) : GPUGeometryRoute

    /** Tessellation route. */
    data class Tessellation(val tessellationPlanHash: String) : GPUGeometryRoute

    /** Stencil-cover route. */
    data class StencilCover(val stencilPlan: GPUStencilCoverPlan) : GPUGeometryRoute

    /** Path atlas route. */
    data class PathAtlas(val atlasPlan: GPUPathAtlasPlan) : GPUGeometryRoute

    /** Coverage mask route. */
    data class CoverageMask(val atlasPlan: GPUCoverageAtlasPlan) : GPUGeometryRoute

    /** Prepared geometry route. */
    data class Prepared(val plan: GPUPreparedGeometryPlan) : GPUGeometryRoute

    /** Refused geometry route. */
    data class Refused(val diagnostic: GPUGeometryDiagnostic) : GPUGeometryRoute
}

/** Geometry plan for one shape. */
data class GPUGeometryPlan(
    val descriptor: GPUShapeDescriptor,
    val path: GPUPathDescriptor? = null,
    val stroke: GPUStrokeDescriptor? = null,
    val points: GPUDrawPointsDescriptor? = null,
    val route: GPUGeometryRoute,
    val diagnostics: List<GPUGeometryDiagnostic> = emptyList(),
)

/** Path bounds plan. */
data class GPUPathBoundsPlan(
    val pathKey: String,
    val boundsLabel: String,
    val conservative: Boolean,
    val proofHash: String,
)

/** Stroke expansion plan. */
data class GPUStrokeExpansionPlan(
    val strokeDescriptorHash: String,
    val expansionMode: String,
    val joinsRequireFallback: Boolean,
    val outputBoundsLabel: String,
)

/** Stencil-cover execution plan. */
data class GPUStencilCoverPlan(
    val stencilStepLabel: String,
    val coverStepLabel: String,
    val fillRule: String,
    val requiresMSAA: Boolean,
    val depthStencilFormat: String = "",
    val depthStencilEvidenceLabel: String? = null,
    val sampleCount: Int = 0,
    val sampleCountEvidenceLabel: String? = null,
    val stencilStateLabel: String = "",
    val producerBoundsLabel: String = "",
    val coverBoundsLabel: String = "",
    val clearLoadStorePolicy: String = "",
    val atomicGroupLabel: String = "",
    val orderingToken: String = "",
    val sortWindowPolicy: String = "",
    val adapterEvidenceLabel: String? = null,
    val passResourceEvidenceLabel: String? = null,
    val readbackEvidenceLabel: String? = null,
    val targetStateLabel: String = "",
    val targetEvidenceLabel: String? = null,
    val targetSupportsStencilCover: Boolean = true,
    val clipStateLabel: String = "",
    val clipSupportsStencilCover: Boolean = true,
)

/** Prepared geometry artifact plan. */
data class GPUPreparedGeometryPlan(
    val artifact: PrecomputedGeometryArtifact,
    val consumerKind: String,
    val invalidationFacts: List<String>,
)

/** Geometry render-step plan. */
data class GPUGeometryRenderStepPlan(
    val renderStepLabel: String,
    val geometryClass: String,
    val coverageClass: String,
    val vertexLayoutHash: String,
)

/** Path atlas plan. */
data class GPUPathAtlasPlan(
    val atlasPolicy: GPUAtlasPolicy,
    val entryRef: GPUAtlasEntryRef,
    val mutationPlan: GPUAtlasMutationPlan,
)

/** Coverage atlas plan. */
data class GPUCoverageAtlasPlan(
    val atlasPolicy: GPUAtlasPolicy,
    val entryRef: GPUAtlasEntryRef,
    val maskArtifact: CoverageMaskArtifact,
)

/** Atlas storage policy. */
data class GPUAtlasPolicy(
    val atlasKind: String,
    val budget: GPUAtlasBudgetPolicy,
    val evictionPolicy: String,
)

/** Atlas budget policy. */
data class GPUAtlasBudgetPolicy(
    val maxBytes: Long,
    val maxEntries: Int,
    val pressureClass: String,
)

/** Atlas entry reference. */
@JvmInline
value class GPUAtlasEntryRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUAtlasEntryRef.value must not be blank" }
    }
}

/** Atlas mutation plan. */
data class GPUAtlasMutationPlan(
    val mutationId: String,
    val entryRef: GPUAtlasEntryRef,
    val operation: String,
    val useTokenLabel: String,
)

/** Path atlas artifact descriptor. */
data class PathAtlasArtifact(
    val artifactKey: String,
    val boundsLabel: String,
    val generation: Long,
    val lifetimeClass: String,
    val budgetClass: String,
)

/** Coverage mask artifact descriptor. */
data class CoverageMaskArtifact(
    val artifactKey: String,
    val boundsLabel: String,
    val generation: Long,
    val lifetimeClass: String,
    val budgetClass: String,
)

/** Precomputed geometry artifact descriptor. */
data class PrecomputedGeometryArtifact(
    val artifactKey: String,
    val boundsLabel: String,
    val generation: Long,
    val lifetimeClass: String,
    val budgetClass: String,
)

/** Geometry diagnostic. */
data class GPUGeometryDiagnostic(
    val code: String,
    val geometryLabel: String,
    val message: String,
    val terminal: Boolean,
    val facts: Map<String, String> = emptyMap(),
)

/** Adapter-backed evidence required before a stencil-cover candidate can be dumpable. */
data class GPUStencilCoverEvidence(
    val adapterEvidenceLabel: String?,
    val depthStencilCapability: Boolean,
    val depthStencilEvidenceLabel: String? = null,
    val sampleCount: Int,
    val sampleCountEvidenceLabel: String? = null,
    val stencilStateLabel: String,
    val producerBeforeCoverOrdering: Boolean,
    val passResourceEvidenceLabel: String?,
    val readbackEvidenceLabel: String?,
    val targetStateLabel: String,
    val targetEvidenceLabel: String? = null,
    val targetSupportsStencilCover: Boolean = true,
    val clipStateLabel: String = "",
    val clipSupportsStencilCover: Boolean = true,
)

/** Atlas policy request used to prove refusal gates without atlas activation. */
data class GPUAtlasPolicyRequest(
    val routeLabel: String,
    val artifactType: String,
    val policyMode: String,
    val contentKeyLabel: String,
    val boundsLabel: String,
    val availableFacts: Set<String>,
    val selectorEvidenceOnly: Boolean = false,
)

/** Atlas-specific diagnostic emitted by accepted future routes or current refusals. */
data class GPUAtlasDiagnostic(
    val code: String,
    val routeLabel: String,
    val artifactType: String,
    val policyMode: String,
    val message: String,
    val terminal: Boolean,
)

/** Result of evaluating an atlas policy request. */
sealed interface GPUAtlasPolicyResult {
    /** Accepted atlas policy with entry details. */
    data class Accepted(
        val policy: GPUAtlasPolicy,
        val entryRef: GPUAtlasEntryRef,
        val mutationPlan: GPUAtlasMutationPlan,
        val diagnostic: GPUAtlasDiagnostic,
    ) : GPUAtlasPolicyResult {
        /** Emits stable acceptance evidence without raw atlas keys or resource handles. */
        fun dumpLines(): List<String> =
            listOf(
                "atlas-policy:accepted row=gpu-renderer.atlas-policy-accepted " +
                    "route=${diagnostic.routeLabel} artifact=${diagnostic.artifactType} " +
                    "policy=${diagnostic.policyMode} atlasKind=${policy.atlasKind} " +
                    "eviction=${policy.evictionPolicy} entryRef=${entryRef.value} " +
                    "mutation=${mutationPlan.operation}",
                "atlas-policy:accepted budget=maxBytes=${policy.budget.maxBytes} " +
                    "maxEntries=${policy.budget.maxEntries} " +
                    "pressure=${policy.budget.pressureClass}",
            )
    }

    /** Refused atlas policy with diagnostic. */
    data class Refused(val refusal: GPUAtlasPolicyRefusal) : GPUAtlasPolicyResult {
        /** Emits stable refusal evidence without raw atlas keys or resource handles. */
        fun dumpLines(): List<String> =
            refusal.dumpLines()
    }
}

/** Refusal-only atlas policy result for M3 coverage/path atlas gates. */
data class GPUAtlasPolicyRefusal(
    val diagnostic: GPUAtlasDiagnostic,
    val classification: String,
    val requiredFacts: List<String>,
    val missingFacts: List<String>,
    val dashboardRow: String = "gpu-renderer.atlas-policy-refusal",
) {
    /** Emits stable refusal evidence without raw atlas keys or resource handles. */
    fun dumpLines(): List<String> =
        listOf(
            "atlas-policy:refused row=$dashboardRow classification=$classification " +
                "route=${diagnostic.routeLabel} artifact=${diagnostic.artifactType} " +
                "policy=${diagnostic.policyMode} reason=${diagnostic.code}",
            "atlas-policy:required facts=${requiredFacts.joinToString(",")}",
            "atlas-policy:missing facts=${missingFacts.joinToString(",")}",
            atlasPolicyNonClaimLine,
        )
}

/** Evaluates path and coverage atlas policy requests with conditional acceptance for basic fills. */
class GPUAtlasPolicyRefusalGate {
    /** Evaluates a path or coverage atlas request, returning accepted policy or stable refusal. */
    fun evaluate(request: GPUAtlasPolicyRequest): GPUAtlasPolicyResult {
        val requiredFacts = request.requiredFacts()
        val missingFacts = request.missingPolicyFacts(requiredFacts)
        val diagnosticCode = request.refusalCode(missingFacts)

        if (diagnosticCode != null) {
            val effectiveMissingFacts = when (diagnosticCode) {
                "unsupported.atlas.key_nondeterministic" -> listOf("content-key")
                "unsupported.coverage.mask_bounds_invalid" -> listOf("bounds-proof")
                else -> missingFacts
            }
            return GPUAtlasPolicyResult.Refused(
                GPUAtlasPolicyRefusal(
                    diagnostic = GPUAtlasDiagnostic(
                        code = diagnosticCode,
                        routeLabel = request.routeLabel,
                        artifactType = request.artifactType,
                        policyMode = request.policyMode,
                        message = "Atlas route refused: $diagnosticCode",
                        terminal = true,
                    ),
                    classification = "RefuseRequired",
                    requiredFacts = requiredFacts,
                    missingFacts = effectiveMissingFacts,
                ),
            )
        }

        val atlasKind = when (request.artifactType) {
            "PathAtlasArtifact" -> "PathAtlas"
            "CoverageMaskArtifact" -> "CoverageAtlas"
            else -> "GenericAtlas"
        }
        val sanitizedKey = request.contentKeyLabel.sanitizeForArtifactKey()
        val sanitizedBounds = request.boundsLabel.sanitizeForArtifactKey()
        val entryRef = GPUAtlasEntryRef("$atlasKind:$sanitizedKey:$sanitizedBounds:v1")
        val mutationPlan = GPUAtlasMutationPlan(
            mutationId = "atlas-mutation:$sanitizedKey",
            entryRef = entryRef,
            operation = "upload-before-sample",
            useTokenLabel = "use-token:$sanitizedKey",
        )

        return GPUAtlasPolicyResult.Accepted(
            policy = GPUAtlasPolicy(
                atlasKind = atlasKind,
                budget = GPUAtlasBudgetPolicy(
                    maxBytes = 64 * 1024 * 1024L,
                    maxEntries = 256,
                    pressureClass = "path-coverage-medium",
                ),
                evictionPolicy = "generation-stale-with-use-token",
            ),
            entryRef = entryRef,
            mutationPlan = mutationPlan,
            diagnostic = GPUAtlasDiagnostic(
                code = "atlas.policy.accepted",
                routeLabel = request.routeLabel,
                artifactType = request.artifactType,
                policyMode = request.policyMode,
                message = "Atlas route accepted: ${request.policyMode}",
                terminal = false,
            ),
        )
    }

    private fun GPUAtlasPolicyRequest.requiredFacts(): List<String> =
        when (artifactType) {
            "PathAtlasArtifact" -> pathAtlasRequiredFacts
            "CoverageMaskArtifact" -> coverageAtlasRequiredFacts
            else -> commonAtlasRequiredFacts
        }

    private fun GPUAtlasPolicyRequest.missingPolicyFacts(requiredFacts: List<String>): List<String> =
        requiredFacts
            .filterNot { fact -> availableFacts.contains(fact) }
            .let { missing ->
                if (selectorEvidenceOnly) {
                    missing + "selector-only-evidence"
                } else {
                    missing
                }
            }

    private fun GPUAtlasPolicyRequest.refusalCode(missingFacts: List<String>): String? =
        when {
            !contentKeyLabel.isCanonicalAtlasContentKey() -> "unsupported.atlas.key_nondeterministic"
            boundsLabel.isBlank() -> "unsupported.coverage.mask_bounds_invalid"
            selectorEvidenceOnly -> "unsupported.atlas.policy_unavailable"
            missingFacts.any { it in atlasPolicyFacts } -> "unsupported.atlas.policy_unavailable"
            missingFacts.any { it in atlasSyncFacts } -> "unsupported.atlas.sync_unavailable"
            else -> null
        }

    private companion object {
        val commonAtlasRequiredFacts = listOf(
            "content-key",
            "bounds-proof",
            "budget-policy",
            "generation-policy",
            "eviction-policy",
            "use-token-policy",
            "mutation-ordering",
            "upload-before-sample",
            "sync-policy",
        )
        val pathAtlasRequiredFacts = commonAtlasRequiredFacts + "gpu-sampling-evidence"
        val coverageAtlasRequiredFacts = commonAtlasRequiredFacts
        val atlasPolicyFacts = setOf(
            "budget-policy",
            "generation-policy",
            "eviction-policy",
            "use-token-policy",
            "mutation-ordering",
        )
        val atlasSyncFacts = setOf("upload-before-sample", "sync-policy")
    }
}

/** Builds the first M3 prepared path-fill route evidence without product activation. */
class GPUBasicPathFillPreparedPlanner(
    private val maxEdges: Int = 256,
) {
    /**
     * Plans one bounded path fill as a typed CPU-prepared GPU artifact.
     *
     * The plan is contract evidence only: it names the GPU consumer that would
     * sample the prepared coverage, but it does not create an atlas entry,
     * upload resources, submit adapter work, or activate product routing.
     */
    fun plan(
        descriptor: GPUShapeDescriptor,
        path: GPUPathDescriptor,
    ): GPUGeometryPlan {
        val refusalCode = descriptor.refusalCode() ?: path.refusalCode(maxEdges = maxEdges)
        if (refusalCode != null) {
            val diagnostic = GPUGeometryDiagnostic(
                code = refusalCode,
                geometryLabel = descriptor.shapeKind.ifBlank { "path-fill" },
                message = "Basic prepared path fill refused: $refusalCode",
                terminal = true,
            )
            return GPUGeometryPlan(
                descriptor = descriptor,
                path = path,
                route = GPUGeometryRoute.Refused(diagnostic),
                diagnostics = listOf(diagnostic),
            )
        }

        val artifact = PrecomputedGeometryArtifact(
            artifactKey = path.preparedArtifactKey(),
            boundsLabel = descriptor.boundsLabel,
            generation = 1,
            lifetimeClass = "recording-local",
            budgetClass = "path-fill-small",
        )
        val preparedPlan = GPUPreparedGeometryPlan(
            artifact = artifact,
            consumerKind = pathFillConsumerKind,
            invalidationFacts = pathFillInvalidationFacts,
        )

        return GPUGeometryPlan(
            descriptor = descriptor,
            path = path,
            route = GPUGeometryRoute.Prepared(preparedPlan),
            diagnostics = listOf(
                GPUGeometryDiagnostic(
                    code = "geometry:path-fill.prepared",
                    geometryLabel = descriptor.shapeKind,
                    message = "Prepared path-fill artifact is available for $pathFillConsumerKind",
                    terminal = false,
                ),
            ),
        )
    }

    /**
     * M15: Tessellates flattened device-space vertices into a single fan
     * (triangle-fan) vertex buffer for GPU submission.
     *
     * Each contour is tessellated as a separate fan. For single-contour paths
     * this produces an indexed fan; for multi-contour paths each contour is
     * emitted as its own triangle list. The max 256 edges budget is enforced
     * on total vertex count (2 floats per vertex).
     */
    fun tessellate(
        flattenedVertices: List<Float>,
        contourStarts: List<Int>,
        edgeCount: Int,
    ): GPUPathTessellationResult {
        if (flattenedVertices.size < 6 || contourStarts.isEmpty()) {
            return GPUPathTessellationResult(
                accepted = false,
                vertexBuffer = emptyList(),
                indexBuffer = emptyList(),
                triangleCount = 0,
                vertexCount = 0,
                refusalCode = "unsupported.geometry.path_degenerate",
            )
        }
        if (edgeCount < 3 || edgeCount > maxEdges) {
            return GPUPathTessellationResult(
                accepted = false,
                vertexBuffer = emptyList(),
                indexBuffer = emptyList(),
                triangleCount = 0,
                vertexCount = 0,
                refusalCode = "unsupported.path.edge_budget",
            )
        }

        val vertexCount = flattenedVertices.size / 2
        val indices = mutableListOf<Int>()
        for (ci in contourStarts.indices) {
            val start = contourStarts[ci]
            val end = if (ci + 1 < contourStarts.size) contourStarts[ci + 1] else vertexCount
            val contourVertexCount = end - start
            if (contourVertexCount < 3) continue
            indices.add(start)
            for (i in 1 until contourVertexCount - 1) {
                indices.add(start + i)
                indices.add(start + i + 1)
            }
        }

        return GPUPathTessellationResult(
            accepted = true,
            vertexBuffer = flattenedVertices,
            indexBuffer = indices,
            triangleCount = indices.size / 3,
            vertexCount = vertexCount,
            refusalCode = null,
        )
    }

    private companion object {
        const val pathFillConsumerKind = "coverage-mask.sample.path-fill"
        val pathFillInvalidationFacts = listOf("path-content-hash", "fill-rule", "transform-class", "bounds-proof")
    }
}

/** M15 path tessellation result produced by [GPUBasicPathFillPreparedPlanner.tessellate]. */
data class GPUPathTessellationResult(
    val accepted: Boolean,
    val vertexBuffer: List<Float>,
    val indexBuffer: List<Int>,
    val triangleCount: Int,
    val vertexCount: Int,
    val refusalCode: String?,
)

/** Builds the first M3 prepared simple-stroke route evidence without product activation. */
class GPUSimpleStrokePreparedPlanner(
    private val maxEdges: Int = 128,
) {
    /**
     * Plans one bounded simple stroke as a typed CPU-prepared GPU geometry artifact.
     *
     * The plan is contract evidence only: CPU work may expand stroke geometry
     * into buffers consumed by a GPU render step, but it does not rasterize
     * shaded pixels, submit adapter work, or claim broad stroke parity.
     */
    fun plan(
        descriptor: GPUShapeDescriptor,
        path: GPUPathDescriptor,
        stroke: GPUStrokeDescriptor,
    ): GPUGeometryPlan {
        val refusalCode = descriptor.strokeRefusalCode() ?: path.strokePathRefusalCode() ?: stroke.refusalCode(maxEdges)
        if (refusalCode != null) {
            val diagnostic = GPUGeometryDiagnostic(
                code = refusalCode,
                geometryLabel = descriptor.shapeKind.ifBlank { "path-stroke" },
                message = "Simple prepared stroke refused: $refusalCode",
                terminal = true,
            )
            return GPUGeometryPlan(
                descriptor = descriptor,
                path = path,
                stroke = stroke,
                route = GPUGeometryRoute.Refused(diagnostic),
                diagnostics = listOf(diagnostic),
            )
        }

        val descriptorHash = stroke.preparedStrokeDescriptorHash(path)
        val expansionPlan = GPUStrokeExpansionPlan(
            strokeDescriptorHash = descriptorHash,
            expansionMode = strokeExpansionMode,
            joinsRequireFallback = false,
            outputBoundsLabel = descriptor.boundsLabel,
        )
        val artifact = PrecomputedGeometryArtifact(
            artifactKey = "prepared.stroke.${descriptorHash.removePrefix("stroke.")}",
            boundsLabel = descriptor.boundsLabel,
            generation = 1,
            lifetimeClass = "recording-local",
            budgetClass = "stroke-simple",
        )

        return GPUGeometryPlan(
            descriptor = descriptor,
            path = path,
            stroke = stroke,
            route = GPUGeometryRoute.Prepared(
                GPUPreparedGeometryPlan(
                    artifact = artifact,
                    consumerKind = strokeConsumerKind,
                    invalidationFacts = strokeInvalidationFacts,
                ),
            ),
            diagnostics = listOf(
                GPUGeometryDiagnostic(
                    code = "geometry:stroke.prepared",
                    geometryLabel = descriptor.shapeKind,
                    message = "Prepared simple stroke artifact is available for $strokeConsumerKind",
                    terminal = false,
                ),
            ),
        )
    }

    private companion object {
        const val strokeConsumerKind = "stroke-strip.render-step"
        const val strokeExpansionMode = "cpu-prepared-stroke-strip"
        val strokeInvalidationFacts = listOf(
            "path-content-hash",
            "stroke-width",
            "cap",
            "join",
            "miter",
            "transform-class",
            "bounds-proof",
        )
    }
}

/** Builds drawPoints prepared-route evidence without runtime local-matrix compilation. */
class GPUDrawPointsPreparedPlanner {
    fun plan(
        descriptor: GPUShapeDescriptor,
        points: GPUDrawPointsDescriptor,
    ): GPUGeometryPlan {
        val refusalCode = descriptor.drawPointsRefusalCode() ?: points.refusalCode()
        if (refusalCode != null) {
            val diagnostic = GPUGeometryDiagnostic(
                code = refusalCode,
                geometryLabel = descriptor.shapeKind.ifBlank { "draw-points" },
                message = "Prepared drawPoints refused: $refusalCode",
                terminal = true,
                facts = points.stableFacts(),
            )
            return GPUGeometryPlan(
                descriptor = descriptor,
                points = points,
                route = GPUGeometryRoute.Refused(diagnostic),
                diagnostics = listOf(diagnostic),
            )
        }

        val artifact = PrecomputedGeometryArtifact(
            artifactKey = points.preparedArtifactKey(),
            boundsLabel = descriptor.boundsLabel,
            generation = 1,
            lifetimeClass = "recording-local",
            budgetClass = "draw-points",
        )

        return GPUGeometryPlan(
            descriptor = descriptor,
            points = points,
            route = GPUGeometryRoute.Prepared(
                GPUPreparedGeometryPlan(
                    artifact = artifact,
                    consumerKind = points.consumerKind(),
                    invalidationFacts = drawPointsInvalidationFacts,
                ),
            ),
            diagnostics = listOf(
                GPUGeometryDiagnostic(
                    code = "geometry:draw-points.prepared",
                    geometryLabel = descriptor.shapeKind,
                    message = "Prepared drawPoints artifact is available for ${points.consumerKind()}",
                    terminal = false,
                ),
            ),
        )
    }

    private companion object {
        val drawPointsInvalidationFacts = listOf(
            "point-mode",
            "point-count",
            "stroke-width",
            "stroke-cap",
            "transform-class",
            "local-matrix",
            "bounds-proof",
        )
    }
}

/** Builds the M3 native stencil-cover gate evidence without product activation. */
class GPUStencilCoverGatePlanner(
    private val maxEdges: Int = 256,
) {
    /**
     * Plans one bounded path fill as a stencil-cover candidate only when every
     * adapter-backed evidence gate is named. Missing gates remain stable
     * refusals with skipped-lane diagnostics.
     */
    fun plan(
        descriptor: GPUShapeDescriptor,
        path: GPUPathDescriptor,
        evidence: GPUStencilCoverEvidence,
    ): GPUGeometryPlan {
        val refusalCode =
            descriptor.stencilCoverRefusalCode() ?: path.stencilCoverPathRefusalCode(maxEdges) ?: evidence.refusalCode()
        if (refusalCode != null) {
            val diagnostic = GPUGeometryDiagnostic(
                code = refusalCode,
                geometryLabel = stencilCoverGeometryLabel,
                message = "Stencil-cover route refused: $refusalCode",
                terminal = true,
                facts = evidence.dumpFacts(),
            )
            return GPUGeometryPlan(
                descriptor = descriptor,
                path = path,
                route = GPUGeometryRoute.Refused(diagnostic),
                diagnostics = listOf(diagnostic),
            )
        }

        val plan = GPUStencilCoverPlan(
            stencilStepLabel = stencilProducerStep,
            coverStepLabel = coverConsumerStep,
            fillRule = path.fillRule,
            requiresMSAA = evidence.sampleCount > 1,
            depthStencilFormat = depthStencilFormat,
            depthStencilEvidenceLabel = evidence.depthStencilEvidenceLabel,
            sampleCount = evidence.sampleCount,
            sampleCountEvidenceLabel = evidence.sampleCountEvidenceLabel,
            stencilStateLabel = evidence.stencilStateLabel,
            producerBoundsLabel = descriptor.boundsLabel,
            coverBoundsLabel = descriptor.boundsLabel,
            clearLoadStorePolicy = clearLoadStorePolicy,
            atomicGroupLabel = "atomic-group:path-stencil-cover:${path.pathKey.sanitizeForArtifactKey()}",
            orderingToken = stencilCoverOrderingToken,
            sortWindowPolicy = sortWindowPolicy,
            adapterEvidenceLabel = evidence.adapterEvidenceLabel,
            passResourceEvidenceLabel = evidence.passResourceEvidenceLabel,
            readbackEvidenceLabel = evidence.readbackEvidenceLabel,
            targetStateLabel = evidence.targetStateLabel,
            targetEvidenceLabel = evidence.targetEvidenceLabel,
            targetSupportsStencilCover = evidence.targetSupportsStencilCover,
            clipStateLabel = evidence.clipStateLabel,
            clipSupportsStencilCover = evidence.clipSupportsStencilCover,
        )
        return GPUGeometryPlan(
            descriptor = descriptor,
            path = path,
            route = GPUGeometryRoute.StencilCover(plan),
            diagnostics = listOf(
                GPUGeometryDiagnostic(
                    code = "geometry:stencil-cover.candidate",
                    geometryLabel = stencilCoverGeometryLabel,
                    message = "Stencil-cover candidate evidence is complete but not product-promoted",
                    terminal = false,
                    facts = evidence.dumpFacts(),
                ),
            ),
        )
    }

    private companion object {
        const val stencilCoverGeometryLabel = "path-stencil-cover"
        const val stencilProducerStep = "path-fill.stencil-producer"
        const val coverConsumerStep = "path-fill.cover-consumer"
        const val depthStencilFormat = "Depth24PlusStencil8"
        const val clearLoadStorePolicy = "clear-stencil-store-color-discard-stencil"
        const val stencilCoverOrderingToken = "producer-before-cover"
        const val sortWindowPolicy = "atomic-no-interleave"
    }
}

/** Emits stable M3 path-fill evidence lines for reports and tests. */
fun GPUGeometryPlan.dumpLines(): List<String> =
    when (val selectedRoute = route) {
        is GPUGeometryRoute.Prepared -> {
            if (points != null) {
                listOf(
                    "geometry:draw-points.prepared routeKind=CPUPreparedGPU consumer=${selectedRoute.plan.consumerKind}",
                    "draw-points:descriptor mode=${points.pointMode} count=${points.pointCount} " +
                        "width=${points.strokeWidth} cap=${points.strokeCap} " +
                        "transform=${points.transformClass} finite=${points.finiteProof} " +
                        "localMatrix=${points.stableLocalMatrixEvidenceLabel()}",
                    "artifact:key=${selectedRoute.plan.artifact.artifactKey} " +
                        "lifetime=${selectedRoute.plan.artifact.lifetimeClass} " +
                        "budget=${selectedRoute.plan.artifact.budgetClass} " +
                        "bounds=${selectedRoute.plan.artifact.boundsLabel}",
                    drawPointsNonClaimLine,
                )
            } else if (stroke != null) {
                val pathDescriptor = requireNotNull(path) { "prepared stroke dump requires a path descriptor" }
                val expansionPlan = GPUStrokeExpansionPlan(
                    strokeDescriptorHash = stroke.preparedStrokeDescriptorHash(pathDescriptor),
                    expansionMode = "cpu-prepared-stroke-strip",
                    joinsRequireFallback = false,
                    outputBoundsLabel = descriptor.boundsLabel,
                )
                listOf(
                    "geometry:stroke.prepared routeKind=CPUPreparedGPU consumer=${selectedRoute.plan.consumerKind}",
                    "stroke:descriptor path=${pathDescriptor.pathKey} width=${stroke.width} " +
                        "cap=${stroke.cap} join=${stroke.join} miter=${stroke.miter} " +
                        "transform=${stroke.transformClass} edges=${stroke.edgeCount} " +
                        "finite=${stroke.finiteWidth} hairline=${stroke.hairline} " +
                        "dash=${stroke.dashOrPathEffectRef ?: "none"}",
                    "stroke:expansion mode=${expansionPlan.expansionMode} " +
                        "descriptorHash=${expansionPlan.strokeDescriptorHash} " +
                        "outputBounds=${expansionPlan.outputBoundsLabel} " +
                        "joinsFallback=${expansionPlan.joinsRequireFallback}",
                    "artifact:key=${selectedRoute.plan.artifact.artifactKey} " +
                        "lifetime=${selectedRoute.plan.artifact.lifetimeClass} " +
                        "budget=${selectedRoute.plan.artifact.budgetClass} " +
                        "bounds=${selectedRoute.plan.artifact.boundsLabel}",
                    strokeNonClaimLine,
                )
            } else {
                val pathDescriptor = requireNotNull(path) { "prepared path-fill dump requires a path descriptor" }
                listOf(
                    "geometry:path-fill.prepared routeKind=CPUPreparedGPU consumer=${selectedRoute.plan.consumerKind}",
                    "path:descriptor key=${pathDescriptor.pathKey} verbs=${pathDescriptor.verbCount} " +
                        "points=${pathDescriptor.pointCount} fillRule=${pathDescriptor.fillRule} " +
                        "inverse=${pathDescriptor.inverseFill} transform=${pathDescriptor.transformClass} " +
                        "edges=${pathDescriptor.edgeCount} finite=${pathDescriptor.finiteProof} " +
                        "volatility=${pathDescriptor.volatility}",
                    "artifact:key=${selectedRoute.plan.artifact.artifactKey} " +
                        "lifetime=${selectedRoute.plan.artifact.lifetimeClass} " +
                        "budget=${selectedRoute.plan.artifact.budgetClass} " +
                        "bounds=${selectedRoute.plan.artifact.boundsLabel}",
                    pathFillNonClaimLine,
                )
            }
        }
        is GPUGeometryRoute.Refused -> {
            if (selectedRoute.diagnostic.geometryLabel == "path-stencil-cover") {
                listOf(
                    "geometry:stencil-cover.refused row=gpu-renderer.path.stencil-cover " +
                        "classification=TargetNative routeKind=GPUNative reason=${selectedRoute.diagnostic.code}",
                    selectedRoute.diagnostic.stencilCoverSkippedLine(),
                    stencilCoverRefusalNonClaimLine,
                )
            } else if (points != null) {
                listOf(
                    "geometry:draw-points.refused reason=${selectedRoute.diagnostic.code}",
                    "draw-points:descriptor mode=${points.pointMode} count=${points.pointCount} " +
                        "width=${points.strokeWidth.stableLabel()} cap=${points.strokeCap} " +
                        "transform=${points.transformClass} finite=${points.finiteProof} " +
                        "localMatrix=${points.stableLocalMatrixEvidenceLabel()}",
                    drawPointsNonClaimLine,
                )
            } else if (stroke != null) {
                listOf(
                    "geometry:stroke.refused reason=${selectedRoute.diagnostic.code}",
                    strokeNonClaimLine,
                )
            } else {
                listOf(
                    "geometry:path-fill.refused reason=${selectedRoute.diagnostic.code}",
                    pathFillNonClaimLine,
                )
            }
        }
        is GPUGeometryRoute.StencilCover -> {
            val pathDescriptor = requireNotNull(path) { "stencil-cover dump requires a path descriptor" }
            val plan = selectedRoute.stencilPlan
            listOf(
                "geometry:stencil-cover.candidate row=gpu-renderer.path.stencil-cover " +
                    "routeKind=GPUNative classification=TargetNative promoted=false",
                "path:descriptor key=${pathDescriptor.pathKey} verbs=${pathDescriptor.verbCount} " +
                    "points=${pathDescriptor.pointCount} fillRule=${pathDescriptor.fillRule} " +
                    "inverse=${pathDescriptor.inverseFill} transform=${pathDescriptor.transformClass} " +
                    "edges=${pathDescriptor.edgeCount} finite=${pathDescriptor.finiteProof} " +
                    "volatility=${pathDescriptor.volatility}",
                "stencil-cover:steps producer=${plan.stencilStepLabel} cover=${plan.coverStepLabel} " +
                    "fillRule=${plan.fillRule} msaa=${plan.requiresMSAA} sampleCount=${plan.sampleCount} " +
                    "depthStencil=${plan.depthStencilFormat} state=${plan.stencilStateLabel}",
                "stencil-cover:ordering atomicGroup=${plan.atomicGroupLabel} token=${plan.orderingToken} " +
                    "sortWindow=${plan.sortWindowPolicy}",
                "stencil-cover:bounds producer=${plan.producerBoundsLabel} cover=${plan.coverBoundsLabel} " +
                    "clearLoadStore=${plan.clearLoadStorePolicy}",
                "stencil-cover:clip state=${plan.clipStateLabel} supported=${plan.clipSupportsStencilCover}",
                "stencil-cover:evidence adapter=${plan.adapterEvidenceLabel ?: "missing"} " +
                    "depthStencil=${plan.depthStencilEvidenceLabel ?: "missing"} " +
                    "samples=${plan.sampleCountEvidenceLabel ?: "missing"} " +
                    "target=${plan.targetEvidenceLabel ?: "missing"} " +
                    "targetSupported=${plan.targetSupportsStencilCover} " +
                    "passResources=${plan.passResourceEvidenceLabel ?: "missing"} " +
                    "readback=${plan.readbackEvidenceLabel ?: "missing"}",
                stencilCoverCandidateNonClaimLine,
            )
        }
        is GPUGeometryRoute.Analytic,
        is GPUGeometryRoute.Tessellation,
        is GPUGeometryRoute.PathAtlas,
        is GPUGeometryRoute.CoverageMask,
        -> listOf(
            "geometry:path-fill.unsupported-dump route=${selectedRoute::class.simpleName}",
            pathFillNonClaimLine,
        )
    }

/**
 * Derives stencil-cover pass materialization preimage from geometry gate evidence.
 *
 * The result names the pass resource and depth/stencil evidence labels only.
 * It does not allocate pass attachments, encode commands, perform readback, or
 * promote stencil-cover product routing.
 */
fun GPUGeometryPlan.toStencilCoverPassMaterializationPreimage(): GPUResourceMaterializationPreimagePlan {
    val pathDescriptor = path
    val planLabel = "stencil-cover:${pathDescriptor?.pathKey?.toMaterializationPreimageLabel() ?: "unknown"}"
    val selectedRoute = route
    if (selectedRoute is GPUGeometryRoute.StencilCover) {
        val stencilPlan = selectedRoute.stencilPlan
        return GPUResourceMaterializationPreimagePlan(
            planLabel = planLabel,
            sourceGate = "gpu-renderer.path.stencil-cover",
            accepted = true,
            resources = listOf(
                GPUMaterializedResourceReference(
                    label = stencilPlan.passResourceEvidenceLabel ?: stencilPlan.atomicGroupLabel,
                    role = GPUMaterializedResourceRole.StencilAttachment,
                    descriptorHash = stencilPlan.depthStencilEvidenceLabel ?: stencilPlan.depthStencilFormat,
                    generation = 0,
                    lifetimeClass = "pass-local",
                    usageLabels = listOf("render_attachment", "stencil_attachment"),
                    evidenceFacts = mapOf(
                        "clip" to stencilPlan.clipStateLabel,
                        "readback" to (stencilPlan.readbackEvidenceLabel ?: "missing"),
                        "sampleCount" to stencilPlan.sampleCount.toString(),
                        "state" to stencilPlan.stencilStateLabel,
                        "target" to (stencilPlan.targetEvidenceLabel ?: "missing"),
                    ),
                ),
            ),
        )
    }

    val refusal = (selectedRoute as? GPUGeometryRoute.Refused)?.diagnostic?.code
        ?: "unsupported.geometry.stencil_cover_preimage_route"
    return GPUResourceMaterializationPreimagePlan(
        planLabel = planLabel,
        sourceGate = "gpu-renderer.path.stencil-cover",
        accepted = false,
        resources = emptyList(),
        refusalCode = refusal,
    )
}

/** Provider input for live bounded stencil-cover materialization. */
data class GPUStencilCoverMaterializationRequest(
    val targetId: String,
    val taskIds: List<String> = emptyList(),
    val resourcePlanLabels: List<String> = emptyList(),
    val geometryPlan: GPUGeometryPlan,
    val passId: String,
    val targetStateHash: String,
    val loadStoreLabel: String,
    val deviceGeneration: Long,
    val expectedResourceGeneration: Long,
    val actualResourceGeneration: Long,
    val availableUsageLabels: Set<String>,
    val attachmentAvailable: Boolean,
    val attachmentByteEstimate: Long,
    val attachmentBudgetBytes: Long,
    val actualBoundsLabel: String,
    val actualDepthStencilFormat: String,
    val actualSampleCount: Int,
    val stencilCompare: String,
    val stencilWriteMask: String,
    val stencilClearValue: Int,
    val producerPipelineLabel: String,
    val coverPipelineLabel: String,
    val producerPacketId: String,
    val coverPacketId: String,
    val producerBeforeCoverOrdering: Boolean,
) {
    internal val dumpTaskIdsSnapshot: List<String> = taskIds.toList()
    internal val dumpResourcePlanLabelsSnapshot: List<String> = resourcePlanLabels.toList()
    internal val dumpAvailableUsageLabelsSnapshot: Set<String> = availableUsageLabels.toSet()

    init {
        require(targetId.isNotBlank()) { "GPUStencilCoverMaterializationRequest.targetId must not be blank" }
        require(passId.isNotBlank()) { "GPUStencilCoverMaterializationRequest.passId must not be blank" }
        require(targetStateHash.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.targetStateHash must not be blank"
        }
        require(loadStoreLabel.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.loadStoreLabel must not be blank"
        }
        require(deviceGeneration >= 0L) {
            "GPUStencilCoverMaterializationRequest.deviceGeneration must be non-negative"
        }
        require(expectedResourceGeneration >= 0L) {
            "GPUStencilCoverMaterializationRequest.expectedResourceGeneration must be non-negative"
        }
        require(actualResourceGeneration >= 0L) {
            "GPUStencilCoverMaterializationRequest.actualResourceGeneration must be non-negative"
        }
        require(attachmentByteEstimate >= 0L) {
            "GPUStencilCoverMaterializationRequest.attachmentByteEstimate must be non-negative"
        }
        require(attachmentBudgetBytes >= 0L) {
            "GPUStencilCoverMaterializationRequest.attachmentBudgetBytes must be non-negative"
        }
        require(actualBoundsLabel.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.actualBoundsLabel must not be blank"
        }
        require(actualDepthStencilFormat.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.actualDepthStencilFormat must not be blank"
        }
        require(actualSampleCount > 0) {
            "GPUStencilCoverMaterializationRequest.actualSampleCount must be positive"
        }
        require(stencilCompare.isNotBlank()) { "GPUStencilCoverMaterializationRequest.stencilCompare must not be blank" }
        require(stencilWriteMask.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.stencilWriteMask must not be blank"
        }
        require(producerPipelineLabel.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.producerPipelineLabel must not be blank"
        }
        require(coverPipelineLabel.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.coverPipelineLabel must not be blank"
        }
        require(producerPacketId.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.producerPacketId must not be blank"
        }
        require(coverPacketId.isNotBlank()) {
            "GPUStencilCoverMaterializationRequest.coverPacketId must not be blank"
        }
        require(producerPacketId != coverPacketId) {
            "GPUStencilCoverMaterializationRequest producer and cover packet ids must be distinct"
        }
        require(taskIds.none { taskId -> taskId.isBlank() }) {
            "GPUStencilCoverMaterializationRequest.taskIds must not contain blank labels"
        }
        require(resourcePlanLabels.none { label -> label.isBlank() }) {
            "GPUStencilCoverMaterializationRequest.resourcePlanLabels must not contain blank labels"
        }
        require(availableUsageLabels.none { label -> label.isBlank() }) {
            "GPUStencilCoverMaterializationRequest.availableUsageLabels must not contain blank labels"
        }
    }
}

/** Live stencil-cover materialization output used by resource and command-stream evidence. */
data class GPUStencilCoverMaterializationResult(
    val resourceDecision: GPUResourceMaterializationDecision,
    val commandStream: GPUPassCommandStream,
    val pathLabel: String,
    val attachmentLabel: String,
    val producerStepLabel: String,
    val coverStepLabel: String,
    val orderingToken: String,
    val clearLoadStorePolicy: String,
    val stencilCompare: String,
    val stencilWriteMask: String,
    val sampleCount: Int,
    val adapterBacked: Boolean = false,
    val productActivation: Boolean = true,
) {
    init {
        require(!adapterBacked) { "GPUStencilCoverMaterializationResult.adapterBacked must stay false" }
    }

    /** Emits deterministic stencil-cover live materialization evidence without support promotion. */
    fun dumpLines(): List<String> {
        val head = if (resourceDecision is GPUResourceMaterializationDecision.Refused) {
            "stencil-cover:materialization.refused row=$STENCIL_COVER_LIVE_ROW " +
                "path=$pathLabel attachment=$attachmentLabel code=${resourceDecision.diagnostic.code} " +
                "adapterBacked=$adapterBacked productActivation=$productActivation"
        } else {
            "stencil-cover:materialization row=$STENCIL_COVER_LIVE_ROW " +
                "path=$pathLabel attachment=$attachmentLabel " +
                "producer=$producerStepLabel cover=$coverStepLabel ordering=$orderingToken " +
                "clear=$clearLoadStorePolicy compare=$stencilCompare writeMask=$stencilWriteMask " +
                "sampleCount=$sampleCount adapterBacked=$adapterBacked productActivation=$productActivation"
        }
        return listOf(head, STENCIL_COVER_LIVE_NONCLAIM_LINE) +
            resourceDecision.dumpLines() +
            commandStream.dumpLines()
    }
}

/** Validates and materializes accepted bounded stencil-cover gate evidence. */
class ValidatingStencilCoverMaterializer {
    /** Materializes stencil attachments and command-stream ordering evidence, or refuses stably. */
    fun materialize(
        request: GPUStencilCoverMaterializationRequest,
        context: GPUTargetPreparationContext,
    ): GPUStencilCoverMaterializationResult {
        val diagnostics = request.materializationDiagnostics(context)
        val pathLabel = request.pathLabel()
        val attachmentLabel = request.attachmentLabel()
        val stencilPlan = request.stencilPlanOrNull()
        val producerStep = stencilPlan?.stencilStepLabel ?: "none"
        val coverStep = stencilPlan?.coverStepLabel ?: "none"
        val orderingToken = stencilPlan?.orderingToken ?: "none"
        val clearPolicy = stencilPlan?.clearLoadStorePolicy ?: "none"
        val sampleCount = stencilPlan?.sampleCount ?: request.actualSampleCount

        if (diagnostics.isNotEmpty()) {
            val decision = GPUResourceMaterializationDecision.Refused(
                diagnostic = diagnostics.first(),
                targetId = context.targetId,
                taskIds = request.dumpTaskIdsSnapshot,
                resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
                diagnostics = diagnostics,
            )
            return GPUStencilCoverMaterializationResult(
                resourceDecision = decision,
                commandStream = request.refusedCommandStream(diagnostics.first().code),
                pathLabel = pathLabel,
                attachmentLabel = attachmentLabel,
                producerStepLabel = producerStep,
                coverStepLabel = coverStep,
                orderingToken = orderingToken,
                clearLoadStorePolicy = clearPolicy,
                stencilCompare = request.stencilCompare,
                stencilWriteMask = request.stencilWriteMask,
                sampleCount = sampleCount,
            )
        }

        val materializedBridge = request.stencilCoverOperandBridge()
        val decision = GPUResourceMaterializationDecision.Materialized(
            resources = listOf(GPUTextureResourceRef("texture-ref:$attachmentLabel")),
            targetId = context.targetId,
            taskIds = request.dumpTaskIdsSnapshot,
            resourcePlanLabels = request.resourcePlanLabelsOrDefault(),
            operandBridge = materializedBridge,
        )
        return GPUStencilCoverMaterializationResult(
            resourceDecision = decision,
            commandStream = request.commandStream(materializedBridge),
            pathLabel = pathLabel,
            attachmentLabel = attachmentLabel,
            producerStepLabel = producerStep,
            coverStepLabel = coverStep,
            orderingToken = orderingToken,
            clearLoadStorePolicy = clearPolicy,
            stencilCompare = request.stencilCompare,
            stencilWriteMask = request.stencilWriteMask,
            sampleCount = sampleCount,
        )
    }
}

private fun GPUStencilCoverMaterializationRequest.materializationDiagnostics(
    context: GPUTargetPreparationContext,
): List<GPUResourceDiagnostic> {
    val attachmentLabel = attachmentLabel()
    val selectedRoute = geometryPlan.route
    if (selectedRoute is GPUGeometryRoute.Refused) {
        return listOf(
            stencilCoverMaterializationDiagnostic(
                code = selectedRoute.diagnostic.code,
                attachmentLabel = attachmentLabel,
                facts = selectedRoute.diagnostic.facts + mapOf("reason" to selectedRoute.diagnostic.code),
            ),
        )
    }
    if (selectedRoute !is GPUGeometryRoute.StencilCover) {
        val code = "unsupported.geometry.stencil_cover_preimage_route"
        return listOf(stencilCoverMaterializationDiagnostic(code = code, attachmentLabel = attachmentLabel))
    }

    val stencilPlan = selectedRoute.stencilPlan
    return buildList {
        if (targetId != context.targetId) {
            add(
                GPUResourceDiagnostic.commandOperandTargetMismatch(
                    resourceLabel = attachmentLabel,
                    requestTargetId = targetId,
                    contextTargetId = context.targetId,
                ),
            )
        }
        if (deviceGeneration != context.deviceGeneration) {
            add(
                GPUResourceDiagnostic.deviceGenerationStale(
                    resourceLabel = attachmentLabel,
                    expectedDeviceGeneration = context.deviceGeneration,
                    actualDeviceGeneration = deviceGeneration,
                ),
            )
        }
        if (!attachmentAvailable) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_unavailable",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf("attachmentAvailable" to "false"),
                ),
            )
        }
        val missingUsage = requiredStencilAttachmentUsageLabels - dumpAvailableUsageLabelsSnapshot
        if (missingUsage.isNotEmpty()) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_pass_resources_missing",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "availableUsageLabels" to dumpAvailableUsageLabelsSnapshot.sorted().joinToString(","),
                        "missingUsageLabels" to missingUsage.sorted().joinToString(","),
                    ),
                ),
            )
        }
        if (attachmentByteEstimate > attachmentBudgetBytes) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_budget_exceeded",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "budgetBytes" to attachmentBudgetBytes.toString(),
                        "requestedBytes" to attachmentByteEstimate.toString(),
                    ),
                ),
            )
        }
        if (actualResourceGeneration != expectedResourceGeneration) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_generation_stale",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualGeneration" to actualResourceGeneration.toString(),
                        "expectedGeneration" to expectedResourceGeneration.toString(),
                    ),
                ),
            )
        }
        if (actualBoundsLabel != stencilPlan.producerBoundsLabel || actualBoundsLabel != stencilPlan.coverBoundsLabel) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_bounds_mismatch",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualBounds" to actualBoundsLabel,
                        "coverBounds" to stencilPlan.coverBoundsLabel,
                        "producerBounds" to stencilPlan.producerBoundsLabel,
                    ),
                ),
            )
        }
        if (actualDepthStencilFormat != stencilPlan.depthStencilFormat) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_depth_stencil_mismatch",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualFormat" to actualDepthStencilFormat,
                        "expectedFormat" to stencilPlan.depthStencilFormat,
                    ),
                ),
            )
        }
        if (actualSampleCount != stencilPlan.sampleCount) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_sample_count_mismatch",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualSampleCount" to actualSampleCount.toString(),
                        "expectedSampleCount" to stencilPlan.sampleCount.toString(),
                    ),
                ),
            )
        }
        if (stencilCompare != supportedStencilCoverCompare) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_compare_mismatch",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualCompare" to stencilCompare,
                        "expectedCompare" to supportedStencilCoverCompare,
                    ),
                ),
            )
        }
        if (stencilWriteMask != supportedStencilCoverWriteMask) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_write_mask_mismatch",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualWriteMask" to stencilWriteMask,
                        "expectedWriteMask" to supportedStencilCoverWriteMask,
                    ),
                ),
            )
        }
        if (stencilClearValue != supportedStencilCoverClearValue) {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_clear_value_mismatch",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "actualClearValue" to stencilClearValue.toString(),
                        "expectedClearValue" to supportedStencilCoverClearValue.toString(),
                    ),
                ),
            )
        }
        if (!producerBeforeCoverOrdering || stencilPlan.orderingToken != "producer-before-cover") {
            add(
                stencilCoverMaterializationDiagnostic(
                    code = "unsupported.geometry.stencil_cover_ordering_illegal",
                    attachmentLabel = attachmentLabel,
                    facts = mapOf(
                        "orderingToken" to stencilPlan.orderingToken,
                        "producerBeforeCoverOrdering" to producerBeforeCoverOrdering.toString(),
                    ),
                ),
            )
        }
    }
}

private fun GPUStencilCoverMaterializationRequest.stencilCoverOperandBridge(): List<GPUMaterializedCommandOperandBinding> {
    val stencilPlan = requireNotNull(stencilPlanOrNull()) {
        "accepted stencil-cover materialization requires a stencil plan"
    }
    val attachmentLabel = attachmentLabel()
    val descriptorHash = depthStencilDescriptorHash()
    val textureOperand = GPUMaterializedCommandOperandReference(
        label = attachmentLabel,
        kind = GPUMaterializedCommandOperandKind.Texture,
        descriptorHash = descriptorHash,
        deviceGeneration = deviceGeneration,
        ownerScope = "GPURecorderScope",
        usageLabels = requiredStencilAttachmentUsageLabels.sorted(),
        invalidationPolicy = "pass-end",
        evidenceFacts = mapOf(
            "allocation" to "create-depth-stencil-attachment",
            "bounds" to actualBoundsLabel,
            "bytes" to attachmentByteEstimate.toString(),
            "format" to actualDepthStencilFormat,
            "lifetime" to "pass-local",
            "readback" to (stencilPlan.readbackEvidenceLabel ?: "missing"),
            "sampleCount" to actualSampleCount.toString(),
        ),
    )
    val depthStencilOperand = GPUMaterializedCommandOperandReference(
        label = "depth-stencil:$attachmentLabel",
        kind = GPUMaterializedCommandOperandKind.DepthStencilAttachment,
        descriptorHash = descriptorHash,
        deviceGeneration = deviceGeneration,
        ownerScope = attachmentLabel,
        usageLabels = requiredStencilAttachmentUsageLabels.sorted(),
        invalidationPolicy = "pass-end",
        evidenceFacts = mapOf(
            "clear" to stencilClearValue.toString(),
            "clearLoadStore" to stencilPlan.clearLoadStorePolicy,
            "compare" to stencilCompare,
            "format" to actualDepthStencilFormat,
            "load" to "clear",
            "sampleCount" to actualSampleCount.toString(),
            "store" to "store",
            "writeMask" to stencilWriteMask,
        ),
    )
    val producerPipelineOperand = GPUMaterializedCommandOperandReference(
        label = producerPipelineLabel,
        kind = GPUMaterializedCommandOperandKind.RenderPipeline,
        descriptorHash = producerPipelineDescriptorHash(stencilPlan),
        deviceGeneration = deviceGeneration,
        ownerScope = "pipeline-cache",
        usageLabels = listOf("render_pipeline"),
        invalidationPolicy = "pipeline-cache",
        evidenceFacts = mapOf(
            "attachment" to attachmentLabel,
            "depthStencil" to descriptorHash,
            "sampleCount" to actualSampleCount.toString(),
            "state" to stencilPlan.stencilStateLabel,
            "step" to stencilPlan.stencilStepLabel,
            "token" to stencilPlan.orderingToken,
        ),
    )
    val coverPipelineOperand = GPUMaterializedCommandOperandReference(
        label = coverPipelineLabel,
        kind = GPUMaterializedCommandOperandKind.RenderPipeline,
        descriptorHash = coverPipelineDescriptorHash(stencilPlan),
        deviceGeneration = deviceGeneration,
        ownerScope = "pipeline-cache",
        usageLabels = listOf("render_pipeline"),
        invalidationPolicy = "pipeline-cache",
        evidenceFacts = mapOf(
            "attachment" to attachmentLabel,
            "compare" to stencilCompare,
            "depthStencil" to descriptorHash,
            "sampleCount" to actualSampleCount.toString(),
            "step" to stencilPlan.coverStepLabel,
            "token" to stencilPlan.orderingToken,
            "writeMask" to stencilWriteMask,
        ),
    )
    return listOf(
        GPUMaterializedCommandOperandBinding(
            commandLabel = "prepareStencilAttachment",
            operand = textureOperand,
        ),
        GPUMaterializedCommandOperandBinding(
            commandLabel = "clearStencilAttachment",
            operand = depthStencilOperand,
        ),
        GPUMaterializedCommandOperandBinding(
            packetId = producerPacketId,
            commandLabel = "stencilCoverProducer",
            operand = producerPipelineOperand,
        ),
        GPUMaterializedCommandOperandBinding(
            packetId = producerPacketId,
            commandLabel = "stencilCoverProducer",
            operand = depthStencilOperand,
        ),
        GPUMaterializedCommandOperandBinding(
            packetId = coverPacketId,
            commandLabel = "stencilCoverDraw",
            operand = coverPipelineOperand,
        ),
        GPUMaterializedCommandOperandBinding(
            packetId = coverPacketId,
            commandLabel = "stencilCoverDraw",
            operand = depthStencilOperand,
        ),
    )
}

private fun GPUStencilCoverMaterializationRequest.commandStream(
    materializedBridge: List<GPUMaterializedCommandOperandBinding>,
): GPUPassCommandStream {
    val stencilPlan = requireNotNull(stencilPlanOrNull()) {
        "accepted stencil-cover command stream requires a stencil plan"
    }
    return GPUPassCommandStream(
        streamId = "stencil-cover-command-stream:${pathLabel().toMaterializationPreimageLabel()}",
        packetStreamId = "stencil-cover-packet-stream:${pathLabel().toMaterializationPreimageLabel()}",
        passId = passId,
        commands = listOf(
            GPUPassCommand.PrepareStencilAttachment(
                attachmentLabel = attachmentLabel(),
                descriptorHash = depthStencilDescriptorHash(),
                formatLabel = actualDepthStencilFormat,
                usageLabel = requiredStencilAttachmentUsageLabels.sorted().joinToString(","),
                sampleCount = actualSampleCount,
                byteEstimate = attachmentByteEstimate,
            ),
            GPUPassCommand.BeginRenderPass(
                targetStateHash = targetStateHash,
                loadStoreLabel = loadStoreLabel,
            ),
            GPUPassCommand.ClearStencilAttachment(
                attachmentLabel = attachmentLabel(),
                clearValue = stencilClearValue,
                loadStorePolicy = stencilPlan.clearLoadStorePolicy,
            ),
            GPUPassCommand.StencilCoverProducer(
                attachmentLabel = attachmentLabel(),
                pipelineLabel = producerPipelineLabel,
                boundsLabel = stencilPlan.producerBoundsLabel,
                stencilStateLabel = stencilPlan.stencilStateLabel,
                tokenLabel = stencilPlan.orderingToken,
                packetId = GPUDrawPacketID(producerPacketId),
            ),
            GPUPassCommand.StencilCoverDraw(
                attachmentLabel = attachmentLabel(),
                pipelineLabel = coverPipelineLabel,
                boundsLabel = stencilPlan.coverBoundsLabel,
                compareLabel = stencilCompare,
                writeMaskLabel = stencilWriteMask,
                tokenLabel = stencilPlan.orderingToken,
                packetId = GPUDrawPacketID(coverPacketId),
            ),
            GPUPassCommand.EndRenderPass(passId = passId),
        ),
        operandBridge = materializedBridge.map(GPUPassCommandOperandBridge::fromMaterializedBinding),
    )
}

private fun GPUStencilCoverMaterializationRequest.refusedCommandStream(reasonCode: String): GPUPassCommandStream =
    GPUPassCommandStream(
        streamId = "stencil-cover-command-stream:${pathLabel().toMaterializationPreimageLabel()}",
        packetStreamId = "stencil-cover-packet-stream:${pathLabel().toMaterializationPreimageLabel()}",
        passId = passId,
        commands = listOf(GPUPassCommand.RefuseStencilCover(pathLabel = pathLabel(), reasonCode = reasonCode)),
    )

private fun GPUStencilCoverMaterializationRequest.stencilPlanOrNull(): GPUStencilCoverPlan? =
    (geometryPlan.route as? GPUGeometryRoute.StencilCover)?.stencilPlan

private fun GPUStencilCoverMaterializationRequest.pathLabel(): String =
    geometryPlan.path?.pathKey ?: "unknown"

private fun GPUStencilCoverMaterializationRequest.attachmentLabel(): String =
    stencilPlanOrNull()?.passResourceEvidenceLabel ?: geometryPlan.path?.pathKey?.stencilAttachmentFallbackLabel()
        ?: "stencil-attachment:unknown"

private fun String.stencilAttachmentFallbackLabel(): String =
    "stencil-attachment:${removePrefix("path:").substringBefore(':').sanitizeForArtifactKey().ifBlank { "unknown" }}"

private fun GPUStencilCoverMaterializationRequest.depthStencilDescriptorHash(): String =
    stencilPlanOrNull()?.depthStencilEvidenceLabel ?: actualDepthStencilFormat

private fun GPUStencilCoverMaterializationRequest.producerPipelineDescriptorHash(
    stencilPlan: GPUStencilCoverPlan,
): String =
    listOf(
        "stencil-producer-pipeline",
        producerPipelineLabel,
        stencilPlan.stencilStepLabel,
        stencilPlan.stencilStateLabel,
        targetStateHash,
        depthStencilDescriptorHash(),
    ).joinToString(":")

private fun GPUStencilCoverMaterializationRequest.coverPipelineDescriptorHash(
    stencilPlan: GPUStencilCoverPlan,
): String =
    listOf(
        "stencil-cover-pipeline",
        coverPipelineLabel,
        stencilPlan.coverStepLabel,
        stencilCompare,
        stencilWriteMask,
        targetStateHash,
        depthStencilDescriptorHash(),
    ).joinToString(":")

private fun GPUStencilCoverMaterializationRequest.resourcePlanLabelsOrDefault(): List<String> =
    if (dumpResourcePlanLabelsSnapshot.isEmpty()) {
        listOf("stencil-cover-materialization")
    } else {
        dumpResourcePlanLabelsSnapshot
    }

private fun stencilCoverMaterializationDiagnostic(
    code: String,
    attachmentLabel: String,
    facts: Map<String, String> = mapOf("reason" to code),
): GPUResourceDiagnostic =
    GPUResourceDiagnostic(
        code = code,
        resourceLabel = attachmentLabel,
        message = "Stencil-cover materialization for $attachmentLabel refused: $code.",
        terminal = true,
        facts = facts,
    )

private val requiredStencilAttachmentUsageLabels = setOf("render_attachment", "stencil_attachment")

private const val supportedStencilCoverCompare = "equal"

private const val supportedStencilCoverWriteMask = "0xff"

private const val supportedStencilCoverClearValue = 0

private const val STENCIL_COVER_LIVE_ROW = "gpu-renderer.path.stencil-cover.live"

private const val STENCIL_COVER_LIVE_NONCLAIM_LINE =
    "stencil-cover:nonclaim nativeStencilCover=false adapterBacked=false productActivation=true " +
        "gpuReadbackCompleted=false cpuFallback=false broadPathAA=false"

private fun GPUShapeDescriptor.refusalCode(): String? =
    when {
        shapeKind != "path-fill" -> "unsupported.geometry.shape_kind"
        boundsLabel.isBlank() -> "unsupported.bounds.path"
        antiAliasMode !in setOf("coverage-aa", "none") -> "unsupported.path.aa_mode"
        else -> null
    }

private fun GPUPathDescriptor.refusalCode(maxEdges: Int): String? =
    when {
        !pathKey.isCanonicalPathKey() -> "unsupported.path.noncanonical_key"
        verbCount <= 0 || pointCount <= 0 -> "unsupported.path.empty"
        fillRule !in setOf("NonZero", "EvenOdd", "InverseWinding", "InverseEvenOdd") -> "unsupported.path.fill_rule"
        transformClass == "perspective" -> "unsupported.transform.path_perspective"
        transformClass !in setOf("identity", "translate") -> "unsupported.transform.path_class"
        edgeCount < 0 || edgeCount > maxEdges -> "unsupported.path.edge_budget"
        finiteProof != "finite" -> "unsupported.bounds.path"
        volatility != "immutable" -> "unsupported.path.volatile"
        else -> null
    }

private fun GPUPathDescriptor.preparedArtifactKey(): String =
    "prepared.path-fill.${pathKey.sanitizeForArtifactKey()}.${fillRule.lowercase()}.${transformClass}.edges$edgeCount"

private fun String.isCanonicalPathKey(): Boolean =
    startsWith("path:") &&
        isNotBlank() &&
        !contains("handle", ignoreCase = true) &&
        !contains("pointer", ignoreCase = true) &&
        !contains("0x", ignoreCase = true)

private fun String.sanitizeForArtifactKey(): String =
    map { char ->
        when {
            char.isLetterOrDigit() -> char
            else -> '_'
        }
    }.joinToString("")
        .trim('_')

private fun String.toMaterializationPreimageLabel(): String =
    replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifBlank { "unknown" }

private const val pathFillNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-path-aa"

internal fun GPUShapeDescriptor.strokeRefusalCode(): String? =
    when {
        shapeKind != "path-stroke" -> "unsupported.geometry.shape_kind"
        boundsLabel.isBlank() -> "unsupported.geometry.path_nonfinite"
        antiAliasMode !in setOf("coverage-aa", "none") -> "unsupported.stroke.aa_mode"
        else -> null
    }

private fun GPUShapeDescriptor.drawPointsRefusalCode(): String? =
    when {
        shapeKind != "draw-points" -> "unsupported.geometry.shape_kind"
        boundsLabel.isBlank() -> "unsupported.bounds.draw_points"
        antiAliasMode !in setOf("coverage-aa", "none") -> "unsupported.draw_points.aa_mode"
        else -> null
    }

internal fun GPUPathDescriptor.strokePathRefusalCode(): String? =
    when {
        !pathKey.isCanonicalPathKey() -> "unsupported.geometry.path_key_nondeterministic"
        verbCount <= 0 || pointCount <= 0 -> "unsupported.geometry.descriptor_invalid"
        finiteProof != "finite" -> "unsupported.geometry.path_nonfinite"
        volatility != "immutable" -> "unsupported.geometry.path_mutable"
        inverseFill -> "unsupported.geometry.path_empty_inverse_unbounded"
        else -> null
    }

internal fun GPUStrokeDescriptor.refusalCode(maxEdges: Int): String? =
    when {
        !finiteWidth || !width.isFinite() || width <= 0f -> "unsupported.stroke.width_invalid"
        hairline -> "unsupported.stroke.hairline_policy"
        cap != "Butt" -> "unsupported.stroke.cap"
        join != "Miter" -> "unsupported.stroke.join"
        miter < 1f -> "unsupported.stroke.miter_limit"
        dashOrPathEffectRef != null -> {
            val ref = dashOrPathEffectRef
            if (ref.startsWith("dash:")) {
                val elementCount = ref.removePrefix("dash:").count { it == ',' } + 1
                if (elementCount > 4) "unsupported.stroke.dash_complex"
                else null
            } else "unsupported.stroke.path_effect_unregistered"
        }
        transformClass == "nonuniform" -> "unsupported.stroke.nonuniform_transform"
        transformClass !in setOf("identity", "translate") -> "unsupported.geometry.perspective_path"
        edgeCount < 0 || edgeCount > maxEdges -> "unsupported.stroke.expansion_budget_exceeded"
        else -> null
    }

private fun GPUStrokeDescriptor.preparedStrokeDescriptorHash(path: GPUPathDescriptor): String =
    "stroke.${path.pathKey.sanitizeForArtifactKey()}.width${width.stableLabel()}.${cap.lowercase()}." +
        "${join.lowercase()}${miter.stableLabel()}.$transformClass.edges$edgeCount"

private fun GPUDrawPointsDescriptor.refusalCode(): String? =
    when {
        pointMode !in setOf("Points", "Lines", "Polygon") -> "unsupported.draw_points.mode"
        pointCount <= 0 -> "unsupported.draw_points.empty"
        !strokeWidth.isFinite() || strokeWidth < 0f -> "unsupported.draw_points.stroke_width"
        strokeCap !in setOf("Butt", "Round", "Square") -> "unsupported.draw_points.cap"
        transformClass !in setOf("identity", "translate", "scale", "affine") -> "unsupported.draw_points.transform"
        finiteProof != "finite" -> "unsupported.bounds.draw_points"
        localMatrixHash != null && !localMatrixHash.isStableDrawPointsEvidenceKey() ->
            "unsupported.draw_points.local_matrix_key"
        else -> null
    }

private fun GPUDrawPointsDescriptor.preparedArtifactKey(): String =
    "prepared.draw-points.${pointMode.lowercase()}.count$pointCount.width${strokeWidth.stableLabel()}." +
        "${strokeCap.lowercase()}.$transformClass.lm_${(localMatrixHash ?: "none").sanitizeForArtifactKey()}"

private fun GPUDrawPointsDescriptor.consumerKind(): String =
    when (pointMode) {
        "Lines" -> "draw-points-line-strip.render-step"
        "Polygon" -> "draw-points-polyline.render-step"
        else -> "draw-points-sprites.render-step"
    }

private fun GPUDrawPointsDescriptor.stableFacts(): Map<String, String> =
    mapOf(
        "pointMode" to pointMode,
        "pointCount" to pointCount.toString(),
        "strokeWidth" to strokeWidth.stableLabel(),
        "strokeCap" to strokeCap,
        "transformClass" to transformClass,
        "finiteProof" to finiteProof,
        "localMatrix" to stableLocalMatrixEvidenceLabel(),
    )

private fun GPUDrawPointsDescriptor.stableLocalMatrixEvidenceLabel(): String =
    when {
        localMatrixHash == null -> "none"
        localMatrixHash.isStableDrawPointsEvidenceKey() -> localMatrixHash
        else -> "invalid"
    }

private fun Float.stableLabel(): String =
    if (this % 1f == 0f) {
        toInt().toString()
    } else {
        toString().replace('.', '_')
    }

private const val strokeNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback " +
        "no-broad-stroke-parity no-hairline no-dash no-round-cap-join"

private fun String.isStableDrawPointsEvidenceKey(): Boolean =
    isNotBlank() &&
        length <= maxDrawPointsEvidenceKeyLength &&
        all { char ->
            char.isAsciiLetterOrDigit() ||
                char == '.' ||
                char == '_' ||
                char == '-'
        }

private fun Char.isAsciiLetterOrDigit(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

private const val maxDrawPointsEvidenceKeyLength = 64

private const val drawPointsNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback " +
        "no-broad-draw-points-parity no-local-matrix-runtime-compilation"

private fun GPUShapeDescriptor.stencilCoverRefusalCode(): String? =
    when {
        shapeKind != "path-fill" -> "unsupported.geometry.shape_kind"
        boundsLabel.isBlank() -> "unsupported.bounds.path"
        antiAliasMode !in setOf("coverage-aa", "none") -> "unsupported.path.aa_mode"
        else -> null
    }

private fun GPUPathDescriptor.stencilCoverPathRefusalCode(maxEdges: Int): String? =
    when {
        !pathKey.isCanonicalPathKey() -> "unsupported.geometry.path_key_nondeterministic"
        verbCount <= 0 || pointCount <= 0 -> "unsupported.geometry.descriptor_invalid"
        fillRule !in setOf("NonZero", "EvenOdd", "InverseWinding", "InverseEvenOdd") -> "unsupported.geometry.path_fill_rule"
        transformClass == "perspective" -> "unsupported.geometry.perspective_path"
        transformClass !in setOf("identity", "translate") -> "unsupported.transform.path_class"
        edgeCount < 0 || edgeCount > maxEdges -> "unsupported.geometry.path_edge_budget_exceeded"
        finiteProof != "finite" -> "unsupported.geometry.path_nonfinite"
        volatility != "immutable" -> "unsupported.geometry.path_mutable"
        else -> null
    }

private fun GPUStencilCoverEvidence.refusalCode(): String? =
    when {
        adapterEvidenceLabel.isNullOrBlank() -> "unsupported.geometry.stencil_cover_unavailable"
        !depthStencilCapability -> "unsupported.geometry.stencil_cover_unavailable"
        depthStencilEvidenceLabel.isNullOrBlank() -> "unsupported.geometry.stencil_cover_unavailable"
        sampleCount <= 0 -> "unsupported.geometry.stencil_cover_unavailable"
        sampleCountEvidenceLabel.isNullOrBlank() -> "unsupported.geometry.stencil_cover_unavailable"
        targetStateLabel.isBlank() -> "unsupported.geometry.stencil_cover_target"
        targetEvidenceLabel.isNullOrBlank() -> "unsupported.geometry.stencil_cover_target"
        !targetSupportsStencilCover -> "unsupported.geometry.stencil_cover_target"
        stencilStateLabel.isBlank() -> "unsupported.geometry.stencil_cover_unavailable"
        clipStateLabel.isBlank() -> "unsupported.clip.stencil_cover"
        !clipSupportsStencilCover -> "unsupported.clip.stencil_cover"
        !producerBeforeCoverOrdering -> "unsupported.geometry.stencil_cover_ordering_illegal"
        passResourceEvidenceLabel.isNullOrBlank() -> "unsupported.geometry.stencil_cover_pass_resources_missing"
        readbackEvidenceLabel.isNullOrBlank() -> "unsupported.execution.readback_unavailable"
        else -> null
    }

private fun GPUStencilCoverEvidence.dumpFacts(): Map<String, String> =
    mapOf(
        "adapterEvidenceLabel" to (adapterEvidenceLabel ?: "missing"),
        "depthStencilEvidenceLabel" to (depthStencilEvidenceLabel ?: "missing"),
        "sampleCountEvidenceLabel" to (sampleCountEvidenceLabel ?: "missing"),
        "targetEvidenceLabel" to (targetEvidenceLabel ?: "missing"),
        "targetSupportsStencilCover" to targetSupportsStencilCover.toString(),
        "stencilStateLabel" to stencilStateLabel.ifBlank { "missing" },
        "clipStateLabel" to clipStateLabel.ifBlank { "missing" },
        "clipSupportsStencilCover" to clipSupportsStencilCover.toString(),
        "passResourceEvidenceLabel" to (passResourceEvidenceLabel ?: "missing"),
        "readbackEvidenceLabel" to (readbackEvidenceLabel ?: "missing"),
        "producerBeforeCoverOrdering" to producerBeforeCoverOrdering.toString(),
    )

private fun GPUGeometryDiagnostic.stencilCoverSkippedLine(): String =
    "stencil-cover:skipped adapter=${facts["adapterEvidenceLabel"] ?: "missing"} " +
        "depthStencil=${facts["depthStencilEvidenceLabel"] ?: "missing"} " +
        "samples=${facts["sampleCountEvidenceLabel"] ?: "missing"} " +
        "target=${facts["targetEvidenceLabel"] ?: "missing"} " +
        "targetSupported=${facts["targetSupportsStencilCover"] ?: "false"} " +
        "stencilState=${facts["stencilStateLabel"] ?: "missing"} " +
        "clip=${facts["clipStateLabel"] ?: "missing"} " +
        "clipSupported=${facts["clipSupportsStencilCover"] ?: "false"} " +
        "passResources=${facts["passResourceEvidenceLabel"] ?: "missing"} " +
        "readback=${facts["readbackEvidenceLabel"] ?: "missing"} " +
        "ordering=${facts["producerBeforeCoverOrdering"] ?: "false"}"

private const val stencilCoverCandidateNonClaimLine =
    "nonclaim:no-product-activation no-release-blocking-gate no-broad-path-aa no-graphite-port " +
        "no-cpu-prepared-continuation-as-support"

private const val stencilCoverRefusalNonClaimLine =
    "nonclaim:no-native-stencil-cover-support no-product-activation no-release-blocking-gate " +
        "no-cpu-prepared-continuation-as-support no-refusal-only-selector-as-support"

private fun String.isCanonicalAtlasContentKey(): Boolean =
    isNotBlank() &&
        contains(":") &&
        !contains("handle", ignoreCase = true) &&
        !contains("pointer", ignoreCase = true) &&
        !contains("0x", ignoreCase = true)

private const val atlasPolicyNonClaimLine =
    "atlas-policy:nonclaim no-atlas-generation no-path-atlas-support no-coverage-atlas-support " +
        "no-selector-only-support no-hidden-cpu-texture-fallback"
