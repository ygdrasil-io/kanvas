package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileTableColorFilter : public PrecompileColorFilter {
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination == 0);
 *         TableColorFilterBlock::TableColorFilterData data(/* proxy= */ nullptr);
 *         TableColorFilterBlock::AddBlock(keyContext, data);
 *     }
 * }
 * ```
 */
public open class PrecompileTableColorFilter : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination == 0);
   *         TableColorFilterBlock::TableColorFilterData data(/* proxy= */ nullptr);
   *         TableColorFilterBlock::AddBlock(keyContext, data);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
