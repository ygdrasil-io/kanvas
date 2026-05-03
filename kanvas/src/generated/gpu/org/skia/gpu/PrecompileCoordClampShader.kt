package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileCoordClampShader final : public PrecompileShader {
 * public:
 *     PrecompileCoordClampShader(SkSpan<const sk_sp<PrecompileShader>> shaders)
 *             : fShaders(shaders.begin(), shaders.end()) {
 *         fNumShaderCombos = 0;
 *         for (const auto& s : fShaders) {
 *             fNumShaderCombos += s->priv().numCombinations();
 *         }
 *     }
 *
 * private:
 *     int numChildCombinations() const override {
 *         return fNumShaderCombos;
 *     }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < fNumShaderCombos);
 *
 *         constexpr SkRect kIgnored { 0, 0, 256, 256 }; // ignored bc we're precompiling
 *
 *         // TODO: update CoordClampShaderBlock so this is optional
 *         CoordClampShaderBlock::CoordClampData data(kIgnored);
 *
 *         CoordClampShaderBlock::BeginBlock(keyContext, data);
 *             AddToKey<PrecompileShader>(keyContext, fShaders, desiredCombination);
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     std::vector<sk_sp<PrecompileShader>> fShaders;
 *     int fNumShaderCombos;
 * }
 * ```
 */
public class PrecompileCoordClampShader public constructor(
  shaders: SkSpan<SkSp<PrecompileShader>>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileShader>> fShaders
   * ```
   */
  private var fShaders: Int = TODO("Initialize fShaders")

  /**
   * C++ original:
   * ```cpp
   * int fNumShaderCombos
   * ```
   */
  private var fNumShaderCombos: Int = TODO("Initialize fNumShaderCombos")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override {
   *         return fNumShaderCombos;
   *     }
   * ```
   */
  public override fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination < fNumShaderCombos);
   *
   *         constexpr SkRect kIgnored { 0, 0, 256, 256 }; // ignored bc we're precompiling
   *
   *         // TODO: update CoordClampShaderBlock so this is optional
   *         CoordClampShaderBlock::CoordClampData data(kIgnored);
   *
   *         CoordClampShaderBlock::BeginBlock(keyContext, data);
   *             AddToKey<PrecompileShader>(keyContext, fShaders, desiredCombination);
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
