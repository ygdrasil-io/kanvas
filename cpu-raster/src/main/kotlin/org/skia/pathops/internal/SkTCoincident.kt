/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `class SkTCoincident` from `src/pathops/SkPathOpsTSect.h`.
 *
 * Phase D1.1.e.2.b — coincidence-tracking helper used by [SkTSpan]
 * to record where a perpendicular ray from one curve hits the
 * opposing curve. If the perpendicular hit is approximately equal
 * to the source point, the spans are marked coincident at that t.
 */
package org.skia.pathops.internal


import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
internal class SkTCoincident {
    private var fPerpPt: SkDPoint = SkDPoint(Double.NaN, Double.NaN)
    private var fPerpT: Double = -1.0
    private var fMatch: Boolean = false

    init { init() }

    fun init() {
        fPerpT = -1.0
        fMatch = false
        fPerpPt = SkDPoint(Double.NaN, Double.NaN)
    }

    /** Mark this coincidence as a true match (both curves share the perp pt). */
    fun markCoincident() {
        if (!fMatch) fPerpT = -1.0
        fMatch = true
    }

    fun isMatch(): Boolean = fMatch
    fun perpT(): Double = fPerpT
    fun perpPt(): SkDPoint = fPerpPt

    /**
     * Bulk copy of state from [other]. Mirrors the upstream
     * `work->fCoinStart = prior->fCoinEnd;` value-assignment idiom
     * used by [SkTSect.computePerpendiculars] / [SkTSect.mergeCoincidence].
     */
    fun copyFrom(other: SkTCoincident) {
        fPerpPt = other.fPerpPt
        fPerpT = other.fPerpT
        fMatch = other.fMatch
    }

    /**
     * Drop a perpendicular at parameter [t] on curve [c1] (point [cPt])
     * and find where it crosses [c2]. Stores the closest hit's t and
     * point, plus a `fMatch` flag that's true iff [cPt] is approximately
     * equal to the perp hit. Mirrors `SkTCoincident::setPerp`.
     */
    fun setPerp(c1: SkTCurve, t: Double, cPt: SkDPoint, c2: SkTCurve) {
        val dxdy = c1.dxdyAtT(t)
        val perp = SkDLine(arrayOf(cPt, SkDPoint(cPt.x + dxdy.y, cPt.y - dxdy.x)))
        val ix = SkIntersections()
        val used = c2.intersectRay(ix, perp)
        // Only keep the closest. A `used == 3` is suspect and treated
        // as "no match" (matches upstream).
        if (used == 0 || used == 3) { init(); return }
        fPerpT = ix.t(0, 0)
        fPerpPt = ix.pt(0)
        require(used <= 2)
        if (used == 2) {
            val distSq = (fPerpPt - cPt).lengthSquared()
            val dist2Sq = (ix.pt(1) - cPt).lengthSquared()
            if (dist2Sq < distSq) {
                fPerpT = ix.t(0, 1)
                fPerpPt = ix.pt(1)
            }
        }
        fMatch = cPt.approximatelyEqual(fPerpPt)
    }
}
