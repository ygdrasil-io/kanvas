package org.skia.math


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

    /** `M.preScale(sx_, sy_)` ≡ `M · Scale(sx_, sy_)`. */
    public fun preScale(sx_: SkScalar, sy_: SkScalar): SkMatrix =
        copy(sx = sx * sx_, kx = kx * sy_, ky = ky * sx_, sy = sy * sy_)

    /**
     * `M.preScale(sx_, sy_, px, py)` ≡ `M · S(sx_, sy_, px, py)` where the
     * scale leaves the pivot `(px, py)` fixed: `T(px, py) · Scale · T(-px, -py)`.
     * Mirrors Skia's [`SkMatrix::preScale(sx, sy, px, py)`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L319).
     */
    public fun preScale(sx_: SkScalar, sy_: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        if (sx_ == 1f && sy_ == 1f) this else preConcat(MakeScale(sx_, sy_, px, py))

    /**
     * `M.preRotate(deg)` ≡ `M · Rotate(deg)` around the origin.
     * Use [preRotate] with `(deg, px, py)` for a rotation around an
     * arbitrary pivot.
     */
    public fun preRotate(deg: SkScalar): SkMatrix = preConcat(MakeRotate(deg))

    public fun preRotate(deg: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        preConcat(MakeRotate(deg, px, py))

    /** `M.preSkew(kx_, ky_)` ≡ `M · Skew(kx_, ky_)`. */
    public fun preSkew(kx_: SkScalar, ky_: SkScalar): SkMatrix =
        preConcat(MakeSkew(kx_, ky_))

    /**
     * `M.preSkew(kx_, ky_, px, py)` ≡ `M · Skew(kx_, ky_, px, py)` where the
     * skew leaves the pivot `(px, py)` fixed.
     */
    public fun preSkew(kx_: SkScalar, ky_: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        preConcat(MakeSkew(kx_, ky_, px, py))

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
        val sigmaMaxSq = 0.5f * sumSq + SkScalarSqrt(diffHalf * diffHalf + cross * cross)
        return SkScalarSqrt(sigmaMaxSq)
    }

    /**
     * Inverse of this affine matrix, or `null` if the linear part is
     * singular or near-singular. Mirrors Skia's `SkMatrix::invert` +
     * `sk_inv_determinant` (src/core/SkMatrix.cpp).
     *
     * For the 2 × 2 linear part `[[sx, kx], [ky, sy]]` with determinant
     * `det = sx·sy − kx·ky`, the inverse linear part is
     * `(1/det) · [[sy, −kx], [−ky, sx]]`. The translate component of the
     * inverse is `−inverseLinear · (tx, ty)`.
     *
     * The determinant is computed in double-precision then compared to
     * `SK_ScalarNearlyZero³` (≈ 1.46e-11). A matrix whose `|det|` falls
     * below that returns `null` to avoid producing finite-but-garbage
     * inverse values; this matches Skia's behaviour where a near-degenerate
     * matrix is treated as singular.
     *
     * Used by [SkShader] implementations to map device-space pixel coords
     * back into the shader's local coordinate system (where the gradient
     * geometry was defined).
     */
    public fun invert(): SkMatrix? {
        val det = sx.toDouble() * sy - kx.toDouble() * ky
        if (SkScalarNearlyZero(det.toFloat(), SK_DetNearlyZero)) return null
        val invDet = 1.0 / det
        val isx = (sy.toDouble() * invDet).toFloat()
        val ikx = (-kx.toDouble() * invDet).toFloat()
        val iky = (-ky.toDouble() * invDet).toFloat()
        val isy = (sx.toDouble() * invDet).toFloat()
        // Inverse translate via dcross_dscale to keep double precision through the
        // cross product (matches Skia's ComputeInv, src/core/SkMatrix.cpp).
        val itx = dcrossDscale(kx, ty, sy, tx, invDet)
        val ity = dcrossDscale(ky, tx, sx, ty, invDet)
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
         * Scale around a pivot `(px, py)`, equivalent to
         * `T(px, py) · S(sx, sy) · T(-px, -py)`. Closed form mirrors
         * Skia's [`SkMatrix::setScale(sx, sy, px, py)`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L300):
         * `tx = px - sx*px`, `ty = py - sy*py`.
         */
        public fun MakeScale(sx: SkScalar, sy: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
            if (sx == 1f && sy == 1f) Identity
            else SkMatrix(sx = sx, kx = 0f, tx = px - sx * px, ky = 0f, sy = sy, ty = py - sy * py)

        /**
         * Rotation matrix around the origin, angle in **degrees** (Skia's
         * convention). Positive angle is clockwise in screen-space (y-down).
         *
         * `sin` and `cos` results within `SK_ScalarSinCosNearlyZero` of zero
         * are snapped to exactly `0f` (mirrors Skia's
         * `SkScalarSinSnapToZero` / `SkScalarCosSnapToZero`,
         * include/core/SkScalar.h). This guarantees that `MakeRotate(90)`,
         * `MakeRotate(180)`, etc. produce bit-exact axis-aligned matrices,
         * so [isAxisAligned] returns `true` for cardinal angles instead of
         * tripping over a `~6e-8` cosine residue.
         */
        public fun MakeRotate(deg: SkScalar): SkMatrix {
            val rad = SkDegreesToRadians(deg)
            val s = SkScalarSinSnapToZero(rad)
            val c = SkScalarCosSnapToZero(rad)
            // Avoid `-0f` from `-s` when `s` was snapped to `+0f`: explicit
            // negation that preserves the positive-zero representation.
            val negS = if (s == 0f) 0f else -s
            return SkMatrix(sx = c, kx = negS, ky = s, sy = c)
        }

        /**
         * Singular-determinant threshold for [invert]. Skia compares
         * `|det|` (cast back to float) against `SK_ScalarNearlyZero³`
         * (cube of `1f / (1 << 12)` = ≈ 1.4552e-11). See `sk_inv_determinant`
         * in src/core/SkMatrix.cpp.
         */
        private const val SK_DetNearlyZero: Float =
            SK_ScalarNearlyZero * SK_ScalarNearlyZero * SK_ScalarNearlyZero

        /** Rotation around a pivot point. */
        public fun MakeRotate(deg: SkScalar, px: SkScalar, py: SkScalar): SkMatrix {
            // T(px, py) · R(deg) · T(-px, -py).
            return MakeTrans(px, py).preConcat(MakeRotate(deg)).preConcat(MakeTrans(-px, -py))
        }

        public fun MakeSkew(kx: SkScalar, ky: SkScalar): SkMatrix =
            SkMatrix(kx = kx, ky = ky)

        /**
         * Skew around a pivot `(px, py)`, equivalent to
         * `T(px, py) · Skew(kx, ky) · T(-px, -py)`. Closed form mirrors
         * Skia's [`SkMatrix::setSkew(sx, sy, px, py)`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L492):
         * `tx = -kx*py`, `ty = -ky*px`.
         */
        public fun MakeSkew(kx: SkScalar, ky: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
            SkMatrix(sx = 1f, kx = kx, tx = -kx * py, ky = ky, sy = 1f, ty = -ky * px)

        /**
         * Matrix multiply: returns `a · b`. A point `p` is mapped first by
         * `b`, then by `a`: `(a · b).map(p) == a.map(b.map(p))`.
         *
         * Each `a*b + c*d` cross-term is promoted to `double` before the
         * final round to `float`, mirroring Skia's `muladdmul`
         * (src/core/SkMatrix.cpp), so a long chain of `concat` accumulates
         * at most ~1 ulp of error per step instead of the ~2 ulp a naive
         * float fma can produce on adversarial inputs.
         */
        public fun concat(a: SkMatrix, b: SkMatrix): SkMatrix = SkMatrix(
            sx = muladdmul(a.sx, b.sx, a.kx, b.ky),
            kx = muladdmul(a.sx, b.kx, a.kx, b.sy),
            tx = muladdmul(a.sx, b.tx, a.kx, b.ty) + a.tx,
            ky = muladdmul(a.ky, b.sx, a.sy, b.ky),
            sy = muladdmul(a.ky, b.kx, a.sy, b.sy),
            ty = muladdmul(a.ky, b.tx, a.sy, b.ty) + a.ty,
        )

        /** Skia src/core/SkMatrix.cpp:603 — `(double)a*b + (double)c*d`, then round. */
        private fun muladdmul(a: Float, b: Float, c: Float, d: Float): Float =
            (a.toDouble() * b + c.toDouble() * d).toFloat()

        /** Skia src/core/SkMatrix.cpp `ComputeInv` — `(a*b - c*d) * scale` in double. */
        private fun dcrossDscale(a: Float, b: Float, c: Float, d: Float, scale: Double): Float =
            ((a.toDouble() * b - c.toDouble() * d) * scale).toFloat()

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

