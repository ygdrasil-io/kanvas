/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `include/pathops/SkPathOps.h` (`enum SkPathOp`).
 */
package org.skia.pathops

/**
 * Logical operations that can be performed when combining two paths.
 *
 * Mirrors Skia's
 * [`enum SkPathOp`](https://github.com/google/skia/blob/main/include/pathops/SkPathOps.h#L23).
 *
 * Variant naming follows Skia's `kXxx_SkPathOp` convention, dropped the
 * `_SkPathOp` suffix since Kotlin enums don't need the global-scope
 * disambiguation that C does.
 */
public enum class SkPathOp {
    /** Subtract the op path from the first path. */
    kDifference,

    /** Intersect the two paths. */
    kIntersect,

    /** Union (inclusive-or) the two paths. */
    kUnion,

    /** Exclusive-or the two paths. */
    kXOR,

    /** Subtract the first path from the op path. */
    kReverseDifference,
}
