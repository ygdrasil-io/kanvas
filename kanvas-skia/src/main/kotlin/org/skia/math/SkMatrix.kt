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
    /**
     * Cached type mask, computed once at construction. Bit-OR of the
     * `k*_Mask` constants in the companion object. Mirrors Skia's
     * `SkMatrix::computeTypeMask` ([src/core/SkMatrix.cpp:101](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L101))
     * specialised for the affine subset (perspective row is hardcoded
     * `[0, 0, 1]` so `kPerspective_Mask` is never set).
     *
     * Body-declared (not a primary-constructor field) so it stays out of
     * `data class` `equals` / `hashCode` / `copy`.
     */
    private val fTypeMask: Int = computeTypeMask(sx, kx, ky, sy, tx, ty)

    /**
     * Public type mask: bit-OR of [kIdentity_Mask] / [kTranslate_Mask] /
     * [kScale_Mask] / [kAffine_Mask]. [kPerspective_Mask] is never set
     * for this affine-only port. Mirrors Skia's `SkMatrix::getType`.
     *
     * Use the higher-level predicates ([isIdentity], [isTranslate],
     * [isScaleTranslate], [rectStaysRect]) when you don't need the raw
     * mask.
     */
    public fun getType(): Int = fTypeMask and 0x0F

    /** `true` if no scale, skew, or translation. */
    public val isIdentity: Boolean
        get() = getType() == kIdentity_Mask

    /** `true` if the matrix only translates — no scale, skew, or rotation. */
    public fun isTranslate(): Boolean = (getType() and kTranslate_Mask.inv().and(0x0F)) == 0

    /**
     * `true` if the matrix is some combination of identity / translate
     * / scale (no rotation, skew, or perspective). Mirrors Skia's
     * `SkMatrix::isScaleTranslate`.
     */
    public fun isScaleTranslate(): Boolean =
        (getType() and (kAffine_Mask or kPerspective_Mask)) == 0

    /**
     * Legacy alias for [isScaleTranslate] kept for the existing kanvas
     * call sites. Renamed conceptually now that we expose the full
     * type-mask system; callers writing new code should prefer
     * [isScaleTranslate] for Skia naming parity.
     */
    public val isAxisAligned: Boolean get() = isScaleTranslate()

    /**
     * `true` if the matrix maps a rect to a rect — identity, scale,
     * cardinal-angle rotation, mirror, plus optional translation. Mirrors
     * Skia's `rectStaysRect` ([SkMatrix.h](https://github.com/google/skia/blob/main/include/core/SkMatrix.h)).
     */
    public fun rectStaysRect(): Boolean = (fTypeMask and kRectStaysRect_Mask) != 0

    /** Skia uses both names interchangeably. */
    public fun preservesAxisAlignment(): Boolean = rectStaysRect()

    /** Always `false` in this affine-only port. Mirrors Skia's `hasPerspective`. */
    public fun hasPerspective(): Boolean = (getType() and kPerspective_Mask) != 0

    /**
     * `true` if the matrix is a rotation + uniform scale + translate
     * (a similarity transform). Mirrors Skia's [`SkMatrix::isSimilarity`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L184).
     */
    public fun isSimilarity(tol: SkScalar = SK_ScalarNearlyZero): Boolean {
        val mask = getType()
        if (mask <= kTranslate_Mask) return true
        if (mask and kPerspective_Mask != 0) return false

        if (mask and kAffine_Mask == 0) {
            // No skew — just compare scale magnitudes.
            return !SkScalarNearlyZero(sx) &&
                SkScalarNearlyEqual(SkScalarAbs(sx), SkScalarAbs(sy))
        }
        // Degenerate 2x2 ⇒ no inverse ⇒ not a similarity.
        if (isDegenerate2x2(sx, kx, ky, sy)) return false

        // Upper 2x2 is rotation/reflection + uniform scale iff basis vectors are
        // 90° rotations of each other.
        return (SkScalarNearlyEqual(sx, sy, tol) && SkScalarNearlyEqual(kx, -ky, tol)) ||
            (SkScalarNearlyEqual(sx, -sy, tol) && SkScalarNearlyEqual(kx, ky, tol))
    }

    /**
     * `true` if the matrix maps perpendicular axes to perpendicular axes
     * (i.e. preserves right angles). Mirrors Skia's
     * [`preservesRightAngles`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L213).
     */
    public fun preservesRightAngles(tol: SkScalar = SK_ScalarNearlyZero): Boolean {
        val mask = getType()
        if (mask <= kTranslate_Mask) return true
        if (mask and kPerspective_Mask != 0) return false
        if (isDegenerate2x2(sx, kx, ky, sy)) return false
        // Upper 2x2 is scale + rotation iff basis vectors are orthogonal.
        val dot = sx * kx + ky * sy        // (sx, ky) · (kx, sy)
        return SkScalarNearlyZero(dot, tol * tol)
    }

    /**
     * IEEE-strict (NaN-asymmetric) equality, mirroring Skia's
     * `operator==`. The data-class generated `equals` uses
     * `Float.equals` (NaN-friendly), so use this when porting hot-path
     * C++ that relies on the IEEE semantic.
     */
    public fun cheapEqualTo(other: SkMatrix): Boolean =
        sx == other.sx && kx == other.kx && tx == other.tx &&
            ky == other.ky && sy == other.sy && ty == other.ty

    /** Apply this matrix to a point. */
    public fun mapXY(x: SkScalar, y: SkScalar): Pair<SkScalar, SkScalar> =
        Pair(sx * x + kx * y + tx, ky * x + sy * y + ty)

    /** Apply this matrix to a [SkPoint], returning a new mapped point. */
    public fun mapXY(p: SkPoint): SkPoint =
        SkPoint(sx * p.fX + kx * p.fY + tx, ky * p.fX + sy * p.fY + ty)

    /**
     * Apply only the linear part (drop translation) — used for direction
     * vectors. Mirrors Skia's `SkMatrix::mapVector(dx, dy)`.
     */
    public fun mapVector(dx: SkScalar, dy: SkScalar): SkPoint =
        SkPoint(sx * dx + kx * dy, ky * dx + sy * dy)

    /**
     * Bulk apply this matrix to `count` source points, writing the
     * result to `dst`. `dst` and `src` may alias. Type-mask fast paths
     * mirror Skia's `getMapPtsProc` dispatch:
     *
     * - identity → straight copy
     * - translate-only → `+ (tx, ty)` per point
     * - scale + translate → `(sx*x + tx, sy*y + ty)` per point
     * - else → full affine
     *
     * Mirrors [`SkMatrix::mapPoints`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L782).
     */
    public fun mapPoints(dst: Array<SkPoint>, src: Array<SkPoint>, count: Int) {
        require(count <= dst.size && count <= src.size) {
            "mapPoints count=$count exceeds dst.size=${dst.size} or src.size=${src.size}"
        }
        when (val type = getType()) {
            kIdentity_Mask -> {
                for (i in 0 until count) {
                    dst[i] = SkPoint(src[i].fX, src[i].fY)
                }
            }
            kTranslate_Mask -> {
                for (i in 0 until count) {
                    dst[i] = SkPoint(src[i].fX + tx, src[i].fY + ty)
                }
            }
            else -> if (type and (kAffine_Mask or kPerspective_Mask) == 0) {
                // Scale + translate (no rotation/skew/perspective).
                for (i in 0 until count) {
                    dst[i] = SkPoint(sx * src[i].fX + tx, sy * src[i].fY + ty)
                }
            } else {
                // Full affine.
                for (i in 0 until count) {
                    val x = src[i].fX
                    val y = src[i].fY
                    dst[i] = SkPoint(sx * x + kx * y + tx, ky * x + sy * y + ty)
                }
            }
        }
    }

    /** In-place bulk map. Equivalent to `mapPoints(pts, pts, count)`. */
    public fun mapPoints(pts: Array<SkPoint>, count: Int = pts.size) {
        mapPoints(pts, pts, count)
    }

    /**
     * Bulk apply only the linear part (drop translation) to vectors.
     * Mirrors Skia's `SkMatrix::mapVectors`.
     */
    public fun mapVectors(dst: Array<SkPoint>, src: Array<SkPoint>, count: Int) {
        require(count <= dst.size && count <= src.size)
        if (getType() and (kAffine_Mask or kPerspective_Mask or kScale_Mask) == 0) {
            // Identity or translate-only — vectors are unchanged.
            for (i in 0 until count) {
                if (dst !== src || dst[i] !== src[i]) dst[i] = SkPoint(src[i].fX, src[i].fY)
            }
        } else {
            for (i in 0 until count) {
                val x = src[i].fX
                val y = src[i].fY
                dst[i] = SkPoint(sx * x + kx * y, ky * x + sy * y)
            }
        }
    }

    public fun mapVectors(vectors: Array<SkPoint>, count: Int = vectors.size) {
        mapVectors(vectors, vectors, count)
    }

    /**
     * Apply this matrix to a rect, returning the bounding box of the
     * transformed quad. Equivalent to Skia's `SkMatrix::mapRect`.
     */
    public fun mapRect(r: SkRect): SkRect {
        // Fast path: scale + translate preserves rect orientation, no
        // need to map all four corners.
        if (isScaleTranslate()) return mapRectScaleTranslate(r)

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

    /**
     * Fast-path rect mapping for matrices satisfying [isScaleTranslate].
     * Maps `(left, top)` and `(right, bottom)` directly; the result is
     * sorted to handle negative scales (which flip edges).
     *
     * Mirrors Skia's [`SkMatrix::mapRectScaleTranslate`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1133).
     * Caller must verify `isScaleTranslate()` first.
     */
    public fun mapRectScaleTranslate(r: SkRect): SkRect {
        check(isScaleTranslate()) {
            "mapRectScaleTranslate requires isScaleTranslate matrix; got mask=${getType()}"
        }
        val l1 = sx * r.left + tx
        val r1 = sx * r.right + tx
        val t1 = sy * r.top + ty
        val b1 = sy * r.bottom + ty
        return SkRect.MakeLTRB(minOf(l1, r1), minOf(t1, b1), maxOf(l1, r1), maxOf(t1, b1))
    }

    /**
     * Heuristic mapped radius — for a circle with radius `r` mapped by
     * this matrix, returns the radius of a "representative" circle in
     * device space. Skia uses the geometric mean of the singular values
     * for stroke width estimation; we use the same formula
     * `sqrt(|det|)` derived from `σ_max · σ_min`.
     *
     * Mirrors [`SkMatrix::mapRadius`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp).
     */
    public fun mapRadius(r: SkScalar): SkScalar {
        // Skia: mapRadius(r) = r * sqrt(|sx*sy - kx*ky|).
        val det = SkScalarAbs(sx * sy - kx * ky)
        return r * SkScalarSqrt(det)
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

        // ─── TypeMask constants (mirror Skia's SkMatrix::TypeMask enum) ──
        // [SkMatrix.h:165](https://github.com/google/skia/blob/main/include/core/SkMatrix.h#L165)

        /** No scale, skew, or translate. */
        public const val kIdentity_Mask: Int = 0
        /** Translation only. */
        public const val kTranslate_Mask: Int = 0x01
        /** Scale (uniform or non-uniform). */
        public const val kScale_Mask: Int = 0x02
        /** Skew or rotate. */
        public const val kAffine_Mask: Int = 0x04
        /** Perspective — never set in this affine-only port. */
        public const val kPerspective_Mask: Int = 0x08
        /**
         * Internal "rect stays rect" mask — set when the upper 2x2 maps a rect
         * to a rect (axis-aligned, 90° rotation, or mirror). Skia keeps this
         * bit out of the public 4-bit subset returned by `getType()`.
         */
        internal const val kRectStaysRect_Mask: Int = 0x10

        /**
         * Compute the type mask for a 6-tuple affine matrix. Mirrors Skia's
         * `computeTypeMask` ([src/core/SkMatrix.cpp:101](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L101))
         * with the perspective branch removed.
         *
         * The skew-non-zero branch tracks Skia's `(m01 | m10) != 0` short-circuit:
         * any skew implies `kAffine_Mask | kScale_Mask`, then `rectStaysRect`
         * holds iff the primary diagonal (`sx`, `sy`) is all zero AND the
         * secondary diagonal (`kx`, `ky`) is all non-zero — i.e. a 90°-class
         * rotation. The no-skew branch is simpler: `rectStaysRect` holds iff
         * the primary diagonal is non-zero (translate / scale / mirror).
         */
        internal fun computeTypeMask(
            sx: Float, kx: Float, ky: Float, sy: Float, tx: Float, ty: Float,
        ): Int {
            var mask = 0
            if (tx != 0f || ty != 0f) mask = mask or kTranslate_Mask
            if (kx != 0f || ky != 0f) {
                mask = mask or kAffine_Mask or kScale_Mask
                // rectStaysRect: primary diagonal both 0, secondary both non-zero.
                if (sx == 0f && sy == 0f && kx != 0f && ky != 0f) {
                    mask = mask or kRectStaysRect_Mask
                }
            } else {
                if (sx != 1f || sy != 1f) mask = mask or kScale_Mask
                // rectStaysRect: primary diagonal both non-zero (secondary already 0).
                if (sx != 0f && sy != 0f) mask = mask or kRectStaysRect_Mask
            }
            return mask
        }

        /** Skia's `is_degenerate_2x2`. Used by [isSimilarity] / [preservesRightAngles]. */
        internal fun isDegenerate2x2(sx: Float, kx: Float, ky: Float, sy: Float): Boolean {
            val perpDot = sx * sy - kx * ky
            return SkScalarNearlyZero(perpDot, SK_ScalarNearlyZero * SK_ScalarNearlyZero)
        }

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

