package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.SizeI32

/** Exact physical footprint for a W4d.2 frame that uses four-sample path coverage. */
public data class PathAaMemoryFootprint(
    public val base: PathFillMemoryFootprint,
    public val multisampleColorBytes: Long,
    public val multisampleDepthStencilBytes: Long,
    public val hardEdgeMaskCapacityBytes: Long,
    public val hardEdgeDepthStencilCapacityBytes: Long,
    public val peakBytes: Long,
)

/** Result of calculating all pooled W4d.2 path resources before graph publication. */
public sealed interface PathAaPlanBudgetResult {
    public data class WithinBudget(public val footprint: PathAaMemoryFootprint) : PathAaPlanBudgetResult
    public data class Exceeded(public val requiredBytes: Long, public val limitBytes: Long) : PathAaPlanBudgetResult
    public data class Invalid(public val code: String) : PathAaPlanBudgetResult
}

/** Checked AA4 extension of the established W4d path snapshot budget. */
public object PathAaPlanBudget {
    public fun calculate(
        targetExtent: SizeI32,
        geometriesF32: Collection<PathFillGeometryF32>,
        requiresAa4DepthStencil: Boolean,
        requiresHardMask: Boolean,
        requiresHardEdgeDepthStencil: Boolean,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): PathAaPlanBudgetResult {
        val base = when (
            val result = PathStrokePlanBudget.calculate(
                targetExtent = targetExtent,
                geometriesF32 = geometriesF32,
                capabilities = capabilities,
                budget = PlanBudget(Long.MAX_VALUE),
            )
        ) {
            is PathStrokePlanBudgetResult.WithinBudget -> result.footprint
            is PathStrokePlanBudgetResult.Exceeded -> return PathAaPlanBudgetResult.Invalid("unexpected-base-budget")
            is PathStrokePlanBudgetResult.Invalid -> return PathAaPlanBudgetResult.Invalid(result.code)
        }
        return try {
            val multisampleColorBytes = checkedTextureBytesI64(4, targetExtent.width, targetExtent.height, 4)
            val multisampleDepthStencilBytes = if (requiresAa4DepthStencil) {
                checkedTextureBytesI64(4, targetExtent.width, targetExtent.height, 4)
            } else {
                0L
            }
            val hardEdgeMaskCapacityBytes = if (requiresHardMask) {
                checkedTextureBytesI64(4, targetExtent.width, targetExtent.height, 1)
            } else {
                0L
            }
            val hardEdgeDepthStencilCapacityBytes = if (requiresHardEdgeDepthStencil) {
                checkedTextureBytesI64(4, targetExtent.width, targetExtent.height, 1)
            } else {
                0L
            }
            val terminalPeakBytes = Math.subtractExact(base.peakBytes, base.depthStencilBytes)
            val colorPeakBytes = listOf(
                terminalPeakBytes,
                -base.readbackBytes,
                multisampleColorBytes,
                multisampleDepthStencilBytes,
                hardEdgeMaskCapacityBytes,
                hardEdgeDepthStencilCapacityBytes,
            ).fold(0L, Math::addExact)
            val peakBytes = maxOf(terminalPeakBytes, colorPeakBytes)
            val footprint = PathAaMemoryFootprint(
                base = base,
                multisampleColorBytes = multisampleColorBytes,
                multisampleDepthStencilBytes = multisampleDepthStencilBytes,
                hardEdgeMaskCapacityBytes = hardEdgeMaskCapacityBytes,
                hardEdgeDepthStencilCapacityBytes = hardEdgeDepthStencilCapacityBytes,
                peakBytes = peakBytes,
            )
            if (peakBytes <= budget.maxFrameLocalBytes) {
                PathAaPlanBudgetResult.WithinBudget(footprint)
            } else {
                PathAaPlanBudgetResult.Exceeded(peakBytes, budget.maxFrameLocalBytes)
            }
        } catch (_: ArithmeticException) {
            PathAaPlanBudgetResult.Invalid("size-overflow")
        } catch (_: IllegalArgumentException) {
            PathAaPlanBudgetResult.Invalid("size-overflow")
        }
    }
}
