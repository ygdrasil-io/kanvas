/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Forward declaration / skeleton for `SkOpSegment` (Phase D1.2.a).
 * The full segment class — head/tail span list, intersection adders,
 * windSum / oppSum bookkeeping, coincidence linking — ships in
 * D1.2.c / d.
 *
 * For now, this empty class allows [SkOpSpanBase] / [SkOpSpan] /
 * [SkOpPtT] to reference `SkOpSegment` as their parent without
 * pulling in the algorithmic body.
 */
package org.skia.pathops.internal

internal class SkOpSegment {
    // Body lands in D1.2.c. The minimal accessor stubs below let the
    // span data model compile against this reference type.

    /**
     * Singly-linked next sibling in the contour's segment list.
     * Populated by [SkOpContour] when segments are added.
     */
    var fNext: SkOpSegment? = null

    /** Owning contour. Populated by [SkOpContour.appendSegment] (D1.2.e). */
    var fContour: SkOpContour? = null

    fun next(): SkOpSegment? = fNext
    fun contour(): SkOpContour? = fContour
}
