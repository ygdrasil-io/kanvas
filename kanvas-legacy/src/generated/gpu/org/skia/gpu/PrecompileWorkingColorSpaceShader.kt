package org.skia.gpu

import kotlin.Int
import kotlin.Pair
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileWorkingColorSpaceShader final : public PrecompileShader {
 * public:
 *     PrecompileWorkingColorSpaceShader(SkSpan<const sk_sp<PrecompileShader>> shaders,
 *                                       SkSpan<const std::pair<sk_sp<SkColorSpace>,
 *                                                              sk_sp<SkColorSpace>>> colorSpaces)
 *             : fShaders(shaders.begin(), shaders.end())
 *             , fColorSpaces(colorSpaces.begin(), colorSpaces.end()) {
 *         if (colorSpaces.empty()) {
 *             fColorSpaces.push_back({nullptr, nullptr}); // encode identity
 *         }
 *         this->updateNumShaderCombos();
 *     }
 *
 *     PrecompileWorkingColorSpaceShader(SkSpan<const sk_sp<PrecompileShader>> shaders,
 *                                       SkSpan<const sk_sp<SkColorSpace>> inputSpaces,
 *                                       SkSpan<const sk_sp<SkColorSpace>> outputSpaces)
 *             : fShaders(shaders.begin(), shaders.end()) {
 *         static const sk_sp<SkColorSpace> kNullCS;
 *         SkSpan<const sk_sp<SkColorSpace>> nullSpan{&kNullCS, 1};
 *         if (inputSpaces.empty())  { inputSpaces  = nullSpan; }
 *         if (outputSpaces.empty()) { outputSpaces = nullSpan; }
 *
 *         fColorSpaces.reserve(inputSpaces.size() * outputSpaces.size());
 *         for (const sk_sp<SkColorSpace>& iCS : inputSpaces) {
 *             for (const sk_sp<SkColorSpace>& oCS : outputSpaces) {
 *                 fColorSpaces.push_back({iCS, oCS});
 *             }
 *         }
 *
 *         this->updateNumShaderCombos();
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumShaderCombos * fColorSpaces.size(); }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < this->numCombinations());
 *
 *         int desiredShaderCombination = desiredCombination % fNumShaderCombos;
 *         int desiredColorSpaceCombination = desiredCombination / fNumShaderCombos;
 *         SkASSERT(desiredColorSpaceCombination < (int) fColorSpaces.size());
 *
 *         // Check for an identity working colorspace (that is detected up front with
 *         // makeWithColorSpace, but due to return type mismatches, can't be handled as easily with
 *         // the WorkingColorSpace() factory).
 *         if (!fColorSpaces[desiredColorSpaceCombination].first &&
 *             !fColorSpaces[desiredColorSpaceCombination].second) {
 *             // So just add the desired shader direction
 *             AddToKey<PrecompileShader>(keyContext, fShaders, desiredShaderCombination);
 *             return;
 *         }
 *
 *         const SkColorInfo& dstInfo = keyContext.dstColorInfo();
 *         const SkAlphaType dstAT = dstInfo.alphaType();
 *         sk_sp<SkColorSpace> dstCS = dstInfo.refColorSpace();
 *         if (!dstCS) {
 *             dstCS = SkColorSpace::MakeSRGB();
 *         }
 *
 *         sk_sp<SkColorSpace> inputCS = fColorSpaces[desiredColorSpaceCombination].first;
 *         if (!inputCS) {
 *             inputCS = dstCS;
 *         }
 *         sk_sp<SkColorSpace> outputCS = fColorSpaces[desiredColorSpaceCombination].second;
 *         if (!outputCS) {
 *             outputCS = inputCS;
 *         }
 *
 *         // SkWorkingColorSpaceShader's workInUnpremul is not exposed yet in the public API so
 *         // precompile can assume that it'll always use dstAT.
 *         const SkAlphaType workingAT = dstAT;
 *         KeyContext workingContext =
 *                 keyContext.withColorInfo({dstInfo.colorType(), workingAT, inputCS});
 *
 *         Compose(keyContext,
 *                 /* addInnerToKey= */ [&]() -> void {
 *                     AddToKey<PrecompileShader>(workingContext, fShaders, desiredShaderCombination);
 *                 },
 *                 /* addOuterToKey= */ [&]() -> void {
 *                     ColorSpaceTransformBlock::ColorSpaceTransformData data(
 *                             outputCS.get(), workingAT, dstCS.get(), dstAT);
 *                     ColorSpaceTransformBlock::AddBlock(keyContext, data);
 *                 });
 *     }
 *
 *     void updateNumShaderCombos() {
 *         fNumShaderCombos = 0;
 *         for (const auto& s : fShaders) {
 *             fNumShaderCombos += s->priv().numCombinations();
 *         }
 *     }
 *
 *     std::vector<sk_sp<PrecompileShader>> fShaders;
 *     std::vector<std::pair</*input =*/sk_sp<SkColorSpace>,
 *                           /*output=*/sk_sp<SkColorSpace>>> fColorSpaces;
 *     int fNumShaderCombos;
 * }
 * ```
 */
public class PrecompileWorkingColorSpaceShader public constructor(
  shaders: SkSpan<SkSp<PrecompileShader>>,
  colorSpaces: SkSpan<Pair<SkSp<SkColorSpace>, SkSp<SkColorSpace>>>,
) : PrecompileShader() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileShader>> fShaders
   * ```
   */
  private var fShaders: Int = TODO("Initialize fShaders")

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
   * PrecompileWorkingColorSpaceShader(SkSpan<const sk_sp<PrecompileShader>> shaders,
   *                                       SkSpan<const std::pair<sk_sp<SkColorSpace>,
   *                                                              sk_sp<SkColorSpace>>> colorSpaces)
   *             : fShaders(shaders.begin(), shaders.end())
   *             , fColorSpaces(colorSpaces.begin(), colorSpaces.end()) {
   *         if (colorSpaces.empty()) {
   *             fColorSpaces.push_back({nullptr, nullptr}); // encode identity
   *         }
   *         this->updateNumShaderCombos();
   *     }
   * ```
   */
  public constructor(
    shaders: SkSpan<SkSp<PrecompileShader>>,
    inputSpaces: SkSpan<SkSp<SkColorSpace>>,
    outputSpaces: SkSpan<SkSp<SkColorSpace>>,
  ) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumShaderCombos * fColorSpaces.size(); }
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
   *         int desiredColorSpaceCombination = desiredCombination / fNumShaderCombos;
   *         SkASSERT(desiredColorSpaceCombination < (int) fColorSpaces.size());
   *
   *         // Check for an identity working colorspace (that is detected up front with
   *         // makeWithColorSpace, but due to return type mismatches, can't be handled as easily with
   *         // the WorkingColorSpace() factory).
   *         if (!fColorSpaces[desiredColorSpaceCombination].first &&
   *             !fColorSpaces[desiredColorSpaceCombination].second) {
   *             // So just add the desired shader direction
   *             AddToKey<PrecompileShader>(keyContext, fShaders, desiredShaderCombination);
   *             return;
   *         }
   *
   *         const SkColorInfo& dstInfo = keyContext.dstColorInfo();
   *         const SkAlphaType dstAT = dstInfo.alphaType();
   *         sk_sp<SkColorSpace> dstCS = dstInfo.refColorSpace();
   *         if (!dstCS) {
   *             dstCS = SkColorSpace::MakeSRGB();
   *         }
   *
   *         sk_sp<SkColorSpace> inputCS = fColorSpaces[desiredColorSpaceCombination].first;
   *         if (!inputCS) {
   *             inputCS = dstCS;
   *         }
   *         sk_sp<SkColorSpace> outputCS = fColorSpaces[desiredColorSpaceCombination].second;
   *         if (!outputCS) {
   *             outputCS = inputCS;
   *         }
   *
   *         // SkWorkingColorSpaceShader's workInUnpremul is not exposed yet in the public API so
   *         // precompile can assume that it'll always use dstAT.
   *         const SkAlphaType workingAT = dstAT;
   *         KeyContext workingContext =
   *                 keyContext.withColorInfo({dstInfo.colorType(), workingAT, inputCS});
   *
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     AddToKey<PrecompileShader>(workingContext, fShaders, desiredShaderCombination);
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     ColorSpaceTransformBlock::ColorSpaceTransformData data(
   *                             outputCS.get(), workingAT, dstCS.get(), dstAT);
   *                     ColorSpaceTransformBlock::AddBlock(keyContext, data);
   *                 });
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }

  /**
   * C++ original:
   * ```cpp
   * void updateNumShaderCombos() {
   *         fNumShaderCombos = 0;
   *         for (const auto& s : fShaders) {
   *             fNumShaderCombos += s->priv().numCombinations();
   *         }
   *     }
   * ```
   */
  private fun updateNumShaderCombos() {
    TODO("Implement updateNumShaderCombos")
  }
}
