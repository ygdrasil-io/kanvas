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

    public companion object {
        /**
         * Mirrors Skia's
         * [`SkPathEffect::MakeCompose(outer, inner)`](https://github.com/google/skia/blob/main/include/core/SkPathEffect.h).
         *
         * Returns a path effect that applies [inner] first, then
         * [outer] to the result : `compose.filterPath(p) ==
         * outer.filterPath(inner.filterPath(p))`.
         *
         * Mirrors Skia's null-handling convention :
         *  - `outer == null` ⇒ returns [inner] (or null if both are null).
         *  - `inner == null` ⇒ returns [outer].
         *  - both null ⇒ returns null (no-op chain).
         *
         * Used by the canonical `gm/patheffects.cpp::PathEffectGM`
         * pattern : `Compose(corner, dash)` produces a dashed-line
         * stroke whose remaining stipple corners are then rounded.
         */
        public fun MakeCompose(outer: SkPathEffect?, inner: SkPathEffect?): SkPathEffect? {
            if (outer == null) return inner
            if (inner == null) return outer
            return SkComposePathEffect(outer, inner)
        }

        /**
         * Mirrors Skia's
         * [`SkPathEffect::MakeSum(first, second)`](https://github.com/google/skia/blob/main/include/core/SkPathEffect.h).
         *
         * Returns a path effect that applies both [first] and [second]
         * to the input independently and concatenates the results
         * (every contour of `first.filterPath(p)` followed by every
         * contour of `second.filterPath(p)` in the output).
         *
         * Same null-handling convention as [MakeCompose].
         */
        public fun MakeSum(first: SkPathEffect?, second: SkPathEffect?): SkPathEffect? {
            if (first == null) return second
            if (second == null) return first
            return SkSumPathEffect(first, second)
        }
    }
}

/**
 * `outer ∘ inner` — applies [inner] first, then [outer]. When the
 * inner effect returns `null` (passthrough), the outer is applied to
 * the original [input]. When the outer returns `null`, the inner's
 * result is returned unchanged.
 */
internal class SkComposePathEffect(
    private val outer: SkPathEffect,
    private val inner: SkPathEffect,
) : SkPathEffect() {
    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        val mid = inner.filterPath(input, ctm) ?: input
        return outer.filterPath(mid, ctm) ?: mid
    }
}

/**
 * `first(p) ⊕ second(p)` — concatenates the verb streams of both
 * effects' outputs. When either effect returns `null` (passthrough),
 * its branch is filled by the original [input].
 */
internal class SkSumPathEffect(
    private val first: SkPathEffect,
    private val second: SkPathEffect,
) : SkPathEffect() {
    override fun filterPath(input: SkPath, ctm: SkMatrix): SkPath? {
        val a = first.filterPath(input, ctm) ?: input
        val b = second.filterPath(input, ctm) ?: input
        if (a.isEmpty() && b.isEmpty()) return null
        if (a.isEmpty()) return b
        if (b.isEmpty()) return a
        return SkPathBuilder()
            .addPath(a)
            .addPath(b)
            .detach()
    }
}
