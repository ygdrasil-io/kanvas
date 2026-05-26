package org.skia.gpu.webgpu

import org.skia.pipeline.BackendKind
import org.skia.pipeline.CoverageBackendStrategy
import org.skia.pipeline.CoverageLoweringResult
import org.skia.pipeline.CoveragePlan
import org.skia.pipeline.CoveragePlanAdapter
import org.skia.pipeline.DiagnosticReason
import org.skia.pipeline.FallbackPlan
import org.skia.pipeline.StandardCoverageReason

public enum class WebGpuCoverageStrategy {
    AnalyticRect,
    AnalyticRRect,
    ExistingGpuCompatibility,
    RefuseDiagnostic,
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
        appendLine("pipelineAxes=${pipelineKeyDump()}")
        appendLine("diagnostic=${diagnostic?.dump() ?: "none"}")
    }.trimEnd()
}

public object WebGpuCoveragePlanSelector {
    public fun select(drawKind: String, plan: CoveragePlan): WebGpuCoverageSelection {
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
            is CoveragePlan.PathCoverage -> unsupportedStrategy(drawKind, plan, lowering)
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

    private fun unsupportedStrategy(
        drawKind: String,
        plan: CoveragePlan,
        lowering: CoverageLoweringResult,
    ): WebGpuCoverageSelection {
        val strategy = (lowering as? CoverageLoweringResult.StrategyResult)?.strategy
        val reason = (strategy as? CoverageBackendStrategy.UnsupportedFallback)?.reason
            ?: StandardCoverageReason.StencilCoverUnavailable
        return unsupported(drawKind, plan, lowering, reason)
    }

    private fun unsupported(
        drawKind: String,
        plan: CoveragePlan,
        lowering: CoverageLoweringResult,
        reason: DiagnosticReason,
    ): WebGpuCoverageSelection = WebGpuCoverageSelection(
        drawKind = drawKind,
        strategy = WebGpuCoverageStrategy.RefuseDiagnostic,
        coveragePlan = plan,
        loweringResult = lowering,
        pipelineAxes = emptyList(),
        routeIdentifier = "webgpu.coverage.refuse",
        diagnostic = WebGpuCoverageDiagnostic(
            backend = BackendKind.GPU,
            reason = reason,
            action = FallbackPlan.RefuseDiagnostic(reason.code),
        ),
    )
}
