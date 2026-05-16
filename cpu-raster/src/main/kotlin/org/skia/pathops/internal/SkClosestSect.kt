/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `struct SkClosestRecord` and `struct SkClosestSect`
 * from `src/pathops/SkPathOpsTSect.cpp` (the local helpers used by
 * `SkTSect::BinarySearch` to gather the closest endpoint pairs after
 * the iterative span-splitting converges).
 *
 * Phase D1.1.e.2.c.4 — final-pass intersection extraction.
 */
package org.skia.pathops.internal

/**
 * One candidate endpoint match produced by [SkClosestSect.find].
 * The instance is reusable — [reset] zeroes its state and the parent
 * [SkClosestSect] reuses one slot at the tail of its list.
 */
internal class SkClosestRecord : Comparable<SkClosestRecord> {

    var fC1Span: SkTSpan? = null
    var fC2Span: SkTSpan? = null
    var fC1StartT: Double = 0.0
    var fC1EndT: Double = 0.0
    var fC2StartT: Double = 0.0
    var fC2EndT: Double = 0.0
    var fClosest: Double = Float.MAX_VALUE.toDouble()
    var fC1Index: Int = -1
    var fC2Index: Int = -1

    override fun compareTo(other: SkClosestRecord): Int = fClosest.compareTo(other.fClosest)

    /** Append `(r1t, r2t, pt)` for this record into [intersections]. */
    fun addIntersection(intersections: SkIntersections) {
        val r1t = if (fC1Index != 0) fC1Span!!.endT() else fC1Span!!.startT()
        val r2t = if (fC2Index != 0) fC2Span!!.endT() else fC2Span!!.startT()
        intersections.insert(r1t, r2t, fC1Span!!.part()[fC1Index])
    }

    /**
     * If `span1[c1Index]` is approximately equal to `span2[c2Index]`,
     * record the pair into this record (overwriting if closer).
     */
    fun findEnd(span1: SkTSpan, span2: SkTSpan, c1Index: Int, c2Index: Int) {
        val c1 = span1.part()
        val c2 = span2.part()
        if (!c1[c1Index].approximatelyEqual(c2[c2Index])) return
        val dist = c1[c1Index].distanceSquared(c2[c2Index])
        if (fClosest < dist) return
        fC1Span = span1
        fC2Span = span2
        fC1StartT = span1.startT()
        fC1EndT = span1.endT()
        fC2StartT = span2.startT()
        fC2EndT = span2.endT()
        fC1Index = c1Index
        fC2Index = c2Index
        fClosest = dist
    }

    /**
     * True if this record and [mate] reference the same span pair, or
     * if their t-ranges are adjacent on either side.
     */
    fun matesWith(mate: SkClosestRecord): Boolean {
        return fC1Span === mate.fC1Span
            || fC1Span!!.endT() == mate.fC1Span!!.startT()
            || fC1Span!!.startT() == mate.fC1Span!!.endT()
            || fC2Span === mate.fC2Span
            || fC2Span!!.endT() == mate.fC2Span!!.startT()
            || fC2Span!!.startT() == mate.fC2Span!!.endT()
    }

    fun merge(mate: SkClosestRecord) {
        fC1Span = mate.fC1Span
        fC2Span = mate.fC2Span
        fClosest = mate.fClosest
        fC1Index = mate.fC1Index
        fC2Index = mate.fC2Index
    }

    fun reset() {
        fClosest = Float.MAX_VALUE.toDouble()
        fC1Span = null
        fC2Span = null
        fC1Index = -1
        fC2Index = -1
    }

    fun update(mate: SkClosestRecord) {
        fC1StartT = minOf(fC1StartT, mate.fC1StartT)
        fC1EndT = maxOf(fC1EndT, mate.fC1EndT)
        fC2StartT = minOf(fC2StartT, mate.fC2StartT)
        fC2EndT = maxOf(fC2EndT, mate.fC2EndT)
    }
}

/**
 * Accumulator of [SkClosestRecord]s, one per converged endpoint
 * match. After all spans have been probed, [finish] sorts the
 * records by closest distance and writes them into the destination
 * [SkIntersections]. Mirrors Skia's `struct SkClosestSect`.
 */
internal class SkClosestSect {
    /**
     * The list always carries one extra "current" slot at the tail
     * (`fClosest[fUsed]`) — this matches the upstream `STArray`
     * "push then mutate then promote" pattern.
     */
    private val fClosest: MutableList<SkClosestRecord> = mutableListOf<SkClosestRecord>().apply {
        add(SkClosestRecord())
    }
    private var fUsed: Int = 0

    /**
     * Probe the 4 endpoint corner pairs of [span1] × [span2]. If a
     * record was filled, either merge it into an existing mate or
     * promote it to a new slot. Returns true if a new slot was
     * promoted (i.e. a fresh intersection was added).
     */
    fun find(span1: SkTSpan, span2: SkTSpan): Boolean {
        val record = fClosest[fUsed]
        record.findEnd(span1, span2, 0, 0)
        record.findEnd(span1, span2, 0, span2.part().pointLast())
        record.findEnd(span1, span2, span1.part().pointLast(), 0)
        record.findEnd(span1, span2, span1.part().pointLast(), span2.part().pointLast())
        if (record.fClosest == Float.MAX_VALUE.toDouble()) return false
        for (index in 0 until fUsed) {
            val test = fClosest[index]
            if (test.matesWith(record)) {
                if (test.fClosest > record.fClosest) test.merge(record)
                test.update(record)
                record.reset()
                return false
            }
        }
        ++fUsed
        fClosest.add(SkClosestRecord())
        return true
    }

    /** Sort the recorded matches by closest distance and append each to [intersections]. */
    fun finish(intersections: SkIntersections) {
        val ptrs: List<SkClosestRecord> = (0 until fUsed).map { fClosest[it] }.sorted()
        for (record in ptrs) record.addIntersection(intersections)
    }
}
