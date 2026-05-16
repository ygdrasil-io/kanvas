package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrecompileLightingShader final : public PrecompileShader {
 * public:
 *     PrecompileLightingShader(sk_sp<PrecompileShader> wrapped)
 *             : fWrapped(std::move(wrapped)) {
 *         fNumWrappedCombos = fWrapped->priv().numCombinations();
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumWrappedCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < fNumWrappedCombos);
 *
 *         const SkRuntimeEffect* normalEffect =
 *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kNormal);
 *         const SkRuntimeEffect* lightingEffect =
 *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kLighting);
 *         SkASSERT(normalEffect->children().size() == 1 &&
 *                  lightingEffect->children().size() == 1);
 *
 *         KeyContext lightingContext = keyContext.forRuntimeEffect(lightingEffect, /*child=*/0);
 *         KeyContext normalContext = lightingContext.forRuntimeEffect(normalEffect, /*child=*/0);
 *
 *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(lightingEffect) });
 *             RuntimeEffectBlock::BeginBlock(lightingContext, { sk_ref_sp(normalEffect) });
 *                 fWrapped->priv().addToKey(normalContext, desiredCombination);
 *             keyContext.paintParamsKeyBuilder()->endBlock();
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     sk_sp<PrecompileShader> fWrapped;
 *     int fNumWrappedCombos;
 * }
 * ```
 */
public class PrecompileLightingShader public constructor(
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
   *         const SkRuntimeEffect* normalEffect =
   *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kNormal);
   *         const SkRuntimeEffect* lightingEffect =
   *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kLighting);
   *         SkASSERT(normalEffect->children().size() == 1 &&
   *                  lightingEffect->children().size() == 1);
   *
   *         KeyContext lightingContext = keyContext.forRuntimeEffect(lightingEffect, /*child=*/0);
   *         KeyContext normalContext = lightingContext.forRuntimeEffect(normalEffect, /*child=*/0);
   *
   *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(lightingEffect) });
   *             RuntimeEffectBlock::BeginBlock(lightingContext, { sk_ref_sp(normalEffect) });
   *                 fWrapped->priv().addToKey(normalContext, desiredCombination);
   *             keyContext.paintParamsKeyBuilder()->endBlock();
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
