/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkDConicLineIntersection.cpp` —
 * `LineConicIntersections` + the `SkIntersections` surface for
 * conic-line intersections.
 *
 * Phase D1.1.d.3 — full port. The conic differs from a quadratic
 * only in that the middle control point carries a scalar weight ;
 * the rotated-into-line-space algorithm becomes a rational form
 * `(a + c - 2(b·w - xCept·w + xCept))·t² + (...)·t + (c - xCept) = 0`.
 * The result is still a quadratic in `t`, solved via
 * [SkDQuad.RootsValidT].
 */
package org.skia.pathops.internal

import kotlin.math.abs

internal class LineConicIntersections(
    private val fConic: SkDConic,
    private val fLine: SkDLine,
    private val fIntersections: SkIntersections,
) {

    private enum class PinTPoint { kPointUninitialized, kPointInitialized }

    private var fAllowNear: Boolean = true

    init {
        fIntersections.setMax(4) // partial coincidence + discrete intersection
    }

    fun allowNear(allow: Boolean) { fAllowNear = allow }

    // ─── Public entry points ────────────────────────────────────────

    /**
     * Intersect the parametric conic with the parametric line segment.
     * Mirrors `LineConicIntersections::intersect`. Returns the number
     * of intersection points (≤ [SkDConic.kMaxIntersections]).
     */
    fun intersect(): Int {
        addExactEndPoints()
        if (fAllowNear) addNearEndPoints()
        val rootVals = DoubleArray(2)
        val roots = intersectRay(rootVals)
        for (index in 0 until roots) {
            val conicT = rootVals[index]
            val lineT = findLineT(conicT)
            val pt = SkDPoint()
            val c = doubleArrayOf(conicT)
            val l = doubleArrayOf(lineT)
            if (pinTs(c, l, pt, PinTPoint.kPointUninitialized) && uniqueAnswer(c[0], pt)) {
                fIntersections.insert(c[0], l[0], pt)
            }
        }
        checkCoincident()
        return fIntersections.used()
    }

    /**
     * Compute the raw t-values along the *conic* where it crosses
     * the *infinite line*. Mirrors `LineConicIntersections::intersectRay`.
     */
    fun intersectRay(roots: DoubleArray): Int {
        val adj = fLine[1].x - fLine[0].x
        val opp = fLine[1].y - fLine[0].y
        val r = DoubleArray(3)
        for (n in 0 until 3) {
            r[n] = (fConic[n].y - fLine[0].y) * adj - (fConic[n].x - fLine[0].x) * opp
        }
        return validT(r, 0.0, roots)
    }

    /**
     * Common rational-form solver. Mirrors `LineConicIntersections::validT`.
     *
     * Given the per-control distances `r[0..2]` (perpendicular to the
     * line for `intersectRay`, or per-axis values for horizontal /
     * vertical), build the quadratic coefficients and call
     * [SkDQuad.RootsValidT].
     */
    private fun validT(r: DoubleArray, axisIntercept: Double, roots: DoubleArray): Int {
        val w = fConic.weight.toDouble()
        var A = r[2]
        var B = r[1] * w - axisIntercept * w + axisIntercept
        var C = r[0]
        A += C - 2 * B
        B -= C
        C -= axisIntercept
        return SkDQuad.RootsValidT(A, 2 * B, C, roots)
    }

    /** Mirrors `LineConicIntersections::horizontalIntersect(y, double[2])`. */
    fun horizontalIntersect(axisIntercept: Double, roots: DoubleArray): Int {
        val conicVals = doubleArrayOf(fConic[0].y, fConic[1].y, fConic[2].y)
        return validT(conicVals, axisIntercept, roots)
    }

    fun horizontalIntersect(axisIntercept: Double, left: Double, right: Double, flipped: Boolean): Int {
        addExactHorizontalEndPoints(left, right, axisIntercept)
        if (fAllowNear) addNearHorizontalEndPoints(left, right, axisIntercept)
        val rootVals = DoubleArray(2)
        val count = horizontalIntersect(axisIntercept, rootVals)
        for (index in 0 until count) {
            val conicT = rootVals[index]
            val pt = fConic.ptAtT(conicT)
            val lineT = (pt.x - left) / (right - left)
            val c = doubleArrayOf(conicT); val l = doubleArrayOf(lineT)
            if (pinTs(c, l, pt, PinTPoint.kPointInitialized) && uniqueAnswer(c[0], pt)) {
                fIntersections.insert(c[0], l[0], pt)
            }
        }
        if (flipped) fIntersections.flip()
        checkCoincident()
        return fIntersections.used()
    }

    /** Mirrors `LineConicIntersections::verticalIntersect(x, double[2])`. */
    fun verticalIntersect(axisIntercept: Double, roots: DoubleArray): Int {
        val conicVals = doubleArrayOf(fConic[0].x, fConic[1].x, fConic[2].x)
        return validT(conicVals, axisIntercept, roots)
    }

    fun verticalIntersect(axisIntercept: Double, top: Double, bottom: Double, flipped: Boolean): Int {
        addExactVerticalEndPoints(top, bottom, axisIntercept)
        if (fAllowNear) addNearVerticalEndPoints(top, bottom, axisIntercept)
        val rootVals = DoubleArray(2)
        val count = verticalIntersect(axisIntercept, rootVals)
        for (index in 0 until count) {
            val conicT = rootVals[index]
            val pt = fConic.ptAtT(conicT)
            val lineT = (pt.y - top) / (bottom - top)
            val c = doubleArrayOf(conicT); val l = doubleArrayOf(lineT)
            if (pinTs(c, l, pt, PinTPoint.kPointInitialized) && uniqueAnswer(c[0], pt)) {
                fIntersections.insert(c[0], l[0], pt)
            }
        }
        if (flipped) fIntersections.flip()
        checkCoincident()
        return fIntersections.used()
    }

    // ─── Endpoint helpers ───────────────────────────────────────────

    private fun addExactEndPoints() {
        var cIndex = 0
        while (cIndex < SkDConic.kPointCount) {
            val lineT = fLine.exactPoint(fConic[cIndex])
            if (lineT >= 0) {
                val conicT = (cIndex shr 1).toDouble()
                fIntersections.insert(conicT, lineT, fConic[cIndex])
            }
            cIndex += SkDConic.kPointLast
        }
    }

    private fun addNearEndPoints() {
        var cIndex = 0
        while (cIndex < SkDConic.kPointCount) {
            val conicT = (cIndex shr 1).toDouble()
            if (!fIntersections.hasT(conicT)) {
                val lineT = fLine.nearPoint(fConic[cIndex], null)
                if (lineT >= 0) fIntersections.insert(conicT, lineT, fConic[cIndex])
            }
            cIndex += SkDConic.kPointLast
        }
        addLineNearEndPoints()
    }

    /**
     * Find any line endpoint that lies near the conic curve and
     * insert it. Mirrors `addLineNearEndPoints` ; uses [SkDConic.nearPoint].
     */
    private fun addLineNearEndPoints() {
        for (lIndex in 0..1) {
            val lineT = lIndex.toDouble()
            if (fIntersections.hasOppT(lineT)) continue
            val conicT = fConic.nearPoint(fLine[lIndex], fLine[1 - lIndex])
            if (conicT < 0) continue
            fIntersections.insert(conicT, lineT, fLine[lIndex])
        }
    }

    private fun addExactHorizontalEndPoints(left: Double, right: Double, y: Double) {
        var cIndex = 0
        while (cIndex < SkDConic.kPointCount) {
            val lineT = SkDLine.ExactPointH(fConic[cIndex], left, right, y)
            if (lineT >= 0) {
                val conicT = (cIndex shr 1).toDouble()
                fIntersections.insert(conicT, lineT, fConic[cIndex])
            }
            cIndex += SkDConic.kPointLast
        }
    }

    private fun addNearHorizontalEndPoints(left: Double, right: Double, y: Double) {
        var cIndex = 0
        while (cIndex < SkDConic.kPointCount) {
            val conicT = (cIndex shr 1).toDouble()
            if (!fIntersections.hasT(conicT)) {
                val lineT = SkDLine.NearPointH(fConic[cIndex], left, right, y)
                if (lineT >= 0) fIntersections.insert(conicT, lineT, fConic[cIndex])
            }
            cIndex += SkDConic.kPointLast
        }
        addLineNearEndPoints()
    }

    private fun addExactVerticalEndPoints(top: Double, bottom: Double, x: Double) {
        var cIndex = 0
        while (cIndex < SkDConic.kPointCount) {
            val lineT = SkDLine.ExactPointV(fConic[cIndex], top, bottom, x)
            if (lineT >= 0) {
                val conicT = (cIndex shr 1).toDouble()
                fIntersections.insert(conicT, lineT, fConic[cIndex])
            }
            cIndex += SkDConic.kPointLast
        }
    }

    private fun addNearVerticalEndPoints(top: Double, bottom: Double, x: Double) {
        var cIndex = 0
        while (cIndex < SkDConic.kPointCount) {
            val conicT = (cIndex shr 1).toDouble()
            if (!fIntersections.hasT(conicT)) {
                val lineT = SkDLine.NearPointV(fConic[cIndex], top, bottom, x)
                if (lineT >= 0) fIntersections.insert(conicT, lineT, fConic[cIndex])
            }
            cIndex += SkDConic.kPointLast
        }
        addLineNearEndPoints()
    }

    // ─── Coincidence + uniqueness post-processing ──────────────────

    private fun checkCoincident() {
        var last = fIntersections.used() - 1
        var index = 0
        while (index < last) {
            val conicMidT = (fIntersections.t(0, index) + fIntersections.t(0, index + 1)) / 2
            val conicMidPt = fConic.ptAtT(conicMidT)
            val t = fLine.nearPoint(conicMidPt, null)
            if (t < 0) {
                ++index
                continue
            }
            if (fIntersections.isCoincident(index)) {
                fIntersections.removeOne(index); --last
            } else if (fIntersections.isCoincident(index + 1)) {
                fIntersections.removeOne(index + 1); --last
            } else {
                fIntersections.setCoincident(index); ++index
            }
            fIntersections.setCoincident(index)
        }
    }

    private fun uniqueAnswer(conicT: Double, pt: SkDPoint): Boolean {
        for (inner in 0 until fIntersections.used()) {
            if (fIntersections.pt(inner) != pt) continue
            val existingConicT = fIntersections.t(0, inner)
            if (conicT == existingConicT) return false
            val conicMidT = (existingConicT + conicT) / 2
            val conicMidPt = fConic.ptAtT(conicMidT)
            if (conicMidPt.approximatelyEqual(pt)) return false
        }
        return true
    }

    // ─── Pin / project helpers ──────────────────────────────────────

    private fun findLineT(t: Double): Double {
        val xy = fConic.ptAtT(t)
        val dx = fLine[1].x - fLine[0].x
        val dy = fLine[1].y - fLine[0].y
        return if (abs(dx) > abs(dy)) (xy.x - fLine[0].x) / dx
        else (xy.y - fLine[0].y) / dy
    }

    /**
     * Conic-line variant of pinTs — same shape as quad-line (uses
     * [SkDPoint.ApproximatelyEqual] on endpoints, no `roughlyEqual`
     * line-vs-curve check). Mirrors `LineConicIntersections::pinTs`.
     */
    private fun pinTs(
        conicT: DoubleArray,
        lineT: DoubleArray,
        pt: SkDPoint,
        ptSet: PinTPoint,
    ): Boolean {
        if (!approximately_one_or_less_double(lineT[0])) return false
        if (!approximately_zero_or_more_double(lineT[0])) return false
        val qT = SkPinT(conicT[0]); conicT[0] = qT
        val lT = SkPinT(lineT[0]); lineT[0] = lT
        val computed = if (lT == 0.0 || lT == 1.0
            || (ptSet == PinTPoint.kPointUninitialized && qT != 0.0 && qT != 1.0)
        ) fLine.ptAtT(lT)
        else if (ptSet == PinTPoint.kPointUninitialized) fConic.ptAtT(qT)
        else pt.copy()
        pt.x = computed.x; pt.y = computed.y
        val gridPt = pt.asSkPoint()
        if (SkDPoint.ApproximatelyEqual(gridPt, fLine[0].asSkPoint())) {
            pt.x = fLine[0].x; pt.y = fLine[0].y; lineT[0] = 0.0
        } else if (SkDPoint.ApproximatelyEqual(gridPt, fLine[1].asSkPoint())) {
            pt.x = fLine[1].x; pt.y = fLine[1].y; lineT[0] = 1.0
        }
        if (fIntersections.used() > 0 && approximately_equal(fIntersections.t(1, 0), lineT[0])) {
            return false
        }
        if (gridPt == fConic[0].asSkPoint()) {
            pt.x = fConic[0].x; pt.y = fConic[0].y; conicT[0] = 0.0
        } else if (gridPt == fConic[2].asSkPoint()) {
            pt.x = fConic[2].x; pt.y = fConic[2].y; conicT[0] = 1.0
        }
        return true
    }
}
