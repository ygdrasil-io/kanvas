package org.skia.core

import org.skia.math.SkPoint
import org.skia.math.SkScalarAbs
import org.skia.math.SkScalarNearlyEqual
import kotlin.math.abs
import kotlin.math.pow

/**
 * Mirrors Skia's
 * [`SkCubicMap`](https://github.com/google/skia/blob/main/include/core/SkCubicMap.h)
 * — fast evaluation of a CSS-style cubic-bezier ease curve inside the
 * unit square, parameterised by two control points `p1` and `p2`
 * (where `p0 = (0, 0)` and `p3 = (1, 1)` are implicit).
 *
 * Used for animation easing curves, where the caller wants
 * `computeYFromX(x)` to behave like CSS's `cubic-bezier(p1.x, p1.y,
 * p2.x, p2.y)`. The `X` coordinates of the control points are clamped
 * to `[0, 1]` at construction (the upstream invariant for the
 * monotonic-X solver) ; the `Y` coordinates may be outside `[0, 1]` for
 * overshoot effects (e.g. ease-back / spring).
 *
 * Specialises based on the control points :
 *  - `p1 ≈ p2` on the diagonal → degenerate linear `y = x`.
 *  - `coeff[1].x ≈ 0` AND `coeff[2].x ≈ 0` → `x = A·t³`, solvable by a
 *    cube root.
 *  - Otherwise → Newton-Raphson cubic solver (max 8 iterations).
 *
 * The Kotlin port mirrors the polynomial form ([fCoeff]) and the
 * dispatch ([Type]) byte-for-byte against the C++ implementation in
 * [`src/core/SkCubicMap.cpp`](https://github.com/google/skia/blob/main/src/core/SkCubicMap.cpp).
 */
public class SkCubicMap(p1: SkPoint, p2: SkPoint) {

    private enum class Type {
        /** `x == y` — pure passthrough. */
        kLine,

        /** `A·t³ == x` — cube-root solver. */
        kCubeRoot,

        /** General monotonic cubic solver (Newton-Raphson). */
        kSolver,
    }

    /**
     * Polynomial coefficients `A`, `B`, `C` such that the curve is
     * `(A·t³ + B·t² + C·t)` for each of `X` and `Y`. Built once at
     * construction.
     */
    private val fCoeff: Array<SkPoint> = arrayOf(
        SkPoint(0f, 0f),
        SkPoint(0f, 0f),
        SkPoint(0f, 0f),
    )

    private val fType: Type

    init {
        // Clamp X values only (allow Ys outside [0, 1] for overshoot).
        val p1x = p1.fX.coerceIn(0f, 1f)
        val p1y = p1.fY
        val p2x = p2.fX.coerceIn(0f, 1f)
        val p2y = p2.fY

        // s1 = p1 * 3 ; s2 = p2 * 3
        val s1x = p1x * 3f; val s1y = p1y * 3f
        val s2x = p2x * 3f; val s2y = p2y * 3f

        // A = 1 + s1 - s2
        fCoeff[0].set(1f + s1x - s2x, 1f + s1y - s2y)
        // B = s2 - s1 - s1   (= s2 - 2·s1)
        fCoeff[1].set(s2x - s1x - s1x, s2y - s1y - s1y)
        // C = s1
        fCoeff[2].set(s1x, s1y)

        fType = when {
            SkScalarNearlyEqual(p1x, p1y) && SkScalarNearlyEqual(p2x, p2y) -> Type.kLine
            coeffNearlyZero(fCoeff[1].fX) && coeffNearlyZero(fCoeff[2].fX) -> Type.kCubeRoot
            else -> Type.kSolver
        }
    }

    /**
     * Evaluate the curve at parametric `t ∈ [0, 1]`. Returns the
     * `(x, y)` point on the bezier.
     */
    public fun computeFromT(t: Float): SkPoint {
        val ax = fCoeff[0].fX; val ay = fCoeff[0].fY
        val bx = fCoeff[1].fX; val by = fCoeff[1].fY
        val cx = fCoeff[2].fX; val cy = fCoeff[2].fY
        val x = ((ax * t + bx) * t + cx) * t
        val y = ((ay * t + by) * t + cy) * t
        return SkPoint(x, y)
    }

    /**
     * Solve `curve.x = x` for `t`, then evaluate `curve.y(t)`. `x` is
     * clamped to `[0, 1]` before solving. Mirrors the upstream
     * [SkCubicMap::computeYFromX](https://github.com/google/skia/blob/main/src/core/SkCubicMap.cpp).
     */
    public fun computeYFromX(x: Float): Float {
        val cx = x.coerceIn(0f, 1f)
        if (nearlyZero(cx) || nearlyZero(1f - cx)) return cx
        if (fType == Type.kLine) return cx
        val t: Float = if (fType == Type.kCubeRoot) {
            (cx / fCoeff[0].fX).toDouble().pow(1.0 / 3.0).toFloat()
        } else {
            cubicSolver(fCoeff[0].fX, fCoeff[1].fX, fCoeff[2].fX, -cx)
        }
        val a = fCoeff[0].fY
        val b = fCoeff[1].fY
        val c = fCoeff[2].fY
        return ((a * t + b) * t + c) * t
    }

    public companion object {
        /**
         * Mirrors Skia's static `SkCubicMap::IsLinear` — `true` if the
         * control points lie on the unit diagonal (within
         * [SkScalarNearlyEqual]'s tolerance).
         */
        public fun IsLinear(p1: SkPoint, p2: SkPoint): Boolean =
            SkScalarNearlyEqual(p1.fX, p1.fY) && SkScalarNearlyEqual(p2.fX, p2.fY)

        /**
         * Tolerance for the cube-root specialisation : both `B.x` and `C.x`
         * must be smaller than this magnitude before we treat the curve as
         * `x = A·t³`. Matches the upstream constant `0.0000001f`.
         */
        private const val COEFF_EPS: Float = 1e-7f

        private fun coeffNearlyZero(delta: Float): Boolean = abs(delta) <= COEFF_EPS

        /**
         * Skia's `nearly_zero` for `computeYFromX` — looser than the global
         * [SkScalarNearlyEqual] tolerance ; matches the C++ inline literal
         * `0.0000000001f`.
         */
        private fun nearlyZero(x: Float): Boolean = x <= 1e-10f

        /**
         * Newton-Raphson solver for `A·t³ + B·t² + C·t + D = 0` over
         * `t ∈ [0, 1]` — `D` is `-x` for our remap. Matches Skia's
         * [`cubic_solver`](https://github.com/google/skia/blob/main/src/core/SkCubicMap.cpp).
         * Up to 8 iterations, early-exit when `|f| <= 5e-5`.
         */
        private fun cubicSolver(A: Float, B: Float, C: Float, D: Float): Float {
            var t = -D  // guess_nice_cubic_root : seed at -D (= x for our remap).
            val maxIters = 8
            for (i in 0 until maxIters) {
                val f = ((A * t + B) * t + C) * t + D            // At^3 + Bt^2 + Ct + D
                if (SkScalarAbs(f) <= 5e-5f) break
                val fp = (3f * A * t + 2f * B) * t + C            // 3At^2 + 2Bt + C
                val fpp = (3f * A + 3f * A) * t + 2f * B          // 6At + 2B
                val numer = 2f * fp * f
                val denom = 2f * fp * fp - f * fpp
                t -= numer / denom
            }
            return t
        }
    }
}
