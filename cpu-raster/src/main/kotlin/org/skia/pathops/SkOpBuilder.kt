/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `include/pathops/SkPathOps.h` (`class SK_API SkOpBuilder`).
 */
package org.skia.pathops

import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder

/**
 * Performs a series of path operations, optimized for unioning many
 * paths together.
 *
 * Mirrors Skia's
 * [`SkOpBuilder`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L115).
 *
 * Usage :
 * ```
 * val builder = SkOpBuilder()
 * builder.add(circle, SkPathOp.kUnion)
 * builder.add(square, SkPathOp.kIntersect)
 * val result = builder.resolve()
 * ```
 *
 * The builder is empty before the first add, so the result of a single
 * `add(p, op)` is `(emptyPath OP p)`.
 *
 * **Phase D1.0** : skeleton only — [resolve] returns `null`. Will be
 * filled in in D1.3 once the underlying [SkPathOps.Op] is implemented.
 */
public class SkOpBuilder {

    private val paths: MutableList<SkPath> = mutableListOf()
    private val ops: MutableList<SkPathOp> = mutableListOf()

    /**
     * Add one or more paths and their operand. The builder is empty
     * before the first path is added, so the result of a single add
     * is `(emptyPath OP path)`.
     *
     * Mirrors `SkOpBuilder::add(const SkPath&, SkPathOp)`.
     */
    public fun add(path: SkPath, op: SkPathOp) {
        paths += path
        ops += op
    }

    /**
     * Compute the sum of all paths and operands and reset the builder
     * to its initial state. Returns `null` on failure.
     *
     * Mirrors `SkOpBuilder::resolve()`
     * (`src/pathops/SkOpBuilder.cpp:145`). Implements the
     * chained-`Op` fallback path : iteratively folds each `(path, op)`
     * pair into the running result via [SkPathOps.Op]. The upstream
     * all-union fast path (concatenate then Simplify, with a
     * convex-bounds short-circuit and `FixWinding`) is **not** ported
     * yet — it's an optimisation, not a correctness requirement.
     *
     * Empty builder → empty path. Single entry → `Op(emptyPath, path,
     * op)`, mirroring upstream's handling of a single `add`.
     */
    public fun resolve(): SkPath? {
        if (paths.isEmpty()) {
            reset()
            return SkPathBuilder().detach()
        }
        var result: SkPath = paths[0]
        for (i in 1 until paths.size) {
            val next = SkPathOps.Op(result, paths[i], ops[i]) ?: run {
                reset()
                return null
            }
            result = next
        }
        reset()
        return result
    }

    private fun reset() {
        paths.clear()
        ops.clear()
    }
}
