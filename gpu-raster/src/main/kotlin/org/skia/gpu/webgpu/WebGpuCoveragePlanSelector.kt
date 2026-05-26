package org.skia.gpu.webgpu

import org.skia.pipeline.BackendKind
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
    val loweringResult: CoverageLoweringResult,
    val pipelineAxes: List<SkWebGpuDevice.PipelineKeyClassification>,
    val routeIdentifier: String,
    val diagnostic: WebGpuCoverageDiagnostic?,
) {
    public fun pipelineKeyDump(): String =
        pipelineAxes.sortedBy { it.axis }
            .joinToString("|") { "${it.axis}=${it.value}:${it.axisClass}" }

    public fun dump(): String = buildString {
        appendLine("WebGpuCoverageSelection(v1)")
        appendLine("drawKind=$drawKind")
        appendLine("strategy=$strategy")
        appendLine("route=$routeIdentifier")
        appendLine("coverage=${dumpCoveragePlan(coveragePlan)}")
        appendLine("pipelineAxes=${pipelineKeyDump()}")
        appendLine("diagnostic=${diagnostic?.dump() ?: "none"}")
    }.trimEnd()
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
    ): WebGpuCoverageSelection {
        val lowering = CoveragePlanAdapter.lower(plan)
        return when (plan) {
            is CoveragePlan.AnalyticRect -> supported(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.AnalyticRect,
                plan = plan,
                lowering = lowering,
                coverageKind = "analyticRect",
                route = "webgpu.coverage.analytic-rect",
            )
            is CoveragePlan.AnalyticRRect -> supported(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.AnalyticRRect,
                plan = plan,
                lowering = lowering,
                coverageKind = "analyticRRect",
                route = "webgpu.coverage.analytic-rrect",
            )
            CoveragePlan.Full -> supported(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.ExistingGpuCompatibility,
                plan = plan,
                lowering = lowering,
                coverageKind = "full",
                route = "webgpu.coverage.full-scissor",
            )
            is CoveragePlan.AlphaMask -> unsupported(
                drawKind = drawKind,
                plan = plan,
                lowering = lowering,
                reason = StandardCoverageReason.AlphaMaskUnsupported,
            )
            is CoveragePlan.SpanRuns -> unsupported(
                drawKind = drawKind,
                plan = plan,
                lowering = lowering,
                reason = StandardCoverageReason.SpanRunsUnsupported,
            )
            is CoveragePlan.PathCoverage -> pathStrategy(drawKind, plan, lowering, pathFacts)
            is CoveragePlan.CoverageAtlas -> unsupported(
                drawKind = drawKind,
                plan = plan,
                lowering = lowering,
                reason = StandardCoverageReason.AtlasPolicyUnavailable,
            )
            is CoveragePlan.Unsupported -> unsupported(
                drawKind = drawKind,
                plan = plan,
                lowering = lowering,
                reason = plan.reason,
            )
        }
    }

    private fun supported(
        drawKind: String,
        strategy: WebGpuCoverageStrategy,
        plan: CoveragePlan,
        lowering: CoverageLoweringResult,
        coverageKind: String,
        route: String,
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = strategy,
        coveragePlan = plan,
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
        lowering: CoverageLoweringResult,
        facts: WebGpuPathCoverageFacts?,
    ): WebGpuCoverageSelection {
        if (facts == null) {
            return unsupported(drawKind, plan, lowering, StandardCoverageReason.StencilCoverUnavailable)
        }
        if (plan.aa && facts.edgeCount > WEBGPU_PATH_AA_EDGE_BUDGET) {
            return if (facts.maskOrAtlasFallbackEnabled) {
                supportedPath(
                    drawKind = drawKind,
                    strategy = WebGpuCoverageStrategy.CoverageMaskOrAtlasFallback,
                    plan = plan,
                    lowering = lowering,
                    coverageKind = "pathMaskOrAtlas",
                    route = "webgpu.coverage.path-mask-or-atlas",
                )
            } else {
                unsupported(
                    drawKind = drawKind,
                    plan = plan,
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
                lowering = lowering,
                coverageKind = "pathConvexFan",
                route = "webgpu.coverage.path-convex-fan",
            )
        } else {
            supportedPath(
                drawKind = drawKind,
                strategy = WebGpuCoverageStrategy.StencilCover,
                plan = plan,
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
        lowering: CoverageLoweringResult,
        coverageKind: String,
        route: String,
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = strategy,
        coveragePlan = plan,
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
        lowering: CoverageLoweringResult,
        reason: DiagnosticReason,
        pipelineAxes: List<SkWebGpuDevice.PipelineKeyClassification> = emptyList(),
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
        coveragePlan = plan,
        loweringResult = lowering,
        pipelineAxes = pipelineAxes,
        routeIdentifier = "webgpu.coverage.refuse",
        diagnostic = WebGpuCoverageDiagnostic(
            backend = BackendKind.GPU,
            reason = reason,
            action = FallbackPlan.RefuseDiagnostic(reason.code),
        ),
    )

}
