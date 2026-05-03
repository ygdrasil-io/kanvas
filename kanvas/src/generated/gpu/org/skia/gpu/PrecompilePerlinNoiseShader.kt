package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompilePerlinNoiseShader final : public PrecompileShader {
 * public:
 *     PrecompilePerlinNoiseShader() {}
 *
 * private:
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *
 *         SkASSERT(desiredCombination == 0); // The Perlin noise shader only ever has one combination
 *
 *         // TODO: update PerlinNoiseShaderBlock so the NoiseData is optional
 *         static const PerlinNoiseShaderBlock::PerlinNoiseData kIgnoredNoiseData(
 *                 PerlinNoiseShaderBlock::Type::kFractalNoise, { 0.0f, 0.0f }, 2, {1, 1});
 *
 *         PerlinNoiseShaderBlock::AddBlock(keyContext, kIgnoredNoiseData);
 *     }
 *
 * }
 * ```
 */
public class PrecompilePerlinNoiseShader public constructor() : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *
   *         SkASSERT(desiredCombination == 0); // The Perlin noise shader only ever has one combination
   *
   *         // TODO: update PerlinNoiseShaderBlock so the NoiseData is optional
   *         static const PerlinNoiseShaderBlock::PerlinNoiseData kIgnoredNoiseData(
   *                 PerlinNoiseShaderBlock::Type::kFractalNoise, { 0.0f, 0.0f }, 2, {1, 1});
   *
   *         PerlinNoiseShaderBlock::AddBlock(keyContext, kIgnoredNoiseData);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
