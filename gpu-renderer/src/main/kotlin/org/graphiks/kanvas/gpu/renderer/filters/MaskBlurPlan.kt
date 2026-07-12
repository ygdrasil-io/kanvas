package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.math.ceil
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds

sealed interface MaskBlurPlan {
    data object Identity : MaskBlurPlan

    data class Ready(
        val style: NormalizedBlurStyle,
        val requestedSigma: Float,
        val normalizedSigma: Float,
        val effectiveSigma: Float,
        val halo: Int,
        val scale: Float,
        val deviceBounds: GPUBounds,
        val localWidth: Int,
        val localHeight: Int,
        val bytesPerTexture: Long,
        val requiredBytes: Long,
        val diagnostics: List<MaskBlurDiagnostic>,
    ) : MaskBlurPlan

    data class Refused(val code: String) : MaskBlurPlan
}

data class MaskBlurDiagnostic(val code: String, val detail: String)

data class MaskBlurRequest(
    val bounds: GPUBounds,
    val clipBounds: GPUBounds,
    val targetWidth: Int,
    val targetHeight: Int,
    val style: NormalizedBlurStyle,
    val sigma: Float,
    val maxTextureDimension2D: Int,
    val maxIntermediateBytes: Long,
)

object MaskBlurPlanner {
    fun plan(request: MaskBlurRequest): MaskBlurPlan {
        if (!request.sigma.isFinite() || request.sigma < 0f) {
            return MaskBlurPlan.Refused("unsupported.mask-filter.blur.sigma")
        }
        if (request.sigma == 0f) return MaskBlurPlan.Identity

        val normalized = request.sigma.coerceIn(0.5f, 135f)
        val halo = ceil(3f * normalized).toInt()
        val left = maxOf(0f, request.bounds.left - halo, request.clipBounds.left)
        val top = maxOf(0f, request.bounds.top - halo, request.clipBounds.top)
        val right = minOf(request.targetWidth.toFloat(), request.bounds.right + halo, request.clipBounds.right)
        val bottom = minOf(request.targetHeight.toFloat(), request.bounds.bottom + halo, request.clipBounds.bottom)
        var scale = minOf(1f, 12f / normalized)

        repeat(16) {
            val width = ceil((right - left) * scale).toInt().coerceAtLeast(1)
            val height = ceil((bottom - top) * scale).toInt().coerceAtLeast(1)
            val bytesPerTexture = width.toLong() * height.toLong() * 4L
            val requiredBytes = bytesPerTexture * 4L
            if (width <= request.maxTextureDimension2D && height <= request.maxTextureDimension2D &&
                requiredBytes <= request.maxIntermediateBytes
            ) {
                return MaskBlurPlan.Ready(
                    request.style,
                    request.sigma,
                    normalized,
                    normalized * scale,
                    halo,
                    scale,
                    GPUBounds(left, top, right, bottom),
                    width,
                    height,
                    bytesPerTexture,
                    requiredBytes,
                    buildList {
                        if (normalized != request.sigma) {
                            add(MaskBlurDiagnostic("mask-filter.blur.sigma-clamped", "execution sigma was clamped"))
                        }
                        if (scale != 1f) {
                            add(MaskBlurDiagnostic("mask-filter.blur.reduced-resolution", "reduced route selected"))
                        }
                    },
                )
            }
            scale *= 0.5f
        }

        return MaskBlurPlan.Refused("unsupported.mask-filter.blur.intermediate-budget")
    }
}
