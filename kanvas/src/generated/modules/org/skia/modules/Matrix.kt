package org.skia.modules

import kotlin.Any
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * template <typename T>
 * class Matrix final : public Transform {
 * public:
 *     template <typename = std::enable_if<std::is_same<T, SkMatrix>::value ||
 *                                         std::is_same<T, SkM44   >::value>>
 *     static sk_sp<Matrix> Make(const T& m) { return sk_sp<Matrix>(new Matrix(m)); }
 *
 *     SG_ATTRIBUTE(Matrix, T, fMatrix)
 *
 * protected:
 *     explicit Matrix(const T& m) : fMatrix(m) {}
 *
 *     SkRect onRevalidate(InvalidationController*, const SkMatrix&) override {
 *         return SkRect::MakeEmpty();
 *     }
 *
 *     bool is44() const override { return std::is_same<T, SkM44>::value; }
 *
 *     SkMatrix asMatrix() const override;
 *     SkM44    asM44   () const override;
 *
 * private:
 *     T fMatrix;
 *
 *     using INHERITED = Transform;
 * }
 * ```
 */
public open class Matrix<T> : Transform() {
  /**
   * C++ original:
   * ```cpp
   * T fMatrix
   * ```
   */
  private var fMatrix: T = TODO("Initialize fMatrix")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Matrix, T, fMatrix)
   * ```
   */
  public fun sgATTRIBUTE(param0: Matrix<T>, param1: T): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkM44    asM44   () const override
   * ```
   */
  protected override fun asM44(): Int {
    TODO("Implement asM44")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<Matrix> Make(const T& m) { return sk_sp<Matrix>(new Matrix(m)); }
     * ```
     */
    public fun make(m: Any): Int {
      TODO("Implement make")
    }
  }
}
