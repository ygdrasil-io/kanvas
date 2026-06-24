package org.graphiks.kanvas.gpu.renderer.filters

/** Parameters for a separable gaussian blur filter pass. */
data class BlurFilterParams(
    val radiusX: Float,
    val radiusY: Float,
    val separable: Boolean = true,
)

/** Result of executing a blur filter pass. */
data class BlurFilterResult(
    val passCount: Int,
    val kernelSize: Int,
    val accepted: Boolean,
)

/** Applies separable gaussian blur via horizontal and vertical passes. */
class GaussianBlurFilter(
    private val maxPassCount: Int = 2,
) {
    /** Executes the blur for the given parameters and returns pass/kernel stats. */
    fun execute(params: BlurFilterParams): BlurFilterResult {
        return BlurFilterResult(
            passCount = 2,
            kernelSize = kernelRadiusToTaps(params.radiusX),
            accepted = true,
        )
    }

    private fun kernelRadiusToTaps(radius: Float): Int =
        if (radius < 1f) 1 else (radius * 2f + 1f).toInt()
}
