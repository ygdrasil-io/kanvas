package org.skia.core

import kotlin.Array
import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix

/**
 * C++ original:
 * ```cpp
 * class SK_API SkShader : public SkFlattenable {
 * public:
 *     /**
 *      *  Returns true if the shader is guaranteed to produce only opaque
 *      *  colors, subject to the SkPaint using the shader to apply an opaque
 *      *  alpha value. Subclasses should override this to allow some
 *      *  optimizations.
 *      */
 *     virtual bool isOpaque() const { return false; }
 *
 *     /**
 *      *  Iff this shader is backed by a single SkImage, return its ptr (the caller must ref this
 *      *  if they want to keep it longer than the lifetime of the shader). If not, return nullptr.
 *      */
 *     SkImage* isAImage(SkMatrix* localMatrix, SkTileMode xy[2]) const;
 *
 *     bool isAImage() const {
 *         return this->isAImage(nullptr, (SkTileMode*)nullptr) != nullptr;
 *     }
 *
 *     //////////////////////////////////////////////////////////////////////////
 *     //  Methods to create combinations or variants of shaders
 *
 *     /**
 *      *  Return a shader that will apply the specified localMatrix to this shader.
 *      *  The specified matrix will be applied before any matrix associated with this shader.
 *      */
 *     sk_sp<SkShader> makeWithLocalMatrix(const SkMatrix&) const;
 *
 *     /**
 *      *  Create a new shader that produces the same colors as invoking this shader and then applying
 *      *  the colorfilter.
 *      */
 *     sk_sp<SkShader> makeWithColorFilter(sk_sp<SkColorFilter>) const;
 *
 *     /**
 *      *  Return a shader that will compute this shader in a context such that any child shaders
 *      *  return RGBA values converted to the `inputCS` colorspace.
 *      *
 *      *  It is then assumed that the RGBA values returned by this shader have been transformed into
 *      *  `outputCS` by the shader being wrapped.  By default, shaders are assumed to return values
 *      *  in the destination colorspace and premultiplied. Using a different outputCS than inputCS
 *      *  allows custom shaders to replace the color management Skia normally performs w/o forcing
 *      *  authors to otherwise manipulate surface/image color info to avoid unnecessary or incorrect
 *      *  work.
 *      *
 *      *  If the shader is not performing colorspace conversion but needs to operate in the `inputCS`
 *      *  then it should have `outputCS` be the same as `inputCS`. Regardless of the `outputCS` here,
 *      *  the RGBA values of the returned SkShader are always converted from `outputCS` to the
 *      *  destination surface color space.
 *      *
 *      *  A null inputCS is assumed to be the destination CS.
 *      *  A null outputCS is assumed to be the inputCS.
 *      */
 *     sk_sp<SkShader> makeWithWorkingColorSpace(sk_sp<SkColorSpace> inputCS,
 *                                               sk_sp<SkColorSpace> outputCS=nullptr) const;
 *
 * private:
 *     SkShader() = default;
 *     friend class SkShaderBase;
 *
 *     using INHERITED = SkFlattenable;
 * }
 * ```
 */
public open class SkShader public constructor() : SkFlattenable() {
  /**
   * C++ original:
   * ```cpp
   * virtual bool isOpaque() const { return false; }
   * ```
   */
  public open fun isOpaque(): Boolean {
    TODO("Implement isOpaque")
  }

  /**
   * C++ original:
   * ```cpp
   * SkImage* SkShader::isAImage(SkMatrix* localMatrix, SkTileMode xy[2]) const {
   *     return as_SB(this)->onIsAImage(localMatrix, xy);
   * }
   * ```
   */
  public fun isAImage(localMatrix: SkMatrix?, xy: Array<SkTileMode>): SkImage {
    TODO("Implement isAImage")
  }

  /**
   * C++ original:
   * ```cpp
   * bool isAImage() const {
   *         return this->isAImage(nullptr, (SkTileMode*)nullptr) != nullptr;
   *     }
   * ```
   */
  public fun isAImage(): Boolean {
    TODO("Implement isAImage")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkShader::makeWithLocalMatrix(const SkMatrix& localMatrix) const {
   *     const SkMatrix* lm = &localMatrix;
   *
   *     sk_sp<SkShader> baseShader;
   *     SkMatrix otherLocalMatrix;
   *     sk_sp<SkShader> proxy = as_SB(this)->makeAsALocalMatrixShader(&otherLocalMatrix);
   *     if (proxy) {
   *         otherLocalMatrix = SkShaderBase::ConcatLocalMatrices(localMatrix, otherLocalMatrix);
   *         lm = &otherLocalMatrix;
   *         baseShader = proxy;
   *     } else {
   *         baseShader = sk_ref_sp(const_cast<SkShader*>(this));
   *     }
   *
   *     return sk_make_sp<SkLocalMatrixShader>(std::move(baseShader), *lm);
   * }
   * ```
   */
  public fun makeWithLocalMatrix(localMatrix: SkMatrix): Int {
    TODO("Implement makeWithLocalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkShader::makeWithColorFilter(sk_sp<SkColorFilter> filter) const {
   *     return SkColorFilterShader::Make(sk_ref_sp(this), 1.0f, std::move(filter));
   * }
   * ```
   */
  public fun makeWithColorFilter(filter: SkSp<SkColorFilter>): Int {
    TODO("Implement makeWithColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkShader::makeWithWorkingColorSpace(sk_sp<SkColorSpace> inputCS,
   *                                                     sk_sp<SkColorSpace> outputCS) const {
   *     return SkWorkingColorSpaceShader::Make(
   *             sk_ref_sp(this), std::move(inputCS), std::move(outputCS), /*workInUnpremul=*/false);
   * }
   * ```
   */
  public fun makeWithWorkingColorSpace(inputCS: SkSp<SkColorSpace>, outputCS: SkSp<SkColorSpace> = null): Int {
    TODO("Implement makeWithWorkingColorSpace")
  }
}
