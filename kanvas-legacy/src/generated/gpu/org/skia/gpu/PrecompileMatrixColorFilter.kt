package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileMatrixColorFilter : public PrecompileColorFilter {
 * public:
 *     PrecompileMatrixColorFilter(bool inHSLA) : fInHSLA(inHSLA) {}
 *
 * private:
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination == 0);
 *         static constexpr float kIdentity[20] = { 1, 0, 0, 0, 0,
 *                                                  0, 1, 0, 0, 0,
 *                                                  0, 0, 1, 0, 0,
 *                                                  0, 0, 0, 1, 0 };
 *         MatrixColorFilterBlock::MatrixColorFilterData matrixCFData(kIdentity, fInHSLA,
 *                                                                    /* clamp= */ true);
 *         MatrixColorFilterBlock::AddBlock(keyContext, matrixCFData);
 *     }
 *
 *     bool fInHSLA;
 * }
 * ```
 */
public open class PrecompileMatrixColorFilter public constructor(
  inHSLA: Boolean,
) : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * bool fInHSLA
   * ```
   */
  private var fInHSLA: Boolean = TODO("Initialize fInHSLA")

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination == 0);
   *         static constexpr float kIdentity[20] = { 1, 0, 0, 0, 0,
   *                                                  0, 1, 0, 0, 0,
   *                                                  0, 0, 1, 0, 0,
   *                                                  0, 0, 0, 1, 0 };
   *         MatrixColorFilterBlock::MatrixColorFilterData matrixCFData(kIdentity, fInHSLA,
   *                                                                    /* clamp= */ true);
   *         MatrixColorFilterBlock::AddBlock(keyContext, matrixCFData);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
