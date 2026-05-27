package org.skia.gpu.webgpu

import org.skia.pipeline.BackendKind
import org.skia.pipeline.ClipInteraction
import org.skia.pipeline.CoverageLoweringResult
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.CoveragePlanAdapter
import org.skia.pipeline.DiagnosticReason
import org.skia.pipeline.FallbackPlan
import org.skia.pipeline.PathFillType
import org.skia.pipeline.StandardCoverageReason

public const val WEBGPU_PATH_AA_EDGE_BUDGET: Int = 256

public enum class WebGpuCoverageStrategy {
    AnalyticRect,
    AnalyticRRect,
    CpuPreparedConvexFan,
    StencilCover,
    CoverageMaskOrAtlasFallback,
    ExistingGpuCompatibility,
    RefuseDiagnostic,
}

public enum class WebGpuCoverageEvidenceStatus(public val wireName: String) {
    Proven("proven"),
    Refused("refused"),
    Compatibility("compatibility"),
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
    public const val ciAdapterLaneAvailable: Boolean = false
    private const val ADAPTER_UNBLOCK: String =
        "Enable a non-skippable GPU adapter CI/scheduled lane that uploads gpu-raster artifacts and fails on adapter skips."

    public val rows: List<WebGpuCoverageStrategyInventoryRow> = listOf(
        WebGpuCoverageStrategyInventoryRow(
            branch = "analytic-rect",
            strategy = WebGpuCoverageStrategy.AnalyticRect,
            status = WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane,
            routeIdentifier = "webgpu.coverage.analytic-rect",
            diagnosticReason = null,
            evidence = "selector, pipeline-key, and local adapter rect fixture exist; release CI adapter lane is disabled",
            unblockCondition = ADAPTER_UNBLOCK,
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "analytic-rrect",
            strategy = WebGpuCoverageStrategy.AnalyticRRect,
            status = WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane,
            routeIdentifier = "webgpu.coverage.analytic-rrect",
            diagnosticReason = null,
            evidence = "selector, pipeline-key, and local adapter rrect fixture exist; release CI adapter lane is disabled",
            unblockCondition = ADAPTER_UNBLOCK,
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-convex-fan",
            strategy = WebGpuCoverageStrategy.CpuPreparedConvexFan,
            status = WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane,
            routeIdentifier = "webgpu.coverage.path-convex-fan",
            diagnosticReason = null,
            evidence = "selector and local adapter convex path fixture exist; release CI adapter lane is disabled",
            unblockCondition = ADAPTER_UNBLOCK,
        ),
        WebGpuCoverageStrategyInventoryRow(
            branch = "path-stencil-cover",
            strategy = WebGpuCoverageStrategy.StencilCover,
            status = WebGpuCoverageEvidenceStatus.BlockedNoAdapterLane,
            routeIdentifier = "webgpu.coverage.path-stencil-cover",
            diagnosticReason = null,
            evidence = "selector and local adapter concave/inverse path fixtures exist; release CI adapter lane is disabled",
            unblockCondition = ADAPTER_UNBLOCK,
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

    public fun dump(): String = rows.joinToString(separator = "\n") { it.dump() }
}

public data class WebGpuPathCoverageFacts(
    val isConvex: Boolean,
    val contourCount: Int,
    val edgeCount: Int,
    val maskOrAtlasFallbackEnabled: Boolean = false,
)

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
    val pipelineAxes: List<SkWebGpuDevice.PipelineKeyClassification>,
    val routeIdentifier: String,
    val diagnostic: WebGpuCoverageDiagnostic?,
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
            SkWebGpuDevice.PipelineKeyClassification(
                axis = "coverageKind",
                axisClass = SkWebGpuDevice.PipelineKeyAxisClass.Code,
                value = coverageKind,
            ),
        ),
        routeIdentifier = route,
        diagnostic = null,
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
        if (plan.aa && facts.edgeCount > WEBGPU_PATH_AA_EDGE_BUDGET) {
            return if (facts.maskOrAtlasFallbackEnabled) {
                supportedPath(
                    drawKind = drawKind,
                    strategy = WebGpuCoverageStrategy.CoverageMaskOrAtlasFallback,
                    plan = plan,
                    clipInteraction = clipInteraction,
                    lowering = lowering,
                    coverageKind = "pathMaskOrAtlas",
                    route = "webgpu.coverage.path-mask-or-atlas",
                )
            } else {
                unsupported(
                    drawKind = drawKind,
                    plan = plan,
                    clipInteraction = clipInteraction,
                    lowering = lowering,
                    reason = StandardCoverageReason.EdgeCountExceeded,
                    pipelineAxes = pathPipelineAxes("pathCoverageUnsupported", plan),
                )
            }
        }
        val useConvexFan = facts.isConvex && facts.contourCount == 1 && !plan.inverse
        return if (useConvexFan) {
            supportedPath(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.CpuPreparedConvexFan,
                plan = plan,
                clipInteraction = clipInteraction,
                lowering = lowering,
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
                coverageKind = "pathStencilCover",
                route = "webgpu.coverage.path-stencil-cover",
            )
        }
    }

    private fun supportedPath(
        drawKind: String,
        strategy: WebGpuCoverageStrategy,
        plan: CoveragePlan.PathCoverage,
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
        pipelineAxes = pathPipelineAxes(coverageKind, plan),
        routeIdentifier = route,
        diagnostic = null,
    )

    private fun pathPipelineAxes(
        coverageKind: String,
        plan: CoveragePlan.PathCoverage,
    ): List<SkWebGpuDevice.PipelineKeyClassification> = listOf(
        SkWebGpuDevice.PipelineKeyClassification(
            axis = "coverageKind",
            axisClass = SkWebGpuDevice.PipelineKeyAxisClass.Code,
            value = coverageKind,
        ),
        SkWebGpuDevice.PipelineKeyClassification(
            axis = "pathFillRule",
            axisClass = SkWebGpuDevice.PipelineKeyAxisClass.PipelineState,
            value = if (plan.fillType == PathFillType.EvenOdd) "evenOdd" else "winding",
        ),
        SkWebGpuDevice.PipelineKeyClassification(
            axis = "topology",
            axisClass = SkWebGpuDevice.PipelineKeyAxisClass.PipelineState,
            value = "triangleList",
        ),
    )

    private fun unsupported(
        drawKind: String,
        plan: CoveragePlan,
        clipInteraction: ClipInteraction,
        lowering: CoverageLoweringResult,
        reason: DiagnosticReason,
        pipelineAxes: List<SkWebGpuDevice.PipelineKeyClassification> = emptyList(),
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
