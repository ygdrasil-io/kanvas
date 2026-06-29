package org.graphiks.kanvas.gpu.renderer.stroke

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

data class StrokeDescriptor(val strokeWidth: Float)

sealed interface AdvancedStrokeRoute {
    data class Accepted(val descriptor: StrokeDescriptor) : AdvancedStrokeRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : AdvancedStrokeRoute
}

data class GPUStrokeStyleCompositionPlan(
    val width: Float,
    val cap: GPUStrokeCap,
    val join: GPUStrokeJoin,
    val miterLimit: Float,
    val dashPlan: GPUComplexDashPlan?,
    val pathEffectChain: GPUPathEffectChainPlan?,
) {
    companion object {
        fun plan(
            width: Float,
            cap: GPUStrokeCap = GPUStrokeCap.Butt,
            join: GPUStrokeJoin = GPUStrokeJoin.Miter,
            miterLimit: Float = 4f,
            dashArray: FloatArray? = null,
            dashPhase: Float = 0f,
            pathEffects: List<GPUPathEffectDescriptor>? = null,
        ): Result<GPUStrokeStyleCompositionPlan> {
            val dashPlan = if (dashArray != null && dashArray.isNotEmpty()) {
                val classification = GPUComplexDashPlan.classify(dashArray)
                if (classification == GPUDashClassification.UnsupportedLength) {
                    return Result.failure(
                        IllegalStateException("unsupported.stroke.dash_pattern_length")
                    )
                }
                GPUComplexDashPlan.plan(dashArray, dashPhase)
            } else null

            val pathEffectChain = if (pathEffects != null && pathEffects.isNotEmpty()) {
                val plan = GPUPathEffectChainPlan.plan(pathEffects)
                if (plan.maxDepthReached) {
                    return Result.failure(
                        IllegalStateException("unsupported.stroke.path_effect_chain_depth")
                    )
                }
                plan
            } else null

            return Result.success(
                GPUStrokeStyleCompositionPlan(
                    width = width,
                    cap = cap,
                    join = join,
                    miterLimit = miterLimit,
                    dashPlan = dashPlan,
                    pathEffectChain = pathEffectChain,
                )
            )
        }
    }
}

data class AdvancedStrokePlan(
    val strokeWidth: Float,
    val dashEffect: PathEffect? = null,
    val cornerEffect: PathEffect? = null,
) {
    fun toDescriptor(): StrokeDescriptor = StrokeDescriptor(strokeWidth)

    fun analyze(): AdvancedStrokeRoute {
        val effects = listOfNotNull(dashEffect, cornerEffect)
        if (effects.isEmpty()) return AdvancedStrokeRoute.Accepted(toDescriptor())

        val unsupported = effects.filterIsInstance<PathEffect.Unsupported>()
        if (unsupported.isNotEmpty()) {
            return AdvancedStrokeRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.stroke.unsupported_path_effect",
                    message = "unsupported path effect: ${unsupported.joinToString(",") { it.name }}",
                    stage = "stroke.analysis",
                    terminal = true,
                )
            )
        }

        val chain = PathEffectChain(effects)
        val result = chain.apply(100f)
        return if (result.isValid) {
            AdvancedStrokeRoute.Accepted(toDescriptor())
        } else if (result.report.startsWith("depth_exceeded:")) {
            AdvancedStrokeRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.stroke.path_effect_chain_depth",
                    message = "path effect chain depth exceeded: ${result.report}",
                    stage = "stroke.analysis",
                    terminal = true,
                )
            )
        } else {
            AdvancedStrokeRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.stroke.path_effect",
                    message = "unsupported path effect: ${result.report}",
                    stage = "stroke.analysis",
                    terminal = true,
                )
            )
        }
    }
}

object StrokeOracle {

    fun computeExpectedStroke(
        width: Float,
        dashArray: FloatArray?,
        pathLength: Float,
    ): StrokeDescriptor {
        val effectiveWidth = if (dashArray != null && dashArray.isNotEmpty()) {
            width * computeDashScale(dashArray)
        } else {
            width
        }
        return StrokeDescriptor(effectiveWidth.coerceAtLeast(0.5f))
    }

    private fun computeDashScale(dashArray: FloatArray): Float {
        val sumOn = dashArray.filterIndexed { index, _ -> index % 2 == 0 }.sum()
        val total = dashArray.sum()
        return if (total > 0f) sumOn / total else 1f
    }

    fun deterministicOracle(
        width: Float,
        dashArray: FloatArray?,
        dashPhase: Float,
        pathEffects: List<GPUPathEffectDescriptor>?,
    ): Boolean {
        val result1 = GPUStrokeStyleCompositionPlan.plan(
            width = width,
            dashArray = dashArray,
            dashPhase = dashPhase,
            pathEffects = pathEffects,
        )
        val result2 = GPUStrokeStyleCompositionPlan.plan(
            width = width,
            dashArray = dashArray,
            dashPhase = dashPhase,
            pathEffects = pathEffects,
        )
        return result1 == result2
    }
}
