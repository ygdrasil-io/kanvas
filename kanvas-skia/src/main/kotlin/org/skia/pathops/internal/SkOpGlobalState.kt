/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkOpGlobalState` from
 * `src/pathops/SkPathOpsTypes.{h,cpp}`.
 *
 * The upstream class is a fat per-`SkPathOpsOp` context bag :
 * arena allocator, contour head, allocation flags, debug IDs, debug
 * coincidence dictionaries, etc. The Kotlin port lands a minimal
 * shim that just holds a back-reference to the active
 * [SkOpCoincidence] container — the only piece needed by the
 * coincidence walker (D1.2.g.c.3 `SkOpSpanBase.checkForCollapsedCoincidence`).
 *
 * The remaining fields — allocator, contour head, winding-failed
 * flag, etc. — will land alongside their first non-trivial consumer.
 */
package org.skia.pathops.internal

internal class SkOpGlobalState {
    private var fCoincidence: SkOpCoincidence? = null
    private var fAllocatedOpSpan: Boolean = false

    fun coincidence(): SkOpCoincidence? = fCoincidence
    fun setCoincidence(c: SkOpCoincidence?) { fCoincidence = c }

    fun allocatedOpSpan(): Boolean = fAllocatedOpSpan
    fun setAllocatedOpSpan() { fAllocatedOpSpan = true }
    fun resetAllocatedOpSpan() { fAllocatedOpSpan = false }
}
