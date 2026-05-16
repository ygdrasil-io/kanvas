package org.skia.effects

import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.ParameterSpace
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImage
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkImageImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkImageImageFilter(sk_sp<SkImage> image,
 *                        const SkRect& srcRect,
 *                        const SkRect& dstRect,
 *                        const SkSamplingOptions& sampling)
 *             : SkImageFilter_Base(nullptr, 0)
 *             , fImage(std::move(image))
 *             , fSrcRect(srcRect)
 *             , fDstRect(dstRect)
 *             , fSampling(sampling) {
 *         // The dst rect should be non-empty
 *         SkASSERT(fImage && !dstRect.isEmpty());
 *     }
 *
 *     SkRect computeFastBounds(const SkRect&) const override { return SkRect(fDstRect); }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterImageImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkImageImageFilter)
 *
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
 *
 *     skif::FilterResult onFilterImage(const skif::Context&) const override;
 *
 *     skif::LayerSpace<SkIRect> onGetInputLayerBounds(
 *             const skif::Mapping& mapping,
 *             const skif::LayerSpace<SkIRect>& desiredOutput,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     std::optional<skif::LayerSpace<SkIRect>> onGetOutputLayerBounds(
 *             const skif::Mapping& mapping,
 *             std::optional<skif::LayerSpace<SkIRect>> contentBounds) const override;
 *
 *     sk_sp<SkImage> fImage;
 *     // The src rect is relative to the image's contents, so is not technically in the parameter
 *     // coordinate space that responds to the layer matrix (unlike fDstRect).
 *     SkRect fSrcRect;
 *     skif::ParameterSpace<SkRect> fDstRect;
 *     SkSamplingOptions fSampling;
 * }
 * ```
 */
public class SkImageImageFilter public constructor(
  image: SkSp<SkImage>,
  srcRect: SkRect,
  dstRect: SkRect,
  sampling: SkSamplingOptions,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImage> fImage
   * ```
   */
  private var fImage: SkSp<SkImage> = TODO("Initialize fImage")

  /**
   * C++ original:
   * ```cpp
   * SkRect fSrcRect
   * ```
   */
  private var fSrcRect: SkRect = TODO("Initialize fSrcRect")

  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkRect> fDstRect
   * ```
   */
  private var fDstRect: ParameterSpace<SkRect> = TODO("Initialize fDstRect")

  /**
   * C++ original:
   * ```cpp
   * SkSamplingOptions fSampling
   * ```
   */
  private var fSampling: SkSamplingOptions = TODO("Initialize fSampling")

  /**
   * C++ original:
   * ```cpp
   * SkRect computeFastBounds(const SkRect&) const override { return SkRect(fDstRect); }
   * ```
   */
  public override fun computeFastBounds(param0: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkImageImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeSampling(fSampling);
   *     buffer.writeRect(fSrcRect);
   *     buffer.writeRect(SkRect(fDstRect));
   *     buffer.writeImage(fImage.get());
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
   * ```
   */
  public override fun onGetCTMCapability(): MatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkImageImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     return skif::FilterResult::MakeFromImage(ctx, fImage, fSrcRect, fDstRect, fSampling);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkImageImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping&,
   *         const skif::LayerSpace<SkIRect>&,
   *         std::optional<skif::LayerSpace<SkIRect>>) const {
   *     // This is a leaf filter, it requires no input and no further recursion
   *     return skif::LayerSpace<SkIRect>::Empty();
   * }
   * ```
   */
  public override fun onGetInputLayerBounds(
    mapping: Mapping,
    desiredOutput: LayerSpace<SkIRect>,
    contentBounds: LayerSpace<SkIRect>?,
  ): LayerSpace<SkIRect> {
    TODO("Implement onGetInputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<skif::LayerSpace<SkIRect>> SkImageImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>>) const {
   *     // The output is the transformed bounds of the image.
   *     return mapping.paramToLayer(fDstRect).roundOut();
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkImageImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SkSamplingOptions sampling;
   *     if (buffer.isVersionLT(SkPicturePriv::kImageFilterImageSampling_Version)) {
   *         sampling = SkSamplingPriv::FromFQ(buffer.checkFilterQuality(), kLinear_SkMediumAs);
   *     } else {
   *         sampling = buffer.readSampling();
   *     }
   *
   *     SkRect src, dst;
   *     buffer.readRect(&src);
   *     buffer.readRect(&dst);
   *
   *     sk_sp<SkImage> image(buffer.readImage());
   *     if (!image) {
   *         return nullptr;
   *     }
   *
   *     return SkImageFilters::Image(std::move(image), src, dst, sampling);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
