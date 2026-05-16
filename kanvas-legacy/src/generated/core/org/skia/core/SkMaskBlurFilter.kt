package org.skia.core

import kotlin.Boolean
import kotlin.Double
import org.skia.foundation.SkMask
import org.skia.foundation.SkMaskBuilder
import org.skia.math.SkIPoint

/**
 * C++ original:
 * ```cpp
 * class SkMaskBlurFilter {
 * public:
 *     // Create an object suitable for filtering an SkMask using a filter with width sigmaW and
 *     // height sigmaH.
 *     SkMaskBlurFilter(double sigmaW, double sigmaH);
 *
 *     // returns true iff the sigmas will result in an identity mask (no blurring)
 *     bool hasNoBlur() const;
 *
 *     // Given a src SkMask, generate dst SkMask returning the border width and height.
 *     SkIPoint blur(const SkMask& src, SkMaskBuilder* dst) const;
 *
 * private:
 *     const double fSigmaW;
 *     const double fSigmaH;
 * }
 * ```
 */
public data class SkMaskBlurFilter public constructor(
  /**
   * C++ original:
   * ```cpp
   * const double fSigmaW
   * ```
   */
  private val fSigmaW: Double,
  /**
   * C++ original:
   * ```cpp
   * const double fSigmaH
   * ```
   */
  private val fSigmaH: Double,
) {
  /**
   * C++ original:
   * ```cpp
   * bool SkMaskBlurFilter::hasNoBlur() const {
   *     // If the sigma value is less than a certain amount, the window will be 0 which means
   *     // there is effectively no blur. Using Wolfram alpha to solve the equation used for
   *     // possibleWindow above shows that the threshold is (2 * sqrt(2/pi))/3
   * #if defined(SK_USE_LARGER_NO_BLUR_THRESHOLD)
   *     constexpr double kNoWindowSigma = 0.531923;
   * #else
   *     // However, historically we used 1/3 as the cutoff. Clients who might have pixel tests
   *     // that depend on this can be updated one at a time.
   *     constexpr double kNoWindowSigma = 1./3.;
   * #endif
   *     return fSigmaW < kNoWindowSigma && fSigmaH <= kNoWindowSigma;
   * }
   * ```
   */
  public fun hasNoBlur(): Boolean {
    TODO("Implement hasNoBlur")
  }

  /**
   * C++ original:
   * ```cpp
   * SkIPoint SkMaskBlurFilter::blur(const SkMask& src, SkMaskBuilder* dst) const {
   *
   *     if (fSigmaW < 2.0 && fSigmaH < 2.0) {
   *         return small_blur(fSigmaW, fSigmaH, src, dst);
   *     }
   *
   *     // 1024 is a place holder guess until more analysis can be done.
   *     SkSTArenaAlloc<1024> alloc;
   *
   *     PlanGauss planW(fSigmaW);
   *     PlanGauss planH(fSigmaH);
   *
   *     int borderW = planW.border(),
   *         borderH = planH.border();
   *     SkASSERT(borderH >= 0 && borderW >= 0);
   *
   *     *dst = SkMaskBuilder::PrepareDestination(borderW, borderH, src);
   *     if (src.fImage == nullptr) {
   *         return {SkTo<int32_t>(borderW), SkTo<int32_t>(borderH)};
   *     }
   *     if (dst->fImage == nullptr) {
   *         dst->bounds().setEmpty();
   *         return {0, 0};
   *     }
   *
   *     int srcW = src.fBounds.width(),
   *         srcH = src.fBounds.height(),
   *         dstW = dst->fBounds.width(),
   *         dstH = dst->fBounds.height();
   *     SkASSERT(srcW >= 0 && srcH >= 0 && dstW >= 0 && dstH >= 0);
   *
   *     auto bufferSize = std::max(planW.bufferSize(), planH.bufferSize());
   *     auto buffer = alloc.makeArrayDefault<uint32_t>(bufferSize);
   *
   *     // Blur both directions.
   *     int tmpW = srcH,
   *         tmpH = dstW;
   *
   *     // Make sure not to overflow the multiply for the tmp buffer size.
   *     if (tmpH > std::numeric_limits<int>::max() / tmpW) {
   *         return {0, 0};
   *     }
   *     auto tmp = alloc.makeArrayDefault<uint8_t>(tmpW * tmpH);
   *
   *     // Blur horizontally, and transpose.
   *     const PlanGauss::Scan& scanW = planW.makeBlurScan(srcW, buffer);
   *     switch (src.fFormat) {
   *         case SkMask::kBW_Format: {
   *             const uint8_t* bwStart = src.fImage;
   *             auto start = SkMask::AlphaIter<SkMask::kBW_Format>(bwStart, 0);
   *             auto end = SkMask::AlphaIter<SkMask::kBW_Format>(bwStart + (srcW / 8), srcW % 8);
   *             for (int y = 0; y < srcH; ++y, start >>= src.fRowBytes, end >>= src.fRowBytes) {
   *                 auto tmpStart = &tmp[y];
   *                 scanW.blur(start, end, tmpStart, tmpW, tmpStart + tmpW * tmpH);
   *             }
   *         } break;
   *         case SkMask::kA8_Format: {
   *             const uint8_t* a8Start = src.fImage;
   *             auto start = SkMask::AlphaIter<SkMask::kA8_Format>(a8Start);
   *             auto end = SkMask::AlphaIter<SkMask::kA8_Format>(a8Start + srcW);
   *             for (int y = 0; y < srcH; ++y, start >>= src.fRowBytes, end >>= src.fRowBytes) {
   *                 auto tmpStart = &tmp[y];
   *                 scanW.blur(start, end, tmpStart, tmpW, tmpStart + tmpW * tmpH);
   *             }
   *         } break;
   *         case SkMask::kARGB32_Format: {
   *             const uint32_t* argbStart = reinterpret_cast<const uint32_t*>(src.fImage);
   *             auto start = SkMask::AlphaIter<SkMask::kARGB32_Format>(argbStart);
   *             auto end = SkMask::AlphaIter<SkMask::kARGB32_Format>(argbStart + srcW);
   *             for (int y = 0; y < srcH; ++y, start >>= src.fRowBytes, end >>= src.fRowBytes) {
   *                 auto tmpStart = &tmp[y];
   *                 scanW.blur(start, end, tmpStart, tmpW, tmpStart + tmpW * tmpH);
   *             }
   *         } break;
   *         case SkMask::kLCD16_Format: {
   *             const uint16_t* lcdStart = reinterpret_cast<const uint16_t*>(src.fImage);
   *             auto start = SkMask::AlphaIter<SkMask::kLCD16_Format>(lcdStart);
   *             auto end = SkMask::AlphaIter<SkMask::kLCD16_Format>(lcdStart + srcW);
   *             for (int y = 0; y < srcH; ++y, start >>= src.fRowBytes, end >>= src.fRowBytes) {
   *                 auto tmpStart = &tmp[y];
   *                 scanW.blur(start, end, tmpStart, tmpW, tmpStart + tmpW * tmpH);
   *             }
   *         } break;
   *         default:
   *             SK_ABORT("Unhandled format.");
   *     }
   *
   *     // Blur vertically (scan in memory order because of the transposition),
   *     // and transpose back to the original orientation.
   *     const PlanGauss::Scan& scanH = planH.makeBlurScan(tmpW, buffer);
   *     for (int y = 0; y < tmpH; y++) {
   *         auto tmpStart = &tmp[y * tmpW];
   *         auto dstStart = &dst->image()[y];
   *
   *         scanH.blur(tmpStart, tmpStart + tmpW,
   *                    dstStart, dst->fRowBytes, dstStart + dst->fRowBytes * dstH);
   *     }
   *
   *     return {SkTo<int32_t>(borderW), SkTo<int32_t>(borderH)};
   * }
   * ```
   */
  public fun blur(src: SkMask, dst: SkMaskBuilder?): SkIPoint {
    TODO("Implement blur")
  }
}
