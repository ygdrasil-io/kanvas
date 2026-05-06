package org.skia.foundation

import org.skia.math.SkMatrix

/**
 * Mirrors Skia's
 * [`SkImageFilter`](https://github.com/google/skia/blob/main/include/core/SkImageFilter.h)
 * — an immutable `SkImage → SkImage` transformation applied **between**
 * the rasterised source pixels and the [SkPaint.colorFilter] /
 * [SkPaint.blendMode] composite stages.
 *
 * In the kanvas-skia raster pipeline the per-draw sequence for
 * `drawImage` / `saveLayer` with a non-null `paint.imageFilter` is :
 *
 * ```
 *   src image (or rasterised layer)
 *     → imageFilter.filterImage(src, ctm) → (image, offset)
 *     → colorFilter (per-pixel, sRGB pipeline — Phase 7e)
 *     → blendMode → dst
 * ```
 *
 * The filter may :
 *  - **translate** the result (e.g. [SkImageFilters.Offset]) — returned
 *    via the `(offsetX, offsetY)` field of [FilterResult].
 *  - **resize** the result (e.g. blur grows the image by `±radius` per
 *    side — Phase 7d.2).
 *  - **chain** multiple filters together (e.g. [SkImageFilters.Compose]).
 *
 * **Phase 7d.1 status** : the abstract base ships now alongside three
 * concrete filters — [SkImageFilters.Offset],
 * [SkImageFilters.ColorFilter], and [SkImageFilters.Compose]. The
 * `Blur` / `MatrixTransform` / `DropShadow` family follow in
 * Phase 7d.2 (~550 LOC). The `filterImage` contract is stable.
 *
 * Construct via the [SkImageFilters] factory namespace ; the base
 * type is exposed so client code can hold a generic reference
 * (`val filter: SkImageFilter? = ...`).
 */
public abstract class SkImageFilter {

    /**
     * Apply the filter to [src], returning a new image and the
     * device-space offset by which the result should be repositioned
     * relative to [src]'s top-left corner.
     *
     * The [ctm] is the canvas matrix at draw time. Filters that need
     * device-space precision (e.g. an `Offset(dx, dy)` filter wants
     * the displacement to scale with the CTM) read it ; filters that
     * are purely image-space ignore it.
     *
     * Implementations must not mutate [src] (it's an immutable
     * [SkImage]) ; returning the same object as a no-op is allowed
     * when the filter has no effect (e.g. zero-displacement Offset).
     */
    public abstract fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult

    /**
     * Result of [filterImage] : the transformed image plus the
     * device-space `(offsetX, offsetY)` displacement of its top-left
     * corner relative to [src]'s top-left.
     *
     * Example — `Offset(10, 5).filterImage(src, identity)` returns
     * `FilterResult(src, 10, 5)` : the original pixels, but the
     * caller should draw them 10 px right + 5 px down.
     */
    public data class FilterResult(
        public val image: SkImage,
        public val offsetX: Int = 0,
        public val offsetY: Int = 0,
    )
}
