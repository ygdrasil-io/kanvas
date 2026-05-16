package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SDFTMatrixRange {
 * public:
 *     SDFTMatrixRange(SkScalar min, SkScalar max) : fMatrixMin{min}, fMatrixMax{max} {}
 *     bool matrixInRange(const SkMatrix& matrix) const;
 *     void flatten(SkWriteBuffer& buffer) const;
 *     static SDFTMatrixRange MakeFromBuffer(SkReadBuffer& buffer);
 *
 * private:
 *     const SkScalar fMatrixMin,
 *                    fMatrixMax;
 * }
 * ```
 */
public data class SDFTMatrixRange public constructor(
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fMatrixMin
   * ```
   */
  private val fMatrixMin: Int,
  /**
   * C++ original:
   * ```cpp
   * const SkScalar fMatrixMin,
   *                    fMatrixMax
   * ```
   */
  private val fMatrixMax: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SDFTMatrixRange::matrixInRange(const SkMatrix& matrix) const {
   *     SkScalar maxScale = matrix.getMaxScale();
   *     return fMatrixMin < maxScale && maxScale <= fMatrixMax;
   * }
   * ```
   */
  public fun matrixInRange(matrix: SkMatrix): Boolean {
    TODO("Implement matrixInRange")
  }

  /**
   * C++ original:
   * ```cpp
   * void SDFTMatrixRange::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeScalar(fMatrixMin);
   *     buffer.writeScalar(fMatrixMax);
   * }
   * ```
   */
  public fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SDFTMatrixRange SDFTMatrixRange::MakeFromBuffer(SkReadBuffer& buffer) {
     *     SkScalar min = buffer.readScalar();
     *     SkScalar max = buffer.readScalar();
     *     return SDFTMatrixRange{min, max};
     * }
     * ```
     */
    public fun makeFromBuffer(buffer: SkReadBuffer): SDFTMatrixRange {
      TODO("Implement makeFromBuffer")
    }
  }
}
