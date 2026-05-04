package org.skia.math

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 2D affine transformation matrix — port of Skia's `SkMatrix` restricted to
 * the affine sub-group (no perspective). Stored row-major as a 2 × 3 matrix:
 *
 * ```
 * [ sx  kx  tx ]   [ x ]   [ sx·x + kx·y + tx ]
 * [ ky  sy  ty ] · [ y ] = [ ky·x + sy·y + ty ]
 *                  [ 1 ]
 * ```
 *
 * Naming follows Skia's `SkMatrix::kMScaleX/kMSkewX/kMTransX/kMSkewY/kMScaleY/kMTransY`:
 * `sx` is the scale-X coefficient, `kx` is the skew-X coefficient, etc.
 *
 * Operations come in two flavours mirroring Skia's `pre*` / `post*` split:
 *  - **`pre*` (default for `canvas.translate/scale/rotate/skew/concat`)**:
 *    `M = M · L` — the local transform is applied to the *source* coords
 *    first, then the prior CTM. Mirrors Skia's behaviour where
 *    `canvas.translate(dx, dy)` shifts the origin of the *source* space.
 *  - **`post*`**: `M = L · M` — the local transform is applied to the
 *    *device* coords after the prior CTM.
 *
 * Perspective is out of scope: callers attempting `setPerspective`
 * arithmetic should use a different abstraction. `SkMatrix` here covers
 * Identity / Translate / Scale / Rotate / Skew and any composition
 * thereof, which is what every GM in scope uses.
 */
public data class SkMatrix(
    val sx: SkScalar = 1f,
    val kx: SkScalar = 0f,
    val tx: SkScalar = 0f,
    val ky: SkScalar = 0f,
    val sy: SkScalar = 1f,
    val ty: SkScalar = 0f,
) {
    /** Returns true if this matrix is the identity (no scale, skew, or translate). */
    public val isIdentity: Boolean
        get() = sx == 1f && kx == 0f && tx == 0f && ky == 0f && sy == 1f && ty == 0f

    /** True if `kx == 0 && ky == 0` — the transform is axis-aligned (translate + scale only). */
    public val isAxisAligned: Boolean
        get() = kx == 0f && ky == 0f

    /** Apply this matrix to a point. */
    public fun mapXY(x: SkScalar, y: SkScalar): Pair<SkScalar, SkScalar> =
        Pair(sx * x + kx * y + tx, ky * x + sy * y + ty)

    /**
     * Apply this matrix to a rect, returning the bounding box of the
     * transformed quad. Equivalent to Skia's `SkMatrix::mapRect`.
     */
    public fun mapRect(r: SkRect): SkRect {
        val (x0, y0) = mapXY(r.left, r.top)
        val (x1, y1) = mapXY(r.right, r.top)
        val (x2, y2) = mapXY(r.right, r.bottom)
        val (x3, y3) = mapXY(r.left, r.bottom)
        return SkRect.MakeLTRB(
            minOf(x0, x1, x2, x3),
            minOf(y0, y1, y2, y3),
            maxOf(x0, x1, x2, x3),
            maxOf(y0, y1, y2, y3),
        )
    }

    /** Pre-concat: `this = this · other`. Mirrors `SkMatrix::preConcat`. */
    public fun preConcat(other: SkMatrix): SkMatrix = concat(this, other)

    /** Post-concat: `this = other · this`. Mirrors `SkMatrix::postConcat`. */
    public fun postConcat(other: SkMatrix): SkMatrix = concat(other, this)

    /** `M.preTranslate(dx, dy)` ≡ `M · Translate(dx, dy)`. */
    public fun preTranslate(dx: SkScalar, dy: SkScalar): SkMatrix =
        // Closed form: keeps numerical precision (no accumulating Identity multiplies).
        copy(tx = tx + sx * dx + kx * dy, ty = ty + ky * dx + sy * dy)

    /** `M.preScale(kx_, ky_)` ≡ `M · Scale(kx_, ky_)`. */
    public fun preScale(kx_: SkScalar, ky_: SkScalar): SkMatrix =
        copy(sx = sx * kx_, kx = kx * ky_, ky = ky * kx_, sy = sy * ky_)

    /**
     * `M.preRotate(deg)` ≡ `M · Rotate(deg)` around the origin.
     * Use [preRotate] with `(deg, px, py)` for a rotation around an
     * arbitrary pivot.
     */
    public fun preRotate(deg: SkScalar): SkMatrix = preConcat(MakeRotate(deg))

    public fun preRotate(deg: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        preConcat(MakeRotate(deg, px, py))

    /** `M.preSkew(sx_, sy_)` ≡ `M · Skew(sx_, sy_)`. */
    public fun preSkew(sx_: SkScalar, sy_: SkScalar): SkMatrix =
        preConcat(MakeSkew(sx_, sy_))

    /**
     * Maximum scale factor of the matrix in any direction — the largest
     * singular value. Used by [SkStroker] to compute its CTM-aware
     * flattening tolerance. For pure scale, returns `max(|sx|, |sy|)`;
     * for pure rotation, returns `1`; for any affine combination, returns
     * the longest semi-axis of the unit-circle's image.
     *
     * For a 2×2 linear part `[[a, b], [c, d]]`, `σ_max²` is the largest
     * eigenvalue of `MᵀM = [[a²+c², ab+cd], [ab+cd, b²+d²]]`:
     * `σ_max² = (a² + b² + c² + d²)/2 + sqrt(((a² + c² − b² − d²)/2)² + (ab + cd)²)`.
     * Translation components don't contribute to the scale factor and are
     * ignored.
     */
    public fun computeMaxScale(): SkScalar {
        val a = sx; val b = kx; val c = ky; val d = sy
        val sumSq = a * a + b * b + c * c + d * d
        val diffHalf = ((a * a + c * c) - (b * b + d * d)) * 0.5f
        val cross = a * b + c * d
        val sigmaMaxSq = 0.5f * sumSq + sqrt(diffHalf * diffHalf + cross * cross)
        return sqrt(sigmaMaxSq)
    }

    /**
     * Inverse of this affine matrix, or `null` if the linear part is
     * singular (`det == 0`). Mirrors Skia's `SkMatrix::invert`.
     *
     * For the 2 × 2 linear part `[[sx, kx], [ky, sy]]` with determinant
     * `det = sx·sy − kx·ky`, the inverse linear part is
     * `(1/det) · [[sy, −kx], [−ky, sx]]`. The translate component of the
     * inverse is `−inverseLinear · (tx, ty)`.
     *
     * The intermediate determinant is computed in double-precision so a
     * matrix that's close to singular but not exactly zero (e.g. a near-
     * degenerate gradient under heavy CTM scale) inverts cleanly without
     * spurious `NaN`s.
     *
     * Used by [SkShader] implementations to map device-space pixel coords
     * back into the shader's local coordinate system (where the gradient
     * geometry was defined).
     */
    public fun invert(): SkMatrix? {
        val det = sx.toDouble() * sy.toDouble() - kx.toDouble() * ky.toDouble()
        if (det == 0.0) return null
        val invDet = 1.0 / det
        val isx = (sy.toDouble() * invDet).toFloat()
        val ikx = (-kx.toDouble() * invDet).toFloat()
        val iky = (-ky.toDouble() * invDet).toFloat()
        val isy = (sx.toDouble() * invDet).toFloat()
        // Inverse translate: -inverseLinear · (tx, ty).
        val itx = -(isx * tx + ikx * ty)
        val ity = -(iky * tx + isy * ty)
        return SkMatrix(sx = isx, kx = ikx, tx = itx, ky = iky, sy = isy, ty = ity)
    }

    public companion object {
        public val Identity: SkMatrix = SkMatrix()

        public fun MakeTrans(dx: SkScalar, dy: SkScalar): SkMatrix =
            SkMatrix(tx = dx, ty = dy)

        public fun MakeScale(sx: SkScalar, sy: SkScalar): SkMatrix =
            SkMatrix(sx = sx, sy = sy)

        public fun MakeScale(s: SkScalar): SkMatrix = MakeScale(s, s)

        /**
         * Rotation matrix around the origin, angle in **degrees** (Skia's
         * convention). Positive angle is clockwise in screen-space (y-down).
         *
         * Trigonometric values use `kotlin.math.{sin, cos}` which are the
         * IEEE-754 single-precision approximations on the JVM — bit-equivalent
         * to upstream's `sk_float_sin/cos` to within 1 ULP.
         */
        public fun MakeRotate(deg: SkScalar): SkMatrix {
            val rad = deg.toDouble() * PI / 180.0
            val s = sin(rad).toFloat()
            val c = cos(rad).toFloat()
            return SkMatrix(sx = c, kx = -s, ky = s, sy = c)
        }

        /** Rotation around a pivot point. */
        public fun MakeRotate(deg: SkScalar, px: SkScalar, py: SkScalar): SkMatrix {
            // T(px, py) · R(deg) · T(-px, -py).
            return MakeTrans(px, py).preConcat(MakeRotate(deg)).preConcat(MakeTrans(-px, -py))
        }

        public fun MakeSkew(kx: SkScalar, ky: SkScalar): SkMatrix =
            SkMatrix(kx = kx, ky = ky)

        /**
         * Matrix multiply: returns `a · b`. A point `p` is mapped first by
         * `b`, then by `a`: `(a · b).map(p) == a.map(b.map(p))`.
         */
        public fun concat(a: SkMatrix, b: SkMatrix): SkMatrix = SkMatrix(
            sx = a.sx * b.sx + a.kx * b.ky,
            kx = a.sx * b.kx + a.kx * b.sy,
            tx = a.sx * b.tx + a.kx * b.ty + a.tx,
            ky = a.ky * b.sx + a.sy * b.ky,
            sy = a.ky * b.kx + a.sy * b.sy,
            ty = a.ky * b.tx + a.sy * b.ty + a.ty,
        )

        /**
         * `MakeAll(sx, kx, tx, ky, sy, ty)` — verbatim row-major construction
         * for callers that have all six scalars to hand (typically test
         * fixtures or hand-translated `SkMatrix::MakeAll(...)` from C++).
         */
        public fun MakeAll(
            sx: SkScalar, kx: SkScalar, tx: SkScalar,
            ky: SkScalar, sy: SkScalar, ty: SkScalar,
        ): SkMatrix = SkMatrix(sx, kx, tx, ky, sy, ty)
    }
}

