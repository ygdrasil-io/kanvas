package org.skia.gpu.webgpu

import org.skia.pipeline.BackendKind
import org.skia.pipeline.ClipInteraction
import org.skia.pipeline.CoverageLoweringResult
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.CoveragePlanAdapter
import org.skia.pipeline.DiagnosticReason
import org.skia.pipeline.FallbackPlan
import org.skia.pipeline.FloatRect
import org.skia.pipeline.PathFillType
import org.skia.pipeline.StandardCoverageReason

public const val WEBGPU_PATH_AA_EDGE_BUDGET: Int = 256
public const val WEBGPU_PATH_AA_VERB_BUDGET: Int = 96
public const val WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET: Int = 16
public const val WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET: Int = 8
public const val WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET: Int = 4
public const val WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET: Float = 2048f

public enum class WebGpuCoverageStrategy {
    AnalyticRect,
    AnalyticRRect,
    CpuPreparedConvexFan,
    StencilCover,
    PathAaStrokePrimitive,
    CoverageMaskOrAtlasFallback,
    ExistingGpuCompatibility,
    RefuseDiagnostic,
}

public enum class WebGpuCoverageEvidenceStatus(public val wireName: String) {
    AdapterPass("adapter-pass"),
    AdapterFail("adapter-fail"),
    AdapterSkipped("adapter-skipped"),
    AdapterTimeout("adapter-timeout"),
    Proven("proven"),
    Refused("refused"),
    Compatibility("compatibility"),
    BlockedNoAdapterLane("blocked-no-adapter-lane"),
}

public enum class CiAdapterLaneStatus(public val wireName: String) {
    AdapterPass("adapter-pass"),
    AdapterFail("adapter-fail"),
    AdapterSkipped("adapter-skipped"),
    AdapterTimeout("adapter-timeout"),
    BlockedNoAdapterLane("blocked-no-adapter-lane"),
}

public data class WebGpuCoverageStrategyInventoryRow(
    val branch: String,
    val strategy: WebGpuCoverageStrategy,
    val status: WebGpuCoverageEvidenceStatus,
    val routeIdentifier: String,
    val diagnosticReason: DiagnosticReason?,
    val evidence: String,
    val unblockCondition: String?,
) {
    public fun dump(): String =
        "branch=$branch;strategy=$strategy;status=${status.wireName};route=$routeIdentifier;" +
            "reason=${diagnosticReason?.code ?: "none"};evidence=$evidence;" +
            "unblock=${unblockCondition ?: "none"}"
}

public object WebGpuCoverageStrategyInventory {
    public const val requiredCiAdapterJobName: String = "GPU tests (macos)"
    public val defaultCiAdapterLaneStatus: CiAdapterLaneStatus = CiAdapterLaneStatus.AdapterPass
    public val ciAdapterLaneAvailable: Boolean
        get() = defaultCiAdapterLaneStatus != CiAdapterLaneStatus.BlockedNoAdapterLane

    private const val ADAPTER_UNBLOCK: String =
        "Enable a non-skippable GPU adapter CI/scheduled lane that uploads gpu-raster artifacts and fails on adapter skips."

    private fun promotedStatus(ciAdapterLaneStatus: CiAdapterLaneStatus): WebGpuCoverageEvidenceStatus = when (ciAdapterLaneStatus) {
        CiAdapterLaneStatus.AdapterPass -> WebGpuCoverageEvidenceStatus.AdapterPass
        CiAdapterLaneStatus.AdapterFail -> WebGpuCoverageEvidenceStatus.AdapterFail
        CiAdapterLaneStatus.AdapterSkipped -> WebGpuCoverageEvidenceStatus.AdapterSkipped
        CiAdapterLaneStatus.AdapterTimeout -> WebGpuCoverageEvidenceStatus.AdapterTimeout
        CiAdapterLaneStatus.BlockedNoAdapterLane -> WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane
    }

    private fun promotedEvidence(branchEvidence: String, ciAdapterLaneStatus: CiAdapterLaneStatus): String = when (ciAdapterLaneStatus) {
        CiAdapterLaneStatus.AdapterPass ->
            "$branchEvidence; required `$requiredCiAdapterJobName` lane reports adapter-pass"
        CiAdapterLaneStatus.AdapterFail ->
            "$branchEvidence; required `$requiredCiAdapterJobName` lane reports adapter-fail"
        CiAdapterLaneStatus.AdapterSkipped ->
            "$branchEvidence; required `$requiredCiAdapterJobName` lane reports adapter-skipped"
        CiAdapterLaneStatus.AdapterTimeout ->
            "$branchEvidence; required `$requiredCiAdapterJobName` lane reports adapter-timeout"
        CiAdapterLaneStatus.BlockedNoAdapterLane ->
            "$branchEvidence; required `$requiredCiAdapterJobName` lane is unavailable"
    }

    private fun promotedUnblockCondition(ciAdapterLaneStatus: CiAdapterLaneStatus): String? = when (ciAdapterLaneStatus) {
        CiAdapterLaneStatus.AdapterPass -> null
        CiAdapterLaneStatus.AdapterFail ->
            "Fix adapter-lane failures in `$requiredCiAdapterJobName` before release promotion."
        CiAdapterLaneStatus.AdapterSkipped ->
            "Remove adapter skips in `$requiredCiAdapterJobName`; smoke must fail closed on skipped adapter-dependent tests."
        CiAdapterLaneStatus.AdapterTimeout ->
            "Stabilize `$requiredCiAdapterJobName` until it completes with adapter-backed results and artifacts."
        CiAdapterLaneStatus.BlockedNoAdapterLane -> ADAPTER_UNBLOCK
    }

    public fun rowsForCiAdapterStatus(
        ciAdapterLaneStatus: CiAdapterLaneStatus,
    ): List<WebGpuCoverageStrategyInventoryRow> = listOf(
        WebGpuCoverageStrategyInventoryRow(
            branch = "analytic-rect",
            strategy = WebGpuCoverageStrategy.AnalyticRect,
            status = promotedStatus(ciAdapterLaneStatus),
            routeIdentifier = "webgpu.coverage.analytic-rect",
            diagnosticReason = null,
            evidence = promotedEvidence(
                branchEvidence = "selector, pipeline-key, and local adapter rect fixture exist",
                ciAdapterLaneStatus = ciAdapterLaneStatus,
            ),
            unblockCondition = promotedUnblockCondition(ciAdapterLaneStatus),
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "analytic-rrect",
            strategy = WebGpuCoverageStrategy.AnalyticRRect,
            status = promotedStatus(ciAdapterLaneStatus),
            routeIdentifier = "webgpu.coverage.analytic-rrect",
            diagnosticReason = null,
            evidence = promotedEvidence(
                branchEvidence = "selector, pipeline-key, and local adapter rrect fixture exist",
                ciAdapterLaneStatus = ciAdapterLaneStatus,
            ),
            unblockCondition = promotedUnblockCondition(ciAdapterLaneStatus),
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-aa-stroke-primitive",
            strategy = WebGpuCoverageStrategy.PathAaStrokePrimitive,
            status = promotedStatus(ciAdapterLaneStatus),
            routeIdentifier = "webgpu.coverage.path-aa-stroke-primitive",
            diagnosticReason = null,
            evidence = promotedEvidence(
                branchEvidence = "selector and adapter-backed StrokeRectGM/StrokeCircleGM fixtures exist",
                ciAdapterLaneStatus = ciAdapterLaneStatus,
            ),
            unblockCondition = promotedUnblockCondition(ciAdapterLaneStatus),
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-convex-fan",
            strategy = WebGpuCoverageStrategy.CpuPreparedConvexFan,
            status = promotedStatus(ciAdapterLaneStatus),
            routeIdentifier = "webgpu.coverage.path-convex-fan",
            diagnosticReason = null,
            evidence = promotedEvidence(
                branchEvidence = "selector and local adapter convex path fixture exist",
                ciAdapterLaneStatus = ciAdapterLaneStatus,
            ),
            unblockCondition = promotedUnblockCondition(ciAdapterLaneStatus),
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-stencil-cover",
            strategy = WebGpuCoverageStrategy.StencilCover,
            status = promotedStatus(ciAdapterLaneStatus),
            routeIdentifier = "webgpu.coverage.path-stencil-cover",
            diagnosticReason = null,
            evidence = promotedEvidence(
                branchEvidence = "selector and local adapter concave/inverse path fixtures exist",
                ciAdapterLaneStatus = ciAdapterLaneStatus,
            ),
            unblockCondition = promotedUnblockCondition(ciAdapterLaneStatus),
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-mask-or-atlas-selector",
            strategy = WebGpuCoverageStrategy.CoverageMaskOrAtlasFallback,
            status = WebGpuCoverageEvidenceStatus.Proven,
            routeIdentifier = "webgpu.coverage.path-mask-or-atlas",
            diagnosticReason = null,
            evidence = "selector-only proof: edge-overflow path chooses the explicit mask/atlas route when enabled; this is not adapter CI evidence",
            unblockCondition = "Add mask/atlas ownership, cache policy, and adapter-backed rendering evidence before release promotion.",
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "full-scissor",
            strategy = WebGpuCoverageStrategy.ExistingGpuCompatibility,
            status = WebGpuCoverageEvidenceStatus.Compatibility,
            routeIdentifier = "webgpu.coverage.full-scissor",
            diagnosticReason = null,
            evidence = "existing GPU compatibility route, not promoted as new adapter evidence",
            unblockCondition = null,
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "span-runs",
            strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
            status = WebGpuCoverageEvidenceStatus.Refused,
            routeIdentifier = "webgpu.coverage.refuse",
            diagnosticReason = StandardCoverageReason.SpanRunsUnsupported,
            evidence = "WebGPU selector refuses span-run coverage with stable diagnostic",
            unblockCondition = "Add an upload/mask strategy and adapter-backed cross-backend evidence before promotion.",
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "alpha-mask",
            strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
            status = WebGpuCoverageEvidenceStatus.Refused,
            routeIdentifier = "webgpu.coverage.refuse",
            diagnosticReason = StandardCoverageReason.AlphaMaskUnsupported,
            evidence = "WebGPU selector refuses standalone alpha-mask coverage with stable diagnostic",
            unblockCondition = "Define mask upload/sampling ownership and adapter-backed evidence before promotion.",
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "coverage-atlas",
            strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
            status = WebGpuCoverageEvidenceStatus.Refused,
            routeIdentifier = "webgpu.coverage.refuse",
            diagnosticReason = StandardCoverageReason.AtlasPolicyUnavailable,
            evidence = "persistent atlas policy gate reports no-go until profiling and ownership evidence are accepted",
            unblockCondition = "Add profiling, ownership, cache metrics, and adapter-backed cache evidence before promotion.",
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-edge-overflow",
            strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
            status = WebGpuCoverageEvidenceStatus.Refused,
            routeIdentifier = "webgpu.coverage.refuse",
            diagnosticReason = StandardCoverageReason.EdgeCountExceeded,
            evidence = "AA edge overflow refuses when mask/atlas fallback is disabled",
            unblockCondition = "Enable a reviewed mask/atlas fallback or raise the budget with adapter-backed evidence.",
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "arbitrary-aa-clip",
            strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
            status = WebGpuCoverageEvidenceStatus.Refused,
            routeIdentifier = "webgpu.coverage.refuse",
            diagnosticReason = StandardCoverageReason.ArbitraryAaClipUnsupported,
            evidence = "arbitrary AA clips and shader clips refuse instead of scissor-only approximation",
            unblockCondition = "Add mask/list/atlas clip strategy plus adapter-backed cross-backend evidence.",
        ),
    )

    public val rows: List<WebGpuCoverageStrategyInventoryRow>
        get() = rowsForCiAdapterStatus(defaultCiAdapterLaneStatus)

    public fun dump(): String = rows.joinToString(separator = "\n") { it.dump() }
}

public data class WebGpuPathCoverageFacts(
    val isConvex: Boolean,
    val contourCount: Int,
    val edgeCount: Int,
    val pathVerbCount: Int? = null,
    val maxCubicSegmentsPerCubic: Int? = null,
    val dashIntervalCount: Int? = null,
    val clipStackDepth: Int? = null,
    val deviceBounds: FloatRect? = null,
    val strokeWidth: Float? = null,
    val strokeCaps: List<String> = emptyList(),
    val strokeJoins: List<String> = emptyList(),
    val maskOrAtlasFallbackEnabled: Boolean = false,
    val strokeOutlineFallbackEnabled: Boolean = false,
) {
    public fun budgetDiagnostics(): WebGpuPathAaBudgetDiagnostics =
        WebGpuPathAaBudgetDiagnostics(
            pathVerbCount = pathVerbCount,
            coverageEdgeCount = edgeCount,
            maxCubicSegmentsPerCubic = maxCubicSegmentsPerCubic,
            dashIntervalCount = dashIntervalCount,
            clipStackDepth = clipStackDepth,
            deviceBounds = deviceBounds,
            strokeWidth = strokeWidth,
            strokeCaps = strokeCaps,
            strokeJoins = strokeJoins,
        )

    public val hasStrokeStyleFacts: Boolean
        get() = strokeWidth != null || strokeCaps.isNotEmpty() || strokeJoins.isNotEmpty()
}

public data class WebGpuPathAaBudgetDiagnostics(
    val pathVerbCount: Int?,
    val pathVerbBudget: Int = WEBGPU_PATH_AA_VERB_BUDGET,
    val coverageEdgeCount: Int,
    val coverageEdgeBudget: Int = WEBGPU_PATH_AA_EDGE_BUDGET,
    val maxCubicSegmentsPerCubic: Int?,
    val cubicSegmentBudget: Int = WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET,
    val dashIntervalCount: Int?,
    val dashIntervalBudget: Int = WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET,
    val clipStackDepth: Int?,
    val clipStackDepthBudget: Int = WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET,
    val deviceBounds: FloatRect?,
    val deviceBoundsBudget: Float = WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET,
    val strokeWidth: Float? = null,
    val strokeCaps: List<String> = emptyList(),
    val strokeJoins: List<String> = emptyList(),
) {
    public fun dump(): String =
        "pathVerbCount=${pathVerbCount ?: "n/a"}/$pathVerbBudget;" +
            "coverageEdgeCount=$coverageEdgeCount/$coverageEdgeBudget;" +
            "cubicMaxSegmentsPerCubic=${maxCubicSegmentsPerCubic ?: "n/a"}/$cubicSegmentBudget;" +
            "dashIntervalCount=${dashIntervalCount ?: "n/a"}/$dashIntervalBudget;" +
            "clipStackDepth=${clipStackDepth ?: "n/a"}/$clipStackDepthBudget;" +
            "deviceBounds=${deviceBounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" } ?: "n/a"};" +
            "deviceBoundsMaxSize=${deviceBounds?.let { maxOf(it.right - it.left, it.bottom - it.top) } ?: "n/a"}/$deviceBoundsBudget;" +
            "strokeWidth=${strokeWidth ?: "n/a"};" +
            "strokeCaps=${if (strokeCaps.isEmpty()) "n/a" else strokeCaps.joinToString("+")};" +
            "strokeJoins=${if (strokeJoins.isEmpty()) "n/a" else strokeJoins.joinToString("+")}"
}

public data class WebGpuCoverageDiagnostic(
    val backend: BackendKind,
    val reason: DiagnosticReason,
    val action: FallbackPlan,
) {
    public fun dump(): String =
        "backend=$backend,reason=${reason.code},action=${action::class.simpleName}(${action.reason})"
}

public data class WebGpuCoverageSelection(
    val drawKind: String,
    val strategy: WebGpuCoverageStrategy,
    val coveragePlan: CoveragePlan,
    val clipInteraction: ClipInteraction,
    val loweringResult: CoverageLoweringResult,
    val pipelineAxes: List<PipelineKeyClassification>,
    val routeIdentifier: String,
    val diagnostic: WebGpuCoverageDiagnostic?,
    val budgetDiagnostics: WebGpuPathAaBudgetDiagnostics? = null,
) {
    public fun pipelineKeyDump(): String =
        canonicalPipelineKeyIdentity(pipelineAxes).dump()

    public fun dump(): String = buildString {
        appendLine("WebGpuCoverageSelection(v1)")
        appendLine("drawKind=$drawKind")
        appendLine("strategy=$strategy")
        appendLine("route=$routeIdentifier")
        appendLine("coverage=${dumpCoveragePlan(coveragePlan)}")
        appendLine("clip=${dumpClipInteraction(clipInteraction)}")
        appendLine("pathAaBudgets=${budgetDiagnostics?.dump() ?: "n/a"}")
        appendLine("pipelineAxes=${pipelineKeyDump()}")
        appendLine("diagnostic=${diagnostic?.dump() ?: "none"}")
    }.trimEnd()
}

private fun dumpClipInteraction(clip: ClipInteraction): String = when (clip) {
    ClipInteraction.None -> "None"
    is ClipInteraction.DeviceRect -> "DeviceRect(${clip.bounds.left},${clip.bounds.top},${clip.bounds.right},${clip.bounds.bottom})"
    is ClipInteraction.AnalyticShape -> "AnalyticShape(${clip.shape.kind},${clip.shape.bounds.left},${clip.shape.bounds.top},${clip.shape.bounds.right},${clip.shape.bounds.bottom})"
    is ClipInteraction.AaClip -> "AaClip(ref=${clip.ref.id},bounds=${clip.bounds.left},${clip.bounds.top},${clip.bounds.right},${clip.bounds.bottom})"
    is ClipInteraction.ShaderClip -> "ShaderClip(reason=${clip.reason.code})"
    is ClipInteraction.Unsupported -> "Unsupported(reason=${clip.reason.code})"
}

private fun dumpCoveragePlan(plan: CoveragePlan): String = when (plan) {
    CoveragePlan.Full -> "Full"
    is CoveragePlan.AnalyticRect -> "AnalyticRect(aa=${plan.aa})"
    is CoveragePlan.AnalyticRRect -> "AnalyticRRect(aa=${plan.aa})"
    is CoveragePlan.SpanRuns -> "SpanRuns"
    is CoveragePlan.AlphaMask -> "AlphaMask(format=${plan.format})"
    is CoveragePlan.PathCoverage -> "PathCoverage(fillType=${plan.fillType},aa=${plan.aa},inverse=${plan.inverse})"
    is CoveragePlan.CoverageAtlas -> "CoverageAtlas(policy=${plan.cachePolicy::class.simpleName})"
    is CoveragePlan.Unsupported -> "Unsupported(reason=${plan.reason.code})"
}

public object WebGpuCoveragePlanSelector {
    public fun select(
        drawKind: String,
        plan: CoveragePlan,
        pathFacts: WebGpuPathCoverageFacts? = null,
        clipInteraction: ClipInteraction = ClipInteraction.None,
    ): WebGpuCoverageSelection {
        val lowering = CoveragePlanAdapter.lower(plan)
        val clipDiagnosticReason = unsupportedClipReason(clipInteraction)
        if (clipDiagnosticReason != null) {
            return unsupported(
                drawKind = drawKind,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                reason = clipDiagnosticReason,
            )
        }
        return when (plan) {
            is CoveragePlan.AnalyticRect -> supported(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.AnalyticRect,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                coverageKind = "analyticRect",
                route = "webgpu.coverage.analytic-rect",
            )
            is CoveragePlan.AnalyticRRect -> supported(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.AnalyticRRect,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                coverageKind = "analyticRRect",
                route = "webgpu.coverage.analytic-rrect",
            )
            CoveragePlan.Full -> supported(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.ExistingGpuCompatibility,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                coverageKind = "full",
                route = "webgpu.coverage.full-scissor",
            )
            is CoveragePlan.AlphaMask -> unsupported(
                drawKind = drawKind,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                reason = StandardCoverageReason.AlphaMaskUnsupported,
            )
            is CoveragePlan.SpanRuns -> unsupported(
                drawKind = drawKind,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                reason = StandardCoverageReason.SpanRunsUnsupported,
            )
            is CoveragePlan.PathCoverage -> pathStrategy(drawKind, plan, clipInteraction, lowering, pathFacts)
            is CoveragePlan.CoverageAtlas -> unsupported(
                drawKind = drawKind,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                reason = StandardCoverageReason.AtlasPolicyUnavailable,
            )
            is CoveragePlan.Unsupported -> unsupported(
                drawKind = drawKind,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                reason = plan.reason,
            )
        }
    }

    private fun supported(
        drawKind: String,
        strategy: WebGpuCoverageStrategy,
        plan: CoveragePlan,
        clipInteraction: ClipInteraction,
        lowering: CoverageLoweringResult,
        coverageKind: String,
        route: String,
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = strategy,
        coveragePlan = plan,
        clipInteraction = clipInteraction,
        loweringResult = lowering,
        pipelineAxes = listOf(
            PipelineKeyClassification(
                axis = "coverageKind",
                axisClass = PipelineKeyAxisClass.Code,
                value = coverageKind,
            ),
        ),
        routeIdentifier = route,
        diagnostic = null,
        budgetDiagnostics = null,
    )

    private fun pathStrategy(
        drawKind: String,
        plan: CoveragePlan.PathCoverage,
        clipInteraction: ClipInteraction,
        lowering: CoverageLoweringResult,
        facts: WebGpuPathCoverageFacts?,
    ): WebGpuCoverageSelection {
        if (facts == null) {
            return unsupported(drawKind, plan, clipInteraction, lowering, StandardCoverageReason.StencilCoverUnavailable)
        }
        val budgetDiagnostics = facts.budgetDiagnostics()
        val budgetReason = pathBudgetExceededReason(facts)
        if (plan.aa && budgetReason != null) {
            if (budgetReason == StandardCoverageReason.EdgeCountExceeded && facts.strokeOutlineFallbackEnabled) {
                return supportedPath(
                    drawKind = drawKind,
                    strategy = WebGpuCoverageStrategy.PathAaStrokePrimitive,
                    plan = plan,
                    clipInteraction = clipInteraction,
                    lowering = lowering,
                    budgetDiagnostics = budgetDiagnostics,
                    coverageKind = "pathAaStrokePrimitive",
                    route = "webgpu.coverage.path-aa-stroke-primitive",
                )
            }
            return if (budgetReason == StandardCoverageReason.EdgeCountExceeded && facts.maskOrAtlasFallbackEnabled) {
                supportedPath(
                    drawKind = drawKind,
                    strategy = WebGpuCoverageStrategy.CoverageMaskOrAtlasFallback,
                    plan = plan,
                    clipInteraction = clipInteraction,
                    lowering = lowering,
                    budgetDiagnostics = budgetDiagnostics,
                    coverageKind = "pathMaskOrAtlas",
                    route = "webgpu.coverage.path-mask-or-atlas",
                )
            } else {
                unsupported(
                    drawKind = drawKind,
                    plan = plan,
                    clipInteraction = clipInteraction,
                    lowering = lowering,
                    reason = budgetReason,
                    pipelineAxes = pathPipelineAxes("pathCoverageUnsupported", plan),
                    budgetDiagnostics = budgetDiagnostics,
                )
            }
        }
        if (plan.aa && facts.hasStrokeStyleFacts) {
            return unsupported(
                drawKind = drawKind,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                reason = StandardCoverageReason.StrokeCapJoinVisualParityBelowThreshold,
                pipelineAxes = pathPipelineAxes("pathAaStrokeCapJoinBlocked", plan),
                budgetDiagnostics = budgetDiagnostics,
            )
        }
        val useConvexFan = facts.isConvex && facts.contourCount == 1 && !plan.inverse
        return if (useConvexFan) {
            supportedPath(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.CpuPreparedConvexFan,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                budgetDiagnostics = budgetDiagnostics,
                coverageKind = "pathConvexFan",
                route = "webgpu.coverage.path-convex-fan",
            )
        } else {
            supportedPath(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.StencilCover,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
                budgetDiagnostics = budgetDiagnostics,
                coverageKind = "pathStencilCover",
                route = "webgpu.coverage.path-stencil-cover",
            )
        }
    }

    private fun pathBudgetExceededReason(facts: WebGpuPathCoverageFacts): StandardCoverageReason? = when {
        facts.edgeCount > WEBGPU_PATH_AA_EDGE_BUDGET -> StandardCoverageReason.EdgeCountExceeded
        facts.pathVerbCount != null && facts.pathVerbCount > WEBGPU_PATH_AA_VERB_BUDGET ->
            StandardCoverageReason.VerbBudgetExceeded
        facts.maxCubicSegmentsPerCubic != null &&
            facts.maxCubicSegmentsPerCubic > WEBGPU_PATH_AA_CUBIC_SEGMENT_BUDGET ->
            StandardCoverageReason.CubicSegmentBudgetExceeded
        facts.dashIntervalCount != null && facts.dashIntervalCount > WEBGPU_PATH_AA_DASH_INTERVAL_BUDGET ->
            StandardCoverageReason.DashBudgetExceeded
        facts.clipStackDepth != null && facts.clipStackDepth > WEBGPU_PATH_AA_CLIP_STACK_DEPTH_BUDGET ->
            StandardCoverageReason.ClipDepthExceeded
        facts.deviceBounds != null && maxOf(
            facts.deviceBounds.right - facts.deviceBounds.left,
            facts.deviceBounds.bottom - facts.deviceBounds.top,
        ) > WEBGPU_PATH_AA_DEVICE_BOUNDS_BUDGET -> StandardCoverageReason.BoundsBudgetExceeded
        else -> null
    }

    private fun supportedPath(
        drawKind: String,
        strategy: WebGpuCoverageStrategy,
        plan: CoveragePlan.PathCoverage,
        clipInteraction: ClipInteraction,
        lowering: CoverageLoweringResult,
        budgetDiagnostics: WebGpuPathAaBudgetDiagnostics,
        coverageKind: String,
        route: String,
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = strategy,
        coveragePlan = plan,
        clipInteraction = clipInteraction,
        loweringResult = lowering,
        pipelineAxes = pathPipelineAxes(coverageKind, plan),
        routeIdentifier = route,
        diagnostic = null,
        budgetDiagnostics = budgetDiagnostics,
    )

    private fun pathPipelineAxes(
        coverageKind: String,
        plan: CoveragePlan.PathCoverage,
    ): List<PipelineKeyClassification> = listOf(
        PipelineKeyClassification(
            axis = "coverageKind",
            axisClass = PipelineKeyAxisClass.Code,
            value = coverageKind,
        ),
        PipelineKeyClassification(
            axis = "pathFillRule",
            axisClass = PipelineKeyAxisClass.PipelineState,
            value = if (plan.fillType == PathFillType.EvenOdd) "evenOdd" else "winding",
        ),
        PipelineKeyClassification(
            axis = "topology",
            axisClass = PipelineKeyAxisClass.PipelineState,
            value = "triangleList",
        ),
    )

    private fun unsupported(
        drawKind: String,
        plan: CoveragePlan,
        clipInteraction: ClipInteraction,
        lowering: CoverageLoweringResult,
        reason: DiagnosticReason,
        pipelineAxes: List<PipelineKeyClassification> = emptyList(),
        budgetDiagnostics: WebGpuPathAaBudgetDiagnostics? = null,
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
        coveragePlan = plan,
        clipInteraction = clipInteraction,
        loweringResult = lowering,
        pipelineAxes = pipelineAxes,
        routeIdentifier = "webgpu.coverage.refuse",
        diagnostic = WebGpuCoverageDiagnostic(
            backend = BackendKind.GPU,
            reason = reason,
            action = FallbackPlan.RefuseDiagnostic(reason.code),
        ),
        budgetDiagnostics = budgetDiagnostics,
    )

    private fun unsupportedClipReason(clip: ClipInteraction): DiagnosticReason? = when (clip) {
        ClipInteraction.None,
        is ClipInteraction.DeviceRect,
        is ClipInteraction.AnalyticShape -> null
        is ClipInteraction.AaClip,
        is ClipInteraction.ShaderClip -> StandardCoverageReason.ArbitraryAaClipUnsupported
        is ClipInteraction.Unsupported -> clip.reason
    }
}
