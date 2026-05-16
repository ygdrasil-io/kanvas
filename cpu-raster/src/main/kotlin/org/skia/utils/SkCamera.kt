package org.skia.utils

import org.skia.core.SkCanvas
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkV3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Iso-aligned port of Skia's pseudo-3D utilities from
 * [`include/utils/SkCamera.h`](https://github.com/google/skia/blob/main/include/utils/SkCamera.h)
 * (`src/utils/SkCamera.cpp`).
 *
 * The header is marked DEPRECATED upstream — Skia is migrating to direct
 * 4×4 matrix support on `SkCanvas`. We still need it for ports such as
 * `Camera3DGM`, so the API is provided verbatim, with the only change
 * being a switch to a 16-float column-major scratch matrix in lieu of
 * `SkM44` (tracked as R3.1).
 */

// ─── shared internal 4×4 helper (column-major, like SkM44) ──────────────────

private const val M00 = 0; private const val M10 = 1; private const val M20 = 2; private const val M30 = 3
private const val M01 = 4; private const val M11 = 5; private const val M21 = 6; private const val M31 = 7
private const val M02 = 8; private const val M12 = 9; private const val M22 = 10; private const val M32 = 11
private const val M03 = 12; private const val M13 = 13; private const val M23 = 14; private const val M33 = 15

private fun m44Identity(): FloatArray = FloatArray(16).apply {
    this[M00] = 1f; this[M11] = 1f; this[M22] = 1f; this[M33] = 1f
}

/** Column-major mul : `dst = a · b`. */
private fun m44Mul(dst: FloatArray, a: FloatArray, b: FloatArray) {
    val tmp = FloatArray(16)
    for (col in 0..3) {
        val b0 = b[col * 4 + 0]
        val b1 = b[col * 4 + 1]
        val b2 = b[col * 4 + 2]
        val b3 = b[col * 4 + 3]
        tmp[col * 4 + 0] = a[M00] * b0 + a[M01] * b1 + a[M02] * b2 + a[M03] * b3
        tmp[col * 4 + 1] = a[M10] * b0 + a[M11] * b1 + a[M12] * b2 + a[M13] * b3
        tmp[col * 4 + 2] = a[M20] * b0 + a[M21] * b1 + a[M22] * b2 + a[M23] * b3
        tmp[col * 4 + 3] = a[M30] * b0 + a[M31] * b1 + a[M32] * b2 + a[M33] * b3
    }
    tmp.copyInto(dst)
}

private fun m44PreTranslate(m: FloatArray, x: Float, y: Float, z: Float) {
    // m * T(x,y,z) — only the 4th column changes.
    // newCol3 = col0*x + col1*y + col2*z + col3
    for (row in 0..3) {
        m[M03 + row] =
            m[M00 + row] * x +
            m[M01 + row] * y +
            m[M02 + row] * z +
            m[M03 + row]
    }
}

private fun m44PreConcat(m: FloatArray, b: FloatArray) {
    // m = m · b
    m44Mul(m, m, b)
}

/**
 * Replaces `dst` (16 floats, column-major) with a rotation matrix
 * around an arbitrary axis. Mirrors `SkM44::setRotateUnitSinCos` from
 * [`src/core/SkM44.cpp`](https://github.com/google/skia/blob/main/src/core/SkM44.cpp).
 */
private fun m44SetRotate(dst: FloatArray, axis: SkV3, radians: Float) {
    val len = axis.length()
    if (len <= 0f || !len.isFinite()) {
        // Identity
        dst.fill(0f); dst[M00] = 1f; dst[M11] = 1f; dst[M22] = 1f; dst[M33] = 1f
        return
    }
    val inv = 1f / len
    val x = axis.x * inv
    val y = axis.y * inv
    val z = axis.z * inv
    val c = cos(radians.toDouble()).toFloat()
    val s = sin(radians.toDouble()).toFloat()
    val t = 1f - c

    // Row-major from the upstream:
    //  [t·x²+c     t·x·y-s·z   t·x·z+s·y   0]
    //  [t·x·y+s·z  t·y²+c      t·y·z-s·x   0]
    //  [t·x·z-s·y  t·y·z+s·x   t·z²+c      0]
    //  [0          0           0           1]
    // Convert to column-major.
    dst[M00] = t * x * x + c
    dst[M10] = t * x * y + s * z
    dst[M20] = t * x * z - s * y
    dst[M30] = 0f

    dst[M01] = t * x * y - s * z
    dst[M11] = t * y * y + c
    dst[M21] = t * y * z + s * x
    dst[M31] = 0f

    dst[M02] = t * x * z + s * y
    dst[M12] = t * y * z - s * x
    dst[M22] = t * z * z + c
    dst[M32] = 0f

    dst[M03] = 0f; dst[M13] = 0f; dst[M23] = 0f; dst[M33] = 1f
}

/**
 * `out = m · v` where the input is treated as `(vx, vy, vz, 1)` and the
 * result is a 3-vector (the `w` component is dropped — the matrix is
 * affine in `Sk3DView`'s usage so `w` stays at 1).
 */
private fun m44MapPoint(m: FloatArray, v: SkV3): SkV3 {
    val x = m[M00] * v.x + m[M01] * v.y + m[M02] * v.z + m[M03]
    val y = m[M10] * v.x + m[M11] * v.y + m[M12] * v.z + m[M13]
    val z = m[M20] * v.x + m[M21] * v.y + m[M22] * v.z + m[M23]
    return SkV3(x, y, z)
}

/** `out = m · v` treating `v` as `(vx, vy, vz, 0)` — a direction. */
private fun m44MapVector(m: FloatArray, v: SkV3): SkV3 {
    val x = m[M00] * v.x + m[M01] * v.y + m[M02] * v.z
    val y = m[M10] * v.x + m[M11] * v.y + m[M12] * v.z
    val z = m[M20] * v.x + m[M21] * v.y + m[M22] * v.z
    return SkV3(x, y, z)
}

// ────────────────────────── SkPatch3D ──────────────────────────────

/**
 * Internal scratch — 3-vector patch (origin + U + V tangent frame).
 * Skia exposes this as `SkPatch3D`, but the only externally-visible
 * use of it is `SkCamera3D::patchToMatrix`, which we re-expose with
 * an `Array<SkV3>` of `[U, V, origin]` for symmetry with the spec.
 */
internal class SkPatch3D {
    var fU: SkV3 = SkV3(1f, 0f, 0f)
    var fV: SkV3 = SkV3(0f, -1f, 0f)
    var fOrigin: SkV3 = SkV3(0f, 0f, 0f)

    fun reset() {
        fOrigin = SkV3(0f, 0f, 0f)
        fU = SkV3(1f, 0f, 0f)
        fV = SkV3(0f, -1f, 0f)
    }

    /** Applies the 4×4 transform `m` to U / V (as directions) and origin (as a point). */
    fun transform(m: FloatArray) {
        val newU = m44MapVector(m, fU)
        val newV = m44MapVector(m, fV)
        val newO = m44MapPoint(m, fOrigin)
        fU = newU; fV = newV; fOrigin = newO
    }

    /** Dot the patch's surface normal `(U × V)` with `(dx, dy, dz)`. */
    fun dotWith(dx: Float, dy: Float, dz: Float): Float {
        // Faithful port of upstream — the C++ has a bug-style `fU.x * fV.y` for the
        // y-component of the cross, but we mirror it verbatim to keep behaviour identical.
        val cx = fU.y * fV.z - fU.z * fV.y
        val cy = fU.z * fV.x - fU.x * fV.y
        val cz = fU.x * fV.y - fU.y * fV.x
        return cx * dx + cy * dy + cz * dz
    }
}

// ────────────────────────── SkCamera3D ──────────────────────────────

/**
 * Iso-aligned port of Skia's `SkCamera3D` ([include/utils/SkCamera.h](https://github.com/google/skia/blob/main/include/utils/SkCamera.h)).
 *
 * Perspective camera that projects 3D coordinates onto a 2D plane via
 * [patchToMatrix]. Public members mirror the upstream public fields ;
 * call [update] after mutating them to force the next [patchToMatrix]
 * to recompute the cached orientation.
 */
public class SkCamera3D {

    public var location: SkV3 = SkV3(0f, 0f, -576f)  // 8 inches backward (72 dpi)
    public var axis: SkV3 = SkV3(0f, 0f, 1f)         // forward
    public var zenith: SkV3 = SkV3(0f, -1f, 0f)      // up
    public var observer: SkV3 = SkV3(0f, 0f, -576f)  // eye position (same z as `location` by default)

    // Cached SkMatrix-style 3×3 orientation (sx, kx, tx, ky, sy, ty, p0, p1, p2).
    private val fOrientation: FloatArray = FloatArray(9)
    private var fNeedToUpdate: Boolean = true

    public fun reset() {
        location = SkV3(0f, 0f, -576f)
        axis = SkV3(0f, 0f, 1f)
        zenith = SkV3(0f, -1f, 0f)
        observer = SkV3(0f, 0f, -576f)
        fNeedToUpdate = true
    }

    /** Marks the cached orientation dirty ; call after mutating any of the public fields. */
    public fun update() {
        fNeedToUpdate = true
    }

    private fun doUpdate() {
        val nAxis = axis.normalize()
        var z = zenith - nAxis * nAxis.dot(zenith)
        z = z.normalize()
        val cross = nAxis.cross(z)

        val obs = observer

        // Faithful port — see SkCamera.cpp::doUpdate().
        fOrientation[0] = obs.x * nAxis.x - obs.z * cross.x   // kMScaleX
        fOrientation[1] = obs.x * nAxis.y - obs.z * cross.y   // kMSkewX
        fOrientation[2] = obs.x * nAxis.z - obs.z * cross.z   // kMTransX
        fOrientation[3] = obs.y * nAxis.x - obs.z * z.x       // kMSkewY
        fOrientation[4] = obs.y * nAxis.y - obs.z * z.y       // kMScaleY
        fOrientation[5] = obs.y * nAxis.z - obs.z * z.z       // kMTransY
        fOrientation[6] = nAxis.x                             // kMPersp0
        fOrientation[7] = nAxis.y                             // kMPersp1
        fOrientation[8] = nAxis.z                             // kMPersp2
    }

    internal fun patchToMatrixInternal(patch: SkPatch3D): SkMatrix {
        if (fNeedToUpdate) {
            doUpdate()
            fNeedToUpdate = false
        }
        val map = fOrientation
        val diff = patch.fOrigin - location
        val dot = diff.dot(SkV3(map[6], map[7], map[8]))

        // patchPtr ordering : fU.x, fU.y, fU.z, fV.x, fV.y, fV.z, origin.x, origin.y, origin.z
        // but the C++ actually uses (diff) for the last 3, not `origin`. Match exactly.
        fun dotDiv(p0: Float, p1: Float, p2: Float, mOffset: Int): Float =
            (p0 * map[mOffset] + p1 * map[mOffset + 1] + p2 * map[mOffset + 2]) / dot

        val sx = dotDiv(patch.fU.x, patch.fU.y, patch.fU.z, 0)
        val ky = dotDiv(patch.fU.x, patch.fU.y, patch.fU.z, 3)
        val p0 = dotDiv(patch.fU.x, patch.fU.y, patch.fU.z, 6)

        val kx = dotDiv(patch.fV.x, patch.fV.y, patch.fV.z, 0)
        val sy = dotDiv(patch.fV.x, patch.fV.y, patch.fV.z, 3)
        val p1 = dotDiv(patch.fV.x, patch.fV.y, patch.fV.z, 6)

        val tx = dotDiv(diff.x, diff.y, diff.z, 0)
        val ty = dotDiv(diff.x, diff.y, diff.z, 3)
        // Persp2 is always SK_Scalar1, regardless of dotDiv.

        return SkMatrix.MakeAll(
            sx, kx, tx,
            ky, sy, ty,
            p0, p1, 1f,
        )
    }

    /**
     * Project a patch (origin + tangent frame) into 2D space.
     * `quad` must contain exactly 3 vectors — `[U, V, origin]`.
     *
     * ## Immutable-adaptation note (R-suivi.15)
     *
     * Upstream's signature is :
     *
     * ```cpp
     *   void SkCamera3D::patchToMatrix(const SkPatch3D&, SkMatrix* matrix) const;
     * ```
     *
     * — the caller passes a mutable [SkMatrix] out-param and the
     * implementation calls `matrix->set(...)` nine times to write the
     * 3×3 projection in place. That contract is **incompatible** with
     * kanvas-skia's immutable [SkMatrix] : a `set` on the field would
     * have to allocate a new instance, which defeats the upstream
     * out-param optimisation.
     *
     * The kanvas-skia adaptation **returns** the matrix instead. The
     * resulting [SkMatrix] is independent of any later mutation of the
     * camera state ([reset], [update], or writes to the public
     * [location] / [axis] / [zenith] / [observer] fields) — call
     * [patchToMatrix] again to recompute the projection after the
     * camera has been retargeted.
     *
     * Idiom :
     *
     * ```kotlin
     *   val m: SkMatrix = camera.patchToMatrix(arrayOf(u, v, origin))
     *   canvas.concat(m)
     * ```
     *
     * rather than the upstream :
     *
     * ```cpp
     *   SkMatrix m;
     *   camera.patchToMatrix(patch, &m);
     *   canvas->concat(m);
     * ```
     */
    public fun patchToMatrix(quad: Array<SkV3>): SkMatrix {
        require(quad.size == 3) { "patchToMatrix expects 3 SkV3 (U, V, origin), got ${quad.size}" }
        val patch = SkPatch3D().apply {
            fU = quad[0]
            fV = quad[1]
            fOrigin = quad[2]
        }
        return patchToMatrixInternal(patch)
    }
}

// ────────────────────────── Sk3DView ──────────────────────────────

/**
 * Iso-aligned port of Skia's `Sk3DView` ([include/utils/SkCamera.h](https://github.com/google/skia/blob/main/include/utils/SkCamera.h)).
 *
 * Accumulates pseudo-3D transformations (translate / rotate around X /
 * Y / Z) on top of a [SkCamera3D]. The accumulated 4×4 transform can
 * be projected back to 2D via [getMatrix] or applied directly to a
 * [SkCanvas] through [applyToCanvas].
 *
 * Implementation note : the 4×4 matrix stack is held as a list of
 * `FloatArray(16)` (column-major, matching `SkM44`) since `SkM44`
 * is not yet ported (scheduled for R3.1).
 */
public class Sk3DView {

    private val fStack: ArrayDeque<FloatArray> = ArrayDeque<FloatArray>().also { it.addLast(m44Identity()) }
    private val fCamera: SkCamera3D = SkCamera3D()

    /** Push a copy of the current matrix onto the stack. */
    public fun save() {
        fStack.addLast(fStack.last().copyOf())
    }

    /** Pop the current matrix off the stack. Cannot pop the last (initial) entry. */
    public fun restore() {
        check(fStack.size > 1) { "Sk3DView.restore() called with no matching save()" }
        fStack.removeLast()
    }

    public fun translate(x: Float, y: Float, z: Float) {
        m44PreTranslate(fStack.last(), x, y, z)
    }

    public fun rotateX(degrees: Float) {
        val r = FloatArray(16)
        m44SetRotate(r, SkV3(1f, 0f, 0f), (degrees * PI / 180.0).toFloat())
        m44PreConcat(fStack.last(), r)
    }

    public fun rotateY(degrees: Float) {
        val r = FloatArray(16)
        m44SetRotate(r, SkV3(0f, -1f, 0f), (degrees * PI / 180.0).toFloat())
        m44PreConcat(fStack.last(), r)
    }

    public fun rotateZ(degrees: Float) {
        val r = FloatArray(16)
        m44SetRotate(r, SkV3(0f, 0f, 1f), (degrees * PI / 180.0).toFloat())
        m44PreConcat(fStack.last(), r)
    }

    /**
     * Project the accumulated 4×4 transform back into a 2D `SkMatrix`
     * using the embedded camera.
     *
     * ## Immutable-adaptation note (R-suivi.15)
     *
     * Upstream's signature is :
     *
     * ```cpp
     *   void Sk3DView::getMatrix(SkMatrix* matrix) const;
     * ```
     *
     * The caller hands a mutable [SkMatrix] out-param ; the
     * implementation forwards to
     * [SkCamera3D.patchToMatrix][SkCamera3D.patchToMatrix] which writes
     * all nine 3×3 elements in place via `matrix->set(...)`. That
     * out-param contract is **incompatible** with kanvas-skia's
     * immutable [SkMatrix] — see the long-form note on
     * [SkCamera3D.patchToMatrix].
     *
     * The kanvas-skia adaptation **returns** the matrix. The returned
     * value is independent of any later [Sk3DView] mutation
     * ([translate], [rotateX] / [rotateY] / [rotateZ], [save] /
     * [restore]) — call [getMatrix] again after the view has been
     * mutated to fetch the new projection.
     *
     * Idiom :
     *
     * ```kotlin
     *   val m: SkMatrix = view.getMatrix()
     *   canvas.concat(m)
     * ```
     *
     * rather than the upstream :
     *
     * ```cpp
     *   SkMatrix m;
     *   view.getMatrix(&m);
     *   canvas->concat(m);
     * ```
     *
     * For ergonomic parity with the upstream signature, see also
     * [getMatrixCopy] — same behaviour, parameter-less convenience
     * wrapper that returns the matrix value rather than mutating an
     * out-param.
     */
    public fun getMatrix(): SkMatrix {
        val patch = SkPatch3D()
        patch.transform(fStack.last())
        return fCamera.patchToMatrixInternal(patch)
    }

    /**
     * Parameter-less convenience wrapper around [getMatrix] (R-suivi.15).
     * Identical behaviour — included as a self-documenting entry point
     * that makes the immutable-return contract explicit at the call
     * site (the name hints "build the matrix and return it" rather
     * than "fill an out-param"). No additional allocation cost.
     */
    public fun getMatrixCopy(): SkMatrix = getMatrix()

    /** Concat [getMatrix] onto the provided canvas. */
    public fun applyToCanvas(canvas: SkCanvas) {
        canvas.concat(getMatrix())
    }

    /**
     * Dot the transformed patch's normal with `(dx, dy, dz)`. The
     * sign of the result tells you which way the rotated quad is
     * facing — used by GMs to skip drawing back-facing surfaces.
     */
    public fun dotWithNormal(dx: Float, dy: Float, dz: Float): Float {
        val patch = SkPatch3D()
        patch.transform(fStack.last())
        return patch.dotWith(dx, dy, dz)
    }
}
