package org.graphiks.math


/**
 * 2D transformation matrix — full 3 × 3 port of Skia's `SkMatrix`.
 * Stored row-major:
 *
 * ```
 * [ sx      kx      tx     ]   [ x ]   [ sx·x + kx·y + tx     ]
 * [ ky      sy      ty     ] · [ y ] = [ ky·x + sy·y + ty     ]   →  divide by w
 * [ persp0  persp1  persp2 ]   [ 1 ]   [ p0·x + p1·y + p2 = w ]
 * ```
 *
 * The default-constructed matrix is the identity (no scale, skew,
 * translate, or perspective). The 6 affine fields mirror Skia's
 * `kMScaleX / kMSkewX / kMTransX / kMSkewY / kMScaleY / kMTransY`;
 * the 3 perspective fields mirror `kMPersp0 / kMPersp1 / kMPersp2`.
 *
 * Operations come in two flavours mirroring Skia's `pre*` / `post*` split:
 *  - **`pre*` (default for `canvas.translate/scale/rotate/skew/concat`)**:
 *    `M = M · L` — the local transform is applied to the *source* coords
 *    first, then the prior CTM. Mirrors Skia's behaviour where
 *    `canvas.translate(dx, dy)` shifts the origin of the *source* space.
 *  - **`post*`**: `M = L · M` — the local transform is applied to the
 *    *device* coords after the prior CTM.
 *
 * Perspective semantics: when `(persp0, persp1, persp2) != (0, 0, 1)`,
 * [mapXY] / [mapPoints] perform the homogeneous divide; [concat] uses
 * a full 3×3 multiply; [invert] uses the cofactor matrix. Affine
 * fast paths kick in when [hasPerspective] is `false`, so the cost
 * of the perspective fields is zero on existing affine traffic.
 */
public data class SkMatrix(
    val sx: SkScalar = 1f,
    val kx: SkScalar = 0f,
    val tx: SkScalar = 0f,
    val ky: SkScalar = 0f,
    val sy: SkScalar = 1f,
    val ty: SkScalar = 0f,
    val persp0: SkScalar = 0f,
    val persp1: SkScalar = 0f,
    val persp2: SkScalar = 1f,
) {
    /**
     * `ScaleToFit` describes how [Companion.MakeRectToRect] maps one
     * rect to another. Mirrors Skia's enum
     * ([SkMatrix.h:129](https://github.com/google/skia/blob/main/include/core/SkMatrix.h#L129)).
     */
    public enum class ScaleToFit {
        /** Stretch independently in x and y to fill `dst`. */
        kFill_ScaleToFit,
        /** Uniform scale; align to top-left of `dst`. */
        kStart_ScaleToFit,
        /** Uniform scale; centre within `dst`. */
        kCenter_ScaleToFit,
        /** Uniform scale; align to bottom-right of `dst`. */
        kEnd_ScaleToFit,
    }

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
    private val fTypeMask: Int = computeTypeMask(sx, kx, ky, sy, tx, ty, persp0, persp1, persp2)

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
            ky == other.ky && sy == other.sy && ty == other.ty &&
            persp0 == other.persp0 && persp1 == other.persp1 && persp2 == other.persp2

    // ─── Function-style accessors (Skia naming) ──────────────────────────

    /** Mirrors Skia's `SkMatrix::getScaleX()`. Equivalent to direct field [sx] access. */
    public fun getScaleX(): SkScalar = sx
    /** Mirrors Skia's `SkMatrix::getScaleY()`. Equivalent to direct field [sy] access. */
    public fun getScaleY(): SkScalar = sy
    /** Mirrors Skia's `SkMatrix::getSkewX()`. */
    public fun getSkewX(): SkScalar = kx
    /** Mirrors Skia's `SkMatrix::getSkewY()`. */
    public fun getSkewY(): SkScalar = ky
    /** Mirrors Skia's `SkMatrix::getTranslateX()`. */
    public fun getTranslateX(): SkScalar = tx
    /** Mirrors Skia's `SkMatrix::getTranslateY()`. */
    public fun getTranslateY(): SkScalar = ty

    /** Always `0` for this affine port (perspective row is hardcoded `[0, 0, 1]`). */
    public fun getPerspX(): SkScalar = persp0
    /** Returns `kMPersp1` (`persp1`). */
    public fun getPerspY(): SkScalar = persp1

    /**
     * Determinant of the upper 2×2 (linear part). Equivalent to
     * `sx * sy - kx * ky`. Used by [mapRadius] and the inverse algorithm.
     */
    public fun det2x2(): SkScalar = sx * sy - kx * ky

    /**
     * Full 3×3 determinant via cofactor expansion along the first row.
     * For an affine matrix (perspective row `[0, 0, 1]`) this collapses
     * to [det2x2].
     */
    public fun det(): SkScalar =
        sx * (sy * persp2 - ty * persp1) -
            kx * (ky * persp2 - ty * persp0) +
            tx * (ky * persp1 - sy * persp0)

    // ─── Array exchange ─────────────────────────────────────────────────

    /**
     * Fill `buffer[0..8]` with the matrix in Skia's row-major 9-tuple
     * order: `[sx, kx, tx, ky, sy, ty, persp0, persp1, persp2]`.
     *
     * Mirrors Skia's `SkMatrix::get9` ([SkMatrix.h](https://github.com/google/skia/blob/main/include/core/SkMatrix.h)).
     */
    public fun get9(buffer: FloatArray) {
        require(buffer.size >= 9) { "get9 buffer must have ≥ 9 elements (got ${buffer.size})" }
        buffer[0] = sx; buffer[1] = kx; buffer[2] = tx
        buffer[3] = ky; buffer[4] = sy; buffer[5] = ty
        buffer[6] = persp0; buffer[7] = persp1; buffer[8] = persp2
    }

    /**
     * Fill `buffer[0..5]` with the affine 6-tuple in Skia's COLUMN-major
     * order: `[scaleX, skewY, skewX, scaleY, transX, transY]`. Note the
     * subtle reordering vs `get9` — Skia stores affine arrays as
     * `[a, c, b, d, e, f]` where the matrix is `[[a, b, e], [c, d, f]]`.
     *
     * Returns `false` (and leaves `buffer` untouched) when this matrix
     * has perspective — the affine 6-tuple cannot represent it.
     * Mirrors Skia's [`SkMatrix::asAffine`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L767).
     */
    public fun asAffine(buffer: FloatArray): Boolean {
        require(buffer.size >= 6) { "asAffine buffer must have ≥ 6 elements (got ${buffer.size})" }
        if (hasPerspective()) return false
        buffer[kAScaleX] = sx
        buffer[kASkewY] = ky
        buffer[kASkewX] = kx
        buffer[kAScaleY] = sy
        buffer[kATransX] = tx
        buffer[kATransY] = ty
        return true
    }

    /**
     * Bulk map source points to homogeneous output (no perspective
     * divide). `dst[i] = (sx*x + kx*y + tx, ky*x + sy*y + ty,
     * persp0*x + persp1*y + persp2)`. Mirrors Skia's
     * [`SkMatrix::mapPointsToHomogeneous`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1080).
     */
    public fun mapHomogeneousPoints(
        dst: Array<SkPoint3>,
        src: Array<SkPoint>,
        count: Int = src.size,
    ) {
        require(count <= dst.size && count <= src.size)
        if (isIdentity) {
            for (i in 0 until count) dst[i] = SkPoint3(src[i].fX, src[i].fY, 1f)
        } else if (hasPerspective()) {
            for (i in 0 until count) {
                val x = src[i].fX
                val y = src[i].fY
                dst[i] = SkPoint3(
                    sx * x + kx * y + tx,
                    ky * x + sy * y + ty,
                    persp0 * x + persp1 * y + persp2,
                )
            }
        } else {
            for (i in 0 until count) {
                val x = src[i].fX
                val y = src[i].fY
                dst[i] = SkPoint3(sx * x + kx * y + tx, ky * x + sy * y + ty, 1f)
            }
        }
    }

    // ─── Singular values / scale decomposition ──────────────────────────

    /**
     * Largest singular value of the upper 2×2. For pure scale, returns
     * `max(|sx|, |sy|)`; for pure rotation, `1`; for any affine
     * combination, the longest semi-axis of the unit-circle's image.
     *
     * Mirrors Skia's [`SkMatrix::getMaxScale`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1451).
     */
    public fun getMaxScale(): SkScalar {
        val results = FloatArray(1)
        if (computeScaleFactor(scaleKind = SCALE_KIND_MAX, results)) return results[0]
        return -1f   // unreachable for affine-only port
    }

    /**
     * Smallest singular value of the upper 2×2 — matches Skia's `getMinScale`.
     * Returns `-1` if the matrix has perspective (never the case here).
     */
    public fun getMinScale(): SkScalar {
        val results = FloatArray(1)
        if (computeScaleFactor(scaleKind = SCALE_KIND_MIN, results)) return results[0]
        return -1f
    }

    /**
     * Fill `scaleFactors[0..1]` with `[min, max]` singular values.
     * Returns `false` if the matrix has perspective (always succeeds
     * here).
     */
    public fun getMinMaxScales(scaleFactors: FloatArray): Boolean {
        require(scaleFactors.size >= 2)
        return computeScaleFactor(scaleKind = SCALE_KIND_BOTH, scaleFactors)
    }

    /**
     * Legacy alias kept to avoid breaking pre-Phase-3 callers. Renamed
     * to [getMaxScale] for Skia parity.
     */
    @Deprecated(
        "Use getMaxScale() for Skia naming parity",
        ReplaceWith("getMaxScale()"),
    )
    public fun computeMaxScale(): SkScalar = getMaxScale()

    /**
     * Decompose this matrix into a pure scale and a "remaining" rotation
     * + skew + translate component, such that
     * `this = remaining · S(scale.fX, scale.fY)`. Returns `null` when
     * the decomposition fails (perspective, non-finite, or near-singular
     * scales — matching Skia's `decomposeScale`).
     *
     * Result type differs from Skia's out-parameter form because our
     * `SkMatrix` is immutable: returns a `Pair<SkPoint, SkMatrix>` of
     * `(scale, remaining)`.
     *
     * Mirrors [`SkMatrix::decomposeScale`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1479).
     */
    public fun decomposeScale(): Pair<SkPoint, SkMatrix>? {
        if (hasPerspective()) return null
        val sxLen = SkPoint.Length(sx, ky)
        val syLen = SkPoint.Length(kx, sy)
        if (!sxLen.isFinite() || !syLen.isFinite() ||
            SkScalarNearlyZero(sxLen) || SkScalarNearlyZero(syLen)
        ) return null
        val remaining = preScale(SkScalarInvert(sxLen), SkScalarInvert(syLen))
        return Pair(SkPoint(sxLen, syLen), remaining)
    }

    /**
     * Internal min/max scale solver. Mirrors Skia's `get_scale_factor`
     * template ([SkMatrix.cpp:1348](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1348)).
     * The eigenvalues of `MᵀM` are the squared singular values.
     */
    private fun computeScaleFactor(scaleKind: Int, results: FloatArray): Boolean {
        val mask = getType()
        if (mask == kIdentity_Mask) {
            results[0] = 1f
            if (scaleKind == SCALE_KIND_BOTH) results[1] = 1f
            return true
        }
        if (mask and kAffine_Mask == 0) {
            // Pure scale (and/or translate): singular values are |sx|, |sy|.
            val ax = SkScalarAbs(sx)
            val ay = SkScalarAbs(sy)
            when (scaleKind) {
                SCALE_KIND_MIN -> results[0] = minOf(ax, ay)
                SCALE_KIND_MAX -> results[0] = maxOf(ax, ay)
                else -> {
                    results[0] = minOf(ax, ay)
                    results[1] = maxOf(ax, ay)
                }
            }
            return true
        }
        // [a b; b c] = MᵀM (computed in float; Skia uses sdot).
        val a = sx * sx + ky * ky
        val b = sx * kx + sy * ky
        val c = kx * kx + sy * sy
        val bSqd = b * b
        val first: Float
        val second: Float
        if (bSqd <= SK_ScalarNearlyZero * SK_ScalarNearlyZero) {
            // Already orthogonal — singular values are sqrt(a), sqrt(c).
            first = minOf(a, c)
            second = maxOf(a, c)
        } else {
            val aMinusC = a - c
            val aPlusCDiv2 = (a + c) * 0.5f
            val x = SkScalarSqrt(aMinusC * aMinusC + 4f * bSqd) * 0.5f
            first = aPlusCDiv2 - x   // smaller eigenvalue
            second = aPlusCDiv2 + x  // larger eigenvalue
        }
        // Floating-point may drive a near-zero negative; Skia clamps to 0.
        val sFirst = if (!first.isFinite()) return false else if (first < 0f) 0f else first
        val sSecond = if (!second.isFinite()) return false else if (second < 0f) 0f else second
        when (scaleKind) {
            SCALE_KIND_MIN -> results[0] = SkScalarSqrt(sFirst)
            SCALE_KIND_MAX -> results[0] = SkScalarSqrt(sSecond)
            else -> { results[0] = SkScalarSqrt(sFirst); results[1] = SkScalarSqrt(sSecond) }
        }
        return true
    }

    /**
     * Apply this matrix to a point. With perspective, returns the
     * homogeneous-divided result `(x/w, y/w)`.
     */
    public fun mapXY(x: SkScalar, y: SkScalar): Pair<SkScalar, SkScalar> {
        val px = sx * x + kx * y + tx
        val py = ky * x + sy * y + ty
        if (!hasPerspective()) return Pair(px, py)
        val w = persp0 * x + persp1 * y + persp2
        val invW = if (w != 0f) 1f / w else 0f
        return Pair(px * invW, py * invW)
    }

    /** Apply this matrix to a [SkPoint], returning a new mapped point. */
    public fun mapXY(p: SkPoint): SkPoint {
        val (x, y) = mapXY(p.fX, p.fY)
        return SkPoint(x, y)
    }

    /**
     * Apply only the linear part (drop translation) — used for direction
     * vectors. Mirrors Skia's `SkMatrix::mapVector(dx, dy)`.
     */
    public fun mapVector(dx: SkScalar, dy: SkScalar): SkPoint {
        val dst = Array(1) { SkPoint() }
        mapVectors(dst, arrayOf(SkPoint(dx, dy)), 1)
        return dst[0]
    }

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
            } else if (type and kPerspective_Mask == 0) {
                // Full affine, no perspective.
                for (i in 0 until count) {
                    val x = src[i].fX
                    val y = src[i].fY
                    dst[i] = SkPoint(sx * x + kx * y + tx, ky * x + sy * y + ty)
                }
            } else {
                // Perspective: full 3×3 with homogeneous divide.
                for (i in 0 until count) {
                    val x = src[i].fX
                    val y = src[i].fY
                    val w = persp0 * x + persp1 * y + persp2
                    val invW = if (w != 0f) 1f / w else 0f
                    dst[i] = SkPoint(
                        (sx * x + kx * y + tx) * invW,
                        (ky * x + sy * y + ty) * invW,
                    )
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
     * Mirrors Skia's [`SkMatrix::mapVectors`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1108).
     *
     * For perspective matrices the linear part is **not** simply the
     * 2×2 sub-matrix: upstream takes the discrete derivative of
     * `mapPointPerspective` at the origin, i.e. `mapPoint(src) -
     * mapPoint(0, 0)`. The naive `(sx*x + kx*y, ky*x + sy*y)` is only
     * correct for affine matrices.
     */
    public fun mapVectors(dst: Array<SkPoint>, src: Array<SkPoint>, count: Int) {
        require(count <= dst.size && count <= src.size)
        if (hasPerspective()) {
            // Perspective: dst[i] = mapPoint(src[i]) - mapPoint(0, 0).
            // Matches the upstream `mapPointPerspective(src) -
            // mapPointPerspective({0, 0})` formula (SkMatrix.cpp:1108-1122),
            // which is the local Jacobian times src.
            val originW = persp2
            val originInvW = if (originW != 0f) 1f / originW else 0f
            val originX = tx * originInvW
            val originY = ty * originInvW
            // Walk backwards so dst === src aliasing stays well-defined
            // when count > 1 (mirrors upstream's reverse loop).
            for (i in count - 1 downTo 0) {
                val x = src[i].fX
                val y = src[i].fY
                val w = persp0 * x + persp1 * y + persp2
                val invW = if (w != 0f) 1f / w else 0f
                val px = (sx * x + kx * y + tx) * invW
                val py = (ky * x + sy * y + ty) * invW
                dst[i] = SkPoint(px - originX, py - originY)
            }
        } else if (getType() and (kAffine_Mask or kScale_Mask) == 0) {
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
     * device space. Skia maps `(r, 0)` and `(0, r)`, takes their lengths,
     * then returns the geometric mean.
     *
     * Mirrors [`SkMatrix::mapRadius`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp).
     */
    public fun mapRadius(r: SkScalar): SkScalar {
        val vec = arrayOf(SkPoint(r, 0f), SkPoint(0f, r))
        mapVectors(vec)

        val d0 = SkScalarSqrt(vec[0].fX * vec[0].fX + vec[0].fY * vec[0].fY)
        val d1 = SkScalarSqrt(vec[1].fX * vec[1].fX + vec[1].fY * vec[1].fY)
        return SkScalarSqrt(d0 * d1)
    }

    /** Pre-concat: `this = this · other`. Mirrors `SkMatrix::preConcat`. */
    public fun preConcat(other: SkMatrix): SkMatrix = concat(this, other)

    /** Post-concat: `this = other · this`. Mirrors `SkMatrix::postConcat`. */
    public fun postConcat(other: SkMatrix): SkMatrix = concat(other, this)

    /**
     * `M.preTranslate(dx, dy)` ≡ `M · Translate(dx, dy)`. Affine fast
     * path; perspective dispatches through [preConcat] because
     * `M · T` also updates the `persp2` row entry. Mirrors Skia's
     * [`SkMatrix::preTranslate`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L267)
     * dispatch (mask <= kTranslate / mask & kPerspective / else).
     */
    public fun preTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
        if (hasPerspective()) return preConcat(MakeTrans(dx, dy))
        // Closed form for affine: keeps numerical precision (no accumulating
        // Identity multiplies).
        return copy(tx = tx + sx * dx + kx * dy, ty = ty + ky * dx + sy * dy)
    }

    /**
     * `M.preScale(sx_, sy_)` ≡ `M · Scale(sx_, sy_)`. Closed-form per-cell
     * multiply (mirrors Skia's `preScale` ([SkMatrix.cpp:329](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L329))).
     * The perspective-row updates (`persp0 *= sx_`, `persp1 *= sy_`) are
     * unconditional — they collapse to no-op on affine matrices (`persp0
     * = persp1 = 0`) but propagate correctly through perspective matrices.
     */
    public fun preScale(sx_: SkScalar, sy_: SkScalar): SkMatrix =
        copy(
            sx = sx * sx_, kx = kx * sy_,
            ky = ky * sx_, sy = sy * sy_,
            persp0 = persp0 * sx_, persp1 = persp1 * sy_,
        )

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

    // ─── post* convenience family (sugar over postConcat) ───────────────

    /**
     * `M.postTranslate(dx, dy)` ≡ `T(dx, dy) · M`. Affine fast path; for
     * perspective matrices the closed form would also need to update the
     * scale columns (since `T · M = [[sx, kx, tx], [ky, sy, ty], [persp0,
     * persp1, persp2]] + [[dx*persp0, dx*persp1, dx*persp2], ...]`), so
     * we delegate to [postConcat] there. Mirrors Skia's
     * [`SkMatrix::postTranslate`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L285).
     */
    public fun postTranslate(dx: SkScalar, dy: SkScalar): SkMatrix {
        if (hasPerspective()) return postConcat(MakeTrans(dx, dy))
        return copy(tx = tx + dx, ty = ty + dy)
    }

    /**
     * `M.postScale(sx_, sy_)` ≡ `S(sx_, sy_) · M`. Closed-form per-cell
     * multiply (the scale row scales row 0 of M; the y row scales row 1).
     * Persp row stays untouched. Affine-correct AND perspective-correct
     * unconditionally. Mirrors Skia's [`SkMatrix::postScale`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L373).
     */
    public fun postScale(sx_: SkScalar, sy_: SkScalar): SkMatrix {
        if (sx_ == 1f && sy_ == 1f) return this
        return copy(
            sx = sx * sx_, kx = kx * sx_, tx = tx * sx_,
            ky = ky * sy_, sy = sy * sy_, ty = ty * sy_,
        )
    }

    /**
     * `M.postScale(sx_, sy_, px, py)` ≡ `S(sx_, sy_, px, py) · M`,
     * keeping `(px, py)` in *device space* fixed. Mirrors Skia's
     * [`SkMatrix::postScale`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L364).
     */
    public fun postScale(sx_: SkScalar, sy_: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        if (sx_ == 1f && sy_ == 1f) this else postConcat(MakeScale(sx_, sy_, px, py))

    /** `M.postRotate(deg)` ≡ `R(deg) · M`. */
    public fun postRotate(deg: SkScalar): SkMatrix = postConcat(MakeRotate(deg))

    /**
     * `M.postRotate(deg, px, py)` ≡ `R(deg, px, py) · M`, where the
     * rotation is anchored at `(px, py)` in *device space*.
     */
    public fun postRotate(deg: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        postConcat(MakeRotate(deg, px, py))

    /** `M.postSkew(kx_, ky_)` ≡ `Skew(kx_, ky_) · M`. */
    public fun postSkew(kx_: SkScalar, ky_: SkScalar): SkMatrix =
        postConcat(MakeSkew(kx_, ky_))

    /**
     * `M.postSkew(kx_, ky_, px, py)` ≡ `Skew(kx_, ky_, px, py) · M`.
     */
    public fun postSkew(kx_: SkScalar, ky_: SkScalar, px: SkScalar, py: SkScalar): SkMatrix =
        postConcat(MakeSkew(kx_, ky_, px, py))

    /**
     * Kotlin-idiomatic matrix multiply: `a * b ≡ SkMatrix.concat(a, b)`.
     * A point `p` is mapped first by `b`, then by `a`. Mirrors Skia's
     * `operator*` on the C++ class.
     */
    public operator fun times(other: SkMatrix): SkMatrix = concat(this, other)

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
        if (hasPerspective()) return invertPerspective()
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

    /**
     * Full 3×3 cofactor inverse for perspective matrices. Mirrors
     * Skia's `ComputeInv` perspective branch
     * ([SkMatrix.cpp:792](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L792)).
     * The det is computed in double; each cofactor is `(a*b - c*d)*invDet`
     * via [dcrossDscale] to preserve precision.
     */
    private fun invertPerspective(): SkMatrix? {
        // 3x3 determinant via cofactor expansion along the first row.
        val a = sy.toDouble() * persp2 - ty.toDouble() * persp1
        val b = ty.toDouble() * persp0 - ky.toDouble() * persp2
        val c = ky.toDouble() * persp1 - sy.toDouble() * persp0
        val det = sx.toDouble() * a + kx.toDouble() * b + tx.toDouble() * c
        if (SkScalarNearlyZero(det.toFloat(), SK_DetNearlyZero)) return null
        val invDet = 1.0 / det
        // Adjugate (transpose of cofactor matrix), each entry scaled by invDet.
        val isx = dcrossDscale(sy, persp2, ty, persp1, invDet)
        val ikx = dcrossDscale(tx, persp1, kx, persp2, invDet)
        val itx = dcrossDscale(kx, ty, tx, sy, invDet)
        val iky = dcrossDscale(ty, persp0, ky, persp2, invDet)
        val isy = dcrossDscale(sx, persp2, tx, persp0, invDet)
        val ity = dcrossDscale(tx, ky, sx, ty, invDet)
        val ip0 = dcrossDscale(ky, persp1, sy, persp0, invDet)
        val ip1 = dcrossDscale(kx, persp0, sx, persp1, invDet)
        val ip2 = dcrossDscale(sx, sy, kx, ky, invDet)
        return SkMatrix(
            sx = isx, kx = ikx, tx = itx,
            ky = iky, sy = isy, ty = ity,
            persp0 = ip0, persp1 = ip1, persp2 = ip2,
        )
    }

    public companion object {
        public val Identity: SkMatrix = SkMatrix()

        /**
         * Function-form alias for [Identity] matching Skia's static
         * accessor `SkMatrix::I()` ([SkMatrix.cpp:1464](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1464)).
         */
        public fun I(): SkMatrix = Identity

        // ─── 9-element matrix index constants (Skia row-major) ──────────
        public const val kMScaleX: Int = 0
        public const val kMSkewX: Int = 1
        public const val kMTransX: Int = 2
        public const val kMSkewY: Int = 3
        public const val kMScaleY: Int = 4
        public const val kMTransY: Int = 5
        public const val kMPersp0: Int = 6
        public const val kMPersp1: Int = 7
        public const val kMPersp2: Int = 8

        // ─── 6-element affine index constants (Skia COLUMN-major: a, c, b, d, e, f) ──
        public const val kAScaleX: Int = 0
        public const val kASkewY: Int = 1
        public const val kASkewX: Int = 2
        public const val kAScaleY: Int = 3
        public const val kATransX: Int = 4
        public const val kATransY: Int = 5

        // ─── Internal markers for the min/max scale solver ─────────────
        public const val SCALE_KIND_MIN: Int = 0
        public const val SCALE_KIND_MAX: Int = 1
        public const val SCALE_KIND_BOTH: Int = 2

        /**
         * Build a matrix that maps `src` onto `dst` per the given
         * [ScaleToFit] mode. Returns `null` if `src` is empty (matches
         * Skia's `Rect2Rect`).
         *
         * Mirrors Skia's [`SkMatrix::Rect2Rect`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L559).
         */
        public fun MakeRectToRect(
            src: SkRect,
            dst: SkRect,
            stf: ScaleToFit = ScaleToFit.kFill_ScaleToFit,
        ): SkMatrix? {
            if (src.isEmpty) return null
            var sx = if (src.width() == 0f) Float.POSITIVE_INFINITY else dst.width() / src.width()
            var sy = if (src.height() == 0f) Float.POSITIVE_INFINITY else dst.height() / src.height()
            var xLarger = false

            if (stf != ScaleToFit.kFill_ScaleToFit) {
                if (sx > sy) { xLarger = true; sx = sy } else { sy = sx }
            }

            var tx = dst.left - src.left * sx
            var ty = dst.top - src.top * sy
            if (stf == ScaleToFit.kCenter_ScaleToFit || stf == ScaleToFit.kEnd_ScaleToFit) {
                var diff = if (xLarger) dst.width() - src.width() * sy
                else dst.height() - src.height() * sy
                if (stf == ScaleToFit.kCenter_ScaleToFit) diff *= 0.5f
                if (xLarger) tx += diff else ty += diff
            }
            return SkMatrix(sx = sx, kx = 0f, tx = tx, ky = 0f, sy = sy, ty = ty)
        }

        /**
         * Mirrors Skia's `SkMatrix::RectToRectOrIdentity(src, dst)`.
         * Same as [MakeRectToRect] with `kFill_ScaleToFit` but returns
         * [Identity] instead of `null` when [src] is empty or degenerate.
         */
        public fun RectToRectOrIdentity(src: SkRect, dst: SkRect): SkMatrix =
            MakeRectToRect(src, dst, ScaleToFit.kFill_ScaleToFit) ?: Identity

        /**
         * Mirrors Skia's `SkMatrix::setPolyToPoly(src, dst, count)`.
         *
         * Computes the projective transform that maps source points to the
         * corresponding destination points (0 ≤ count ≤ 4).
         * Returns `null` when the system is degenerate (no unique solution).
         */
        public fun setPolyToPoly(src: Array<SkPoint>, dst: Array<SkPoint>): SkMatrix? =
            MakePolyToPoly(src, dst)

        /**
         * Build a matrix from a 9-element row-major buffer. The
         * perspective row is taken verbatim — pass `[0, 0, 1]` for an
         * affine matrix. Mirrors Skia's
         * [`SkMatrix::set9`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L55).
         */
        public fun MakeFrom9(buffer: FloatArray): SkMatrix {
            require(buffer.size >= 9) { "MakeFrom9 expects ≥ 9 elements (got ${buffer.size})" }
            return SkMatrix(
                sx = buffer[kMScaleX], kx = buffer[kMSkewX], tx = buffer[kMTransX],
                ky = buffer[kMSkewY], sy = buffer[kMScaleY], ty = buffer[kMTransY],
                persp0 = buffer[kMPersp0], persp1 = buffer[kMPersp1], persp2 = buffer[kMPersp2],
            )
        }

        /**
         * Build a matrix that applies pure perspective with the given
         * x/y biases (`persp2 = 1`). Mirrors Skia's `SkMatrix::setAll`
         * with the perspective row `[p0, p1, 1]`.
         */
        public fun MakePerspective(p0: SkScalar, p1: SkScalar): SkMatrix =
            SkMatrix(persp0 = p0, persp1 = p1)

        /**
         * Build a matrix from a 6-element COLUMN-major affine buffer
         * `[scaleX, skewY, skewX, scaleY, transX, transY]`. Mirrors
         * Skia's [`SkMatrix::setAffine`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L61).
         */
        public fun MakeFromAffine(buffer: FloatArray): SkMatrix {
            require(buffer.size >= 6) { "MakeFromAffine expects ≥ 6 elements (got ${buffer.size})" }
            return SkMatrix(
                sx = buffer[kAScaleX], kx = buffer[kASkewX], tx = buffer[kATransX],
                ky = buffer[kASkewY], sy = buffer[kAScaleY], ty = buffer[kATransY],
            )
        }

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
        public const val kRectStaysRect_Mask: Int = 0x10

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
        public fun computeTypeMask(
            sx: Float, kx: Float, ky: Float, sy: Float, tx: Float, ty: Float,
            persp0: Float, persp1: Float, persp2: Float,
        ): Int {
            // Perspective short-circuit: once perspective is on, Skia ORs all
            // bits including kRectStaysRect_Mask. Match that to keep mask
            // monotonic with respect to type-decay (an affine portion of a
            // perspective matrix shouldn't claim to preserve axis alignment).
            if (persp0 != 0f || persp1 != 0f || persp2 != 1f) {
                return kTranslate_Mask or kScale_Mask or kAffine_Mask or kPerspective_Mask or
                    kRectStaysRect_Mask
            }
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
        public fun isDegenerate2x2(sx: Float, kx: Float, ky: Float, sy: Float): Boolean {
            val perpDot = sx * sy - kx * ky
            return SkScalarNearlyZero(perpDot, SK_ScalarNearlyZero * SK_ScalarNearlyZero)
        }

        public fun MakeTrans(dx: SkScalar, dy: SkScalar): SkMatrix =
            SkMatrix(tx = dx, ty = dy)

        /**
         * Vector-form `MakeTrans` overload — Skia C++ exposes both
         * `Translate(SkScalar dx, SkScalar dy)` and `Translate(SkVector v)`
         * for hand-port readability.
         */
        public fun MakeTrans(v: SkVector): SkMatrix = MakeTrans(v.fX, v.fY)

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
         * Dispatches between the affine 6-cell formula and a full 3×3
         * `rowcol3` multiply when either input has perspective (mirrors
         * Skia's setConcat short-circuit, src/core/SkMatrix.cpp:615).
         * Each `a*b + c*d` cross-term is promoted to `double` before the
         * final round to `float` via `muladdmul`, matching Skia's
         * precision-tightening trick.
         */
        public fun concat(a: SkMatrix, b: SkMatrix): SkMatrix {
            if (a.hasPerspective() || b.hasPerspective()) {
                // Full 3×3 multiply via rowcol3.
                return SkMatrix(
                    sx = rowcol3(a.sx, a.kx, a.tx, b.sx, b.ky, b.persp0),
                    kx = rowcol3(a.sx, a.kx, a.tx, b.kx, b.sy, b.persp1),
                    tx = rowcol3(a.sx, a.kx, a.tx, b.tx, b.ty, b.persp2),
                    ky = rowcol3(a.ky, a.sy, a.ty, b.sx, b.ky, b.persp0),
                    sy = rowcol3(a.ky, a.sy, a.ty, b.kx, b.sy, b.persp1),
                    ty = rowcol3(a.ky, a.sy, a.ty, b.tx, b.ty, b.persp2),
                    persp0 = rowcol3(a.persp0, a.persp1, a.persp2, b.sx, b.ky, b.persp0),
                    persp1 = rowcol3(a.persp0, a.persp1, a.persp2, b.kx, b.sy, b.persp1),
                    persp2 = rowcol3(a.persp0, a.persp1, a.persp2, b.tx, b.ty, b.persp2),
                )
            }
            // Affine fast path: perspective row stays at default (0, 0, 1).
            return SkMatrix(
                sx = muladdmul(a.sx, b.sx, a.kx, b.ky),
                kx = muladdmul(a.sx, b.kx, a.kx, b.sy),
                tx = muladdmul(a.sx, b.tx, a.kx, b.ty) + a.tx,
                ky = muladdmul(a.ky, b.sx, a.sy, b.ky),
                sy = muladdmul(a.ky, b.kx, a.sy, b.sy),
                ty = muladdmul(a.ky, b.tx, a.sy, b.ty) + a.ty,
            )
        }

        /** Skia src/core/SkMatrix.cpp:603 — `(double)a*b + (double)c*d`, then round. */
        private fun muladdmul(a: Float, b: Float, c: Float, d: Float): Float =
            (a.toDouble() * b + c.toDouble() * d).toFloat()

        /**
         * Skia src/core/SkMatrix.cpp:607 — `a*b + c*d + e*f`, computed in
         * float (Skia uses native float `rowcol3`; the leading two terms
         * dominate, third is small). Used by the perspective concat path.
         */
        private fun rowcol3(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Float =
            a * d + b * e + c * f

        /** Skia src/core/SkMatrix.cpp `ComputeInv` — `(a*b - c*d) * scale` in double. */
        private fun dcrossDscale(a: Float, b: Float, c: Float, d: Float, scale: Double): Float =
            ((a.toDouble() * b - c.toDouble() * d) * scale).toFloat()

        /**
         * `MakeAll(sx, kx, tx, ky, sy, ty)` — verbatim row-major construction
         * for callers that have all six scalars to hand (typically test
         * fixtures or hand-translated `SkMatrix::MakeAll(...)` from C++).
         * Perspective row defaults to `[0, 0, 1]` (affine matrix).
         */
        public fun MakeAll(
            sx: SkScalar, kx: SkScalar, tx: SkScalar,
            ky: SkScalar, sy: SkScalar, ty: SkScalar,
        ): SkMatrix = SkMatrix(sx, kx, tx, ky, sy, ty)

        /**
         * 9-argument `MakeAll` — full row-major 3×3 construction. Mirrors
         * Skia's `SkMatrix::MakeAll(scaleX, skewX, transX, skewY, scaleY,
         * transY, pers0, pers1, pers2)` ([SkMatrix.h](https://github.com/google/skia/blob/main/include/core/SkMatrix.h)).
         */
        public fun MakeAll(
            sx: SkScalar, kx: SkScalar, tx: SkScalar,
            ky: SkScalar, sy: SkScalar, ty: SkScalar,
            p0: SkScalar, p1: SkScalar, p2: SkScalar,
        ): SkMatrix = SkMatrix(sx, kx, tx, ky, sy, ty, p0, p1, p2)

        // ─── RSXform factory ─────────────────────────────────────────────

        /**
         * Build the matrix that corresponds to a Skia `SkRSXform`:
         * rotation + uniform scale + translation, optionally pivoted
         * around `(anchorX, anchorY)`. `scos` and `ssin` are
         * `cos(angle) * scale` and `sin(angle) * scale`. Mirrors
         * Skia's [`SkMatrix::setRSXform`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L424).
         */
        public fun MakeRSXform(
            scos: SkScalar, ssin: SkScalar, tx: SkScalar, ty: SkScalar,
        ): SkMatrix = SkMatrix(
            sx = scos, kx = -ssin, tx = tx,
            ky = ssin, sy = scos, ty = ty,
        )

        /**
         * Pivoted `MakeRSXform`: applies rotation + scale around
         * `(anchor.x, anchor.y)` then translates by `(tx, ty)`. Equivalent
         * to `T(tx, ty) · R(scos, ssin) · T(-anchorX, -anchorY)`.
         * Mirrors Skia's `SkRSXform::toQuad` style construction.
         */
        public fun MakeRSXform(
            scos: SkScalar, ssin: SkScalar,
            anchorX: SkScalar, anchorY: SkScalar,
            tx: SkScalar, ty: SkScalar,
        ): SkMatrix = SkMatrix(
            sx = scos, kx = -ssin, tx = tx - scos * anchorX + ssin * anchorY,
            ky = ssin, sy = scos, ty = ty + (-ssin * anchorX - scos * anchorY),
        )

        // ─── Polygon-to-polygon factory ─────────────────────────────────

        /**
         * Build a matrix that maps `src[0..count-1]` onto `dst[0..count-1]`,
         * with `count` in `0..4`. Returns `null` if the mapping doesn't
         * exist (degenerate source) or sizes mismatch / exceed 4.
         *
         *  - 0 points → identity.
         *  - 1 point → translate.
         *  - 2 points → rotate-scale-translate (uniform scale).
         *  - 3 points → general affine (no perspective needed).
         *  - 4 points → general projective (perspective).
         *
         * Algorithm mirrors Skia's
         * [`SkMatrix::PolyToPoly`](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1306):
         * build `srcMap` from src, invert, build `dstMap` from dst,
         * return `dstMap · srcMap⁻¹`.
         */
        public fun MakePolyToPoly(src: Array<SkPoint>, dst: Array<SkPoint>): SkMatrix? {
            if (src.size != dst.size || src.size > 4) return null
            return when (src.size) {
                0 -> Identity
                1 -> MakeTrans(dst[0].fX - src[0].fX, dst[0].fY - src[0].fY)
                2, 3, 4 -> {
                    val srcMap = polyToMap(src) ?: return null
                    val srcInv = srcMap.invert() ?: return null
                    val dstMap = polyToMap(dst) ?: return null
                    concat(dstMap, srcInv)
                }
                else -> null
            }
        }

        /** Dispatcher for [Poly2Proc] / [Poly3Proc] / [Poly4Proc]. */
        private fun polyToMap(pts: Array<SkPoint>): SkMatrix? = when (pts.size) {
            2 -> Poly2Proc(pts)
            3 -> Poly3Proc(pts)
            4 -> Poly4Proc(pts)
            else -> null
        }

        /**
         * Mirrors Skia's `Poly2Proc` ([SkMatrix.cpp:1214](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1214)):
         * the 2-point fit is rotate-scale-translate built so that the
         * basis maps `(0, 0) → src[0]` and `(1, 0) → src[1]`.
         */
        private fun Poly2Proc(p: Array<SkPoint>): SkMatrix = SkMatrix(
            sx = p[1].fY - p[0].fY,
            kx = p[1].fX - p[0].fX,
            tx = p[0].fX,
            ky = p[0].fX - p[1].fX,
            sy = p[1].fY - p[0].fY,
            ty = p[0].fY,
        )

        /** Mirrors Skia's `Poly3Proc` ([SkMatrix.cpp:1230](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1230)). */
        private fun Poly3Proc(p: Array<SkPoint>): SkMatrix = SkMatrix(
            sx = p[2].fX - p[0].fX,
            kx = p[1].fX - p[0].fX,
            tx = p[0].fX,
            ky = p[2].fY - p[0].fY,
            sy = p[1].fY - p[0].fY,
            ty = p[0].fY,
        )

        /**
         * Mirrors Skia's `Poly4Proc` ([SkMatrix.cpp:1246](https://github.com/google/skia/blob/main/src/core/SkMatrix.cpp#L1246)).
         * Solves for the perspective coefficients `a1`, `a2` such that
         * the four corners `p[0..3]` are mapped to `(0,0), (1,0), (1,1),
         * (0,1)` (the unit square). Returns `null` on degenerate input
         * (Skia's `checkForZero(denom)` short-circuit).
         */
        private fun Poly4Proc(p: Array<SkPoint>): SkMatrix? {
            val x0 = p[2].fX - p[0].fX
            val y0 = p[2].fY - p[0].fY
            val x1 = p[2].fX - p[1].fX
            val y1 = p[2].fY - p[1].fY
            val x2 = p[2].fX - p[3].fX
            val y2 = p[2].fY - p[3].fY

            val a1: Float
            // |x2| > |y2| ?
            val xLargerThanYIn2 =
                if (x2 > 0f) (if (y2 > 0f) x2 > y2 else x2 > -y2)
                else (if (y2 > 0f) -x2 > y2 else x2 < y2)
            if (xLargerThanYIn2) {
                val denom = (x1 * y2) / x2 - y1
                if (checkForZero(denom)) return null
                a1 = (((x0 - x1) * y2 / x2) - y0 + y1) / denom
            } else {
                val denom = x1 - (y1 * x2) / y2
                if (checkForZero(denom)) return null
                a1 = (x0 - x1 - ((y0 - y1) * x2) / y2) / denom
            }

            val a2: Float
            val xLargerThanYIn1 =
                if (x1 > 0f) (if (y1 > 0f) x1 > y1 else x1 > -y1)
                else (if (y1 > 0f) -x1 > y1 else x1 < y1)
            if (xLargerThanYIn1) {
                val denom = y2 - (x2 * y1) / x1
                if (checkForZero(denom)) return null
                a2 = (y0 - y2 - ((x0 - x2) * y1) / x1) / denom
            } else {
                val denom = (y2 * x1) / y1 - x2
                if (checkForZero(denom)) return null
                a2 = (((y0 - y2) * x1) / y1 - x0 + x2) / denom
            }

            return SkMatrix(
                sx = a2 * p[3].fX + p[3].fX - p[0].fX,
                kx = a1 * p[1].fX + p[1].fX - p[0].fX,
                tx = p[0].fX,
                ky = a2 * p[3].fY + p[3].fY - p[0].fY,
                sy = a1 * p[1].fY + p[1].fY - p[0].fY,
                ty = p[0].fY,
                persp0 = a2,
                persp1 = a1,
                persp2 = 1f,
            )
        }

        /** Skia's `checkForZero` — `x*x == 0` (catches subnormals). */
        private fun checkForZero(x: Float): Boolean = x * x == 0f
    }
}
