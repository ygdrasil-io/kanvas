package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileColorFilterShader final : public PrecompileShader {
 * public:
 *     PrecompileColorFilterShader(SkSpan<const sk_sp<PrecompileShader>> shaders,
 *                                 SkSpan<const sk_sp<PrecompileColorFilter>> colorFilters)
 *             : fShaders(shaders.begin(), shaders.end())
 *             , fColorFilters(colorFilters.begin(), colorFilters.end()) {
 *         fNumShaderCombos = 0;
 *         for (const auto& s : fShaders) {
 *             fNumShaderCombos += s->priv().numCombinations();
 *         }
 *         fNumColorFilterCombos = 0;
 *         for (const auto& cf : fColorFilters) {
 *             fNumColorFilterCombos += cf->priv().numCombinations();
 *         }
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumShaderCombos * fNumColorFilterCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         int desiredShaderCombination = desiredCombination % fNumShaderCombos;
 *         int desiredColorFilterCombination = desiredCombination / fNumShaderCombos;
 *         SkASSERT(desiredColorFilterCombination < fNumColorFilterCombos);
 *
 *         Compose(keyContext,
 *                 /* addInnerToKey= */ [&]() -> void {
 *                     AddToKey<PrecompileShader>(keyContext, fShaders, desiredShaderCombination);
 *                 },
 *                 /* addOuterToKey= */ [&]() -> void {
 *                     AddToKey<PrecompileColorFilter>(keyContext, fColorFilters,
 *                                                     desiredColorFilterCombination);
 *                 });
 *     }
 *
 *     std::vector<sk_sp<PrecompileShader>>      fShaders;
 *     std::vector<sk_sp<PrecompileColorFilter>> fColorFilters;
 *     int fNumShaderCombos;
 *     int fNumColorFilterCombos;
 * }
 * ```
 */
public class PrecompileColorFilterShader public constructor(
  shaders: SkSpan<SkSp<PrecompileShader>>,
  colorFilters: SkSpan<SkSp<PrecompileColorFilter>>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileShader>>      fShaders
   * ```
   */
  private var fShaders: Int = TODO("Initialize fShaders")

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileColorFilter>> fColorFilters
   * ```
   */
  private var fColorFilters: Int = TODO("Initialize fColorFilters")

  /**
   * C++ original:
   * ```cpp
   * int fNumShaderCombos
   * ```
   */
  private var fNumShaderCombos: Int = TODO("Initialize fNumShaderCombos")

  /**
   * C++ original:
   * ```cpp
   * int fNumColorFilterCombos
   * ```
   */
  private var fNumColorFilterCombos: Int = TODO("Initialize fNumColorFilterCombos")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumShaderCombos * fNumColorFilterCombos; }
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
   *         int desiredShaderCombination = desiredCombination % fNumShaderCombos;
   *         int desiredColorFilterCombination = desiredCombination / fNumShaderCombos;
   *         SkASSERT(desiredColorFilterCombination < fNumColorFilterCombos);
   *
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     AddToKey<PrecompileShader>(keyContext, fShaders, desiredShaderCombination);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     AddToKey<PrecompileColorFilter>(keyContext, fColorFilters,
   *                                                     desiredColorFilterCombination);
   *                 });
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
