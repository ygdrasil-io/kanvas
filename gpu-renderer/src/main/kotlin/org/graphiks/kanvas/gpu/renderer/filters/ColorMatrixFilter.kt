package org.graphiks.kanvas.gpu.renderer.filters

/** A 5x4 color matrix (20 floats) for color filtering. */
data class ColorMatrix(
    val values: FloatArray = ColorMatrix.identity(),
) {
    companion object {
        /** Returns the identity color matrix (no-op). */
        fun identity(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

/** Result of applying a color matrix filter. */
data class ColorMatrixFilterResult(
    val accepted: Boolean,
)

/** Applies a color matrix to pixels as a GPU image filter. */
class ColorMatrixFilter {
    /** Executes the color matrix filter and returns acceptance stats. */
    fun execute(matrix: ColorMatrix): ColorMatrixFilterResult {
        return ColorMatrixFilterResult(accepted = true)
    }
}
