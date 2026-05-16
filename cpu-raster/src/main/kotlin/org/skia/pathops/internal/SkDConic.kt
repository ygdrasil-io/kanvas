/*
 * Copyright 2026 The kanvas-skia authors.
 *
 * Mirrors Skia's `src/pathops/SkPathOpsConic.{h,cpp}` — `SkDConic`,
 * the rational quadratic Bézier (with weight) used for arcs in the
 * pathops machinery.
 *
 * Phase D1.1.b — port of the data type + parametric helpers
 * (ptAtT / dxdyAtT / FindExtrema), subdivision (subDivide), and
 * the small forwarders to the inner [SkDQuad] (collapsed /
 * controlsInside / monotonicInX/Y / otherPts / align /
 * AddValidTs / RootsReal / RootsValidT).
 *
 * Deferred to subsequent slices :
 *  - `isLinear`               → needs SkLineParameters (D1.1.c).
 *  - `hullIntersects(SkDCubic)` → needs SkDCubic.hullIntersects (D1.1.d).
 *  - pinned `subDivide(a, c, t1, t2, weight)` → D1.1.c.
 */
package org.skia.pathops.internal


import org.graphiks.math.AlmostBetweenUlps
import org.graphiks.math.AlmostEqualUlpsPin
import org.graphiks.math.SkDLine
import org.graphiks.math.SkDPoint
import org.graphiks.math.SkDVector
import org.graphiks.math.SkPinT
import org.graphiks.math.zero_or_one
import kotlin.math.sqrt
import org.graphiks.math.SkPoint

/**
 * Double-precision rational quadratic Bézier with a single scalar
 * weight on the middle control point. Mirrors
 * [`SkDConic`](https://github.com/google/skia/blob/main/src/pathops/SkPathOpsConic.h#L26).
 */
internal data class SkDConic(
    val pts: SkDQuad = SkDQuad(),
    var weight: Float = 0f,
) {

    operator fun get(n: Int): SkDPoint = pts[n]
    operator fun set(n: Int, p: SkDPoint) { pts[n] = p }

    /** Mirrors `SkDConic::set(const SkPoint pts[3], SkScalar weight)`. */
    fun set(p0: SkPoint, p1: SkPoint, p2: SkPoint, w: Float): SkDConic {
        pts.set(p0, p1, p2)
        weight = w
        return this
    }

    // ─── Predicates / forwarders ─────────────────────────────────────

    fun collapsed(): Boolean = pts.collapsed()
    fun controlsInside(): Boolean = pts.controlsInside()
    fun monotonicInX(): Boolean = pts.monotonicInX()
    fun monotonicInY(): Boolean = pts.monotonicInY()
    fun otherPts(oddMan: Int, endPt: Array<SkDPoint?>) { pts.otherPts(oddMan, endPt) }
    fun align(endIndex: Int, dstPt: SkDPoint) { pts.align(endIndex, dstPt) }

    /**
     * Returns true if the conic between indices [startIndex] and
     * [endIndex] is approximately linear. Delegates to the inner
     * [SkDQuad.isLinear] (the conic's linearity is determined entirely
     * by the control-point geometry ; the weight only modifies the
     * parametric speed along the curve). Mirrors `SkDConic::isLinear`.
     */
    fun isLinear(startIndex: Int, endIndex: Int): Boolean = pts.isLinear(startIndex, endIndex)

    /** Mirrors `SkDConic::flip` — return a reversed copy with the same weight. */
    fun flip(): SkDConic = SkDConic(pts.flip(), weight)

    // ─── Cross-curve hullIntersects (Phase D1.1.e.1) ────────────────
    //
    // Conic-vs-{quad,conic} delegates to the inner SkDQuad's hull
    // (the conic's hull is identical to its underlying quad's hull —
    // the weight doesn't change which side of a line a control point
    // lies on, only how the curve interpolates between them).
    // Conic-vs-cubic delegates to the cubic's polymorphic helper.

    /** Mirrors `SkDConic::hullIntersects(const SkDQuad&, bool*)`. */
    fun hullIntersects(quad: SkDQuad, isLinearOut: BooleanArray): Boolean =
        pts.hullIntersects(quad, isLinearOut)

    /** Mirrors `SkDConic::hullIntersects(const SkDConic&, bool*)`. */
    fun hullIntersects(conic: SkDConic, isLinearOut: BooleanArray): Boolean =
        pts.hullIntersects(conic.pts, isLinearOut)

    /** Mirrors `SkDConic::hullIntersects(const SkDCubic&, bool*)`. */
    fun hullIntersects(cubic: SkDCubic, isLinearOut: BooleanArray): Boolean =
        cubic.hullIntersects(this, isLinearOut)

    // ─── Evaluation ──────────────────────────────────────────────────

    /**
     * Evaluate the conic at parameter [t] using the rational form
     * `P(t) = N(t) / D(t)`. Mirrors `SkDConic::ptAtT`.
     */
    fun ptAtT(t: Double): SkDPoint {
        if (t == 0.0) return pts[0]
        if (t == 1.0) return pts[2]
        val denom = conicEvalDenominator(weight, t)
        return SkDPoint(
            ieeeDivide(conicEvalNumerator(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), weight, t), denom),
            ieeeDivide(conicEvalNumerator(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), weight, t), denom),
        )
    }

    /**
     * Tangent vector at parameter [t]. Mirrors `SkDConic::dxdyAtT`.
     * Falls back to the chord vector at endpoints when the rational
     * derivative collapses to `(0, 0)`.
     */
    fun dxdyAtT(t: Double): SkDVector {
        val result = SkDVector(
            conicEvalTan(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), weight, t),
            conicEvalTan(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), weight, t),
        )
        if (result.x == 0.0 && result.y == 0.0 && zero_or_one(t)) {
            return pts[2] - pts[0]
        }
        return result
    }

    // ─── Subdivision ─────────────────────────────────────────────────

    /**
     * Return the sub-conic that covers the parametric interval
     * `[t1, t2]`. Mirrors `SkDConic::subDivide`.
     *
     * The new weight is derived from the new midpoint matching the
     * curve at `(t1 + t2) / 2` — see the long comment block in the
     * upstream `.cpp` for the algebra.
     */
    fun subDivide(t1: Double, t2: Double): SkDConic {
        val ax: Double; val ay: Double; val az: Double
        when (t1) {
            0.0 -> { ax = pts[0].x; ay = pts[0].y; az = 1.0 }
            1.0 -> { ax = pts[2].x; ay = pts[2].y; az = 1.0 }
            else -> {
                ax = conicEvalNumerator(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), weight, t1)
                ay = conicEvalNumerator(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), weight, t1)
                az = conicEvalDenominator(weight, t1)
            }
        }
        val midT = (t1 + t2) / 2
        val dx = conicEvalNumerator(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), weight, midT)
        val dy = conicEvalNumerator(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), weight, midT)
        val dz = conicEvalDenominator(weight, midT)
        val cx: Double; val cy: Double; val cz: Double
        when (t2) {
            1.0 -> { cx = pts[2].x; cy = pts[2].y; cz = 1.0 }
            0.0 -> { cx = pts[0].x; cy = pts[0].y; cz = 1.0 }
            else -> {
                cx = conicEvalNumerator(doubleArrayOf(pts[0].x, pts[1].x, pts[2].x), weight, t2)
                cy = conicEvalNumerator(doubleArrayOf(pts[0].y, pts[1].y, pts[2].y), weight, t2)
                cz = conicEvalDenominator(weight, t2)
            }
        }
        val bx = 2 * dx - (ax + cx) / 2
        val by = 2 * dy - (ay + cy) / 2
        var bz = 2 * dz - (az + cz) / 2
        if (bz == 0.0) bz = 1.0 // weight 0 ⇒ control irrelevant ; any value works
        return SkDConic(
            pts = SkDQuad(arrayOf(
                SkDPoint(ax / az, ay / az),
                SkDPoint(bx / bz, by / bz),
                SkDPoint(cx / cz, cy / cz),
            )),
            weight = (bz / sqrt(az * cz)).toFloat(),
        )
    }

    /**
     * "Pinned" sub-divide variant — given pinned endpoints [a] and [c],
     * returns the middle control point and writes the new weight into
     * [weightOut] (length-1 out array).
     * Mirrors `SkDConic::subDivide(SkDPoint, SkDPoint, double, double, SkScalar*)`.
     */
    fun subDivide(a: SkDPoint, c: SkDPoint, t1: Double, t2: Double, weightOut: FloatArray): SkDPoint {
        require(weightOut.size >= 1)
        val chopped = subDivide(t1, t2)
        weightOut[0] = chopped.weight
        // The pinned endpoints are accepted as-is — only the middle
        // control + new weight are returned. (Upstream just returns
        // chopped[1] without re-snapping to a/c.)
        return chopped[1]
    }

    // ─── Line-intercept helpers (axis-aligned crossings) ───────────

    /**
     * Number of valid t-values (`0 < t < 1`) where this conic crosses
     * the horizontal line `y = yIntercept`. Mirrors
     * `SkIntersections::HorizontalIntercept(SkDConic, ...)` (delegates
     * to the conic-line intersection class). Solves the rational
     * `validT` quadratic in `t`.
     */
    fun horizontalIntersect(yIntercept: Double, roots: DoubleArray): Int {
        val w = weight.toDouble()
        var A = pts[2].y
        var B = pts[1].y * w - yIntercept * w + yIntercept
        var C = pts[0].y
        A += C - 2 * B
        B -= C
        C -= yIntercept
        return SkDQuad.RootsValidT(A, 2 * B, C, roots)
    }

    /** Vertical analogue of [horizontalIntersect]. */
    fun verticalIntersect(xIntercept: Double, roots: DoubleArray): Int {
        val w = weight.toDouble()
        var A = pts[2].x
        var B = pts[1].x * w - xIntercept * w + xIntercept
        var C = pts[0].x
        A += C - 2 * B
        B -= C
        C -= xIntercept
        return SkDQuad.RootsValidT(A, 2 * B, C, roots)
    }

    /**
     * Project [xy] onto this conic via a perpendicular ray (rotated
     * chord direction). Returns the curve t-value if `xy` is "near"
     * the curve (within ULPs tolerance scaled by the curve's
     * coordinate range), or `-1` if not. Mirrors
     * `SkDCurve::nearPoint(SkPath::kConic_Verb, ...)`.
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

    // ─── Equality ────────────────────────────────────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkDConic) return false
        return pts == other.pts && weight == other.weight
    }

    override fun hashCode(): Int = 31 * pts.hashCode() + weight.hashCode()

    companion object {
        const val kPointCount = 3
        const val kPointLast = kPointCount - 1
        const val kMaxIntersections = 4

        fun pointCount(): Int = kPointCount
        fun pointLast(): Int = kPointLast
        fun maxIntersections(): Int = kMaxIntersections

        /** Mirrors `SkDConic::IsConic` — yes. */
        fun IsConic(): Boolean = true

        /** Forwards to [SkDQuad.AddValidTs]. */
        fun AddValidTs(s: DoubleArray, realRoots: Int, t: DoubleArray): Int =
            SkDQuad.AddValidTs(s, realRoots, t)

        /** Forwards to [SkDQuad.RootsReal]. */
        fun RootsReal(A: Double, B: Double, C: Double, t: DoubleArray): Int =
            SkDQuad.RootsReal(A, B, C, t)

        /** Forwards to [SkDQuad.RootsValidT]. */
        fun RootsValidT(A: Double, B: Double, C: Double, s: DoubleArray): Int =
            SkDQuad.RootsValidT(A, B, C, s)

        /**
         * Find the (at-most-1) interior extremum of the conic on a
         * single axis. Mirrors `SkDConic::FindExtrema`.
         */
        fun FindExtrema(src: DoubleArray, w: Float, t: DoubleArray): Int {
            val coeff = DoubleArray(3)
            conicDerivCoeff(src, w, coeff)
            val tValues = DoubleArray(2)
            val roots = SkDQuad.RootsValidT(coeff[0], coeff[1], coeff[2], tValues)
            return if (roots == 1) { t[0] = tValues[0]; 1 } else 0
        }

        // ─── Internal helpers (mirrored from upstream .cpp) ─────────

        /**
         * Coefficients of the conic derivative polynomial.
         * Mirrors `conic_deriv_coeff` static helper.
         *
         * Note : upstream uses a stride-2 layout `&fPts[0].fX` so its
         * indices are `src[0] / src[2] / src[4]`. We pass stride-1
         * arrays of length 3 (`pts[0].x`, `pts[1].x`, `pts[2].x`) so
         * the indices become `src[0] / src[1] / src[2]`.
         */
        private fun conicDerivCoeff(src: DoubleArray, w: Float, coeff: DoubleArray) {
            val P20 = src[2] - src[0]
            val P10 = src[1] - src[0]
            val wP10 = w * P10
            coeff[0] = w * P20 - P20
            coeff[1] = P20 - 2 * wP10
            coeff[2] = wP10
        }

        /** Evaluate `t · (t · coeff[0] + coeff[1]) + coeff[2]`. */
        private fun conicEvalTan(coord: DoubleArray, w: Float, t: Double): Double {
            val coeff = DoubleArray(3)
            conicDerivCoeff(coord, w, coeff)
            return t * (t * coeff[0] + coeff[1]) + coeff[2]
        }

        /**
         * Numerator polynomial `(A·t + B)·t + C` of the rational form.
         * Mirrors `conic_eval_numerator` (with the stride conversion
         * documented in [conicDerivCoeff]).
         */
        private fun conicEvalNumerator(src: DoubleArray, w: Float, t: Double): Double {
            val src2w = src[1] * w
            val C = src[0]
            val A = src[2] - 2 * src2w + C
            val B = 2 * (src2w - C)
            return (A * t + B) * t + C
        }

        /**
         * Denominator polynomial `(A·t + B)·t + C` of the rational form.
         * Mirrors `conic_eval_denominator`.
         */
        private fun conicEvalDenominator(w: Float, t: Double): Double {
            val B = 2 * (w - 1)
            val C = 1.0
            val A = -B
            return (A * t + B) * t + C
        }

        /** IEEE divide that returns ±Inf / NaN rather than throwing on zero divisor. */
        private fun ieeeDivide(numer: Double, denom: Double): Double = numer / denom
    }
}
