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

/**
 * Per-op processing phase. Mirrors `enum class SkOpPhase`
 * (`src/pathops/SkPathOpsTypes.h:24`). The Kotlin port currently
 * only distinguishes [kFixWinding] (set during `AsWinding`) from
 * the rest ; intermediate values used by upstream debug-validate
 * (`kNoChange` / `kIntersecting` / `kWalking`) are folded into
 * [kIntersecting] for now.
 */
internal enum class SkOpPhase {
    kNoChange, kIntersecting, kWalking, kFixWinding,
}

internal class SkOpGlobalState {
    private var fCoincidence: SkOpCoincidence? = null
    private var fAllocatedOpSpan: Boolean = false
    private var fContourHead: SkOpContour? = null
    private var fPhase: SkOpPhase = SkOpPhase.kIntersecting
    private var fNested: Int = 0

    fun coincidence(): SkOpCoincidence? = fCoincidence
    fun setCoincidence(c: SkOpCoincidence?) { fCoincidence = c }

    fun allocatedOpSpan(): Boolean = fAllocatedOpSpan
    fun setAllocatedOpSpan() { fAllocatedOpSpan = true }
    fun resetAllocatedOpSpan() { fAllocatedOpSpan = false }

    /**
     * Active contour-list head. Set by [SortContourList] once it
     * picks the lex-smallest non-empty contour to be the new chain
     * head.
     *
     * The upstream signature is `SkOpContourHead*` — but in C++ the
     * sort routine `static_cast<>`s an arbitrary [SkOpContour] (the
     * lex-smallest survivor) to the head type, relying on layout
     * equivalence (subclass adds no fields). We expose the looser
     * [SkOpContour] type to skip that cast.
     *
     * Mirrors `SkOpGlobalState::contourHead` / `setContourHead`
     * (`SkPathOpsTypes.h:66, 156`).
     */
    fun contourHead(): SkOpContour? = fContourHead
    fun setContourHead(head: SkOpContour?) { fContourHead = head }

    /**
     * Current op phase. Mirrors `SkOpGlobalState::phase / setPhase`
     * (`SkPathOpsTypes.h:143`). [SkOpSpan.sortableTop] consults
     * this to decide whether to call `markAndChaseWinding` (default
     * `Op` / `Simplify` path) or just record the CCW flag on the
     * contour (`AsWinding` path).
     */
    fun phase(): SkOpPhase = fPhase
    fun setPhase(p: SkOpPhase) { fPhase = p }

    /**
     * Increment the nested-op counter. Used as a debug guard against
     * runaway recursion in [SkOpSpan.sortableTop]'s safety net.
     * Mirrors `SkOpGlobalState::bumpNested`
     * (`SkPathOpsTypes.h:54`).
     */
    fun bumpNested() { ++fNested }
    fun nested(): Int = fNested

    companion object {
        /**
         * Max number of t-guess retries [SkOpSpan.sortableTop] is
         * allowed before giving up. Mirrors
         * `SkOpGlobalState::kMaxWindingTries`
         * (`SkPathOpsTypes.h:43`).
         */
        const val kMaxWindingTries: Int = 10
    }
}
