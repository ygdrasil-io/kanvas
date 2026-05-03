package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileGaussianColorFilter : public PrecompileColorFilter {
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination == 0);
 *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kGaussianColorFilter);
 *     }
 * }
 * ```
 */
public open class PrecompileGaussianColorFilter : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination == 0);
   *         keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kGaussianColorFilter);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
