/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `include/pathops/SkPathOps.h` (free functions in the
 * top-level `skia` namespace).
 *
 * # Phase D1.0 — package skeleton + TightBounds shim
 *
 * This file ships the public API surface of `SkPathOps` (chantier D1
 * of the raster-completion plan). Only `TightBounds` is implemented
 * in this slice — it delegates to [SkPath.computeTightBounds] which
 * already exists and is fully tested.
 *
 * `Op`, `Simplify`, `AsWinding` return `null` and log a `NotImplementedError`
 * to signal "not yet ported". They will be implemented in subsequent
 * D1.x slices :
 *  - **D1.1** : segment / intersection primitives.
 *  - **D1.2** : op contour assembly + winding propagation.
 *  - **D1.3** : top-level `Op` / `Simplify` / `AsWinding` algorithms.
 *
 * The reason this slice exists separately is to commit the package
 * skeleton, the public contracts, and the immediate-win `TightBounds`
 * delegate without blocking on the (~9 000 LOC) intersection machinery.
 */
package org.skia.pathops

import org.skia.foundation.SkPath
import org.skia.math.SkRect

/**
 * Pathops free functions. Mirrors Skia's `include/pathops/SkPathOps.h`.
 *
 * Upstream lives in the `skia::` namespace as free functions ; we
 * group them into an `object` for Kotlin idiom while keeping the names
 * verbatim (`SkPathOps.Op(a, b, op)`, `SkPathOps.Simplify(p)`).
 */
public object SkPathOps {

    /**
     * Returns the result of applying the boolean [op] to [one] and [two].
     *
     * The resulting path is constructed from non-overlapping contours.
     * Curve order is reduced where possible (cubics may become quadratics,
     * quadratics may become lines). Returns `null` if the operation
     * fails — typically due to numerical robustness issues on degenerate
     * inputs.
     *
     * Mirrors Skia's
     * [`Op(const SkPath&, const SkPath&, SkPathOp)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L47).
     *
     * **Phase D1.0** : not yet implemented ; returns `null`.
     */
    public fun Op(one: SkPath, two: SkPath, op: SkPathOp): SkPath? {
        // TODO(D1.3) : implement the boolean operation. Requires
        // D1.1 (intersections) + D1.2 (contour assembly).
        return null
    }

    /**
     * Returns a path with non-overlapping contours equivalent to [path].
     *
     * Resolves self-intersections, normalizes the fill type, and reduces
     * curve order where possible. Returns `null` on failure.
     *
     * Mirrors Skia's
     * [`Simplify(const SkPath&)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L66).
     *
     * **Phase D1.0** : not yet implemented ; returns `null`.
     */
    public fun Simplify(path: SkPath): SkPath? {
        // TODO(D1.3) : implement Simplify. Same machinery as Op,
        // restricted to a single input.
        return null
    }

    /**
     * Returns the tight (curve-aware) bounding box of [path], or `null`
     * if the bounds are not finite (e.g. NaN coordinates anywhere).
     *
     * Mirrors Skia's
     * [`TightBounds(const SkPath&, SkRect*)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L84)
     * — itself a deprecated thin wrapper around `SkPath::computeTightBounds()`.
     *
     * Implemented here as a thin delegate to [SkPath.computeTightBounds]
     * (full curve-extrema math already shipped in the SkPath foundation).
     */
    @Deprecated(
        message = "Use SkPath.computeTightBounds() directly. Mirrors the [[deprecated]] " +
            "marker on the upstream Skia API.",
        replaceWith = ReplaceWith("path.computeTightBounds()"),
    )
    public fun TightBounds(path: SkPath): SkRect? {
        val rect = path.computeTightBounds()
        return if (rect.isFinite()) rect else null
    }

    /**
     * Returns a path with `kWinding` fill type covering the same area
     * as [path] (which typically has `kEvenOdd` fill).
     *
     * Does not detect self-intersecting / overlapping contours ; in
     * those cases the result may not fill the same area.
     * Returns `null` on failure.
     *
     * Mirrors Skia's
     * [`AsWinding(const SkPath&)`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L102).
     *
     * **Phase D1.0** : not yet implemented ; returns `null`.
     */
    public fun AsWinding(path: SkPath): SkPath? {
        // TODO(D1.3) : implement AsWinding. Independent of Op but
        // shares the contour walker.
        return null
    }
}
