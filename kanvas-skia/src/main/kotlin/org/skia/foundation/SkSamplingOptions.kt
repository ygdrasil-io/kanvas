package org.skia.foundation

/**
 * Mirrors Skia's [`SkSamplingOptions`](https://github.com/google/skia/blob/main/include/core/SkSamplingOptions.h).
 *
 * **Default** matches Skia: `filter = kNearest`, `mipmap = kNone` (the
 * legacy "no filter" sampling that Skia falls back to when the caller
 * passes `SkSamplingOptions()` with no arguments). Bicubic sampling is
 * intentionally omitted from this Phase — `useCubic` is always `false`.
 */
public data class SkSamplingOptions(
    public val filter: SkFilterMode = SkFilterMode.kNearest,
    public val mipmap: SkMipmapMode = SkMipmapMode.kNone,
) {
    public companion object {
        /** `SkSamplingOptions(kNearest, kNone)` — Skia's default constructor. */
        public val Default: SkSamplingOptions = SkSamplingOptions()

        public fun nearest(): SkSamplingOptions = SkSamplingOptions(SkFilterMode.kNearest)
        public fun linear(): SkSamplingOptions = SkSamplingOptions(SkFilterMode.kLinear)
    }
}
