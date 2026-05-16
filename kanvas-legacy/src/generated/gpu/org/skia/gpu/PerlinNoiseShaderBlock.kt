package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct PerlinNoiseShaderBlock {
 *     enum class Type {
 *         kFractalNoise,
 *         kTurbulence,
 *     };
 *
 *     struct PerlinNoiseData {
 *         PerlinNoiseData(Type type,
 *                         SkVector baseFrequency,
 *                         int numOctaves,
 *                         SkISize stitchData)
 *             : fType(type)
 *             , fBaseFrequency(baseFrequency)
 *             , fNumOctaves(numOctaves)
 *             , fStitchData{ SkIntToFloat(stitchData.fWidth), SkIntToFloat(stitchData.fHeight) } {
 *         }
 *
 *         bool stitching() const { return !fStitchData.isZero(); }
 *
 *         Type fType;
 *         SkVector fBaseFrequency;
 *         int fNumOctaves;
 *         SkVector fStitchData;
 *
 *         sk_sp<TextureProxy> fPermutationsProxy;
 *         sk_sp<TextureProxy> fNoiseProxy;
 *     };
 *
 *     static void AddBlock(const KeyContext&, const PerlinNoiseData&);
 * }
 * ```
 */
public open class PerlinNoiseShaderBlock {
  public data class PerlinNoiseData public constructor(
    public var fType: org.skia.gpu.Type,
    public var fBaseFrequency: Int,
    public var fNumOctaves: Int,
    public var fStitchData: Int,
    public var fPermutationsProxy: Int,
    public var fNoiseProxy: Int,
  ) {
    public fun stitching(): Boolean {
      TODO("Implement stitching")
    }
  }

  public enum class Type {
    kFractalNoise,
    kTurbulence,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * void PerlinNoiseShaderBlock::AddBlock(const KeyContext& keyContext,
     *                                       const PerlinNoiseData& noiseData) {
     *     add_perlin_noise_uniform_data(keyContext, noiseData);
     *
     *     keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPerlinNoiseShader);
     * }
     * ```
     */
    public fun addBlock(keyContext: KeyContext, noiseData: PerlinNoiseData) {
      TODO("Implement addBlock")
    }
  }
}
