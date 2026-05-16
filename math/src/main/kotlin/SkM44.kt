package org.graphiks.math

/**
 * 4×4 transformation matrix — iso-aligned port of upstream Skia's
 * `SkM44` ([include/core/SkM44.h:150](https://github.com/google/skia/blob/main/include/core/SkM44.h#L150),
 * [src/core/SkM44.cpp](https://github.com/google/skia/blob/main/src/core/SkM44.cpp)).
 *
 * Storage is a length-16 `FloatArray` in **column-major** layout —
 * matching the upstream memory layout and OpenGL/Vulkan conventions:
 *
 * ```
 *  index  0  4  8 12        col 0  col 1  col 2  col 3
 *  index  1  5  9 13          ↓     ↓      ↓      ↓
 *  index  2  6 10 14
 *  index  3  7 11 15
 * ```
 *
 * The class is **mutable** to mirror Skia's `setIdentity / setRotate /
 * preTranslate / postConcat` in-place idioms. For value-class semantics
 * use the `SkM44(other)` copy constructor or [copy].
 *
 * Skia assumes a right-handed coordinate system: +X right, +Y down,
 * +Z into the screen.
 *
 * SkCanvas integration is deferred — this port is standalone and does
 * not yet feed the canvas matrix stack.
 */
public class SkM44 {

    /**
     * Backing array — 16 floats, column-major.
     * `m[col * 4 + row]` is the element at row `row`, column `col`.
     */
    public val fMat: FloatArray = FloatArray(16)

    // ─── Constructors ──────────────────────────────────────────────────

    /** Default constructor: identity matrix. */
    public constructor() {
        setIdentity()
    }

    /** Copy constructor — clones [src]. */
    public constructor(src: SkM44) {
        System.arraycopy(src.fMat, 0, fMat, 0, 16)
    }

    /**
     * Compose constructor: `this = a · b`. Mirrors Skia's
     * `SkM44(const SkM44& a, const SkM44& b) { setConcat(a, b); }`.
     */
    public constructor(a: SkM44, b: SkM44) {
        setConcat(a, b)
    }

    /**
     * Row-major scalar constructor. Parameters are listed in **row-major**
     * order (`m0 m4 m8 m12` is the first row), but stored column-major.
     * Mirrors Skia's `SkM44(SkScalar m0, SkScalar m4, …)` constructor
     * ([SkM44.h:184](https://github.com/google/skia/blob/main/include/core/SkM44.h#L184)).
     */
    public constructor(
        m0: Float, m4: Float, m8: Float, m12: Float,
        m1: Float, m5: Float, m9: Float, m13: Float,
        m2: Float, m6: Float, m10: Float, m14: Float,
        m3: Float, m7: Float, m11: Float, m15: Float,
    ) {
        fMat[0] = m0; fMat[1] = m1; fMat[2] = m2; fMat[3] = m3
        fMat[4] = m4; fMat[5] = m5; fMat[6] = m6; fMat[7] = m7
        fMat[8] = m8; fMat[9] = m9; fMat[10] = m10; fMat[11] = m11
        fMat[12] = m12; fMat[13] = m13; fMat[14] = m14; fMat[15] = m15
    }

    /**
     * Build an [SkM44] from a 3×3 [SkMatrix]. The third row/column of the
     * 4×4 stays identity; the perspective row maps to the bottom row of
     * the M44. Mirrors Skia's `explicit SkM44(const SkMatrix& src)`
     * ([SkM44.h:415](https://github.com/google/skia/blob/main/include/core/SkM44.h#L415)).
     *
     * ```
     * [ a b c ]      [ a b 0 c ]
     * [ d e f ]  ->  [ d e 0 f ]
     * [ g h i ]      [ 0 0 1 0 ]
     *                [ g h 0 i ]
     * ```
     */
    public constructor(src: SkMatrix) : this(
        src.sx,     src.kx,     0f, src.tx,
        src.ky,     src.sy,     0f, src.ty,
        0f,         0f,         1f, 0f,
        src.persp0, src.persp1, 0f, src.persp2,
    )

    // ─── Companion factories ───────────────────────────────────────────

    public companion object {
        /** Identity matrix. Mirrors upstream's default constructor. */
        public fun identity(): SkM44 = SkM44()

        /**
         * Translation matrix. Mirrors Skia's
         * [`SkM44::Translate`](https://github.com/google/skia/blob/main/include/core/SkM44.h#L225).
         */
        public fun translate(x: Float, y: Float, z: Float = 0f): SkM44 = SkM44(
            1f, 0f, 0f, x,
            0f, 1f, 0f, y,
            0f, 0f, 1f, z,
            0f, 0f, 0f, 1f,
        )

        /**
         * Diagonal scale matrix. Mirrors Skia's
         * [`SkM44::Scale`](https://github.com/google/skia/blob/main/include/core/SkM44.h#L232).
         */
        public fun scale(x: Float, y: Float, z: Float = 1f): SkM44 = SkM44(
            x,  0f, 0f, 0f,
            0f, y,  0f, 0f,
            0f, 0f, z,  0f,
            0f, 0f, 0f, 1f,
        )

        /**
         * Rotation around an arbitrary axis (radians). The axis is
         * normalized internally; identity is returned for a zero or
         * non-finite axis. Mirrors Skia's
         * [`SkM44::Rotate`](https://github.com/google/skia/blob/main/include/core/SkM44.h#L239)
         * and [`SkM44::setRotate`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L279).
         */
        public fun rotate(axis: SkV3, radians: Float): SkM44 = SkM44().also {
            it.setRotate(axis, radians)
        }

        /**
         * Build the view matrix that places the camera at `eye` looking
         * at `center` with the given `up` direction. Mirrors Skia's
         * [`SkM44::LookAt`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L331).
         */
        public fun lookAt(eye: SkV3, center: SkV3, up: SkV3): SkM44 {
            val f = normalize3(center - eye)
            val u = normalize3(up)
            val s = normalize3(f.cross(u))

            val cols = SkM44()
            cols.setCol(0, v4(s, 0f))
            cols.setCol(1, v4(s.cross(f), 0f))
            cols.setCol(2, v4(-f, 0f))
            cols.setCol(3, v4(eye, 1f))

            val out = SkM44()
            val inv = cols.invert()
            if (inv != null) {
                System.arraycopy(inv.fMat, 0, out.fMat, 0, 16)
            } else {
                out.setIdentity()
            }
            return out
        }

        /**
         * Perspective projection. Mirrors Skia's
         * [`SkM44::Perspective`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L343).
         * `near < far` and `angle != 0` are required.
         */
        public fun perspective(near: Float, far: Float, angle: Float): SkM44 {
            require(far > near) { "perspective: far ($far) must be > near ($near)" }
            val denomInv = 1f / (far - near)
            val halfAngle = angle * 0.5f
            require(halfAngle != 0f) { "perspective: angle ($angle) must be non-zero" }
            val cot = 1f / SkScalarTan(halfAngle)
            val m = SkM44()
            m.setRC(0, 0, cot)
            m.setRC(1, 1, cot)
            m.setRC(2, 2, (far + near) * denomInv)
            m.setRC(2, 3, 2f * far * near * denomInv)
            m.setRC(3, 2, -1f)
            // Note: m.rc(3, 3) stays at 1 (default identity), matching upstream
            // which doesn't reset it. See src/core/SkM44.cpp:343.
            return m
        }

        /**
         * Build a matrix that scales-and-translates `src` to fill `dst`
         * exactly. Mirrors Skia's
         * [`SkM44::RectToRect`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L304).
         */
        public fun rectToRect(src: SkRect, dst: SkRect): SkM44 {
            if (src.isEmpty) return SkM44()
            if (dst.isEmpty) return scale(0f, 0f, 0f)
            val sxF = dst.width() / src.width()
            val syF = dst.height() / src.height()
            val tx = dst.left - sxF * src.left
            val ty = dst.top - syF * src.top
            return SkM44(
                sxF, 0f,  0f, tx,
                0f,  syF, 0f, ty,
                0f,  0f,  1f, 0f,
                0f,  0f,  0f, 1f,
            )
        }

        /** Skia-style `Normalize` helper that returns `v` if `length` is near zero. */
        private fun normalize3(v: SkV3): SkV3 {
            val len = v.length()
            return if (kotlin.math.abs(len) < SK_ScalarNearlyZero) v else v * (1f / len)
        }

        private fun v4(v: SkV3, w: Float): SkV4 = SkV4(v.x, v.y, v.z, w)
    }

    // ─── Element access ────────────────────────────────────────────────

    /** Read element at `row`, `col` (0 <= row, col <= 3). */
    public fun rc(row: Int, col: Int): Float {
        require(row in 0..3 && col in 0..3) { "rc($row, $col) out of range" }
        return fMat[col * 4 + row]
    }

    /** Write element at `row`, `col` (0 <= row, col <= 3). */
    public fun setRC(row: Int, col: Int, value: Float) {
        require(row in 0..3 && col in 0..3) { "setRC($row, $col) out of range" }
        fMat[col * 4 + row] = value
    }

    /** Return row `i` as a [SkV4]. Mirrors Skia's `SkM44::row(int)`. */
    public fun row(i: Int): SkV4 {
        require(i in 0..3) { "row($i) out of range" }
        return SkV4(fMat[i + 0], fMat[i + 4], fMat[i + 8], fMat[i + 12])
    }

    /** Return column `i` as a [SkV4]. Mirrors Skia's `SkM44::col(int)`. */
    public fun col(i: Int): SkV4 {
        require(i in 0..3) { "col($i) out of range" }
        return SkV4(fMat[i * 4 + 0], fMat[i * 4 + 1], fMat[i * 4 + 2], fMat[i * 4 + 3])
    }

    /** Replace row `i` with `v`. Mirrors Skia's `SkM44::setRow`. */
    public fun setRow(i: Int, v: SkV4) {
        require(i in 0..3) { "setRow($i) out of range" }
        fMat[i + 0] = v.x
        fMat[i + 4] = v.y
        fMat[i + 8] = v.z
        fMat[i + 12] = v.w
    }

    /** Replace column `i` with `v`. Mirrors Skia's `SkM44::setCol`. */
    public fun setCol(i: Int, v: SkV4) {
        require(i in 0..3) { "setCol($i) out of range" }
        fMat[i * 4 + 0] = v.x
        fMat[i * 4 + 1] = v.y
        fMat[i * 4 + 2] = v.z
        fMat[i * 4 + 3] = v.w
    }

    /** Copy backing column-major storage into [v]. */
    public fun getColMajor(v: FloatArray) {
        require(v.size >= 16) { "getColMajor needs >= 16 elements (got ${v.size})" }
        System.arraycopy(fMat, 0, v, 0, 16)
    }

    /** Copy storage into [v] in row-major order (transposed). */
    public fun getRowMajor(v: FloatArray) {
        require(v.size >= 16) { "getRowMajor needs >= 16 elements (got ${v.size})" }
        transposeArrays(v, fMat)
    }

    // ─── State / setters ───────────────────────────────────────────────

    /** `true` if this is the identity matrix. */
    public fun isIdentity(): Boolean {
        return fMat[0] == 1f && fMat[5] == 1f && fMat[10] == 1f && fMat[15] == 1f &&
            fMat[1] == 0f && fMat[2] == 0f && fMat[3] == 0f &&
            fMat[4] == 0f && fMat[6] == 0f && fMat[7] == 0f &&
            fMat[8] == 0f && fMat[9] == 0f && fMat[11] == 0f &&
            fMat[12] == 0f && fMat[13] == 0f && fMat[14] == 0f
    }

    /** Reset to identity. */
    public fun setIdentity(): SkM44 = apply {
        fMat.fill(0f)
        fMat[0] = 1f
        fMat[5] = 1f
        fMat[10] = 1f
        fMat[15] = 1f
    }

    /** Reset to translation. */
    public fun setTranslate(x: Float, y: Float, z: Float = 0f): SkM44 = apply {
        fMat[0] = 1f; fMat[1] = 0f; fMat[2] = 0f; fMat[3] = 0f
        fMat[4] = 0f; fMat[5] = 1f; fMat[6] = 0f; fMat[7] = 0f
        fMat[8] = 0f; fMat[9] = 0f; fMat[10] = 1f; fMat[11] = 0f
        fMat[12] = x; fMat[13] = y; fMat[14] = z; fMat[15] = 1f
    }

    /** Reset to scale. */
    public fun setScale(x: Float, y: Float, z: Float = 1f): SkM44 = apply {
        fMat[0] = x;  fMat[1] = 0f; fMat[2] = 0f; fMat[3] = 0f
        fMat[4] = 0f; fMat[5] = y;  fMat[6] = 0f; fMat[7] = 0f
        fMat[8] = 0f; fMat[9] = 0f; fMat[10] = z; fMat[11] = 0f
        fMat[12] = 0f; fMat[13] = 0f; fMat[14] = 0f; fMat[15] = 1f
    }

    /**
     * Set this matrix to rotate about the **already unit-length** axis
     * `axis` by the given sin and cos. Mirrors Skia's
     * [`SkM44::setRotateUnitSinCos`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L262).
     */
    public fun setRotateUnitSinCos(axis: SkV3, sinAngle: Float, cosAngle: Float): SkM44 {
        val x = axis.x
        val y = axis.y
        val z = axis.z
        val c = cosAngle
        val s = sinAngle
        val t = 1 - c

        // Row-major as written upstream; stored column-major below.
        fMat[0] = t * x * x + c;     fMat[4] = t * x * y - s * z; fMat[8] = t * x * z + s * y; fMat[12] = 0f
        fMat[1] = t * x * y + s * z; fMat[5] = t * y * y + c;     fMat[9] = t * y * z - s * x; fMat[13] = 0f
        fMat[2] = t * x * z - s * y; fMat[6] = t * y * z + s * x; fMat[10] = t * z * z + c;   fMat[14] = 0f
        fMat[3] = 0f;                fMat[7] = 0f;                fMat[11] = 0f;              fMat[15] = 1f
        return this
    }

    /** Set this matrix to rotate about the unit-length `axis` by `radians`. */
    public fun setRotateUnit(axis: SkV3, radians: Float): SkM44 =
        setRotateUnitSinCos(axis, SkScalarSin(radians), SkScalarCos(radians))

    /**
     * Set this matrix to rotate about the (possibly un-normalised) axis
     * by `radians`. Normalises `axis` internally; falls back to
     * identity if `axis.length()` is zero or non-finite. Mirrors Skia's
     * [`SkM44::setRotate`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L279).
     */
    public fun setRotate(axis: SkV3, radians: Float): SkM44 {
        val len = axis.length()
        return if (len > 0f && len.isFinite()) {
            setRotateUnit(axis * (1f / len), radians)
        } else {
            setIdentity()
        }
    }

    // ─── Multiplication ────────────────────────────────────────────────

    /**
     * Set this = `a · b`. Result may alias `a` or `b` (writes are
     * staged in locals).
     */
    public fun setConcat(a: SkM44, b: SkM44): SkM44 {
        val a00 = a.fMat[0];  val a10 = a.fMat[1];  val a20 = a.fMat[2];  val a30 = a.fMat[3]
        val a01 = a.fMat[4];  val a11 = a.fMat[5];  val a21 = a.fMat[6];  val a31 = a.fMat[7]
        val a02 = a.fMat[8];  val a12 = a.fMat[9];  val a22 = a.fMat[10]; val a32 = a.fMat[11]
        val a03 = a.fMat[12]; val a13 = a.fMat[13]; val a23 = a.fMat[14]; val a33 = a.fMat[15]

        val out = FloatArray(16)
        for (c in 0..3) {
            val b0 = b.fMat[c * 4 + 0]
            val b1 = b.fMat[c * 4 + 1]
            val b2 = b.fMat[c * 4 + 2]
            val b3 = b.fMat[c * 4 + 3]
            out[c * 4 + 0] = a00 * b0 + a01 * b1 + a02 * b2 + a03 * b3
            out[c * 4 + 1] = a10 * b0 + a11 * b1 + a12 * b2 + a13 * b3
            out[c * 4 + 2] = a20 * b0 + a21 * b1 + a22 * b2 + a23 * b3
            out[c * 4 + 3] = a30 * b0 + a31 * b1 + a32 * b2 + a33 * b3
        }
        System.arraycopy(out, 0, fMat, 0, 16)
        return this
    }

    /** Returns a new matrix equal to `this · other`. */
    public operator fun times(other: SkM44): SkM44 = SkM44(this, other)

    /** In-place: `this = this · m`. Mirrors Skia's `preConcat`. */
    public fun preConcat(m: SkM44): SkM44 = setConcat(this, m)

    /** In-place: `this = m · this`. Mirrors Skia's `postConcat`. */
    public fun postConcat(m: SkM44): SkM44 = setConcat(m, this)

    /**
     * In-place pre-multiply by a 3×3 [SkMatrix] (promoted as in the
     * `SkMatrix` ctor). Mirrors Skia's
     * [`SkM44::preConcat(const SkMatrix&)`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L70).
     */
    public fun preConcat(b: SkMatrix): SkM44 = preConcat(SkM44(b))

    /**
     * In-place: pre-multiply by `translate(x, y, z)`. Mirrors
     * [`SkM44::preTranslate`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L89).
     */
    public fun preTranslate(x: Float, y: Float, z: Float = 0f): SkM44 {
        // Only the last column changes: c0*x + c1*y + c2*z + c3
        val c00 = fMat[0]; val c10 = fMat[1]; val c20 = fMat[2]; val c30 = fMat[3]
        val c01 = fMat[4]; val c11 = fMat[5]; val c21 = fMat[6]; val c31 = fMat[7]
        val c02 = fMat[8]; val c12 = fMat[9]; val c22 = fMat[10]; val c32 = fMat[11]
        val c03 = fMat[12]; val c13 = fMat[13]; val c23 = fMat[14]; val c33 = fMat[15]
        fMat[12] = c00 * x + c01 * y + c02 * z + c03
        fMat[13] = c10 * x + c11 * y + c12 * z + c13
        fMat[14] = c20 * x + c21 * y + c22 * z + c23
        fMat[15] = c30 * x + c31 * y + c32 * z + c33
        return this
    }

    /**
     * In-place: post-multiply by `translate(x, y, z)`. Mirrors
     * [`SkM44::postTranslate`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L100).
     *
     * `t = (x, y, z, 0)` and each column `c_i` of the result is
     * `c_i + t * c_i.w`.
     */
    public fun postTranslate(x: Float, y: Float, z: Float = 0f): SkM44 {
        for (c in 0..3) {
            val w = fMat[c * 4 + 3]
            fMat[c * 4 + 0] += x * w
            fMat[c * 4 + 1] += y * w
            fMat[c * 4 + 2] += z * w
        }
        return this
    }

    /**
     * In-place 2D pre-scale. Mirrors
     * [`SkM44::preScale(SkScalar, SkScalar)`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L109).
     */
    public fun preScale(x: Float, y: Float): SkM44 {
        fMat[0] *= x; fMat[1] *= x; fMat[2] *= x; fMat[3] *= x
        fMat[4] *= y; fMat[5] *= y; fMat[6] *= y; fMat[7] *= y
        return this
    }

    /**
     * In-place 3D pre-scale. Mirrors
     * [`SkM44::preScale(SkScalar, SkScalar, SkScalar)`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L118).
     */
    public fun preScale(x: Float, y: Float, z: Float): SkM44 {
        fMat[0] *= x; fMat[1] *= x; fMat[2] *= x; fMat[3] *= x
        fMat[4] *= y; fMat[5] *= y; fMat[6] *= y; fMat[7] *= y
        fMat[8] *= z; fMat[9] *= z; fMat[10] *= z; fMat[11] *= z
        return this
    }

    // ─── Mapping ───────────────────────────────────────────────────────

    /**
     * Apply this matrix to the 4-vector `(x, y, z, w)`. Mirrors Skia's
     * [`SkM44::map`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L129).
     */
    public fun map(x: Float, y: Float, z: Float, w: Float): SkV4 {
        val rx = fMat[0] * x + fMat[4] * y + fMat[8] * z + fMat[12] * w
        val ry = fMat[1] * x + fMat[5] * y + fMat[9] * z + fMat[13] * w
        val rz = fMat[2] * x + fMat[6] * y + fMat[10] * z + fMat[14] * w
        val rw = fMat[3] * x + fMat[7] * y + fMat[11] * z + fMat[15] * w
        return SkV4(rx, ry, rz, rw)
    }

    /** Convenience wrapper. */
    public fun map(v: SkV4): SkV4 = map(v.x, v.y, v.z, v.w)

    /** Operator overload: `this * v`. Mirrors C++ `operator*(const SkV4&)`. */
    public operator fun times(v: SkV4): SkV4 = map(v.x, v.y, v.z, v.w)

    /**
     * Operator overload: apply with `w = 0` (vector mapping, drops
     * translation). Mirrors C++ `operator*(SkV3)`.
     */
    public operator fun times(v: SkV3): SkV3 {
        val r = map(v.x, v.y, v.z, 0f)
        return SkV3(r.x, r.y, r.z)
    }

    /**
     * Apply this matrix to a 2D point, treating it as `(x, y, 0, 1)`
     * with a homogeneous divide on the result. If the bottom row is
     * `[0, 0, 0, 1]` this returns the affine drop of the M44 to its
     * 2D action (matches `asM33()?.mapXY`).
     */
    public fun mapPoint(p: SkPoint): SkPoint {
        val r = map(p.fX, p.fY, 0f, 1f)
        return if (r.w == 1f || r.w == 0f) {
            SkPoint(r.x, r.y)
        } else {
            val invW = 1f / r.w
            SkPoint(r.x * invW, r.y * invW)
        }
    }

    /**
     * Apply this matrix to `r` and return the axis-aligned bounding
     * box of the transformed rect (z = 0). Mirrors
     * [`SkMatrixPriv::MapRect(const SkM44&, const SkRect&)`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L216)
     * for the affine path; perspective uses the same approach as
     * [SkMatrix.mapRect] (project four corners, divide by w).
     */
    public fun mapRect(r: SkRect): SkRect {
        val tl = map(r.left,  r.top,    0f, 1f)
        val tr = map(r.right, r.top,    0f, 1f)
        val bl = map(r.left,  r.bottom, 0f, 1f)
        val br = map(r.right, r.bottom, 0f, 1f)

        fun divide(v: SkV4): Pair<Float, Float> =
            if (v.w == 0f || v.w == 1f) Pair(v.x, v.y) else Pair(v.x / v.w, v.y / v.w)

        val (x0, y0) = divide(tl)
        val (x1, y1) = divide(tr)
        val (x2, y2) = divide(bl)
        val (x3, y3) = divide(br)
        return SkRect(
            minOf(x0, x1, x2, x3),
            minOf(y0, y1, y2, y3),
            maxOf(x0, x1, x2, x3),
            maxOf(y0, y1, y2, y3),
        )
    }

    // ─── Linear algebra ────────────────────────────────────────────────

    /**
     * Return the inverse of this matrix, or `null` if it is singular.
     * Computed in `double` precision (matches Skia's
     * [`SkInvert4x4Matrix`](https://github.com/google/skia/blob/main/src/core/SkMatrixInvert.cpp#L72)).
     */
    public fun invert(): SkM44? {
        val a00 = fMat[0].toDouble();  val a01 = fMat[1].toDouble()
        val a02 = fMat[2].toDouble();  val a03 = fMat[3].toDouble()
        val a10 = fMat[4].toDouble();  val a11 = fMat[5].toDouble()
        val a12 = fMat[6].toDouble();  val a13 = fMat[7].toDouble()
        val a20 = fMat[8].toDouble();  val a21 = fMat[9].toDouble()
        val a22 = fMat[10].toDouble(); val a23 = fMat[11].toDouble()
        val a30 = fMat[12].toDouble(); val a31 = fMat[13].toDouble()
        val a32 = fMat[14].toDouble(); val a33 = fMat[15].toDouble()

        var b00 = a00 * a11 - a01 * a10
        var b01 = a00 * a12 - a02 * a10
        var b02 = a00 * a13 - a03 * a10
        var b03 = a01 * a12 - a02 * a11
        var b04 = a01 * a13 - a03 * a11
        var b05 = a02 * a13 - a03 * a12
        var b06 = a20 * a31 - a21 * a30
        var b07 = a20 * a32 - a22 * a30
        var b08 = a20 * a33 - a23 * a30
        var b09 = a21 * a32 - a22 * a31
        var b10 = a21 * a33 - a23 * a31
        var b11 = a22 * a33 - a23 * a32

        val determinant = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06
        if (determinant == 0.0 || !determinant.isFinite()) return null

        val invdet = 1.0 / determinant
        b00 *= invdet; b01 *= invdet; b02 *= invdet; b03 *= invdet
        b04 *= invdet; b05 *= invdet; b06 *= invdet; b07 *= invdet
        b08 *= invdet; b09 *= invdet; b10 *= invdet; b11 *= invdet

        val out = FloatArray(16)
        out[0]  = (a11 * b11 - a12 * b10 + a13 * b09).toFloat()
        out[1]  = (a02 * b10 - a01 * b11 - a03 * b09).toFloat()
        out[2]  = (a31 * b05 - a32 * b04 + a33 * b03).toFloat()
        out[3]  = (a22 * b04 - a21 * b05 - a23 * b03).toFloat()
        out[4]  = (a12 * b08 - a10 * b11 - a13 * b07).toFloat()
        out[5]  = (a00 * b11 - a02 * b08 + a03 * b07).toFloat()
        out[6]  = (a32 * b02 - a30 * b05 - a33 * b01).toFloat()
        out[7]  = (a20 * b05 - a22 * b02 + a23 * b01).toFloat()
        out[8]  = (a10 * b10 - a11 * b08 + a13 * b06).toFloat()
        out[9]  = (a01 * b08 - a00 * b10 - a03 * b06).toFloat()
        out[10] = (a30 * b04 - a31 * b02 + a33 * b00).toFloat()
        out[11] = (a21 * b02 - a20 * b04 - a23 * b00).toFloat()
        out[12] = (a11 * b07 - a10 * b09 - a12 * b06).toFloat()
        out[13] = (a00 * b09 - a01 * b07 + a02 * b06).toFloat()
        out[14] = (a31 * b01 - a30 * b03 - a32 * b00).toFloat()
        out[15] = (a20 * b03 - a21 * b01 + a22 * b00).toFloat()

        for (i in 0..15) {
            if (!out[i].isFinite()) return null
        }
        val result = SkM44()
        System.arraycopy(out, 0, result.fMat, 0, 16)
        return result
    }

    /** Return the transpose of this matrix. Mirrors Skia's `transpose()`. */
    public fun transpose(): SkM44 {
        val t = SkM44()
        transposeArrays(t.fMat, fMat)
        return t
    }

    // ─── Convert to/from SkMatrix ──────────────────────────────────────

    /**
     * Drop this 4×4 to its 3×3 affine image: the third row and column
     * are dropped, the perspective row of the M44 maps to the
     * perspective row of the 3×3. Always returns a non-null matrix —
     * the upstream API returns `SkMatrix` directly, so this returns
     * `SkMatrix?` only for symmetry with [setM33] (use it as
     * `asM33()!!` if you want non-nullable).
     */
    public fun asM33(): SkMatrix? = SkMatrix.MakeAll(
        fMat[0], fMat[4], fMat[12],
        fMat[1], fMat[5], fMat[13],
        fMat[3], fMat[7], fMat[15],
    )

    /**
     * Overwrite this matrix with the M44 form of [m]. Returns `this`
     * for chaining. Mirrors the `SkM44(const SkMatrix&)` constructor's
     * in-place sibling.
     */
    public fun setM33(m: SkMatrix): SkM44 {
        fMat[0] = m.sx;     fMat[1] = m.ky;     fMat[2] = 0f; fMat[3] = m.persp0
        fMat[4] = m.kx;     fMat[5] = m.sy;     fMat[6] = 0f; fMat[7] = m.persp1
        fMat[8] = 0f;       fMat[9] = 0f;       fMat[10] = 1f; fMat[11] = 0f
        fMat[12] = m.tx;    fMat[13] = m.ty;    fMat[14] = 0f; fMat[15] = m.persp2
        return this
    }

    // ─── Utility ───────────────────────────────────────────────────────

    /**
     * If the bottom row is `[0, 0, 0, X]` with `X != 0, 1`, scale the
     * whole matrix by `1/X` so the bottom row becomes `[0, 0, 0, 1]`.
     * Mirrors Skia's
     * [`SkM44::normalizePerspective`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L226).
     */
    public fun normalizePerspective() {
        if (fMat[15] != 1f && fMat[15] != 0f &&
            fMat[3] == 0f && fMat[7] == 0f && fMat[11] == 0f
        ) {
            val inv = 1.0 / fMat[15]
            for (i in 0..15) {
                fMat[i] = (fMat[i] * inv).toFloat()
            }
            fMat[15] = 1f
        }
    }

    /** `true` if all 16 entries are finite (no Inf / NaN). */
    public fun isFinite(): Boolean {
        for (i in 0..15) if (!fMat[i].isFinite()) return false
        return true
    }

    // ─── equals / hashCode / toString ──────────────────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkM44) return false
        for (i in 0..15) {
            if (fMat[i] != other.fMat[i]) return false
        }
        return true
    }

    override fun hashCode(): Int = fMat.contentHashCode()

    override fun toString(): String = buildString {
        append("SkM44(\n")
        for (r in 0..3) {
            append("  [")
            for (c in 0..3) {
                if (c != 0) append(", ")
                append(fMat[c * 4 + r])
            }
            append("]\n")
        }
        append(")")
    }
}

private fun transposeArrays(dst: FloatArray, src: FloatArray) {
    dst[0]  = src[0]; dst[1]  = src[4]; dst[2]  = src[8];  dst[3]  = src[12]
    dst[4]  = src[1]; dst[5]  = src[5]; dst[6]  = src[9];  dst[7]  = src[13]
    dst[8]  = src[2]; dst[9]  = src[6]; dst[10] = src[10]; dst[11] = src[14]
    dst[12] = src[3]; dst[13] = src[7]; dst[14] = src[11]; dst[15] = src[15]
}
