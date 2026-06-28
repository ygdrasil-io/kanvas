package org.graphiks.kanvas.gpu.renderer.filters

enum class GPUDropShadowMode { ShadowOnly, Composite }

data class GPUDropShadowPlan(
    val offsetDx: Float,
    val offsetDy: Float,
    val sigmaX: Float,
    val sigmaY: Float,
    val shadowColor: GPUColor,
    val mode: GPUDropShadowMode,
    val tileMode: GPUTileMode,
)

data class GPUDropShadowBlurPlan(
    val blur: SeparableBlurPlan,
)

sealed interface GPUDropShadowResult {
    data class Accepted(
        val plan: GPUDropShadowPlan,
        val blurPlan: GPUDropShadowBlurPlan?,
        val diagnostics: List<GPUFilterDiagnostic>,
    ) : GPUDropShadowResult

    data class Refused(
        val plan: GPUDropShadowPlan,
        val diagnostics: List<GPUFilterDiagnostic>,
        val blurPlan: GPUDropShadowBlurPlan? = null,
    ) : GPUDropShadowResult
}

class GpuDropShadowFilter(
    private val blurPlanner: GpuSeparableBlurPlanner = GpuSeparableBlurPlanner(),
) {
    fun plan(params: GPUDropShadowPlan): GPUDropShadowResult {
        val needsBlur = params.sigmaX > 0f || params.sigmaY > 0f

        val blurPlan: SeparableBlurPlan? = if (needsBlur) {
            val plan = blurPlanner.plan(
                radiusX = params.sigmaX,
                radiusY = params.sigmaY,
            )
            if (plan.diagnostics.any { it.terminal }) {
                return GPUDropShadowResult.Refused(
                    plan = params,
                    diagnostics = listOf(
                        GPUFilterDiagnostic(
                            code = "unsupported.filter.drop_shadow_blur_unavailable",
                            message = "Drop shadow blur pass unavailable: blur planner refused with terminal diagnostics.",
                            terminal = true,
                        ),
                    ),
                )
            }
            plan
        } else {
            null
        }

        return GPUDropShadowResult.Accepted(
            plan = params,
            blurPlan = if (blurPlan != null) GPUDropShadowBlurPlan(blur = blurPlan) else null,
            diagnostics = listOf(
                GPUFilterDiagnostic(
                    code = "accepted.filter.drop_shadow",
                    message = "Drop shadow plan accepted: mode=${params.mode}, " +
                        "offset=(${params.offsetDx},${params.offsetDy}), " +
                        "sigma=(${params.sigmaX},${params.sigmaY})",
                    terminal = false,
                ),
            ),
        )
    }
}
