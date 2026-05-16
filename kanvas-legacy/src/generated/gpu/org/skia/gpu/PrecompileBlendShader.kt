package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlendShader final : public PrecompileShader {
 * public:
 *     PrecompileBlendShader(PrecompileBlenderList&& blenders,
 *                           SkSpan<const sk_sp<PrecompileShader>> dsts,
 *                           SkSpan<const sk_sp<PrecompileShader>> srcs)
 *             : fBlenderOptions(std::move(blenders))
 *             , fDstOptions(dsts.begin(), dsts.end())
 *             , fSrcOptions(srcs.begin(), srcs.end()) {
 *         fNumDstCombos = 0;
 *         for (const auto& d : fDstOptions) {
 *             fNumDstCombos += d->priv().numCombinations();
 *         }
 *
 *         fNumSrcCombos = 0;
 *         for (const auto& s : fSrcOptions) {
 *             fNumSrcCombos += s->priv().numCombinations();
 *         }
 *     }
 *
 * private:
 *     int numChildCombinations() const override {
 *         return fBlenderOptions.numCombinations() * fNumDstCombos * fNumSrcCombos;
 *     }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         const int desiredDstCombination = desiredCombination % fNumDstCombos;
 *         int remainingCombinations = desiredCombination / fNumDstCombos;
 *
 *         const int desiredSrcCombination = remainingCombinations % fNumSrcCombos;
 *         remainingCombinations /= fNumSrcCombos;
 *
 *         int desiredBlendCombination = remainingCombinations;
 *         SkASSERT(desiredBlendCombination < fBlenderOptions.numCombinations());
 *
 *         auto [blender, blenderCombination] = fBlenderOptions.selectOption(desiredBlendCombination);
 *         if (blender->priv().asBlendMode()) {
 *             // Coefficient and HSLC blends, and other fixed SkBlendMode blenders use the
 *             // BlendCompose block to organize the children.
 *             BlendComposeBlock::BeginBlock(keyContext);
 *         } else {
 *             // Runtime blenders are wrapped in the kBlend runtime shader, although functionally
 *             // it is identical to the BlendCompose snippet.
 *             const SkRuntimeEffect* blendEffect =
 *                     GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kBlend);
 *
 *             RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(blendEffect) });
 *         }
 *
 *         AddToKey<PrecompileShader>(keyContext, fSrcOptions, desiredSrcCombination);
 *         AddToKey<PrecompileShader>(keyContext, fDstOptions, desiredDstCombination);
 *
 *         if (blender->priv().asBlendMode()) {
 *             SkASSERT(blenderCombination == 0);
 *             AddBlendMode(keyContext, *blender->priv().asBlendMode());
 *         } else {
 *             blender->priv().addToKey(keyContext, blenderCombination);
 *         }
 *
 *         keyContext.paintParamsKeyBuilder()->endBlock();  // BlendComposeBlock or RuntimeEffectBlock
 *     }
 *
 *     PrecompileBlenderList fBlenderOptions;
 *     std::vector<sk_sp<PrecompileShader>> fDstOptions;
 *     std::vector<sk_sp<PrecompileShader>> fSrcOptions;
 *
 *     int fNumDstCombos;
 *     int fNumSrcCombos;
 * }
 * ```
 */
public class PrecompileBlendShader public constructor(
  blenders: PrecompileBlenderList,
  dsts: SkSpan<SkSp<PrecompileShader>>,
  srcs: SkSpan<SkSp<PrecompileShader>>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * PrecompileBlenderList fBlenderOptions
   * ```
   */
  private var fBlenderOptions: PrecompileBlenderList = TODO("Initialize fBlenderOptions")

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileShader>> fDstOptions
   * ```
   */
  private var fDstOptions: Int = TODO("Initialize fDstOptions")

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileShader>> fSrcOptions
   * ```
   */
  private var fSrcOptions: Int = TODO("Initialize fSrcOptions")

  /**
   * C++ original:
   * ```cpp
   * int fNumDstCombos
   * ```
   */
  private var fNumDstCombos: Int = TODO("Initialize fNumDstCombos")

  /**
   * C++ original:
   * ```cpp
   * int fNumSrcCombos
   * ```
   */
  private var fNumSrcCombos: Int = TODO("Initialize fNumSrcCombos")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override {
   *         return fBlenderOptions.numCombinations() * fNumDstCombos * fNumSrcCombos;
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
   *         SkASSERT(desiredCombination < this->numCombinations());
   *
   *         const int desiredDstCombination = desiredCombination % fNumDstCombos;
   *         int remainingCombinations = desiredCombination / fNumDstCombos;
   *
   *         const int desiredSrcCombination = remainingCombinations % fNumSrcCombos;
   *         remainingCombinations /= fNumSrcCombos;
   *
   *         int desiredBlendCombination = remainingCombinations;
   *         SkASSERT(desiredBlendCombination < fBlenderOptions.numCombinations());
   *
   *         auto [blender, blenderCombination] = fBlenderOptions.selectOption(desiredBlendCombination);
   *         if (blender->priv().asBlendMode()) {
   *             // Coefficient and HSLC blends, and other fixed SkBlendMode blenders use the
   *             // BlendCompose block to organize the children.
   *             BlendComposeBlock::BeginBlock(keyContext);
   *         } else {
   *             // Runtime blenders are wrapped in the kBlend runtime shader, although functionally
   *             // it is identical to the BlendCompose snippet.
   *             const SkRuntimeEffect* blendEffect =
   *                     GetKnownRuntimeEffect(SkKnownRuntimeEffects::StableKey::kBlend);
   *
   *             RuntimeEffectBlock::BeginBlock(keyContext, { sk_ref_sp(blendEffect) });
   *         }
   *
   *         AddToKey<PrecompileShader>(keyContext, fSrcOptions, desiredSrcCombination);
   *         AddToKey<PrecompileShader>(keyContext, fDstOptions, desiredDstCombination);
   *
   *         if (blender->priv().asBlendMode()) {
   *             SkASSERT(blenderCombination == 0);
   *             AddBlendMode(keyContext, *blender->priv().asBlendMode());
   *         } else {
   *             blender->priv().addToKey(keyContext, blenderCombination);
   *         }
   *
   *         keyContext.paintParamsKeyBuilder()->endBlock();  // BlendComposeBlock or RuntimeEffectBlock
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
