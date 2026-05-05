package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkColorFilter`](https://github.com/google/skia/blob/main/include/core/SkColorFilter.h)
 * — an immutable function `SkColor4f → SkColor4f` applied to every
 * source pixel after rasterisation but before the [SkPaint.blendMode]
 * composite.
 *
 * In the kanvas-skia raster pipeline the per-pixel sequence is :
 *
 * ```
 *   shader (or paint.color) → coverage → colorFilter → blendMode → dst
 * ```
 *
 * Filters operate on **non-premultiplied** floating-point colour in
 * `[0, 1]`. The device handles premul/unpremul boundaries on either
 * side : input pixels are unpremultiplied before [filterColor4f],
 * the result is re-premultiplied (× output alpha) before the blend.
 *
 * Filters are **immutable** — implementations may share state, never
 * mutate it after construction. This lets the device cache the output
 * of [filterColor4f] when the source colour is constant (solid-paint
 * draws), evaluating the filter exactly once instead of per-pixel.
 *
 * Construct via the [SkColorFilters] factory or via concrete
 * implementations like [SkLumaColorFilter].
 */
public abstract class SkColorFilter {

    /**
     * Apply the filter to a single colour. Both input and output are
     * **non-premultiplied** [SkColor4f] in the destination working
     * colour space. Implementations may produce out-of-`[0, 1]` values
     * (matrix filters with negative coefficients, additive lighting,
     * etc.) — the device clamps before storing into the bitmap.
     */
    public abstract fun filterColor4f(src: SkColor4f): SkColor4f

    /**
     * Convenience overload : decodes an 8-bit [SkColor], delegates to
     * [filterColor4f], encodes back. Useful for one-off colour
     * transforms outside the rasteriser hot path.
     */
    public open fun filterColor(c: SkColor): SkColor {
        val src = SkColor4f(
            SkColorGetR(c) / 255f,
            SkColorGetG(c) / 255f,
            SkColorGetB(c) / 255f,
            SkColorGetA(c) / 255f,
        )
        val out = filterColor4f(src)
        return SkColorSetARGB(
            (out.fA.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (out.fR.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (out.fG.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
            (out.fB.coerceIn(0f, 1f) * 255f + 0.5f).toInt(),
        )
    }

    /**
     * Mirrors Skia's `SkColorFilter::makeComposed`. Returns a new
     * filter that applies [inner] first, then `this` to the result.
     */
    public open fun makeComposed(inner: SkColorFilter): SkColorFilter =
        SkComposeColorFilter(outer = this, inner = inner)

    /**
     * Mirrors Skia's `SkColorFilter::isAlphaUnchanged`. `true` if this
     * filter never modifies the alpha channel of its input —
     * optimisation hint that lets the device skip the unpremul/repremul
     * round-trip in some hot paths. Default `false` ; concrete
     * implementations override when known.
     */
    public open fun isAlphaUnchanged(): Boolean = false
}
