package org.skia.gpu

import kotlin.Int
import org.skia.core.StableKey
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrecompileMorphologyShader final : public PrecompileShader {
 * public:
 *     PrecompileMorphologyShader(sk_sp<PrecompileShader> wrapped,
 *                                SkKnownRuntimeEffects::StableKey stableKey)
 *             : fWrapped(std::move(wrapped))
 *             , fStableKey(stableKey) {
 *         fNumWrappedCombos = fWrapped->priv().numCombinations();
 *         SkASSERT(stableKey == SkKnownRuntimeEffects::StableKey::kLinearMorphology ||
 *                  stableKey == SkKnownRuntimeEffects::StableKey::kSparseMorphology);
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumWrappedCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < fNumWrappedCombos);
 *
 *         const SkRuntimeEffect* effect = GetKnownRuntimeEffect(fStableKey);
 *         SkASSERT(effect->children().size() == 1);
 *
 *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
 *             fWrapped->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
 *                                       desiredCombination);
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     sk_sp<PrecompileShader> fWrapped;
 *     int fNumWrappedCombos;
 *     SkKnownRuntimeEffects::StableKey fStableKey;
 * }
 * ```
 */
public class PrecompileMorphologyShader public constructor(
  wrapped: SkSp<PrecompileShader>,
  stableKey: StableKey,
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
   * SkKnownRuntimeEffects::StableKey fStableKey
   * ```
   */
  private var fStableKey: StableKey = TODO("Initialize fStableKey")

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
   *         SkASSERT(desiredCombination < fNumWrappedCombos);
   *
   *         const SkRuntimeEffect* effect = GetKnownRuntimeEffect(fStableKey);
   *         SkASSERT(effect->children().size() == 1);
   *
   *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
   *             fWrapped->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
   *                                       desiredCombination);
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
