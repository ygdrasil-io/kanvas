/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors free functions from Skia's `src/pathops/SkPathOpsCommon.cpp`
 * — the orchestration helpers shared by the top-level `Op` /
 * `Simplify` drivers.
 *
 * Phase D1.2.h.1 — `SortContourList`.
 *
 * Phase D1.2.h.3 — `AddIntersectTs` (curve-vs-curve intersection
 * driver from `SkAddIntersections.cpp`).
 *
 * Phase D1.2.h.4 — `HandleCoincidence` orchestrator + the small
 * static contour-walker helpers (`move_multiples`, `move_nearby`,
 * `missing_coincidence`, `calc_angles`, `sort_angles`).
 *
 * Phase D1.2.h.5.3 — `AngleWinding` (angle-ring winding lookup).
 * The remaining helpers (`FindSortableTop` ray-tracing suite,
 * `bridgeOp` walker, `Op` final wiring) land in D1.2.h.5.4+.
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

// ─── AddIntersectTs (D1.2.h.3) ────────────────────────────────────

/** Internal segment classification used by [AddIntersectTs]'s dispatch. */
private enum class IntersectKind { kHorizontalLine, kVerticalLine, kLine, kQuad, kConic, kCubic }

private fun classify(seg: SkOpSegment): IntersectKind {
    val pts = seg.pts()
    return when (seg.verb()) {
        SkOpSegment.SegVerb.kLine -> when {
            pts[0].fY == pts[1].fY && pts[0].fX != pts[1].fX -> IntersectKind.kHorizontalLine
            pts[0].fX == pts[1].fX && pts[0].fY != pts[1].fY -> IntersectKind.kVerticalLine
            else -> IntersectKind.kLine
        }
        SkOpSegment.SegVerb.kQuad -> IntersectKind.kQuad
        SkOpSegment.SegVerb.kConic -> IntersectKind.kConic
        SkOpSegment.SegVerb.kCubic -> IntersectKind.kCubic
        SkOpSegment.SegVerb.kUnset -> error("invalid verb (kUnset)")
    }
}

/**
 * Drive curve-vs-curve intersections for every pair of segments
 * across two contours and push results into the [coincidence]
 * container.
 *
 * Iteration model :
 *  - For [test] vs [next] (different contours), all segment pairs.
 *  - For [test] == [next] (self-intersection), only ordered pairs
 *    `(wt, wn)` where `wn` comes strictly after `wt`.
 *
 * Per pair, dispatch on the (verb, verb) cross-product into the
 * matching [SkIntersections] method, then for each intersection
 * point :
 *  1. Allocate / find pt-Ts on both segments via [SkOpSegment.addT].
 *  2. Splice them into a shared opp loop via [SkOpPtT.addOpp] when
 *     not already coincident.
 *  3. If the intersection is flagged coincident and is the second of
 *     a pair, push a coincidence span via
 *     [SkOpCoincidence.add] (the "pair them up" logic).
 *
 * Returns false on the early-exit path (test bounds entirely above
 * next bounds — `AlmostLessUlps`). Returns true otherwise. The
 * boolean drives the caller's `while` over all contour pairs.
 *
 * Mirrors `AddIntersectTs` (`src/pathops/SkAddIntersections.cpp:275`).
 */
internal fun AddIntersectTs(
    test: SkOpContour,
    next: SkOpContour,
    coincidence: SkOpCoincidence,
): Boolean {
    if (test !== next) {
        if (AlmostLessUlps(test.bounds().bottom, next.bounds().top)) return false
        if (!test.bounds().intersects(next.bounds())) return true
    }
    var wt: SkOpSegment? = test.fHead
    while (wt != null) {
        val wnStart: SkOpSegment? = if (test === next) wt.next() else next.fHead
        var wn = wnStart
        while (wn != null) {
            if (wt.bounds().intersects(wn.bounds())) {
                processSegmentPair(wt, wn, coincidence)
            }
            wn = wn.next()
        }
        wt = wt.next()
    }
    return true
}

/**
 * Per-pair worker for [AddIntersectTs] : runs the (verb × verb)
 * dispatch and pushes intersection points + optional coincidences.
 *
 * Extracted into its own function to keep the dispatch table
 * compact and the outer iteration readable.
 */
private fun processSegmentPair(wt: SkOpSegment, wn: SkOpSegment, coincidence: SkOpCoincidence) {
    val ts = SkIntersections()
    val swap: Boolean
    val pts: Int
    val tKind = classify(wt)
    val nKind = classify(wn)
    val wtPts = wt.pts(); val wnPts = wn.pts()
    when (tKind) {
        IntersectKind.kHorizontalLine -> {
            swap = true
            val left = minOf(wtPts[0].fX, wtPts[1].fX)
            val right = maxOf(wtPts[0].fX, wtPts[1].fX)
            val y = wtPts[0].fY
            val flip = wtPts[0].fX > wtPts[1].fX
            pts = when (nKind) {
                IntersectKind.kHorizontalLine,
                IntersectKind.kVerticalLine,
                IntersectKind.kLine -> ts.lineHorizontal(wnPts, left, right, y, flip)
                IntersectKind.kQuad -> ts.quadHorizontal(wnPts, left, right, y, flip)
                IntersectKind.kConic -> ts.conicHorizontal(wnPts, wn.weight(), left, right, y, flip)
                IntersectKind.kCubic -> ts.cubicHorizontal(wnPts, left, right, y, flip)
            }
        }
        IntersectKind.kVerticalLine -> {
            swap = true
            val top = minOf(wtPts[0].fY, wtPts[1].fY)
            val bottom = maxOf(wtPts[0].fY, wtPts[1].fY)
            val x = wtPts[0].fX
            val flip = wtPts[0].fY > wtPts[1].fY
            pts = when (nKind) {
                IntersectKind.kHorizontalLine,
                IntersectKind.kVerticalLine,
                IntersectKind.kLine -> ts.lineVertical(wnPts, top, bottom, x, flip)
                IntersectKind.kQuad -> ts.quadVertical(wnPts, top, bottom, x, flip)
                IntersectKind.kConic -> ts.conicVertical(wnPts, wn.weight(), top, bottom, x, flip)
                IntersectKind.kCubic -> ts.cubicVertical(wnPts, top, bottom, x, flip)
            }
        }
        IntersectKind.kLine -> {
            when (nKind) {
                IntersectKind.kHorizontalLine -> {
                    swap = false
                    val left = minOf(wnPts[0].fX, wnPts[1].fX)
                    val right = maxOf(wnPts[0].fX, wnPts[1].fX)
                    pts = ts.lineHorizontal(wtPts, left, right, wnPts[0].fY,
                        wnPts[0].fX > wnPts[1].fX)
                }
                IntersectKind.kVerticalLine -> {
                    swap = false
                    val top = minOf(wnPts[0].fY, wnPts[1].fY)
                    val bottom = maxOf(wnPts[0].fY, wnPts[1].fY)
                    pts = ts.lineVertical(wtPts, top, bottom, wnPts[0].fX,
                        wnPts[0].fY > wnPts[1].fY)
                }
                IntersectKind.kLine -> { swap = false; pts = ts.lineLine(wtPts, wnPts) }
                IntersectKind.kQuad -> { swap = true; pts = ts.quadLine(wnPts, wtPts) }
                IntersectKind.kConic -> { swap = true; pts = ts.conicLine(wnPts, wn.weight(), wtPts) }
                IntersectKind.kCubic -> { swap = true; pts = ts.cubicLine(wnPts, wtPts) }
            }
        }
        IntersectKind.kQuad -> {
            val q1 = SkDQuad().apply { set(wtPts[0], wtPts[1], wtPts[2]) }
            when (nKind) {
                IntersectKind.kHorizontalLine -> {
                    swap = false
                    val left = minOf(wnPts[0].fX, wnPts[1].fX)
                    val right = maxOf(wnPts[0].fX, wnPts[1].fX)
                    pts = ts.quadHorizontal(wtPts, left, right, wnPts[0].fY,
                        wnPts[0].fX > wnPts[1].fX)
                }
                IntersectKind.kVerticalLine -> {
                    swap = false
                    val top = minOf(wnPts[0].fY, wnPts[1].fY)
                    val bottom = maxOf(wnPts[0].fY, wnPts[1].fY)
                    pts = ts.quadVertical(wtPts, top, bottom, wnPts[0].fX,
                        wnPts[0].fY > wnPts[1].fY)
                }
                IntersectKind.kLine -> { swap = false; pts = ts.quadLine(wtPts, wnPts) }
                IntersectKind.kQuad -> {
                    swap = false
                    val q2 = SkDQuad().apply { set(wnPts[0], wnPts[1], wnPts[2]) }
                    pts = ts.intersect(q1, q2)
                }
                IntersectKind.kConic -> {
                    swap = true
                    val c2 = SkDConic().apply { set(wnPts[0], wnPts[1], wnPts[2], wn.weight()) }
                    pts = ts.intersect(c2, q1)
                }
                IntersectKind.kCubic -> {
                    swap = true
                    val c2 = SkDCubic().apply { set(wnPts[0], wnPts[1], wnPts[2], wnPts[3]) }
                    pts = ts.intersect(c2, q1)
                }
            }
        }
        IntersectKind.kConic -> {
            val c1 = SkDConic().apply { set(wtPts[0], wtPts[1], wtPts[2], wt.weight()) }
            when (nKind) {
                IntersectKind.kHorizontalLine -> {
                    swap = false
                    val left = minOf(wnPts[0].fX, wnPts[1].fX)
                    val right = maxOf(wnPts[0].fX, wnPts[1].fX)
                    pts = ts.conicHorizontal(wtPts, wt.weight(), left, right, wnPts[0].fY,
                        wnPts[0].fX > wnPts[1].fX)
                }
                IntersectKind.kVerticalLine -> {
                    swap = false
                    val top = minOf(wnPts[0].fY, wnPts[1].fY)
                    val bottom = maxOf(wnPts[0].fY, wnPts[1].fY)
                    pts = ts.conicVertical(wtPts, wt.weight(), top, bottom, wnPts[0].fX,
                        wnPts[0].fY > wnPts[1].fY)
                }
                IntersectKind.kLine -> {
                    swap = false; pts = ts.conicLine(wtPts, wt.weight(), wnPts)
                }
                IntersectKind.kQuad -> {
                    swap = false
                    val q2 = SkDQuad().apply { set(wnPts[0], wnPts[1], wnPts[2]) }
                    pts = ts.intersect(c1, q2)
                }
                IntersectKind.kConic -> {
                    swap = false
                    val c2 = SkDConic().apply { set(wnPts[0], wnPts[1], wnPts[2], wn.weight()) }
                    pts = ts.intersect(c1, c2)
                }
                IntersectKind.kCubic -> {
                    swap = true
                    val cu = SkDCubic().apply { set(wnPts[0], wnPts[1], wnPts[2], wnPts[3]) }
                    pts = ts.intersect(cu, c1)
                }
            }
        }
        IntersectKind.kCubic -> {
            val c1 = SkDCubic().apply { set(wtPts[0], wtPts[1], wtPts[2], wtPts[3]) }
            when (nKind) {
                IntersectKind.kHorizontalLine -> {
                    swap = false
                    val left = minOf(wnPts[0].fX, wnPts[1].fX)
                    val right = maxOf(wnPts[0].fX, wnPts[1].fX)
                    pts = ts.cubicHorizontal(wtPts, left, right, wnPts[0].fY,
                        wnPts[0].fX > wnPts[1].fX)
                }
                IntersectKind.kVerticalLine -> {
                    swap = false
                    val top = minOf(wnPts[0].fY, wnPts[1].fY)
                    val bottom = maxOf(wnPts[0].fY, wnPts[1].fY)
                    pts = ts.cubicVertical(wtPts, top, bottom, wnPts[0].fX,
                        wnPts[0].fY > wnPts[1].fY)
                }
                IntersectKind.kLine -> { swap = false; pts = ts.cubicLine(wtPts, wnPts) }
                IntersectKind.kQuad -> {
                    swap = false
                    val q2 = SkDQuad().apply { set(wnPts[0], wnPts[1], wnPts[2]) }
                    pts = ts.intersect(c1, q2)
                }
                IntersectKind.kConic -> {
                    swap = false
                    val co2 = SkDConic().apply { set(wnPts[0], wnPts[1], wnPts[2], wn.weight()) }
                    pts = ts.intersect(c1, co2)
                }
                IntersectKind.kCubic -> {
                    swap = false
                    val c2 = SkDCubic().apply { set(wnPts[0], wnPts[1], wnPts[2], wnPts[3]) }
                    pts = ts.intersect(c1, c2)
                }
            }
        }
    }
    // Push pt-T entries + coincidence pairs.
    var coinPtT0: SkOpPtT? = null
    var coinPtT1: SkOpPtT? = null
    var coinIndex = -1
    for (i in 0 until pts) {
        val tT = ts.t(if (swap) 1 else 0, i)
        val nT = ts.t(if (swap) 0 else 1, i)
        require(tT in 0.0..1.0) { "intersection t out of range : $tT" }
        require(nT in 0.0..1.0) { "intersection t out of range : $nT" }
        // Use the int-pixel-aligned point hint if intersection is integral
        // (matches upstream's iPtIsIntegral check).
        val iPt = ts.pt(i).asSkPoint()
        val integral = iPt.fX == kotlin.math.floor(iPt.fX) && iPt.fY == kotlin.math.floor(iPt.fY)
        val tAt = if (integral) wt.addT(tT, iPt) else wt.addT(tT)
        val nAt = if (integral) wn.addT(nT, iPt) else wn.addT(nT)
        if (tAt == null || nAt == null) continue
        if (!tAt.contains(nAt)) {
            val oppPrev = tAt.oppPrev(nAt)
            if (oppPrev != null) {
                tAt.span()?.mergeMatches(nAt.span()!!)
                tAt.addOpp(nAt, oppPrev)
            }
            if (tAt.fPt != nAt.fPt) {
                tAt.span()?.unaligned()
                nAt.span()?.unaligned()
            }
        }
        if (!ts.isCoincident(i)) continue
        if (coinIndex < 0) {
            coinPtT0 = tAt
            coinPtT1 = nAt
            coinIndex = i
            continue
        }
        if (coinPtT0!!.span() === tAt.span() || coinPtT1!!.span() === nAt.span()) {
            coinIndex = -1
            continue
        }
        var c0 = coinPtT0; var c1 = coinPtT1
        var t0 = tAt; var n0 = nAt
        if (swap) {
            val tmp = c0; c0 = c1; c1 = tmp
            val tmp2 = t0; t0 = n0; n0 = tmp2
        }
        if (c0!!.span()?.deleted() == true || t0.span()?.deleted() == true) {
            coinIndex = -1
            continue
        }
        coincidence.add(c0, t0, c1!!, n0)
        coinIndex = -1
    }
}

// ─── Contour-walker statics for HandleCoincidence (D1.2.h.4) ─────

/**
 * Walk every contour calling [SkOpContour.calcAngles]. Mirrors
 * `calc_angles` (`src/pathops/SkPathOpsCommon.cpp:188`).
 */
internal fun calc_angles(contourList: SkOpContour) {
    var c: SkOpContour? = contourList
    while (c != null) {
        c.calcAngles()
        c = c.next()
    }
}

/**
 * Walk every contour OR-ing together `missingCoincidence`. Returns
 * true iff at least one contour found a missing coincidence pair.
 * Mirrors `missing_coincidence`
 * (`src/pathops/SkPathOpsCommon.cpp:196`).
 */
internal fun missing_coincidence(contourList: SkOpContour): Boolean {
    var c: SkOpContour? = contourList
    var result = false
    while (c != null) {
        if (c.missingCoincidence()) result = true
        c = c.next()
    }
    return result
}

/**
 * Walk every contour calling `moveMultiples` ; short-circuit-false
 * on the first failure. Mirrors `move_multiples`
 * (`src/pathops/SkPathOpsCommon.cpp:206`).
 */
internal fun move_multiples(contourList: SkOpContour): Boolean {
    var c: SkOpContour? = contourList
    while (c != null) {
        if (!c.moveMultiples()) return false
        c = c.next()
    }
    return true
}

/**
 * Walk every contour calling `moveNearby` ; short-circuit-false on
 * the first failure. Mirrors `move_nearby`
 * (`src/pathops/SkPathOpsCommon.cpp:217`).
 */
internal fun move_nearby(contourList: SkOpContour): Boolean {
    var c: SkOpContour? = contourList
    while (c != null) {
        if (!c.moveNearby()) return false
        c = c.next()
    }
    return true
}

/**
 * Walk every contour calling `sortAngles` ; short-circuit-false on
 * the first failure. Mirrors `sort_angles`
 * (`src/pathops/SkPathOpsCommon.cpp:228`).
 */
internal fun sort_angles(contourList: SkOpContour): Boolean {
    var c: SkOpContour? = contourList
    while (c != null) {
        if (!c.sortAngles()) return false
        c = c.next()
    }
    return true
}

// ─── HandleCoincidence (D1.2.h.4) ────────────────────────────────

/**
 * The "fix coincidence" phase orchestrator that runs after
 * intersection detection ([AddIntersectTs]) and before the
 * contour-walk emit phase ([bridgeOp], lands in D1.2.h.5). Drives
 * the entire D1.2.g coincidence machinery + the
 * [contourList]-walker helpers above into a single pipeline :
 *
 *  1. `coincidence.addExpanded` — match span boundaries on already-
 *     tracked coincidence pairs.
 *  2. `move_multiples` then `move_nearby` — fold span lists where t
 *     values overlap or sit close together.
 *  3. `coincidence.correctEnds` + `addEndMovedSpans` — re-snap end
 *     pairs on curves whose endpoints drifted during the merge.
 *  4. `addMissing` loop (≤ 3 iters with `move_nearby` between
 *     iterations) — detect coincidence visible in pair (A, B) +
 *     pair (A, C) but not yet recorded between (B, C).
 *  5. `coincidence.expand` + `addMissing` + `addExpanded` +
 *     `move_multiples` + `move_nearby` — second growth pass when
 *     `expand` finds new ranges.
 *  6. `coincidence.addExpanded` again to align ranges that grew.
 *  7. `coincidence.mark` — install the coincidence linkage on
 *     spans.
 *  8. `missing_coincidence` (contour-side) → if it finds anything,
 *     re-run `expand` + `addExpanded` + `mark`.
 *  9. Two more `coincidence.expand` — defensive after step 8.
 * 10. Apply / find-overlaps loop on the local `overlaps` container :
 *     each iter `apply` to the active pair set then collect new
 *     overlaps ; loop until `overlaps.isEmpty`. Bounded by a safety
 *     counter at 3 iterations.
 * 11. `calc_angles` + `sort_angles` — final pre-emit setup.
 *
 * Returns false on any abort path (a sub-step returned false, or
 * the safety counter tripped) ; true otherwise.
 *
 * Mirrors `HandleCoincidence` (`src/pathops/SkPathOpsCommon.cpp:238`).
 */
internal fun HandleCoincidence(
    contourList: SkOpContour,
    coincidence: SkOpCoincidence,
): Boolean {
    if (!coincidence.addExpanded()) return false
    if (!move_multiples(contourList)) return false
    if (!move_nearby(contourList)) return false
    coincidence.correctEnds()
    if (!coincidence.addEndMovedSpans()) return false
    val SAFETY_COUNT = 3
    var safetyHatch = SAFETY_COUNT
    while (true) {
        val addedOut = booleanArrayOf(false)
        if (!coincidence.addMissing(addedOut)) return false
        if (!addedOut[0]) break
        if (--safetyHatch == 0) return false
        move_nearby(contourList)
    }
    if (coincidence.expand()) {
        val addedOut = booleanArrayOf(false)
        if (!coincidence.addMissing(addedOut)) return false
        if (!coincidence.addExpanded()) return false
        if (!move_multiples(contourList)) return false
        move_nearby(contourList)
    }
    if (!coincidence.addExpanded()) return false
    coincidence.mark()
    if (missing_coincidence(contourList)) {
        coincidence.expand()
        if (!coincidence.addExpanded()) return false
        if (!coincidence.mark()) return false
    } else {
        coincidence.expand()
    }
    coincidence.expand()

    val overlaps = SkOpCoincidence()
    safetyHatch = SAFETY_COUNT
    do {
        val pairs = if (overlaps.isEmpty()) coincidence else overlaps
        if (!pairs.apply()) return false
        if (!pairs.findOverlaps(overlaps)) return false
        if (--safetyHatch == 0) return false
    } while (!overlaps.isEmpty())
    calc_angles(contourList)
    if (!sort_angles(contourList)) return false
    return true
}

// ─── AngleWinding (D1.2.h.5.3) ──────────────────────────────────

/**
 * Walk the angle ring at the `(start, end)` span pair to find the
 * first angle whose segment has an already-computed `windSum` ;
 * return it along with the winding value into [windingOut] and a
 * sortability flag into [sortableOut]. Used by [findChaseOp]
 * (lands in D1.2.h.5.4) to set up the running winding for the
 * angle-side walker.
 *
 * Two-pass strategy :
 *  1. Walk forward from `spanToAngle(start, end)` through
 *     `angle.next()` until an angle reports a known windSum.
 *     Track whether any angle is unorderable along the way.
 *  2. If the loop found nothing or saw an unorderable, walk again
 *     consulting `lesser->windSum()` directly (and falling back to
 *     `lesser->computeWindSum()` — currently a stub returning the
 *     stored value, full ray-tracing pending).
 *
 * Returns null on a hard abort (no angle ring at start/end), or
 * the first angle with a known windSum (writing the winding value
 * into [windingOut] and `!unorderable` into [sortableOut]).
 *
 * Mirrors `AngleWinding` (`src/pathops/SkPathOpsCommon.cpp:21`).
 */
internal fun AngleWinding(
    start: SkOpSpanBase,
    end: SkOpSpanBase,
    windingOut: IntArray,
    sortableOut: BooleanArray,
): SkOpAngle? {
    var segment = start.segment() ?: return null
    var angle: SkOpAngle = segment.spanToAngle(start, end) ?: run {
        windingOut[0] = SkOpSpan.SK_MinS32
        return null
    }
    var computeWinding = false
    val firstAngle = angle
    var loop = false
    var unorderable = false
    var winding = SkOpSpan.SK_MinS32
    do {
        angle = angle.next() ?: return null
        unorderable = unorderable || angle.unorderable()
        computeWinding = unorderable || (angle === firstAngle && loop)
        if (computeWinding) break
        loop = loop || angle === firstAngle
        segment = angle.segment() ?: return null
        winding = segment.windSum(angle)
    } while (winding == SkOpSpan.SK_MinS32)
    if (computeWinding) {
        var a = angle
        winding = SkOpSpan.SK_MinS32
        do {
            val sStart = a.start() ?: return null
            val sEnd = a.end() ?: return null
            val lesser = sStart.starter(sEnd)
            var testWinding = lesser.windSum()
            if (testWinding == SkOpSpan.SK_MinS32) {
                testWinding = lesser.computeWindSum()
            }
            if (testWinding != SkOpSpan.SK_MinS32) {
                winding = testWinding
            }
            a = a.next() ?: return null
        } while (a !== angle)
    }
    sortableOut[0] = !unorderable
    windingOut[0] = winding
    return angle
}
