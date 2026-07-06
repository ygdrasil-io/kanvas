package org.graphiks.kanvas.gpu.renderer.filters

/** Blur style for normalized mask filters, mirrored from Kanvas BlurStyle. */
enum class NormalizedBlurStyle { NORMAL, SOLID, OUTER, INNER }

/** Normalized mask filter descriptor captured before route analysis. */
sealed interface NormalizedMaskFilter {
    /** Gaussian blur mask filter with style and sigma parameters. */
    data class Blur(val style: NormalizedBlurStyle, val sigma: Float) : NormalizedMaskFilter {
        init {
            require(sigma >= 0f && sigma.isFinite()) {
                "Blur sigma must be non-negative and finite"
            }
        }
    }
}
