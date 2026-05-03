package org.skia.gpu

import kotlin.Int
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkSp
import org.skia.foundation.SkSpan

/**
 * C++ original:
 * ```cpp
 * class PrecompileColorSpaceXformColorFilter : public PrecompileColorFilter {
 * public:
 *     PrecompileColorSpaceXformColorFilter(SkSpan<const sk_sp<SkColorSpace>> src,
 *                                          SkSpan<const sk_sp<SkColorSpace>> dst)
 *             : fSrc(src.begin(), src.end())
 *             , fDst(dst.begin(), dst.end())
 *             , fNumCombinations(src.size() * dst.size()) {}
 *
 * private:
 *     int numIntrinsicCombinations() const override { return fNumCombinations; }
 *
 *     void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
 *         const int srcCombination = desiredCombination % fSrc.size();
 *         const int dstCombination = desiredCombination / fSrc.size();
 *         SkASSERT(dstCombination < static_cast<int>(fDst.size()));
 *
 *         // The alpha type is unused for determining which color space transform block to use.
 *         constexpr SkAlphaType kAlphaType = kPremul_SkAlphaType;
 *
 *         ColorSpaceTransformBlock::ColorSpaceTransformData csData =
 *                 ColorSpaceTransformBlock::ColorSpaceTransformData(
 *                         fSrc[srcCombination].get(), kAlphaType,
 *                         fDst[dstCombination].get(), kAlphaType);
 *
 *         ColorSpaceTransformBlock::AddBlock(keyContext, csData);
 *     }
 *
 *     std::vector<sk_sp<SkColorSpace>> fSrc;
 *     std::vector<sk_sp<SkColorSpace>> fDst;
 *
 *     const int fNumCombinations;
 * }
 * ```
 */
public open class PrecompileColorSpaceXformColorFilter public constructor(
  src: SkSpan<SkSp<SkColorSpace>>,
  dst: SkSpan<SkSp<SkColorSpace>>,
) : PrecompileColorFilter() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkColorSpace>> fSrc
   * ```
   */
  private var fSrc: Int = TODO("Initialize fSrc")

  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkColorSpace>> fDst
   * ```
   */
  private var fDst: Int = TODO("Initialize fDst")

  /**
   * C++ original:
   * ```cpp
   * const int fNumCombinations
   * ```
   */
  private val fNumCombinations: Int = TODO("Initialize fNumCombinations")

  /**
   * C++ original:
   * ```cpp
   * int numIntrinsicCombinations() const override { return fNumCombinations; }
   * ```
   */
  public override fun numIntrinsicCombinations(): Int {
    TODO("Implement numIntrinsicCombinations")
  }

  /**
   * C++ original:
   * ```cpp
   * void addToKey(const KeyContext& keyContext, int desiredCombination) const override {
   *         const int srcCombination = desiredCombination % fSrc.size();
   *         const int dstCombination = desiredCombination / fSrc.size();
   *         SkASSERT(dstCombination < static_cast<int>(fDst.size()));
   *
   *         // The alpha type is unused for determining which color space transform block to use.
   *         constexpr SkAlphaType kAlphaType = kPremul_SkAlphaType;
   *
   *         ColorSpaceTransformBlock::ColorSpaceTransformData csData =
   *                 ColorSpaceTransformBlock::ColorSpaceTransformData(
   *                         fSrc[srcCombination].get(), kAlphaType,
   *                         fDst[dstCombination].get(), kAlphaType);
   *
   *         ColorSpaceTransformBlock::AddBlock(keyContext, csData);
   *     }
   * ```
   */
  public override fun addToKey(keyContext: KeyContext, desiredCombination: Int) {
    TODO("Implement addToKey")
  }
}
