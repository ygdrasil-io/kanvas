package org.graphiks.kanvas.gpu.renderer.filters

/** Parameters for a separable gaussian blur filter pass. */
data class BlurFilterParams(
<<<<<<< HEAD
    val sigmaX: Float,
    val sigmaY: Float,
=======
    val radiusX: Float,
    val radiusY: Float,
>>>>>>> master
    val separable: Boolean = true,
)

/** Result of executing a blur filter pass. */
data class BlurFilterResult(
    val passCount: Int,
    val kernelSize: Int,
    val accepted: Boolean,
)

<<<<<<< HEAD
/** Applies separable gaussian blur via horizontal and vertical passes.
 *  Delegates actual kernel computation to [GpuSeparableBlurPlanner]. */
=======
/** Applies separable gaussian blur via horizontal and vertical passes. */
>>>>>>> master
class GaussianBlurFilter(
    private val maxPassCount: Int = 2,
) {
    /** Executes the blur for the given parameters and returns pass/kernel stats. */
    fun execute(params: BlurFilterParams): BlurFilterResult {
<<<<<<< HEAD
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
=======
        return BlurFilterResult(
            passCount = 2,
            kernelSize = kernelRadiusToTaps(params.radiusX),
>>>>>>> master
            accepted = true,
        )
    }

<<<<<<< HEAD
    companion object {
        fun kernelSigmaToTaps(sigma: Float): Int =
            if (sigma < 0.5f) 1 else (sigma * 2f + 1f).toInt()
    }
=======
    private fun kernelRadiusToTaps(radius: Float): Int =
        if (radius < 1f) 1 else (radius * 2f + 1f).toInt()
>>>>>>> master
}
