package org.skia.foundation

/**
 * Mirrors Skia's [`SkSamplingOptions`](https://github.com/google/skia/blob/main/include/core/SkSamplingOptions.h).
 *
 * **Default** matches Skia: `filter = kNearest`, `mipmap = kNone`,
 * `cubic = null` (the legacy "no filter" sampling that Skia falls back
 * to when the caller passes `SkSamplingOptions()` with no arguments).
 *
 * Phase G2 — bicubic sampling (Mitchell / Catmull-Rom / arbitrary B-C
 * cubic) is selected by setting [cubic] to a non-null
 * [SkCubicResampler]. When [cubic] is non-null it **takes precedence**
 * over [filter] and [mipmap], matching upstream's `useCubic` branch in
 * the sampling-options-dispatch (the constructor
 * `SkSamplingOptions(SkCubicResampler{B, C})` forces
 * `useCubic = true`).
 */
public data class SkSamplingOptions(
    public val filter: SkFilterMode = SkFilterMode.kNearest,
    public val mipmap: SkMipmapMode = SkMipmapMode.kNone,
    public val cubic: SkCubicResampler? = null,
) {

    /**
     * Convenience constructor mirroring upstream's
     * `SkSamplingOptions(SkCubicResampler{B, C})`. The cubic resampler
     * sets `useCubic = true` and the linear/nearest mode is irrelevant
     * — we record `kNearest` / `kNone` as a placeholder.
     */
    public constructor(cubic: SkCubicResampler) : this(SkFilterMode.kNearest, SkMipmapMode.kNone, cubic)

    public companion object {
        /** `SkSamplingOptions(kNearest, kNone, cubic = null)` — Skia's default. */
        public val Default: SkSamplingOptions = SkSamplingOptions()

        public fun nearest(): SkSamplingOptions = SkSamplingOptions(SkFilterMode.kNearest)
        public fun linear(): SkSamplingOptions = SkSamplingOptions(SkFilterMode.kLinear)
    }
}
