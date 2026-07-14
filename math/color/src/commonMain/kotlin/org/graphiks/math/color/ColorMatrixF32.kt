package org.graphiks.math.color

/**
 * A 4×5 color matrix for transforming RGBA color vectors.
 *
 * Iso-aligned port of Skia's `SkColorMatrix`
 * ([include/core/SkColorMatrix.h](https://github.com/google/skia/blob/main/include/core/SkColorMatrix.h)).
 *
 * The 20-element backing array stores the matrix in row-major order:
 * rows 0-3 multiply R, G, B, A respectively; column 4 is the
 * additive translate column. Operations mirror Skia's `preConcat` /
 * `postConcat`, RGB↔YUV conversion, saturation, and identity/reset.
 */
public class ColorMatrixF32 {

    private val fMat: FloatArray = FloatArray(20)

    /** Constructs an identity color matrix. */
    public constructor() {
        setIdentity()
    }

    /** Constructs from a 20-element [FloatArray]. */
    public constructor(values: FloatArray) {
        require(values.size == 20) { "ColorMatrixF32 expects 20 floats, got ${values.size}" }
        values.copyInto(fMat)
    }

    /** Constructs from 20 individual floats (row-major). */
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

    /** Resets this matrix to identity. */
    public fun setIdentity() {
        fMat.fill(0f)
        fMat[kR_Scale] = 1f
        fMat[kG_Scale] = 1f
        fMat[kB_Scale] = 1f
        fMat[kA_Scale] = 1f
    }

    /** Sets this matrix to a diagonal scale. Mirrors `SkColorMatrix::setScale`. */
    public fun setScale(rScale: Float, gScale: Float, bScale: Float, aScale: Float = 1f) {
        fMat.fill(0f)
        fMat[kR_Scale] = rScale
        fMat[kG_Scale] = gScale
        fMat[kB_Scale] = bScale
        fMat[kA_Scale] = aScale
    }

    /** Adds `(dr, dg, db, da)` to the translate column. */
    public fun postTranslate(dr: Float, dg: Float, db: Float, da: Float) {
        fMat[kR_Trans] += dr
        fMat[kG_Trans] += dg
        fMat[kB_Trans] += db
        fMat[kA_Trans] += da
    }

    /** Sets this matrix to the JPEG full-range RGB→YUV conversion matrix. */
    public fun setRGB2YUV() {
        JPEG_FULL_RGB_TO_YUV.copyInto(fMat)
    }

    /** Sets this matrix to the JPEG full-range YUV→RGB conversion matrix. */
    public fun setYUV2RGB() {
        JPEG_FULL_YUV_TO_RGB.copyInto(fMat)
    }

    /** Sets this matrix to a saturation adjustment. Mirrors `SkColorMatrix::setSaturation`. */
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

    /** Writes a 20-element [FloatArray] into this matrix (row-major). */
    public fun setRowMajor(src: FloatArray) {
        require(src.size == 20) { "expected 20 floats, got ${src.size}" }
        src.copyInto(fMat)
    }

    /** Copies this matrix into [dst] (row-major). */
    public fun getRowMajor(dst: FloatArray) {
        require(dst.size == 20) { "expected 20-element destination, got ${dst.size}" }
        fMat.copyInto(dst)
    }

    /** Returns a copy of the backing array. */
    public fun toFloatArray(): FloatArray = fMat.copyOf()

    /** Pre-multiplies: `this = this * other`. */
    public fun preConcat(other: ColorMatrixF32) {
        setConcat(this, other)
    }

    /** Post-multiplies: `this = other * this`. */
    public fun postConcat(other: ColorMatrixF32) {
        setConcat(other, this)
    }

    /** Returns `this * other`. */
    public operator fun times(other: ColorMatrixF32): ColorMatrixF32 {
        val out = ColorMatrixF32()
        setConcatInto(out.fMat, other.fMat, this.fMat)
        return out
    }

    private fun setConcat(outerHolder: ColorMatrixF32, innerHolder: ColorMatrixF32) {
        setConcatInto(fMat, outerHolder.fMat, innerHolder.fMat)
    }

    override fun equals(other: Any?): Boolean =
        other is ColorMatrixF32 && fMat.contentEquals(other.fMat)

    override fun hashCode(): Int = fMat.contentHashCode()

    override fun toString(): String =
        fMat.joinToString(prefix = "ColorMatrixF32[", postfix = "]")

    public companion object {
        /** Stub for per-color-space RGB→YUV conversion. */
        public fun RGBtoYUV(yuvColorSpace: Any): ColorMatrixF32 {
            TODO(
                "STUB.YUVA_PIXMAPS: ColorMatrixF32.RGBtoYUV($yuvColorSpace) — " +
                    "per-color-space RGB→YUV matrices not yet ported from " +
                    "src/core/SkYUVMath.cpp (SkColorMatrix_RGB2YUV)."
            )
        }

        private const val kR_Scale = 0
        private const val kG_Scale = 6
        private const val kB_Scale = 12
        private const val kA_Scale = 18
        private const val kR_Trans = 4
        private const val kG_Trans = 9
        private const val kB_Trans = 14
        private const val kA_Trans = 19

        private const val kHueR = 0.213f
        private const val kHueG = 0.715f
        private const val kHueB = 0.072f

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
