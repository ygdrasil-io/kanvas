package org.skia.foundation

/**
 * Mirrors Skia's [`SkCubicResampler`](https://github.com/google/skia/blob/main/include/core/SkSamplingOptions.h)
 * — the (B, C) parameter pair that selects a member of the
 * Mitchell-Netravali bicubic-filter family.
 *
 * Used inside [SkSamplingOptions] (`cubic` field). When that field is
 * non-null, samplers must apply the bicubic kernel and ignore the
 * `filter` / `mipmap` fields (matches upstream's
 * `SkSamplingOptions(SkCubicResampler{B, C})` semantics — the cubic
 * resampler takes precedence over the linear/nearest mode).
 *
 * The two named presets cover the GMs in scope :
 *  - [Mitchell] : `B = 1/3, C = 1/3` — Mitchell-Netravali, the default
 *    "high quality" smoothing cubic.
 *  - [CatmullRom] : `B = 0, C = 1/2` — Catmull-Rom, slightly sharper /
 *    overshoots at edges.
 */
public data class SkCubicResampler(public val B: Float, public val C: Float) {
    public companion object {
        public val Mitchell: SkCubicResampler = SkCubicResampler(1f / 3f, 1f / 3f)
        public val CatmullRom: SkCubicResampler = SkCubicResampler(0f, 0.5f)
    }
}
