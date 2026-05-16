package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlurShader final : public PrecompileShader {
 * public:
 *     PrecompileBlurShader(sk_sp<PrecompileShader> wrapped)
 *             : fWrapped(std::move(wrapped)) {
 *         fNumWrappedCombos = fWrapped->priv().numCombinations();
 *     }
 *
 * private:
 *     // 6 known 1D blur effects + 6 known 2D blur effects
 *     inline static constexpr int kNumIntrinsicCombinations = 12;
 *
 *     int numIntrinsicCombinations() const override { return kNumIntrinsicCombinations; }
 *
 *     int numChildCombinations() const override { return fNumWrappedCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         using namespace SkKnownRuntimeEffects;
 *
 *         int desiredBlurCombination = desiredCombination % kNumIntrinsicCombinations;
 *         int desiredWrappedCombination = desiredCombination / kNumIntrinsicCombinations;
 *         SkASSERT(desiredWrappedCombination < fNumWrappedCombos);
 *
 *         static const StableKey kIDs[kNumIntrinsicCombinations] = {
 *                 StableKey::k1DBlur4,  StableKey::k1DBlur8,  StableKey::k1DBlur12,
 *                 StableKey::k1DBlur16, StableKey::k1DBlur20, StableKey::k1DBlur28,
 *
 *                 StableKey::k2DBlur4,  StableKey::k2DBlur8,  StableKey::k2DBlur12,
 *                 StableKey::k2DBlur16, StableKey::k2DBlur20, StableKey::k2DBlur28,
 *         };
 *
 *         const SkRuntimeEffect* effect = GetKnownRuntimeEffect(kIDs[desiredBlurCombination]);
 *         SkASSERT(effect->children().size() == 1);
 *
 *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
 *             fWrapped->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
 *                                       desiredWrappedCombination);
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     sk_sp<PrecompileShader> fWrapped;
 *     int fNumWrappedCombos;
 * }
 * ```
 */
public class PrecompileBlurShader public constructor(
  wrapped: SkSp<PrecompileShader>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * inline static constexpr int kNumIntrinsicCombinations = 12
   * ```
   */
  private var fWrapped: SkSp<PrecompileShader> = TODO("Initialize fWrapped")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> fWrapped
   * ```
   */
  private var fNumWrappedCombos: Int = TODO("Initialize fNumWrappedCombos")

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override { return kNumIntrinsicCombinations; }
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
   *         SkASSERT(desiredCombination < this->numCombinations());
   *
   *         using namespace SkKnownRuntimeEffects;
   *
   *         int desiredBlurCombination = desiredCombination % kNumIntrinsicCombinations;
   *         int desiredWrappedCombination = desiredCombination / kNumIntrinsicCombinations;
   *         SkASSERT(desiredWrappedCombination < fNumWrappedCombos);
   *
   *         static const StableKey kIDs[kNumIntrinsicCombinations] = {
   *                 StableKey::k1DBlur4,  StableKey::k1DBlur8,  StableKey::k1DBlur12,
   *                 StableKey::k1DBlur16, StableKey::k1DBlur20, StableKey::k1DBlur28,
   *
   *                 StableKey::k2DBlur4,  StableKey::k2DBlur8,  StableKey::k2DBlur12,
   *                 StableKey::k2DBlur16, StableKey::k2DBlur20, StableKey::k2DBlur28,
   *         };
   *
   *         const SkRuntimeEffect* effect = GetKnownRuntimeEffect(kIDs[desiredBlurCombination]);
   *         SkASSERT(effect->children().size() == 1);
   *
   *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
   *             fWrapped->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
   *                                       desiredWrappedCombination);
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  public companion object {
    private val kNumIntrinsicCombinations: Int = TODO("Initialize kNumIntrinsicCombinations")
  }
}
