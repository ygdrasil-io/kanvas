package org.skia.foundation


import org.graphiks.math.SkColor4f
/**
 * Mirrors Skia's
 * [`SkLumaColorFilter`](https://github.com/google/skia/blob/main/include/effects/SkLumaColorFilter.h)
 * — the "luminance to alpha" filter used by SVG `<feColorMatrix
 * type="luminanceToAlpha">` and CSS `mask-mode: luminance`.
 *
 * Reduces the input pixel to `(0, 0, 0, luma)` where `luma` is the
 * Rec.709 luminance `0.2126·R + 0.7152·G + 0.0722·B`. This is the
 * complement of [SkColorFilters.Blend] with [SkBlendMode.kDstIn] —
 * it converts the colour information of the source into mask
 * coverage.
 *
 * Singleton instance via [Make] ; the filter is stateless, so all
 * callers share a single object.
 */
public object SkLumaColorFilter {

    /** Mirrors `SkLumaColorFilter::Make()`. */
    public fun Make(): SkColorFilter = INSTANCE

    private val INSTANCE: SkColorFilter = LumaImpl

    private object LumaImpl : SkColorFilter() {
        override fun filterColor4f(src: SkColor4f): SkColor4f {
            val luma = 0.2126f * src.fR + 0.7152f * src.fG + 0.0722f * src.fB
            return SkColor4f(0f, 0f, 0f, luma * src.fA)
        }

        override fun isAlphaUnchanged(): Boolean = false
    }
}
