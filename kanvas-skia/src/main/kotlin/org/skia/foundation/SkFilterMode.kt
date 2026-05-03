package org.skia.foundation

/**
 * Mirrors Skia's [`SkFilterMode`](https://github.com/google/skia/blob/main/include/core/SkSamplingOptions.h).
 * Selects the texel filter used when an image is sampled at a non-integer
 * coordinate.
 */
public enum class SkFilterMode {
    /** Pick the texel whose center is nearest the sample point. */
    kNearest,

    /** Bilinear interpolation between the 4 neighbouring texels. */
    kLinear,
}
