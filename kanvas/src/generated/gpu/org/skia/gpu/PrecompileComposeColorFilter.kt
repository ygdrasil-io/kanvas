package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileComposeColorFilter : public PrecompileColorFilter {
 * public:
 *     PrecompileComposeColorFilter(SkSpan<const sk_sp<PrecompileColorFilter>> outerOptions,
 *                                  SkSpan<const sk_sp<PrecompileColorFilter>> innerOptions)
 *             : fOuterOptions(outerOptions.begin(), outerOptions.end())
 *             , fInnerOptions(innerOptions.begin(), innerOptions.end()) {
 *
 *         fNumOuterCombos = 0;
 *         for (const auto& outerOption : fOuterOptions) {
 *             fNumOuterCombos += outerOption ? outerOption->priv().numCombinations() : 1;
 *         }
 *
 *         fNumInnerCombos = 0;
 *         for (const auto& innerOption : fInnerOptions) {
 *             fNumInnerCombos += innerOption ? innerOption->priv().numCombinations() : 1;
 *         }
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumOuterCombos * fNumInnerCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         const int desiredOuterCombination = desiredCombination % fNumOuterCombos;
 *         int remainingCombinations = desiredCombination / fNumOuterCombos;
 *
 *         const int desiredInnerCombination = remainingCombinations % fNumInnerCombos;
 *         remainingCombinations /= fNumInnerCombos;
 *
 *         SkASSERT(!remainingCombinations);
 *
 *         sk_sp<PrecompileColorFilter> inner, outer;
 *         int innerChildOptions, outerChildOptions;
 *
 *         std::tie(outer, outerChildOptions) = SelectOption<PrecompileColorFilter>(
 *                 fOuterOptions, desiredOuterCombination);
 *         std::tie(inner, innerChildOptions) = SelectOption<PrecompileColorFilter>(
 *                 fInnerOptions, desiredInnerCombination);
 *
 *         if (!inner && !outer) {
 *             // A "passthrough" color filter returns the input color as-is.
 *             keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
 *         } else if (!inner) {
 *             outer->priv().addToKey(keyContext, outerChildOptions);
 *         } else if (!outer) {
 *             inner->priv().addToKey(keyContext, innerChildOptions);
 *         } else {
 *             Compose(keyContext,
 *                     /* addInnerToKey= */ [&]() -> void {
 *                         inner->priv().addToKey(keyContext, innerChildOptions);
 *                     },
 *                     /* addOuterToKey= */ [&]() -> void {
 *                         outer->priv().addToKey(keyContext, outerChildOptions);
 *                     });
 *         }
 *     }
 *
 *     std::vector<sk_sp<PrecompileColorFilter>> fOuterOptions;
 *     std::vector<sk_sp<PrecompileColorFilter>> fInnerOptions;
 *
 *     int fNumOuterCombos;
 *     int fNumInnerCombos;
 * }
 * ```
 */
public open class PrecompileComposeColorFilter public constructor(
  outerOptions: SkSpan<SkSp<PrecompileColorFilter>>,
  innerOptions: SkSpan<SkSp<PrecompileColorFilter>>,
) : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileColorFilter>> fOuterOptions
   * ```
   */
  private var fOuterOptions: Int = TODO("Initialize fOuterOptions")

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileColorFilter>> fInnerOptions
   * ```
   */
  private var fInnerOptions: Int = TODO("Initialize fInnerOptions")

  /**
   * C++ original:
   * ```cpp
   * int fNumOuterCombos
   * ```
   */
  private var fNumOuterCombos: Int = TODO("Initialize fNumOuterCombos")

  /**
   * C++ original:
   * ```cpp
   * int fNumInnerCombos
   * ```
   */
  private var fNumInnerCombos: Int = TODO("Initialize fNumInnerCombos")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumOuterCombos * fNumInnerCombos; }
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
   *         const int desiredOuterCombination = desiredCombination % fNumOuterCombos;
   *         int remainingCombinations = desiredCombination / fNumOuterCombos;
   *
   *         const int desiredInnerCombination = remainingCombinations % fNumInnerCombos;
   *         remainingCombinations /= fNumInnerCombos;
   *
   *         SkASSERT(!remainingCombinations);
   *
   *         sk_sp<PrecompileColorFilter> inner, outer;
   *         int innerChildOptions, outerChildOptions;
   *
   *         std::tie(outer, outerChildOptions) = SelectOption<PrecompileColorFilter>(
   *                 fOuterOptions, desiredOuterCombination);
   *         std::tie(inner, innerChildOptions) = SelectOption<PrecompileColorFilter>(
   *                 fInnerOptions, desiredInnerCombination);
   *
   *         if (!inner && !outer) {
   *             // A "passthrough" color filter returns the input color as-is.
   *             keyContext.paintParamsKeyBuilder()->addBlock(BuiltInCodeSnippetID::kPriorOutput);
   *         } else if (!inner) {
   *             outer->priv().addToKey(keyContext, outerChildOptions);
   *         } else if (!outer) {
   *             inner->priv().addToKey(keyContext, innerChildOptions);
   *         } else {
   *             Compose(keyContext,
   *                     /* addInnerToKey= */ [&]() -> void {
   *                         inner->priv().addToKey(keyContext, innerChildOptions);
   *                     },
   *                     /* addOuterToKey= */ [&]() -> void {
   *                         outer->priv().addToKey(keyContext, outerChildOptions);
   *                     });
   *         }
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
