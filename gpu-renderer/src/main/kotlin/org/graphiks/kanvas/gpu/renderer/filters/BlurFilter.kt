package org.graphiks.kanvas.gpu.renderer.filters

/** Parameters for a separable gaussian blur filter pass. */
data class BlurFilterParams(
    val sigmaX: Float,
    val sigmaY: Float,
    val separable: Boolean = true,
)

/** Result of executing a blur filter pass. */
data class BlurFilterResult(
    val passCount: Int,
    val kernelSize: Int,
    val accepted: Boolean,
)

/** Applies separable gaussian blur via horizontal and vertical passes.
 *  Delegates actual kernel computation to [GpuSeparableBlurPlanner]. */
class GaussianBlurFilter(
    private val maxPassCount: Int = 2,
) {
    /** Executes the blur for the given parameters and returns pass/kernel stats. */
    fun execute(params: BlurFilterParams): BlurFilterResult {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(
            sigmaX = params.sigmaX,
            sigmaY = params.sigmaY,
            qualityTier = SeparableBlurQualityTier.NORMAL,
        )
        if (plan.passes.isEmpty() || plan.diagnostics.any { it.terminal }) {
            return BlurFilterResult(passCount = 0, kernelSize = 0, accepted = false)
        }
        return BlurFilterResult(
            passCount = plan.passes.size,
            kernelSize = plan.passes.first().kernelTaps,
            accepted = true,
        )
    }

    companion object {
        fun kernelSigmaToTaps(sigma: Float): Int =
            if (sigma < 0.5f) 1 else (sigma * 2f + 1f).toInt()
    }
}
