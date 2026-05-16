package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileColorShader final : public PrecompileShader {
 * private:
 *     bool isConstant(int desiredCombination) const override {
 *         SkASSERT(desiredCombination == 0); // The color shader only ever has one combination
 *         return true;
 *     }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination == 0); // The color shader only ever has one combination
 *         // The white PMColor is just a placeholder for the actual paint params color
 *         SolidColorShaderBlock::AddBlock(keyContext, SK_PMColor4fWHITE);
 *     }
 * }
 * ```
 */
public class PrecompileColorShader : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * bool isConstant(int desiredCombination) const override {
   *         SkASSERT(desiredCombination == 0); // The color shader only ever has one combination
   *         return true;
   *     }
   * ```
   */
  public override fun isConstant(desiredCombination: Int): Boolean {
    TODO("Implement isConstant")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination == 0); // The color shader only ever has one combination
   *         // The white PMColor is just a placeholder for the actual paint params color
   *         SolidColorShaderBlock::AddBlock(keyContext, SK_PMColor4fWHITE);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
