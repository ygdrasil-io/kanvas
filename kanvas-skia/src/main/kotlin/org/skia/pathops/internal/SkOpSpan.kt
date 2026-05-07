/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkOpSpan.{h,cpp}` — the per-span data
 * model used by the path-ops contour assembly (D1.2). Contains three
 * tightly-coupled classes :
 *  - [SkOpPtT]      : (point, t) on a curve, doubly-linked across
 *                     coincident segments to form an "opp loop".
 *  - [SkOpSpanBase] : common base for spans (terminal + non-terminal),
 *                     anchored to a parent [SkOpSegment].
 *  - [SkOpSpan]     : non-terminal span (`t < 1`) with windSum /
 *                     oppSum / coincidence linkage.
 *
 * Phase D1.2.a — data model + simple methods. Methods that depend on
 * SkOpSegment / SkOpAngle / SkOpCoincidence algorithms (mergeMatches,
 * computeWindSum, sortableTop, release, insertCoincidence(SkOpSegment*),
 * setCoinStart, alignInner, …) are deferred to the matching D1.2.x
 * sub-slice that lands those classes.
 */
package org.skia.pathops.internal

import org.skia.math.SkPoint

/**
 * One `(point, t)` entry on a curve. Forms a singly-linked "opp
 * loop" via [fNext] across coincident segments — when two segments
 * cross at the same point, their respective `SkOpPtT` entries are
 * spliced into one another's loops via [addOpp] / [insert].
 *
 * Mirrors [`SkOpPtT`](https://github.com/google/skia/blob/main/src/pathops/SkOpSpan.h#L24).
 */
internal class SkOpPtT {
    var fT: Double = 0.0
    var fPt: SkPoint = SkPoint()
    /** Owning span. */
    var fSpan: SkOpSpanBase? = null
    /** Next entry in the opp loop (intersection on opp curve or alias on this one). */
    var fNext: SkOpPtT? = null
    var fDeleted: Boolean = false
    var fDuplicatePt: Boolean = false
    /** Mutable since referrer is otherwise always const in upstream. */
    var fCoincident: Boolean = false

    /** Mirrors `SkOpPtT::init`. */
    fun init(span: SkOpSpanBase, t: Double, pt: SkPoint, dup: Boolean) {
        fT = t
        fPt = pt
        fSpan = span
        fNext = this
        fDuplicatePt = dup
        fDeleted = false
        fCoincident = false
    }

    fun deleted(): Boolean = fDeleted
    fun duplicate(): Boolean = fDuplicatePt
    fun coincident(): Boolean = fCoincident
    fun next(): SkOpPtT? = fNext
    fun span(): SkOpSpanBase? = fSpan

    fun setSpan(span: SkOpSpanBase) { fSpan = span }
    fun setCoincident() {
        require(!fDeleted)
        fCoincident = true
    }
    fun setDeleted() { fDeleted = true }

    /** Mirrors `SkOpPtT::insert`. */
    fun insert(span: SkOpPtT) {
        require(span !== this)
        span.fNext = fNext
        fNext = span
    }

    /**
     * Splice [opp] into this loop just after `this`, and patch
     * [oppPrev]'s next to where this' next pointed. Mirrors `addOpp`.
     */
    fun addOpp(opp: SkOpPtT, oppPrev: SkOpPtT) {
        val oldNext = fNext
        require(this !== opp)
        fNext = opp
        require(oppPrev !== oldNext)
        oppPrev.fNext = oldNext
    }

    /**
     * Find the predecessor of [opp] in this' loop ; returns null if
     * walking the loop comes back to `this` first (meaning `this` is
     * already in [opp]'s loop). Mirrors `SkOpPtT::oppPrev`.
     */
    fun oppPrev(opp: SkOpPtT): SkOpPtT? {
        var oppPrev = opp.fNext ?: return null
        if (oppPrev === this) return null
        while (oppPrev.fNext !== opp) {
            oppPrev = oppPrev.fNext ?: return null
            if (oppPrev === this) return null
        }
        return oppPrev
    }

    /** Returns `this` if `fT < end.fT`, else [end]. Mirrors `SkOpPtT::starter`. */
    fun starter(end: SkOpPtT): SkOpPtT = if (fT < end.fT) this else end

    /**
     * Returns true iff `fT == 1` (the terminal entry's t-value).
     * Mirrors the contract `onEnd()` — but in upstream this is a
     * cpp-defined helper. We replicate the simple check.
     */
    fun onEnd(): Boolean = fT == 1.0 || fT == 0.0

    /** True if [other] is anywhere in this' opp loop. Mirrors `SkOpPtT::contains(SkOpPtT*)`. */
    fun contains(other: SkOpPtT): Boolean {
        if (other === this) return false // upstream returns false on self
        var test: SkOpPtT? = this
        do {
            if (test === other) return true
            test = test?.fNext
            if (test === this) return false
        } while (test != null)
        return false
    }

    companion object {
        /** Mirrors the kIsAlias / kIsDuplicate enum in upstream. */
        const val kIsAlias: Int = 1
        const val kIsDuplicate: Int = 1

        /**
         * Compute the t-overlap range `[sOut, eOut]` of two segments
         * `(s1, e1)` and `(s2, e2)`. Returns true iff a non-empty
         * overlap exists ; writes the start / end into [sOut] / [eOut]
         * (length-1 out arrays). Mirrors `SkOpPtT::Overlaps`.
         */
        fun Overlaps(
            s1: SkOpPtT, e1: SkOpPtT, s2: SkOpPtT, e2: SkOpPtT,
            sOut: Array<SkOpPtT?>, eOut: Array<SkOpPtT?>,
        ): Boolean {
            val start1 = if (s1.fT < e1.fT) s1 else e1
            val start2 = if (s2.fT < e2.fT) s2 else e2
            sOut[0] = when {
                between(s1.fT, start2.fT, e1.fT) -> start2
                between(s2.fT, start1.fT, e2.fT) -> start1
                else -> null
            }
            val end1 = if (s1.fT < e1.fT) e1 else s1
            val end2 = if (s2.fT < e2.fT) e2 else s2
            eOut[0] = when {
                between(s1.fT, end2.fT, e1.fT) -> end2
                between(s2.fT, end1.fT, e2.fT) -> end1
                else -> null
            }
            if (sOut[0] === eOut[0]) return false
            return sOut[0] != null && eOut[0] != null
        }
    }
}

/**
 * Common base for spans on a curve segment. Stored as a doubly-
 * linked list of (start, terminal-end) pairs in the parent
 * [SkOpSegment]. Each base owns a single [SkOpPtT] (the start of
 * its t-range), with the next span's `fPtT` defining its end.
 *
 * Mirrors [`SkOpSpanBase`](https://github.com/google/skia/blob/main/src/pathops/SkOpSpan.h#L178).
 */
internal open class SkOpSpanBase {
    enum class Collapsed { kNo, kYes, kError }

    val fPtT: SkOpPtT = SkOpPtT()
    var fSegment: SkOpSegment? = null
    /** Linked list head of coincident-end spans (may point to itself). */
    var fCoinEnd: SkOpSpanBase = this
    var fFromAngle: SkOpAngle? = null
    var fPrev: SkOpSpan? = null
    var fSpanAdds: Int = 0
    var fAligned: Boolean = true
    var fChased: Boolean = false

    /** Mirrors `SkOpSpanBase::initBase`. */
    fun initBase(parent: SkOpSegment, prev: SkOpSpan?, t: Double, pt: SkPoint) {
        fSegment = parent
        fPrev = prev
        fSpanAdds = 0
        fAligned = true
        fChased = false
        fCoinEnd = this
        fPtT.init(this, t, pt, false)
    }

    fun bumpSpanAdds() { ++fSpanAdds }
    fun chased(): Boolean = fChased
    fun setChased(c: Boolean) { fChased = c }
    fun coinEnd(): SkOpSpanBase = fCoinEnd
    fun deleted(): Boolean = fPtT.deleted()
    fun final(): Boolean = fPtT.fT == 1.0
    fun fromAngle(): SkOpAngle? = fFromAngle
    fun setFromAngle(angle: SkOpAngle) { fFromAngle = angle }
    fun pt(): SkPoint = fPtT.fPt
    fun ptT(): SkOpPtT = fPtT
    fun segment(): SkOpSegment? = fSegment
    fun setAligned() { fAligned = true }
    fun unaligned() { fAligned = false }
    fun aligned(): Boolean = fAligned
    fun prev(): SkOpSpan? = fPrev
    fun setPrev(p: SkOpSpan?) { fPrev = p }
    fun spanAddsCount(): Int = fSpanAdds
    fun simple(): Boolean = fPtT.next()?.next() === fPtT
    fun t(): Double = fPtT.fT

    /**
     * True if [coin] is anywhere in this' coincident-end loop.
     * Mirrors `SkOpSpanBase::containsCoinEnd(SkOpSpanBase*)`.
     */
    fun containsCoinEnd(coin: SkOpSpanBase): Boolean {
        require(this !== coin)
        var next: SkOpSpanBase = this
        while (true) {
            next = next.fCoinEnd
            if (next === this) return false
            if (next === coin) return true
        }
    }

    /**
     * Splice [coin] into this' coincident-end loop. Mirrors
     * `SkOpSpanBase::insertCoinEnd`.
     */
    fun insertCoinEnd(coin: SkOpSpanBase) {
        if (containsCoinEnd(coin)) {
            require(coin.containsCoinEnd(this))
            return
        }
        require(this !== coin)
        val coinNext = coin.fCoinEnd
        coin.fCoinEnd = fCoinEnd
        fCoinEnd = coinNext
    }

    /** Direction step from this to [end] : +1 if `t() < end.t()`, else -1. */
    fun step(end: SkOpSpanBase): Int = if (t() < end.t()) 1 else -1

    /** Returns this if `t() < end.t()`, else [end] (cast to SkOpSpan). */
    fun starter(end: SkOpSpanBase): SkOpSpan {
        val result = if (t() < end.t()) this else end
        return result.upCast()
    }

    /**
     * Variant of [starter] that may swap the out-param [endPtr] to
     * point at this if this is later in t-space. Mirrors the upstream
     * `starter(SkOpSpanBase**)` overload.
     */
    fun starter(endPtr: Array<SkOpSpanBase?>): SkOpSpan {
        require(endPtr.size >= 1)
        val end = endPtr[0]!!
        require(this.segment() === end.segment())
        val result: SkOpSpanBase
        if (t() < end.t()) {
            result = this
        } else {
            result = end
            endPtr[0] = this
        }
        return result.upCast()
    }

    /**
     * Down-cast to [SkOpSpan]. Asserts non-final ; use [upCastable]
     * for the nullable variant.
     */
    open fun upCast(): SkOpSpan {
        require(!final()) { "cannot upCast a final SkOpSpanBase" }
        return this as SkOpSpan
    }

    /** Nullable variant of [upCast] — returns null if final. */
    fun upCastable(): SkOpSpan? = if (final()) null else upCast()
}

/**
 * Non-terminal span (`t < 1`) with windSum / oppSum bookkeeping
 * and a coincidence-spans loop ([fCoincident]).
 *
 * Mirrors [`SkOpSpan`](https://github.com/google/skia/blob/main/src/pathops/SkOpSpan.h#L418).
 */
internal class SkOpSpan : SkOpSpanBase() {
    /** Linked list of spans coincident with this one (may point to itself). */
    var fCoincident: SkOpSpan = this
    var fToAngle: SkOpAngle? = null
    var fNext: SkOpSpanBase? = null
    var fWindSum: Int = SK_MinS32
    var fOppSum: Int = SK_MinS32
    var fWindValue: Int = 0
    var fOppValue: Int = 0
    var fTopTTry: Int = 0
    var fDone: Boolean = false
    var fAlreadyAdded: Boolean = false

    /** Mirrors `SkOpSpan::init`. */
    fun init(parent: SkOpSegment, prev: SkOpSpan?, t: Double, pt: SkPoint) {
        initBase(parent, prev, t, pt)
        fCoincident = this
        fToAngle = null
        fNext = null
        fWindSum = SK_MinS32
        fOppSum = SK_MinS32
        fWindValue = 0
        fOppValue = 0
        fTopTTry = 0
        fDone = false
        fAlreadyAdded = false
    }

    fun alreadyAdded(): Boolean = fAlreadyAdded
    fun markAdded() { fAlreadyAdded = true }
    fun done(): Boolean { require(!final()); return fDone }
    fun setDone(d: Boolean) { require(!final()); fDone = d }

    fun next(): SkOpSpanBase? { require(!final()); return fNext }
    fun setNext(n: SkOpSpanBase?) { require(!final()); fNext = n }

    fun toAngle(): SkOpAngle? { require(!final()); return fToAngle }
    fun setToAngle(a: SkOpAngle) { require(!final()); fToAngle = a }

    fun windSum(): Int { require(!final()); return fWindSum }
    fun windValue(): Int { require(!final()); return fWindValue }
    fun oppSum(): Int { require(!final()); return fOppSum }
    fun oppValue(): Int { require(!final()); return fOppValue }

    fun setWindValue(v: Int) {
        require(!final())
        require(v >= 0)
        require(fWindSum == SK_MinS32)
        require(v == 0 || !fDone)
        fWindValue = v
    }

    fun setOppValue(v: Int) {
        require(!final())
        require(fOppSum == SK_MinS32)
        require(v == 0 || !fDone)
        fOppValue = v
    }

    fun isCanceled(): Boolean { require(!final()); return fWindValue == 0 && fOppValue == 0 }
    fun isCoincident(): Boolean { require(!final()); return fCoincident !== this }

    /**
     * Reset the coincident loop pointer back to this. Returns false
     * if it was already self-referential (i.e. nothing to clear).
     * Mirrors `SkOpSpan::clearCoincident`.
     */
    fun clearCoincident(): Boolean {
        require(!final())
        if (fCoincident === this) return false
        fCoincident = this
        return true
    }

    /**
     * True if [coin] is anywhere in this' coincidence loop.
     * Mirrors `SkOpSpan::containsCoincidence(SkOpSpan*)`.
     */
    fun containsCoincidence(coin: SkOpSpan): Boolean {
        require(this !== coin)
        var next: SkOpSpan = this
        while (true) {
            next = next.fCoincident
            if (next === this) return false
            if (next === coin) return true
        }
    }

    /**
     * Splice [coin] into this' coincidence loop. Mirrors
     * `SkOpSpan::insertCoincidence(SkOpSpan*)`.
     */
    fun insertCoincidence(coin: SkOpSpan) {
        if (containsCoincidence(coin)) {
            require(coin.containsCoincidence(this))
            return
        }
        require(this !== coin)
        val coinNext = coin.fCoincident
        coin.fCoincident = fCoincident
        fCoincident = coinNext
    }

    override fun upCast(): SkOpSpan = this

    companion object {
        // Mirrors `include/private/base/SkPoint_impl.h`'s SK_MinS32 = -2147483648.
        internal const val SK_MinS32 = Int.MIN_VALUE
    }
}
