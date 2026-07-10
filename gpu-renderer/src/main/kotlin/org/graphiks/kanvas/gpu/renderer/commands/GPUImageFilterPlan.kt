package org.graphiks.kanvas.gpu.renderer.commands

/**
 * Command-owned image-filter intent for image draws.
 *
 * This first slice records a supported blur without scheduling its execution.
 */
sealed interface GPUImageFilterPlan {
    data object None : GPUImageFilterPlan
    data object Identity : GPUImageFilterPlan

    data class Blur(
        val sigmaX: Float,
        val sigmaY: Float,
        val haloX: Int,
        val haloY: Int,
        val outputBounds: GPURect,
    ) : GPUImageFilterPlan

    data class Refused(val code: String) : GPUImageFilterPlan
}
