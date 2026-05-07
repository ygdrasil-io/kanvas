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

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import org.skia.math.SkPoint

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
    }
}
