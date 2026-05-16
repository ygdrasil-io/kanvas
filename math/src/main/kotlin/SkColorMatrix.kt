package org.graphiks.math

/**
 * Iso-aligned port of Skia's
 * [`SkColorMatrix`](https://github.com/google/skia/blob/main/include/effects/SkColorMatrix.h)
 * (`include/effects/SkColorMatrix.h` / `src/effects/SkColorMatrix.cpp`).
 *
 * A 4×5 row-major color matrix : 4 output channels (R, G, B, A) by
 * 5 input weights (R, G, B, A, bias). Applied to a color via
 * ```
 *   R' = m00·R + m01·G + m02·B + m03·A + m04
 *   G' = m10·R + m11·G + m12·B + m13·A + m14
 *   B' = m20·R + m21·G + m22·B + m23·A + m24
 *   A' = m30·R + m31·G + m32·B + m33·A + m34
 * ```
 *
 * The 20 floats are exposed in row-major order via [getRowMajor] /
 * [setRowMajor], matching the layout that `SkColorFilters::Matrix`
 * consumes upstream.
 */
public class SkColorMatrix {

    private val fMat: FloatArray = FloatArray(20)

    /** Default ctor — identity matrix. */
    public constructor() {
        setIdentity()
    }

    /**
     * Row-major full-state constructor. `values.size` must be 20.
     * Mirrors the 20-arg constexpr ctor in
     * [SkColorMatrix.h](https://github.com/google/skia/blob/main/include/effects/SkColorMatrix.h).
     */
    public constructor(values: FloatArray) {
        require(values.size == 20) { "SkColorMatrix expects 20 floats, got ${values.size}" }
        values.copyInto(fMat)
    }

    public constructor(
        m00: Float, m01: Float, m02: Float, m03: Float, m04: Float,
        m10: Float, m11: Float, m12: Float, m13: Float, m14: Float,
        m20: Float, m21: Float, m22: Float, m23: Float, m24: Float,
        m30: Float, m31: Float, m32: Float, m33: Float, m34: Float,
    ) {
        fMat[0] = m00; fMat[1] = m01; fMat[2] = m02; fMat[3] = m03; fMat[4] = m04
        fMat[5] = m10; fMat[6] = m11; fMat[7] = m12; fMat[8] = m13; fMat[9] = m14
        fMat[10] = m20; fMat[11] = m21; fMat[12] = m22; fMat[13] = m23; fMat[14] = m24
        fMat[15] = m30; fMat[16] = m31; fMat[17] = m32; fMat[18] = m33; fMat[19] = m34
    }

    /** Resets to the identity matrix (scales = 1, bias = 0, off-diagonals = 0). */
    public fun setIdentity() {
        fMat.fill(0f)
        fMat[kR_Scale] = 1f
        fMat[kG_Scale] = 1f
        fMat[kB_Scale] = 1f
        fMat[kA_Scale] = 1f
    }

    /**
     * Diagonal scale, with bias = 0. Mirrors
     * [`SkColorMatrix::setScale`](https://github.com/google/skia/blob/main/src/effects/SkColorMatrix.cpp).
     */
    public fun setScale(rScale: Float, gScale: Float, bScale: Float, aScale: Float = 1f) {
        fMat.fill(0f)
        fMat[kR_Scale] = rScale
        fMat[kG_Scale] = gScale
        fMat[kB_Scale] = bScale
        fMat[kA_Scale] = aScale
    }

    /** Adds the translation column in-place. */
    public fun postTranslate(dr: Float, dg: Float, db: Float, da: Float) {
        fMat[kR_Trans] += dr
        fMat[kG_Trans] += dg
        fMat[kB_Trans] += db
        fMat[kA_Trans] += da
    }

    /**
     * Replaces this matrix with the JPEG-Full RGB→YUV color matrix.
     * Constants taken from
     * [`SkYUVMath.cpp`](https://github.com/google/skia/blob/main/src/core/SkYUVMath.cpp)
     * (`JPEG_full_rgb_to_yuv`).
     */
    public fun setRGB2YUV() {
        JPEG_FULL_RGB_TO_YUV.copyInto(fMat)
    }

    /**
     * Replaces this matrix with the JPEG-Full YUV→RGB color matrix.
     * Constants from
     * [`SkYUVMath.cpp`](https://github.com/google/skia/blob/main/src/core/SkYUVMath.cpp)
     * (`JPEG_full_yuv_to_rgb`).
     */
    public fun setYUV2RGB() {
        JPEG_FULL_YUV_TO_RGB.copyInto(fMat)
    }

    /**
     * Saturation matrix — `sat = 0` produces luminance grayscale,
     * `sat = 1` is the identity (within the RGB rows ; alpha is
     * unchanged). Mirrors `SkColorMatrix::setSaturation`.
     */
    public fun setSaturation(sat: Float) {
        fMat.fill(0f)
        val R = kHueR * (1f - sat)
        val G = kHueG * (1f - sat)
        val B = kHueB * (1f - sat)

        fMat[0] = R + sat;     fMat[1] = G;            fMat[2] = B
        fMat[5] = R;            fMat[6] = G + sat;     fMat[7] = B
        fMat[10] = R;           fMat[11] = G;           fMat[12] = B + sat
        fMat[kA_Scale] = 1f
    }

    /** Copies 20 floats from `src` (must be size 20) into this matrix. */
    public fun setRowMajor(src: FloatArray) {
        require(src.size == 20) { "expected 20 floats, got ${src.size}" }
        src.copyInto(fMat)
    }

    /** Copies this matrix's 20 floats into `dst` (must be size 20). */
    public fun getRowMajor(dst: FloatArray) {
        require(dst.size == 20) { "expected 20-element destination, got ${dst.size}" }
        fMat.copyInto(dst)
    }

    /** Returns the underlying 20 floats as a fresh copy. */
    public fun toFloatArray(): FloatArray = fMat.copyOf()

    /**
     * Replaces this matrix with `other * this` ; left-multiplies the
     * current transform by `other`.
     */
    public fun preConcat(other: SkColorMatrix) {
        setConcat(this, other)
    }

    /**
     * Replaces this matrix with `this * other` ; right-multiplies the
     * current transform by `other`.
     */
    public fun postConcat(other: SkColorMatrix) {
        setConcat(other, this)
    }

    /** Convenience operator : `a * b` returns the concatenated matrix `a then b` (post-concat semantics). */
    public operator fun times(other: SkColorMatrix): SkColorMatrix {
        val out = SkColorMatrix()
        setConcatInto(out.fMat, other.fMat, this.fMat)
        return out
    }

    /** Replaces this with `outer · inner`. Mirrors `set_concat()` in SkColorMatrix.cpp. */
    private fun setConcat(outerHolder: SkColorMatrix, innerHolder: SkColorMatrix) {
        setConcatInto(fMat, outerHolder.fMat, innerHolder.fMat)
    }

    override fun equals(other: Any?): Boolean =
        other is SkColorMatrix && fMat.contentEquals(other.fMat)

    override fun hashCode(): Int = fMat.contentHashCode()

    override fun toString(): String =
        fMat.joinToString(prefix = "SkColorMatrix[", postfix = "]")

    public companion object {
        // Row offsets — match the C++ kR_Scale / kG_Scale / ... enum.
        private const val kR_Scale = 0
        private const val kG_Scale = 6
        private const val kB_Scale = 12
        private const val kA_Scale = 18
        private const val kR_Trans = 4
        private const val kG_Trans = 9
        private const val kB_Trans = 14
        private const val kA_Trans = 19

        // Luma weights used by setSaturation — from src/effects/SkColorMatrix.cpp.
        private const val kHueR = 0.213f
        private const val kHueG = 0.715f
        private const val kHueB = 0.072f

        // JPEG-Full RGB->YUV / YUV->RGB. Verbatim from src/core/SkYUVMath.cpp
        // (low-precision branch), kJPEG_Full_SkYUVColorSpace == 0.
        private val JPEG_FULL_RGB_TO_YUV: FloatArray = floatArrayOf(
            0.299000f,  0.587000f,  0.114000f, 0f,  0.000000f,
            -0.168736f, -0.331264f,  0.500000f, 0f,  0.501961f,
            0.500000f, -0.418688f, -0.081312f, 0f,  0.501961f,
            0.000000f,  0.000000f,  0.000000f, 1f,  0.000000f,
        )
        private val JPEG_FULL_YUV_TO_RGB: FloatArray = floatArrayOf(
            1.000000f,  0.000000f,  1.402000f, 0f, -0.703749f,
            1.000000f, -0.344136f, -0.714136f, 0f,  0.531211f,
            1.000000f,  1.772000f,  0.000000f, 0f, -0.889475f,
            0.000000f,  0.000000f,  0.000000f, 1f,  0.000000f,
        )

        /**
         * Core 4×5 matrix multiply, writing `outer · inner` into `result`.
         * Handles aliasing the same way the C++ does — uses a scratch
         * buffer if `result` aliases either operand.
         */
        private fun setConcatInto(result: FloatArray, outer: FloatArray, inner: FloatArray) {
            val tmp = FloatArray(20)
            val target: FloatArray = if (result === outer || result === inner) tmp else result

            var index = 0
            var j = 0
            while (j < 20) {
                for (i in 0..3) {
                    target[index++] =
                        outer[j + 0] * inner[i + 0] +
                        outer[j + 1] * inner[i + 5] +
                        outer[j + 2] * inner[i + 10] +
                        outer[j + 3] * inner[i + 15]
                }
                target[index++] =
                    outer[j + 0] * inner[4] +
                    outer[j + 1] * inner[9] +
                    outer[j + 2] * inner[14] +
                    outer[j + 3] * inner[19] +
                    outer[j + 4]
                j += 5
            }

            if (target !== result) {
                tmp.copyInto(result)
            }
        }
    }
}
