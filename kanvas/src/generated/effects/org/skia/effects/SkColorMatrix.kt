package org.skia.effects

import kotlin.Array
import kotlin.Float
import kotlin.FloatArray
import org.skia.foundation.SkYUVColorSpace

/**
 * C++ original:
 * ```cpp
 * class SK_API SkColorMatrix {
 * public:
 *     constexpr SkColorMatrix() : SkColorMatrix(1, 0, 0, 0, 0,
 *                                               0, 1, 0, 0, 0,
 *                                               0, 0, 1, 0, 0,
 *                                               0, 0, 0, 1, 0) {}
 *
 *     constexpr SkColorMatrix(float m00, float m01, float m02, float m03, float m04,
 *                             float m10, float m11, float m12, float m13, float m14,
 *                             float m20, float m21, float m22, float m23, float m24,
 *                             float m30, float m31, float m32, float m33, float m34)
 *         : fMat { m00, m01, m02, m03, m04,
 *                  m10, m11, m12, m13, m14,
 *                  m20, m21, m22, m23, m24,
 *                  m30, m31, m32, m33, m34 } {}
 *
 *     static SkColorMatrix RGBtoYUV(SkYUVColorSpace);
 *     static SkColorMatrix YUVtoRGB(SkYUVColorSpace);
 *
 *     void setIdentity();
 *     void setScale(float rScale, float gScale, float bScale, float aScale = 1.0f);
 *
 *     void postTranslate(float dr, float dg, float db, float da);
 *
 *     void setConcat(const SkColorMatrix& a, const SkColorMatrix& b);
 *     void preConcat(const SkColorMatrix& mat) { this->setConcat(*this, mat); }
 *     void postConcat(const SkColorMatrix& mat) { this->setConcat(mat, *this); }
 *
 *     void setSaturation(float sat);
 *
 *     void setRowMajor(const float src[20]) { std::copy_n(src, 20, fMat.begin()); }
 *     void getRowMajor(float dst[20]) const { std::copy_n(fMat.begin(), 20, dst); }
 *
 * private:
 *     std::array<float, 20> fMat;
 *
 *     friend class SkColorFilters;
 * }
 * ```
 */
public data class SkColorMatrix public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::array<float, 20> fMat
   * ```
   */
  private var fMat: Array<Float>,
) {
  /**
   * C++ original:
   * ```cpp
   * void SkColorMatrix::setIdentity() {
   *     fMat.fill(0.0f);
   *     fMat[kR_Scale] = fMat[kG_Scale] = fMat[kB_Scale] = fMat[kA_Scale] = 1;
   * }
   * ```
   */
  public fun setIdentity() {
    TODO("Implement setIdentity")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorMatrix::setScale(float rScale, float gScale, float bScale, float aScale) {
   *     fMat.fill(0.0f);
   *     fMat[kR_Scale] = rScale;
   *     fMat[kG_Scale] = gScale;
   *     fMat[kB_Scale] = bScale;
   *     fMat[kA_Scale] = aScale;
   * }
   * ```
   */
  public fun setScale(
    rScale: Float,
    gScale: Float,
    bScale: Float,
    aScale: Float = TODO(),
  ) {
    TODO("Implement setScale")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorMatrix::postTranslate(float dr, float dg, float db, float da) {
   *     fMat[kR_Trans] += dr;
   *     fMat[kG_Trans] += dg;
   *     fMat[kB_Trans] += db;
   *     fMat[kA_Trans] += da;
   * }
   * ```
   */
  public fun postTranslate(
    dr: Float,
    dg: Float,
    db: Float,
    da: Float,
  ) {
    TODO("Implement postTranslate")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorMatrix::setConcat(const SkColorMatrix& matA, const SkColorMatrix& matB) {
   *     set_concat(fMat.data(), matA.fMat.data(), matB.fMat.data());
   * }
   * ```
   */
  public fun setConcat(a: SkColorMatrix, b: SkColorMatrix) {
    TODO("Implement setConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * void preConcat(const SkColorMatrix& mat) { this->setConcat(*this, mat); }
   * ```
   */
  public fun preConcat(mat: SkColorMatrix) {
    TODO("Implement preConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * void postConcat(const SkColorMatrix& mat) { this->setConcat(mat, *this); }
   * ```
   */
  public fun postConcat(mat: SkColorMatrix) {
    TODO("Implement postConcat")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorMatrix::setSaturation(float sat) {
   *     fMat.fill(0.0f);
   *
   *     const float R = kHueR * (1 - sat);
   *     const float G = kHueG * (1 - sat);
   *     const float B = kHueB * (1 - sat);
   *
   *     setrow(fMat.data() +  0, R + sat, G, B);
   *     setrow(fMat.data() +  5, R, G + sat, B);
   *     setrow(fMat.data() + 10, R, G, B + sat);
   *     fMat[kA_Scale] = 1;
   * }
   * ```
   */
  public fun setSaturation(sat: Float) {
    TODO("Implement setSaturation")
  }

  /**
   * C++ original:
   * ```cpp
   * void setRowMajor(const float src[20]) { std::copy_n(src, 20, fMat.begin()); }
   * ```
   */
  public fun setRowMajor(src: FloatArray) {
    TODO("Implement setRowMajor")
  }

  /**
   * C++ original:
   * ```cpp
   * void getRowMajor(float dst[20]) const { std::copy_n(fMat.begin(), 20, dst); }
   * ```
   */
  public fun getRowMajor(dst: FloatArray) {
    TODO("Implement getRowMajor")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkColorMatrix SkColorMatrix::RGBtoYUV(SkYUVColorSpace cs) {
     *     SkColorMatrix m;
     *     SkColorMatrix_RGB2YUV(cs, m.fMat.data());
     *     return m;
     * }
     * ```
     */
    public fun rGBtoYUV(cs: SkYUVColorSpace): SkColorMatrix {
      TODO("Implement rGBtoYUV")
    }

    /**
     * C++ original:
     * ```cpp
     * SkColorMatrix SkColorMatrix::YUVtoRGB(SkYUVColorSpace cs) {
     *     SkColorMatrix m;
     *     SkColorMatrix_YUV2RGB(cs, m.fMat.data());
     *     return m;
     * }
     * ```
     */
    public fun yUVtoRGB(cs: SkYUVColorSpace): SkColorMatrix {
      TODO("Implement yUVtoRGB")
    }
  }
}
