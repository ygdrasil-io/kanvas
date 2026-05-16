package org.skia.gpu

import kotlin.Boolean
import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API PrecompileShader : public PrecompileBase {
 * public:
 *     /**
 *      *  This is the Precompile correlate to SkShader::makeWithLocalMatrix. The actual matrix
 *      *  involved is abstracted away, except for whether or the not the matrix involves perspective
 *      *  so the correct generated shader variation is chosen.
 *      *  The PrecompileShaders::LocalMatrix factory can be used to generate a set of shaders
 *      *  that would've been generated via multiple makeWithLocalMatrix calls. That is, rather than
 *      *  performing:
 *      *     sk_sp<PrecompileShader> option1 = source1->makeWithLocalMatrix(false);
 *      *     sk_sp<PrecompileShader> option2 = source2->makeWithLocalMatrix(false);
 *      *  one could call:
 *      *     sk_sp<PrecompileShader> combinedOptions = LocalMatrix({ source1, source2 }, false);
 *      */
 *     sk_sp<PrecompileShader> makeWithLocalMatrix(bool isPerspective) const;
 *
 *     /**
 *      *  This is the Precompile correlate to SkShader::makeWithColorFilter.
 *      *  The PrecompileShaders::ColorFilter factory can be used to generate a set of shaders that
 *      *  would've been generated via multiple makeWithColorFilter calls. That is, rather than
 *      *  performing:
 *      *     sk_sp<PrecompileShader> option1 = source->makeWithColorFilter(colorFilter1);
 *      *     sk_sp<PrecompileShader> option2 = source->makeWithColorFilter(colorFilter2);
 *      *  one could call:
 *      *     sk_sp<PrecompileShader> combinedOptions = ColorFilter({ source },
 *      *                                                           { colorFilter1, colorFilter2 });
 *      *  With an alternative use case one could also use the ColorFilter factory thusly:
 *      *     sk_sp<PrecompileShader> combinedOptions = ColorFilter({ source1, source2 },
 *      *                                                           { colorFilter });
 *      */
 *     sk_sp<PrecompileShader> makeWithColorFilter(sk_sp<PrecompileColorFilter>) const;
 *
 *     /**
 *      *  This is the Precompile correlate to SkShader::makeWithWorkingColorSpace.
 *      *  The PrecompileShaders::WorkingColorSpace factory can be used to generate a set of shaders
 *      *  that would've been generated via multiple makeWithWorkingColorSpace calls. That is, rather
 *      *  than performing:
 *      *     sk_sp<PrecompileShader> option1 = source->makeWithWorkingColorSpace(colorSpace1);
 *      *     sk_sp<PrecompileShader> option2 = source->makeWithWorkingColorSpace(colorSpace2);
 *      *  one could call:
 *      *     sk_sp<PrecompileShader> combinedOptions = WorkingColorSpace({ source },
 *      *                                                                 { colorSpace1,
 *      *                                                                   colorSpace2 });
 *      *  With an alternative use case one could also use the WorkingColorSpace factory thusly:
 *      *     sk_sp<PrecompileShader> combinedOptions = WorkingColorSpace({ source1, source2 },
 *      *                                                                 { colorSpace });
 *      */
 *     sk_sp<PrecompileShader> makeWithWorkingColorSpace(sk_sp<SkColorSpace> inputCS,
 *                                                       sk_sp<SkColorSpace> outputCS=nullptr) const;
 *
 *     // Provides access to functions that aren't part of the public API.
 *     PrecompileShaderPriv priv();
 *     const PrecompileShaderPriv priv() const;  // NOLINT(readability-const-return-type)
 *
 * protected:
 *     friend class PrecompileShaderPriv;
 *
 *     PrecompileShader() : PrecompileBase(Type::kShader) {}
 *     ~PrecompileShader() override;
 *
 *     virtual bool isConstant(int /* desiredCombination */) const { return false; }
 *
 *     virtual bool isALocalMatrixShader() const { return false; }
 * }
 * ```
 */
public open class PrecompileShader public constructor() : PrecompileBase(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> PrecompileShader::makeWithLocalMatrix(bool isPerspective) const {
   *     if (this->priv().isALocalMatrixShader()) {
   *         // SkShader::makeWithLocalMatrix collapses chains of localMatrix shaders so we need to
   *         // follow suit here, folding in any new perspective flag if needed.
   *         auto thisAsLMShader = static_cast<const PrecompileLocalMatrixShader*>(this);
   *         if (isPerspective && !(thisAsLMShader->getFlags() &
   *                 PrecompileLocalMatrixShader::Flags::kIsPerspective)) {
   *             return sk_make_sp<PrecompileLocalMatrixShader>(
   *                 thisAsLMShader->getWrapped(),
   *                 thisAsLMShader->getFlags() | PrecompileLocalMatrixShader::Flags::kIsPerspective);
   *         }
   *
   *         return sk_ref_sp(this);
   *     }
   *
   *     return PrecompileShaders::LocalMatrix({{ sk_ref_sp(this) }}, isPerspective);
   * }
   * ```
   */
  public fun makeWithLocalMatrix(isPerspective: Boolean): Int {
    TODO("Implement makeWithLocalMatrix")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> PrecompileShader::makeWithColorFilter(
   *         sk_sp<PrecompileColorFilter> cf) const {
   *     if (!cf) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     return PrecompileShaders::ColorFilter({{ sk_ref_sp(this) }}, {{ std::move(cf) }});
   * }
   * ```
   */
  public fun makeWithColorFilter(cf: SkSp<PrecompileColorFilter>): Int {
    TODO("Implement makeWithColorFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> PrecompileShader::makeWithWorkingColorSpace(
   *         sk_sp<SkColorSpace> inputCS, sk_sp<SkColorSpace> outputCS) const {
   *     if (!inputCS && !outputCS) {
   *         return sk_ref_sp(this);
   *     }
   *
   *     return PrecompileShaders::WorkingColorSpaceExplicit(
   *             {{ sk_ref_sp(this) }},
   *             {{ { std::move(inputCS), std::move(outputCS) } }});
   * }
   * ```
   */
  public fun makeWithWorkingColorSpace(inputCS: SkSp<SkColorSpace>, outputCS: SkSp<SkColorSpace> = TODO()): Int {
    TODO("Implement makeWithWorkingColorSpace")
  }

  /**
   * C++ original:
   * ```cpp
   * PrecompileShaderPriv priv()
   * ```
   */
  public override fun priv(): PrecompileShaderPriv {
    TODO("Implement priv")
  }

  /**
   * C++ original:
   * ```cpp
   * const PrecompileShaderPriv priv() const
   * ```
   */
  protected open fun isConstant(param0: Int): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool isConstant(int /* desiredCombination */) const { return false; }
   * ```
   */
  protected open fun isALocalMatrixShader(): Boolean {
    TODO("Implement isALocalMatrixShader")
  }
}
