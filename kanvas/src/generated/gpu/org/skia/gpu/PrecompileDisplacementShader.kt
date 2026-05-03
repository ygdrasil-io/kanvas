package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class PrecompileDisplacementShader final : public PrecompileShader {
 * public:
 *     PrecompileDisplacementShader(sk_sp<PrecompileShader> displacement,
 *                                  sk_sp<PrecompileShader> color)
 *             : fDisplacement(std::move(displacement))
 *             , fColor(std::move(color)) {
 *         fNumDisplacementCombos = fDisplacement->priv().numCombinations();
 *         fNumColorCombos = fColor->priv().numCombinations();
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumDisplacementCombos * fNumColorCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numChildCombinations());
 *
 *         const int desiredDisplacementCombination = desiredCombination % fNumDisplacementCombos;
 *         const int desiredColorCombination = desiredCombination / fNumDisplacementCombos;
 *         SkASSERT(desiredColorCombination < fNumColorCombos);
 *
 *         const SkRuntimeEffect* effect =
 *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kDisplacement);
 *         SkASSERT(effect->children().size() == 2);
 *
 *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
 *             fDisplacement->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
 *                                            desiredDisplacementCombination);
 *             fColor->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/1),
 *                                     desiredColorCombination);
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     sk_sp<PrecompileShader> fDisplacement;
 *     int fNumDisplacementCombos;
 *     sk_sp<PrecompileShader> fColor;
 *     int fNumColorCombos;
 * }
 * ```
 */
public class PrecompileDisplacementShader public constructor(
  displacement: SkSp<PrecompileShader>,
  color: SkSp<PrecompileShader>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> fDisplacement
   * ```
   */
  private var fDisplacement: SkSp<PrecompileShader> = TODO("Initialize fDisplacement")

  /**
   * C++ original:
   * ```cpp
   * int fNumDisplacementCombos
   * ```
   */
  private var fNumDisplacementCombos: Int = TODO("Initialize fNumDisplacementCombos")

  /**
   * C++ original:
   * ```cpp
   * sk_sp<PrecompileShader> fColor
   * ```
   */
  private var fColor: SkSp<PrecompileShader> = TODO("Initialize fColor")

  /**
   * C++ original:
   * ```cpp
   * int fNumColorCombos
   * ```
   */
  private var fNumColorCombos: Int = TODO("Initialize fNumColorCombos")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumDisplacementCombos * fNumColorCombos; }
   * ```
   */
  public override fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination < this->numChildCombinations());
   *
   *         const int desiredDisplacementCombination = desiredCombination % fNumDisplacementCombos;
   *         const int desiredColorCombination = desiredCombination / fNumDisplacementCombos;
   *         SkASSERT(desiredColorCombination < fNumColorCombos);
   *
   *         const SkRuntimeEffect* effect =
   *                 GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kDisplacement);
   *         SkASSERT(effect->children().size() == 2);
   *
   *         RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(effect) });
   *             fDisplacement->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/0),
   *                                            desiredDisplacementCombination);
   *             fColor->priv().addToKey(keyContext.forRuntimeEffect(effect, /*child=*/1),
   *                                     desiredColorCombination);
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
