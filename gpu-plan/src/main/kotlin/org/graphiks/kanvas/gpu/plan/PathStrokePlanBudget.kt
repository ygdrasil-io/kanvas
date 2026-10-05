package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.SizeI32

/** Checked physical footprint calculation for immutable W4d path snapshots. */
public object PathStrokePlanBudget {
    public fun calculate(
        targetExtent: SizeI32,
        geometriesF32: Collection<PathFillGeometryF32>,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        usesW5aMaterialContract: Boolean = true,
    ): PathStrokePlanBudgetResult = when (
        val result = PathFillPlanBudget.calculate(
            targetExtent, geometriesF32, capabilities, budget, usesW5aMaterialContract,
        )
    ) {
        is PathFillPlanBudgetResult.WithinBudget -> PathStrokePlanBudgetResult.WithinBudget(result.footprint)
        is PathFillPlanBudgetResult.Exceeded -> PathStrokePlanBudgetResult.Exceeded(
            result.requiredBytes,
            result.limitBytes,
        )
        is PathFillPlanBudgetResult.Invalid -> PathStrokePlanBudgetResult.Invalid(result.code)
    }
}

public sealed interface PathStrokePlanBudgetResult {
    public data class WithinBudget(public val footprint: PathFillMemoryFootprint) : PathStrokePlanBudgetResult
    public data class Exceeded(public val requiredBytes: Long, public val limitBytes: Long) : PathStrokePlanBudgetResult
    public data class Invalid(public val code: String) : PathStrokePlanBudgetResult
}
