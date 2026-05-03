package org.skia.gpu

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class PrecompileBlenderList {
 * public:
 *     PrecompileBlenderList(SkSpan<const sk_sp<PrecompileBlender>> blenders);
 *     PrecompileBlenderList(SkSpan<const SkBlendMode> blendModes);
 *
 *     int numCombinations() const { return fNumCombos; }
 *
 *     // For options that use a consolidated blend function, a representative blend mode is returned.
 *     // Blend modes passed directly to the list's constructor will be re-wrapped in a
 *     // PrecompileBlender that returns the correct value from asBlendMode().
 *     //
 *     // The representative blend mode is consistent with the block selection logic in AddBlendMode().
 *     std::pair<sk_sp<PrecompileBlender>, int> selectOption(int desiredCombination) const;
 *
 * private:
 *     // Porter Duff and HSLC blend modes are removed, but any remaining SkBlendModes that do not
 *     // have a consolidated function must be fixed in the PaintParamsKey just like runtime blenders.
 *     std::vector<sk_sp<PrecompileBlender>> fFixedBlenderEffects;
 *     bool fHasPorterDuffBlender = false;
 *     bool fHasHSLCBlender = false;
 *
 *     int fNumCombos = 0;
 * }
 * ```
 */
public data class PrecompileBlenderList public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileBlender>> fFixedBlenderEffects
   * ```
   */
  private var fFixedBlenderEffects: Int,
  /**
   * C++ original:
   * ```cpp
   * bool fHasPorterDuffBlender = false
   * ```
   */
  private var fHasPorterDuffBlender: Boolean,
  /**
   * C++ original:
   * ```cpp
   * bool fHasHSLCBlender = false
   * ```
   */
  private var fHasHSLCBlender: Boolean,
  /**
   * C++ original:
   * ```cpp
   * int fNumCombos = 0
   * ```
   */
  private var fNumCombos: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * int numCombinations() const { return fNumCombos; }
   * ```
   */
  public fun numCombinations(): Int {
    TODO("Implement numCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * std::pair<sk_sp<PrecompileBlender>, int> PrecompileBlenderList::selectOption(
   *         int desiredCombination) const {
   *     SkASSERT(desiredCombination >= 0 && desiredCombination < this->numCombinations());
   *
   *     if (fHasPorterDuffBlender) {
   *         if (desiredCombination == 0) {
   *             // Porter-Duff constant consolidated blend option, pick kSrcOver as a stand-in
   *             return {PrecompileBlenders::Mode(SkBlendMode::kSrcOver), 0};
   *         } else {
   *             desiredCombination--;
   *         }
   *     }
   *
   *     if (fHasHSLCBlender) {
   *         if (desiredCombination == 0) {
   *             // HSLC blend option, pick kHue arbitrarily
   *             return {PrecompileBlenders::Mode(SkBlendMode::kHue), 0};
   *         } else {
   *             desiredCombination--;
   *         }
   *     }
   *
   *     if (!fFixedBlenderEffects.empty()) {
   *         auto [option, childCombination] =
   *                 PrecompileBase::SelectOption<PrecompileBlender>(fFixedBlenderEffects,
   *                                                                 desiredCombination);
   *
   *         // Double-check that we aren't returning a blend mode that should have been consolidated.
   *         SkDEBUGCODE(auto bm = option->priv().asBlendMode();)
   *         SkASSERT(!bm || (*bm > SkBlendMode::kXor && *bm < SkBlendMode::kHue));
   *         return {option, childCombination};
   *     }
   *
   *     SkUNREACHABLE;
   * }
   * ```
   */
  public fun selectOption(desiredCombination: Int): Int {
    TODO("Implement selectOption")
  }
}
