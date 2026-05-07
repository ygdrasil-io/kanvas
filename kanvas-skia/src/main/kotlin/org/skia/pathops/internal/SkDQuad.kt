/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsQuad.{h,cpp}` — `SkDQuad`, the
 * double-precision quadratic Bézier used by the pathops machinery.
 *
 * Phase D1.1.b — port of the data type + parametric helpers
 * (ptAtT / dxdyAtT / monotonic*), subdivision (subDivide / chopAt),
 * extrema and quadratic-equation root finders (FindExtrema /
 * RootsReal / RootsValidT / AddValidTs / SetABC), and the small
 * helpers (collapsed / controlsInside / flip / otherPts / align /
 * horizontalIntersect / verticalIntersect).
 *
 * Deferred to subsequent slices :
 *  - `isLinear`           → needs SkLineParameters (D1.1.c).
 *  - `hullIntersects(SkDConic/SkDCubic)` → cross-curve helpers (D1.1.d).
 *  - pinned `subDivide(a, c, t1, t2)` → needs SkIntersections (D1.1.c).
 */
package org.skia.pathops.internal

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.skia.math.SkPoint

/**
 * Pair of subdivided quadratics produced by [SkDQuad.chopAt]. The 5
 * shared points are stored linearly so `first` covers indices 0..2
 * and `second` covers indices 2..4.
 */
internal data class SkDQuadPair(val pts: Array<SkDPoint>) {

    init {
        require(pts.size == 5) { "SkDQuadPair requires exactly 5 points (got ${pts.size})" }
    }

    fun first(): SkDQuad = SkDQuad(arrayOf(pts[0], pts[1], pts[2]))
    fun second(): SkDQuad = SkDQuad(arrayOf(pts[2], pts[3], pts[4]))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkDQuadPair) return false
        return pts.contentEquals(other.pts)
    }

    override fun hashCode(): Int = pts.contentHashCode()
}

/**
 * Double-precision quadratic Bézier defined by 3 control points.
 * Mirrors
 * [`SkDQuad`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsQuad.h#L34).
 */
internal data class SkDQuad(val pts: Array<SkDPoint> = arrayOf(SkDPoint(), SkDPoint(), SkDPoint())) {

    init {
        require(pts.size == kPointCount) {
            "SkDQuad requires exactly $kPointCount points (got ${pts.size})"
        }
    }

    operator fun get(n: Int): SkDPoint {
        require(n in 0 until kPointCount)
        return pts[n]
    }

    operator fun set(n: Int, p: SkDPoint) {
        require(n in 0 until kPointCount)
        pts[n] = p
    }

    /** Mirrors `SkDQuad::set(const SkPoint pts[3])`. */
    fun set(p0: SkPoint, p1: SkPoint, p2: SkPoint): SkDQuad {
        pts[0] = SkDPoint(p0.fX.toDouble(), p0.fY.toDouble())
        pts[1] = SkDPoint(p1.fX.toDouble(), p1.fY.toDouble())
        pts[2] = SkDPoint(p2.fX.toDouble(), p2.fY.toDouble())
        return this
    }

    // ─── Predicates ──────────────────────────────────────────────────

    /** Mirrors `SkDQuad::collapsed`. */
    fun collapsed(): Boolean =
        pts[0].approximatelyEqual(pts[1]) && pts[0].approximatelyEqual(pts[2])

    /** Mirrors `SkDQuad::controlsInside` — the two end-points'
     *  vector dot products with the chord both point inward. */
    fun controlsInside(): Boolean {
        val v01 = pts[0] - pts[1]
        val v02 = pts[0] - pts[2]
        val v12 = pts[1] - pts[2]
        return v02.dot(v01) > 0 && v02.dot(v12) > 0
    }

    /** Mirrors `SkDQuad::monotonicInX` — `pts[1].x` between `pts[0].x` and `pts[2].x`. */
    fun monotonicInX(): Boolean = between(pts[0].x, pts[1].x, pts[2].x)

    /** Mirrors `SkDQuad::monotonicInY`. */
    fun monotonicInY(): Boolean = between(pts[0].y, pts[1].y, pts[2].y)

    /** Mirrors `SkDQuad::flip` — return a reversed copy. */
    fun flip(): SkDQuad = SkDQuad(arrayOf(pts[2], pts[1], pts[0]))

    /**
     * Returns true if the quadratic from index [startIndex] to [endIndex]
     * is approximately linear (control point lies within ULPs tolerance
     * of the chord through the endpoints). Mirrors
     * `SkDQuad::isLinear(int startIndex, int endIndex)`.
     *
     * Phase D1.1.c — wired to [SkLineParameters].
     */
    fun isLinear(startIndex: Int, endIndex: Int): Boolean {
        val params = SkLineParameters()
        params.quadEndPoints(this, startIndex, endIndex)
        params.normalize()
        val distance = params.controlPtDistance(this)
        var tiniest = pts[0].x
        for (p in pts) {
            tiniest = minOf(tiniest, p.x); tiniest = minOf(tiniest, p.y)
        }
        var largest = pts[0].x
        for (p in pts) {
            largest = maxOf(largest, p.x); largest = maxOf(largest, p.y)
        }
        largest = maxOf(largest, -tiniest)
        return approximately_zero_when_compared_to(distance, largest)
    }

    // ─── Pointer helpers (used by hullIntersects) ────────────────────

    /**
     * Fill [endPt] (length 2) with the two non-`oddMan` points.
     * Mirrors `SkDQuad::otherPts` ; see the upstream comment block
     * for the bit-twiddling rationale.
     */
    fun otherPts(oddMan: Int, endPt: Array<SkDPoint?>) {
        for (opp in 1 until kPointCount) {
            var end = (oddMan xor opp) - oddMan
            end = end and (end shr 2).inv() // clamp negative to zero
            endPt[opp - 1] = pts[end]
        }
    }

    // ─── Hull-intersect helpers (Phase D1.1.e.1) ────────────────────

    /**
     * Quick reject for quad-quad intersection by hull-direction
     * separation. Returns false if [q2]'s control hull lies entirely
     * on one side of every line through 2 of this quad's points (so
     * the curves at most share endpoints) ; returns true otherwise
     * and writes whether the hull is approximately linear into
     * [isLinearOut] (length-1 out array).
     *
     * Mirrors `SkDQuad::hullIntersects(const SkDQuad&)`. The
     * algorithm is the "rotate and check sign" approach from
     * `at_most_end_pts_in_common`.
     */
    fun hullIntersects(q2: SkDQuad, isLinearOut: BooleanArray): Boolean {
        var linear = true
        for (oddMan in 0 until kPointCount) {
            val endPt = arrayOfNulls<SkDPoint>(2)
            otherPts(oddMan, endPt)
            val origX = endPt[0]!!.x
            val origY = endPt[0]!!.y
            val adj = endPt[1]!!.x - origX
            val opp = endPt[1]!!.y - origY
            val sign = (pts[oddMan].y - origY) * adj - (pts[oddMan].x - origX) * opp
            if (approximately_zero(sign)) continue
            linear = false
            var foundOutlier = false
            for (n in 0 until kPointCount) {
                val test = (q2[n].y - origY) * adj - (q2[n].x - origX) * opp
                if (test * sign > 0 && !precisely_zero(test)) {
                    foundOutlier = true
                    break
                }
            }
            if (!foundOutlier) {
                isLinearOut[0] = linear
                return false
            }
        }
        // If hull is linear and q2's endpoints aren't this quad's endpoints,
        // check if either endpoint falls inside the (degenerate) triangle.
        if (linear && !matchesEnd(q2[0]) && !matchesEnd(q2[2])) {
            if (pointInTriangle(q2[0]) || pointInTriangle(q2[2])) linear = false
        }
        isLinearOut[0] = linear
        return true
    }

    /** Forwarding overload — delegates to [SkDConic.hullIntersects]. */
    fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean =
        conic.hullIntersects(this, isLinearOut)

    /** Forwarding overload — delegates to [SkDCubic.hullIntersects]. */
    fun hullIntersects(cubic: SkDCubic, isLinearOut: BooleanArray): Boolean =
        cubic.hullIntersects(this, isLinearOut)

    /** Mirrors `matchesEnd` static helper in `SkPathOpsQuad.cpp`. */
    private fun matchesEnd(test: SkDPoint): Boolean = pts[0] == test || pts[2] == test

    /**
     * Barycentric-coordinate point-in-triangle test using `pts[0..2]`
     * as the triangle vertices. Mirrors `pointInTriangle` from
     * blackpawn.com/texts/pointinpoly via `SkPathOpsQuad.cpp`.
     */
    private fun pointInTriangle(test: SkDPoint): Boolean {
        val v0 = pts[2] - pts[0]
        val v1 = pts[1] - pts[0]
        val v2 = test - pts[0]
        val dot00 = v0.dot(v0)
        val dot01 = v0.dot(v1)
        val dot02 = v0.dot(v2)
        val dot11 = v1.dot(v1)
        val dot12 = v1.dot(v2)
        val denom = dot00 * dot11 - dot01 * dot01
        val u = dot11 * dot02 - dot01 * dot12
        val v = dot00 * dot12 - dot01 * dot02
        return if (denom >= 0) u >= 0 && v >= 0 && u + v < denom
        else u <= 0 && v <= 0 && u + v > denom
    }

    // ─── Evaluation ──────────────────────────────────────────────────

    /**
     * Evaluate the quadratic at parameter [t]. Mirrors `SkDQuad::ptAtT`.
     */
    fun ptAtT(t: Double): SkDPoint {
        if (0.0 == t) return pts[0]
        if (1.0 == t) return pts[2]
        val oneT = 1 - t
        val a = oneT * oneT
        val b = 2 * oneT * t
        val c = t * t
        return SkDPoint(
            a * pts[0].x + b * pts[1].x + c * pts[2].x,
            a * pts[0].y + b * pts[1].y + c * pts[2].y,
        )
    }

    /**
     * Tangent vector at parameter [t] (the derivative of [ptAtT]).
     * Mirrors `SkDQuad::dxdyAtT`. When the derivative is `(0, 0)` and
     * `t` is at an endpoint, falls back to the chord vector.
     */
    fun dxdyAtT(t: Double): SkDVector {
        val a = t - 1
        val b = 1 - 2 * t
        val c = t
        val result = SkDVector(
            a * pts[0].x + b * pts[1].x + c * pts[2].x,
            a * pts[0].y + b * pts[1].y + c * pts[2].y,
        )
        if (result.x == 0.0 && result.y == 0.0 && zero_or_one(t)) {
            return pts[2] - pts[0]
        }
        return result
    }

    // ─── Subdivision ─────────────────────────────────────────────────

    /**
     * Return the sub-quadratic that covers the parametric interval
     * `[t1, t2]` of this quadratic. Mirrors `SkDQuad::subDivide`.
     *
     * Uses the de-Casteljau midpoint-and-endpoints reconstruction
     * (see the long comment block in the upstream `.cpp`).
     */
    fun subDivide(t1: Double, t2: Double): SkDQuad {
        if (0.0 == t1 && 1.0 == t2) return this
        val srcX = doubleArrayOf(pts[0].x, pts[1].x, pts[2].x)
        val srcY = doubleArrayOf(pts[0].y, pts[1].y, pts[2].y)
        val ax = interpQuadCoords(srcX, t1)
        val ay = interpQuadCoords(srcY, t1)
        val dx = interpQuadCoords(srcX, (t1 + t2) / 2)
        val dy = interpQuadCoords(srcY, (t1 + t2) / 2)
        val cx = interpQuadCoords(srcX, t2)
        val cy = interpQuadCoords(srcY, t2)
        val bx = 2 * dx - (ax + cx) / 2
        val by = 2 * dy - (ay + cy) / 2
        return SkDQuad(arrayOf(SkDPoint(ax, ay), SkDPoint(bx, by), SkDPoint(cx, cy)))
    }

    /**
     * Split the quadratic at parameter [t] into two sub-quadratics
     * (the classic single-step de Casteljau). Mirrors `SkDQuad::chopAt`.
     */
    fun chopAt(t: Double): SkDQuadPair {
        val pair = Array(5) { SkDPoint() }
        chopAxis(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), t) { i, v -> pair[i].x = v }
        chopAxis(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), t) { i, v -> pair[i].y = v }
        return SkDQuadPair(pair)
    }

    /**
     * In-place align — if the control point coincides with the end-point
     * on either axis, snap the destination point to that exact value
     * (eliminates near-zero residuals from float arithmetic).
     * Mirrors `SkDQuad::align`.
     */
    fun align(endIndex: Int, dstPt: SkDPoint) {
        if (pts[endIndex].x == pts[1].x) dstPt.x = pts[endIndex].x
        if (pts[endIndex].y == pts[1].y) dstPt.y = pts[endIndex].y
    }

    /**
     * "Pinned" sub-divide variant — given pinned endpoints [a] (≈ value
     * at [t1]) and [c] (≈ value at [t2]), recover the middle control
     * point of the sub-quadratic. Mirrors
     * `SkDPoint SkDQuad::subDivide(const SkDPoint&, const SkDPoint&, double, double)`.
     *
     * Algorithm : compute the regular sub-divided quad's control,
     * project two rays through `(a, sub[1] + (a - sub[0]))` and
     * `(c, sub[1] + (c - sub[2]))`, intersect them ; their crossing
     * point is the pinned middle control. Falls back to the midpoint
     * of the two ray endpoints if the rays don't meet cleanly.
     *
     * Phase D1.1.c — wired to [SkIntersections.intersectRay].
     */
    fun subDivide(a: SkDPoint, c: SkDPoint, t1: Double, t2: Double): SkDPoint {
        require(t1 != t2)
        val sub = subDivide(t1, t2)
        val b0 = SkDLine(arrayOf(a, sub[1] + (a - sub[0])))
        val b1 = SkDLine(arrayOf(c, sub[1] + (c - sub[2])))
        val ix = SkIntersections()
        ix.intersectRay(b0, b1)
        val b: SkDPoint = if (ix.used() == 1 && ix.t(0, 0) >= 0 && ix.t(1, 0) >= 0) {
            ix.pt(0).copy()
        } else {
            return SkDPoint.Mid(b0[1], b1[1])
        }
        if (t1 == 0.0 || t2 == 0.0) align(0, b)
        if (t1 == 1.0 || t2 == 1.0) align(2, b)
        if (AlmostBequalUlps(b.x, a.x)) b.x = a.x
        else if (AlmostBequalUlps(b.x, c.x)) b.x = c.x
        if (AlmostBequalUlps(b.y, a.y)) b.y = a.y
        else if (AlmostBequalUlps(b.y, c.y)) b.y = c.y
        return b
    }

    // ─── Linear-line intercepts (axis-aligned) ───────────────────────

    /**
     * Number of valid roots `(0 < t < 1)` where this quadratic
     * intersects the horizontal line `y = yIntercept`.
     * Mirrors `SkDQuad::horizontalIntersect`.
     */
    fun horizontalIntersect(yIntercept: Double, roots: DoubleArray): Int {
        val a = DoubleArray(1); val b = DoubleArray(1); val c = DoubleArray(1)
        SetABC(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), a, b, c)
        c[0] -= yIntercept
        return RootsValidT(a[0], b[0], c[0], roots)
    }

    /** Vertical-line analogue. Mirrors `SkDQuad::verticalIntersect`. */
    fun verticalIntersect(xIntercept: Double, roots: DoubleArray): Int {
        val a = DoubleArray(1); val b = DoubleArray(1); val c = DoubleArray(1)
        SetABC(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), a, b, c)
        c[0] -= xIntercept
        return RootsValidT(a[0], b[0], c[0], roots)
    }

    /**
     * Project [xy] onto this quadratic via a perpendicular ray (the
     * perpendicular is built from the chord direction `xy → opp` —
     * rotated 90°). Returns the curve t-value if `xy` is "near" the
     * curve (within ULPs tolerance scaled by the curve's coordinate
     * range), or `-1` if not. Mirrors `SkDCurve::nearPoint(SkPath::kQuad_Verb, ...)`.
     *
     * Used by [LineQuadraticIntersections.addLineNearEndPoints] to
     * register near-tangent intersections at the line endpoints.
     */
    fun nearPoint(xy: SkDPoint, opp: SkDPoint): Double {
        // Bbox reject : skip if xy is clearly outside the control hull.
        var minX = pts[0].x; var maxX = minX
        var minY = pts[0].y; var maxY = minY
        for (i in 1 until kPointCount) {
            minX = minOf(minX, pts[i].x); maxX = maxOf(maxX, pts[i].x)
            minY = minOf(minY, pts[i].y); maxY = maxOf(maxY, pts[i].y)
        }
        if (!AlmostBetweenUlps(minX, xy.x, maxX)) return -1.0
        if (!AlmostBetweenUlps(minY, xy.y, maxY)) return -1.0
        // Perpendicular ray from xy : direction is the chord rotated 90°.
        val perp = SkDLine(arrayOf(
            xy,
            SkDPoint(xy.x + opp.y - xy.y, xy.y + xy.x - opp.x),
        ))
        val ix = SkIntersections()
        ix.intersectRay(this, perp)
        var minIdx = -1
        var minDist = Double.MAX_VALUE
        for (i in 0 until ix.used()) {
            val d = xy.distance(ix.pt(i))
            if (minDist > d) { minDist = d; minIdx = i }
        }
        if (minIdx < 0) return -1.0
        var largest = maxOf(maxX, maxY)
        largest = maxOf(largest, -minOf(minX, minY))
        if (!AlmostEqualUlpsPin(largest, largest + minDist)) return -1.0
        return SkPinT(ix.t(0, minIdx))
    }

    // ─── Equality (default array equality is referential) ────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkDQuad) return false
        return pts[0] == other.pts[0] && pts[1] == other.pts[1] && pts[2] == other.pts[2]
    }

    override fun hashCode(): Int =
        31 * (31 * pts[0].hashCode() + pts[1].hashCode()) + pts[2].hashCode()

    companion object {
        const val kPointCount = 3
        const val kPointLast = kPointCount - 1
        const val kMaxIntersections = 4

        fun pointCount(): Int = kPointCount
        fun pointLast(): Int = kPointLast
        fun maxIntersections(): Int = kMaxIntersections

        /** Mirrors `SkDQuad::IsConic` — quadratic is not a conic. */
        fun IsConic(): Boolean = false

        // ─── Root-finding ────────────────────────────────────────────

        /**
         * Filter [s] (raw real roots) into [t] (valid roots in [0, 1]
         * with deduplication). Mirrors `SkDQuad::AddValidTs`.
         */
        fun AddValidTs(s: DoubleArray, realRoots: Int, t: DoubleArray): Int {
            var foundRoots = 0
            for (index in 0 until realRoots) {
                var tValue = s[index]
                if (approximately_zero_or_more(tValue) && approximately_one_or_less(tValue)) {
                    if (approximately_less_than_zero(tValue)) tValue = 0.0
                    else if (approximately_greater_than_one(tValue)) tValue = 1.0
                    var dup = false
                    for (idx2 in 0 until foundRoots) {
                        if (approximately_equal(t[idx2], tValue)) { dup = true; break }
                    }
                    if (!dup) t[foundRoots++] = tValue
                }
            }
            return foundRoots
        }

        /**
         * Returns the number of valid roots of `A*t² + B*t + C = 0`
         * in `[0, 1]`. Mirrors `SkDQuad::RootsValidT`.
         */
        fun RootsValidT(A: Double, B: Double, C: Double, t: DoubleArray): Int {
            val s = DoubleArray(2)
            val real = RootsReal(A, B, C, s)
            return AddValidTs(s, real, t)
        }

        /**
         * Real-root finder for `A*t² + B*t + C = 0`. Mirrors
         * `SkDQuad::RootsReal` — uses the `Q = -1/2(B + sgn(B) sqrt(B² - 4AC))`
         * formulation from Numerical Recipes 5.6 to avoid loss of
         * significance.
         */
        fun RootsReal(A: Double, B: Double, C: Double, s: DoubleArray): Int {
            if (A == 0.0) return handleZero(B, C, s)
            val p = B / (2 * A)
            val q = C / A
            if (approximately_zero(A) && (approximately_zero_inverse(p) || approximately_zero_inverse(q))) {
                return handleZero(B, C, s)
            }
            // normal form : x² + p·x + q = 0
            val p2 = p * p
            if (!AlmostDequalUlps(p2, q) && p2 < q) return 0
            var sqrtD = 0.0
            if (p2 > q) sqrtD = sqrt(p2 - q)
            s[0] = sqrtD - p
            s[1] = -sqrtD - p
            return 1 + (if (!AlmostDequalUlps(s[0], s[1])) 1 else 0)
        }

        private fun handleZero(B: Double, C: Double, s: DoubleArray): Int {
            if (approximately_zero(B)) {
                s[0] = 0.0
                return if (C == 0.0) 1 else 0
            }
            s[0] = -C / B
            return 1
        }

        /**
         * Solve for the parametric form coefficients :
         * `A*t*t + 2*B*t*(1-t) + C*(1-t)*(1-t)`
         * → `a*t² + b*t + c`. Mirrors `SkDQuad::SetABC`.
         *
         * Note : upstream uses a stride-2 layout `&fPts[0].fX` so its
         * indices are `quad[0] / quad[2] / quad[4]`. We pass a stride-1
         * array of length 3 (one axis only) so the indices become
         * `quad[0] / quad[1] / quad[2]`.
         */
        fun SetABC(quad: DoubleArray, a: DoubleArray, b: DoubleArray, c: DoubleArray) {
            a[0] = quad[0]
            b[0] = 2 * quad[1]
            c[0] = quad[2]
            b[0] -= c[0]
            a[0] -= b[0]
            b[0] -= c[0]
        }

        /**
         * Find the (at-most-1) interior extremum of `Q(t) = src[0..2]`
         * on a single axis. Mirrors `SkDQuad::FindExtrema`.
         *
         * `Q'(t) = A*t + B = 0` solved for `t`.
         *
         * Stride-1 layout (see [SetABC] for the conversion note).
         */
        fun FindExtrema(src: DoubleArray, tValue: DoubleArray): Int {
            val a = src[0]; val b = src[1]; val c = src[2]
            return validUnitDivide(a - b, a - b - b + c, tValue)
        }

        // ─── Internal helpers ────────────────────────────────────────

        private fun validUnitDivide(numerIn: Double, denomIn: Double, ratio: DoubleArray): Int {
            var numer = numerIn; var denom = denomIn
            if (numer < 0) { numer = -numer; denom = -denom }
            if (denom == 0.0 || numer == 0.0 || numer >= denom) return 0
            val r = numer / denom
            if (r == 0.0) return 0 // catch underflow
            ratio[0] = r
            return 1
        }

        /**
         * de-Casteljau coordinate eval (single axis) at parameter [t].
         * Input layout : stride-1 `src[0..2]`. Equivalent to upstream's
         * `interp_quad_coords(&fPts[0].fX, t)` which uses stride-2
         * indices `src[0/2/4]`.
         */
        private fun interpQuadCoords(src: DoubleArray, t: Double): Double {
            if (0.0 == t) return src[0]
            if (1.0 == t) return src[2]
            val ab = SkDInterp(src[0], src[1], t)
            val bc = SkDInterp(src[1], src[2], t)
            return SkDInterp(ab, bc, t)
        }

        /**
         * Subdivide one axis at [t], filling the 5-element output with
         * the chopped pair (output indices 0/2/4/6/8 — Skia's
         * stride-2 layout — but we expose only the 5 used positions
         * and let the caller assign x or y).
         */
        private fun chopAxis(src: DoubleArray, t: Double, write: (Int, Double) -> Unit) {
            val ab = SkDInterp(src[0], src[1], t)
            val bc = SkDInterp(src[1], src[2], t)
            val abc = SkDInterp(ab, bc, t)
            write(0, src[0])
            write(1, ab)
            write(2, abc)
            write(3, bc)
            write(4, src[2])
        }
    }
}
