/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsCubic.{h,cpp}` — `SkDCubic`,
 * the double-precision cubic Bézier used by the pathops machinery.
 *
 * Phase D1.1.b — port of the data type + parametric helpers
 * (ptAtT / dxdyAtT / monotonic*), subdivision (subDivide / chopAt),
 * extrema / inflections / max-curvature finders, cubic-equation root
 * finders (Coefficients / RootsReal / RootsValidT), and the small
 * helpers (collapsed / controlsInside / otherPts / align /
 * endsAreExtremaInXOrY / toQuad / toFloatPoints / calcPrecision).
 *
 * Deferred to subsequent slices :
 *  - `isLinear`           → needs SkLineParameters (D1.1.c).
 *  - `hullIntersects(...)` → needs convexHull (D1.1.d).
 *  - `ComplexBreak`       → needs SkClassifyCubic (D1.1.d).
 *  - `binarySearch` / `searchRoots` / `horizontalIntersect` /
 *    `verticalIntersect` / `top` → use binarySearch + findInflections,
 *    deferred until needed.
 *  - pinned `subDivide(a, d, t1, t2, dst[2])` → needs the unpinned
 *    variant, defer to D1.1.c when intersection-based pinning lands.
 */
package org.skia.pathops.internal


import org.graphiks.math.AlmostBequalUlps
import org.graphiks.math.AlmostBetweenUlps
import org.graphiks.math.AlmostDequalUlps
import org.graphiks.math.AlmostEqualUlpsPin
import org.graphiks.math.SkDInterp
import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.graphiks.math.SkDVector
import org.graphiks.math.SkPinT
import org.graphiks.math.approximately_equal
import org.graphiks.math.approximately_equal_half
import org.graphiks.math.approximately_one_or_less
import org.graphiks.math.approximately_zero
import org.graphiks.math.approximately_zero_or_more
import org.graphiks.math.approximately_zero_when_compared_to
import org.graphiks.math.between
import org.graphiks.math.precisely_between
import org.graphiks.math.precisely_zero
import org.graphiks.math.zero_or_one
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.graphiks.math.SkPoint

/**
 * Pair of subdivided cubics produced by [SkDCubic.chopAt]. The 7
 * shared points are stored linearly so `first` covers indices 0..3
 * and `second` covers indices 3..6.
 */
internal data class SkDCubicPair(val pts: Array<SkDPoint>) {

    init {
        require(pts.size == 7) { "SkDCubicPair requires exactly 7 points (got ${pts.size})" }
    }

    fun first(): SkDCubic = SkDCubic(arrayOf(pts[0], pts[1], pts[2], pts[3]))
    fun second(): SkDCubic = SkDCubic(arrayOf(pts[3], pts[4], pts[5], pts[6]))

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkDCubicPair) return false
        return pts.contentEquals(other.pts)
    }

    override fun hashCode(): Int = pts.contentHashCode()
}

/**
 * Double-precision cubic Bézier defined by 4 control points.
 * Mirrors
 * [`SkDCubic`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsCubic.h#L29).
 */
internal data class SkDCubic(
    val pts: Array<SkDPoint> = arrayOf(SkDPoint(), SkDPoint(), SkDPoint(), SkDPoint()),
) {

    init {
        require(pts.size == kPointCount) {
            "SkDCubic requires exactly $kPointCount points (got ${pts.size})"
        }
    }

    enum class SearchAxis { kXAxis, kYAxis }

    operator fun get(n: Int): SkDPoint {
        require(n in 0 until kPointCount)
        return pts[n]
    }

    operator fun set(n: Int, p: SkDPoint) {
        require(n in 0 until kPointCount)
        pts[n] = p
    }

    /** Mirrors `SkDCubic::set`. */
    fun set(p0: SkPoint, p1: SkPoint, p2: SkPoint, p3: SkPoint): SkDCubic {
        pts[0] = SkDPoint(p0.fX.toDouble(), p0.fY.toDouble())
        pts[1] = SkDPoint(p1.fX.toDouble(), p1.fY.toDouble())
        pts[2] = SkDPoint(p2.fX.toDouble(), p2.fY.toDouble())
        pts[3] = SkDPoint(p3.fX.toDouble(), p3.fY.toDouble())
        return this
    }

    // ─── Predicates ──────────────────────────────────────────────────

    /** Mirrors `SkDCubic::collapsed`. */
    fun collapsed(): Boolean =
        pts[0].approximatelyEqual(pts[1])
            && pts[0].approximatelyEqual(pts[2])
            && pts[0].approximatelyEqual(pts[3])

    /** Mirrors `SkDCubic::controlsInside`. */
    fun controlsInside(): Boolean {
        val v01 = pts[0] - pts[1]
        val v02 = pts[0] - pts[2]
        val v03 = pts[0] - pts[3]
        val v13 = pts[1] - pts[3]
        val v23 = pts[2] - pts[3]
        return v03.dot(v01) > 0 && v03.dot(v02) > 0
            && v03.dot(v13) > 0 && v03.dot(v23) > 0
    }

    /** Mirrors `SkDCubic::monotonicInX` — uses `precisely_between`. */
    fun monotonicInX(): Boolean =
        precisely_between(pts[0].x, pts[1].x, pts[3].x)
            && precisely_between(pts[0].x, pts[2].x, pts[3].x)

    /** Mirrors `SkDCubic::monotonicInY`. */
    fun monotonicInY(): Boolean =
        precisely_between(pts[0].y, pts[1].y, pts[3].y)
            && precisely_between(pts[0].y, pts[2].y, pts[3].y)

    /** Mirrors `SkDCubic::endsAreExtremaInXOrY`. */
    fun endsAreExtremaInXOrY(): Boolean =
        (between(pts[0].x, pts[1].x, pts[3].x)
            && between(pts[0].x, pts[2].x, pts[3].x))
            || (between(pts[0].y, pts[1].y, pts[3].y)
            && between(pts[0].y, pts[2].y, pts[3].y))

    // ─── Pointer helpers ─────────────────────────────────────────────

    /**
     * Fill [o1Pts] (length kPointCount-1 = 3) with the three points
     * other than `pts[index ? 0 : 3]`. Mirrors `SkDCubic::otherPts`.
     */
    fun otherPts(index: Int, o1Pts: Array<SkDPoint?>) {
        var offset = if (index == 0) 1 else 0
        o1Pts[0] = pts[offset]; offset++
        o1Pts[1] = pts[offset]; offset++
        o1Pts[2] = pts[offset]
    }

    // ─── Convex hull + cross-curve hullIntersects (Phase D1.1.e.1) ──

    /**
     * Compute the convex hull of the 4 control points. Writes the
     * vertex indices in CCW order into [order] (length 4). Returns
     * the number of hull vertices (3 or 4 — never less, since
     * collapsed cubics are not considered).
     *
     * Mirrors `SkDCubic::convexHull` (`src/pathops/SkOpCubicHull.cpp`).
     * The algorithm assumes 3 unique non-linear points form a
     * triangle ; the 4th point may replace one of the first three,
     * be discarded if inside, or be inserted to form a quadrilateral.
     */
    fun convexHull(order: CharArray): Int {
        require(order.size >= 4)
        // Find top point (smallest y, then smallest x).
        var yMin = 0
        for (index in 1 until 4) {
            if (pts[yMin].y > pts[index].y
                || (pts[yMin].y == pts[index].y && pts[yMin].x > pts[index].x)
            ) yMin = index
        }
        order[0] = yMin.toChar()
        var midX = -1
        var backupYMin = -1
        for (pass in 0 until 2) {
            for (index in 0 until 4) {
                if (index == yMin) continue
                val mask = otherTwo(yMin, index)
                val side1 = yMin xor mask
                val side2 = index xor mask
                val rotPath = SkDCubic(arrayOf(SkDPoint(), SkDPoint(), SkDPoint(), SkDPoint()))
                if (!rotateForHull(yMin, index, rotPath)) {
                    order[1] = side1.toChar()
                    order[2] = side2.toChar()
                    return 3
                }
                var sides = sideSign(rotPath[side1].y - rotPath[yMin].y)
                sides = sides xor sideSign(rotPath[side2].y - rotPath[yMin].y)
                if (sides == 2) { // one remaining point < 0, one > 0 — index is the mid
                    if (midX >= 0) {
                        // Two midpoint candidates : means a control coincides with an end.
                        order[0] = 0.toChar()
                        order[1] = 3.toChar()
                        if (pts[1] == pts[0] || pts[1] == pts[3]) {
                            order[2] = 2.toChar(); return 3
                        }
                        if (pts[2] == pts[0] || pts[2] == pts[3]) {
                            order[2] = 1.toChar(); return 3
                        }
                        // Near-equal control to end (numerically) — pick the closer.
                        val d10 = pts[1].distanceSquared(pts[0])
                        val d13 = pts[1].distanceSquared(pts[3])
                        val d20 = pts[2].distanceSquared(pts[0])
                        val d23 = pts[2].distanceSquared(pts[3])
                        val s1 = minOf(d10, d13)
                        val s2 = minOf(d20, d23)
                        if (approximately_zero(minOf(s1, s2))) {
                            order[2] = (if (s1 < s2) 2 else 1).toChar()
                            return 3
                        }
                    }
                    midX = index
                } else if (sides == 0) {
                    // Both points to one side — index is a backup top.
                    backupYMin = index
                }
            }
            if (midX >= 0) break
            if (backupYMin < 0) break
            yMin = backupYMin
            backupYMin = -1
        }
        if (midX < 0) midX = yMin xor 3 // any other point
        val mask = otherTwo(yMin, midX)
        val least = yMin xor mask
        val most = midX xor mask
        order[0] = yMin.toChar()
        order[1] = least.toChar()
        // Check whether mid is on same side as yMin of the (least, most) line.
        val midPath = SkDCubic(arrayOf(SkDPoint(), SkDPoint(), SkDPoint(), SkDPoint()))
        if (!rotateForHull(least, most, midPath)) {
            order[2] = midX.toChar()
            return 3
        }
        var midSides = sideSign(midPath[yMin].y - midPath[least].y)
        midSides = midSides xor sideSign(midPath[midX].y - midPath[least].y)
        if (midSides != 2) {
            order[2] = most.toChar()
            return 3
        }
        order[2] = midX.toChar()
        order[3] = most.toChar()
        return 4
    }

    /**
     * Generic hull-intersects test : does this cubic's hull share a
     * separating line with the polygon `pts[0..ptCount-1]` ?
     * Mirrors `SkDCubic::hullIntersects(const SkDPoint*, int, bool*)`.
     */
    fun hullIntersects(opp: Array<SkDPoint>, ptCount: Int, isLinearOut: BooleanArray): Boolean {
        var linear = true
        val hullOrder = CharArray(4)
        val hullCount = convexHull(hullOrder)
        var end1 = hullOrder[0].code
        var hullIndex = 0
        var endPt0 = pts[end1]
        var endPt1 = pts[end1] // placeholder, overwritten below
        do {
            hullIndex = (hullIndex + 1) % hullCount
            val end2 = hullOrder[hullIndex].code
            endPt1 = pts[end2]
            val origX = endPt0.x
            val origY = endPt0.y
            val adj = endPt1.x - origX
            val opp_ = endPt1.y - origY
            val oddManMask = otherTwo(end1, end2)
            val oddMan = end1 xor oddManMask
            val sign0 = (pts[oddMan].y - origY) * adj - (pts[oddMan].x - origX) * opp_
            val oddMan2 = end2 xor oddManMask
            val sign1 = (pts[oddMan2].y - origY) * adj - (pts[oddMan2].x - origX) * opp_
            var sign = sign0
            if (sign * sign1 < 0) {
                endPt0 = endPt1; end1 = end2
                continue
            }
            if (approximately_zero(sign)) {
                sign = sign1
                if (approximately_zero(sign)) {
                    endPt0 = endPt1; end1 = end2
                    continue
                }
            }
            linear = false
            var foundOutlier = false
            for (n in 0 until ptCount) {
                val test = (opp[n].y - origY) * adj - (opp[n].x - origX) * opp_
                if (test * sign > 0 && !precisely_zero(test)) {
                    foundOutlier = true
                    break
                }
            }
            if (!foundOutlier) {
                isLinearOut[0] = linear
                return false
            }
            endPt0 = endPt1
            end1 = end2
        } while (hullIndex != 0)
        isLinearOut[0] = linear
        return true
    }

    /** Mirrors `SkDCubic::hullIntersects(const SkDCubic&)`. */
    fun hullIntersects(c2: SkDCubic, isLinearOut: BooleanArray): Boolean =
        hullIntersects(arrayOf(c2[0], c2[1], c2[2], c2[3]), kPointCount, isLinearOut)

    /** Mirrors `SkDCubic::hullIntersects(const SkDQuad&)`. */
    fun hullIntersects(quad: SkDQuad, isLinearOut: BooleanArray): Boolean =
        hullIntersects(arrayOf(quad[0], quad[1], quad[2]), SkDQuad.kPointCount, isLinearOut)

    /** Mirrors `SkDCubic::hullIntersects(const SkDConic&)`. */
    fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean =
        hullIntersects(arrayOf(conic[0], conic[1], conic[2]), SkDConic.kPointCount, isLinearOut)

    // ─── Evaluation ──────────────────────────────────────────────────

    /** Mirrors `SkDCubic::ptAtT`. */
    fun ptAtT(t: Double): SkDPoint {
        if (0.0 == t) return pts[0]
        if (1.0 == t) return pts[3]
        val oneT = 1 - t
        val oneT2 = oneT * oneT
        val a = oneT2 * oneT
        val b = 3 * oneT2 * t
        val t2 = t * t
        val c = 3 * oneT * t2
        val d = t2 * t
        return SkDPoint(
            a * pts[0].x + b * pts[1].x + c * pts[2].x + d * pts[3].x,
            a * pts[0].y + b * pts[1].y + c * pts[2].y + d * pts[3].y,
        )
    }

    /**
     * Tangent vector at parameter [t]. Mirrors `SkDCubic::dxdyAtT`.
     * Falls back to a chord vector at endpoints when the direct
     * derivative collapses to `(0, 0)`.
     */
    fun dxdyAtT(t: Double): SkDVector {
        var result = SkDVector(
            derivativeAtT(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x), t),
            derivativeAtT(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y), t),
        )
        if (result.x == 0.0 && result.y == 0.0) {
            if (t == 0.0) result = pts[2] - pts[0]
            else if (t == 1.0) result = pts[3] - pts[1]
            if (result.x == 0.0 && result.y == 0.0 && zero_or_one(t)) {
                result = pts[3] - pts[0]
            }
        }
        return result
    }

    // ─── Subdivision ─────────────────────────────────────────────────

    /**
     * Split this cubic into two sub-cubics at parameter [t]
     * (single-step de Casteljau). Mirrors `SkDCubic::chopAt`.
     */
    fun chopAt(t: Double): SkDCubicPair {
        val pair = Array(7) { SkDPoint() }
        if (t == 0.5) {
            pair[0] = pts[0]
            pair[1].x = (pts[0].x + pts[1].x) / 2
            pair[1].y = (pts[0].y + pts[1].y) / 2
            pair[2].x = (pts[0].x + 2 * pts[1].x + pts[2].x) / 4
            pair[2].y = (pts[0].y + 2 * pts[1].y + pts[2].y) / 4
            pair[3].x = (pts[0].x + 3 * (pts[1].x + pts[2].x) + pts[3].x) / 8
            pair[3].y = (pts[0].y + 3 * (pts[1].y + pts[2].y) + pts[3].y) / 8
            pair[4].x = (pts[1].x + 2 * pts[2].x + pts[3].x) / 4
            pair[4].y = (pts[1].y + 2 * pts[2].y + pts[3].y) / 4
            pair[5].x = (pts[2].x + pts[3].x) / 2
            pair[5].y = (pts[2].y + pts[3].y) / 2
            pair[6] = pts[3]
            return SkDCubicPair(pair)
        }
        chopAxis(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x), t) { i, v -> pair[i].x = v }
        chopAxis(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y), t) { i, v -> pair[i].y = v }
        return SkDCubicPair(pair)
    }

    /**
     * Return the sub-cubic that covers the parametric interval
     * `[t1, t2]`. Mirrors `SkDCubic::subDivide`.
     *
     * Uses the system `B = (M*2 - N)/18`, `C = (N*2 - M)/18`
     * derived in the upstream comment block, where `M` and `N` are
     * combinations of the cubic's value at `(2*t1+t2)/3` and
     * `(t1+2*t2)/3`.
     */
    fun subDivide(t1: Double, t2: Double): SkDCubic {
        if (t1 == 0.0 || t2 == 1.0) {
            if (t1 == 0.0 && t2 == 1.0) return this
            val pair = chopAt(if (t1 == 0.0) t2 else t1)
            return if (t1 == 0.0) pair.first() else pair.second()
        }
        val srcX = doubleArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x)
        val srcY = doubleArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y)
        val ax = interpCubicCoords(srcX, t1)
        val ay = interpCubicCoords(srcY, t1)
        val ex = interpCubicCoords(srcX, (t1 * 2 + t2) / 3)
        val ey = interpCubicCoords(srcY, (t1 * 2 + t2) / 3)
        val fx = interpCubicCoords(srcX, (t1 + t2 * 2) / 3)
        val fy = interpCubicCoords(srcY, (t1 + t2 * 2) / 3)
        val dx = interpCubicCoords(srcX, t2)
        val dy = interpCubicCoords(srcY, t2)
        val mx = ex * 27 - ax * 8 - dx
        val my = ey * 27 - ay * 8 - dy
        val nx = fx * 27 - ax - dx * 8
        val ny = fy * 27 - ay - dy * 8
        val bx = (mx * 2 - nx) / 18
        val by = (my * 2 - ny) / 18
        val cx = (nx * 2 - mx) / 18
        val cy = (ny * 2 - my) / 18
        return SkDCubic(arrayOf(
            SkDPoint(ax, ay), SkDPoint(bx, by), SkDPoint(cx, cy), SkDPoint(dx, dy),
        ))
    }

    /**
     * In-place align : if the control coincides with the end on either
     * axis, snap [dstPt] to that exact value. Mirrors `SkDCubic::align`.
     */
    fun align(endIndex: Int, ctrlIndex: Int, dstPt: SkDPoint) {
        if (pts[endIndex].x == pts[ctrlIndex].x) dstPt.x = pts[endIndex].x
        if (pts[endIndex].y == pts[ctrlIndex].y) dstPt.y = pts[endIndex].y
    }

    // ─── Inflections / extrema / max-curvature ───────────────────────

    /**
     * Return the t-values of the cubic's inflections (up to 2).
     * Mirrors `SkDCubic::findInflections`.
     */
    fun findInflections(tValues: DoubleArray): Int {
        val Ax = pts[1].x - pts[0].x
        val Ay = pts[1].y - pts[0].y
        val Bx = pts[2].x - 2 * pts[1].x + pts[0].x
        val By = pts[2].y - 2 * pts[1].y + pts[0].y
        val Cx = pts[3].x + 3 * (pts[1].x - pts[2].x) - pts[0].x
        val Cy = pts[3].y + 3 * (pts[1].y - pts[2].y) - pts[0].y
        return SkDQuad.RootsValidT(Bx * Cy - By * Cx, Ax * Cy - Ay * Cx, Ax * By - Ay * Bx, tValues)
    }

    /**
     * Return the t-values where curvature is maximum (up to 3).
     * Mirrors `SkDCubic::findMaxCurvature`.
     */
    fun findMaxCurvature(tValues: DoubleArray): Int {
        val coeffX = DoubleArray(4)
        val coeffY = DoubleArray(4)
        formulateF1DotF2(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x), coeffX)
        formulateF1DotF2(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y), coeffY)
        for (i in 0 until 4) coeffX[i] += coeffY[i]
        return RootsValidT(coeffX[0], coeffX[1], coeffX[2], coeffX[3], tValues)
    }

    // ─── Conversions ─────────────────────────────────────────────────

    /**
     * Convert this cubic to a quadratic by averaging the two
     * degree-elevation guesses for the quadratic control point.
     * Mirrors `SkDCubic::toQuad` (`src/pathops/SkDCubicToQuads.cpp`).
     *
     * From `Q1 = 1/3·P0 + 2/3·P1` and `Q2 = 2/3·P1 + 1/3·P2`, both
     * equations solve for `P1` if the cubic is a degree-elevated
     * quadratic. For a true cubic we average :
     * `P1 = (-Q0 + 3·Q1 + 3·Q2 - Q3) / 4`.
     */
    fun toQuad(): SkDQuad {
        val bx = (3 * (pts[1].x + pts[2].x) - pts[0].x - pts[3].x) / 4
        val by = (3 * (pts[1].y + pts[2].y) - pts[0].y - pts[3].y) / 4
        return SkDQuad(arrayOf(pts[0], SkDPoint(bx, by), pts[3]))
    }

    /**
     * Round each control point to single-precision floats. Returns
     * `false` if any rounded coordinate is non-finite.
     * Mirrors `SkDCubic::toFloatPoints`.
     */
    fun toFloatPoints(out: Array<SkPoint>): Boolean {
        require(out.size >= kPointCount)
        for (i in 0 until kPointCount) {
            val fx = pts[i].x.toFloat()
            val fy = pts[i].y.toFloat()
            if (!fx.isFinite() || !fy.isFinite()) return false
            out[i] = SkPoint(fX = fx, fY = fy)
        }
        return true
    }

    /**
     * Heuristic precision : sum of leg lengths divided by [gPrecisionUnit].
     * Mirrors `SkDCubic::calcPrecision`.
     */
    fun calcPrecision(): Double =
        ((pts[1] - pts[0]).length()
            + (pts[2] - pts[1]).length()
            + (pts[3] - pts[2]).length()) / gPrecisionUnit

    /**
     * Binary-search for `t` in `[min, max]` such that the curve's
     * [xAxis] coordinate at `t` equals [axisIntercept] (within
     * `approximately_equal`). Returns -1 if no such t exists.
     *
     * The search bisects until the candidate point stops moving
     * (`approximately_equal_half` on both axes), or until it
     * straddles the target. Mirrors `SkDCubic::binarySearch`.
     */
    fun binarySearch(min: Double, max: Double, axisIntercept: Double, xAxis: SearchAxis): Double {
        var t = (min + max) / 2
        var step = (t - min) / 2
        var cubicAtT = ptAtT(t)
        var calcPos = if (xAxis == SearchAxis.kXAxis) cubicAtT.x else cubicAtT.y
        var calcDist = calcPos - axisIntercept
        do {
            val priorT = maxOf(min, t - step)
            val lessPt = ptAtT(priorT)
            if (approximately_equal_half(lessPt.x, cubicAtT.x)
                && approximately_equal_half(lessPt.y, cubicAtT.y)
            ) return -1.0 // search has stalled
            val lessPos = if (xAxis == SearchAxis.kXAxis) lessPt.x else lessPt.y
            val lessDist = lessPos - axisIntercept
            val lastStep = step
            step /= 2
            if (if (calcDist > 0) calcDist > lessDist else calcDist < lessDist) {
                t = priorT
            } else {
                val nextT = t + lastStep
                if (nextT > max) return -1.0
                val morePt = ptAtT(nextT)
                if (approximately_equal_half(morePt.x, cubicAtT.x)
                    && approximately_equal_half(morePt.y, cubicAtT.y)
                ) return -1.0
                val morePos = if (xAxis == SearchAxis.kXAxis) morePt.x else morePt.y
                val moreDist = morePos - axisIntercept
                if (if (calcDist > 0) calcDist <= moreDist else calcDist >= moreDist) continue
                t = nextT
            }
            cubicAtT = ptAtT(t)
            calcPos = if (xAxis == SearchAxis.kXAxis) cubicAtT.x else cubicAtT.y
            calcDist = calcPos - axisIntercept
        } while (!approximately_equal(calcPos, axisIntercept))
        return t
    }

    /**
     * Combine the cubic's extrema (passed in) + inflections + the
     * `[0, 1]` bookends, then run [binarySearch] in each interval
     * looking for a curve point at [axisIntercept]. Up to 3 valid
     * roots are written to [validRoots]. Mirrors `SkDCubic::searchRoots`.
     *
     * @param extremeTs scratch buffer of length ≥ 6, pre-filled with
     *                  the [extrema] x-axis or y-axis extrema values.
     * @param extrema   number of extrema entries already in [extremeTs].
     */
    fun searchRoots(
        extremeTs: DoubleArray,
        extremaIn: Int,
        axisIntercept: Double,
        xAxis: SearchAxis,
        validRoots: DoubleArray,
    ): Int {
        var extrema = extremaIn
        // Append inflections.
        val inflectionScratch = DoubleArray(2)
        val nInf = findInflections(inflectionScratch)
        for (i in 0 until nInf) extremeTs[extrema++] = inflectionScratch[i]
        // Append bookend t-values 0 and 1.
        extremeTs[extrema++] = 0.0
        extremeTs[extrema] = 1.0
        require(extrema < 6)
        // Sort the (extrema + 1) entries — upstream uses SkTQSort.
        java.util.Arrays.sort(extremeTs, 0, extrema + 1)
        var validCount = 0
        var index = 0
        while (index < extrema) {
            val tMin = extremeTs[index]
            index++
            val tMax = extremeTs[index]
            if (tMin == tMax) continue
            val newT = binarySearch(tMin, tMax, axisIntercept, xAxis)
            if (newT >= 0) {
                if (validCount >= 3) return 0
                validRoots[validCount++] = newT
            }
        }
        return validCount
    }

    /**
     * Number of valid t-values (`0 < t < 1`) where this cubic crosses
     * `y = yIntercept`. Mirrors `SkDCubic::horizontalIntersect`.
     */
    fun horizontalIntersect(yIntercept: Double, roots: DoubleArray): Int {
        val A = DoubleArray(1); val B = DoubleArray(1); val C = DoubleArray(1); val D = DoubleArray(1)
        Coefficients(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y), A, B, C, D)
        D[0] -= yIntercept
        var count = RootsValidT(A[0], B[0], C[0], D[0], roots)
        // Numeric robustness fallback : if any root doesn't actually
        // produce a y close to yIntercept, fall back to searchRoots.
        for (index in 0 until count) {
            val calcPt = ptAtT(roots[index])
            if (!approximately_equal(calcPt.y, yIntercept)) {
                val extremeTs = DoubleArray(6)
                val extrema = FindExtrema(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y, pts[3].y), extremeTs)
                count = searchRoots(extremeTs, extrema, yIntercept, SearchAxis.kYAxis, roots)
                break
            }
        }
        return count
    }

    /** Vertical analogue of [horizontalIntersect]. Mirrors `SkDCubic::verticalIntersect`. */
    fun verticalIntersect(xIntercept: Double, roots: DoubleArray): Int {
        val A = DoubleArray(1); val B = DoubleArray(1); val C = DoubleArray(1); val D = DoubleArray(1)
        Coefficients(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x), A, B, C, D)
        D[0] -= xIntercept
        var count = RootsValidT(A[0], B[0], C[0], D[0], roots)
        for (index in 0 until count) {
            val calcPt = ptAtT(roots[index])
            if (!approximately_equal(calcPt.x, xIntercept)) {
                val extremeTs = DoubleArray(6)
                val extrema = FindExtrema(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x, pts[3].x), extremeTs)
                count = searchRoots(extremeTs, extrema, xIntercept, SearchAxis.kXAxis, roots)
                break
            }
        }
        return count
    }

    /**
     * Project [xy] onto this cubic via a perpendicular ray (rotated
     * chord direction). Returns the curve t-value if `xy` is "near"
     * the curve (within ULPs tolerance scaled by the curve's
     * coordinate range), or `-1` if not. Mirrors
     * `SkDCurve::nearPoint(SkPath::kCubic_Verb, ...)`. Used by
     * [LineCubicIntersections.addLineNearEndPoints].
     */
    fun nearPoint(xy: SkDPoint, opp: SkDPoint): Double {
        var minX = pts[0].x; var maxX = minX
        var minY = pts[0].y; var maxY = minY
        for (i in 1 until kPointCount) {
            minX = minOf(minX, pts[i].x); maxX = maxOf(maxX, pts[i].x)
            minY = minOf(minY, pts[i].y); maxY = maxOf(maxY, pts[i].y)
        }
        if (!AlmostBetweenUlps(minX, xy.x, maxX)) return -1.0
        if (!AlmostBetweenUlps(minY, xy.y, maxY)) return -1.0
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

    /**
     * Returns true if the cubic from index [startIndex] to [endIndex]
     * is approximately linear (both interior controls within ULPs
     * tolerance of the chord through the chosen endpoints). Mirrors
     * `SkDCubic::isLinear(int startIndex, int endIndex)`.
     *
     * Edge case : if `pts[0] ≈ pts[3]` (closed loop), defer to the
     * inner-quad linearity check. Phase D1.1.c — wired to
     * [SkLineParameters].
     */
    fun isLinear(startIndex: Int, endIndex: Int): Boolean {
        if (pts[0].approximatelyDEqual(pts[3])) {
            // Upstream casts `(SkDQuad*) this` to reinterpret pts[0..2].
            // We construct an explicit quad for type safety.
            return SkDQuad(arrayOf(pts[0], pts[1], pts[2])).isLinear(0, 2)
        }
        val params = SkLineParameters()
        params.cubicEndPoints(this, startIndex, endIndex)
        params.normalize()
        var tiniest = pts[0].x
        for (p in pts) {
            tiniest = minOf(tiniest, p.x); tiniest = minOf(tiniest, p.y)
        }
        var largest = pts[0].x
        for (p in pts) {
            largest = maxOf(largest, p.x); largest = maxOf(largest, p.y)
        }
        largest = maxOf(largest, -tiniest)
        var distance = params.controlPtDistance(this, 1)
        if (!approximately_zero_when_compared_to(distance, largest)) return false
        distance = params.controlPtDistance(this, 2)
        return approximately_zero_when_compared_to(distance, largest)
    }

    /**
     * "Pinned" sub-divide variant — given pinned endpoints [a] (≈
     * value at [t1]) and [d] (≈ value at [t2]), recover the two
     * interior control points of the sub-cubic into [dst] (length 2).
     * Mirrors `SkDCubic::subDivide(SkDPoint, SkDPoint, double, double, SkDPoint[2])`.
     *
     * Algorithm assumes the directly-computed sub-divided cubic's
     * controls are accurate enough as a starting point ; the pinned
     * endpoints are used only for endpoint snap (`align` + ULPs-equal).
     */
    fun subDivide(a: SkDPoint, d: SkDPoint, t1: Double, t2: Double, dst: Array<SkDPoint>) {
        require(t1 != t2)
        require(dst.size >= 2)
        val sub = subDivide(t1, t2)
        dst[0] = sub[1] + (a - sub[0])
        dst[1] = sub[2] + (d - sub[3])
        if (t1 == 0.0 || t2 == 0.0) align(0, 1, if (t1 == 0.0) dst[0] else dst[1])
        if (t1 == 1.0 || t2 == 1.0) align(3, 2, if (t1 == 1.0) dst[0] else dst[1])
        if (AlmostBequalUlps(dst[0].x, a.x)) dst[0].x = a.x
        if (AlmostBequalUlps(dst[0].y, a.y)) dst[0].y = a.y
        if (AlmostBequalUlps(dst[1].x, d.x)) dst[1].x = d.x
        if (AlmostBequalUlps(dst[1].y, d.y)) dst[1].y = d.y
    }

    // ─── Equality ────────────────────────────────────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkDCubic) return false
        return pts[0] == other.pts[0] && pts[1] == other.pts[1]
            && pts[2] == other.pts[2] && pts[3] == other.pts[3]
    }

    override fun hashCode(): Int {
        var h = pts[0].hashCode()
        h = 31 * h + pts[1].hashCode()
        h = 31 * h + pts[2].hashCode()
        h = 31 * h + pts[3].hashCode()
        return h
    }

    companion object {
        const val kPointCount = 4
        const val kPointLast = kPointCount - 1
        const val kMaxIntersections = 9

        fun pointCount(): Int = kPointCount
        fun pointLast(): Int = kPointLast
        fun maxIntersections(): Int = kMaxIntersections

        /** Mirrors `SkDCubic::IsConic` — cubic is not a conic. */
        fun IsConic(): Boolean = false

        /** Default cubic precision unit (256). Mirrors `SkDCubic::gPrecisionUnit`. */
        const val gPrecisionUnit: Double = 256.0

        /**
         * Convert cubic Bézier control points to monomial coefficients.
         * Result : `f(t) = A*t³ + B*t² + C*t + D`. Mirrors
         * `SkDCubic::Coefficients`.
         *
         * Stride-1 input layout `src[0..3]` (one axis only). Upstream
         * uses stride-2 indices `src[0/2/4/6]`.
         */
        fun Coefficients(src: DoubleArray, A: DoubleArray, B: DoubleArray, C: DoubleArray, D: DoubleArray) {
            A[0] = src[3]
            B[0] = src[2] * 3
            C[0] = src[1] * 3
            D[0] = src[0]
            A[0] -= D[0] - C[0] + B[0]
            B[0] += 3 * D[0] - 2 * C[0]
            C[0] -= 3 * D[0]
        }

        /**
         * Static analogue of [findInflections] taking single-precision
         * input. Mirrors `SkDCubic::FindInflections`.
         */
        fun FindInflections(a: Array<SkPoint>, tValues: DoubleArray): Int {
            require(a.size >= kPointCount)
            val cubic = SkDCubic().set(a[0], a[1], a[2], a[3])
            return cubic.findInflections(tValues)
        }

        /**
         * Find the two interior extrema of `f(t) = src[0..3]` on a
         * single axis. Mirrors `SkDCubic::FindExtrema`.
         *
         * Stride-1 input layout (upstream uses stride-2 `src[0/2/4/6]`).
         */
        fun FindExtrema(src: DoubleArray, tValues: DoubleArray): Int {
            val a = src[0]; val b = src[1]; val c = src[2]; val d = src[3]
            val A = d - a + 3 * (b - c)
            val B = 2 * (a - b - b + c)
            val C = b - a
            return SkDQuad.RootsValidT(A, B, C, tValues)
        }

        /**
         * Real-root finder for `A*t³ + B*t² + C*t + D = 0`. Mirrors
         * `SkDCubic::RootsReal` — uses the Cardano / trigonometric
         * formulation (3-real-roots branch via `acos` / `cos`,
         * 1-real-root branch via cube root).
         */
        fun RootsReal(Ain: Double, Bin: Double, C: Double, D: Double, s: DoubleArray): Int {
            var A = Ain; var B = Bin
            // Degenerate to quadratic when A is small relative to other coefficients.
            if (approximately_zero(A)
                && approximately_zero_when_compared_to(A, B)
                && approximately_zero_when_compared_to(A, C)
                && approximately_zero_when_compared_to(A, D)
            ) return SkDQuad.RootsReal(B, C, D, s)
            // 0 is one root if D is small relative to A/B/C.
            if (approximately_zero_when_compared_to(D, A)
                && approximately_zero_when_compared_to(D, B)
                && approximately_zero_when_compared_to(D, C)
            ) {
                val num = SkDQuad.RootsReal(A, B, C, s)
                for (i in 0 until num) if (approximately_zero(s[i])) return num
                s[num] = 0.0
                return num + 1
            }
            // 1 is one root if A+B+C+D ≈ 0.
            if (approximately_zero(A + B + C + D)) {
                val num = SkDQuad.RootsReal(A, A + B, -D, s)
                for (i in 0 until num) if (AlmostDequalUlps(s[i], 1.0)) return num
                s[num] = 1.0
                return num + 1
            }
            val invA = 1 / A
            val a = B * invA
            val b = C * invA
            val c = D * invA
            val a2 = a * a
            val Q = (a2 - b * 3) / 9
            val R = (2 * a2 * a - 9 * a * b + 27 * c) / 54
            val R2 = R * R
            val Q3 = Q * Q * Q
            val R2MinusQ3 = R2 - Q3
            val adiv3 = a / 3
            var idx = 0
            if (R2MinusQ3 < 0) {
                // 3 real roots
                val theta = acos(skTPin(R / sqrt(Q3), -1.0, 1.0))
                val neg2RootQ = -2 * sqrt(Q)
                val r0 = neg2RootQ * cos(theta / 3) - adiv3
                s[idx++] = r0
                val r1 = neg2RootQ * cos((theta + 2 * PI) / 3) - adiv3
                if (!AlmostDequalUlps(s[0], r1)) s[idx++] = r1
                val r2 = neg2RootQ * cos((theta - 2 * PI) / 3) - adiv3
                if (!AlmostDequalUlps(s[0], r2) && (idx == 1 || !AlmostDequalUlps(s[1], r2))) {
                    s[idx++] = r2
                }
            } else {
                // 1 real root
                val sqrtR2MinusQ3 = sqrt(R2MinusQ3)
                var Acu = kotlin.math.abs(R) + sqrtR2MinusQ3
                Acu = cbrt(Acu)
                if (R > 0) Acu = -Acu
                if (Acu != 0.0) Acu += Q / Acu
                val r = Acu - adiv3
                s[idx++] = r
                if (AlmostDequalUlps(R2, Q3)) {
                    val r2 = -Acu / 2 - adiv3
                    if (!AlmostDequalUlps(s[0], r2)) s[idx++] = r2
                }
            }
            return idx
        }

        /**
         * Returns the number of valid roots of the cubic in `[0, 1]`,
         * with end-clamping for roots barely outside the interval
         * (matches the upstream `between(1, t, 1.00005)` slack).
         * Mirrors `SkDCubic::RootsValidT`.
         */
        fun RootsValidT(A: Double, B: Double, C: Double, D: Double, t: DoubleArray): Int {
            val s = DoubleArray(3)
            val real = RootsReal(A, B, C, D, s)
            var foundRoots = SkDQuad.AddValidTs(s, real, t)
            for (index in 0 until real) {
                val tValue = s[index]
                if (!approximately_one_or_less(tValue) && between(1.0, tValue, 1.00005)) {
                    var dup = false
                    for (i2 in 0 until foundRoots) if (approximately_equal(t[i2], 1.0)) { dup = true; break }
                    if (!dup && foundRoots < 3) t[foundRoots++] = 1.0
                } else if (!approximately_zero_or_more(tValue) && between(-0.00005, tValue, 0.0)) {
                    var dup = false
                    for (i2 in 0 until foundRoots) if (approximately_equal(t[i2], 0.0)) { dup = true; break }
                    if (!dup && foundRoots < 3) t[foundRoots++] = 0.0
                }
            }
            return foundRoots
        }

        // ─── Internal helpers ────────────────────────────────────────

        /**
         * `c'(t) = 3*((b-a)(1-t)² + 2(c-b)t(1-t) + (d-c)t²)`. Used by
         * [dxdyAtT].
         */
        private fun derivativeAtT(src: DoubleArray, t: Double): Double {
            val oneT = 1 - t
            val a = src[0]; val b = src[1]; val c = src[2]; val d = src[3]
            return 3 * ((b - a) * oneT * oneT + 2 * (c - b) * t * oneT + (d - c) * t * t)
        }

        /**
         * `F' dot F''` polynomial coefficients for max-curvature
         * search. Mirrors `formulate_F1DotF2` static helper in the
         * upstream `.cpp` (stride-1 here vs stride-2 there).
         */
        private fun formulateF1DotF2(src: DoubleArray, coeff: DoubleArray) {
            val a = src[1] - src[0]
            val b = src[2] - 2 * src[1] + src[0]
            val c = src[3] + 3 * (src[1] - src[2]) - src[0]
            coeff[0] = c * c
            coeff[1] = 3 * b * c
            coeff[2] = 2 * b * b + c * a
            coeff[3] = a * b
        }

        /** de-Casteljau coordinate eval (single axis) at parameter [t]. */
        private fun interpCubicCoords(src: DoubleArray, t: Double): Double {
            val ab = SkDInterp(src[0], src[1], t)
            val bc = SkDInterp(src[1], src[2], t)
            val cd = SkDInterp(src[2], src[3], t)
            val abc = SkDInterp(ab, bc, t)
            val bcd = SkDInterp(bc, cd, t)
            return SkDInterp(abc, bcd, t)
        }

        private fun chopAxis(src: DoubleArray, t: Double, write: (Int, Double) -> Unit) {
            val ab = SkDInterp(src[0], src[1], t)
            val bc = SkDInterp(src[1], src[2], t)
            val cd = SkDInterp(src[2], src[3], t)
            val abc = SkDInterp(ab, bc, t)
            val bcd = SkDInterp(bc, cd, t)
            val abcd = SkDInterp(abc, bcd, t)
            write(0, src[0])
            write(1, ab)
            write(2, abc)
            write(3, abcd)
            write(4, bcd)
            write(5, cd)
            write(6, src[3])
        }

        private fun skTPin(value: Double, lo: Double, hi: Double): Double =
            if (value < lo) lo else if (value > hi) hi else value

        // ─── Hull helpers (Phase D1.1.e.1) ──────────────────────

        /**
         * Given two indices in `{0, 1, 2, 3}`, return an XOR mask that
         * identifies the other two : `mask == 2` for {0,3}/{1,2} pairs,
         * `mask == 3` for adjacent pairs. Mirrors `inline int
         * other_two(int, int)` in `SkPathOpsCubic.h`.
         */
        internal fun otherTwo(one: Int, two: Int): Int =
            (1 shr (3 - (one xor two))) xor 3

        /** Returns 0 if negative, 1 if zero, 2 if positive. Mirrors `side` in `SkOpCubicHull.cpp`. */
        private fun sideSign(x: Double): Int = (if (x > 0) 1 else 0) + (if (x >= 0) 1 else 0)
    }

    /**
     * Rotate this cubic so the line `(zero, index)` aligns with the
     * x-axis ; write the rotated cubic into [rotPath]. Returns false
     * if `pts[zero]` and `pts[index]` are coincident on both axes.
     * Mirrors the static `rotate` helper in `SkOpCubicHull.cpp`.
     */
    private fun rotateForHull(zero: Int, index: Int, rotPath: SkDCubic): Boolean {
        val dy = pts[index].y - pts[zero].y
        val dx = pts[index].x - pts[zero].x
        if (approximately_zero(dy)) {
            if (approximately_zero(dx)) return false
            for (i in 0 until 4) {
                rotPath[i] = SkDPoint(pts[i].x, pts[i].y)
            }
            if (dy != 0.0) {
                rotPath[index].y = pts[zero].y
                val mask = otherTwo(index, zero)
                val side1 = index xor mask
                val side2 = zero xor mask
                if (approximately_equal(pts[side1].y, pts[zero].y)) rotPath[side1].y = pts[zero].y
                if (approximately_equal(pts[side2].y, pts[zero].y)) rotPath[side2].y = pts[zero].y
            }
            return true
        }
        for (i in 0 until 4) {
            rotPath[i] = SkDPoint(
                pts[i].x * dx + pts[i].y * dy,
                pts[i].y * dx - pts[i].x * dy,
            )
        }
        return true
    }
}
