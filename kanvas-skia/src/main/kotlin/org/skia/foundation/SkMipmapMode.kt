package org.skia.foundation

/**
 * Mirrors Skia's [`SkMipmapMode`](https://github.com/google/skia/blob/main/include/core/SkSamplingOptions.h).
 * Mipmap sampling is not implemented in the kanvas-skia raster pipeline yet —
 * the enum exists so [SkSamplingOptions] can mirror upstream's signature.
 */
public enum class SkMipmapMode {
    kNone,
    kNearest,
    kLinear,
}
