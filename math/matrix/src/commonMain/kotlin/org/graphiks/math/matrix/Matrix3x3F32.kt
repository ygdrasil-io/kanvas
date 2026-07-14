package org.graphiks.math.matrix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import org.graphiks.math.scalar.cos
import org.graphiks.math.scalar.interp
import org.graphiks.math.scalar.nearlyEqual
import org.graphiks.math.scalar.nearlyZero
import org.graphiks.math.scalar.sin
import org.graphiks.math.scalar.tan
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.vector.Vector4F32

/**
 * 2D transformation matrix — full 3 × 3 homogeneous representation.
 * Stored row-major:
 *
 * ```
 * [ sx      kx      tx     ]   [ x ]   [ sx·x + kx·y + tx     ]
 * [ ky      sy      ty     ] · [ y ] = [ ky·x + sy·y + ty     ]   →  divide by w
 * [ persp0  persp1  persp2 ]   [ 1 ]   [ p0·x + p1·y + p2 = w ]
 * ```
 *
 * The default-constructed matrix is the identity (no scale, skew,
 * translate, or perspective).
 *
 * Operations come in two flavours:
 *  - **`pre*` (default for `canvas.translate/scale/rotate/skew/concat`)**:
 *    `M = M · L` — the local transform is applied to the *source* coords
 *    first, then the prior CTM.
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
public data class Matrix3x3F32(
    val sx: Float = 1f,
    val kx: Float = 0f,
    val tx: Float = 0f,
    val ky: Float = 0f,
    val sy: Float = 1f,
    val ty: Float = 0f,
    val persp0: Float = 0f,
    val persp1: Float = 0f,
    val persp2: Float = 1f,
) {
    /**
     * `ScaleToFit` describes how [Companion.MakeRectToRect] maps one
     * rect to another.
     */
    public enum class ScaleToFit {
        /** Stretch independently in x and y to fill `dst`. */
        fillScaleToFit,
        /** Uniform scale; align to top-left of `dst`. */
        startScaleToFit,
        /** Uniform scale; centre within `dst`. */
        centerScaleToFit,
        /** Uniform scale; align to bottom-right of `dst`. */
        endScaleToFit,
    }

    /**
     * Cached type mask, computed once at construction. Bit-OR of the
     * `k*_Mask` constants in the companion object.cpp:101](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L101))
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
     * for this affine-only port.
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
     * / scale (no rotation, skew, or perspective).
     */
    public fun isScaleTranslate(): Boolean =
        (getType() and (kAffine_Mask or kPerspective_Mask)) == 0

    /**
     * Legacy alias for [isScaleTranslate] kept for the existing kanvas
     * call sites. Renamed conceptually now that we expose the full
     * type-mask system; callers writing new code should prefer
     * [isScaleTranslate] for naming consistency.
     */
    public val isAxisAligned: Boolean get() = isScaleTranslate()

    /**
     * `true` if the matrix maps a rect to a rect — identity, scale,
     * cardinal-angle rotation, mirror, plus optional translation. Mirrors the rect stays rect algorithm).
     */
    public fun rectStaysRect(): Boolean = (fTypeMask and kRectStaysRect_Mask) != 0

    public fun preservesAxisAlignment(): Boolean = rectStaysRect()

    /** Always `false` in this affine-only port. */
    public fun hasPerspective(): Boolean = (getType() and kPerspective_Mask) != 0

    /**
     * `true` if the matrix is a rotation + uniform scale + translate
     * (a similarity transform).
     */
    public fun isSimilarity(tol: Float = 1e-7f): Boolean {
        val mask = getType()
        if (mask <= kTranslate_Mask) return true
        if (mask and kPerspective_Mask != 0) return false

        if (mask and kAffine_Mask == 0) {
            // No skew — just compare scale magnitudes.
            return !nearlyZero(sx) &&
                nearlyEqual(abs(sx), abs(sy))
        }
        // Degenerate 2x2 ⇒ no inverse ⇒ not a similarity.
        if (isDegenerate2x2(sx, kx, ky, sy)) return false

        // Upper 2x2 is rotation/reflection + uniform scale iff basis vectors are
        // 90° rotations of each other.
        return (nearlyEqual(sx, sy, tol) && nearlyEqual(kx, -ky, tol)) ||
            (nearlyEqual(sx, -sy, tol) && nearlyEqual(kx, ky, tol))
    }

    /**
     * `true` if the matrix maps perpendicular axes to perpendicular axes
     * (i.e. preserves right angles).com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L213).
     */
    public fun preservesRightAngles(tol: Float = 1e-7f): Boolean {
        val mask = getType()
        if (mask <= kTranslate_Mask) return true
        if (mask and kPerspective_Mask != 0) return false
        if (isDegenerate2x2(sx, kx, ky, sy)) return false
        // Upper 2x2 is scale + rotation iff basis vectors are orthogonal.
        val dot = sx * kx + ky * sy        // (sx, ky) · (kx, sy)
        return nearlyZero(dot, tol * tol)
    }

    /**
     * IEEE-strict (NaN-asymmetric) equality.
     * `operator==`. The data-class generated `equals` uses
     * `Float.equals` (NaN-friendly), so use this when porting hot-path
     * C++ that relies on the IEEE semantic.
     */
    public fun cheapEqualTo(other: Matrix3x3F32): Boolean =
        sx == other.sx && kx == other.kx && tx == other.tx &&
            ky == other.ky && sy == other.sy && ty == other.ty &&
            persp0 == other.persp0 && persp1 == other.persp1 && persp2 == other.persp2

    // ─── Function-style accessors ───────────────────────────────────────

    /** Equivalent to direct field [sx] access. */
    public fun getScaleX(): Float = sx
    /** Equivalent to direct field [sy] access. */
    public fun getScaleY(): Float = sy
    /** Accessor for matrix element. */
    public fun getSkewX(): Float = kx
    /** Accessor for matrix element. */
    public fun getSkewY(): Float = ky
    /** Accessor for matrix element. */
    public fun getTranslateX(): Float = tx
    /** Accessor for matrix element. */
    public fun getTranslateY(): Float = ty

    /** Always `0` for this affine port (perspective row is hardcoded `[0, 0, 1]`). */
    public fun getPerspX(): Float = persp0
    /** Returns `kMPersp1` (`persp1`). */
    public fun getPerspY(): Float = persp1

    /**
     * Determinant of the upper 2×2 (linear part). Equivalent to
     * `sx * sy - kx * ky`. Used by [mapRadius] and the inverse algorithm.
     */
    public fun det2x2(): Float = sx * sy - kx * ky

    /**
     * Full 3×3 determinant via cofactor expansion along the first row.
     * For an affine matrix (perspective row `[0, 0, 1]`) this collapses
     * to [det2x2].
     */
    public fun det(): Float =
        sx * (sy * persp2 - ty * persp1) -
            kx * (ky * persp2 - ty * persp0) +
            tx * (ky * persp1 - sy * persp0)

    // ─── Array exchange ─────────────────────────────────────────────────

    /**
     * Fill `buffer[0..8]` with the matrix in row-major 9-tuple
     * order: `[sx, kx, tx, ky, sy, ty, persp0, persp1, persp2]`.
     *
     */
    public fun get9(buffer: FloatArray) {
        require(buffer.size >= 9) { "get9 buffer must have ≥ 9 elements (got ${buffer.size})" }
        buffer[0] = sx; buffer[1] = kx; buffer[2] = tx
        buffer[3] = ky; buffer[4] = sy; buffer[5] = ty
        buffer[6] = persp0; buffer[7] = persp1; buffer[8] = persp2
    }

    /**
     * Fill `buffer[0..5]` with the affine 6-tuple in column-major
     * order: `[scaleX, skewY, skewX, scaleY, transX, transY]`. Note the
     * subtle reordering vs `get9` — affine arrays are stored as
     * `[a, c, b, d, e, f]` where the matrix is `[[a, b, e], [c, d, f]]`.
     *
     * Returns `false` (and leaves `buffer` untouched) when this matrix
     * has perspective — the affine 6-tuple cannot represent it.
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
     * persp0*x + persp1*y + persp2)`.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L1080).
     */
    public fun mapHomogeneousPoints(
        dst: Array<Vector3F32>,
        src: Array<Vector2F32>,
        count: Int = src.size,
    ) {
        require(count <= dst.size && count <= src.size)
        if (isIdentity) {
            for (i in 0 until count) dst[i] = Vector3F32.of(src[i].x, src[i].y, 1f)
        } else if (hasPerspective()) {
            for (i in 0 until count) {
                val x = src[i].x
                val y = src[i].y
                dst[i] = Vector3F32.of(
                    sx * x + kx * y + tx,
                    ky * x + sy * y + ty,
                    persp0 * x + persp1 * y + persp2,
                )
            }
        } else {
            for (i in 0 until count) {
                val x = src[i].x
                val y = src[i].y
                dst[i] = Vector3F32.of(sx * x + kx * y + tx, ky * x + sy * y + ty, 1f)
            }
        }
    }

    // ─── Singular values / scale decomposition ──────────────────────────

    /**
     * Largest singular value of the upper 2×2. For pure scale, returns
     * `max(|sx|, |sy|)`; for pure rotation, `1`; for any affine
     * combination, the longest semi-axis of the unit-circle's image.
     *
         */
    public fun getMaxScale(): Float {
        val results = FloatArray(1)
        if (computeScaleFactor(scaleKind = SCALE_KIND_MAX, results)) return results[0]
        return -1f   // unreachable for affine-only port
    }

    /**
     * Smallest singular value of the upper 2×2 — matches the getMinScale algorithm.
     * Returns `-1` if the matrix has perspective (never the case here).
     */
    public fun getMinScale(): Float {
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
     * to [getMaxScale] for compatibility.
     */
    @Deprecated(
        "Use getMaxScale() for naming consistency",
        ReplaceWith("getMaxScale()"),
    )
    public fun computeMaxScale(): Float = getMaxScale()

    /**
     * Decompose this matrix into a pure scale and a "remaining" rotation
     * + skew + translate component, such that
     * `this = remaining · S(scale.x, scale.y)`. Returns `null` when
     * the decomposition fails (perspective, non-finite, or near-singular
     * scales — matching the algorithm).
     *
     * Result type uses a `Pair` return because
     * `Matrix3x3F32` is immutable: returns a `Pair<Vector2F32, Matrix3x3F32>` of
     * `(scale, remaining)`.
     *
     * Mirrors [`Matrix3x3F32::decomposeScale`](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L1479).
     */
    public fun decomposeScale(): Pair<Vector2F32, Matrix3x3F32>? {
        if (hasPerspective()) return null
        val sxLen = sqrt(sx * sx + ky * ky)
        val syLen = sqrt(kx * kx + sy * sy)
        if (!sxLen.isFinite() || !syLen.isFinite() ||
            nearlyZero(sxLen) || nearlyZero(syLen)
        ) return null
        val remaining = preScale((1f / sxLen), (1f / syLen))
        return Pair(Vector2F32.of(sxLen, syLen), remaining)
    }

    /**
     * Internal min/max scale solver.cpp:1348](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L1348)).
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
            val ax = abs(sx)
            val ay = abs(sy)
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
        // [a b; b c] = MᵀM (computed in float; uses sdot).
        val a = sx * sx + ky * ky
        val b = sx * kx + sy * ky
        val c = kx * kx + sy * sy
        val bSqd = b * b
        val first: Float
        val second: Float
        if (bSqd <= 1e-7f * 1e-7f) {
            // Already orthogonal — singular values are sqrt(a), sqrt(c).
            first = minOf(a, c)
            second = maxOf(a, c)
        } else {
            val aMinusC = a - c
            val aPlusCDiv2 = (a + c) * 0.5f
            val x = sqrt(aMinusC * aMinusC + 4f * bSqd) * 0.5f
            first = aPlusCDiv2 - x   // smaller eigenvalue
            second = aPlusCDiv2 + x  // larger eigenvalue
        }
        // Floating-point may drive a near-zero negative; clamp to 0.
        val sFirst = if (!first.isFinite()) return false else if (first < 0f) 0f else first
        val sSecond = if (!second.isFinite()) return false else if (second < 0f) 0f else second
        when (scaleKind) {
            SCALE_KIND_MIN -> results[0] = sqrt(sFirst)
            SCALE_KIND_MAX -> results[0] = sqrt(sSecond)
            else -> { results[0] = sqrt(sFirst); results[1] = sqrt(sSecond) }
        }
        return true
    }

    /**
     * Apply this matrix to a point. With perspective, returns the
     * homogeneous-divided result `(x/w, y/w)`.
     */
    public fun mapXY(x: Float, y: Float): Pair<Float, Float> {
        val px = sx * x + kx * y + tx
        val py = ky * x + sy * y + ty
        if (!hasPerspective()) return Pair(px, py)
        val w = persp0 * x + persp1 * y + persp2
        val invW = if (w != 0f) 1f / w else 0f
        return Pair(px * invW, py * invW)
    }

    /** Apply this matrix to a [Vector2F32], returning a new mapped point. */
    public fun mapXY(p: Vector2F32): Vector2F32 {
        val (x, y) = mapXY(p.x, p.y)
        return Vector2F32.of(x, y)
    }

    /**
     * Apply only the linear part (drop translation) — used for direction
     * vectors.
     */
    public fun mapVector(dx: Float, dy: Float): Vector2F32 {
        val dst = Array(1) { Vector2F32.Zero }
        mapVectors(dst, arrayOf(Vector2F32.of(dx, dy)), 1)
        return dst[0]
    }

    /**
     * Bulk apply this matrix to `count` source points, writing the
     * result to `dst`. `dst` and `src` may alias. Type-mask fast paths
     * follow the type-dispatch pattern:
     *
     * - identity → straight copy
     * - translate-only → `+ (tx, ty)` per point
     * - scale + translate → `(sx*x + tx, sy*y + ty)` per point
     * - else → full affine
     *
     * Mirrors [`Matrix3x3F32::mapPoints`](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L782).
     */
    public fun mapPoints(dst: Array<Vector2F32>, src: Array<Vector2F32>, count: Int) {
        require(count <= dst.size && count <= src.size) {
            "mapPoints count=$count exceeds dst.size=${dst.size} or src.size=${src.size}"
        }
        when (val type = getType()) {
            kIdentity_Mask -> {
                for (i in 0 until count) {
                    dst[i] = Vector2F32.of(src[i].x, src[i].y)
                }
            }
            kTranslate_Mask -> {
                for (i in 0 until count) {
                    dst[i] = Vector2F32.of(src[i].x + tx, src[i].y + ty)
                }
            }
            else -> if (type and (kAffine_Mask or kPerspective_Mask) == 0) {
                // Scale + translate (no rotation/skew/perspective).
                for (i in 0 until count) {
                    dst[i] = Vector2F32.of(sx * src[i].x + tx, sy * src[i].y + ty)
                }
            } else if (type and kPerspective_Mask == 0) {
                // Full affine, no perspective.
                for (i in 0 until count) {
                    val x = src[i].x
                    val y = src[i].y
                    dst[i] = Vector2F32.of(sx * x + kx * y + tx, ky * x + sy * y + ty)
                }
            } else {
                // Perspective: full 3×3 with homogeneous divide.
                for (i in 0 until count) {
                    val x = src[i].x
                    val y = src[i].y
                    val w = persp0 * x + persp1 * y + persp2
                    val invW = if (w != 0f) 1f / w else 0f
                    dst[i] = Vector2F32.of(
                        (sx * x + kx * y + tx) * invW,
                        (ky * x + sy * y + ty) * invW,
                    )
                }
            }
        }
    }

    /** In-place bulk map. Equivalent to `mapPoints(pts, pts, count)`. */
    public fun mapPoints(pts: Array<Vector2F32>, count: Int = pts.size) {
        mapPoints(pts, pts, count)
    }

    /**
     * Bulk apply only the linear part (drop translation) to vectors.
         *
     * For perspective matrices the linear part is **not** simply the
     * 2×2 sub-matrix: the discrete derivative of
     * `mapPointPerspective` at the origin, i.e. `mapPoint(src) -
     * mapPoint(0, 0)`. The naive `(sx*x + kx*y, ky*x + sy*y)` is only
     * correct for affine matrices.
     */
    public fun mapVectors(dst: Array<Vector2F32>, src: Array<Vector2F32>, count: Int) {
        require(count <= dst.size && count <= src.size)
        if (hasPerspective()) {
            // Perspective: dst[i] = mapPoint(src[i]) - mapPoint(0, 0).
            // Uses the mapPoint - mapPoint(origin) formula,
            // which is the local Jacobian times src.
            val originW = persp2
            val originInvW = if (originW != 0f) 1f / originW else 0f
            val originX = tx * originInvW
            val originY = ty * originInvW
            // Walk backwards so dst === src aliasing stays well-defined
            // when count > 1 (reverse loop).
            for (i in count - 1 downTo 0) {
                val x = src[i].x
                val y = src[i].y
                val w = persp0 * x + persp1 * y + persp2
                val invW = if (w != 0f) 1f / w else 0f
                val px = (sx * x + kx * y + tx) * invW
                val py = (ky * x + sy * y + ty) * invW
                dst[i] = Vector2F32.of(px - originX, py - originY)
            }
        } else if (getType() and (kAffine_Mask or kScale_Mask) == 0) {
            // Identity or translate-only — vectors are unchanged.
            for (i in 0 until count) {
                if (dst !== src || dst[i] !== src[i]) dst[i] = Vector2F32.of(src[i].x, src[i].y)
            }
        } else {
            for (i in 0 until count) {
                val x = src[i].x
                val y = src[i].y
                dst[i] = Vector2F32.of(sx * x + kx * y, ky * x + sy * y)
            }
        }
    }

    public fun mapVectors(vectors: Array<Vector2F32>, count: Int = vectors.size) {
        mapVectors(vectors, vectors, count)
    }

    /**
     * Apply this matrix to a rect, returning the bounding box of the
     * transformed quad. Equivalent to `Matrix3x3F32::mapRect`.
     */
    public fun mapRect(r: RectF32): RectF32 {
        // Fast path: scale + translate preserves rect orientation, no
        // need to map all four corners.
        if (isScaleTranslate()) return mapRectScaleTranslate(r)

        val (x0, y0) = mapXY(r.left, r.top)
        val (x1, y1) = mapXY(r.right, r.top)
        val (x2, y2) = mapXY(r.right, r.bottom)
        val (x3, y3) = mapXY(r.left, r.bottom)
        return RectF32.ofLTRB(
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
         * Caller must verify `isScaleTranslate()` first.
     */
    public fun mapRectScaleTranslate(r: RectF32): RectF32 {
        check(isScaleTranslate()) {
            "mapRectScaleTranslate requires isScaleTranslate matrix; got mask=${getType()}"
        }
        val l1 = sx * r.left + tx
        val r1 = sx * r.right + tx
        val t1 = sy * r.top + ty
        val b1 = sy * r.bottom + ty
        return RectF32.ofLTRB(minOf(l1, r1), minOf(t1, b1), maxOf(l1, r1), maxOf(t1, b1))
    }

    /**
     * Heuristic mapped radius — for a circle with radius `r` mapped by
     * this matrix, returns the radius of a "representative" circle in
     * device space. maps `(r, 0)` and `(0, r)` to device space, takes their lengths,
     * then returns the geometric mean.
     *
     * Mirrors [`Matrix3x3F32::mapRadius`](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp).
     */
    public fun mapRadius(r: Float): Float {
        val vec = arrayOf(Vector2F32.of(r, 0f), Vector2F32.of(0f, r))
        mapVectors(vec)

        val d0 = sqrt(vec[0].x * vec[0].x + vec[0].y * vec[0].y)
        val d1 = sqrt(vec[1].x * vec[1].x + vec[1].y * vec[1].y)
        return sqrt(d0 * d1)
    }

    /** Pre-concat: `this = this · other` */
    public fun preConcat(other: Matrix3x3F32): Matrix3x3F32 = concat(this, other)

    /** Post-concat: `this = other · this` */
    public fun postConcat(other: Matrix3x3F32): Matrix3x3F32 = concat(other, this)

    /**
     * `M.preTranslate(dx, dy)` ≡ `M · Translate(dx, dy)`. Affine fast
     * path; perspective dispatches through [preConcat] because
     * `M · T` also updates the `persp2` row entry.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L267)
     * dispatch (mask <= kTranslate / mask & kPerspective / else).
     */
    public fun preTranslate(dx: Float, dy: Float): Matrix3x3F32 {
        if (hasPerspective()) return preConcat(translation(dx, dy))
        // Closed form for affine: keeps numerical precision (no accumulating
        // Identity multiplies).
        return copy(tx = tx + sx * dx + kx * dy, ty = ty + ky * dx + sy * dy)
    }

    /**
     * `M.preScale(sx_, sy_)` ≡ `M · Scale(sx_, sy_)`. Closed-form per-cell
     * multiply.
     * The perspective-row updates (`persp0 *= sx_`, `persp1 *= sy_`) are
     * unconditional — they collapse to no-op on affine matrices (`persp0
     * = persp1 = 0`) but propagate correctly through perspective matrices.
     */
    public fun preScale(sx_: Float, sy_: Float): Matrix3x3F32 =
        copy(
            sx = sx * sx_, kx = kx * sy_,
            ky = ky * sx_, sy = sy * sy_,
            persp0 = persp0 * sx_, persp1 = persp1 * sy_,
        )

    /**
     * `M.preScale(sx_, sy_, px, py)` ≡ `M · S(sx_, sy_, px, py)` where the
     * scale leaves the pivot `(px, py)` fixed: `T(px, py) · Scale · T(-px, -py)`.
         */
    public fun preScale(sx_: Float, sy_: Float, px: Float, py: Float): Matrix3x3F32 =
        if (sx_ == 1f && sy_ == 1f) this else preConcat(scaling(sx_, sy_, px, py))

    /**
     * `M.preRotate(deg)` ≡ `M · Rotate(deg)` around the origin.
     * Use [preRotate] with `(deg, px, py)` for a rotation around an
     * arbitrary pivot.
     */
    public fun preRotate(deg: Float): Matrix3x3F32 = preConcat(rotation(deg))

    public fun preRotate(deg: Float, px: Float, py: Float): Matrix3x3F32 =
        preConcat(rotation(deg, px, py))

    /** `M.preSkew(kx_, ky_)` ≡ `M · Skew(kx_, ky_)`. */
    public fun preSkew(kx_: Float, ky_: Float): Matrix3x3F32 =
        preConcat(skewing(kx_, ky_))

    /**
     * `M.preSkew(kx_, ky_, px, py)` ≡ `M · Skew(kx_, ky_, px, py)` where the
     * skew leaves the pivot `(px, py)` fixed.
     */
    public fun preSkew(kx_: Float, ky_: Float, px: Float, py: Float): Matrix3x3F32 =
        preConcat(skewing(kx_, ky_, px, py))

    // ─── post* convenience family (sugar over postConcat) ───────────────

    /**
     * `M.postTranslate(dx, dy)` ≡ `T(dx, dy) · M`. Affine fast path; for
     * perspective matrices the closed form would also need to update the
     * scale columns (since `T · M = [[sx, kx, tx], [ky, sy, ty], [persp0,
     * persp1, persp2]] + [[dx*persp0, dx*persp1, dx*persp2], ...]`), so
     * we delegate to [postConcat] there.
     */
    public fun postTranslate(dx: Float, dy: Float): Matrix3x3F32 {
        if (hasPerspective()) return postConcat(translation(dx, dy))
        return copy(tx = tx + dx, ty = ty + dy)
    }

    /**
     * `M.postScale(sx_, sy_)` ≡ `S(sx_, sy_) · M`. Closed-form per-cell
     * multiply (the scale row scales row 0 of M; the y row scales row 1).
     * Persp row stays untouched. Affine-correct AND perspective-correct
     * unconditionally.
     */
    public fun postScale(sx_: Float, sy_: Float): Matrix3x3F32 {
        if (sx_ == 1f && sy_ == 1f) return this
        return copy(
            sx = sx * sx_, kx = kx * sx_, tx = tx * sx_,
            ky = ky * sy_, sy = sy * sy_, ty = ty * sy_,
        )
    }

    /**
     * `M.postScale(sx_, sy_, px, py)` ≡ `S(sx_, sy_, px, py) · M`,
     * keeping `(px, py)` in *device space* fixed.
     */
    public fun postScale(sx_: Float, sy_: Float, px: Float, py: Float): Matrix3x3F32 =
        if (sx_ == 1f && sy_ == 1f) this else postConcat(scaling(sx_, sy_, px, py))

    /** `M.postRotate(deg)` ≡ `R(deg) · M`. */
    public fun postRotate(deg: Float): Matrix3x3F32 = postConcat(rotation(deg))

    /**
     * `M.postRotate(deg, px, py)` ≡ `R(deg, px, py) · M`, where the
     * rotation is anchored at `(px, py)` in *device space*.
     */
    public fun postRotate(deg: Float, px: Float, py: Float): Matrix3x3F32 =
        postConcat(rotation(deg, px, py))

    /** `M.postSkew(kx_, ky_)` ≡ `Skew(kx_, ky_) · M`. */
    public fun postSkew(kx_: Float, ky_: Float): Matrix3x3F32 =
        postConcat(skewing(kx_, ky_))

    /**
     * `M.postSkew(kx_, ky_, px, py)` ≡ `Skew(kx_, ky_, px, py) · M`.
     */
    public fun postSkew(kx_: Float, ky_: Float, px: Float, py: Float): Matrix3x3F32 =
        postConcat(skewing(kx_, ky_, px, py))

    /**
     * Kotlin-idiomatic matrix multiply: `a * b ≡ Matrix3x3F32.concat(a, b)`.
     * A point `p` is mapped first by `b`, then by `a`.
     */
    public operator fun times(other: Matrix3x3F32): Matrix3x3F32 = concat(this, other)

    /**
     * Inverse of this affine matrix, or `null` if the linear part is
     * singular or near-singular.
     *
     * For the 2 × 2 linear part `[[sx, kx], [ky, sy]]` with determinant
     * `det = sx·sy − kx·ky`, the inverse linear part is
     * `(1/det) · [[sy, −kx], [−ky, sx]]`. The translate component of the
     * inverse is `−inverseLinear · (tx, ty)`.
     *
     * The determinant is computed in double-precision then compared to
     * `1e-7f³` (≈ 1.46e-11). A matrix whose `|det|` falls
     * below that returns `null` to avoid producing finite-but-garbage
     * inverse values; this matches the reference behaviour where a near-degenerate
     * matrix is treated as singular.
     *
     * Used by [SkShader] implementations to map device-space pixel coords
     * back into the shader's local coordinate system (where the gradient
     * geometry was defined).
     */
    public fun invert(): Matrix3x3F32? {
        if (hasPerspective()) return invertPerspective()
        val det = sx.toDouble() * sy - kx.toDouble() * ky
        if (nearlyZero(det.toFloat(), SK_DetNearlyZero)) return null
        val invDet = 1.0 / det
        val isx = (sy.toDouble() * invDet).toFloat()
        val ikx = (-kx.toDouble() * invDet).toFloat()
        val iky = (-ky.toDouble() * invDet).toFloat()
        val isy = (sx.toDouble() * invDet).toFloat()
        // Inverse translate via dcross_dscale to keep double precision through the
        // cross product (double-precision).
        val itx = dcrossDscale(kx, ty, sy, tx, invDet)
        val ity = dcrossDscale(ky, tx, sx, ty, invDet)
        return Matrix3x3F32(sx = isx, kx = ikx, tx = itx, ky = iky, sy = isy, ty = ity)
    }

    /**
     * Full 3×3 cofactor inverse for perspective matrices. Uses the
     * perspective branch of the cofactor method
     * ([Matrix3x3F32.cpp:792](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L792)).
     * The det is computed in double; each cofactor is `(a*b - c*d)*invDet`
     * via [dcrossDscale] to preserve precision.
     */
    private fun invertPerspective(): Matrix3x3F32? {
        // 3x3 determinant via cofactor expansion along the first row.
        val a = sy.toDouble() * persp2 - ty.toDouble() * persp1
        val b = ty.toDouble() * persp0 - ky.toDouble() * persp2
        val c = ky.toDouble() * persp1 - sy.toDouble() * persp0
        val det = sx.toDouble() * a + kx.toDouble() * b + tx.toDouble() * c
        if (nearlyZero(det.toFloat(), SK_DetNearlyZero)) return null
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
        return Matrix3x3F32(
            sx = isx, kx = ikx, tx = itx,
            ky = iky, sy = isy, ty = ity,
            persp0 = ip0, persp1 = ip1, persp2 = ip2,
        )
    }

    public companion object {
        /** Identity matrix. */
        public val Identity: Matrix3x3F32 = Matrix3x3F32()

        /**
         * Function-form alias for [Identity].
         */
        public fun I(): Matrix3x3F32 = Identity

        // ─── 9-element matrix index constants (row-major) ──────────────────
        /** Row-major index for scale X ([sx]). */
        private const val kMScaleX: Int = 0
        /** Row-major index for skew X ([kx]). */
        private const val kMSkewX: Int = 1
        /** Row-major index for translate X ([tx]). */
        private const val kMTransX: Int = 2
        /** Row-major index for skew Y ([ky]). */
        private const val kMSkewY: Int = 3
        /** Row-major index for scale Y ([sy]). */
        private const val kMScaleY: Int = 4
        /** Row-major index for translate Y ([ty]). */
        private const val kMTransY: Int = 5
        /** Row-major index for perspective 0 ([persp0]). */
        private const val kMPersp0: Int = 6
        /** Row-major index for perspective 1 ([persp1]). */
        private const val kMPersp1: Int = 7
        /** Row-major index for perspective 2 ([persp2]). */
        private const val kMPersp2: Int = 8

        // ─── 6-element affine index constants (column-major: a, c, b, d, e, f) ────────
        /** Column-major affine index for scale X. */
        private const val kAScaleX: Int = 0
        /** Column-major affine index for skew Y. */
        private const val kASkewY: Int = 1
        /** Column-major affine index for skew X. */
        private const val kASkewX: Int = 2
        /** Column-major affine index for scale Y. */
        private const val kAScaleY: Int = 3
        /** Column-major affine index for translate X. */
        private const val kATransX: Int = 4
        /** Column-major affine index for translate Y. */
        private const val kATransY: Int = 5

        // ─── Internal markers for the min/max scale solver ─────────────
        /** Compute minimum singular value only. */
        private const val SCALE_KIND_MIN: Int = 0
        /** Compute maximum singular value only. */
        private const val SCALE_KIND_MAX: Int = 1
        /** Compute both min and max singular values. */
        private const val SCALE_KIND_BOTH: Int = 2

        /**
         * Build a matrix that maps `src` onto `dst` per the given
         * [ScaleToFit] mode. Returns `null` if `src` is empty (matches
        .
         *
                 */
        public fun rectToRect(
            src: RectF32,
            dst: RectF32,
            stf: ScaleToFit = ScaleToFit.fillScaleToFit,
        ): Matrix3x3F32? {
            if (src.isEmpty) return null
            var sx = if (src.width() == 0f) Float.POSITIVE_INFINITY else dst.width() / src.width()
            var sy = if (src.height() == 0f) Float.POSITIVE_INFINITY else dst.height() / src.height()
            var xLarger = false

            if (stf != ScaleToFit.fillScaleToFit) {
                if (sx > sy) { xLarger = true; sx = sy } else { sy = sx }
            }

            var tx = dst.left - src.left * sx
            var ty = dst.top - src.top * sy
            if (stf == ScaleToFit.centerScaleToFit || stf == ScaleToFit.endScaleToFit) {
                var diff = if (xLarger) dst.width() - src.width() * sy
                else dst.height() - src.height() * sy
                if (stf == ScaleToFit.centerScaleToFit) diff *= 0.5f
                if (xLarger) tx += diff else ty += diff
            }
            return Matrix3x3F32(sx = sx, kx = 0f, tx = tx, ky = 0f, sy = sy, ty = ty)
        }

        /**
                 * Same as [MakeRectToRect] with `fillScaleToFit` but returns
         * [Identity] instead of `null` when [src] is empty or degenerate.
         */
        public fun RectToRectOrIdentity(src: RectF32, dst: RectF32): Matrix3x3F32 =
            rectToRect(src, dst, ScaleToFit.fillScaleToFit) ?: Identity

        /**
                 *
         * Computes the projective transform that maps source points to the
         * corresponding destination points (0 ≤ count ≤ 4).
         * Returns `null` when the system is degenerate (no unique solution).
         */
        public fun setPolyToPoly(src: Array<Vector2F32>, dst: Array<Vector2F32>): Matrix3x3F32? =
            polyToPoly(src, dst)

        /**
         * Build a matrix from a 9-element row-major buffer. The
         * perspective row is taken verbatim — pass `[0, 0, 1]` for an
         * affine matrix.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L55).
         */
        public fun from9(buffer: FloatArray): Matrix3x3F32 {
            require(buffer.size >= 9) { "MakeFrom9 expects ≥ 9 elements (got ${buffer.size})" }
            return Matrix3x3F32(
                sx = buffer[kMScaleX], kx = buffer[kMSkewX], tx = buffer[kMTransX],
                ky = buffer[kMSkewY], sy = buffer[kMScaleY], ty = buffer[kMTransY],
                persp0 = buffer[kMPersp0], persp1 = buffer[kMPersp1], persp2 = buffer[kMPersp2],
            )
        }

        /**
         * Build a matrix that applies pure perspective with the given
         * x/y biases (`persp2 = 1`).
         */
        public fun perspective(p0: Float, p1: Float): Matrix3x3F32 =
            Matrix3x3F32(persp0 = p0, persp1 = p1)

        /**
         * Build a matrix from a 6-element COLUMN-major affine buffer
         * `[scaleX, skewY, skewX, scaleY, transX, transY]`. Mirrors
                 */
        public fun fromAffine(buffer: FloatArray): Matrix3x3F32 {
            require(buffer.size >= 6) { "MakeFromAffine expects ≥ 6 elements (got ${buffer.size})" }
            return Matrix3x3F32(
                sx = buffer[kAScaleX], kx = buffer[kASkewX], tx = buffer[kATransX],
                ky = buffer[kASkewY], sy = buffer[kAScaleY], ty = buffer[kATransY],
            )
        }

        // ─── TypeMask constants ───────────────────────────────────────────────


        /** No scale, skew, or translate. */
        private const val kIdentity_Mask: Int = 0
        /** Translation only. */
        private const val kTranslate_Mask: Int = 0x01
        /** Scale (uniform or non-uniform). */
        private const val kScale_Mask: Int = 0x02
        /** Skew or rotate. */
        private const val kAffine_Mask: Int = 0x04
        /** Perspective — never set in this affine-only port. */
        private const val kPerspective_Mask: Int = 0x08
        /**
         * Internal "rect stays rect" mask — set when the upper 2x2 maps a rect
         * to a rect (axis-aligned, 90° rotation, or mirror). This bit is kept out of the public 4-bit subset returned by `getType()`.
         */
        private const val kRectStaysRect_Mask: Int = 0x10

        /**
         * Compute the type mask for a 6-tuple affine matrix.cpp:101](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L101))
         * with the perspective branch removed.
         *
         * The skew-non-zero branch short-circuits on `(m01 | m10) != 0`:
         * any skew implies `kAffine_Mask | kScale_Mask`, then `rectStaysRect`
         * holds iff the primary diagonal (`sx`, `sy`) is all zero AND the
         * secondary diagonal (`kx`, `ky`) is all non-zero — i.e. a 90°-class
         * rotation. The no-skew branch is simpler: `rectStaysRect` holds iff
         * the primary diagonal is non-zero (translate / scale / mirror).
         */
        private fun computeTypeMask(
            sx: Float, kx: Float, ky: Float, sy: Float, tx: Float, ty: Float,
            persp0: Float, persp1: Float, persp2: Float,
        ): Int {
            // Perspective short-circuit: once perspective is on, OR all
            // bits including kRectStaysRect_Mask. Match that to keep mask
            // monotonic with respect to type-decay (an affine portion of a
            // perspective matrix shouldn't claim to preserve axis alignment).
            if (persp0 != 0f || persp1 != 0f || persp2 != 1f) {
                return kTranslate_Mask or kScale_Mask or kAffine_Mask or kPerspective_Mask
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

        /** Checks if the 2x2 matrix is degenerate. Used by [isSimilarity] / [preservesRightAngles]. */
        private fun isDegenerate2x2(sx: Float, kx: Float, ky: Float, sy: Float): Boolean {
            val perpDot = sx * sy - kx * ky
            return nearlyZero(perpDot, 1e-7f * 1e-7f)
        }

        /**
         * Translation matrix.
         */
        public fun translation(x: Float, y: Float): Matrix3x3F32 =
            Matrix3x3F32(tx = x, ty = y)

        /**
         * Vector-form `translation` overload.
         */
        public fun translation(v: Vector2F32): Matrix3x3F32 = translation(v.x, v.y)

        /**
         * Non-uniform scale matrix.
         */
        public fun scaling(x: Float, y: Float): Matrix3x3F32 =
            Matrix3x3F32(sx = x, sy = y)

        /** Uniform scale matrix. */
        public fun scaling(scale: Float): Matrix3x3F32 = scaling(scale, scale)

        /**
         * Scale around a pivot `(px, py)`, equivalent to
         * `T(px, py) · S(sx, sy) · T(-px, -py)`. Closed form mirrors
                 * `tx = px - sx*px`, `ty = py - sy*py`.
         */
        public fun scaling(x: Float, y: Float, pivotX: Float, pivotY: Float): Matrix3x3F32 =
            if (x == 1f && y == 1f) Identity
            else Matrix3x3F32(sx = x, kx = 0f, tx = pivotX - x * pivotX, ky = 0f, sy = y, ty = pivotY - y * pivotY)

        /**
         * Rotation matrix around the origin, angle in **degrees** (
         * convention). Positive angle is clockwise in screen-space (y-down).
         *
         * `sin` and `cos` results within `SK_ScalarSinCosNearlyZero` of zero
         * are snapped to exactly `0f`. This guarantees that `rotation(90)`,
         * `rotation(180)`, etc. produce bit-exact axis-aligned matrices,
         * so [isAxisAligned] returns `true` for cardinal angles instead of
         * tripping over a `~6e-8` cosine residue.
         */
        public fun rotation(degrees: Float): Matrix3x3F32 {
            val rad = (degrees * (PI.toFloat() / 180f))
            val s = sin(rad)
            val c = cos(rad)
            // Avoid `-0f` from `-s` when `s` was snapped to `+0f`: explicit
            // negation that preserves the positive-zero representation.
            val negS = if (s == 0f) 0f else -s
            return Matrix3x3F32(sx = c, kx = negS, ky = s, sy = c)
        }

        /**
         * Singular-determinant threshold for [invert]. Compares
         * `|det|` (cast back to float) against `1e-7f³`
         * (cube of `1f / 4096` ≈ 1.4552e-11).
         */
        private const val SK_DetNearlyZero: Float =
            (1f / 4096f) * (1f / 4096f) * (1f / 4096f)

        /**
         * Rotation around a pivot point `(pivotX, pivotY)`.
         * Equivalent to `T(pivotX, pivotY) · R(degrees) · T(-pivotX, -pivotY)`.
         */
        public fun rotation(degrees: Float, pivotX: Float, pivotY: Float): Matrix3x3F32 {
            // T(pivotX, pivotY) · R(degrees) · T(-pivotX, -pivotY).
            return translation(pivotX, pivotY).preConcat(rotation(degrees)).preConcat(translation(-pivotX, -pivotY))
        }

        /**
         * Skew matrix.
         */
        public fun skewing(kx: Float, ky: Float): Matrix3x3F32 =
            Matrix3x3F32(kx = kx, ky = ky)

        /**
         * Skew around a pivot `(px, py)`, equivalent to
         * `T(px, py) · Skew(kx, ky) · T(-px, -py)`. Closed form mirrors
                 * `tx = -kx*py`, `ty = -ky*px`.
         */
        public fun skewing(kx: Float, ky: Float, pivotX: Float, pivotY: Float): Matrix3x3F32 =
            Matrix3x3F32(sx = 1f, kx = kx, tx = -kx * pivotY, ky = ky, sy = 1f, ty = -ky * pivotX)

        /**
         * Matrix multiply: returns `a · b`. A point `p` is mapped first by
         * `b`, then by `a`: `(a · b).map(p) == a.map(b.map(p))`.
         *
         * Dispatches between the affine 6-cell formula and a full 3×3
         * `rowcol3` multiply when either input has perspective (mirrors
                 * Each `a*b + c*d` cross-term is promoted to `double` before the
         * final round to `float` via `muladdmul`.
         */
        public fun concat(a: Matrix3x3F32, b: Matrix3x3F32): Matrix3x3F32 {
            if (a.hasPerspective() || b.hasPerspective()) {
                // Full 3×3 multiply via rowcol3.
                return Matrix3x3F32(
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
            return Matrix3x3F32(
                sx = muladdmul(a.sx, b.sx, a.kx, b.ky),
                kx = muladdmul(a.sx, b.kx, a.kx, b.sy),
                tx = muladdmul(a.sx, b.tx, a.kx, b.ty) + a.tx,
                ky = muladdmul(a.ky, b.sx, a.sy, b.ky),
                sy = muladdmul(a.ky, b.kx, a.sy, b.sy),
                ty = muladdmul(a.ky, b.tx, a.sy, b.ty) + a.ty,
            )
        }


        private fun muladdmul(a: Float, b: Float, c: Float, d: Float): Float =
            (a.toDouble() * b + c.toDouble() * d).toFloat()

        /**
         * Computes `a*b + c*d + e*f` in float (the leading two terms
         * dominate, third is small). Used by the perspective concat path.
         */
        private fun rowcol3(a: Float, b: Float, c: Float, d: Float, e: Float, f: Float): Float =
            a * d + b * e + c * f


        private fun dcrossDscale(a: Float, b: Float, c: Float, d: Float, scale: Double): Float =
            ((a.toDouble() * b - c.toDouble() * d) * scale).toFloat()

        /**
         * `of(sx, kx, tx, ky, sy, ty)` — verbatim row-major construction
         * for callers that have all six scalars to hand (typically test
         * fixtures or hand-translated `Matrix3x3F32::of(...)` from C++).
         * Perspective row defaults to `[0, 0, 1]` (affine matrix).
         */
        public fun of(
            sx: Float, kx: Float, tx: Float,
            ky: Float, sy: Float, ty: Float,
        ): Matrix3x3F32 = Matrix3x3F32(sx, kx, tx, ky, sy, ty)

        /**
         * 9-argument `of` — full row-major 3×3 construction. Mirrors
        .
         */
        public fun of(
            sx: Float, kx: Float, tx: Float,
            ky: Float, sy: Float, ty: Float,
            persp0: Float, persp1: Float, persp2: Float,
        ): Matrix3x3F32 = Matrix3x3F32(sx, kx, tx, ky, sy, ty, persp0, persp1, persp2)

        // ─── RSXform factory ─────────────────────────────────────────────

        /**
         * Build the matrix from scale, rotation, and translation:
         * rotation + uniform scale + translation, optionally pivoted
         * around `(anchorX, anchorY)`. `scos` and `ssin` are
         * `cos(angle) * scale` and `sin(angle) * scale`. Mirrors
                 */
        public fun rsXForm(
            scos: Float, ssin: Float, tx: Float, ty: Float,
        ): Matrix3x3F32 = Matrix3x3F32(
            sx = scos, kx = -ssin, tx = tx,
            ky = ssin, sy = scos, ty = ty,
        )

        /**
         * Pivoted `MakeRSXform`: applies rotation + scale around
         * `(anchor.x, anchor.y)` then translates by `(tx, ty)`. Equivalent
         * to `T(tx, ty) · R(scos, ssin) · T(-anchorX, -anchorY)`.
                 */
        public fun rsXForm(
            scos: Float, ssin: Float,
            anchorX: Float, anchorY: Float,
            tx: Float, ty: Float,
        ): Matrix3x3F32 = Matrix3x3F32(
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
                 * [`Matrix3x3F32::PolyToPoly`](https://github.com/google/skia/blob/main/src/core/Matrix3x3F32.cpp#L1306):
         * build `srcMap` from src, invert, build `dstMap` from dst,
         * return `dstMap · srcMap⁻¹`.
         */
        public fun polyToPoly(src: Array<Vector2F32>, dst: Array<Vector2F32>): Matrix3x3F32? {
            if (src.size != dst.size || src.size > 4) return null
            return when (src.size) {
                0 -> Identity
                1 -> translation(dst[0].x - src[0].x, dst[0].y - src[0].y)
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
        private fun polyToMap(pts: Array<Vector2F32>): Matrix3x3F32? = when (pts.size) {
            2 -> Poly2Proc(pts)
            3 -> Poly3Proc(pts)
            4 -> Poly4Proc(pts)
            else -> null
        }

        /**
         * Computes the type mask):
         * the 2-point fit is rotate-scale-translate built so that the
         * basis maps `(0, 0) → src[0]` and `(1, 0) → src[1]`.
         */
        private fun Poly2Proc(p: Array<Vector2F32>): Matrix3x3F32 = Matrix3x3F32(
            sx = p[1].y - p[0].y,
            kx = p[1].x - p[0].x,
            tx = p[0].x,
            ky = p[0].x - p[1].x,
            sy = p[1].y - p[0].y,
            ty = p[0].y,
        )

        /** Polynomial solver for the 3-point poly-to-poly system. */
        private fun Poly3Proc(p: Array<Vector2F32>): Matrix3x3F32 = Matrix3x3F32(
            sx = p[2].x - p[0].x,
            kx = p[1].x - p[0].x,
            tx = p[0].x,
            ky = p[2].y - p[0].y,
            sy = p[1].y - p[0].y,
            ty = p[0].y,
        )

        /**
         * Computes the type mask).
         * Solves for the perspective coefficients `a1`, `a2` such that
         * the four corners `p[0..3]` are mapped to `(0,0), (1,0), (1,1),
         * (0,1)` (the unit square). Returns `null` on degenerate input
         * (zero-check short-circuit).
         */
        private fun Poly4Proc(p: Array<Vector2F32>): Matrix3x3F32? {
            val x0 = p[2].x - p[0].x
            val y0 = p[2].y - p[0].y
            val x1 = p[2].x - p[1].x
            val y1 = p[2].y - p[1].y
            val x2 = p[2].x - p[3].x
            val y2 = p[2].y - p[3].y

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

            return Matrix3x3F32(
                sx = a2 * p[3].x + p[3].x - p[0].x,
                kx = a1 * p[1].x + p[1].x - p[0].x,
                tx = p[0].x,
                ky = a2 * p[3].y + p[3].y - p[0].y,
                sy = a1 * p[1].y + p[1].y - p[0].y,
                ty = p[0].y,
                persp0 = a2,
                persp1 = a1,
                persp2 = 1f,
            )
        }


        private fun checkForZero(x: Float): Boolean = x * x == 0f
    }
}
