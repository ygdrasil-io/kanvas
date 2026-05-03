package org.skia.effects

import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.ParameterSpace
import org.skia.core.SkImageFilterBase
import org.skia.core.SkPicture
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkPictureImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkPictureImageFilter(sk_sp<SkPicture> picture, const SkRect& cullRect)
 *             : SkImageFilter_Base(nullptr, 0)
 *             , fPicture(std::move(picture))
 *             , fCullRect(cullRect) {
 *         // The external cullrect should already have been intersected with the internal cull rect
 *         SkASSERT(fPicture && fPicture->cullRect().contains(cullRect));
 *     }
 *
 *     SkRect computeFastBounds(const SkRect&) const override { return SkRect(fCullRect); }
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterPictureImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkPictureImageFilter)
 *
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
 *
 *     skif::FilterResult onFilterImage(const skif::Context& ctx) const override;
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
 *     sk_sp<SkPicture> fPicture;
 *     skif::ParameterSpace<SkRect> fCullRect;
 * }
 * ```
 */
public class SkPictureImageFilter public constructor(
  picture: SkSp<SkPicture>,
  cullRect: SkRect,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkPicture> fPicture
   * ```
   */
  private var fPicture: SkSp<SkPicture> = TODO("Initialize fPicture")

  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkRect> fCullRect
   * ```
   */
  private var fCullRect: ParameterSpace<SkRect> = TODO("Initialize fCullRect")

  /**
   * C++ original:
   * ```cpp
   * SkRect computeFastBounds(const SkRect&) const override { return SkRect(fCullRect); }
   * ```
   */
  public override fun computeFastBounds(param0: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkPictureImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     buffer.writeBool(SkToBool(fPicture));
   *     if (fPicture) {
   *         SkPicturePriv::Flatten(fPicture, buffer);
   *     }
   *     buffer.writeRect(SkRect(fCullRect));
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
   * skif::FilterResult SkPictureImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     return skif::FilterResult::MakeFromPicture(ctx, fPicture, fCullRect);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkPictureImageFilter::onGetInputLayerBounds(
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
   * std::optional<skif::LayerSpace<SkIRect>> SkPictureImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>>) const {
   *     // The output is the transformed bounds of the picture.
   *     return mapping.paramToLayer(fCullRect).roundOut();
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkPictureImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     sk_sp<SkPicture> picture;
   *     if (buffer.readBool()) {
   *         picture = SkPicturePriv::MakeFromBuffer(buffer);
   *     }
   *
   *     SkRect cullRect;
   *     buffer.readRect(&cullRect);
   *     return SkImageFilters::Picture(std::move(picture), cullRect);
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
