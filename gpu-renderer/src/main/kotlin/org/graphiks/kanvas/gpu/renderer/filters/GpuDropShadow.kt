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

data class GPUDropShadowMaskPlan(
    val sourceBinding: String,
    val maskOutput: String,
)

data class GPUDropShadowBlurPlan(
    val blur: SeparableBlurPlan,
    val maskInput: String = "",
    val blurredOutput: String = "",
)

data class GPUDropShadowCompositePlan(
    val shadowBinding: String,
    val sourceBinding: String,
    val targetBinding: String,
    val offsetDx: Float,
    val offsetDy: Float,
    val sampleOffset: Boolean,
)

sealed interface GPUDropShadowResult {
    data class Accepted(
        val plan: GPUDropShadowPlan,
        val maskPlan: GPUDropShadowMaskPlan?,
        val blurPlan: GPUDropShadowBlurPlan?,
        val compositePlan: GPUDropShadowCompositePlan?,
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

        val maskPlan = GPUDropShadowMaskPlan(
            sourceBinding = "source:${params.hashCode()}",
            maskOutput = "mask-r8:${params.hashCode()}",
        )

        val blurPlan: SeparableBlurPlan? = if (needsBlur) {
            val plan = blurPlanner.plan(
                sigmaX = params.sigmaX,
                sigmaY = params.sigmaY,
                tileMode = params.tileMode,
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

        val blurPlanWrapper = blurPlan?.let { bp ->
            GPUDropShadowBlurPlan(
                blur = bp,
                maskInput = maskPlan.maskOutput,
                blurredOutput = "blurred-shadow:${params.hashCode()}",
            )
        }

        val compositePlan = GPUDropShadowCompositePlan(
            shadowBinding = blurPlanWrapper?.blurredOutput ?: maskPlan.maskOutput,
            sourceBinding = maskPlan.sourceBinding,
            targetBinding = "target:${params.hashCode()}",
            offsetDx = params.offsetDx,
            offsetDy = params.offsetDy,
            sampleOffset = params.offsetDx != 0f || params.offsetDy != 0f,
        )

        return GPUDropShadowResult.Accepted(
            plan = params,
            maskPlan = maskPlan,
            blurPlan = blurPlanWrapper,
            compositePlan = if (params.mode == GPUDropShadowMode.Composite) compositePlan else null,
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
