package org.graphiks.kanvas.gpu.renderer.geometry

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

/** Keeps path and coverage atlas evidence fail-closed until policy gates land. */
class GPUAtlasPolicyRefusalGate {
    /** Evaluates a path or coverage atlas request as a required refusal. */
    fun evaluate(request: GPUAtlasPolicyRequest): GPUAtlasPolicyRefusal {
        val requiredFacts = request.requiredFacts()
        val missingFacts = request.missingPolicyFacts(requiredFacts)
        val diagnosticCode = request.refusalCode(missingFacts)
        val effectiveMissingFacts = when (diagnosticCode) {
            "unsupported.atlas.key_nondeterministic" -> listOf("content-key")
            "unsupported.coverage.mask_bounds_invalid" -> listOf("bounds-proof")
            else -> missingFacts
        }

        return GPUAtlasPolicyRefusal(
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

    private fun GPUAtlasPolicyRequest.refusalCode(missingFacts: List<String>): String =
        when {
            !contentKeyLabel.isCanonicalAtlasContentKey() -> "unsupported.atlas.key_nondeterministic"
            boundsLabel.isBlank() -> "unsupported.coverage.mask_bounds_invalid"
            selectorEvidenceOnly -> "unsupported.atlas.policy_unavailable"
            missingFacts.any { it in atlasPolicyFacts } -> "unsupported.atlas.policy_unavailable"
            missingFacts.any { it in atlasSyncFacts } -> "unsupported.atlas.sync_unavailable"
            else -> "unsupported.atlas.policy_unavailable"
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

    private companion object {
        const val pathFillConsumerKind = "coverage-mask.sample.path-fill"
        val pathFillInvalidationFacts = listOf("path-content-hash", "fill-rule", "transform-class", "bounds-proof")
    }
}

/** Emits stable M3 path-fill evidence lines for reports and tests. */
fun GPUGeometryPlan.dumpLines(): List<String> =
    when (val selectedRoute = route) {
        is GPUGeometryRoute.Prepared -> {
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
        is GPUGeometryRoute.Refused -> listOf(
            "geometry:path-fill.refused reason=${selectedRoute.diagnostic.code}",
            pathFillNonClaimLine,
        )
        is GPUGeometryRoute.Analytic,
        is GPUGeometryRoute.Tessellation,
        is GPUGeometryRoute.StencilCover,
        is GPUGeometryRoute.PathAtlas,
        is GPUGeometryRoute.CoverageMask,
        -> listOf(
            "geometry:path-fill.unsupported-dump route=${selectedRoute::class.simpleName}",
            pathFillNonClaimLine,
        )
    }

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
        fillRule !in setOf("NonZero", "EvenOdd") -> "unsupported.path.fill_rule"
        inverseFill -> "unsupported.path.inverse_fill"
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

private const val pathFillNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback no-broad-path-aa"

private fun String.isCanonicalAtlasContentKey(): Boolean =
    isNotBlank() &&
        contains(":") &&
        !contains("handle", ignoreCase = true) &&
        !contains("pointer", ignoreCase = true) &&
        !contains("0x", ignoreCase = true)

private const val atlasPolicyNonClaimLine =
    "atlas-policy:nonclaim no-atlas-generation no-path-atlas-support no-coverage-atlas-support " +
        "no-selector-only-support no-hidden-cpu-texture-fallback"
