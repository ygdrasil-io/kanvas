package org.skia.effects

import org.skia.math.SkColor4f
import org.skia.foundation.SkColorFilter

/**
 * Mirrors Skia's
 * [`SkHighContrastConfig`](https://github.com/google/skia/blob/main/include/effects/SkHighContrastFilter.h)
 * — configuration struct for [SkHighContrastFilter].
 *
 * The filter applies up to three transforms, in this order :
 *  1. Optional grayscale (`grayscale = true`) — uses the
 *     `ITU-R BT.709` luma weights (`0.2126·R + 0.7152·G + 0.0722·B`).
 *  2. Optional inversion ([invertStyle]) — either RGB inversion
 *     ("brightness") or HSL-L inversion ("lightness").
 *  3. Linear contrast adjustment ([contrast] in `[-1, 1]`, `0` = no-op).
 *
 * The struct itself only carries data ; the actual transform lives in
 * [SkHighContrastFilter.Make] which produces a [SkColorFilter] that
 * applies the configured pipeline.
 */
public data class SkHighContrastConfig(
    /** If `true`, the colour is converted to grayscale before further steps. */
    public val grayscale: Boolean = false,
    /** Whether to invert RGB, HSL-L, or neither before contrast scaling. */
    public val invertStyle: InvertStyle = InvertStyle.kNoInvert,
    /**
     * Linear contrast adjustment in `[-1, 1]`. `0` is a no-op ; `1` would
     * be a hard step (clamped to `1 - ε` internally to avoid divide-by-zero
     * in upstream's `(1+c)/(1-c)` slope formula) ; `-1` collapses every
     * channel to `0.5` (clamped to `-1 + ε` for the same reason).
     */
    public val contrast: Float = 0f,
) {
    /**
     * Mirrors Skia's
     * [`SkHighContrastConfig::InvertStyle`](https://github.com/google/skia/blob/main/include/effects/SkHighContrastFilter.h).
     *
     * - [kNoInvert] — colour passes through unchanged at the invert step.
     * - [kInvertBrightness] — `color = 1 - color` (negate each RGB channel).
     * - [kInvertLightness] — convert to HSL, invert L, convert back.
     */
    public enum class InvertStyle { kNoInvert, kInvertBrightness, kInvertLightness }

    /**
     * Returns `true` if every field is in its supported range :
     *  - [invertStyle] is one of the three enum values (always true in
     *    Kotlin — kept for API parity).
     *  - [contrast] is in `[-1, 1]`.
     */
    public fun isValid(): Boolean = contrast in -1f..1f
}

/**
 * Mirrors Skia's
 * [`SkHighContrastFilter`](https://github.com/google/skia/blob/main/include/effects/SkHighContrastFilter.h)
 * — colour filter that boosts contrast for low-vision users by applying
 * the [SkHighContrastConfig] pipeline (optional grayscale, optional
 * inversion, linear contrast scale).
 *
 * The contrast scale follows upstream's `(1 + c) / (1 - c)` slope formula
 * around the mid-grey point `0.5`. To avoid the divide-by-zero at
 * `c = ±1`, the contrast is clamped to `±(1 − ε)` before the slope is
 * computed.
 *
 * Operates on non-premultiplied [SkColor4f] in working-space ; alpha is
 * passed through unchanged (matching upstream's
 * `WithWorkingFormat(unpremul)` wrapper which preserves alpha).
 */
public object SkHighContrastFilter {

    /**
     * Mirrors Skia's `SkHighContrastFilter::Make`.
     *
     * @return a [SkColorFilter] that applies the [config] pipeline, or
     *   `null` if [SkHighContrastConfig.isValid] returned `false`
     *   (e.g. out-of-range `contrast`).
     */
    public fun Make(config: SkHighContrastConfig): SkColorFilter? {
        if (!config.isValid()) return null
        return HighContrastImpl(config)
    }

    private class HighContrastImpl(private val config: SkHighContrastConfig) : SkColorFilter() {
        // Pre-compute the contrast slope around 0.5 : out = lerp(0.5, c, m).
        // Pin c to (-1+ε, 1-ε) to avoid divide-by-zero in the slope.
        private val slope: Float = run {
            val c = config.contrast.coerceIn(-1f + EPS, 1f - EPS)
            (1f + c) / (1f - c)
        }

        override fun filterColor4f(src: SkColor4f): SkColor4f {
            var r = src.fR
            var g = src.fG
            var b = src.fB

            // Step 1: grayscale via BT.709 luma.
            if (config.grayscale) {
                val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
                r = y; g = y; b = y
            }

            // Step 2: optional inversion.
            when (config.invertStyle) {
                SkHighContrastConfig.InvertStyle.kNoInvert -> { /* no-op */ }
                SkHighContrastConfig.InvertStyle.kInvertBrightness -> {
                    r = 1f - r; g = 1f - g; b = 1f - b
                }
                SkHighContrastConfig.InvertStyle.kInvertLightness -> {
                    val hsl = rgbToHsl(r, g, b)
                    hsl[2] = 1f - hsl[2]
                    val rgb = hslToRgb(hsl[0], hsl[1], hsl[2])
                    r = rgb[0]; g = rgb[1]; b = rgb[2]
                }
            }

            // Step 3: linear contrast around 0.5, then clamp to [0, 1].
            r = ((r - 0.5f) * slope + 0.5f).coerceIn(0f, 1f)
            g = ((g - 0.5f) * slope + 0.5f).coerceIn(0f, 1f)
            b = ((b - 0.5f) * slope + 0.5f).coerceIn(0f, 1f)

            return SkColor4f(r, g, b, src.fA)
        }

        override fun isAlphaUnchanged(): Boolean = true

        private companion object {
            /**
             * Pin radius for the contrast clamp, matching upstream's
             * `FLT_EPSILON` (the smallest positive normal float).
             */
            const val EPS: Float = 1.1920929e-7f

            /**
             * Convert RGB (each in `[0, 1]`) to HSL (h in `[0, 1)`, s and l in `[0, 1]`).
             * Mirrors the SkSL helper `$high_contrast_rgb_to_hsl` from
             * `sksl_rt_shader.sksl`.
             */
            fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
                val mx = maxOf(r, g, b)
                val mn = minOf(r, g, b)
                val d = mx - mn
                val sum = mx + mn
                val l = sum * 0.5f
                if (d == 0f) return floatArrayOf(0f, 0f, l)
                val s = if (l > 0.5f) d / (2f - sum) else d / sum
                val invd = 1f / d
                val h6 = when {
                    r >= g && r >= b -> {
                        val v = invd * (g - b) + (if (g < b) 6f else 0f)
                        v
                    }
                    g >= b -> invd * (b - r) + 2f
                    else -> invd * (r - g) + 4f
                }
                return floatArrayOf(h6 / 6f, s, l)
            }

            /**
             * Convert HSL to RGB. Mirrors the standard `hsl_to_rgb` helper
             * used by the upstream SkSL pipeline.
             */
            fun hslToRgb(h: Float, s: Float, l: Float): FloatArray {
                if (s == 0f) return floatArrayOf(l, l, l)
                val q = if (l < 0.5f) l * (1f + s) else l + s - l * s
                val p = 2f * l - q
                return floatArrayOf(
                    hueToRgb(p, q, h + 1f / 3f),
                    hueToRgb(p, q, h),
                    hueToRgb(p, q, h - 1f / 3f),
                )
            }

            fun hueToRgb(p: Float, q: Float, tt: Float): Float {
                var t = tt
                if (t < 0f) t += 1f
                if (t > 1f) t -= 1f
                if (t < 1f / 6f) return p + (q - p) * 6f * t
                if (t < 1f / 2f) return q
                if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6f
                return p
            }
        }
    }
}
