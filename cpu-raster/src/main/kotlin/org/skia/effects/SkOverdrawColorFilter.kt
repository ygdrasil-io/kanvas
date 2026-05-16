package org.skia.effects

import org.skia.foundation.SkColor
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorGetA
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SK_ColorTRANSPARENT

/**
 * Mirrors Skia's
 * [`SkOverdrawColorFilter`](https://github.com/google/skia/blob/main/include/effects/SkOverdrawColorFilter.h)
 * — a colour filter that maps an input pixel's **alpha channel** (interpreted as
 * a "number of overdraws" counter in `1..6`) to one of six explicit ARGB
 * colours.
 *
 * Mapping (input alpha in `[0, 255]`) :
 *
 * ```
 *  α == 0       → SK_ColorTRANSPARENT
 *  α == 1       → colors[0]
 *  α == 2       → colors[1]
 *   ...
 *  α >= 6       → colors[5]
 * ```
 *
 * In kanvas-skia the filter is consumed by [org.skia.foundation.SkPaint.colorFilter]
 * and applied per-pixel via [org.skia.core.SkBitmapDevice]'s `applyColorFilter`
 * infrastructure, identical to every other [SkColorFilter].
 *
 * Upstream's runtime-effect implementation operates in floating-point alpha
 * (`alpha < 0.5/255 → color0 … else → color5`, with `color0..color5` mapping
 * to alpha 0..5). The `:kanvas-skia` port adopts the
 * "number of overdraws" semantic specified by the GM port plan — alpha 0 →
 * fully-transparent, alpha 1..6 → `colors[α-1]` — to align with how the
 * filter is used by the overdraw canvas (`SkOverdrawCanvas` increments alpha
 * once per drawn fragment, so 0 means "untouched" and ≥1 means "at least one
 * draw").
 */
public object SkOverdrawColorFilter {

    /** Number of explicit colour entries the filter accepts. */
    public const val kNumColors: Int = 6

    /**
     * Mirrors Skia's `SkOverdrawColorFilter::MakeWithSkColors`.
     *
     * @param colors exactly six packed ARGB colours. `colors[i]` is the
     *   output when the input alpha equals `i + 1`. Inputs with alpha `0`
     *   produce [SK_ColorTRANSPARENT]; inputs with alpha `>= 6` produce
     *   `colors[5]`.
     *
     * @throws IllegalArgumentException if `colors.size != 6`.
     */
    public fun MakeWithSkColors(colors: IntArray): SkColorFilter {
        require(colors.size == kNumColors) {
            "SkOverdrawColorFilter expects $kNumColors colors, got ${colors.size}"
        }
        return OverdrawImpl(colors.copyOf())
    }

    /**
     * Per-pixel core. Operates on the input's quantised alpha so that the
     * 1..6 indexing matches Skia's `SkOverdrawCanvas` (which packs the
     * overdraw counter into the alpha byte at 8-bit precision).
     */
    private class OverdrawImpl(private val colors: IntArray) : SkColorFilter() {

        /**
         * Cache 8-bit-decoded ARGB floats per palette entry — the filter is
         * immutable so the inputs never change after construction.
         */
        private val colors4f: Array<SkColor4f> = Array(kNumColors) { i ->
            val c = colors[i]
            SkColor4f(
                SkColorGetR(c) / 255f,
                SkColorGetG(c) / 255f,
                SkColorGetB(c) / 255f,
                SkColorGetA(c) / 255f,
            )
        }

        override fun filterColor4f(src: SkColor4f): SkColor4f {
            // Quantise the float alpha back to an 8-bit count. round-half-up
            // mirrors the storage round-trip every other filter performs.
            val a = (src.fA.coerceIn(0f, 1f) * 255f + 0.5f).toInt().coerceIn(0, 255)
            if (a == 0) return TRANSPARENT_4F
            val idx = (a - 1).coerceIn(0, kNumColors - 1)
            return colors4f[idx]
        }

        override fun filterColor(c: SkColor): SkColor {
            val a = SkColorGetA(c)
            if (a == 0) return SK_ColorTRANSPARENT
            val idx = (a - 1).coerceIn(0, kNumColors - 1)
            return colors[idx]
        }

        override fun isAlphaUnchanged(): Boolean = false

        private companion object {
            val TRANSPARENT_4F = SkColor4f(0f, 0f, 0f, 0f)
        }
    }
}
