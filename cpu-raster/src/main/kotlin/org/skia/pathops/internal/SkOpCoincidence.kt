/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkCoincidentSpans` and `class SkOpCoincidence`
 * from `src/pathops/SkOpCoincidence.{h,cpp}`.
 *
 * Phase D1.2.g.0 — SkCoincidentSpans data model + simple methods, plus
 * the SkOpCoincidence skeleton (head pointer + Ordered statics).
 *
 * Phase D1.2.g.a — SkOpCoincidence container methods : add / extend /
 * contains (× 3 overloads).
 *
 * Phase D1.2.g.b — list-maintenance methods : release (× 2) /
 * releaseDeleted (× 2) / restoreHead / fixUp (× 2) / markCollapsed
 * (× 2).
 *
 * Phase D1.2.g.c.1 — overlap-detection predicates : overlap / TRange /
 * checkOverlap.
 *
 * Phase D1.2.g.c.4 — the two callers `addIfMissing` / `addOrOverlap`
 * (gated on the SkOpSegment / SkOpPtT / SkOpSpanBase helpers landed
 * in c.2 + c.3).
 *
 * Phase D1.2.g.d — addMissing / addOverlap / findOverlaps /
 * addEndMovedSpans (3 overloads).
 *
 * Phase D1.2.g.e — the marking pass : correctEnds / mark / expand /
 * addExpanded / apply. These run during pathops' "fix coincidence"
 * phase to align span boundaries on coincident pairs and propagate
 * winding values across them. Closes the D1.2.g chantier.
 *
 * # SkCoincidentSpans
 *
 * Tracks one coincident-segment-pair span : a (coinPtTStart,
 * coinPtTEnd) range on the "main" segment that maps to a
 * (oppPtTStart, oppPtTEnd) range on the "opposite" segment. The pair
 * is "flipped" when oppPtTStart.t > oppPtTEnd.t (the curves agree in
 * shape but disagree in direction).
 *
 * Instances form a singly-linked list via [fNext], owned by an
 * [SkOpCoincidence] container.
 *
 * # SkOpCoincidence
 *
 * Container holding two linked lists of `SkCoincidentSpans` :
 *  - `fHead` — active coincidences ;
 *  - `fTop` — saved snapshot from before the current pass (used by the
 *    "missing coincidence" walker).
 *
 * Note : distinct from [SkTCoincident], which is the per-perp-pair
 * helper used by [SkTSect] in the Bézier-clipping machinery.
 */
package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.graphiks.math.between
import org.graphiks.math.zero_or_one
internal class SkCoincidentSpans {
    var fNext: SkCoincidentSpans? = null

    // pt-T endpoints of the coincident range. Set via setCoinPtTStart
    // etc. (which mark them coincident on assignment).
    private var fCoinPtTStart: SkOpPtT? = null
    private var fCoinPtTEnd: SkOpPtT? = null
    private var fOppPtTStart: SkOpPtT? = null
    private var fOppPtTEnd: SkOpPtT? = null

    fun coinPtTStart(): SkOpPtT? = fCoinPtTStart
    fun coinPtTEnd(): SkOpPtT? = fCoinPtTEnd
    fun oppPtTStart(): SkOpPtT? = fOppPtTStart
    fun oppPtTEnd(): SkOpPtT? = fOppPtTEnd

    fun next(): SkCoincidentSpans? = fNext
    fun setNext(n: SkCoincidentSpans?) { fNext = n }

    /** True iff `oppPtTStart.t > oppPtTEnd.t` — opp curve runs backward. */
    fun flipped(): Boolean = (fOppPtTStart?.fT ?: 0.0) > (fOppPtTEnd?.fT ?: 0.0)

    fun setCoinPtTStart(ptT: SkOpPtT) {
        require(fCoinPtTEnd == null || ptT.fT != fCoinPtTEnd!!.fT)
        fCoinPtTStart = ptT
        ptT.setCoincident()
    }

    fun setCoinPtTEnd(ptT: SkOpPtT) {
        require(fCoinPtTStart == null || ptT.fT != fCoinPtTStart!!.fT)
        fCoinPtTEnd = ptT
        ptT.setCoincident()
    }

    fun setOppPtTStart(ptT: SkOpPtT) {
        require(fOppPtTEnd == null || ptT.fT != fOppPtTEnd!!.fT)
        fOppPtTStart = ptT
        ptT.setCoincident()
    }

    fun setOppPtTEnd(ptT: SkOpPtT) {
        require(fOppPtTStart == null || ptT.fT != fOppPtTStart!!.fT)
        fOppPtTEnd = ptT
        ptT.setCoincident()
    }

    fun setStarts(coin: SkOpPtT, opp: SkOpPtT) {
        setCoinPtTStart(coin)
        setOppPtTStart(opp)
    }

    fun setEnds(coin: SkOpPtT, opp: SkOpPtT) {
        setCoinPtTEnd(coin)
        setOppPtTEnd(opp)
    }

    /**
     * Initialise from a freshly-detected coincidence pair. Mirrors
     * `SkCoincidentSpans::set` (`SkOpCoincidence.cpp:122`).
     */
    fun set(
        next: SkCoincidentSpans?,
        coinPtTStart: SkOpPtT,
        coinPtTEnd: SkOpPtT,
        oppPtTStart: SkOpPtT,
        oppPtTEnd: SkOpPtT,
    ) {
        require(SkOpCoincidence.Ordered(coinPtTStart, oppPtTStart))
        fNext = next
        setStarts(coinPtTStart, oppPtTStart)
        setEnds(coinPtTEnd, oppPtTEnd)
    }

    /**
     * True iff [test] is one of the four endpoints AND its opposite
     * endpoint is in test's loop — i.e. the coincidence span has
     * collapsed to a point. Mirrors `SkCoincidentSpans::collapsed`
     * (`SkOpCoincidence.cpp:22`).
     */
    fun collapsed(test: SkOpPtT): Boolean =
        (fCoinPtTStart === test && fCoinPtTEnd?.contains(test) == true) ||
        (fCoinPtTEnd === test && fCoinPtTStart?.contains(test) == true) ||
        (fOppPtTStart === test && fOppPtTEnd?.contains(test) == true) ||
        (fOppPtTEnd === test && fOppPtTStart?.contains(test) == true)

    /**
     * True iff both [s] and [e] are inside this coincidence range
     * (on whichever side they belong to). Mirrors
     * `SkCoincidentSpans::contains(SkOpPtT*, SkOpPtT*)`
     * (`SkOpCoincidence.cpp:131`).
     */
    fun contains(s: SkOpPtT, e: SkOpPtT): Boolean {
        var sIn = s; var eIn = e
        if (sIn.fT > eIn.fT) { val tmp = sIn; sIn = eIn; eIn = tmp }
        val coinSeg = fCoinPtTStart!!.span()?.segment()
        return if (sIn.span()?.segment() === coinSeg) {
            fCoinPtTStart!!.fT <= sIn.fT && eIn.fT <= fCoinPtTEnd!!.fT
        } else {
            require(sIn.span()?.segment() === fOppPtTStart!!.span()?.segment())
            var oppTs = fOppPtTStart!!.fT
            var oppTe = fOppPtTEnd!!.fT
            if (oppTs > oppTe) { val tmp = oppTs; oppTs = oppTe; oppTe = tmp }
            oppTs <= sIn.fT && eIn.fT <= oppTe
        }
    }

    /**
     * Walk both segments outward from the current range, extending it
     * by any adjacent spans where [SkOpSegment.isClose] still holds.
     * Returns true iff either side expanded. Mirrors
     * `SkCoincidentSpans::expand` (`SkOpCoincidence.cpp:66`).
     */
    fun expand(): Boolean {
        var expanded = false
        val segment = fCoinPtTStart!!.span()!!.segment()!!
        val oppSegment = fOppPtTStart!!.span()!!.segment()!!
        // Extend leftward from coinPtTStart.
        while (true) {
            val start = fCoinPtTStart!!.span()!!.upCast()
            val prev = start.prev() ?: break
            val oppPtT = prev.contains(oppSegment) ?: break
            val midT = (prev.t() + start.t()) / 2
            if (!segment.isClose(midT, oppSegment)) break
            setStarts(prev.ptT(), oppPtT)
            expanded = true
        }
        // Extend rightward from coinPtTEnd.
        while (true) {
            val end = fCoinPtTEnd!!.span()!!
            val next = if (end.final()) null else end.upCast().next()
            if (next == null) break
            if (next.ptT().deleted()) break
            val oppPtT = next.contains(oppSegment) ?: break
            val midT = (end.t() + next.t()) / 2
            if (!segment.isClose(midT, oppSegment)) break
            setEnds(next.ptT(), oppPtT)
            expanded = true
        }
        return expanded
    }

    /**
     * Stretch this range to include `[coinPtTStart..coinPtTEnd]` /
     * `[oppPtTStart..oppPtTEnd]` if they extend beyond the current
     * bounds. Returns true iff either end moved. Mirrors
     * `SkCoincidentSpans::extend` (`SkOpCoincidence.cpp:105`).
     */
    fun extend(
        coinPtTStart: SkOpPtT,
        coinPtTEnd: SkOpPtT,
        oppPtTStart: SkOpPtT,
        oppPtTEnd: SkOpPtT,
    ): Boolean {
        var result = false
        val flipped = flipped()
        if (fCoinPtTStart!!.fT > coinPtTStart.fT ||
            (if (flipped) fOppPtTStart!!.fT < oppPtTStart.fT
             else fOppPtTStart!!.fT > oppPtTStart.fT)) {
            setStarts(coinPtTStart, oppPtTStart)
            result = true
        }
        if (fCoinPtTEnd!!.fT < coinPtTEnd.fT ||
            (if (flipped) fOppPtTEnd!!.fT > oppPtTEnd.fT
             else fOppPtTEnd!!.fT < oppPtTEnd.fT)) {
            setEnds(coinPtTEnd, oppPtTEnd)
            result = true
        }
        return result
    }

    /**
     * True iff the spans on the main segment between
     * `coinPtTStart.span` and `coinPtTEnd.span` map (in order or
     * exact reverse) to spans on the opposite segment. Writes the
     * answer into [resultOut]`[0]` ; returns false on a malformed
     * loop (caller treats as unorderable).
     *
     * Mirrors `SkCoincidentSpans::ordered` (`SkOpCoincidence.cpp:163`).
     */
    fun ordered(resultOut: BooleanArray): Boolean {
        val start = fCoinPtTStart!!.span()!!
        val end = fCoinPtTEnd!!.span()!!
        var next = start.upCast().next() ?: return false
        if (next === end) { resultOut[0] = true; return true }
        val flipped = flipped()
        val oppSeg = fOppPtTStart!!.span()!!.segment()!!
        var oppLastT = fOppPtTStart!!.fT
        while (true) {
            val opp = next.contains(oppSeg) ?: return false
            if ((oppLastT > opp.fT) != flipped) {
                resultOut[0] = false; return true
            }
            oppLastT = opp.fT
            if (next === end) break
            if (next.upCastable() == null) {
                resultOut[0] = false; return true
            }
            next = next.upCast().next() ?: return false
        }
        resultOut[0] = true
        return true
    }

    /**
     * Re-snap each endpoint to the canonical pt-T of its span. Mirrors
     * `SkCoincidentSpans::correctEnds` (`SkOpCoincidence.cpp:57`) :
     * an end's pt-T may have drifted from the canonical "first ptT in
     * the loop" representative as adjacent spans merged.
     */
    fun correctEnds() {
        correctOneEnd({ fCoinPtTStart }, { setCoinPtTStart(it) })
        correctOneEnd({ fCoinPtTEnd }, { setCoinPtTEnd(it) })
        correctOneEnd({ fOppPtTStart }, { setOppPtTStart(it) })
        correctOneEnd({ fOppPtTEnd }, { setOppPtTEnd(it) })
    }

    private inline fun correctOneEnd(
        getEnd: () -> SkOpPtT?,
        setEnd: (SkOpPtT) -> Unit,
    ) {
        val origPtT = getEnd() ?: return
        val origSpan = origPtT.span() ?: return
        // origSpan.prev() (SkOpSpanBase API) returns the prev SkOpSpan
        // even when origSpan is the final tail. Only the head's prev
        // is null.
        val prev = origSpan.prev()
        val testPtT = if (prev != null) prev.next()!!.ptT()
                      else origSpan.upCast().next()?.prev()?.ptT() ?: return
        if (origPtT !== testPtT) setEnd(testPtT)
    }
}

internal class SkOpCoincidence {
    /** Active coincidences. */
    var fHead: SkCoincidentSpans? = null

    /**
     * Snapshot from before the current pass — populated by
     * `restoreHead` etc. Used by the "missing coincidence" walker
     * (lands in a later D1.2.g sub-slice).
     */
    var fTop: SkCoincidentSpans? = null

    fun isEmpty(): Boolean = fHead == null && fTop == null

    /**
     * Push a new coincident-pair onto [fHead]. Canonicalises the (coin,
     * opp) pair via [Ordered] (recursing with the opposite ordering if
     * required), then snaps each pt-T to its span's canonical
     * representative before linking.
     *
     * Mirrors `SkOpCoincidence::add`
     * (`SkOpCoincidence.cpp:257`).
     */
    fun add(
        coinPtTStart: SkOpPtT,
        coinPtTEnd: SkOpPtT,
        oppPtTStart: SkOpPtT,
        oppPtTEnd: SkOpPtT,
    ) {
        // OPTIMIZE: caller should have already sorted.
        if (!Ordered(coinPtTStart, oppPtTStart)) {
            if (oppPtTStart.fT < oppPtTEnd.fT) {
                add(oppPtTStart, oppPtTEnd, coinPtTStart, coinPtTEnd)
            } else {
                add(oppPtTEnd, oppPtTStart, coinPtTEnd, coinPtTStart)
            }
            return
        }
        // Choose the ptT at the front of the list to track.
        val cs = coinPtTStart.span()!!.ptT()
        val ce = coinPtTEnd.span()!!.ptT()
        val os = oppPtTStart.span()!!.ptT()
        val oe = oppPtTEnd.span()!!.ptT()
        require(cs.fT < ce.fT)
        require(os.fT != oe.fT)
        require(!cs.deleted())
        require(!ce.deleted())
        require(!os.deleted())
        require(!oe.deleted())
        val coinRec = SkCoincidentSpans()
        coinRec.set(fHead, cs, ce, os, oe)
        fHead = coinRec
    }

    /**
     * If an existing entry on [fHead] overlaps the new
     * `(coinPtTStart..coinPtTEnd, oppPtTStart..oppPtTEnd)` range on
     * the same (coin, opp) segment pair, stretch it to include the new
     * range and return true. Returns false otherwise (caller should
     * fall back to [add]).
     *
     * Mirrors `SkOpCoincidence::extend`
     * (`SkOpCoincidence.cpp:199`).
     */
    fun extend(
        coinPtTStart: SkOpPtT,
        coinPtTEnd: SkOpPtT,
        oppPtTStart: SkOpPtT,
        oppPtTEnd: SkOpPtT,
    ): Boolean {
        var test = fHead ?: return false
        var cs = coinPtTStart
        var ce = coinPtTEnd
        var os = oppPtTStart
        var oe = oppPtTEnd
        var coinSeg = cs.span()?.segment()
        var oppSeg = os.span()?.segment()
        if (!Ordered(cs, os)) {
            val tSeg = coinSeg; coinSeg = oppSeg; oppSeg = tSeg
            val tcs = cs; cs = os; os = tcs
            val tce = ce; ce = oe; oe = tce
            if (cs.fT > ce.fT) {
                val tcs2 = cs; cs = ce; ce = tcs2
                val tos = os; os = oe; oe = tos
            }
        }
        val oppMinT = minOf(os.fT, oe.fT)
        while (true) {
            val tCoinStart = test.coinPtTStart()!!
            val tOppStart = test.oppPtTStart()!!
            if (coinSeg === tCoinStart.span()?.segment() &&
                oppSeg === tOppStart.span()?.segment()) {
                val tCoinEnd = test.coinPtTEnd()!!
                val tOppEnd = test.oppPtTEnd()!!
                val oTestMinT = minOf(tOppStart.fT, tOppEnd.fT)
                val oTestMaxT = maxOf(tOppStart.fT, tOppEnd.fT)
                if ((tCoinStart.fT <= ce.fT && cs.fT <= tCoinEnd.fT) ||
                    (oTestMinT <= oTestMaxT && oppMinT <= oTestMaxT)) {
                    test.extend(cs, ce, os, oe)
                    return true
                }
            }
            test = test.next() ?: return false
        }
    }

    /**
     * True iff some entry on [fHead] or [fTop] reports [seg] as the
     * coin-side (or opp-side) and brackets [oppT] on the opposite end
     * of that entry. Used by the missing-coincidence walker to skip
     * pt-Ts already tracked.
     *
     * Mirrors `SkOpCoincidence::contains(SkOpSegment, SkOpSegment,
     * double)` (`SkOpCoincidence.cpp:942`).
     */
    fun contains(seg: SkOpSegment, opp: SkOpSegment, oppT: Double): Boolean =
        contains(fHead, seg, opp, oppT) || contains(fTop, seg, opp, oppT)

    /**
     * True iff some entry on [coin]'s chain matches the (seg, opp)
     * pair (in either order) and brackets [oppT].
     *
     * Mirrors the private `SkOpCoincidence::contains(const
     * SkCoincidentSpans*, …)` (`SkOpCoincidence.cpp:952`).
     */
    fun contains(
        coin: SkCoincidentSpans?,
        seg: SkOpSegment,
        opp: SkOpSegment,
        oppT: Double,
    ): Boolean {
        var c = coin ?: return false
        while (true) {
            val coinStart = c.coinPtTStart()!!
            val oppStart = c.oppPtTStart()!!
            val coinSeg = coinStart.span()?.segment()
            val oppSeg = oppStart.span()?.segment()
            if (coinSeg === seg && oppSeg === opp &&
                between(oppStart.fT, oppT, c.oppPtTEnd()!!.fT)) {
                return true
            }
            if (oppSeg === seg && coinSeg === opp &&
                between(coinStart.fT, oppT, c.coinPtTEnd()!!.fT)) {
                return true
            }
            c = c.next() ?: return false
        }
    }

    /**
     * True iff some entry on [fHead] already brackets the
     * `[coinPtTStart..coinPtTEnd] / [oppPtTStart..oppPtTEnd]` range on
     * the same (coin, opp) segment pair. Pre-canonicalises via
     * [Ordered] (and a t-swap on the coin side when canonicalisation
     * flips it backwards).
     *
     * Mirrors `SkOpCoincidence::contains(SkOpPtT*, SkOpPtT*, SkOpPtT*,
     * SkOpPtT*)` (`SkOpCoincidence.cpp:970`).
     */
    fun contains(
        coinPtTStart: SkOpPtT,
        coinPtTEnd: SkOpPtT,
        oppPtTStart: SkOpPtT,
        oppPtTEnd: SkOpPtT,
    ): Boolean {
        var test = fHead ?: return false
        var cs = coinPtTStart
        var ce = coinPtTEnd
        var os = oppPtTStart
        var oe = oppPtTEnd
        var coinSeg = cs.span()?.segment()
        var oppSeg = os.span()?.segment()
        if (!Ordered(cs, os)) {
            val tSeg = coinSeg; coinSeg = oppSeg; oppSeg = tSeg
            val tcs = cs; cs = os; os = tcs
            val tce = ce; ce = oe; oe = tce
            if (cs.fT > ce.fT) {
                val tcs2 = cs; cs = ce; ce = tcs2
                val tos = os; os = oe; oe = tos
            }
        }
        val oppMinT = minOf(os.fT, oe.fT)
        val oppMaxT = maxOf(os.fT, oe.fT)
        while (true) {
            val tCoinStart = test.coinPtTStart()!!
            if (coinSeg === tCoinStart.span()?.segment() &&
                cs.fT >= tCoinStart.fT &&
                ce.fT <= test.coinPtTEnd()!!.fT) {
                val tOppStart = test.oppPtTStart()!!
                val tOppEnd = test.oppPtTEnd()!!
                if (oppSeg === tOppStart.span()?.segment() &&
                    oppMinT >= minOf(tOppStart.fT, tOppEnd.fT) &&
                    oppMaxT <= maxOf(tOppStart.fT, tOppEnd.fT)) {
                    return true
                }
            }
            test = test.next() ?: return false
        }
    }

    // ─── list maintenance (D1.2.g.b) ───────────────────────────────

    /**
     * Walk the chain rooted at [coin] looking for [remove] ; unlink
     * it on hit. The chain may be either the [fHead] list or the
     * [fTop] list — `head === fHead` decides which root pointer to
     * patch when [remove] is the very first entry. Returns true iff
     * [remove] was found.
     *
     * Mirrors the private `SkOpCoincidence::release(SkCoincidentSpans*,
     * SkCoincidentSpans*)` (`SkOpCoincidence.cpp:1160`).
     */
    private fun release(coin: SkCoincidentSpans, remove: SkCoincidentSpans): Boolean {
        val head = coin
        var prev: SkCoincidentSpans? = null
        var cur: SkCoincidentSpans? = coin
        while (cur != null) {
            val next = cur.next()
            if (cur === remove) {
                if (prev != null) {
                    prev.setNext(next)
                } else if (head === fHead) {
                    fHead = next
                } else {
                    fTop = next
                }
                return true
            }
            prev = cur
            cur = next
        }
        return false
    }

    /**
     * Release every entry on [fHead] that touches [deleted] (on any
     * of the four endpoint segments). Mirrors
     * `SkOpCoincidence::release(SkOpSegment*)`
     * (`SkOpCoincidence.cpp:1443`).
     */
    fun release(deleted: SkOpSegment) {
        var coin = fHead ?: return
        while (true) {
            if (coin.coinPtTStart()?.span()?.segment() === deleted ||
                coin.coinPtTEnd()?.span()?.segment() === deleted ||
                coin.oppPtTStart()?.span()?.segment() === deleted ||
                coin.oppPtTEnd()?.span()?.segment() === deleted) {
                release(fHead!!, coin)
            }
            coin = coin.next() ?: return
        }
    }

    /**
     * Walk [coin]'s chain and unlink every entry whose `coinPtTStart`
     * has been deleted. Mirrors the private
     * `SkOpCoincidence::releaseDeleted(SkCoincidentSpans*)`
     * (`SkOpCoincidence.cpp:1181`).
     */
    private fun releaseDeleted(coin: SkCoincidentSpans?) {
        if (coin == null) return
        val head = coin
        var prev: SkCoincidentSpans? = null
        var cur: SkCoincidentSpans? = coin
        while (cur != null) {
            val next = cur.next()
            if (cur.coinPtTStart()!!.deleted()) {
                if (prev != null) {
                    prev.setNext(next)
                } else if (head === fHead) {
                    fHead = next
                } else {
                    fTop = next
                }
            } else {
                prev = cur
            }
            cur = next
        }
    }

    /**
     * Apply [releaseDeleted] to both [fHead] and [fTop]. Mirrors
     * `SkOpCoincidence::releaseDeleted()` (`SkOpCoincidence.cpp:1208`).
     */
    fun releaseDeleted() {
        releaseDeleted(fHead)
        releaseDeleted(fTop)
    }

    /**
     * Splice the [fTop] list onto the tail of [fHead], clear [fTop],
     * then prune entries on [fHead] whose coin- or opp-segment is
     * already done. Mirrors `SkOpCoincidence::restoreHead`
     * (`SkOpCoincidence.cpp:1213`).
     */
    fun restoreHead() {
        // Find tail of fHead and append fTop.
        if (fHead == null) {
            fHead = fTop
        } else {
            var tail = fHead!!
            while (true) {
                val next = tail.next() ?: run { tail.setNext(fTop); null }
                if (next == null) break
                tail = next
            }
        }
        fTop = null
        // Prune entries whose segments are already done.
        var prev: SkCoincidentSpans? = null
        var cur = fHead
        while (cur != null) {
            val coinSeg = cur.coinPtTStart()!!.span()!!.segment()!!
            val oppSeg = cur.oppPtTStart()!!.span()!!.segment()!!
            if (coinSeg.done() || oppSeg.done()) {
                val next = cur.next()
                if (prev != null) prev.setNext(next) else fHead = next
                cur = next
            } else {
                prev = cur
                cur = cur.next()
            }
        }
    }

    /**
     * Walk [coin]'s chain ; for each entry whose endpoint pt-T equals
     * [deleted], either release the entry (when its opposite endpoint
     * lives on the same span as [kept] — i.e. the range collapses) or
     * rewire that endpoint to [kept]. Mirrors the private
     * `SkOpCoincidence::fixUp(SkCoincidentSpans*, SkOpPtT*, SkOpPtT*)`
     * (`SkOpCoincidence.cpp:1307`).
     */
    private fun fixUp(coin: SkCoincidentSpans, deleted: SkOpPtT, kept: SkOpPtT) {
        val head = coin
        var cur: SkCoincidentSpans? = coin
        while (cur != null) {
            if (cur.coinPtTStart() === deleted) {
                if (cur.coinPtTEnd()!!.span() === kept.span()) {
                    release(head, cur); cur = cur.next(); continue
                }
                cur.setCoinPtTStart(kept)
            }
            if (cur.coinPtTEnd() === deleted) {
                if (cur.coinPtTStart()!!.span() === kept.span()) {
                    release(head, cur); cur = cur.next(); continue
                }
                cur.setCoinPtTEnd(kept)
            }
            if (cur.oppPtTStart() === deleted) {
                if (cur.oppPtTEnd()!!.span() === kept.span()) {
                    release(head, cur); cur = cur.next(); continue
                }
                cur.setOppPtTStart(kept)
            }
            if (cur.oppPtTEnd() === deleted) {
                if (cur.oppPtTStart()!!.span() === kept.span()) {
                    release(head, cur); cur = cur.next(); continue
                }
                cur.setOppPtTEnd(kept)
            }
            cur = cur.next()
        }
    }

    /**
     * Rewire every reference to [deleted] (across [fHead] and [fTop])
     * to [kept], releasing any entry whose range collapses as a
     * result. Mirrors `SkOpCoincidence::fixUp(SkOpPtT*, const
     * SkOpPtT*)` (`SkOpCoincidence.cpp:1297`).
     */
    fun fixUp(deleted: SkOpPtT, kept: SkOpPtT) {
        require(deleted !== kept)
        fHead?.let { fixUp(it, deleted, kept) }
        fTop?.let { fixUp(it, deleted, kept) }
    }

    /**
     * Walk [coin]'s chain ; release every entry that has collapsed
     * onto [test]. When the collapse spans the full t-range of either
     * side ([0, 1]), mark that segment fully done. Mirrors the
     * private `SkOpCoincidence::markCollapsed(SkCoincidentSpans*,
     * SkOpPtT*)` (`SkOpCoincidence.cpp:1389`).
     */
    private fun markCollapsed(coin: SkCoincidentSpans?, test: SkOpPtT) {
        val head = coin ?: return
        var cur: SkCoincidentSpans? = head
        while (cur != null) {
            if (cur.collapsed(test)) {
                if (zero_or_one(cur.coinPtTStart()!!.fT) &&
                    zero_or_one(cur.coinPtTEnd()!!.fT)) {
                    cur.coinPtTStart()!!.span()!!.segment()!!.markAllDone()
                }
                if (zero_or_one(cur.oppPtTStart()!!.fT) &&
                    zero_or_one(cur.oppPtTEnd()!!.fT)) {
                    cur.oppPtTStart()!!.span()!!.segment()!!.markAllDone()
                }
                release(head, cur)
            }
            cur = cur.next()
        }
    }

    /**
     * Apply [markCollapsed] to both [fHead] and [fTop]. Mirrors
     * `SkOpCoincidence::markCollapsed(SkOpPtT*)`
     * (`SkOpCoincidence.cpp:1406`).
     */
    fun markCollapsed(test: SkOpPtT) {
        markCollapsed(fHead, test)
        markCollapsed(fTop, test)
    }

    // ─── overlap-detection predicates (D1.2.g.c.1) ─────────────────

    /**
     * Pure t-arithmetic intersection of two coin-side ranges that
     * already live on the same segment. Writes the t-overlap into
     * [overOut]`[0]` (start) and [overOut]`[1]` (end). Returns true
     * iff the overlap is non-empty.
     *
     * Mirrors `SkOpCoincidence::overlap`
     * (`SkOpCoincidence.cpp:1434`). Caller must guarantee
     * `coin1s.segment() === coin2s.segment()`.
     */
    fun overlap(
        coin1s: SkOpPtT, coin1e: SkOpPtT,
        coin2s: SkOpPtT, coin2e: SkOpPtT,
        overOut: DoubleArray,
    ): Boolean {
        require(overOut.size >= 2)
        require(coin1s.span()?.segment() === coin2s.span()?.segment())
        val overS = maxOf(minOf(coin1s.fT, coin1e.fT), minOf(coin2s.fT, coin2e.fT))
        val overE = minOf(maxOf(coin1s.fT, coin1e.fT), maxOf(coin2s.fT, coin2e.fT))
        overOut[0] = overS
        overOut[1] = overE
        return overS < overE
    }

    /**
     * Walk [check]'s chain looking for entries on the same `(coinSeg,
     * oppSeg)` pair as the candidate range `[coinTs..coinTe] /
     * `[oppTs..oppTe]`, classify each into one of three buckets :
     *
     *  - **fully outside** any tracked entry — keep walking ;
     *  - **fully inside** an existing entry (already covered) —
     *    abort, return `false` ;
     *  - **partial overlap** — append the entry to [overlaps] for the
     *    caller to merge / extend.
     *
     * Pre-canonicalises via [Ordered] (recursing on the swapped pair
     * if needed), and tracks a `swapOpp` flag for entries whose opp
     * range runs in the opposite direction.
     *
     * Returns true if the caller should add or merge ; false if the
     * candidate range is already fully tracked.
     *
     * Mirrors `SkOpCoincidence::checkOverlap`
     * (`SkOpCoincidence.cpp:576`).
     */
    /**
     * Translate an overlap range on a third segment (`over1s`'s
     * segment) onto the (coinSeg, oppSeg) pair via [TRange], then
     * fall through to [addOrOverlap]. Caller's `addedOut[0]` is set
     * if a new coincidence pair was actually inserted (or merged).
     *
     * Returns `false` only on the underlying `addOrOverlap`
     * abort-paths when the resulting range is malformed ; returns
     * `true` on the collapsed-range short-circuit (which is the
     * upstream's bool-encoded variant).
     *
     * Mirrors `SkOpCoincidence::addIfMissing`
     * (`SkOpCoincidence.cpp:627`).
     */
    // ─── marking pass (D1.2.g.e) ───────────────────────────────────

    /**
     * For every entry on [fHead], snap each endpoint to its span's
     * canonical pt-T. Fast path : empty fHead returns immediately.
     *
     * Mirrors `SkOpCoincidence::correctEnds()`
     * (`SkOpCoincidence.cpp:1014`).
     */
    fun correctEnds() {
        var coin = fHead ?: return
        while (true) {
            coin.correctEnds()
            coin = coin.next() ?: return
        }
    }

    /**
     * Set up the coincidence linkage on each entry's spans : mark
     * the start / end pairs via `insertCoincidence` / `insertCoinEnd`,
     * then walk the interior spans on both sides and route each one
     * through the segment-overload of `insertCoincidence`. Returns
     * `false` on any failure path (non-upcastable terminal, ordered
     * lookup failure, etc.).
     *
     * Mirrors `SkOpCoincidence::mark` (`SkOpCoincidence.cpp:1343`).
     */
    fun mark(): Boolean {
        var coin: SkCoincidentSpans = fHead ?: return true
        while (true) {
            val startBase = coin.coinPtTStart()!!.span() ?: return false
            if (startBase.final()) return false
            val start = startBase.upCast()
            if (start.deleted()) return false
            val end = coin.coinPtTEnd()!!.span() ?: return false
            require(!end.deleted())
            var oStartBase = coin.oppPtTStart()!!.span() ?: return false
            require(!oStartBase.deleted())
            var oEndBase = coin.oppPtTEnd()!!.span() ?: return false
            if (oEndBase.deleted()) return false
            val flipped = coin.flipped()
            if (flipped) {
                val tmp = oStartBase; oStartBase = oEndBase; oEndBase = tmp
            }
            if (oStartBase.final()) return false
            val oStart = oStartBase.upCast()
            start.insertCoincidence(oStart)
            end.insertCoinEnd(oEndBase)
            val seg = start.segment()!!
            val oSeg = oStart.segment()!!
            val orderedOut = booleanArrayOf(false)
            if (!coin.ordered(orderedOut)) return false
            val ordered = orderedOut[0]
            // Walk interior coin-side spans.
            var nextBase: SkOpSpanBase = start
            while (true) {
                val nxt = nextBase.upCast().next() ?: return false
                if (nxt === end) break
                if (nxt.final()) return false
                if (!nxt.upCast().insertCoincidence(oSeg, flipped, ordered)) return false
                nextBase = nxt
            }
            // Walk interior opp-side spans.
            var oNextBase: SkOpSpanBase = oStart
            while (true) {
                val nxt = oNextBase.upCast().next() ?: return false
                if (nxt === oEndBase) break
                if (nxt.final()) return false
                if (!nxt.upCast().insertCoincidence(seg, flipped, ordered)) return false
                oNextBase = nxt
            }
            coin = coin.next() ?: return true
        }
    }

    /**
     * Drive [SkCoincidentSpans.expand] on every entry ; on each
     * successful expansion, scan the rest of the chain for an entry
     * that now matches the same `(coinPtTStart, oppPtTStart)` pair
     * and release the duplicate. Returns true iff at least one entry
     * actually expanded.
     *
     * Mirrors `SkOpCoincidence::expand` (`SkOpCoincidence.cpp:1234`).
     */
    fun expand(): Boolean {
        var coin: SkCoincidentSpans = fHead ?: return false
        var expanded = false
        while (true) {
            if (coin.expand()) {
                var test = fHead
                while (test != null) {
                    if (test !== coin &&
                        coin.coinPtTStart() === test.coinPtTStart() &&
                        coin.oppPtTStart() === test.oppPtTStart()) {
                        release(fHead!!, test)
                        break
                    }
                    test = test.next()
                }
                expanded = true
            }
            coin = coin.next() ?: return expanded
        }
    }

    /**
     * For every coincident pair, walk both sides in lockstep ;
     * whenever a coin-side span lacks a counterpart on the opp side
     * (or vice-versa), call [SkOpSegment.addExpanded] to insert a
     * matching pt-T at the linearly-interpolated t-value, then loop.
     *
     * Returns false on any failure path (degenerate t-range,
     * non-upcastable terminal, addExpanded failure).
     *
     * Mirrors `SkOpCoincidence::addExpanded`
     * (`SkOpCoincidence.cpp:440`).
     */
    fun addExpanded(): Boolean {
        var coin: SkCoincidentSpans = fHead ?: return true
        while (true) {
            val startPtT = coin.coinPtTStart()!!
            val oStartPtT = coin.oppPtTStart()!!
            var priorT = startPtT.fT
            var oPriorT = oStartPtT.fT
            if (!startPtT.contains(oStartPtT)) return false
            require(coin.coinPtTEnd()!!.contains(coin.oppPtTEnd()!!))
            val start = startPtT.span()!!
            val oStart = oStartPtT.span()!!
            var endRef = coin.coinPtTEnd()!!.span()!!
            var oEndRef = coin.oppPtTEnd()!!.span()!!
            if (oEndRef.deleted()) return false
            if (start.final()) return false
            var test: SkOpSpanBase = start.upCast().next() ?: return false
            if (!coin.flipped() && oStart.final()) return false
            var oTest: SkOpSpanBase = if (coin.flipped()) {
                oStart.prev() ?: return false
            } else {
                oStart.upCast().next() ?: return false
            }
            val seg = start.segment()!!
            val oSeg = oStart.segment()!!
            while (test !== endRef || oTest !== oEndRef) {
                val containedOpp = test.contains(oSeg)
                val containedThis = oTest.contains(seg)
                if (containedOpp == null || containedThis == null) {
                    val nextT: Double; val oNextT: Double
                    when {
                        containedOpp != null -> {
                            nextT = test.t(); oNextT = containedOpp.fT
                        }
                        containedThis != null -> {
                            nextT = containedThis.fT; oNextT = oTest.t()
                        }
                        else -> {
                            // Walk forward until we find a span that brackets oSeg.
                            var walk: SkOpSpanBase = test
                            var walkOpp: SkOpPtT? = null
                            while (walkOpp == null && walk !== coin.coinPtTEnd()!!.span()) {
                                if (walk.final()) return false
                                walk = walk.upCast().next() ?: return false
                                walkOpp = walk.contains(oSeg)
                            }
                            if (walkOpp == null) return false
                            nextT = walk.t(); oNextT = walkOpp.fT
                        }
                    }
                    val startRange = nextT - priorT
                    if (startRange == 0.0) return false
                    val startPart = (test.t() - priorT) / startRange
                    val oStartRange = oNextT - oPriorT
                    if (oStartRange == 0.0) return false
                    val oStartPart = (oTest.t() - oPriorT) / oStartRange
                    if (startPart == oStartPart) return false
                    val addToOpp = if (containedOpp == null && containedThis == null) {
                        startPart < oStartPart
                    } else {
                        containedThis != null
                    }
                    val startOver = booleanArrayOf(false)
                    val ok = if (addToOpp) {
                        oSeg.addExpanded(oPriorT + oStartRange * startPart, test, startOver)
                    } else {
                        seg.addExpanded(priorT + startRange * oStartPart, oTest, startOver)
                    }
                    if (!ok) return false
                    if (startOver[0]) {
                        test = start
                        oTest = oStart
                    }
                    endRef = coin.coinPtTEnd()!!.span()!!
                    oEndRef = coin.oppPtTEnd()!!.span()!!
                }
                if (test !== endRef) {
                    if (test.final()) return false
                    priorT = test.t()
                    test = test.upCast().next() ?: return false
                }
                if (oTest !== oEndRef) {
                    oPriorT = oTest.t()
                    oTest = if (coin.flipped()) {
                        oTest.prev() ?: return false
                    } else {
                        if (oTest.final()) return false
                        oTest.upCast().next() ?: return false
                    }
                }
            }
            coin = coin.next() ?: return true
        }
    }

    /**
     * Walk every coincidence pair in lockstep ; for each (coin, opp)
     * span pair, fold the winding values from one side to the other
     * (sign and combination depending on `flipped`, `operandSwap`,
     * and the enclosing segments' `isXor` / `oppXor` flags). Spans
     * whose winding drops to zero on both axes get marked done.
     *
     * Returns false on any failure path (negative wind, non-final
     * terminal mismatch).
     *
     * Mirrors `SkOpCoincidence::apply` (`SkOpCoincidence.cpp:1026`).
     */
    fun apply(): Boolean {
        var coin: SkCoincidentSpans = fHead ?: return true
        while (true) {
            val startBase = coin.coinPtTStart()!!.span() ?: return false
            if (startBase.final()) return false
            var start = startBase.upCast()
            if (!start.deleted()) {
                val end = coin.coinPtTEnd()!!.span() ?: return false
                if (start !== start.starter(end)) return false
                val flipped = coin.flipped()
                val oStartBase = (if (flipped) coin.oppPtTEnd()!! else coin.oppPtTStart()!!).span() ?: return false
                if (oStartBase.final()) return false
                var oStart = oStartBase.upCast()
                if (!oStart.deleted()) {
                    val oEnd = (if (flipped) coin.oppPtTStart()!! else coin.oppPtTEnd()!!).span() ?: return false
                    require(oStart === oStart.starter(oEnd))
                    val seg = start.segment()!!
                    val oSeg = oStart.segment()!!
                    val operandSwap = seg.operand() != oSeg.operand()
                    if (flipped) {
                        if (oEnd.deleted()) {
                            coin = coin.next() ?: return true
                            continue
                        }
                        while (true) {
                            val oNxt = oStart.next() ?: return false
                            if (oNxt === oEnd) break
                            if (oNxt.final()) return false
                            oStart = oNxt.upCast()
                        }
                    }
                    while (true) {
                        var windValue = start.windValue()
                        var oppValue = start.oppValue()
                        var oWindValue = oStart.windValue()
                        var oOppValue = oStart.oppValue()
                        var windDiff = if (operandSwap) oOppValue else oWindValue
                        var oWindDiff = if (operandSwap) oppValue else windValue
                        if (!flipped) { windDiff = -windDiff; oWindDiff = -oWindDiff }
                        var addToStart = windValue != 0 && (windValue > windDiff ||
                            (windValue == windDiff && oWindValue <= oWindDiff))
                        if (if (addToStart) start.done() else oStart.done()) addToStart = !addToStart
                        if (addToStart) {
                            if (operandSwap) { val tmp = oWindValue; oWindValue = oOppValue; oOppValue = tmp }
                            if (flipped) {
                                windValue -= oWindValue
                                oppValue -= oOppValue
                            } else {
                                windValue += oWindValue
                                oppValue += oOppValue
                            }
                            if (seg.isXor()) windValue = windValue and 1
                            if (seg.oppXor()) oppValue = oppValue and 1
                            oWindValue = 0; oOppValue = 0
                        } else {
                            if (operandSwap) { val tmp = windValue; windValue = oppValue; oppValue = tmp }
                            if (flipped) {
                                oWindValue -= windValue
                                oOppValue -= oppValue
                            } else {
                                oWindValue += windValue
                                oOppValue += oppValue
                            }
                            if (oSeg.isXor()) oWindValue = oWindValue and 1
                            if (oSeg.oppXor()) oOppValue = oOppValue and 1
                            windValue = 0; oppValue = 0
                        }
                        if (windValue <= -1) return false
                        start.setWindValue(windValue)
                        start.setOppValue(oppValue)
                        if (oWindValue <= -1) return false
                        oStart.setWindValue(oWindValue)
                        oStart.setOppValue(oOppValue)
                        if (windValue == 0 && oppValue == 0) seg.markDone(start)
                        if (oWindValue == 0 && oOppValue == 0) oSeg.markDone(oStart)
                        val next = start.next() ?: return false
                        var oNext: SkOpSpanBase? = if (flipped) oStart.prev() else oStart.next()
                        if (next === end) break
                        if (next.final()) return false
                        start = next.upCast()
                        if (oNext == null || oNext.final()) {
                            oNext = oStart
                        }
                        oStart = oNext.upCast()
                    }
                }
            }
            coin = coin.next() ?: return true
        }
    }

    /**
     * Public driver : detect coincidence pairs that share at least
     * one segment with another already-tracked pair, but whose
     * t-overlap on the shared side hasn't been recorded yet.
     * Snapshots [fHead] into [fTop], walks every (outer, inner) pair,
     * and routes through [addIfMissing] for each shared-segment
     * overlap. Restores [fHead] on the way out.
     *
     * Sets `addedOut[0]` when a new pair was actually inserted
     * (delegated through `addIfMissing` → `addOrOverlap`). Returns
     * true when the caller should loop again ; false on a hard
     * abort.
     *
     * Mirrors `SkOpCoincidence::addMissing`
     * (`SkOpCoincidence.cpp:797`).
     */
    fun addMissing(addedOut: BooleanArray): Boolean {
        addedOut[0] = false
        var outer: SkCoincidentSpans? = fHead ?: return true
        fTop = outer
        fHead = null
        val overOut = DoubleArray(2)
        while (outer != null) {
            val ocs = outer.coinPtTStart()!!
            if (ocs.deleted()) return false
            val outerCoin = ocs.span()?.segment() ?: return false
            if (outerCoin.done()) return false
            val oos = outer.oppPtTStart()!!
            if (oos.deleted()) return true
            val outerOpp = oos.span()?.segment() ?: return false
            var inner = outer.next()
            while (inner != null) {
                val ics = inner.coinPtTStart()!!
                if (ics.deleted()) return false
                val innerCoin = ics.span()?.segment() ?: return false
                if (innerCoin.done()) return false
                val ios = inner.oppPtTStart()!!
                if (ios.deleted()) return false
                val innerOpp = ios.span()?.segment() ?: return false
                when {
                    outerCoin === innerCoin -> {
                        val oce = outer.coinPtTEnd()!!
                        if (oce.deleted()) return true
                        val ice = inner.coinPtTEnd()!!
                        if (ice.deleted()) return false
                        if (outerOpp !== innerOpp &&
                            overlap(ocs, oce, ics, ice, overOut)) {
                            if (!addIfMissing(ocs.starter(oce), ics.starter(ice),
                                    overOut[0], overOut[1], outerOpp, innerOpp, addedOut)) {
                                return false
                            }
                        }
                    }
                    outerCoin === innerOpp -> {
                        val oce = outer.coinPtTEnd()!!
                        if (oce.deleted()) return false
                        val ioe = inner.oppPtTEnd()!!
                        if (ioe.deleted()) return false
                        if (outerOpp !== innerCoin &&
                            overlap(ocs, oce, ios, ioe, overOut)) {
                            if (!addIfMissing(ocs.starter(oce), ios.starter(ioe),
                                    overOut[0], overOut[1], outerOpp, innerCoin, addedOut)) {
                                return false
                            }
                        }
                    }
                    outerOpp === innerCoin -> {
                        val ooe = outer.oppPtTEnd()!!
                        if (ooe.deleted()) return false
                        val ice = inner.coinPtTEnd()!!
                        if (ice.deleted()) return false
                        require(outerCoin !== innerOpp)
                        if (overlap(oos, ooe, ics, ice, overOut)) {
                            if (!addIfMissing(oos.starter(ooe), ics.starter(ice),
                                    overOut[0], overOut[1], outerCoin, innerOpp, addedOut)) {
                                return false
                            }
                        }
                    }
                    outerOpp === innerOpp -> {
                        val ooe = outer.oppPtTEnd()!!
                        if (ooe.deleted()) return false
                        val ioe = inner.oppPtTEnd()!!
                        if (ioe.deleted()) return true
                        require(outerCoin !== innerCoin)
                        if (overlap(oos, ooe, ios, ioe, overOut)) {
                            if (!addIfMissing(oos.starter(ooe), ios.starter(ioe),
                                    overOut[0], overOut[1], outerCoin, innerCoin, addedOut)) {
                                return false
                            }
                        }
                    }
                }
                inner = inner.next()
            }
            outer = outer.next()
        }
        restoreHead()
        return true
    }

    /**
     * For each non-deleted, non-`baseSeg`, canonical pt-T on
     * [testSpan]'s loop : if the perpendicular ray from [base]'s
     * point hits its segment near the same point, attach a fresh
     * pt-T at that intersection and ask [addOrOverlap] to track the
     * resulting coincidence pair.
     *
     * Mirrors the private `SkOpCoincidence::addEndMovedSpans(const
     * SkOpSpan*, const SkOpSpanBase*)`
     * (`SkOpCoincidence.cpp:289`).
     */
    private fun addEndMovedSpans(base: SkOpSpan, testSpan: SkOpSpanBase): Boolean {
        val stopPtT = testSpan.ptT()
        val baseSeg = base.segment() ?: return false
        var escapeHatch = 100_000
        var testPtT: SkOpPtT = stopPtT.next() ?: return true
        while (testPtT !== stopPtT) {
            if (--escapeHatch <= 0) return false
            val testSeg = testPtT.span()?.segment()
            if (!testPtT.deleted() && testSeg !== null && testSeg !== baseSeg &&
                testPtT.span()?.ptT() === testPtT &&
                !contains(baseSeg, testSeg, testPtT.fT)) {
                val dxdy = baseSeg.dSlopeAtT(base.t())
                val pt = base.pt()
                val ray = SkDLine().apply {
                    set(0, SkDPoint(pt.fX.toDouble(), pt.fY.toDouble()))
                    set(1, SkDPoint(pt.fX + dxdy.y, pt.fY - dxdy.x))
                }
                val ix = SkIntersections()
                testSeg.intersectRay(ray, ix)
                for (index in 0 until ix.used()) {
                    val t = ix.t(0, index)
                    if (!between(0.0, t, 1.0)) continue
                    val oppPt = ix.pt(index)
                    if (!oppPt.approximatelyEqual(SkDPoint(pt.fX.toDouble(), pt.fY.toDouble()))) continue
                    val oppStart = testSeg.addT(t) ?: continue
                    if (oppStart === testPtT) continue
                    if (oppStart.span()?.addOpp(base) != true) continue
                    if (oppStart.deleted()) continue
                    var coinSeg = base.segment()!!
                    var oppSeg = oppStart.span()?.segment() ?: continue
                    var coinTs: Double; var coinTe: Double
                    var oppTs: Double; var oppTe: Double
                    if (Ordered(coinSeg, oppSeg)) {
                        coinTs = base.t(); coinTe = testSpan.t()
                        oppTs = oppStart.fT; oppTe = testPtT.fT
                    } else {
                        val swap = coinSeg; coinSeg = oppSeg; oppSeg = swap
                        coinTs = oppStart.fT; coinTe = testPtT.fT
                        oppTs = base.t(); oppTe = testSpan.t()
                    }
                    if (coinTs > coinTe) {
                        val ttmp = coinTs; coinTs = coinTe; coinTe = ttmp
                        val otmp = oppTs; oppTs = oppTe; oppTe = otmp
                    }
                    val added = booleanArrayOf(false)
                    if (!addOrOverlap(coinSeg, oppSeg, coinTs, coinTe, oppTs, oppTe, added)) {
                        return false
                    }
                }
            }
            testPtT = testPtT.next() ?: return true
        }
        return true
    }

    /**
     * For [ptT]'s span: walk both adjacent (prev / next) spans, and
     * for each one that isn't already canceled, run the
     * `(base, testSpan)` overload.
     *
     * Mirrors the private `SkOpCoincidence::addEndMovedSpans(const
     * SkOpPtT*)` (`SkOpCoincidence.cpp:365`).
     */
    private fun addEndMovedSpans(ptT: SkOpPtT): Boolean {
        val base = ptT.span()?.upCastable() ?: return false
        val prev = base.prev() ?: return false
        if (!prev.isCanceled()) {
            if (!addEndMovedSpans(base, base.prev()!!)) return false
        }
        if (!base.isCanceled()) {
            val nxt = base.next() ?: return false
            if (!addEndMovedSpans(base, nxt)) return false
        }
        return true
    }

    /**
     * Public driver : snapshot [fHead] into [fTop], then for every
     * coincidence entry whose coin- and opp-side pt have drifted at
     * an end, scan the adjacent pt-T loop for a near-coincident
     * segment via the ray-cast helper. Restores [fHead] (with
     * [restoreHead]) on the way out.
     *
     * Mirrors `SkOpCoincidence::addEndMovedSpans()`
     * (`SkOpCoincidence.cpp:392`).
     */
    fun addEndMovedSpans(): Boolean {
        var span = fHead ?: return true
        fTop = span
        fHead = null
        do {
            val coinStart = span.coinPtTStart()!!
            val oppStart = span.oppPtTStart()!!
            if (coinStart.fPt != oppStart.fPt) {
                if (coinStart.fT == 1.0) return false
                val onEnd = coinStart.fT == 0.0
                val oOnEnd = zero_or_one(oppStart.fT)
                if (onEnd) {
                    if (!oOnEnd) {
                        if (!addEndMovedSpans(oppStart)) return false
                    }
                } else if (oOnEnd) {
                    if (!addEndMovedSpans(coinStart)) return false
                }
            }
            val coinEnd = span.coinPtTEnd()!!
            val oppEnd = span.oppPtTEnd()!!
            if (coinEnd.fPt != oppEnd.fPt) {
                val onEnd = coinEnd.fT == 1.0
                val oOnEnd = zero_or_one(oppEnd.fT)
                if (onEnd) {
                    if (!oOnEnd) {
                        if (!addEndMovedSpans(oppEnd)) return false
                    }
                } else if (oOnEnd) {
                    if (!addEndMovedSpans(coinEnd)) return false
                }
            }
            span = span.next() ?: break
        } while (true)
        restoreHead()
        return true
    }

    /**
     * Resolve overlapping pt-Ts on `(seg1, seg1o)` × `(seg2, seg2o)`
     * around an outer t-overlap `[overS..overE]` and append a fresh
     * coincidence pair via [add]. Returns true on the no-op paths
     * (zero windValue or both ranges on the same segment) ; returns
     * false on the `find` failure paths.
     *
     * Mirrors `SkOpCoincidence::addOverlap`
     * (`SkOpCoincidence.cpp:901`).
     */
    fun addOverlap(
        seg1: SkOpSegment, seg1o: SkOpSegment,
        seg2: SkOpSegment, seg2o: SkOpSegment,
        overS: SkOpPtT, overE: SkOpPtT,
    ): Boolean {
        var s1 = overS.find(seg1) ?: return false
        var e1 = overE.find(seg1) ?: return false
        if (s1.starter(e1).span()!!.upCast().windValue() == 0) {
            s1 = overS.find(seg1o) ?: return false
            e1 = overE.find(seg1o) ?: return false
            if (s1.starter(e1).span()!!.upCast().windValue() == 0) return true
        }
        var s2 = overS.find(seg2) ?: return false
        var e2 = overE.find(seg2) ?: return false
        if (s2.starter(e2).span()!!.upCast().windValue() == 0) {
            s2 = overS.find(seg2o) ?: return false
            e2 = overE.find(seg2o) ?: return false
            if (s2.starter(e2).span()!!.upCast().windValue() == 0) return true
        }
        if (s1.span()?.segment() === s2.span()?.segment()) return true
        if (s1.fT > e1.fT) {
            val t1 = s1; s1 = e1; e1 = t1
            val t2 = s2; s2 = e2; e2 = t2
        }
        add(s1, e1, s2, e2)
        return true
    }

    /**
     * Walk every pair of fHead entries ; whenever two distinct
     * segment-pair entries share at least one segment, compute the
     * t-overlap on the shared side via [SkOpPtT.Overlaps] and emit a
     * fresh coincidence pair on [out] via [addOverlap].
     *
     * Used as a "scan for cross-coincidences" pass before [addMissing].
     *
     * Mirrors `SkOpCoincidence::findOverlaps`
     * (`SkOpCoincidence.cpp:1261`).
     */
    fun findOverlaps(out: SkOpCoincidence): Boolean {
        out.fHead = null
        out.fTop = null
        var outer: SkCoincidentSpans? = fHead
        val sOut = arrayOfNulls<SkOpPtT>(1)
        val eOut = arrayOfNulls<SkOpPtT>(1)
        while (outer != null) {
            val outerCoin = outer.coinPtTStart()!!.span()?.segment() ?: return false
            val outerOpp = outer.oppPtTStart()!!.span()?.segment() ?: return false
            var inner = outer.next()
            while (inner != null) {
                val innerCoin = inner.coinPtTStart()!!.span()?.segment() ?: return false
                if (outerCoin === innerCoin) {
                    inner = inner.next(); continue
                }
                val innerOpp = inner.oppPtTStart()!!.span()?.segment() ?: return false
                val matched = (outerOpp === innerCoin && SkOpPtT.Overlaps(
                        outer.oppPtTStart()!!, outer.oppPtTEnd()!!,
                        inner.coinPtTStart()!!, inner.coinPtTEnd()!!, sOut, eOut)) ||
                    (outerCoin === innerOpp && SkOpPtT.Overlaps(
                        outer.coinPtTStart()!!, outer.coinPtTEnd()!!,
                        inner.oppPtTStart()!!, inner.oppPtTEnd()!!, sOut, eOut)) ||
                    (outerOpp === innerOpp && SkOpPtT.Overlaps(
                        outer.oppPtTStart()!!, outer.oppPtTEnd()!!,
                        inner.oppPtTStart()!!, inner.oppPtTEnd()!!, sOut, eOut))
                if (matched) {
                    if (!out.addOverlap(outerCoin, outerOpp, innerCoin, innerOpp,
                            sOut[0]!!, eOut[0]!!)) {
                        return false
                    }
                }
                inner = inner.next()
            }
            outer = outer.next()
        }
        return true
    }

    fun addIfMissing(
        over1s: SkOpPtT, over2s: SkOpPtT,
        tStart: Double, tEnd: Double,
        coinSeg: SkOpSegment, oppSeg: SkOpSegment,
        addedOut: BooleanArray,
    ): Boolean {
        require(tStart < tEnd)
        require(over1s.span()?.segment() === over2s.span()?.segment())
        require(over1s.span()?.segment() !== coinSeg)
        require(over1s.span()?.segment() !== oppSeg)
        require(coinSeg !== oppSeg)
        var coinTs = TRange(over1s, tStart, coinSeg)
        var coinTe = TRange(over1s, tEnd, coinSeg)
        var c = coinSeg.collapsed(coinTs, coinTe)
        if (c != SkOpSpanBase.Collapsed.kNo) {
            return c == SkOpSpanBase.Collapsed.kYes
        }
        var oppTs = TRange(over2s, tStart, oppSeg)
        var oppTe = TRange(over2s, tEnd, oppSeg)
        c = oppSeg.collapsed(oppTs, oppTe)
        if (c != SkOpSpanBase.Collapsed.kNo) {
            return c == SkOpSpanBase.Collapsed.kYes
        }
        if (coinTs > coinTe) {
            val tmp1 = coinTs; coinTs = coinTe; coinTe = tmp1
            val tmp2 = oppTs; oppTs = oppTe; oppTe = tmp2
        }
        addOrOverlap(coinSeg, oppSeg, coinTs, coinTe, oppTs, oppTe, addedOut)
        return true
    }

    /**
     * Add a coincidence pair `(coinSeg[coinTs..coinTe],
     * oppSeg[oppTs..oppTe])`, or merge it into an existing pair when
     * the ranges overlap. Side-effect : may allocate fresh pt-Ts on
     * `coinSeg` / `oppSeg` (via [SkOpSegment.addT]) and splice them
     * via [SkOpSpanBase.addOpp]. Sets `addedOut[0]` on success.
     *
     * Returns `false` on any of the abort conditions in the upstream
     * (collapsed-range degeneracies, deleted pt-Ts, span allocations
     * that fail) — in those cases the caller (`addMissing` /
     * `addEndMovedSpans`) treats the pair as untrackable and moves on.
     *
     * Mirrors `SkOpCoincidence::addOrOverlap`
     * (`SkOpCoincidence.cpp:668`).
     *
     * Pre-condition : [fTop] non-null. The upstream consumer
     * `addMissing` arranges this by snapshotting `fHead` into `fTop`
     * before walking ; calling `addOrOverlap` on a fresh container
     * fails (the upstream `FAIL_IF(!fTop)`).
     */
    fun addOrOverlap(
        coinSeg: SkOpSegment, oppSeg: SkOpSegment,
        coinTs: Double, coinTe: Double,
        oppTs: Double, oppTe: Double,
        addedOut: BooleanArray,
    ): Boolean {
        val overlaps = mutableListOf<SkCoincidentSpans>()
        if (fTop == null) return false
        if (!checkOverlap(fTop, coinSeg, oppSeg, coinTs, coinTe, oppTs, oppTe, overlaps)) {
            return true
        }
        if (fHead != null && !checkOverlap(
                fHead, coinSeg, oppSeg, coinTs, coinTe, oppTs, oppTe, overlaps)) {
            return true
        }
        val overlap: SkCoincidentSpans? = overlaps.firstOrNull()
        // Fold any further overlaps into the first one.
        for (idx in 1 until overlaps.size) {
            val test = overlaps[idx]
            val o = overlap!!
            if (o.coinPtTStart()!!.fT > test.coinPtTStart()!!.fT) {
                o.setCoinPtTStart(test.coinPtTStart()!!)
            }
            if (o.coinPtTEnd()!!.fT < test.coinPtTEnd()!!.fT) {
                o.setCoinPtTEnd(test.coinPtTEnd()!!)
            }
            val moveOppStart = if (o.flipped()) {
                o.oppPtTStart()!!.fT < test.oppPtTStart()!!.fT
            } else {
                o.oppPtTStart()!!.fT > test.oppPtTStart()!!.fT
            }
            if (moveOppStart) o.setOppPtTStart(test.oppPtTStart()!!)
            val moveOppEnd = if (o.flipped()) {
                o.oppPtTEnd()!!.fT > test.oppPtTEnd()!!.fT
            } else {
                o.oppPtTEnd()!!.fT < test.oppPtTEnd()!!.fT
            }
            if (moveOppEnd) o.setOppPtTEnd(test.oppPtTEnd()!!)
            if (fHead == null || !release(fHead!!, test)) {
                require(release(fTop!!, test))
            }
        }
        var cs = coinSeg.existing(coinTs, oppSeg)
        var ce = coinSeg.existing(coinTe, oppSeg)
        if (overlap != null && cs != null && ce != null && overlap.contains(cs, ce)) {
            return true
        }
        if (cs != null && cs === ce) return false
        var os = oppSeg.existing(oppTs, coinSeg)
        var oe = oppSeg.existing(oppTe, coinSeg)
        if (overlap != null && os != null && oe != null && overlap.contains(os, oe)) {
            return true
        }
        if (cs?.deleted() == true) return false
        if (os?.deleted() == true) return false
        if (ce?.deleted() == true) return false
        if (oe?.deleted() == true) return false
        val csExisting = if (cs == null) coinSeg.existing(coinTs, null) else null
        val ceExisting = if (ce == null) coinSeg.existing(coinTe, null) else null
        if (csExisting != null && csExisting === ceExisting) return false
        if (ceExisting != null) {
            if (ceExisting === cs) return false
            val needle = csExisting ?: cs
            if (needle != null && ceExisting.contains(needle)) return false
        }
        val osExisting = if (os == null) oppSeg.existing(oppTs, null) else null
        val oeExisting = if (oe == null) oppSeg.existing(oppTe, null) else null
        if (osExisting != null && osExisting === oeExisting) return false
        if (osExisting != null) {
            if (osExisting === oe) return false
            val needle = oeExisting ?: oe
            if (needle != null && osExisting.contains(needle)) return false
        }
        if (oeExisting != null) {
            if (oeExisting === os) return false
            val needle = osExisting ?: os
            if (needle != null && oeExisting.contains(needle)) return false
        }
        if (cs == null || os == null) {
            val csW = cs ?: coinSeg.addT(coinTs)
            if (csW === ce) return true
            val osW = os ?: oppSeg.addT(oppTs)
            if (csW == null || osW == null) return false
            csW.span()?.addOpp(osW.span()!!) ?: return false
            cs = csW
            os = osW.active() ?: return false
            if (ce?.deleted() == true || oe?.deleted() == true) return false
        }
        if (ce == null || oe == null) {
            val ceW = ce ?: coinSeg.addT(coinTe)
            val oeW = oe ?: oppSeg.addT(oppTe)
            if (ceW == null || oeW == null) return false
            if (ceW.span()?.addOpp(oeW.span()!!) != true) return false
            ce = ceW
            oe = oeW
        }
        // cs / ce / os / oe are now provably non-null on every path
        // (each `if` block above either returned or assigned).
        if (cs.deleted() || os.deleted() || ce.deleted() || oe.deleted()) return false
        if (cs.contains(ce) || os.contains(oe)) return false
        var ok = true
        if (overlap != null) {
            if (overlap.coinPtTStart()!!.span()?.segment() === coinSeg) {
                ok = overlap.extend(cs, ce, os, oe)
            } else {
                var cs2 = cs; var ce2 = ce; var os2 = os; var oe2 = oe
                if (os2.fT > oe2.fT) {
                    val t1 = cs2; cs2 = ce2; ce2 = t1
                    val t2 = os2; os2 = oe2; oe2 = t2
                }
                ok = overlap.extend(os2, oe2, cs2, ce2)
            }
        } else {
            add(cs, ce, os, oe)
        }
        if (ok) addedOut[0] = true
        return true
    }

    fun checkOverlap(
        check: SkCoincidentSpans?,
        coinSeg: SkOpSegment,
        oppSeg: SkOpSegment,
        coinTs: Double, coinTe: Double,
        oppTs: Double, oppTe: Double,
        overlaps: MutableList<SkCoincidentSpans>,
    ): Boolean {
        if (!Ordered(coinSeg, oppSeg)) {
            return if (oppTs < oppTe) {
                checkOverlap(check, oppSeg, coinSeg, oppTs, oppTe, coinTs, coinTe, overlaps)
            } else {
                checkOverlap(check, oppSeg, coinSeg, oppTe, oppTs, coinTe, coinTs, overlaps)
            }
        }
        val swapOpp = oppTs > oppTe
        var oTs = oppTs; var oTe = oppTe
        if (swapOpp) {
            val tmp = oTs; oTs = oTe; oTe = tmp
        }
        var cur = check
        while (cur != null) {
            val tCoinStart = cur.coinPtTStart()!!
            val tOppStart = cur.oppPtTStart()!!
            if (tCoinStart.span()?.segment() === coinSeg &&
                tOppStart.span()?.segment() === oppSeg) {
                val checkTs = tCoinStart.fT
                val checkTe = cur.coinPtTEnd()!!.fT
                val coinOutside = coinTe < checkTs || coinTs > checkTe
                var oCheckTs = tOppStart.fT
                var oCheckTe = cur.oppPtTEnd()!!.fT
                if (swapOpp) {
                    if (oCheckTs <= oCheckTe) return false
                    val tmp = oCheckTs; oCheckTs = oCheckTe; oCheckTe = tmp
                }
                val oppOutside = oTe < oCheckTs || oTs > oCheckTe
                if (!(coinOutside && oppOutside)) {
                    val coinInside = coinTe <= checkTe && coinTs >= checkTs
                    val oppInside = oTe <= oCheckTe && oTs >= oCheckTs
                    if (coinInside && oppInside) return false
                    overlaps.add(cur)
                }
            }
            cur = cur.next()
        }
        return true
    }

    companion object {
        /**
         * Order two segments by verb (lower verb wins) then by their
         * control-point coordinates lexicographically. Used by
         * [SkCoincidentSpans.set] to canonicalise the (coin, opp)
         * pair on construction.
         *
         * Mirrors `SkOpCoincidence::Ordered` (`SkOpCoincidence.cpp:1411`).
         */
        fun Ordered(coinSeg: SkOpSegment, oppSeg: SkOpSegment): Boolean {
            val cVerb = coinSeg.verb().ordinal
            val oVerb = oppSeg.verb().ordinal
            if (cVerb < oVerb) return true
            if (cVerb > oVerb) return false
            val count = (segVerbToPoints(coinSeg.verb()) + 1) * 2
            val cPts = coinSeg.pts()
            val oPts = oppSeg.pts()
            for (index in 0 until count) {
                val ci = index ushr 1
                val cIsX = (index and 1) == 0
                val cVal = if (cIsX) cPts[ci].fX else cPts[ci].fY
                val oVal = if (cIsX) oPts[ci].fX else oPts[ci].fY
                if (cVal < oVal) return true
                if (cVal > oVal) return false
            }
            return true
        }

        /** pt-T overload : delegates to the segment overload. */
        fun Ordered(coinPtTStart: SkOpPtT, oppPtTStart: SkOpPtT): Boolean {
            val coinSeg = coinPtTStart.span()?.segment() ?: return true
            val oppSeg = oppPtTStart.span()?.segment() ?: return true
            return Ordered(coinSeg, oppSeg)
        }

        /**
         * Linearly remap [t] (a t-value on `overS`'s segment) onto
         * [coinSeg], using the bracketing pair of pt-Ts on `overS`'s
         * span loop that have a counterpart on [coinSeg]. Returns `1`
         * (the upstream sentinel) when no bracket can be found.
         *
         * Curves don't scale linearly across the full overlap, so we
         * only interpolate inside a single sub-bracket — this is what
         * lets `addIfMissing` translate a third-segment overlap onto
         * coin / opp sides.
         *
         * Mirrors `SkOpCoincidence::TRange`
         * (`SkOpCoincidence.cpp:540`).
         */
        fun TRange(overS: SkOpPtT, t: Double, coinSeg: SkOpSegment): Double {
            var work: SkOpSpanBase? = overS.span()
            var foundStart: SkOpPtT? = null
            var foundEnd: SkOpPtT? = null
            var coinStart: SkOpPtT? = null
            var coinEnd: SkOpPtT? = null
            while (work != null) {
                val contained = work.contains(coinSeg)
                if (contained == null) {
                    if (work.final()) break
                    work = work.upCast().next()
                    continue
                }
                if (work.t() <= t) {
                    coinStart = contained
                    foundStart = work.ptT()
                }
                if (work.t() >= t) {
                    coinEnd = contained
                    foundEnd = work.ptT()
                    break
                }
                work = work.upCast().next()
            }
            if (coinStart == null || coinEnd == null ||
                foundStart == null || foundEnd == null) {
                return 1.0
            }
            val denom = foundEnd.fT - foundStart.fT
            val sRatio = if (denom != 0.0) (t - foundStart.fT) / denom else 1.0
            return coinStart.fT + (coinEnd.fT - coinStart.fT) * sRatio
        }
    }
}
