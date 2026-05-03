package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkBlendMode

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlendModeBlender final : public PrecompileBlender {
 * public:
 *     PrecompileBlendModeBlender(SkBlendMode blendMode) : fBlendMode(blendMode) {}
 *
 * protected:
 *     std::optional<SkBlendMode> asBlendMode() const final { return fBlendMode; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const final {
 *         SkASSERT(desiredCombination == 0); // The blend mode blender only ever has one combination
 *         AddBlendMode(keyContext, fBlendMode);
 *     }
 *
 * private:
 *     SkBlendMode fBlendMode;
 * }
 * ```
 */
public class PrecompileBlendModeBlender public constructor(
  blendMode: SkBlendMode,
) : PrecompileBlender() {
  /**
   * C++ original:
   * ```cpp
   * SkBlendMode fBlendMode
   * ```
   */
  private var fBlendMode: SkBlendMode = TODO("Initialize fBlendMode")

  /**
   * C++ original:
   * ```cpp
   * std::optional<SkBlendMode> asBlendMode() const final { return fBlendMode; }
   * ```
   */
  protected override fun asBlendMode(): Int {
    TODO("Implement asBlendMode")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const final {
   *         SkASSERT(desiredCombination == 0); // The blend mode blender only ever has one combination
   *         AddBlendMode(keyContext, fBlendMode);
   *     }
   * ```
   */
  protected fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
