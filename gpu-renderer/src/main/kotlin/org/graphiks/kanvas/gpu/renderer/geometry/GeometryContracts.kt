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
    val transformClass: String = "identity",
    val finiteWidth: Boolean = true,
    val hairline: Boolean = false,
    val edgeCount: Int = 0,
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
            if (stroke != null) {
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

private fun GPUShapeDescriptor.strokeRefusalCode(): String? =
    when {
        shapeKind != "path-stroke" -> "unsupported.geometry.shape_kind"
        boundsLabel.isBlank() -> "unsupported.geometry.path_nonfinite"
        antiAliasMode !in setOf("coverage-aa", "none") -> "unsupported.stroke.aa_mode"
        else -> null
    }

private fun GPUPathDescriptor.strokePathRefusalCode(): String? =
    when {
        !pathKey.isCanonicalPathKey() -> "unsupported.geometry.path_key_nondeterministic"
        verbCount <= 0 || pointCount <= 0 -> "unsupported.geometry.descriptor_invalid"
        finiteProof != "finite" -> "unsupported.geometry.path_nonfinite"
        volatility != "immutable" -> "unsupported.geometry.path_mutable"
        inverseFill -> "unsupported.geometry.path_empty_inverse_unbounded"
        else -> null
    }

private fun GPUStrokeDescriptor.refusalCode(maxEdges: Int): String? =
    when {
        !finiteWidth || !width.isFinite() || width <= 0f -> "unsupported.stroke.width_invalid"
        hairline -> "unsupported.stroke.hairline_policy"
        cap != "Butt" -> "unsupported.stroke.cap"
        join != "Miter" -> "unsupported.stroke.join"
        miter < 1f -> "unsupported.stroke.miter_limit"
        dashOrPathEffectRef?.startsWith("dash:") == true -> "unsupported.stroke.dash_complex"
        dashOrPathEffectRef != null -> "unsupported.stroke.path_effect_unregistered"
        transformClass == "nonuniform" -> "unsupported.stroke.nonuniform_transform"
        transformClass !in setOf("identity", "translate") -> "unsupported.geometry.perspective_path"
        edgeCount < 0 || edgeCount > maxEdges -> "unsupported.stroke.expansion_budget_exceeded"
        else -> null
    }

private fun GPUStrokeDescriptor.preparedStrokeDescriptorHash(path: GPUPathDescriptor): String =
    "stroke.${path.pathKey.sanitizeForArtifactKey()}.width${width.stableLabel()}.${cap.lowercase()}." +
        "${join.lowercase()}${miter.stableLabel()}.$transformClass.edges$edgeCount"

private fun Float.stableLabel(): String =
    if (this % 1f == 0f) {
        toInt().toString()
    } else {
        toString().replace('.', '_')
    }

private const val strokeNonClaimLine =
    "nonclaim:no-product-activation no-adapter-backed-execution no-hidden-cpu-texture-fallback " +
        "no-broad-stroke-parity no-hairline no-dash no-round-cap-join"

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
        fillRule !in setOf("NonZero", "EvenOdd") -> "unsupported.geometry.path_fill_rule"
        inverseFill -> "unsupported.geometry.path_empty_inverse_unbounded"
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
