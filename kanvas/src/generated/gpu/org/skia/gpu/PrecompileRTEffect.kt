package org.skia.gpu

import PrecompileChildOptions
import kotlin.Int
import org.skia.effects.SkRuntimeEffect
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * template<typename T>
 * class PrecompileRTEffect : public T {
 * public:
 *     PrecompileRTEffect(sk_sp<SkRuntimeEffect> effect,
 *                        SkSpan<const PrecompileChildOptions> childOptions)
 *             : fEffect(std::move(effect)) {
 *         fChildOptions.reserve(childOptions.size());
 *         for (PrecompileChildOptions c : childOptions) {
 *             fChildOptions.push_back({ c.begin(), c.end() });
 *         }
 *
 *         fNumSlotCombinations.reserve(childOptions.size());
 *         fNumChildCombinations = 1;
 *         for (const std::vector<sk_sp<PrecompileBase>>& optionSet : fChildOptions) {
 *             fNumSlotCombinations.push_back(num_options_in_set(optionSet));
 *             fNumChildCombinations *= fNumSlotCombinations.back();
 *         }
 *
 *         SkASSERT(fChildOptions.size() == fEffect->children().size());
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumChildCombinations; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         SkSpan<const SkRuntimeEffect::Child> childInfo = fEffect->children();
 *
 *         if (!RuntimeEffectBlock::BeginBlock(keyContext, { fEffect })) {
 *             RuntimeEffectBlock::AddNoOpEffect(keyContext, fEffect.get());
 *             return;
 *         }
 *
 *         int remainingCombinations = desiredCombination;
 *
 *         for (size_t rowIndex = 0; rowIndex < fChildOptions.size(); ++rowIndex) {
 *             const std::vector<sk_sp<PrecompileBase>>& slotOptions = fChildOptions[rowIndex];
 *             int numSlotCombinations = fNumSlotCombinations[rowIndex];
 *
 *             const int slotOption = remainingCombinations % numSlotCombinations;
 *             remainingCombinations /= numSlotCombinations;
 *
 *             auto [option, childOptions] = PrecompileBase::SelectOption(
 *                     SkSpan<const sk_sp<PrecompileBase>>(slotOptions),
 *                     slotOption);
 *
 *             KeyContext childContext = keyContext.forRuntimeEffect(fEffect.get(), rowIndex);
 *
 *             SkASSERT(precompilebase_is_valid_as_child(option.get()));
 *             if (option) {
 *                 option->priv().addToKey(childContext, childOptions);
 *             } else {
 *                 SkASSERT(childOptions == 0);
 *
 *                 // We don't have a child effect. Substitute in a no-op effect.
 *                 switch (childInfo[rowIndex].type) {
 *                     case SkRuntimeEffect::ChildType::kShader:
 *                         // A missing shader returns transparent black
 *                         SolidColorShaderBlock::AddBlock(childContext, SK_PMColor4fTRANSPARENT);
 *                         break;
 *
 *                     case SkRuntimeEffect::ChildType::kColorFilter:
 *                         // A "passthrough" shader returns the input color as-is.
 *                         keyContext.paintParamsKeyBuilder()->addBlock(
 *                                 BuiltInCodeSnippetID::kPriorOutput);
 *                         break;
 *
 *                     case SkRuntimeEffect::ChildType::kBlender:
 *                         // A "passthrough" blender performs `blend_src_over(src, dest)`.
 *                         AddFixedBlendMode(childContext, SkBlendMode::kSrcOver);
 *                         break;
 *                 }
 *             }
 *         }
 *
 *         RuntimeEffectBlock::HandleIntrinsics(keyContext, fEffect.get());
 *
 *         keyContext.paintParamsKeyBuilder()->endBlock();
 *     }
 *
 *     sk_sp<SkRuntimeEffect> fEffect;
 *     std::vector<std::vector<sk_sp<PrecompileBase>>> fChildOptions;
 *     skia_private::TArray<int> fNumSlotCombinations;
 *     int fNumChildCombinations;
 * }
 * ```
 */
public open class PrecompileRTEffect<T> public constructor(
  effect: SkSp<SkRuntimeEffect>,
  childOptions: SkSpan<PrecompileChildOptions>,
) : T() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkRuntimeEffect> fEffect
   * ```
   */
  private var fEffect: SkSp<SkRuntimeEffect> = TODO("Initialize fEffect")

  /**
   * C++ original:
   * ```cpp
   * skia_private::TArray<int> fNumSlotCombinations
   * ```
   */
  private var fNumSlotCombinations: Int = TODO("Initialize fNumSlotCombinations")

  /**
   * C++ original:
   * ```cpp
   * int fNumChildCombinations
   * ```
   */
  private var fNumChildCombinations: Int = TODO("Initialize fNumChildCombinations")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumChildCombinations; }
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
   *         SkASSERT(desiredCombination < this->numCombinations());
   *
   *         SkSpan<const SkRuntimeEffect::Child> childInfo = fEffect->children();
   *
   *         if (!RuntimeEffectBlock::BeginBlock(keyContext, { fEffect })) {
   *             RuntimeEffectBlock::AddNoOpEffect(keyContext, fEffect.get());
   *             return;
   *         }
   *
   *         int remainingCombinations = desiredCombination;
   *
   *         for (size_t rowIndex = 0; rowIndex < fChildOptions.size(); ++rowIndex) {
   *             const std::vector<sk_sp<PrecompileBase>>& slotOptions = fChildOptions[rowIndex];
   *             int numSlotCombinations = fNumSlotCombinations[rowIndex];
   *
   *             const int slotOption = remainingCombinations % numSlotCombinations;
   *             remainingCombinations /= numSlotCombinations;
   *
   *             auto [option, childOptions] = PrecompileBase::SelectOption(
   *                     SkSpan<const sk_sp<PrecompileBase>>(slotOptions),
   *                     slotOption);
   *
   *             KeyContext childContext = keyContext.forRuntimeEffect(fEffect.get(), rowIndex);
   *
   *             SkASSERT(precompilebase_is_valid_as_child(option.get()));
   *             if (option) {
   *                 option->priv().addToKey(childContext, childOptions);
   *             } else {
   *                 SkASSERT(childOptions == 0);
   *
   *                 // We don't have a child effect. Substitute in a no-op effect.
   *                 switch (childInfo[rowIndex].type) {
   *                     case SkRuntimeEffect::ChildType::kShader:
   *                         // A missing shader returns transparent black
   *                         SolidColorShaderBlock::AddBlock(childContext, SK_PMColor4fTRANSPARENT);
   *                         break;
   *
   *                     case SkRuntimeEffect::ChildType::kColorFilter:
   *                         // A "passthrough" shader returns the input color as-is.
   *                         keyContext.paintParamsKeyBuilder()->addBlock(
   *                                 BuiltInCodeSnippetID::kPriorOutput);
   *                         break;
   *
   *                     case SkRuntimeEffect::ChildType::kBlender:
   *                         // A "passthrough" blender performs `blend_src_over(src, dest)`.
   *                         AddFixedBlendMode(childContext, SkBlendMode::kSrcOver);
   *                         break;
   *                 }
   *             }
   *         }
   *
   *         RuntimeEffectBlock::HandleIntrinsics(keyContext, fEffect.get());
   *
   *         keyContext.paintParamsKeyBuilder()->endBlock();
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
