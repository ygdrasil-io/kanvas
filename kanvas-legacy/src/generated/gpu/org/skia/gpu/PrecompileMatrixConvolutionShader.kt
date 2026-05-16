package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrecompileMatrixConvolutionShader final : public PrecompileShader {
 * public:
 *     PrecompileMatrixConvolutionShader(sk_sp<PrecompileShader> wrapped)
 *             : fWrapped(std::move(wrapped)) {
 *         fNumWrappedCombos = fWrapped->priv().numCombinations();
 *
 *         // When the matrix convolution ImageFilter uses a texture we know it will only ever
 *         // be SkFilterMode::kNearest and SkTileMode::kClamp.
 *         // TODO: add a PrecompileImageShaderFlags to further limit the raw image shader
 *         // combinations. Right now we're getting two combinations for the raw shader
 *         // (sk_image_shader and sk_hw_image_shader).
 *         fRawImageShader = PrecompileShaders::RawImage();
 *         fNumRawImageShaderCombos = fRawImageShader->priv().numCombinations();
 *     }
 *
 * private:
 *     int numIntrinsicCombinations() const override {
 *         // The uniform version only has one option but the two texture-based versions will
 *         // have as many combinations as the raw image shader.
 *         return 1 + 2 * fNumRawImageShaderCombos;
 *     }
 *
 *     int numChildCombinations() const override { return fNumWrappedCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *
 *         int desiredTextureCombination = 0;
 *
 *         const int desiredWrappedCombination = desiredCombination % fNumWrappedCombos;
 *         int remainingCombinations = desiredCombination / fNumWrappedCombos;
 *
 *         SkKnownRuntimeEffects::StableKey stableKey = SkKnownRuntimeEffects::StableKey::kInvalid;
 *         if (remainingCombinations == 0) {
 *             stableKey = SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms;
 *         } else {
 *             static constexpr SkKnownRuntimeEffects::StableKey kTextureBasedStableKeys[] = {
 *                     SkKnownRuntimeEffects::StableKey::kMatrixConvTexSm,
 *                     SkKnownRuntimeEffects::StableKey::kMatrixConvTexLg,
 *             };
 *
 *             --remainingCombinations;
 *             stableKey = kTextureBasedStableKeys[remainingCombinations % 2];
 *             desiredTextureCombination = remainingCombinations / 2;
 *             SkASSERT(desiredTextureCombination < fNumRawImageShaderCombos);
 *         }
 *
 *         const SkRuntimeEffect* effect = GetKnownRuntimeEffect(stableKey);
 *
 *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
 *             fWrapped->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
 *                                       desiredWrappedCombination);
 *             if (stableKey != SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms) {
 *                 SkASSERT(effect->children().size() == 2);
 *                 fRawImageShader->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/1),
 *                                                  desiredTextureCombination);
 *             } else {
 *                 SkASSERT(effect->children().size() == 1);
 *             }
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     sk_sp<PrecompileShader> fWrapped;
 *     int fNumWrappedCombos;
 *     sk_sp<PrecompileShader> fRawImageShader;
 *     int fNumRawImageShaderCombos;
 * }
 * ```
 */
public class PrecompileMatrixConvolutionShader public constructor(
  wrapped: SkSp<PrecompileShader>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> fWrapped
   * ```
   */
  private var fWrapped: SkSp<PrecompileShader> = TODO("Initialize fWrapped")

  /**
   * C++ original:
   * ```cpp
   * int fNumWrappedCombos
   * ```
   */
  private var fNumWrappedCombos: Int = TODO("Initialize fNumWrappedCombos")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> fRawImageShader
   * ```
   */
  private var fRawImageShader: SkSp<PrecompileShader> = TODO("Initialize fRawImageShader")

  /**
   * C++ original:
   * ```cpp
   * int fNumRawImageShaderCombos
   * ```
   */
  private var fNumRawImageShaderCombos: Int = TODO("Initialize fNumRawImageShaderCombos")

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override {
   *         // The uniform version only has one option but the two texture-based versions will
   *         // have as many combinations as the raw image shader.
   *         return 1 + 2 * fNumRawImageShaderCombos;
   *     }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumWrappedCombos; }
   * ```
   */
  public override fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *
   *         int desiredTextureCombination = 0;
   *
   *         const int desiredWrappedCombination = desiredCombination % fNumWrappedCombos;
   *         int remainingCombinations = desiredCombination / fNumWrappedCombos;
   *
   *         SkKnownRuntimeEffects::StableKey stableKey = SkKnownRuntimeEffects::StableKey::kInvalid;
   *         if (remainingCombinations == 0) {
   *             stableKey = SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms;
   *         } else {
   *             static constexpr SkKnownRuntimeEffects::StableKey kTextureBasedStableKeys[] = {
   *                     SkKnownRuntimeEffects::StableKey::kMatrixConvTexSm,
   *                     SkKnownRuntimeEffects::StableKey::kMatrixConvTexLg,
   *             };
   *
   *             --remainingCombinations;
   *             stableKey = kTextureBasedStableKeys[remainingCombinations % 2];
   *             desiredTextureCombination = remainingCombinations / 2;
   *             SkASSERT(desiredTextureCombination < fNumRawImageShaderCombos);
   *         }
   *
   *         const SkRuntimeEffect* effect = GetKnownRuntimeEffect(stableKey);
   *
   *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
   *             fWrapped->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
   *                                       desiredWrappedCombination);
   *             if (stableKey != SkKnownRuntimeEffects::StableKey::kMatrixConvUniforms) {
   *                 SkASSERT(effect->children().size() == 2);
   *                 fRawImageShader->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/1),
   *                                                  desiredTextureCombination);
   *             } else {
   *                 SkASSERT(effect->children().size() == 1);
   *             }
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
