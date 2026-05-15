package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkRect

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
     * Phase R1-C — mirrors Skia's
     * [`SkImageFilter::computeFastBounds`](https://github.com/google/skia/blob/main/include/core/SkImageFilter.h#L90).
     * Given a source rectangle [src], return the conservative
     * device-space bounds the filter's output will cover. Filters that
     * grow / displace the image (e.g. blur, offset) override this to
     * inflate / translate the rect ; the base-class default mirrors
     * Skia's "union of all input bounds" semantics — for the kanvas-skia
     * port the conservative fallback is `src` itself (we don't model
     * input-DAG bounds here), which is the right answer for filters
     * whose output footprint matches the input.
     *
     * The result is used by [SkPaint.computeFastBounds] to give the
     * rasteriser a quickReject rectangle that includes every pixel the
     * filter pipeline could possibly draw. Used by
     * `gm/filterfastbounds.cpp`.
     */
    public open fun computeFastBounds(src: SkRect): SkRect = src

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

    /**
     * R-final.2 — mirrors Skia's
     * [`SkImageFilter::makeWithLocalMatrix`](https://github.com/google/skia/blob/main/src/effects/imagefilters/...)
     * (factored as `SkLocalMatrixImageFilter::Make(matrix, this)` in
     * upstream).
     *
     * Returns a new filter that, when invoked, behaves as if the
     * canvas CTM had been pre-concatenated with [matrix] before the
     * child filter ran. The wrapped filter sees the augmented CTM in
     * its own [filterImage], so its sampling coordinates are
     * `(matrix · ctm)^-1` relative to device space.
     *
     * **Identity** : a no-op — returns `this` directly.
     *
     * **Singular** : a matrix that has no inverse can't be undone, so
     * we return `this` unchanged (mirrors upstream's `nullptr` short-
     * circuit when [SkMatrix.invert] fails).
     *
     * **Folding** : if `this` is already a [SkLocalMatrixImageFilter],
     * the two matrices fold into one wrapper rather than nesting —
     * see [SkLocalMatrixImageFilter.fold] in the implementation.
     */
    public fun makeWithLocalMatrix(matrix: SkMatrix): SkImageFilter {
        if (matrix.isIdentity) return this
        if (matrix.invert() == null) return this
        if (this is SkLocalMatrixImageFilter) {
            // Fold: the new outer matrix multiplies on the left of the
            // existing wrapper matrix. ConcatLocalMatrices(parent,
            // child) = parent · child (matches upstream
            // SkShaderBase::ConcatLocalMatrices semantics — same
            // convention reused for image filters).
            return SkLocalMatrixImageFilter(matrix.preConcat(localMatrix), child)
        }
        return SkLocalMatrixImageFilter(matrix, this)
    }
}

/**
 * R-final.2 — mirrors Skia's
 * [`SkLocalMatrixImageFilter`](https://github.com/google/skia/blob/main/src/core/SkLocalMatrixImageFilter.cpp).
 *
 * Wraps a child image filter and pre-concatenates [localMatrix] into
 * the canvas matrix passed to [filterImage]. The child filter receives
 * the augmented CTM, so any per-draw matrix-dependent setup (offset
 * scaling, blur sigma scaling, …) sees the same coordinate system as
 * if the caller had `canvas.concat(localMatrix)` before invoking the
 * filter.
 *
 * **CTM augmentation only** — we do **not** rasterise the source image
 * through the local matrix. That mirrors upstream's contract :
 * `localMatrix` adjusts the *parameter space* of the child filter (so
 * an `Offset(8, 8)` child sees its 8-px displacement scaled by the
 * outer matrix) without re-sampling the source image itself. Filters
 * whose output footprint depends on the matrix (e.g. blur sigma scaled
 * by max-scale, drop-shadow displacement) react automatically because
 * they read their `ctm` argument inside [filterImage].
 *
 * Public via the [SkImageFilter.makeWithLocalMatrix] factory ; not
 * constructible directly so call sites cannot bypass the folding step.
 */
internal class SkLocalMatrixImageFilter(
    val localMatrix: SkMatrix,
    val child: SkImageFilter,
) : SkImageFilter() {

    override fun filterImage(src: SkImage, ctm: SkMatrix): FilterResult =
        child.filterImage(src, ctm.preConcat(localMatrix))

    override fun computeFastBounds(src: SkRect): SkRect {
        // Mirror upstream `SkLocalMatrixImageFilter::computeFastBounds` :
        // map the source bounds by `localMatrix^-1`, ask the child for
        // its bounds in that intermediate space, then map back by
        // `localMatrix`. The inverse is guaranteed to exist because
        // [SkImageFilter.makeWithLocalMatrix] rejects singular matrices.
        val inv = localMatrix.invert() ?: return child.computeFastBounds(src)
        val localBounds = inv.mapRect(src)
        return localMatrix.mapRect(child.computeFastBounds(localBounds))
    }
}
