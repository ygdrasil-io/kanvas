package org.skia.foundation

import org.skia.math.SkPoint
import kotlin.math.sqrt

/**
 * Public Bézier subdivision helpers. Mirrors the chop family in
 * Skia's [`src/core/SkGeometry.cpp`](https://github.com/google/skia/blob/main/src/core/SkGeometry.cpp)
 * (`SkChopQuadAt`, `SkChopCubicAt`, `SkConic::chopAt`).
 *
 * Each helper subdivides a Bézier segment at parameter `t ∈ [0, 1]`
 * via de Casteljau (rational form for conics — Farin / Sederberg).
 * The result shares the on-curve point at `t` between the two halves :
 *  - **Quad** : 3 input control points → 5 output points
 *    `(p0, p01, mid, p12, p2)`. The first three describe the left
 *    sub-quad ; the last three describe the right sub-quad
 *    (sharing `mid`).
 *  - **Cubic** : 4 input → 7 output `(p0, q0, q1, mid, q2, q3, p3)`
 *    in the same shared-midpoint layout.
 *  - **Conic** : 3 input + 1 weight → 5 output points and the new
 *    `(leftWeight, rightWeight)` pair returned. The weights renormalise
 *    so each half conic still has endpoint weights of `1`.
 *
 * **In-place output convention** — every helper writes its result into
 * the input array, which therefore must be sized for the output (5, 7,
 * and 5 respectively). This matches Skia's `SkChopQuadAt(src, dst, t)`
 * surface where `src` and `dst` may alias as long as `dst` is sized
 * for the full output ; the kanvas-skia variant collapses the two
 * arguments into a single in-place array because the JVM has no
 * pointer-aliasing concern.
 *
 * Local copies of these helpers existed inside
 * [org.skia.tests.MandolineGM] before this promotion ; cross-cutting
 * GMs (`MandolineGM`, `TrickyCubicStrokesGM`, future stroke / dash /
 * tessellator paths) should now call into [SkGeometry] directly.
 */
public object SkGeometry {

    /**
     * Subdivide the quadratic Bézier defined by `pts[0..2]` at
     * parameter [t]. The 5-element result is written **in place** :
     * `pts[0]` keeps its original value, `pts[4]` keeps its original
     * value, and `pts[1..3]` are filled with `(p01, mid, p12)`.
     *
     * Mirrors `SkChopQuadAt(const SkPoint src[3], SkPoint dst[5],
     * SkScalar t)` (`src/core/SkGeometry.cpp:175`).
     *
     * @throws IllegalArgumentException if [pts] has fewer than 5
     *   elements.
     */
    public fun chopQuadAt(pts: Array<SkPoint>, t: Float) {
        require(pts.size >= 5) { "chopQuadAt requires a 5-element array (3 in / 5 out)" }
        val p0 = pts[0]
        val p1 = pts[1]
        val p2 = pts[2]
        val a = lerp(p0, p1, t)
        val b = lerp(p1, p2, t)
        val mid = lerp(a, b, t)
        pts[0] = p0
        pts[1] = a
        pts[2] = mid
        pts[3] = b
        pts[4] = p2
    }

    /**
     * Subdivide the cubic Bézier defined by `pts[0..3]` at parameter
     * [t]. The 7-element result is written **in place** as
     * `(p0, q0, q1, mid, q2, q3, p3)`.
     *
     * Mirrors `SkChopCubicAt(const SkPoint src[4], SkPoint dst[7],
     * SkScalar t)` (`src/core/SkGeometry.cpp:473`).
     *
     * @throws IllegalArgumentException if [pts] has fewer than 7
     *   elements.
     */
    public fun chopCubicAt(pts: Array<SkPoint>, t: Float) {
        require(pts.size >= 7) { "chopCubicAt requires a 7-element array (4 in / 7 out)" }
        val p0 = pts[0]
        val p1 = pts[1]
        val p2 = pts[2]
        val p3 = pts[3]
        val a = lerp(p0, p1, t)
        val b = lerp(p1, p2, t)
        val c = lerp(p2, p3, t)
        val d = lerp(a, b, t)
        val e = lerp(b, c, t)
        val mid = lerp(d, e, t)
        pts[0] = p0
        pts[1] = a
        pts[2] = d
        pts[3] = mid
        pts[4] = e
        pts[5] = c
        pts[6] = p3
    }

    /**
     * Subdivide the rational conic Bézier defined by `pts[0..2]` and
     * scalar [weight] at parameter [t]. The 5-element point result is
     * written **in place** as `(p0, p01, mid, p12, p2)` (same layout
     * as [chopQuadAt]). The renormalised endpoint-weight `1` form
     * stays implicit ; the returned [Pair] carries the new control-
     * point weights for the two halves so callers can reconstruct
     * each sub-conic as `(left.p[0..2], left.weight)` /
     * `(right.p[0..2], right.weight)`.
     *
     * Mirrors `SkConic::chopAt(SkScalar t, SkConic dst[2]) const`
     * (`src/core/SkGeometry.cpp` ratquad path) — the 3D ratquad
     * promotion + de Casteljau interpolation collapses to the
     * 2D rational form here because the JVM can hold the rational
     * coefficients in plain floats without SIMD packing concerns.
     *
     * @throws IllegalArgumentException if [pts] has fewer than 5
     *   elements.
     * @return `(leftWeight, rightWeight)` — the renormalised
     *   conic weights for the two halves.
     */
    public fun chopConicAt(pts: Array<SkPoint>, weight: Float, t: Float): Pair<Float, Float> {
        require(pts.size >= 5) { "chopConicAt requires a 5-element array (3 in / 5 out)" }
        val p0 = pts[0]
        val p1 = pts[1]
        val p2 = pts[2]

        // Ratquad form : 3D point (x*w, y*w, w). Endpoint weights are
        // implicitly 1 ; the control-point weight is `weight`.
        val w0 = 1f
        val w1 = weight
        val w2 = 1f
        val rx0 = p0.fX * w0; val ry0 = p0.fY * w0
        val rx1 = p1.fX * w1; val ry1 = p1.fY * w1
        val rx2 = p2.fX * w2; val ry2 = p2.fY * w2

        // First-level interpolation (a / b) and second-level mid.
        val ax = rx0 + (rx1 - rx0) * t
        val ay = ry0 + (ry1 - ry0) * t
        val aw = w0 + (w1 - w0) * t
        val bx = rx1 + (rx2 - rx1) * t
        val by = ry1 + (ry2 - ry1) * t
        val bw = w1 + (w2 - w1) * t
        val mx = ax + (bx - ax) * t
        val my = ay + (by - ay) * t
        val mw = aw + (bw - aw) * t

        // Project ratquad back to 2D (divide x, y by w).
        val newP1L = SkPoint(ax / aw, ay / aw)
        val newP1R = SkPoint(bx / bw, by / bw)
        val mid = SkPoint(mx / mw, my / mw)

        // Renormalise weights so each half's endpoints carry weight 1.
        // Mirrors upstream's `w' = w / sqrt(w_a * w_b)` formula.
        val wL = aw / sqrt(w0 * mw)
        val wR = bw / sqrt(mw * w2)

        // Write in-place, matching the 5-point shared-midpoint layout
        // used by [chopQuadAt] / [chopCubicAt].
        pts[0] = p0
        pts[1] = newP1L
        pts[2] = mid
        pts[3] = newP1R
        pts[4] = p2

        return wL to wR
    }

    /** `(1 - t) * a + t * b`, component-wise. */
    private fun lerp(a: SkPoint, b: SkPoint, t: Float): SkPoint =
        SkPoint(a.fX + (b.fX - a.fX) * t, a.fY + (b.fY - a.fY) * t)
}
