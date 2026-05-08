/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors free functions from Skia's `src/pathops/SkPathOpsCommon.cpp`
 * — the orchestration helpers shared by the top-level `Op` /
 * `Simplify` drivers.
 *
 * Phase D1.2.h.1 — `SortContourList`. The remaining helpers
 * (`AddIntersectTs` from `SkAddIntersections.cpp`, `HandleCoincidence`,
 * `bridgeOp`) land in subsequent D1.2.h sub-slices.
 */
package org.skia.pathops.internal

/**
 * Out-param wrapper for the upstream `**SkOpContourHead`
 * pointer-to-pointer ; lets [SortContourList] both read the current
 * head and overwrite it with the lex-smallest non-empty contour.
 */
internal class ContourHeadRef(var head: SkOpContour?)

/**
 * Pre-process the contour list before the intersection / coincidence
 * machinery runs :
 *  1. Drop empty contours (`count() == 0`) ;
 *  2. Stamp `setOppXor` on each surviving contour from the [evenOdd] /
 *     [oppEvenOdd] flags, dispatched on `operand()` (`true` = subtrahend) ;
 *  3. Sort the survivors by [SkOpContour]'s natural order
 *     (`bounds.top` then `bounds.left`) ;
 *  4. Re-link the chain in sorted order ; null-terminate the tail ;
 *  5. Promote the smallest survivor to be the new contour-list head
 *    and stash it on [SkOpGlobalState.setContourHead].
 *
 * Returns `false` when no non-empty contour survives — caller treats
 * this as "nothing to do" and short-circuits the boolean op.
 *
 * Mirrors `SortContourList` (`src/pathops/SkPathOpsCommon.cpp:159`).
 */
internal fun SortContourList(
    contourList: ContourHeadRef,
    evenOdd: Boolean,
    oppEvenOdd: Boolean,
): Boolean {
    val list = mutableListOf<SkOpContour>()
    var c: SkOpContour? = contourList.head
    while (c != null) {
        if (c.count() != 0) {
            c.setOppXor(if (c.operand()) evenOdd else oppEvenOdd)
            list.add(c)
        }
        c = c.next()
    }
    if (list.isEmpty()) return false
    if (list.size > 1) list.sort()
    val firstContour = list[0]
    firstContour.globalState()?.setContourHead(firstContour)
    contourList.head = firstContour
    var prev = firstContour
    for (i in 1 until list.size) {
        val next = list[i]
        prev.setNext(next)
        prev = next
    }
    prev.setNext(null)
    return true
}
