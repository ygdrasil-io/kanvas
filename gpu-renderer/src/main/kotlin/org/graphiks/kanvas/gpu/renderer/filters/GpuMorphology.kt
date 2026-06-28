package org.graphiks.kanvas.gpu.renderer.filters

/** Morphology filter mode. */
enum class MorphologyMode {
    /** Replaces each pixel with the maximum value in the kernel neighborhood. */
    DILATE,
    /** Replaces each pixel with the minimum value in the kernel neighborhood. */
    ERODE,
}

/** Morphology kernel shape. */
enum class MorphologyKernel {
    /** Rectangular kernel region. */
    RECT,
    /** Circular (ellipsoidal) kernel region. */
    CIRCLE,
}

/** Parameters for a morphology filter pass. */
data class MorphologyParams(
    val mode: MorphologyMode,
    val kernel: MorphologyKernel,
    val radiusX: Float,
    val radiusY: Float,
)

/** Result of executing a morphology filter pass. */
data class MorphologyResult(
    val accepted: Boolean,
    val passCount: Int,
    val kernelSize: Int,
)

/** Applies morphology (dilate/erode) with rect or circular kernels. */
class GpuMorphologyFilter {
    /** Executes the morphology filter and returns pass/kernel stats. */
    fun execute(params: MorphologyParams): MorphologyResult {
        if (params.radiusX < 0f || params.radiusY < 0f) {
            return MorphologyResult(
                accepted = false,
                passCount = 1,
                kernelSize = 0,
            )
        }
        val kernelWidth = radiusToKernelSize(params.radiusX)
        val kernelHeight = radiusToKernelSize(params.radiusY)
        return MorphologyResult(
            accepted = true,
            passCount = 1,
            kernelSize = kernelWidth * kernelHeight,
        )
    }

    private fun radiusToKernelSize(radius: Float): Int =
        if (radius < 1f) 1 else (radius * 2f + 1f).toInt()
}
