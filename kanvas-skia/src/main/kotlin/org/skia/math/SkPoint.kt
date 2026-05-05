package org.skia.math

import kotlin.math.sqrt

/**
 * Float 2-D point. Iso-aligned port of Skia's `SkPoint` (which doubles as a
 * displacement / direction vector — see [SkVector] alias).
 *
 * Mirrors the C++ struct semantics ([include/private/base/SkPoint_impl.h](https://github.com/google/skia/blob/main/include/private/base/SkPoint_impl.h)):
 * - mutable `fX`/`fY` (in-place `set`, `offset`, `scale`, `normalize`, …).
 * - `Make(x, y)` factory.
 * - `Length` / `Normalize` / `Distance` / `DotProduct` / `CrossProduct`
 *   companion helpers.
 * - Operator overloads (`+= -= *= - *`) and value-wise `equals`.
 *
 * **NaN caveat:** Kotlin's `data class equals` treats `NaN == NaN` as `true`
 * (`Float.equals` semantics), whereas C++ `operator==` uses IEEE compare
 * where `NaN == NaN` is `false`. The [equals]`(x, y)` overload uses raw
 * `==` to match C++ behaviour for the NaN-aware case.
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
        fX = SkScalarAbs(pt.fX)
        fY = SkScalarAbs(pt.fY)
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
         * fallback when `x*x + y*y` overflows in float space. Matches
         * Skia's [`SkPoint::Length`](https://github.com/google/skia/blob/main/src/core/SkPoint.cpp):
         * the fast path stays in float when `mag²` is finite (cheaper),
         * the slow path promotes to double on overflow.
         */
        public fun Length(x: Float, y: Float): Float {
            val mag2 = x * x + y * y
            if (mag2.isFinite()) {
                return SkScalarSqrt(mag2)
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

        /**
         * Shared core for `setLength` / `Normalize`. Returns the original
         * vector length (always positive) when the rescale succeeds, or
         * `null` when the rescaled coordinates are non-finite or both zero
         * (in which case `pt` is zeroed). Mirrors Skia's
         * [`set_point_length<false>`](https://github.com/google/skia/blob/main/src/core/SkPoint.cpp).
         *
         * The double-precision intermediate handles both overflow (large
         * inputs whose `x²+y²` would saturate `float`) and underflow (tiny
         * inputs where `1/dmag` is finite but the rescale rounds to 0).
         * The final `(0, 0)` fallback fires only when the rescale itself
         * fails, not on a magnitude threshold — `(1e-20, 0)` normalises
         * to `(1, 0)` here, matching upstream.
         */
        private fun setPointLength(pt: SkPoint, x: Float, y: Float, length: Float): Float? {
            val xx = x.toDouble()
            val yy = y.toDouble()
            val dmag = sqrt(xx * xx + yy * yy)
            // IEEE-754: dmag == 0 ⇒ scale = ±Inf ⇒ x*scale = NaN, caught below.
            val dscale = length.toDouble() / dmag
            val nx = (xx * dscale).toFloat()
            val ny = (yy * dscale).toFloat()
            if (!nx.isFinite() || !ny.isFinite() || (nx == 0f && ny == 0f)) {
                pt.set(0f, 0f)
                return null
            }
            pt.set(nx, ny)
            return dmag.toFloat()
        }
    }
}

/**
 * Skia's `SkVector` is a typealias of `SkPoint`. The two types are
 * interchangeable in upstream code; the alias preserves call-site
 * intent (a vector is a displacement/direction; a point is a position).
 */
public typealias SkVector = SkPoint
