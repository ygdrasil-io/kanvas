package org.graphiks.kanvas.paint

sealed interface SamplingOptions {
    data object NEAREST : SamplingOptions
    data object LINEAR : SamplingOptions

    data class Cubic(
        val B: Float,
        val C: Float,
    ) : SamplingOptions {
        companion object {
            val Mitchell = Cubic(1f / 3f, 1f / 3f)
            val CatmullRom = Cubic(0f, 1f / 2f)
        }
    }
}
