package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlendModeColorFilter : public PrecompileColorFilter {
 * public:
 *     PrecompileBlendModeColorFilter(SkSpan<const SkBlendMode> blendModes)
 *             : fBlendOptions(blendModes) {}
 *
 * private:
 *     int numIntrinsicCombinations() const override {
 *         return fBlendOptions.numCombinations();
 *     }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         auto [blender, option ] = fBlendOptions.selectOption(desiredCombination);
 *         SkASSERT(option == 0 && blender->priv().asBlendMode().has_value());
 *         SkBlendMode representativeBlendMode = *blender->priv().asBlendMode();
 *         // Here the color is just a stand-in for a later value.
 *         AddBlendModeColorFilter(keyContext, representativeBlendMode, SK_PMColor4fWHITE);
 *     }
 *
 *     // NOTE: The BlendMode color filter can only be created with SkBlendModes, not arbitrary
 *     // SkBlenders, so this list will only contain consolidated blend functions or fixed blend mode
 *     // options.
 *     PrecompileBlenderList fBlendOptions;
 * }
 * ```
 */
public open class PrecompileBlendModeColorFilter public constructor(
  blendModes: SkSpan<SkBlendMode>,
) : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * PrecompileBlenderList fBlendOptions
   * ```
   */
  private var fBlendOptions: PrecompileBlenderList = TODO("Initialize fBlendOptions")

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override {
   *         return fBlendOptions.numCombinations();
   *     }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         auto [blender, option ] = fBlendOptions.selectOption(desiredCombination);
   *         SkASSERT(option == 0 && blender->priv().asBlendMode().has_value());
   *         SkBlendMode representativeBlendMode = *blender->priv().asBlendMode();
   *         // Here the color is just a stand-in for a later value.
   *         AddBlendModeColorFilter(keyContext, representativeBlendMode, SK_PMColor4fWHITE);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
