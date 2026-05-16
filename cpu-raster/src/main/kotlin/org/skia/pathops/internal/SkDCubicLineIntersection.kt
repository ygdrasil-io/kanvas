/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkDCubicLineIntersection.cpp` —
 * `LineCubicIntersections` + the `SkIntersections` surface for
 * cubic-line intersections.
 *
 * Phase D1.1.d.2 — full port. The algorithm rotates the cubic into
 * line space (line aligned to x-axis), then solves the resulting
 * `A·t³ + B·t² + C·t + D = 0` via [SkDCubic.RootsValidT]. When the
 * direct polynomial roots don't actually map to the line (numeric
 * robustness failure), falls back to [SkDCubic.searchRoots] which
 * binary-searches each `extrema + inflections` interval.
 *
 * Algebraic reference (per upstream comment block) :
 *   Solve in Mathematica :
 *     Resultant[a*(1-t)³ + 3*b*(1-t)²*t + 3*c*(1-t)*t² + d*t³ - x,
 *               e*(1-t)³ + 3*f*(1-t)²*t + 3*g*(1-t)*t² + h*t³ - i*x - j, x]
 *   Yields A·t³ + B·t² + C·t + D with the rotated coefficients
 *   defined below.
 */
package org.skia.pathops.internal


import org.skia.math.SkDLine
import org.skia.math.SkDPoint
import org.skia.math.SkPinT
import org.skia.math.approximately_equal
import org.skia.math.approximately_one_or_less
import org.skia.math.approximately_zero
import org.skia.math.approximately_zero_or_more
import kotlin.math.abs

internal class LineCubicIntersections(
    private val fCubic: SkDCubic,
    private val fLine: SkDLine,
    private val fIntersections: SkIntersections,
) {

    private enum class PinTPoint { kPointUninitialized, kPointInitialized }

    private var fAllowNear: Boolean = true

    init {
        fIntersections.setMax(4)
    }

    fun allowNear(allow: Boolean) { fAllowNear = allow }

    // ─── Public entry points ────────────────────────────────────────

    /**
     * Intersect the parametric cubic with the parametric line segment.
     * Mirrors `LineCubicIntersections::intersect`. Returns the number
     * of intersection points found (≤ [SkDCubic.kMaxIntersections]).
     */
    fun intersect(): Int {
        addExactEndPoints()
        if (fAllowNear) addNearEndPoints()
        val rootVals = DoubleArray(3)
        val roots = intersectRay(rootVals)
        for (index in 0 until roots) {
            val cubicT = rootVals[index]
            val lineT = findLineT(cubicT)
            val pt = SkDPoint()
            val c = doubleArrayOf(cubicT)
            val l = doubleArrayOf(lineT)
            if (pinTs(c, l, pt, PinTPoint.kPointUninitialized) && uniqueAnswer(c[0], pt)) {
                fIntersections.insert(c[0], l[0], pt)
            }
        }
        checkCoincident()
        return fIntersections.used()
    }

    /**
     * Compute the raw t-values along the *cubic* where it crosses
     * the *infinite line* (no segment-bounds check, no endpoint snap).
     * Mirrors `LineCubicIntersections::intersectRay`.
     */
    fun intersectRay(roots: DoubleArray): Int {
        val adj = fLine[1].x - fLine[0].x
        val opp = fLine[1].y - fLine[0].y
        // c[n].x = signed perpendicular distance of fCubic[n] to the line.
        val cX = DoubleArray(4)
        for (n in 0 until 4) {
            cX[n] = (fCubic[n].y - fLine[0].y) * adj - (fCubic[n].x - fLine[0].x) * opp
        }
        val A = DoubleArray(1); val B = DoubleArray(1); val C = DoubleArray(1); val D = DoubleArray(1)
        SkDCubic.Coefficients(cX, A, B, C, D)
        var count = SkDCubic.RootsValidT(A[0], B[0], C[0], D[0], roots)
        // Numeric robustness fallback : if any root doesn't actually
        // place the curve on the line (cX(t) != 0), fall back to
        // searchRoots over the rotated x-axis.
        val rotated = SkDCubic(arrayOf(
            SkDPoint(cX[0], 0.0), SkDPoint(cX[1], 0.0),
            SkDPoint(cX[2], 0.0), SkDPoint(cX[3], 0.0),
        ))
        for (index in 0 until count) {
            val calcPt = rotated.ptAtT(roots[index])
            if (!approximately_zero(calcPt.x)) {
                // Re-rotate using the y-axis projection (parallel-axis math).
                val cY = DoubleArray(4)
                for (n in 0 until 4) {
                    cY[n] = (fCubic[n].y - fLine[0].y) * opp + (fCubic[n].x - fLine[0].x) * adj
                }
                val extremeTs = DoubleArray(6)
                val extrema = SkDCubic.FindExtrema(cY, extremeTs)
                val rotY = SkDCubic(arrayOf(
                    SkDPoint(cY[0], 0.0), SkDPoint(cY[1], 0.0),
                    SkDPoint(cY[2], 0.0), SkDPoint(cY[3], 0.0),
                ))
                count = rotY.searchRoots(extremeTs, extrema, 0.0, SkDCubic.SearchAxis.kXAxis, roots)
                break
            }
        }
        return count
    }

    /** Mirrors the static `LineCubicIntersections::HorizontalIntersect`. */
    fun horizontalIntersect(axisIntercept: Double, roots: DoubleArray): Int {
        return fCubic.horizontalIntersect(axisIntercept, roots)
    }

    fun horizontalIntersect(axisIntercept: Double, left: Double, right: Double, flipped: Boolean): Int {
        addExactHorizontalEndPoints(left, right, axisIntercept)
        if (fAllowNear) addNearHorizontalEndPoints(left, right, axisIntercept)
        val rootVals = DoubleArray(3)
        val count = horizontalIntersect(axisIntercept, rootVals)
        for (index in 0 until count) {
            val cubicT = rootVals[index]
            val pt = SkDPoint(fCubic.ptAtT(cubicT).x, axisIntercept)
            val lineT = (pt.x - left) / (right - left)
            val c = doubleArrayOf(cubicT); val l = doubleArrayOf(lineT)
            if (pinTs(c, l, pt, PinTPoint.kPointInitialized) && uniqueAnswer(c[0], pt)) {
                fIntersections.insert(c[0], l[0], pt)
            }
        }
        if (flipped) fIntersections.flip()
        checkCoincident()
        return fIntersections.used()
    }

    /** Mirrors the static `LineCubicIntersections::VerticalIntersect`. */
    fun verticalIntersect(axisIntercept: Double, roots: DoubleArray): Int {
        return fCubic.verticalIntersect(axisIntercept, roots)
    }

    fun verticalIntersect(axisIntercept: Double, top: Double, bottom: Double, flipped: Boolean): Int {
        addExactVerticalEndPoints(top, bottom, axisIntercept)
        if (fAllowNear) addNearVerticalEndPoints(top, bottom, axisIntercept)
        val rootVals = DoubleArray(3)
        val count = verticalIntersect(axisIntercept, rootVals)
        for (index in 0 until count) {
            val cubicT = rootVals[index]
            val pt = SkDPoint(axisIntercept, fCubic.ptAtT(cubicT).y)
            val lineT = (pt.y - top) / (bottom - top)
            val c = doubleArrayOf(cubicT); val l = doubleArrayOf(lineT)
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
        while (cIndex < 4) {
            val lineT = fLine.exactPoint(fCubic[cIndex])
            if (lineT >= 0) {
                val cubicT = (cIndex shr 1).toDouble()
                fIntersections.insert(cubicT, lineT, fCubic[cIndex])
            }
            cIndex += 3
        }
    }

    private fun addNearEndPoints() {
        var cIndex = 0
        while (cIndex < 4) {
            val cubicT = (cIndex shr 1).toDouble()
            if (!fIntersections.hasT(cubicT)) {
                val lineT = fLine.nearPoint(fCubic[cIndex], null)
                if (lineT >= 0) fIntersections.insert(cubicT, lineT, fCubic[cIndex])
            }
            cIndex += 3
        }
        addLineNearEndPoints()
    }

    /**
     * Find any line endpoint that lies near the cubic curve and
     * insert it. Mirrors `addLineNearEndPoints` ; uses [SkDCubic.nearPoint].
     */
    private fun addLineNearEndPoints() {
        for (lIndex in 0..1) {
            val lineT = lIndex.toDouble()
            if (fIntersections.hasOppT(lineT)) continue
            val cubicT = fCubic.nearPoint(fLine[lIndex], fLine[1 - lIndex])
            if (cubicT < 0) continue
            fIntersections.insert(cubicT, lineT, fLine[lIndex])
        }
    }

    private fun addExactHorizontalEndPoints(left: Double, right: Double, y: Double) {
        var cIndex = 0
        while (cIndex < 4) {
            val lineT = SkDLine.ExactPointH(fCubic[cIndex], left, right, y)
            if (lineT >= 0) {
                val cubicT = (cIndex shr 1).toDouble()
                fIntersections.insert(cubicT, lineT, fCubic[cIndex])
            }
            cIndex += 3
        }
    }

    private fun addNearHorizontalEndPoints(left: Double, right: Double, y: Double) {
        var cIndex = 0
        while (cIndex < 4) {
            val cubicT = (cIndex shr 1).toDouble()
            if (!fIntersections.hasT(cubicT)) {
                val lineT = SkDLine.NearPointH(fCubic[cIndex], left, right, y)
                if (lineT >= 0) fIntersections.insert(cubicT, lineT, fCubic[cIndex])
            }
            cIndex += 3
        }
        addLineNearEndPoints()
    }

    private fun addExactVerticalEndPoints(top: Double, bottom: Double, x: Double) {
        var cIndex = 0
        while (cIndex < 4) {
            val lineT = SkDLine.ExactPointV(fCubic[cIndex], top, bottom, x)
            if (lineT >= 0) {
                val cubicT = (cIndex shr 1).toDouble()
                fIntersections.insert(cubicT, lineT, fCubic[cIndex])
            }
            cIndex += 3
        }
    }

    private fun addNearVerticalEndPoints(top: Double, bottom: Double, x: Double) {
        var cIndex = 0
        while (cIndex < 4) {
            val cubicT = (cIndex shr 1).toDouble()
            if (!fIntersections.hasT(cubicT)) {
                val lineT = SkDLine.NearPointV(fCubic[cIndex], top, bottom, x)
                if (lineT >= 0) fIntersections.insert(cubicT, lineT, fCubic[cIndex])
            }
            cIndex += 3
        }
        addLineNearEndPoints()
    }

    // ─── Coincidence + uniqueness post-processing ──────────────────

    private fun checkCoincident() {
        var last = fIntersections.used() - 1
        var index = 0
        while (index < last) {
            val cubicMidT = (fIntersections.t(0, index) + fIntersections.t(0, index + 1)) / 2
            val cubicMidPt = fCubic.ptAtT(cubicMidT)
            val t = fLine.nearPoint(cubicMidPt, null)
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

    private fun uniqueAnswer(cubicT: Double, pt: SkDPoint): Boolean {
        for (inner in 0 until fIntersections.used()) {
            if (fIntersections.pt(inner) != pt) continue
            val existingCubicT = fIntersections.t(0, inner)
            if (cubicT == existingCubicT) return false
            val cubicMidT = (existingCubicT + cubicT) / 2
            val cubicMidPt = fCubic.ptAtT(cubicMidT)
            if (cubicMidPt.approximatelyEqual(pt)) return false
        }
        return true
    }

    // ─── Pin / project helpers ──────────────────────────────────────

    private fun findLineT(t: Double): Double {
        val xy = fCubic.ptAtT(t)
        val dx = fLine[1].x - fLine[0].x
        val dy = fLine[1].y - fLine[0].y
        return if (abs(dx) > abs(dy)) (xy.x - fLine[0].x) / dx
        else (xy.y - fLine[0].y) / dy
    }

    /**
     * Cubic-line variant of pinTs : differs from quad-line in that
     * it requires `lPt.roughlyEqual(cPt)` (the projected line point
     * must roughly match the cubic at cubicT) before accepting.
     * Mirrors `LineCubicIntersections::pinTs`.
     */
    private fun pinTs(
        cubicT: DoubleArray,
        lineT: DoubleArray,
        pt: SkDPoint,
        ptSet: PinTPoint,
    ): Boolean {
        if (!approximately_one_or_less(lineT[0])) return false
        if (!approximately_zero_or_more(lineT[0])) return false
        val cT = SkPinT(cubicT[0]); cubicT[0] = cT
        val lT = SkPinT(lineT[0]); lineT[0] = lT
        val lPt = fLine.ptAtT(lT)
        val cPt = fCubic.ptAtT(cT)
        if (!lPt.roughlyEqual(cPt)) return false
        // FIXME (upstream) : when roughly equal but not approximately
        // equal, ideally do a binary search to find more precise t.
        val computed = if (lT == 0.0 || lT == 1.0
            || (ptSet == PinTPoint.kPointUninitialized && cT != 0.0 && cT != 1.0)
        ) lPt
        else if (ptSet == PinTPoint.kPointUninitialized) cPt
        else pt.copy()
        pt.x = computed.x; pt.y = computed.y
        val gridPt = pt.asSkPoint()
        if (gridPt == fLine[0].asSkPoint()) lineT[0] = 0.0
        else if (gridPt == fLine[1].asSkPoint()) lineT[0] = 1.0
        if (gridPt == fCubic[0].asSkPoint() && approximately_equal(cubicT[0], 0.0)) cubicT[0] = 0.0
        else if (gridPt == fCubic[3].asSkPoint() && approximately_equal(cubicT[0], 1.0)) cubicT[0] = 1.0
        return true
    }
}
