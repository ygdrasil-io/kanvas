package org.skia.core

import kotlin.Boolean
import kotlin.Float
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.math.SkIRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class RasterA8BlurAlgorithm : public SkBlurEngine::Algorithm {
 * public:
 *     // See analysis in description of GaussPass for the max supported sigma.
 *     float maxSigma() const override {
 *         static constexpr float kMaxSigma = 135.f;
 *         SkASSERT(SkBlurEngine::BoxBlurWindow(kMaxSigma) <= 255);
 *         return kMaxSigma;
 *     }
 *
 *     // TODO: Implement CPU backend for different fTileMode. This is still worth doing inline with
 *     // the blur; at the moment the tiling is applied via the CropImageFilter and carried as metadata
 *     // on the FilterResult. This is forcefully applied in FilterResult::Builder::blur() when
 *     // supportsOnlyDecalTiling() returns true.
 *     bool supportsOnlyDecalTiling() const override { return true; }
 *
 *     sk_sp<SkSpecialImage> blur(SkSize sigma,
 *                                sk_sp<SkSpecialImage> input,
 *                                const SkIRect& originalSrcBounds,
 *                                SkTileMode tileMode,
 *                                const SkIRect& originalDstBounds) const override {
 *         SkASSERT(tileMode == SkTileMode::kDecal);
 *         SkASSERT(SkIRect::MakeSize(input->dimensions()).contains(originalSrcBounds));
 *
 *         SkBitmap src;
 *         if (!SkSpecialImages::AsBitmap(input.get(), &src)) {
 *             return nullptr; // Should only have been called by CPU-backed images
 *         }
 *         // The blur engine should not have picked this algorithm for a non-8-bit color type.
 *         SkASSERT(src.colorType() == kAlpha_8_SkColorType);
 *
 *         // 1024 is a place holder guess until more analysis can be done.
 *         SkSTArenaAlloc<1024> alloc;
 *         auto makeMaker = [&](float sigma) -> PassMaker* {
 *             SkASSERT(0 <= sigma && sigma <= 135); // should be guaranteed after map_sigma
 *             if (PassMaker* maker = GaussianPass<uint8_t>::MakeMaker(sigma, &alloc)) {
 *                 return maker;
 *             }
 *             if (PassMaker* maker = A8Pass::MakeMaker(sigma, &alloc)) {
 *                 return maker;
 *             }
 *             SK_ABORT("Sigma is out of range.");
 *         };
 *
 *         PassMaker* makerX = makeMaker(sigma.width());
 *         PassMaker* makerY = makeMaker(sigma.height());
 *
 *         return eval_blur_passes<uint8_t>(makerX, makerY, src, originalSrcBounds,
 *                                          originalDstBounds, &alloc);
 *     }
 * }
 * ```
 */
public open class RasterA8BlurAlgorithm : Algorithm() {
  /**
   * C++ original:
   * ```cpp
   * float maxSigma() const override {
   *         static constexpr float kMaxSigma = 135.f;
   *         SkASSERT(SkBlurEngine::BoxBlurWindow(kMaxSigma) <= 255);
   *         return kMaxSigma;
   *     }
   * ```
   */
  public override fun maxSigma(): Float {
    TODO("Implement maxSigma")
  }

  /**
   * C++ original:
   * ```cpp
   * bool supportsOnlyDecalTiling() const override { return true; }
   * ```
   */
  public override fun supportsOnlyDecalTiling(): Boolean {
    TODO("Implement supportsOnlyDecalTiling")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkSpecialImage> blur(SkSize sigma,
   *                                sk_sp<SkSpecialImage> input,
   *                                const SkIRect& originalSrcBounds,
   *                                SkTileMode tileMode,
   *                                const SkIRect& originalDstBounds) const override {
   *         SkASSERT(tileMode == SkTileMode::kDecal);
   *         SkASSERT(SkIRect::MakeSize(input->dimensions()).contains(originalSrcBounds));
   *
   *         SkBitmap src;
   *         if (!SkSpecialImages::AsBitmap(input.get(), &src)) {
   *             return nullptr; // Should only have been called by CPU-backed images
   *         }
   *         // The blur engine should not have picked this algorithm for a non-8-bit color type.
   *         SkASSERT(src.colorType() == kAlpha_8_SkColorType);
   *
   *         // 1024 is a place holder guess until more analysis can be done.
   *         SkSTArenaAlloc<1024> alloc;
   *         auto makeMaker = [&](float sigma) -> PassMaker* {
   *             SkASSERT(0 <= sigma && sigma <= 135); // should be guaranteed after map_sigma
   *             if (PassMaker* maker = GaussianPass<uint8_t>::MakeMaker(sigma, &alloc)) {
   *                 return maker;
   *             }
   *             if (PassMaker* maker = A8Pass::MakeMaker(sigma, &alloc)) {
   *                 return maker;
   *             }
   *             SK_ABORT("Sigma is out of range.");
   *         };
   *
   *         PassMaker* makerX = makeMaker(sigma.width());
   *         PassMaker* makerY = makeMaker(sigma.height());
   *
   *         return eval_blur_passes<uint8_t>(makerX, makerY, src, originalSrcBounds,
   *                                          originalDstBounds, &alloc);
   *     }
   * ```
   */
  public override fun blur(
    sigma: SkSize,
    input: SkSp<SkSpecialImage>,
    originalSrcBounds: SkIRect,
    tileMode: SkTileMode,
    originalDstBounds: SkIRect,
  ): SkSp<SkSpecialImage> {
    TODO("Implement blur")
  }
}
