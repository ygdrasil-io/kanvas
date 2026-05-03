package org.skia.gpu

import kotlin.Int
import org.skia.effects.SkWorkingFormatCalculator
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan
import org.skia.gpu.ganesh.SkAlphaType
import org.skia.skcms.SkcmsMatrix3x3
import org.skia.skcms.SkcmsTransferFunction

/**
 * C++ original:
 * ```cpp
 * class PrecompileWithWorkingFormatColorFilter : public PrecompileColorFilter {
 * public:
 *     PrecompileWithWorkingFormatColorFilter(SkSpan<const sk_sp<PrecompileColorFilter>> childOptions,
 *                                            const skcms_TransferFunction* tf,
 *                                            const skcms_Matrix3x3* gamut,
 *                                            const SkAlphaType* at)
 *             : fChildOptions(childOptions.begin(), childOptions.end())
 *             , fWorkingFormatCalculator(tf, gamut, at) {
 *         fNumChildCombos = 0;
 *         for (const auto& childOption : fChildOptions) {
 *             fNumChildCombos += childOption->priv().numCombinations();
 *         }
 *     }
 *
 * private:
 *     int numChildCombinations() const override { return fNumChildCombos; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         SkASSERT(desiredCombination < fNumChildCombos);
 *
 *         const SkColorInfo& dstInfo = keyContext.dstColorInfo();
 *         const SkAlphaType dstAT = dstInfo.alphaType();
 *         const sk_sp<SkColorSpace> dstCS = keyContext.dstColorInfo().colorSpace()
 *                                                   ? keyContext.dstColorInfo().refColorSpace()
 *                                                   : SkColorSpace::MakeSRGB();
 *         SkAlphaType workingAT;
 *         sk_sp<SkColorSpace> workingCS = fWorkingFormatCalculator.workingFormat(dstCS, &workingAT);
 *         KeyContext workingContext =
 *                 keyContext.withColorInfo({dstInfo.colorType(), workingAT, workingCS});
 *
 *         // Use two nested compose blocks to chain (dst->working), child, and (working->dst) together
 *         // while appearing as one block to the parent node.
 *         Compose(keyContext,
 *                 /* addInnerToKey= */ [&]() -> void {
 *                     // Inner compose
 *                     Compose(keyContext,
 *                             /* addInnerToKey= */ [&]() -> void {
 *                                 // Innermost (inner of inner compose)
 *                                 ColorSpaceTransformBlock::ColorSpaceTransformData data1(
 *                                         dstCS.get(), dstAT, workingCS.get(), workingAT);
 *                                 ColorSpaceTransformBlock::AddBlock(keyContext, data1);
 *                             },
 *                             /* addOuterToKey= */ [&]() -> void {
 *                                 // Middle (outer of inner compose)
 *                                 AddToKey<PrecompileColorFilter>(workingContext, fChildOptions,
 *                                                                 desiredCombination);
 *                             });
 *                 },
 *                 /* addOuterToKey= */ [&]() -> void {
 *                     // Outermost (outer of outer compose)
 *                     ColorSpaceTransformBlock::ColorSpaceTransformData data2(
 *                             workingCS.get(), workingAT, dstCS.get(), dstAT);
 *                     ColorSpaceTransformBlock::AddBlock(keyContext, data2);
 *                 });
 *     }
 *
 *     std::vector<sk_sp<PrecompileColorFilter>> fChildOptions;
 *
 *     int fNumChildCombos;
 *
 *     SkWorkingFormatCalculator fWorkingFormatCalculator;
 * }
 * ```
 */
public open class PrecompileWithWorkingFormatColorFilter public constructor(
  childOptions: SkSpan<SkSp<PrecompileColorFilter>>,
  tf: SkcmsTransferFunction?,
  gamut: SkcmsMatrix3x3?,
  at: SkAlphaType?,
) : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<PrecompileColorFilter>> fChildOptions
   * ```
   */
  private var fChildOptions: Int = TODO("Initialize fChildOptions")

  /**
   * C++ original:
   * ```cpp
   * int fNumChildCombos
   * ```
   */
  private var fNumChildCombos: Int = TODO("Initialize fNumChildCombos")

  /**
   * C++ original:
   * ```cpp
   * SkWorkingFormatCalculator fWorkingFormatCalculator
   * ```
   */
  private var fWorkingFormatCalculator: SkWorkingFormatCalculator =
      TODO("Initialize fWorkingFormatCalculator")

  /**
   * C++ original:
   * ```cpp
   * int numChildCombinations() const override { return fNumChildCombos; }
   * ```
   */
  public override fun numChildCombinations(): Int {
    TODO("Implement numChildCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         SkASSERT(desiredCombination < fNumChildCombos);
   *
   *         const SkColorInfo& dstInfo = keyContext.dstColorInfo();
   *         const SkAlphaType dstAT = dstInfo.alphaType();
   *         const sk_sp<SkColorSpace> dstCS = keyContext.dstColorInfo().colorSpace()
   *                                                   ? keyContext.dstColorInfo().refColorSpace()
   *                                                   : SkColorSpace::MakeSRGB();
   *         SkAlphaType workingAT;
   *         sk_sp<SkColorSpace> workingCS = fWorkingFormatCalculator.workingFormat(dstCS, &workingAT);
   *         KeyContext workingContext =
   *                 keyContext.withColorInfo({dstInfo.colorType(), workingAT, workingCS});
   *
   *         // Use two nested compose blocks to chain (dst->working), child, and (working->dst) together
   *         // while appearing as one block to the parent node.
   *         Compose(keyContext,
   *                 /* addInnerToKey= */ [&]() -> void {
   *                     // Inner compose
   *                     Compose(keyContext,
   *                             /* addInnerToKey= */ [&]() -> void {
   *                                 // Innermost (inner of inner compose)
   *                                 ColorSpaceTransformBlock::ColorSpaceTransformData data1(
   *                                         dstCS.get(), dstAT, workingCS.get(), workingAT);
   *                                 ColorSpaceTransformBlock::AddBlock(keyContext, data1);
   *                             },
   *                             /* addOuterToKey= */ [&]() -> void {
   *                                 // Middle (outer of inner compose)
   *                                 AddToKey<PrecompileColorFilter>(workingContext, fChildOptions,
   *                                                                 desiredCombination);
   *                             });
   *                 },
   *                 /* addOuterToKey= */ [&]() -> void {
   *                     // Outermost (outer of outer compose)
   *                     ColorSpaceTransformBlock::ColorSpaceTransformData data2(
   *                             workingCS.get(), workingAT, dstCS.get(), dstAT);
   *                     ColorSpaceTransformBlock::AddBlock(keyContext, data2);
   *                 });
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
