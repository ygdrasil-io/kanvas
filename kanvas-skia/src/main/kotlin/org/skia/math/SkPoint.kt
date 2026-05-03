package org.skia.math

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Float 2-D point. Faithful port of Skia's `SkPoint` (which doubles as a
 * displacement / direction vector — see [SkVector] alias).
 *
 * Mirrors the C++ struct semantics:
 * - mutable `fX`/`fY` (in-place `set`, `offset`, `scale`, `normalize`, …).
 * - `Make(x, y)` factory.
 * - `Length` / `Normalize` / `Distance` / `DotProduct` / `CrossProduct`
 *   companion helpers.
 * - Operator overloads (`+= -= *= - *`) and value-wise `equals`.
 *
 * For NaN / infinity behaviour, Kotlin's `data class equals` treats
 * `NaN == NaN` as `true` (Java `Float.equals` semantics) — the
 * `equals(x, y)` overload uses raw `==` to match C++ behaviour where
 * `NaN != NaN` is the relevant edge case.
 */
public data class SkPoint(public var fX: Float = 0f, public var fY: Float = 0f) {

    public fun x(): Float = fX

    public fun y(): Float = fY

    public fun isZero(): Boolean = fX == 0f && fY == 0f

    public fun set(x: Float, y: Float) {
        fX = x
        fY = y
    }

    public fun iset(x: Int, y: Int) {
        fX = x.toFloat()
        fY = y.toFloat()
    }

    public fun iset(p: SkIPoint) {
        fX = p.fX.toFloat()
        fY = p.fY.toFloat()
    }

    public fun setAbs(pt: SkPoint) {
        fX = abs(pt.fX)
        fY = abs(pt.fY)
    }

    public fun offset(dx: Float, dy: Float) {
        fX += dx
        fY += dy
    }

    public fun length(): Float = Length(fX, fY)

    public fun distanceToOrigin(): Float = length()

    /**
     * Scales `(fX, fY)` so that [length] returns one, preserving direction.
     * Returns `false` (and zeros the point) if the prior length is nearly
     * zero or non-finite. Matches Skia's `SkPoint::normalize`.
     */
    public fun normalize(): Boolean = setLength(fX, fY, 1f)

    public fun setNormalize(x: Float, y: Float): Boolean = setLength(x, y, 1f)

    public fun setLength(length: Float): Boolean = setLength(fX, fY, length)

    public fun setLength(x: Float, y: Float, length: Float): Boolean =
        setPointLength(this, x, y, length) != null

    public fun scale(scale: Float, dst: SkPoint?) {
        val target = dst ?: this
        target.set(fX * scale, fY * scale)
    }

    public fun scale(value: Float) {
        scale(value, this)
    }

    public fun negate() {
        fX = -fX
        fY = -fY
    }

    public operator fun unaryMinus(): SkPoint = SkPoint(-fX, -fY)

    public operator fun plusAssign(v: SkVector) {
        fX += v.fX
        fY += v.fY
    }

    public operator fun minusAssign(v: SkVector) {
        fX -= v.fX
        fY -= v.fY
    }

    public operator fun times(scale: Float): SkPoint = SkPoint(fX * scale, fY * scale)

    public operator fun timesAssign(scale: Float) {
        fX *= scale
        fY *= scale
    }

    public operator fun plus(v: SkVector): SkPoint = SkPoint(fX + v.fX, fY + v.fY)

    public operator fun minus(b: SkPoint): SkVector = SkPoint(fX - b.fX, fY - b.fY)

    public fun isFinite(): Boolean = fX.isFinite() && fY.isFinite()

    /** Strict-`==` value compare matching C++ `equals(float, float)` (NaN-aware: `NaN.equals(NaN) == false`). */
    public fun equals(x: Float, y: Float): Boolean = fX == x && fY == y

    public fun cross(vec: SkVector): Float = CrossProduct(this, vec)

    public fun dot(vec: SkVector): Float = DotProduct(this, vec)

    public companion object {
        public fun Make(x: Float, y: Float): SkPoint = SkPoint(x, y)

        public fun Offset(points: Array<SkPoint>, count: Int, offset: SkVector) {
            Offset(points, count, offset.fX, offset.fY)
        }

        public fun Offset(points: Array<SkPoint>, count: Int, dx: Float, dy: Float) {
            for (i in 0 until count) points[i].offset(dx, dy)
        }

        /**
         * Euclidean magnitude `sqrt(x² + y²)`, with a double-precision
         * fallback when `x*x + y*y` overflows / underflows in float space.
         * Matches Skia's `SkPoint::Length`.
         */
        public fun Length(x: Float, y: Float): Float {
            val mag2 = x * x + y * y
            if (mag2.isFinite()) {
                return sqrt(mag2.toDouble()).toFloat()
            }
            val xx = x.toDouble()
            val yy = y.toDouble()
            return sqrt(xx * xx + yy * yy).toFloat()
        }

        /**
         * Normalise `vec` to unit length in place. Returns the *original*
         * length, or `0` if the vector was nearly-zero / non-finite (in
         * which case `vec` is zeroed).
         */
        public fun Normalize(vec: SkPoint): Float {
            val mag = setPointLength(vec, vec.fX, vec.fY, 1f)
            return mag ?: 0f
        }

        public fun Distance(a: SkPoint, b: SkPoint): Float = Length(a.fX - b.fX, a.fY - b.fY)

        public fun DotProduct(a: SkVector, b: SkVector): Float = a.fX * b.fX + a.fY * b.fY

        public fun CrossProduct(a: SkVector, b: SkVector): Float = a.fX * b.fY - a.fY * b.fX

        // Skia's SkScalarNearlyZero threshold (`SK_ScalarNearlyZero = SK_Scalar1 / (1 << 12)`).
        private const val NearlyZero: Float = 1f / 4096f

        /**
         * Shared core for `setLength` / `Normalize`. Returns the original
         * vector length (always positive) when the rescale succeeds, or
         * `null` when the input is nearly-zero or non-finite (in which
         * case `pt` is zeroed). Mirrors Skia's `set_point_length<false>`.
         */
        private fun setPointLength(pt: SkPoint, x: Float, y: Float, length: Float): Float? {
            var xx = x.toDouble()
            var yy = y.toDouble()
            val mag2 = xx * xx + yy * yy
            if (mag2 < NearlyZero * NearlyZero || !mag2.isFinite()) {
                pt.set(0f, 0f)
                return null
            }
            val mag = sqrt(mag2)
            val scale = length / mag
            pt.set((xx * scale).toFloat(), (yy * scale).toFloat())
            return mag.toFloat()
        }
    }
}

/**
 * Skia's `SkVector` is a typealias of `SkPoint`. The two types are
 * interchangeable in upstream code; the alias preserves call-site
 * intent (a vector is a displacement/direction; a point is a position).
 */
public typealias SkVector = SkPoint
