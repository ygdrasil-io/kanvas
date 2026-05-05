package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkMaskFilter`](https://github.com/google/skia/blob/main/include/core/SkMaskFilter.h)
 * — a transformation of the rasteriser's coverage mask, applied
 * **after** the path effect / stroker but **before** the colour
 * composite. Examples : Gaussian blur, emboss, table lookup.
 *
 * In the kanvas-skia raster pipeline the per-draw sequence for paths
 * with a non-null `paint.maskFilter` is :
 *
 * ```
 *   path → pathEffect → stroker → rasterise to mask
 *        → maskFilter (blur etc.) → tint with paint colour → blend
 * ```
 *
 * **Phase 7c status** : the abstract base ships now alongside the
 * first concrete subclass, [SkBlurMaskFilter]. Other mask filters
 * (emboss, table lookup) follow in subsequent slices ; the
 * [filterMask] / [margin] contract is stable.
 *
 * Construct via [SkBlurMaskFilter.Make] (or future concrete
 * subclasses). The base type is exposed so client code can hold a
 * generic reference (`val mf: SkMaskFilter? = ...`).
 */
public abstract class SkMaskFilter {

    /**
     * Transform an alpha mask. [src] is a row-major 8-bit coverage
     * buffer of [w] × [h] entries (one byte per pixel, `0` =
     * uncovered, `255` = fully covered, in-between = AA). Returns
     * the new mask buffer of the same dimensions ; may be the same
     * object as [src] (mutated in place) or a fresh allocation.
     *
     * The buffer's `(0, 0)` is the top-left of the rasterisation
     * bounds expanded by [margin] pixels, so a blur kernel that
     * reads `±margin` neighbours never overflows.
     */
    public abstract fun filterMask(src: ByteArray, w: Int, h: Int): ByteArray

    /**
     * Pixels of padding the filter needs around the input mask.
     * For a Gaussian blur, this is `ceil(3 × sigma)` per side. The
     * device expands the path's device-space bounds by this much
     * before allocating the mask buffer.
     */
    public abstract fun margin(): Int
}
