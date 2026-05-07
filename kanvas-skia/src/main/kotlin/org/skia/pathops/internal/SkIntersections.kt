/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkIntersections.{h,cpp}` — the result
 * container shared by every curve-curve / curve-line / line-line
 * intersection routine in pathops. Stores up to 13 (t, t', point)
 * triples sorted by `t` (the curve-1 parameter), plus per-side
 * coincidence bitmasks and a "nearly same" alternate-point pair.
 *
 * Phase D1.1.c — container + line-line intersection methods :
 *   - intersect(SkDLine, SkDLine) / intersectRay(SkDLine, SkDLine)
 *   - horizontal(SkDLine) / vertical(SkDLine) (axis-aligned line crossings)
 *   - HorizontalIntercept / VerticalIntercept static helpers
 *   - lineLine / lineHorizontal / lineVertical (SkPoint factory wrappers)
 *
 * Deferred to subsequent slices :
 *   - intersect(quad/cubic/conic, line) → D1.1.d
 *   - intersect(curve, curve) and intersectRay(curve, line) → D1.1.e
 *   - cleanUpCoincidence / cubicInsert / alignQuadPts → D1.1.e (TSect helpers)
 *
 * Stub methods for the deferred intersections throw
 * [NotImplementedError] so calls fail loudly rather than silently
 * returning zero.
 */
package org.skia.pathops.internal

import kotlin.math.abs
import org.skia.math.SkPoint

internal class SkIntersections {

    // ─── Storage ────────────────────────────────────────────────────

    private val fT: Array<DoubleArray> = Array(2) { DoubleArray(13) }
    private val fPt: Array<SkDPoint> = Array(13) { SkDPoint() }
    private val fPt2: Array<SkDPoint> = Array(2) { SkDPoint() }
    private val fIsCoincident: IntArray = IntArray(2) // 16-bit bitfields per curve
    private val fNearlySame: BooleanArray = BooleanArray(2)
    private var fUsed: Int = 0
    private var fMax: Int = 0
    private var fAllowNear: Boolean = true
    private var fSwap: Boolean = false
    private var fDepth: Int = 0

    init { reset() }

    // ─── Accessors ──────────────────────────────────────────────────

    fun used(): Int = fUsed

    /** Get the t-value of curve [side] (0 or 1) at intersection [index]. */
    fun t(side: Int, index: Int): Double = fT[side][index]

    fun pt(index: Int): SkDPoint = fPt[index]
    fun pt2(index: Int): SkDPoint = fPt2[index]
    fun nearlySame(index: Int): Boolean = fNearlySame[index]

    fun isCoincident(index: Int): Boolean = (fIsCoincident[0] and (1 shl index)) != 0

    fun setCoincident(index: Int) {
        require(index >= 0)
        val bit = 1 shl index
        fIsCoincident[0] = fIsCoincident[0] or bit
        fIsCoincident[1] = fIsCoincident[1] or bit
    }

    fun clearCoincidence(index: Int) {
        require(index >= 0)
        val bit = 1 shl index
        fIsCoincident[0] = fIsCoincident[0] and bit.inv()
        fIsCoincident[1] = fIsCoincident[1] and bit.inv()
    }

    fun allowNear(nearAllowed: Boolean) { fAllowNear = nearAllowed }
    fun setMax(max: Int) { require(max <= fPt.size); fMax = max }
    fun swap() { fSwap = !fSwap }
    fun swapped(): Boolean = fSwap
    fun set(swap: Boolean, tIndex: Int, t: Double) { fT[if (swap) 1 else 0][tIndex] = t }

    /** Mirrors `hasT(double)` ; pre-condition `t == 0 || t == 1`. */
    fun hasT(t: Double): Boolean {
        require(t == 0.0 || t == 1.0)
        return fUsed > 0 && (if (t == 0.0) fT[0][0] == 0.0 else fT[0][fUsed - 1] == 1.0)
    }

    fun hasOppT(t: Double): Boolean {
        require(t == 0.0 || t == 1.0)
        return fUsed > 0 && (fT[1][0] == t || fT[1][fUsed - 1] == t)
    }

    fun depth(): Int = fDepth
    fun upDepth() { require(++fDepth < 16) }
    fun downDepth() { require(--fDepth >= 0) }

    // ─── Mutation ───────────────────────────────────────────────────

    /** Reset the intersection list (preserves `fSwap` and `fMax`). */
    fun reset() {
        fAllowNear = true
        fUsed = 0
        fIsCoincident[0] = 0
        fIsCoincident[1] = 0
    }

    fun flip() {
        for (index in 0 until fUsed) fT[1][index] = 1 - fT[1][index]
    }

    /**
     * Insert a new (t1, t2, pt) triple into the sorted list.
     * Mirrors `SkIntersections::insert`. Returns the inserted index,
     * or -1 if the entry was rejected (coincidence overlap, exact
     * duplicate, near-duplicate that displaces an existing entry).
     */
    fun insert(one: Double, two: Double, pt: SkDPoint): Int {
        // Disallow mixing coincident + non-coincident on the same curve.
        if (fIsCoincident[0] == 3 && between(fT[0][0], one, fT[0][1])) return -1
        var index = 0
        while (index < fUsed) {
            val oldOne = fT[0][index]; val oldTwo = fT[1][index]
            if (one == oldOne && two == oldTwo) return -1
            if (more_roughly_equal(oldOne, one) && more_roughly_equal(oldTwo, two)) {
                if ((!precisely_zero(one) || precisely_zero(oldOne))
                    && (!precisely_equal(one, 1.0) || precisely_equal(oldOne, 1.0))
                    && (!precisely_zero(two) || precisely_zero(oldTwo))
                    && (!precisely_equal(two, 1.0) || precisely_equal(oldTwo, 1.0))
                ) return -1
                // Remove the displaced entry — it'll be re-inserted below
                // in the correct sorted position.
                val remaining = fUsed - index - 1
                shiftDown(index, remaining)
                val clearMask = (((1 shl index) - 1)).inv()
                fIsCoincident[0] -= (fIsCoincident[0] ushr 1) and clearMask
                fIsCoincident[1] -= (fIsCoincident[1] ushr 1) and clearMask
                --fUsed
                break
            }
            ++index
        }
        // Find insert position
        index = 0
        while (index < fUsed) {
            if (fT[0][index] > one) break
            ++index
        }
        if (fUsed >= fMax) {
            fUsed = 0
            return 0
        }
        val remaining = fUsed - index
        if (remaining > 0) {
            shiftUp(index, remaining)
            val clearMask = (((1 shl index) - 1)).inv()
            fIsCoincident[0] += fIsCoincident[0] and clearMask
            fIsCoincident[1] += fIsCoincident[1] and clearMask
        }
        fPt[index] = pt
        if (one < 0 || one > 1) return -1
        if (two < 0 || two > 1) return -1
        fT[0][index] = one
        fT[1][index] = two
        ++fUsed
        return index
    }

    fun insertNear(one: Double, two: Double, pt1: SkDPoint, pt2: SkDPoint) {
        require(one == 0.0 || one == 1.0)
        require(two == 0.0 || two == 1.0)
        require(pt1 != pt2)
        fNearlySame[if (one != 0.0) 1 else 0] = true
        insert(one, two, pt1)
        fPt2[if (one != 0.0) 1 else 0] = pt2
    }

    fun insertCoincident(one: Double, two: Double, pt: SkDPoint): Int {
        val index = insertSwap(one, two, pt)
        if (index >= 0) setCoincident(index)
        return index
    }

    fun insertSwap(one: Double, two: Double, pt: SkDPoint): Int =
        if (fSwap) insert(two, one, pt) else insert(one, two, pt)

    fun removeOne(index: Int) {
        --fUsed
        val remaining = fUsed - index
        if (remaining <= 0) return
        shiftDown(index, remaining)
        val coBit = fIsCoincident[0] and (1 shl index)
        val clearMask = (((1 shl index) - 1)).inv()
        fIsCoincident[0] -= ((fIsCoincident[0] ushr 1) and clearMask) + coBit
        fIsCoincident[1] -= ((fIsCoincident[1] ushr 1) and clearMask) + coBit
    }

    fun merge(a: SkIntersections, aIndex: Int, b: SkIntersections, bIndex: Int) {
        reset()
        fT[0][0] = a.fT[0][aIndex]
        fT[1][0] = b.fT[0][bIndex]
        fPt[0] = a.fPt[aIndex]
        fPt2[0] = b.fPt[bIndex]
        fUsed = 1
    }

    /**
     * Of the intersections whose curve-1 t lies in `[rangeStart, rangeEnd]`,
     * return the index closest to [testPt] (squared distance), and write
     * the squared distance to [closestDist] (size-1 out array). Returns
     * -1 if no entry is in range.
     */
    fun closestTo(rangeStart: Double, rangeEnd: Double, testPt: SkDPoint, closestDist: DoubleArray): Int {
        require(closestDist.size >= 1)
        var closest = -1
        closestDist[0] = Double.MAX_VALUE
        for (index in 0 until fUsed) {
            if (!between(rangeStart, fT[0][index], rangeEnd)) continue
            val dist = testPt.distanceSquared(fPt[index])
            if (closestDist[0] > dist) {
                closestDist[0] = dist
                closest = index
            }
        }
        return closest
    }

    /**
     * Find the entry whose point lies "most outside" [origin] (largest
     * counter-clockwise angle from the current "best") within the given
     * t-range. Mirrors `SkIntersections::mostOutside`.
     */
    fun mostOutside(rangeStart: Double, rangeEnd: Double, origin: SkDPoint): Int {
        var result = -1
        for (index in 0 until fUsed) {
            if (!between(rangeStart, fT[0][index], rangeEnd)) continue
            if (result < 0) { result = index; continue }
            val best = fPt[result] - origin
            val test = fPt[index] - origin
            if (test.crossCheck(best) < 0) result = index
        }
        return result
    }

    /**
     * Shift `(t1[index], t2[index]) ± BUMP_EPSILON` outward and remove
     * the entry if it falls outside `[0, 1]`. Mirrors `unBumpT`.
     */
    fun unBumpT(index: Int): Boolean {
        require(fUsed == 1)
        fT[0][index] = fT[0][index] * (1 + BUMP_EPSILON * 2) - BUMP_EPSILON
        if (!between(0.0, fT[0][index], 1.0)) {
            fUsed = 0
            return false
        }
        return true
    }

    // ─── Line-Line intersection ─────────────────────────────────────

    /**
     * Compute the (single, infinite-line) ray intersection of [a] and [b].
     * Mirrors `SkIntersections::intersectRay(SkDLine, SkDLine)`.
     *
     * Returns 1 if the rays intersect at a single point, 2 if the rays
     * are coincident, 0 if parallel and offset.
     */
    fun intersectRay(a: SkDLine, b: SkDLine): Int {
        fMax = 2
        val aLen = a[1] - a[0]
        val bLen = b[1] - b[0]
        val denom = bLen.y * aLen.x - aLen.y * bLen.x
        val used: Int
        if (!approximately_zero(denom)) {
            val ab0 = a[0] - b[0]
            val numerA = ab0.y * bLen.x - bLen.y * ab0.x
            val numerB = ab0.y * aLen.x - aLen.y * ab0.x
            fT[0][0] = numerA / denom
            fT[1][0] = numerB / denom
            used = 1
        } else {
            // axis intercept match check
            if (!AlmostEqualUlps(
                    aLen.x * a[0].y - aLen.y * a[0].x,
                    aLen.x * b[0].y - aLen.y * b[0].x,
                )
            ) {
                fUsed = 0
                return 0
            }
            // No great answer for coincident rays — return endpoints.
            fT[0][0] = 0.0; fT[0][1] = 0.0
            fT[1][0] = 0.0; fT[1][1] = 1.0
            used = 2
        }
        computePoints(a, used)
        return fUsed
    }

    /**
     * Line-line intersection. Mirrors `SkIntersections::intersect(SkDLine,
     * SkDLine)`. Returns 0, 1, or 2.
     */
    fun intersect(a: SkDLine, b: SkDLine): Int {
        fMax = 3 // cleaned up to ≤2 at end
        // End-point matches first
        for (iA in 0..1) {
            val t = b.exactPoint(a[iA])
            if (t >= 0) insert(iA.toDouble(), t, a[iA])
        }
        for (iB in 0..1) {
            val t = a.exactPoint(b[iB])
            if (t >= 0) insert(t, iB.toDouble(), b[iB])
        }
        val axLen = a[1].x - a[0].x
        val ayLen = a[1].y - a[0].y
        val bxLen = b[1].x - b[0].x
        val byLen = b[1].y - b[0].y
        val axByLen = axLen * byLen
        val ayBxLen = ayLen * bxLen
        val unparallel = if (fAllowNear) NotAlmostEqualUlpsPin(axByLen, ayBxLen)
            else NotAlmostDequalUlps(axByLen, ayBxLen)
        if (unparallel && fUsed == 0) {
            val ab0y = a[0].y - b[0].y
            val ab0x = a[0].x - b[0].x
            val numerA = ab0y * bxLen - byLen * ab0x
            val numerB = ab0y * axLen - ayLen * ab0x
            val denom = axByLen - ayBxLen
            if (between(0.0, numerA, denom) && between(0.0, numerB, denom)) {
                fT[0][0] = numerA / denom
                fT[1][0] = numerB / denom
                computePoints(a, 1)
            }
        }
        if (fAllowNear || !unparallel) {
            val aNearB = doubleArrayOf(0.0, 0.0)
            val bNearA = doubleArrayOf(0.0, 0.0)
            val aNotB = booleanArrayOf(false, false)
            val bNotA = booleanArrayOf(false, false)
            var nearCount = 0
            for (index in 0..1) {
                val flag = booleanArrayOf(false)
                aNearB[index] = b.nearPoint(a[index], flag); aNotB[index] = flag[0]
                if (aNearB[index] >= 0) nearCount++
                val flag2 = booleanArrayOf(false)
                bNearA[index] = a.nearPoint(b[index], flag2); bNotA[index] = flag2[0]
                if (bNearA[index] >= 0) nearCount++
            }
            if (nearCount > 0) {
                if (nearCount != 2 || aNotB[0] == aNotB[1]) {
                    for (iA in 0..1) {
                        if (!aNotB[iA]) continue
                        val nearer = if (aNearB[iA] > 0.5) 1 else 0
                        if (!bNotA[nearer]) continue
                        require(a[iA] != b[nearer])
                        insertNear(iA.toDouble(), nearer.toDouble(), a[iA], b[nearer])
                        aNearB[iA] = -1.0
                        bNearA[nearer] = -1.0
                        nearCount -= 2
                    }
                }
                if (nearCount > 0) {
                    for (iA in 0..1) {
                        if (aNearB[iA] >= 0) insert(iA.toDouble(), aNearB[iA], a[iA])
                    }
                    for (iB in 0..1) {
                        if (bNearA[iB] >= 0) insert(bNearA[iB], iB.toDouble(), b[iB])
                    }
                }
            }
        }
        cleanUpParallelLines(!unparallel)
        return fUsed
    }

    // ─── Line vs axis-aligned line crossings ────────────────────────

    /**
     * Mirrors `SkIntersections::horizontal(SkDLine, double left,
     * double right, double y, bool flipped)` — line crossings of a
     * horizontal segment.
     */
    fun horizontal(line: SkDLine, left: Double, right: Double, y: Double, flipped: Boolean): Int {
        fMax = 3
        val leftPt = SkDPoint(left, y)
        run {
            val t = line.exactPoint(leftPt)
            if (t >= 0) insert(t, if (flipped) 1.0 else 0.0, leftPt)
        }
        if (left != right) {
            val rightPt = SkDPoint(right, y)
            val t = line.exactPoint(rightPt)
            if (t >= 0) insert(t, if (flipped) 0.0 else 1.0, rightPt)
            for (index in 0..1) {
                val t2 = SkDLine.ExactPointH(line[index], left, right, y)
                if (t2 >= 0) insert(index.toDouble(), if (flipped) 1 - t2 else t2, line[index])
            }
        }
        val result = horizontalCoincident(line, y)
        if (result == 1 && fUsed == 0) {
            fT[0][0] = HorizontalIntercept(line, y)
            val xIntercept = line[0].x + fT[0][0] * (line[1].x - line[0].x)
            if (between(left, xIntercept, right)) {
                fT[1][0] = (xIntercept - left) / (right - left)
                if (flipped) {
                    for (index in 0 until result) fT[1][index] = 1 - fT[1][index]
                }
                fPt[0] = SkDPoint(xIntercept, y)
                fUsed = 1
            }
        }
        if (fAllowNear || result == 2) {
            run {
                val t = line.nearPoint(leftPt, null)
                if (t >= 0) insert(t, if (flipped) 1.0 else 0.0, leftPt)
            }
            if (left != right) {
                val rightPt = SkDPoint(right, y)
                val t = line.nearPoint(rightPt, null)
                if (t >= 0) insert(t, if (flipped) 0.0 else 1.0, rightPt)
                for (index in 0..1) {
                    val t2 = SkDLine.NearPointH(line[index], left, right, y)
                    if (t2 >= 0) insert(index.toDouble(), if (flipped) 1 - t2 else t2, line[index])
                }
            }
        }
        cleanUpParallelLines(result == 2)
        return fUsed
    }

    /** Vertical analogue of [horizontal]. */
    fun vertical(line: SkDLine, top: Double, bottom: Double, x: Double, flipped: Boolean): Int {
        fMax = 3
        val topPt = SkDPoint(x, top)
        run {
            val t = line.exactPoint(topPt)
            if (t >= 0) insert(t, if (flipped) 1.0 else 0.0, topPt)
        }
        if (top != bottom) {
            val bottomPt = SkDPoint(x, bottom)
            val t = line.exactPoint(bottomPt)
            if (t >= 0) insert(t, if (flipped) 0.0 else 1.0, bottomPt)
            for (index in 0..1) {
                val t2 = SkDLine.ExactPointV(line[index], top, bottom, x)
                if (t2 >= 0) insert(index.toDouble(), if (flipped) 1 - t2 else t2, line[index])
            }
        }
        val result = verticalCoincident(line, x)
        if (result == 1 && fUsed == 0) {
            fT[0][0] = VerticalIntercept(line, x)
            val yIntercept = line[0].y + fT[0][0] * (line[1].y - line[0].y)
            if (between(top, yIntercept, bottom)) {
                fT[1][0] = (yIntercept - top) / (bottom - top)
                if (flipped) {
                    for (index in 0 until result) fT[1][index] = 1 - fT[1][index]
                }
                fPt[0] = SkDPoint(x, yIntercept)
                fUsed = 1
            }
        }
        if (fAllowNear || result == 2) {
            run {
                val t = line.nearPoint(topPt, null)
                if (t >= 0) insert(t, if (flipped) 1.0 else 0.0, topPt)
            }
            if (top != bottom) {
                val bottomPt = SkDPoint(x, bottom)
                val t = line.nearPoint(bottomPt, null)
                if (t >= 0) insert(t, if (flipped) 0.0 else 1.0, bottomPt)
                for (index in 0..1) {
                    val t2 = SkDLine.NearPointV(line[index], top, bottom, x)
                    if (t2 >= 0) insert(index.toDouble(), if (flipped) 1 - t2 else t2, line[index])
                }
            }
        }
        cleanUpParallelLines(result == 2)
        return fUsed
    }

    // ─── SkPoint façade methods ─────────────────────────────────────

    fun lineLine(a: Array<SkPoint>, b: Array<SkPoint>): Int {
        require(a.size >= 2 && b.size >= 2)
        val aLine = SkDLine().set(a[0], a[1])
        val bLine = SkDLine().set(b[0], b[1])
        fMax = 2
        return intersect(aLine, bLine)
    }

    fun lineHorizontal(a: Array<SkPoint>, left: Float, right: Float, y: Float, flipped: Boolean): Int {
        require(a.size >= 2)
        val line = SkDLine().set(a[0], a[1])
        fMax = 2
        return horizontal(line, left.toDouble(), right.toDouble(), y.toDouble(), flipped)
    }

    fun lineVertical(a: Array<SkPoint>, top: Float, bottom: Float, x: Float, flipped: Boolean): Int {
        require(a.size >= 2)
        val line = SkDLine().set(a[0], a[1])
        fMax = 2
        return vertical(line, top.toDouble(), bottom.toDouble(), x.toDouble(), flipped)
    }

    // ─── Coincidence cleanup ────────────────────────────────────────

    /**
     * Post-process : trim down to ≤2 entries and resolve the
     * coincidence bitmask. Mirrors `cleanUpParallelLines`.
     */
    fun cleanUpParallelLines(parallel: Boolean) {
        while (fUsed > 2) removeOne(1)
        if (fUsed == 2 && !parallel) {
            val startMatch = fT[0][0] == 0.0 || zero_or_one(fT[1][0])
            val endMatch = fT[0][1] == 1.0 || zero_or_one(fT[1][1])
            if ((!startMatch && !endMatch) || approximately_equal(fT[0][0], fT[0][1])) {
                require(startMatch || endMatch)
                if (startMatch && endMatch
                    && (fT[0][0] != 0.0 || !zero_or_one(fT[1][0]))
                    && fT[0][1] == 1.0 && zero_or_one(fT[1][1])
                ) removeOne(0)
                else removeOne(if (endMatch) 1 else 0)
            }
        }
        if (fUsed == 2) {
            fIsCoincident[0] = 0x03
            fIsCoincident[1] = 0x03
        }
    }

    fun computePoints(line: SkDLine, used: Int) {
        fPt[0] = line.ptAtT(fT[0][0])
        fUsed = used
        if (used == 2) fPt[1] = line.ptAtT(fT[0][1])
    }

    // ─── Curve intersection methods (D1.1.d.1 quad-line shipped) ────

    /** Mirrors `SkIntersections::intersect(SkDQuad, SkDLine)`. */
    fun intersect(quad: SkDQuad, line: SkDLine): Int {
        val helper = LineQuadraticIntersections(quad, line, this)
        helper.allowNear(fAllowNear)
        return helper.intersect()
    }

    /** Mirrors `SkIntersections::intersectRay(SkDQuad, SkDLine)`. */
    fun intersectRay(quad: SkDQuad, line: SkDLine): Int {
        val helper = LineQuadraticIntersections(quad, line, this)
        val roots = DoubleArray(2)
        val used = helper.intersectRay(roots)
        fUsed = used
        for (index in 0 until used) {
            fT[0][index] = roots[index]
            fPt[index] = quad.ptAtT(roots[index])
        }
        return used
    }

    /** Mirrors `SkIntersections::horizontal(SkDQuad, double, double, double, bool)`. */
    fun horizontal(quad: SkDQuad, left: Double, right: Double, y: Double, flipped: Boolean): Int {
        val line = SkDLine(arrayOf(SkDPoint(left, y), SkDPoint(right, y)))
        val helper = LineQuadraticIntersections(quad, line, this)
        return helper.horizontalIntersect(y, left, right, flipped)
    }

    /** Mirrors `SkIntersections::vertical(SkDQuad, double, double, double, bool)`. */
    fun vertical(quad: SkDQuad, top: Double, bottom: Double, x: Double, flipped: Boolean): Int {
        val line = SkDLine(arrayOf(SkDPoint(x, top), SkDPoint(x, bottom)))
        val helper = LineQuadraticIntersections(quad, line, this)
        return helper.verticalIntersect(x, top, bottom, flipped)
    }

    /** Mirrors `SkIntersections::intersect(SkDCubic, SkDLine)`. */
    fun intersect(cubic: SkDCubic, line: SkDLine): Int {
        val helper = LineCubicIntersections(cubic, line, this)
        helper.allowNear(fAllowNear)
        return helper.intersect()
    }

    /** Mirrors `SkIntersections::intersectRay(SkDCubic, SkDLine)`. */
    fun intersectRay(cubic: SkDCubic, line: SkDLine): Int {
        val helper = LineCubicIntersections(cubic, line, this)
        val roots = DoubleArray(3)
        val used = helper.intersectRay(roots)
        fUsed = used
        for (index in 0 until used) {
            fT[0][index] = roots[index]
            fPt[index] = cubic.ptAtT(roots[index])
        }
        return used
    }

    /** Mirrors `SkIntersections::horizontal(SkDCubic, double, double, double, bool)`. */
    fun horizontal(cubic: SkDCubic, left: Double, right: Double, y: Double, flipped: Boolean): Int {
        val line = SkDLine(arrayOf(SkDPoint(left, y), SkDPoint(right, y)))
        val helper = LineCubicIntersections(cubic, line, this)
        return helper.horizontalIntersect(y, left, right, flipped)
    }

    /** Mirrors `SkIntersections::vertical(SkDCubic, double, double, double, bool)`. */
    fun vertical(cubic: SkDCubic, top: Double, bottom: Double, x: Double, flipped: Boolean): Int {
        val line = SkDLine(arrayOf(SkDPoint(x, top), SkDPoint(x, bottom)))
        val helper = LineCubicIntersections(cubic, line, this)
        return helper.verticalIntersect(x, top, bottom, flipped)
    }

    /** Stub. Will land in D1.1.d.3. */
    fun intersect(conic: SkDConic, line: SkDLine): Int =
        throw NotImplementedError("intersect(SkDConic, SkDLine) lands in Phase D1.1.d.3")

    /** Stub. Will land in D1.1.e. */
    fun intersect(a: SkDQuad, b: SkDQuad): Int =
        throw NotImplementedError("intersect(SkDQuad, SkDQuad) lands in Phase D1.1.e")

    /** Stub. Will land in D1.1.d.3. */
    fun intersectRay(conic: SkDConic, line: SkDLine): Int =
        throw NotImplementedError("intersectRay(SkDConic, SkDLine) lands in Phase D1.1.d.3")

    // ─── SkPoint façade methods for SkDQuad ─────────────────────────

    /** Mirrors `SkIntersections::quadLine(SkPoint a[3], SkPoint b[2])`. */
    fun quadLine(a: Array<org.skia.math.SkPoint>, b: Array<org.skia.math.SkPoint>): Int {
        require(a.size >= 3 && b.size >= 2)
        val quad = SkDQuad().set(a[0], a[1], a[2])
        val line = SkDLine().set(b[0], b[1])
        return intersect(quad, line)
    }

    /** Mirrors `SkIntersections::quadHorizontal(SkPoint a[3], left, right, y, flipped)`. */
    fun quadHorizontal(a: Array<org.skia.math.SkPoint>, left: Float, right: Float, y: Float, flipped: Boolean): Int {
        require(a.size >= 3)
        val quad = SkDQuad().set(a[0], a[1], a[2])
        fMax = 2
        return horizontal(quad, left.toDouble(), right.toDouble(), y.toDouble(), flipped)
    }

    /** Mirrors `SkIntersections::quadVertical(SkPoint a[3], top, bottom, x, flipped)`. */
    fun quadVertical(a: Array<org.skia.math.SkPoint>, top: Float, bottom: Float, x: Float, flipped: Boolean): Int {
        require(a.size >= 3)
        val quad = SkDQuad().set(a[0], a[1], a[2])
        fMax = 2
        return vertical(quad, top.toDouble(), bottom.toDouble(), x.toDouble(), flipped)
    }

    // ─── SkPoint façade methods for SkDCubic ────────────────────────

    /** Mirrors `SkIntersections::cubicLine(SkPoint a[4], SkPoint b[2])`. */
    fun cubicLine(a: Array<org.skia.math.SkPoint>, b: Array<org.skia.math.SkPoint>): Int {
        require(a.size >= 4 && b.size >= 2)
        val cubic = SkDCubic().set(a[0], a[1], a[2], a[3])
        val line = SkDLine().set(b[0], b[1])
        fMax = 3
        return intersect(cubic, line)
    }

    /** Mirrors `SkIntersections::cubicHorizontal(SkPoint a[4], left, right, y, flipped)`. */
    fun cubicHorizontal(a: Array<org.skia.math.SkPoint>, left: Float, right: Float, y: Float, flipped: Boolean): Int {
        require(a.size >= 4)
        val cubic = SkDCubic().set(a[0], a[1], a[2], a[3])
        fMax = 3
        return horizontal(cubic, left.toDouble(), right.toDouble(), y.toDouble(), flipped)
    }

    /** Mirrors `SkIntersections::cubicVertical(SkPoint a[4], top, bottom, x, flipped)`. */
    fun cubicVertical(a: Array<org.skia.math.SkPoint>, top: Float, bottom: Float, x: Float, flipped: Boolean): Int {
        require(a.size >= 4)
        val cubic = SkDCubic().set(a[0], a[1], a[2], a[3])
        fMax = 3
        return vertical(cubic, top.toDouble(), bottom.toDouble(), x.toDouble(), flipped)
    }

    // ─── Internal helpers ───────────────────────────────────────────

    /**
     * `memmove(dst[index], src[index+1], remaining)` analogue — slide
     * each parallel array down by 1, used by [insert] / [removeOne].
     */
    private fun shiftDown(index: Int, remaining: Int) {
        if (remaining <= 0) return
        for (i in index until index + remaining) {
            fPt[i] = fPt[i + 1]
            fT[0][i] = fT[0][i + 1]
            fT[1][i] = fT[1][i + 1]
        }
    }

    /**
     * `memmove(dst[index+1], src[index], remaining)` — slide up to make
     * room for an insert at [index].
     */
    private fun shiftUp(index: Int, remaining: Int) {
        if (remaining <= 0) return
        for (i in (index + remaining) downTo (index + 1)) {
            fPt[i] = fPt[i - 1]
            fT[0][i] = fT[0][i - 1]
            fT[1][i] = fT[1][i - 1]
        }
    }

    companion object {
        /** Mirrors `SkIntersections::HorizontalIntercept(SkDLine, y)`. */
        fun HorizontalIntercept(line: SkDLine, y: Double): Double {
            require(line[1].y != line[0].y)
            return SkPinT((y - line[0].y) / (line[1].y - line[0].y))
        }

        /** Mirrors `SkIntersections::VerticalIntercept(SkDLine, x)`. */
        fun VerticalIntercept(line: SkDLine, x: Double): Double {
            require(line[1].x != line[0].x)
            return SkPinT((x - line[0].x) / (line[1].x - line[0].x))
        }

        /** Mirrors `SkIntersections::HorizontalIntercept(SkDQuad, SkScalar, double*)`. */
        fun HorizontalIntercept(quad: SkDQuad, y: Float, roots: DoubleArray): Int {
            // Direct quadratic root finder ; doesn't need a line argument.
            var D = quad[2].y; var E = quad[1].y; var F = quad[0].y
            D += F - 2 * E
            E -= F
            F -= y.toDouble()
            return SkDQuad.RootsValidT(D, 2 * E, F, roots)
        }

        /** Mirrors `SkIntersections::VerticalIntercept(SkDQuad, SkScalar, double*)`. */
        fun VerticalIntercept(quad: SkDQuad, x: Float, roots: DoubleArray): Int {
            var D = quad[2].x; var E = quad[1].x; var F = quad[0].x
            D += F - 2 * E
            E -= F
            F -= x.toDouble()
            return SkDQuad.RootsValidT(D, 2 * E, F, roots)
        }

        /**
         * Mirrors the static `horizontal_coincident` helper in
         * `SkDLineIntersection.cpp`. Returns 0 (no overlap), 1 (one
         * crossing), or 2 (line is coincident with the horizontal).
         */
        private fun horizontalCoincident(line: SkDLine, y: Double): Int {
            var min = line[0].y; var max = line[1].y
            if (min > max) { val tmp = min; min = max; max = tmp }
            if (min > y || max < y) return 0
            if (AlmostEqualUlps(min, max) && max - min < abs(line[0].x - line[1].x)) return 2
            return 1
        }

        private fun verticalCoincident(line: SkDLine, x: Double): Int {
            var min = line[0].x; var max = line[1].x
            if (min > max) { val tmp = min; min = max; max = tmp }
            if (!precisely_between(min, x, max)) return 0
            if (AlmostEqualUlps(min, max)) return 2
            return 1
        }
    }
}
