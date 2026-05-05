package org.skia.foundation

import org.skia.math.SkMatrix

/**
 * Mirrors Skia's
 * [`SkPathEffect`](https://github.com/google/skia/blob/main/include/core/SkPathEffect.h)
 * — an immutable `SkPath → SkPath` transformation applied **before**
 * the stroker. Examples : dashing (decompose path into stipples),
 * corner rounding, point spreading, 1D / 2D path tiling.
 *
 * In the kanvas-skia raster pipeline the per-draw sequence for paths
 * is :
 *
 * ```
 *   path → pathEffect.filterPath() → stroker.stroke() → fill
 * ```
 *
 * Path effects are **immutable** — implementations may share state,
 * never mutate it after construction. This lets callers cache the
 * filtered path between draws when the input path and CTM are
 * unchanged.
 *
 * **Phase 7b status** : the abstract base ships now alongside the
 * first concrete subclass, [SkDashPathEffect]. Corner / discrete /
 * 1D / 2D / compose / sum follow in subsequent slices ; the
 * [filterPath] contract is stable.
 *
 * Construct via the [SkDashPathEffect.Make] factory (or future
 * concrete subclasses). The base type is exposed so client code
 * can hold a generic reference (`val pe: SkPathEffect? = ...`).
 */
public abstract class SkPathEffect {

    /**
     * Transform the input path into a new one. Returns `null` when
     * the effect can't apply — e.g. degenerate intervals on a dash
     * effect, or an empty input. Returning `null` tells the device
     * to use the original [input] unchanged.
     *
     * The [ctm] is the current canvas matrix at draw time. Effects
     * that need to operate in device-space (e.g. dash with a fixed
     * device-pixel pattern under arbitrary CTM scale) read the matrix
     * to compensate ; effects that don't need it ignore it.
     *
     * Implementations must not mutate [input] (it's immutable in
     * any case — the parameter is `val`-shaped). Returning the same
     * object as a no-op is allowed.
     */
    public abstract fun filterPath(input: SkPath, ctm: SkMatrix): SkPath?
}
