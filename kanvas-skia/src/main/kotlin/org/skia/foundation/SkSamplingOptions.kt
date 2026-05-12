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
    /**
     * Phase G10 — when non-zero, the sampler uses an N-tap shortcut
     * along the texture-space ellipse major axis with `N = maxAniso`.
     * `0` means "no aniso" (regular point / bilinear / mip sampling
     * applies). Construct via [Aniso] to enforce the clamping rules.
     */
    public val maxAniso: Int = 0,
) {

    /**
     * Convenience constructor mirroring upstream's
     * `SkSamplingOptions(SkCubicResampler{B, C})`. The cubic resampler
     * sets `useCubic = true` and the linear/nearest mode is irrelevant
     * — we record `kNearest` / `kNone` as a placeholder.
     */
    public constructor(cubic: SkCubicResampler) : this(SkFilterMode.kNearest, SkMipmapMode.kNone, cubic, 0)

    /** `true` when this sampler is an anisotropic sampler (see [Aniso]). */
    public val useAniso: Boolean get() = maxAniso > 0

    public companion object {
        /** `SkSamplingOptions(kNearest, kNone, cubic = null)` — Skia's default. */
        public val Default: SkSamplingOptions = SkSamplingOptions()

        public fun nearest(): SkSamplingOptions = SkSamplingOptions(SkFilterMode.kNearest)
        public fun linear(): SkSamplingOptions = SkSamplingOptions(SkFilterMode.kLinear)

        /**
         * Phase G10 — mirrors Skia's `SkSamplingOptions::Aniso(int maxAniso)`
         * factory. Selects anisotropic sampling with up to [maxAniso] taps
         * along the texture-space ellipse major axis. Upstream clamps
         * `maxAniso` to `>= 1` ; kanvas-skia matches that clamp.
         *
         * The raster implementation is an N-tap shortcut along the
         * major-axis direction averaged with a per-tap mip-LOD pick
         * derived from the minor-axis footprint — pragmatic for a CPU
         * raster path (full elliptical weighted average kernels are
         * not yet ported).
         */
        public fun Aniso(maxAniso: Int): SkSamplingOptions {
            val n = maxAniso.coerceAtLeast(1)
            return SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear, cubic = null, maxAniso = n)
        }
    }
}
