/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkDQuadLineIntersection.cpp` —
 * `LineQuadraticIntersections` + the public `SkIntersections`
 * surface for quad-line intersections.
 *
 * Phase D1.1.d.1 — full port. The algorithm rotates the quadratic
 * into line space (so the line is horizontal), then solves the
 * resulting `A·t² + 2B·t + C = 0` for valid `t` values via
 * [SkDQuad.RootsValidT]. Endpoint-snapping (`addExactEndPoints` /
 * `addNearEndPoints`), `pinTs` clamping, `uniqueAnswer` dedup,
 * `checkCoincident` post-processing all ported.
 *
 * Algebraic reference (per upstream comment block) :
 *   Solve in Mathematica :
 *     Resultant[a*(1-t)² + 2*b*(1-t)*t + c*t² - x,
 *               d*(1-t)² + 2*e*(1-t)*t + f*t² - g*x - h, x]
 *   Yields A·t² + 2B·t + C with the coefficients defined below.
 */
package org.skia.pathops.internal

import kotlin.math.abs

/**
 * Adapter class encapsulating the line-vs-quadratic intersection
 * algorithm. Created once per intersection request ; results are
 * accumulated into the supplied [SkIntersections].
 */
internal class LineQuadraticIntersections(
    private val fQuad: SkDQuad,
    private val fLine: SkDLine,
    private val fIntersections: SkIntersections,
) {

    private enum class PinTPoint { kPointUninitialized, kPointInitialized }

    private var fAllowNear: Boolean = true

    init {
        fIntersections.setMax(5) // allow short partial coincidence + discrete intersection
    }

    fun allowNear(allow: Boolean) { fAllowNear = allow }

    // ─── Public entry points ────────────────────────────────────────

    /**
     * Intersect the parametric quad with the parametric line segment.
     * Mirrors `LineQuadraticIntersections::intersect`. Returns the
     * number of intersection points found (≤ [SkDQuad.kMaxIntersections]).
     */
    fun intersect(): Int {
        addExactEndPoints()
        if (fAllowNear) addNearEndPoints()
        val rootVals = DoubleArray(2)
        val roots = intersectRay(rootVals)
        for (index in 0 until roots) {
            val quadT = rootVals[index]
            val lineT = findLineT(quadT)
            val pt = SkDPoint()
            // Wrap in 1-element arrays for in-out semantics (Kotlin
            // analogue of upstream `double*`).
            val q = doubleArrayOf(quadT)
            val l = doubleArrayOf(lineT)
            if (pinTs(q, l, pt, PinTPoint.kPointUninitialized) && uniqueAnswer(q[0], pt)) {
                fIntersections.insert(q[0], l[0], pt)
            }
        }
        checkCoincident()
        return fIntersections.used()
    }

    /**
     * Compute the raw t-values along the *quadratic* where it crosses
     * the *infinite line* (no segment-bounds check, no endpoint snap).
     * Mirrors `LineQuadraticIntersections::intersectRay`. Roots are
     * written to [roots] (length ≥ 2) ; returns the count.
     */
    fun intersectRay(roots: DoubleArray): Int {
        // Rotate the quadratic into line space (line aligns to x-axis).
        // Skip the hypotenuse normalization — we only care about t-roots.
        val adj = fLine[1].x - fLine[0].x
        val opp = fLine[1].y - fLine[0].y
        val r = DoubleArray(3)
        for (n in 0 until 3) {
            r[n] = (fQuad[n].y - fLine[0].y) * adj - (fQuad[n].x - fLine[0].x) * opp
        }
        var A = r[2]
        var B = r[1]
        val C = r[0]
        A += C - 2 * B
        B -= C
        return SkDQuad.RootsValidT(A, 2 * B, C, roots)
    }

    /** Mirrors `horizontalIntersect(double y, double[2] roots)` static-ish overload. */
    fun horizontalIntersect(axisIntercept: Double, roots: DoubleArray): Int {
        var D = fQuad[2].y
        var E = fQuad[1].y
        var F = fQuad[0].y
        D += F - 2 * E
        E -= F
        F -= axisIntercept
        return SkDQuad.RootsValidT(D, 2 * E, F, roots)
    }

    fun horizontalIntersect(axisIntercept: Double, left: Double, right: Double, flipped: Boolean): Int {
        addExactHorizontalEndPoints(left, right, axisIntercept)
        if (fAllowNear) addNearHorizontalEndPoints(left, right, axisIntercept)
        val rootVals = DoubleArray(2)
        val roots = horizontalIntersect(axisIntercept, rootVals)
        for (index in 0 until roots) {
            val quadT = rootVals[index]
            val pt = fQuad.ptAtT(quadT)
            val lineT = (pt.x - left) / (right - left)
            val q = doubleArrayOf(quadT); val l = doubleArrayOf(lineT)
            if (pinTs(q, l, pt, PinTPoint.kPointInitialized) && uniqueAnswer(q[0], pt)) {
                fIntersections.insert(q[0], l[0], pt)
            }
        }
        if (flipped) fIntersections.flip()
        checkCoincident()
        return fIntersections.used()
    }

    fun verticalIntersect(axisIntercept: Double, roots: DoubleArray): Int {
        var D = fQuad[2].x
        var E = fQuad[1].x
        var F = fQuad[0].x
        D += F - 2 * E
        E -= F
        F -= axisIntercept
        return SkDQuad.RootsValidT(D, 2 * E, F, roots)
    }

    fun verticalIntersect(axisIntercept: Double, top: Double, bottom: Double, flipped: Boolean): Int {
        addExactVerticalEndPoints(top, bottom, axisIntercept)
        if (fAllowNear) addNearVerticalEndPoints(top, bottom, axisIntercept)
        val rootVals = DoubleArray(2)
        val roots = verticalIntersect(axisIntercept, rootVals)
        for (index in 0 until roots) {
            val quadT = rootVals[index]
            val pt = fQuad.ptAtT(quadT)
            val lineT = (pt.y - top) / (bottom - top)
            val q = doubleArrayOf(quadT); val l = doubleArrayOf(lineT)
            if (pinTs(q, l, pt, PinTPoint.kPointInitialized) && uniqueAnswer(q[0], pt)) {
                fIntersections.insert(q[0], l[0], pt)
            }
        }
        if (flipped) fIntersections.flip()
        checkCoincident()
        return fIntersections.used()
    }

    // ─── Endpoint helpers ───────────────────────────────────────────

    private fun addExactEndPoints() {
        var qIndex = 0
        while (qIndex < 3) {
            val lineT = fLine.exactPoint(fQuad[qIndex])
            if (lineT >= 0) {
                val quadT = (qIndex shr 1).toDouble()
                fIntersections.insert(quadT, lineT, fQuad[qIndex])
            }
            qIndex += 2
        }
    }

    private fun addNearEndPoints() {
        var qIndex = 0
        while (qIndex < 3) {
            val quadT = (qIndex shr 1).toDouble()
            if (!fIntersections.hasT(quadT)) {
                val lineT = fLine.nearPoint(fQuad[qIndex], null)
                if (lineT >= 0) fIntersections.insert(quadT, lineT, fQuad[qIndex])
            }
            qIndex += 2
        }
        addLineNearEndPoints()
    }

    /**
     * Find any line endpoint that lies near the quadratic curve and
     * insert it. Mirrors `addLineNearEndPoints` ; uses [SkDQuad.nearPoint]
     * (the per-curve perpendicular-ray helper).
     */
    private fun addLineNearEndPoints() {
        for (lIndex in 0..1) {
            val lineT = lIndex.toDouble()
            if (fIntersections.hasOppT(lineT)) continue
            val quadT = fQuad.nearPoint(fLine[lIndex], fLine[1 - lIndex])
            if (quadT < 0) continue
            fIntersections.insert(quadT, lineT, fLine[lIndex])
        }
    }

    private fun addExactHorizontalEndPoints(left: Double, right: Double, y: Double) {
        var qIndex = 0
        while (qIndex < 3) {
            val lineT = SkDLine.ExactPointH(fQuad[qIndex], left, right, y)
            if (lineT >= 0) {
                val quadT = (qIndex shr 1).toDouble()
                fIntersections.insert(quadT, lineT, fQuad[qIndex])
            }
            qIndex += 2
        }
    }

    private fun addNearHorizontalEndPoints(left: Double, right: Double, y: Double) {
        var qIndex = 0
        while (qIndex < 3) {
            val quadT = (qIndex shr 1).toDouble()
            if (!fIntersections.hasT(quadT)) {
                val lineT = SkDLine.NearPointH(fQuad[qIndex], left, right, y)
                if (lineT >= 0) fIntersections.insert(quadT, lineT, fQuad[qIndex])
            }
            qIndex += 2
        }
        addLineNearEndPoints()
    }

    private fun addExactVerticalEndPoints(top: Double, bottom: Double, x: Double) {
        var qIndex = 0
        while (qIndex < 3) {
            val lineT = SkDLine.ExactPointV(fQuad[qIndex], top, bottom, x)
            if (lineT >= 0) {
                val quadT = (qIndex shr 1).toDouble()
                fIntersections.insert(quadT, lineT, fQuad[qIndex])
            }
            qIndex += 2
        }
    }

    private fun addNearVerticalEndPoints(top: Double, bottom: Double, x: Double) {
        var qIndex = 0
        while (qIndex < 3) {
            val quadT = (qIndex shr 1).toDouble()
            if (!fIntersections.hasT(quadT)) {
                val lineT = SkDLine.NearPointV(fQuad[qIndex], top, bottom, x)
                if (lineT >= 0) fIntersections.insert(quadT, lineT, fQuad[qIndex])
            }
            qIndex += 2
        }
        addLineNearEndPoints()
    }

    // ─── Coincidence + uniqueness post-processing ──────────────────

    private fun checkCoincident() {
        var last = fIntersections.used() - 1
        var index = 0
        while (index < last) {
            val quadMidT = (fIntersections.t(0, index) + fIntersections.t(0, index + 1)) / 2
            val quadMidPt = fQuad.ptAtT(quadMidT)
            val t = fLine.nearPoint(quadMidPt, null)
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

    private fun uniqueAnswer(quadT: Double, pt: SkDPoint): Boolean {
        for (inner in 0 until fIntersections.used()) {
            if (fIntersections.pt(inner) != pt) continue
            val existingQuadT = fIntersections.t(0, inner)
            if (quadT == existingQuadT) return false
            // mid-curve coincidence check
            val quadMidT = (existingQuadT + quadT) / 2
            val quadMidPt = fQuad.ptAtT(quadMidT)
            if (quadMidPt.approximatelyEqual(pt)) return false
        }
        return true
    }

    // ─── Pin / project helpers ──────────────────────────────────────

    /**
     * Project the quad point at parameter [t] back onto the line and
     * return its line-T. Uses the major-axis projection to minimize
     * floating-point loss. Mirrors `findLineT`.
     */
    private fun findLineT(t: Double): Double {
        val xy = fQuad.ptAtT(t)
        val dx = fLine[1].x - fLine[0].x
        val dy = fLine[1].y - fLine[0].y
        return if (abs(dx) > abs(dy)) (xy.x - fLine[0].x) / dx
        else (xy.y - fLine[0].y) / dy
    }

    /**
     * Clamp [quadT] / [lineT] to `[0, 1]` (with FLT_EPSILON_DOUBLE
     * slack) and snap [pt] to one of the endpoints when the values
     * round to 0 or 1. Returns false if either t is so far out of
     * range it should be rejected. Mirrors `pinTs`.
     */
    private fun pinTs(
        quadT: DoubleArray,
        lineT: DoubleArray,
        pt: SkDPoint,
        ptSet: PinTPoint,
    ): Boolean {
        if (!approximately_one_or_less_double(lineT[0])) return false
        if (!approximately_zero_or_more_double(lineT[0])) return false
        val qT = SkPinT(quadT[0]); quadT[0] = qT
        val lT = SkPinT(lineT[0]); lineT[0] = lT
        val computed = if (lT == 0.0 || lT == 1.0
            || (ptSet == PinTPoint.kPointUninitialized && qT != 0.0 && qT != 1.0)
        ) {
            fLine.ptAtT(lT)
        } else if (ptSet == PinTPoint.kPointUninitialized) {
            fQuad.ptAtT(qT)
        } else {
            // ptSet == kPointInitialized — pt already filled by caller.
            pt.copy()
        }
        // Snap pt to the chosen point (data-class copy is by reference assignment).
        pt.x = computed.x; pt.y = computed.y
        val gridPt = pt.asSkPoint()
        if (SkDPoint.ApproximatelyEqual(gridPt, fLine[0].asSkPoint())) {
            pt.x = fLine[0].x; pt.y = fLine[0].y
            lineT[0] = 0.0
        } else if (SkDPoint.ApproximatelyEqual(gridPt, fLine[1].asSkPoint())) {
            pt.x = fLine[1].x; pt.y = fLine[1].y
            lineT[0] = 1.0
        }
        if (fIntersections.used() > 0 && approximately_equal(fIntersections.t(1, 0), lineT[0])) {
            return false
        }
        if (gridPt == fQuad[0].asSkPoint()) {
            pt.x = fQuad[0].x; pt.y = fQuad[0].y
            quadT[0] = 0.0
        } else if (gridPt == fQuad[2].asSkPoint()) {
            pt.x = fQuad[2].x; pt.y = fQuad[2].y
            quadT[0] = 1.0
        }
        return true
    }
}

// SkIntersections public API for quad-line intersection lives in
// SkIntersections.kt directly (intersect/intersectRay/horizontal/
// vertical/quadLine/quadHorizontal/quadVertical + the static
// HorizontalIntercept/VerticalIntercept overloads).
