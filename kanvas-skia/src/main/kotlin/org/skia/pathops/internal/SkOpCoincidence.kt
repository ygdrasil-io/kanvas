/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkCoincidentSpans` and `class SkOpCoincidence`
 * from `src/pathops/SkOpCoincidence.{h,cpp}`.
 *
 * Phase D1.2.g.0 — SkCoincidentSpans data model + simple methods, plus
 * the SkOpCoincidence skeleton (head pointer + Ordered statics). The
 * heavy SkOpCoincidence methods (add / extend / addMissing / addOverlap
 * / apply / mark / expand / fixUp / release / etc.) land in subsequent
 * D1.2.g sub-slices.
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
        val prev = origSpan.upCastable()?.prev()
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
    }
}
