package org.graphiks.math.matrix

import kotlin.math.abs
import kotlin.math.sqrt
import org.graphiks.math.scalar.cos
import org.graphiks.math.scalar.nearlyZero
import org.graphiks.math.scalar.sin
import org.graphiks.math.scalar.tan
import org.graphiks.math.vector.Vector2F32
import org.graphiks.math.vector.Vector3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.vector.Vector4F32

/**
 * `Matrix4x4F32`,
 * [src/core/SkM44.cpp](https://github.com/google/skia/blob/main/src/core/SkM44.cpp).
 *
 * Storage is a length-16 `FloatArray` in **column-major** layout —
 * matching standard OpenGL/Vulkan conventions:
 *
 * ```
 *  index  0  4  8 12        col 0  col 1  col 2  col 3
 *  index  1  5  9 13          ↓     ↓      ↓      ↓
 *  index  2  6 10 14
 *  index  3  7 11 15
 * ```
 *
 * The class is **mutable** supporting common in-place transform idioms. For value-class semantics
 * use the `Matrix4x4F32(other)` copy constructor or [copy].
 *
 * Assumes a right-handed coordinate system: +X right, +Y down, +Z into the screen.
 *
 * SkCanvas integration is deferred — this port is standalone and does
 * not yet feed the canvas matrix stack.
 */
public class Matrix4x4F32 {

    /**
     * Backing array — 16 floats, column-major.
     * `m[col * 4 + row]` is the element at row `row`, column `col`.
     */
    internal val fMat: FloatArray = FloatArray(16)

    // ─── Constructors ──────────────────────────────────────────────────

    /** Default constructor: identity matrix. */
    public constructor() {
        setIdentity()
    }

    /** Copy constructor — clones [src]. */
    public constructor(src: Matrix4x4F32) {
        src.fMat.copyInto(fMat)
    }

    /**
     * Compose constructor: `this = a · b`.
     */
    public constructor(a: Matrix4x4F32, b: Matrix4x4F32) {
        setConcat(a, b)
    }

    /**
     * Row-major scalar constructor. Parameters are listed in **row-major**
     * order (`m0 m4 m8 m12` is the first row), but stored column-major.
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
     * Build an [Matrix4x4F32] from a 3×3 [Matrix3x3F32]. The third row/column of the
     * 4×4 stays identity; the perspective row maps to the bottom row of
     * the M44.
     *
     * ```
     * [ a b c ]      [ a b 0 c ]
     * [ d e f ]  ->  [ d e 0 f ]
     * [ g h i ]      [ 0 0 1 0 ]
     *                [ g h 0 i ]
     * ```
     */
    public constructor(src: Matrix3x3F32) : this(
        src.sx,     src.kx,     0f, src.tx,
        src.ky,     src.sy,     0f, src.ty,
        0f,         0f,         1f, 0f,
        src.persp0, src.persp1, 0f, src.persp2,
    )

    // ─── Companion factories ───────────────────────────────────────────

    public companion object {
        /**
         * Distance to the `w = 0` plane used when clipping perspective
         * projections in [mapRect]. `kW0PlaneDistance = 1.f / (1 << 14)` ≈ 6.1e-5. Corners with homogeneous `w`
         * below this threshold are treated as being behind the camera.
         */
        public const val kW0PlaneDistance: Float = 1f / (1 shl 14)

        /** Identity matrix. */
        public fun identity(): Matrix4x4F32 = Matrix4x4F32()

        /**
         * Translation matrix.
         */
        public fun translate(x: Float, y: Float, z: Float = 0f): Matrix4x4F32 = Matrix4x4F32(
            1f, 0f, 0f, x,
            0f, 1f, 0f, y,
            0f, 0f, 1f, z,
            0f, 0f, 0f, 1f,
        )

        /**
         * Diagonal scale matrix.
         */
        public fun scale(x: Float, y: Float, z: Float = 1f): Matrix4x4F32 = Matrix4x4F32(
            x,  0f, 0f, 0f,
            0f, y,  0f, 0f,
            0f, 0f, z,  0f,
            0f, 0f, 0f, 1f,
        )

        /**
         * Rotation around an arbitrary axis (radians). The axis is
         * normalized internally; identity is returned for a zero or
         * non-finite axis.
         */
        public fun rotate(axis: Vector3F32, radians: Float): Matrix4x4F32 = Matrix4x4F32().also {
            it.setRotate(axis, radians)
        }

        /**
         * Build the view matrix that places the camera at `eye` looking
         * at `center` with the given `up` direction.
         */
        public fun lookAt(eye: Vector3F32, center: Vector3F32, up: Vector3F32): Matrix4x4F32 {
            val f = normalize3(center - eye)
            val u = normalize3(up)
            val s = normalize3(f.cross(u))

            val cols = Matrix4x4F32()
            cols.setCol(0, v4(s, 0f))
            cols.setCol(1, v4(s.cross(f), 0f))
            cols.setCol(2, v4(-f, 0f))
            cols.setCol(3, v4(eye, 1f))

            val out = Matrix4x4F32()
            val inv = cols.invert()
            if (inv != null) {
                inv.fMat.copyInto(out.fMat)
            } else {
                out.setIdentity()
            }
            return out
        }

        /**
         * Perspective projection.
         * `near < far` and `angle != 0` are required.
         */
        public fun perspective(near: Float, far: Float, angle: Float): Matrix4x4F32 {
            require(far > near) { "perspective: far ($far) must be > near ($near)" }
            val denomInv = 1f / (far - near)
            val halfAngle = angle * 0.5f
            require(halfAngle != 0f) { "perspective: angle ($angle) must be non-zero" }
            val cot = 1f / tan(halfAngle)
            val m = Matrix4x4F32()
            m.setRC(0, 0, cot)
            m.setRC(1, 1, cot)
            m.setRC(2, 2, (far + near) * denomInv)
            m.setRC(2, 3, 2f * far * near * denomInv)
            m.setRC(3, 2, -1f)
           
            // which doesn't reset it.
            return m
        }

        /**
         * Build a matrix that scales-and-translates `src` to fill `dst`
         * exactly.
         */
        public fun rectToRect(src: RectF32, dst: RectF32): Matrix4x4F32 {
            if (src.isEmpty) return Matrix4x4F32()
            if (dst.isEmpty) return scale(0f, 0f, 0f)
            val sxF = dst.width() / src.width()
            val syF = dst.height() / src.height()
            val tx = dst.left - sxF * src.left
            val ty = dst.top - syF * src.top
            return Matrix4x4F32(
                sxF, 0f,  0f, tx,
                0f,  syF, 0f, ty,
                0f,  0f,  1f, 0f,
                0f,  0f,  0f, 1f,
            )
        }

        /** Helper for vector normalization that returns `v` if `length` is near zero. */
        private fun normalize3(v: Vector3F32): Vector3F32 {
            val len = v.length()
            return if (kotlin.math.abs(len) < 1e-7f) v else v * (1f / len)
        }

        private fun v4(v: Vector3F32, w: Float): Vector4F32 = Vector4F32.of(v.x, v.y, v.z, w)
    }

    // ─── Element access ────────────────────────────────────────────────

    /**
     * Read element at `row`, `col` (0 <= row, col <= 3).
     * Uses column-major storage: `fMat[col * 4 + row]`.
     */
    public operator fun get(row: Int, col: Int): Float {
        require(row in 0..3 && col in 0..3) { "get($row, $col) out of range" }
        return fMat[col * 4 + row]
    }

    /**
     * Write element at `row`, `col` (0 <= row, col <= 3).
     * Uses column-major storage: `fMat[col * 4 + row]`.
     */
    public operator fun set(row: Int, col: Int, value: Float) {
        require(row in 0..3 && col in 0..3) { "set($row, $col) out of range" }
        fMat[col * 4 + row] = value
    }

    /**
     * Read element at `row`, `col` (0 <= row, col <= 3).
     * Uses column-major storage: `fMat[col * 4 + row]`.
     */
    public fun rc(row: Int, col: Int): Float {
        require(row in 0..3 && col in 0..3) { "rc($row, $col) out of range" }
        return fMat[col * 4 + row]
    }

    /**
     * Write element at `row`, `col` (0 <= row, col <= 3).
     * Uses column-major storage: `fMat[col * 4 + row]`.
     */
    public fun setRC(row: Int, col: Int, value: Float) {
        require(row in 0..3 && col in 0..3) { "setRC($row, $col) out of range" }
        fMat[col * 4 + row] = value
    }

    /**
     * Returns a defensive copy of the backing column-major storage.
     */
    public fun toFloatArray(): FloatArray = fMat.copyOf()

    /**
     * Return row `i` as a [Vector4F32].
         */
    public fun row(i: Int): Vector4F32 {
        require(i in 0..3) { "row($i) out of range" }
        return Vector4F32.of(fMat[i + 0], fMat[i + 4], fMat[i + 8], fMat[i + 12])
    }

    /**
     * Return column `i` as a [Vector4F32].
         */
    public fun col(i: Int): Vector4F32 {
        require(i in 0..3) { "col($i) out of range" }
        return Vector4F32.of(fMat[i * 4 + 0], fMat[i * 4 + 1], fMat[i * 4 + 2], fMat[i * 4 + 3])
    }

    /**
     * Replace row `i` with [v].
         */
    public fun setRow(i: Int, v: Vector4F32) {
        require(i in 0..3) { "setRow($i) out of range" }
        fMat[i + 0] = v.x
        fMat[i + 4] = v.y
        fMat[i + 8] = v.z
        fMat[i + 12] = v.w
    }

    /**
     * Replace column `i` with [v].
         */
    public fun setCol(i: Int, v: Vector4F32) {
        require(i in 0..3) { "setCol($i) out of range" }
        fMat[i * 4 + 0] = v.x
        fMat[i * 4 + 1] = v.y
        fMat[i * 4 + 2] = v.z
        fMat[i * 4 + 3] = v.w
    }

    /**
     * Copy backing column-major storage into [v].
     * Requires `v.size >= 16`.
     */
    public fun getColMajor(v: FloatArray) {
        require(v.size >= 16) { "getColMajor needs >= 16 elements (got ${v.size})" }
        fMat.copyInto(v)
    }

    /**
     * Copy storage into [v] in row-major order (transposed).
     * Requires `v.size >= 16`.
     */
    public fun getRowMajor(v: FloatArray) {
        require(v.size >= 16) { "getRowMajor needs >= 16 elements (got ${v.size})" }
        transposeArrays(v, fMat)
    }

    // ─── State / setters ───────────────────────────────────────────────

    /**
     * Returns `true` if this is the identity matrix.
     * Performs exact bitwise comparison on all 16 entries.
     */
    public fun isIdentity(): Boolean {
        return fMat[0] == 1f && fMat[5] == 1f && fMat[10] == 1f && fMat[15] == 1f &&
            fMat[1] == 0f && fMat[2] == 0f && fMat[3] == 0f &&
            fMat[4] == 0f && fMat[6] == 0f && fMat[7] == 0f &&
            fMat[8] == 0f && fMat[9] == 0f && fMat[11] == 0f &&
            fMat[12] == 0f && fMat[13] == 0f && fMat[14] == 0f
    }

    /**
     * Reset to identity. Returns `this` for chaining.
         */
    public fun setIdentity(): Matrix4x4F32 = apply {
        fMat.fill(0f)
        fMat[0] = 1f
        fMat[5] = 1f
        fMat[10] = 1f
        fMat[15] = 1f
    }

    /**
     * Reset to a translation matrix. Returns `this` for chaining.
         */
    public fun setTranslate(x: Float, y: Float, z: Float = 0f): Matrix4x4F32 = apply {
        fMat[0] = 1f; fMat[1] = 0f; fMat[2] = 0f; fMat[3] = 0f
        fMat[4] = 0f; fMat[5] = 1f; fMat[6] = 0f; fMat[7] = 0f
        fMat[8] = 0f; fMat[9] = 0f; fMat[10] = 1f; fMat[11] = 0f
        fMat[12] = x; fMat[13] = y; fMat[14] = z; fMat[15] = 1f
    }

    /**
     * Reset to a diagonal scale matrix. Returns `this` for chaining.
         */
    public fun setScale(x: Float, y: Float, z: Float = 1f): Matrix4x4F32 = apply {
        fMat[0] = x;  fMat[1] = 0f; fMat[2] = 0f; fMat[3] = 0f
        fMat[4] = 0f; fMat[5] = y;  fMat[6] = 0f; fMat[7] = 0f
        fMat[8] = 0f; fMat[9] = 0f; fMat[10] = z; fMat[11] = 0f
        fMat[12] = 0f; fMat[13] = 0f; fMat[14] = 0f; fMat[15] = 1f
    }

    /**
     * Set this matrix to rotate about the **already unit-length** axis
     * `axis` by the given sin and cos.
     */
    public fun setRotateUnitSinCos(axis: Vector3F32, sinAngle: Float, cosAngle: Float): Matrix4x4F32 {
        val x = axis.x
        val y = axis.y
        val z = axis.z
        val c = cosAngle
        val s = sinAngle
        val t = 1 - c

        // Row-major formulation; stored column-major below.
        fMat[0] = t * x * x + c;     fMat[4] = t * x * y - s * z; fMat[8] = t * x * z + s * y; fMat[12] = 0f
        fMat[1] = t * x * y + s * z; fMat[5] = t * y * y + c;     fMat[9] = t * y * z - s * x; fMat[13] = 0f
        fMat[2] = t * x * z - s * y; fMat[6] = t * y * z + s * x; fMat[10] = t * z * z + c;   fMat[14] = 0f
        fMat[3] = 0f;                fMat[7] = 0f;                fMat[11] = 0f;              fMat[15] = 1f
        return this
    }

    /** Set this matrix to rotate about the unit-length `axis` by `radians`. */
    public fun setRotateUnit(axis: Vector3F32, radians: Float): Matrix4x4F32 =
        setRotateUnitSinCos(axis, sin(radians), cos(radians))

    /**
     * Set this matrix to rotate about the (possibly un-normalised) axis
     * by `radians`. Normalises `axis` internally; falls back to
     * identity if `axis.length()` is zero or non-finite.
     */
    public fun setRotate(axis: Vector3F32, radians: Float): Matrix4x4F32 {
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
    public fun setConcat(a: Matrix4x4F32, b: Matrix4x4F32): Matrix4x4F32 {
        val out = FloatArray(16)
        m44Concat(a.fMat, b.fMat, out)
        out.copyInto(fMat)
        return this
    }

    /** Returns a new matrix equal to `this · other`. */
    public operator fun times(other: Matrix4x4F32): Matrix4x4F32 = Matrix4x4F32(this, other)

    /** In-place: `this = this · m`. */
    public fun preConcat(m: Matrix4x4F32): Matrix4x4F32 = setConcat(this, m)

    /** In-place: `this = m · this`. */
    public fun postConcat(m: Matrix4x4F32): Matrix4x4F32 = setConcat(m, this)

    /**
     * In-place pre-multiply by a 3×3 [Matrix3x3F32] (promoted as in the
     * `Matrix3x3F32` constructor.
     */
    public fun preConcat(b: Matrix3x3F32): Matrix4x4F32 = preConcat(Matrix4x4F32(b))

    /**
     * In-place: pre-multiply by `translate(x, y, z)`.
     */
    public fun preTranslate(x: Float, y: Float, z: Float = 0f): Matrix4x4F32 {
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
     * In-place: post-multiply by `translate(x, y, z)`.
     *
     * `t = (x, y, z, 0)` and each column `c_i` of the result is
     * `c_i + t * c_i.w`.
     */
    public fun postTranslate(x: Float, y: Float, z: Float = 0f): Matrix4x4F32 {
        for (c in 0..3) {
            val w = fMat[c * 4 + 3]
            fMat[c * 4 + 0] += x * w
            fMat[c * 4 + 1] += y * w
            fMat[c * 4 + 2] += z * w
        }
        return this
    }

    /**
     * In-place 2D pre-scale.
     */
    public fun preScale(x: Float, y: Float): Matrix4x4F32 {
        fMat[0] *= x; fMat[1] *= x; fMat[2] *= x; fMat[3] *= x
        fMat[4] *= y; fMat[5] *= y; fMat[6] *= y; fMat[7] *= y
        return this
    }

    /**
     * In-place 3D pre-scale.
     */
    public fun preScale(x: Float, y: Float, z: Float): Matrix4x4F32 {
        fMat[0] *= x; fMat[1] *= x; fMat[2] *= x; fMat[3] *= x
        fMat[4] *= y; fMat[5] *= y; fMat[6] *= y; fMat[7] *= y
        fMat[8] *= z; fMat[9] *= z; fMat[10] *= z; fMat[11] *= z
        return this
    }

    // ─── Mapping ───────────────────────────────────────────────────────

    /**
     * Apply this matrix to the 4-vector `(x, y, z, w)`.
     */
    public fun map(x: Float, y: Float, z: Float, w: Float): Vector4F32 {
        val rx = fMat[0] * x + fMat[4] * y + fMat[8] * z + fMat[12] * w
        val ry = fMat[1] * x + fMat[5] * y + fMat[9] * z + fMat[13] * w
        val rz = fMat[2] * x + fMat[6] * y + fMat[10] * z + fMat[14] * w
        val rw = fMat[3] * x + fMat[7] * y + fMat[11] * z + fMat[15] * w
        return Vector4F32.of(rx, ry, rz, rw)
    }

    /** Convenience wrapper. */
    public fun map(v: Vector4F32): Vector4F32 = map(v.x, v.y, v.z, v.w)

    /** Operator overload: `this * v`. Mirrors C++ `operator*(const Vector4F32&)`. */
    public operator fun times(v: Vector4F32): Vector4F32 = map(v.x, v.y, v.z, v.w)

    /**
     * Operator overload: apply with `w = 0` (vector mapping, drops
     * translation). Mirrors C++ `operator*(Vector3F32)`.
     */
    public operator fun times(v: Vector3F32): Vector3F32 {
        val r = map(v.x, v.y, v.z, 0f)
        return Vector3F32.of(r.x, r.y, r.z)
    }

    /**
     * Apply this matrix to a 2D point, treating it as `(x, y, 0, 1)`
     * with a homogeneous divide on the result. If the bottom row is
     * `[0, 0, 0, 1]` this returns the affine drop of the M44 to its
     * 2D action (matches `asM33().mapXY`).
     */
    public fun mapPoint(p: Vector2F32): Vector2F32 {
        val r = map(p.x, p.y, 0f, 1f)
        return if (r.w == 1f || r.w == 0f) {
            Vector2F32.of(r.x, r.y)
        } else {
            val invW = 1f / r.w
            Vector2F32.of(r.x * invW, r.y * invW)
        }
    }

    /**
     * Apply this matrix to `r` and return the axis-aligned bounding
     * box of the transformed rect (z = 0). Mirrors
     * [`SkMatrixPriv::MapRect(const SkM44&, const SkRect&)`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L216).
     *
     * Affine (no perspective) path: project four corners, take min/max.
     *
     * Perspective path: ports
     * [`map_rect_perspective`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L164)
     * — corners whose homogeneous `w` is below `kW0PlaneDistance`
     * (≈ 6.1e-5, a threshold to avoid division
     * by ~zero) are clipped against the adjacent edges so the result
     * stays finite even when the rect crosses behind the camera.
     */
    public fun mapRect(r: RectF32): RectF32 {
        val hasPerspective =
            fMat[3] != 0f || fMat[7] != 0f || fMat[11] != 0f || fMat[15] != 1f
        if (!hasPerspective) {
            return mapRectAffine(r)
        }
        return mapRectPerspective(r)
    }

    /** Affine fast path: project 4 corners (w divide skipped since w == 1) and bound. */
    private fun mapRectAffine(r: RectF32): RectF32 {
        val tl = map(r.left,  r.top,    0f, 1f)
        val tr = map(r.right, r.top,    0f, 1f)
        val bl = map(r.left,  r.bottom, 0f, 1f)
        val br = map(r.right, r.bottom, 0f, 1f)
        return RectF32(
            minOf(tl.x, tr.x, bl.x, br.x),
            minOf(tl.y, tr.y, bl.y, br.y),
            maxOf(tl.x, tr.x, bl.x, br.x),
            maxOf(tl.y, tr.y, bl.y, br.y),
        )
    }

    /**
     * Perspective path with `w = 0` plane clipping. Direct port of
     * [`map_rect_perspective`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp#L164).
     *
     * Each of the 4 corners is projected together with its two
     * neighbours (clockwise traversal). When the corner's `w` is below
     * [kW0PlaneDistance] the edges leaving it are clipped against the
     * `w = kW0PlaneDistance` plane; corners with no surviving neighbour
     * contribute `+Infinity`, which `minOf` discards.
     */
    private fun mapRectPerspective(r: RectF32): RectF32 {
        val l = r.left
        val t = r.top
        val rg = r.right
        val b = r.bottom

        // Build four homogeneous corners c0*x + c1*y + c3 (z = 0).
        val tl = map(l,  t,  0f, 1f)
        val tr = map(rg, t,  0f, 1f)
        val br = map(rg, b,  0f, 1f)
        val bl = map(l,  b,  0f, 1f)

        // Walk the corners clockwise. project(p0, p1, p2) returns
        // (minX, minY, -maxX, -maxY) — same "flip" trick so
        // a single min() across the four results yields (l, t, -r, -b).
        val p0 = project(tl, tr, bl)
        val p1 = project(tr, br, tl)
        val p2 = project(br, bl, tr)
        val p3 = project(bl, tl, br)

        val minX = minOf(p0[0], p1[0], p2[0], p3[0])
        val minY = minOf(p0[1], p1[1], p2[1], p3[1])
        val negMaxX = minOf(p0[2], p1[2], p2[2], p3[2])
        val negMaxY = minOf(p0[3], p1[3], p2[3], p3[3])

        return RectF32(minX, minY, -negMaxX, -negMaxY)
    }

    /**
     * Project a homogeneous corner together with its two neighbours,
     * clipping edges against the `w = kW0PlaneDistance` plane when the
     * corner is behind/very close to the camera. Returns a 4-tuple
     * `(x, y, -x, -y)` of the projected position — combined via `min`
     * across all four corners this yields (minX, minY, -maxX, -maxY).
     */
    private fun project(p0: Vector4F32, p1: Vector4F32, p2: Vector4F32): FloatArray {
        val w0 = p0.w
        if (w0 >= kW0PlaneDistance) {
            // Unclipped: just divide xy by w.
            val x = p0.x / w0
            val y = p0.y / w0
            return floatArrayOf(x, y, -x, -y)
        }
        // p0 has w < kW0PlaneDistance — clip the two edges (p0→p1) and
        // (p0→p2) against the w = kW0PlaneDistance plane and keep the
        // componentwise min of the two clipped projections. If both
        // neighbours also have w < kW0PlaneDistance, both return +Inf
        // and the caller's `minOf` chain ignores this corner.
        val c1 = clipEdge(p0, p1, w0)
        val c2 = clipEdge(p0, p2, w0)
        return floatArrayOf(
            minOf(c1[0], c2[0]),
            minOf(c1[1], c2[1]),
            minOf(c1[2], c2[2]),
            minOf(c1[3], c2[3]),
        )
    }

    /**
     * Clip the edge from `p0` (w < kW0PlaneDistance) to `p` against the
     * `w = kW0PlaneDistance` plane. If `p` is also behind the plane,
     * the whole edge is invalid and we return `+Infinity` so the
     * caller's `min` reduction drops it. Otherwise interpolate by
     * `t = (kW0PlaneDistance - w0) / (w - w0)`, divide xy by
     * kW0PlaneDistance, and return `(x, y, -x, -y)` for the flip trick.
     */
    private fun clipEdge(p0: Vector4F32, p: Vector4F32, w0: Float): FloatArray {
        val w = p.w
        if (w < kW0PlaneDistance) {
            return floatArrayOf(
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
                Float.POSITIVE_INFINITY,
            )
        }
        val t = (kW0PlaneDistance - w0) / (w - w0)
        val cx = (t * p.x + (1f - t) * p0.x) / kW0PlaneDistance
        val cy = (t * p.y + (1f - t) * p0.y) / kW0PlaneDistance
        return floatArrayOf(cx, cy, -cx, -cy)
    }

    // ─── Linear algebra ────────────────────────────────────────────────

    /**
     * Return the inverse of this matrix, or `null` if it is singular.
     * Computed in `double` precision.
     */
    public fun invert(): Matrix4x4F32? {
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
        val result = Matrix4x4F32()
        out.copyInto(result.fMat)
        return result
    }

    /** Return the transpose of this matrix. */
    public fun transpose(): Matrix4x4F32 {
        val t = Matrix4x4F32()
        transposeArrays(t.fMat, fMat)
        return t
    }

    // ─── Convert to/from Matrix3x3F32 ──────────────────────────────────────

    /**
     * Drop this 4×4 to its 3×3 affine image: the third row and column
     * are dropped, the perspective row of the M44 maps to the
     * perspective row of the 3×3.
     */
    public fun asM33(): Matrix3x3F32 = Matrix3x3F32.of(
        fMat[0], fMat[4], fMat[12],
        fMat[1], fMat[5], fMat[13],
        fMat[3], fMat[7], fMat[15],
    )

    /**
     * Overwrite this matrix with the M44 form of [m]. Returns `this`
     * for chaining. Mirrors the `Matrix4x4F32(const Matrix3x3F32&)` constructor's
     * in-place sibling.
     */
    public fun setM33(m: Matrix3x3F32): Matrix4x4F32 {
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

    /**
     * Returns `true` if all 16 entries are finite (no Inf / NaN).
         */
    public fun isFinite(): Boolean {
        for (i in 0..15) if (!fMat[i].isFinite()) return false
        return true
    }

    // ─── equals / hashCode / toString ──────────────────────────────────

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Matrix4x4F32) return false
        for (i in 0..15) {
            if (fMat[i] != other.fMat[i]) return false
        }
        return true
    }

    override fun hashCode(): Int = fMat.contentHashCode()

    override fun toString(): String = buildString {
        append("Matrix4x4F32(\n")
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

private fun m44Concat(a: FloatArray, b: FloatArray, out: FloatArray) {
    val a00 = a[0];  val a10 = a[1];  val a20 = a[2];  val a30 = a[3]
    val a01 = a[4];  val a11 = a[5];  val a21 = a[6];  val a31 = a[7]
    val a02 = a[8];  val a12 = a[9];  val a22 = a[10]; val a32 = a[11]
    val a03 = a[12]; val a13 = a[13]; val a23 = a[14]; val a33 = a[15]

    for (c in 0..3) {
        val b0 = b[c * 4 + 0]
        val b1 = b[c * 4 + 1]
        val b2 = b[c * 4 + 2]
        val b3 = b[c * 4 + 3]
        out[c * 4 + 0] = a00 * b0 + a01 * b1 + a02 * b2 + a03 * b3
        out[c * 4 + 1] = a10 * b0 + a11 * b1 + a12 * b2 + a13 * b3
        out[c * 4 + 2] = a20 * b0 + a21 * b1 + a22 * b2 + a23 * b3
        out[c * 4 + 3] = a30 * b0 + a31 * b1 + a32 * b2 + a33 * b3
    }
}
