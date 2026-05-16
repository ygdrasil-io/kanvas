package org.skia.effects

import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.ParameterSpace
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class SkBlurImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkBlurImageFilter(SkSize sigma, sk_sp<SkImageFilter> input)
 *             : SkImageFilter_Base(&input, 1)
 *             , fSigma{sigma} {}
 *
 *     SkBlurImageFilter(SkSize sigma, SkTileMode legacyTileMode, sk_sp<SkImageFilter> input)
 *             : SkImageFilter_Base(&input, 1)
 *             , fSigma(sigma)
 *             , fLegacyTileMode(legacyTileMode) {}
 *
 *     SkRect computeFastBounds(const SkRect&) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterBlurImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkBlurImageFilter)
 *
 *     skif::FilterResult onFilterImage(const skif::Context& context) const override;
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
 *     skif::LayerSpace<SkSize> mapSigma(const skif::Mapping& mapping) const;
 *
 *     skif::LayerSpace<SkIRect> kernelBounds(const skif::Mapping& mapping,
 *                                            skif::LayerSpace<SkIRect> bounds) const {
 *         skif::LayerSpace<SkSize> sigma = this->mapSigma(mapping);
 *         bounds.outset(skif::LayerSpace<SkSize>({3 * sigma.width(), 3 * sigma.height()}).ceil());
 *         return bounds;
 *     }
 *
 *     skif::ParameterSpace<SkSize> fSigma;
 *     // kDecal means no legacy tiling, it will be handled by SkCropImageFilter instead. Legacy
 *     // tiling occurs when there's no provided crop rect, and should be deleted once clients create
 *     // their filters with defined tiling geometry.
 *     SkTileMode fLegacyTileMode = SkTileMode::kDecal;
 * }
 * ```
 */
public class SkBlurImageFilter public constructor(
  sigma: SkSize,
  input: SkSp<SkImageFilter>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkSize> fSigma
   * ```
   */
  private var fSigma: ParameterSpace<SkSize> = TODO("Initialize fSigma")

  /**
   * C++ original:
   * ```cpp
   * SkTileMode fLegacyTileMode = SkTileMode::kDecal
   * ```
   */
  private var fLegacyTileMode: SkTileMode = TODO("Initialize fLegacyTileMode")

  /**
   * C++ original:
   * ```cpp
   * SkBlurImageFilter(SkSize sigma, sk_sp<SkImageFilter> input)
   *             : SkImageFilter_Base(&input, 1)
   *             , fSigma{sigma} {}
   * ```
   */
  public constructor(
    sigma: SkSize,
    legacyTileMode: SkTileMode,
    input: SkSp<SkImageFilter>,
  ) : this(TODO(), TODO()) {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkBlurImageFilter::computeFastBounds(const SkRect& src) const {
   *     SkRect bounds = this->getInput(0) ? this->getInput(0)->computeFastBounds(src) : src;
   *     bounds.outset(SkSize(fSigma).width() * 3, SkSize(fSigma).height() * 3);
   *     return bounds;
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkBlurImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *
   *     buffer.writeScalar(SkSize(fSigma).fWidth);
   *     buffer.writeScalar(SkSize(fSigma).fHeight);
   *     buffer.writeInt(static_cast<int>(fLegacyTileMode));
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkBlurImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     skif::Context inputCtx = ctx.withNewDesiredOutput(
   *             this->kernelBounds(ctx.mapping(), ctx.desiredOutput()));
   *
   *     skif::FilterResult childOutput = this->getChildOutput(0, inputCtx);
   *     skif::LayerSpace<SkSize> sigma = this->mapSigma(ctx.mapping());
   *     if (sigma.width() == 0.f && sigma.height() == 0.f) {
   *         // No actual blur, so just return the input unmodified
   *         return childOutput;
   *     }
   *
   *     SkASSERT(sigma.width() >= 0.f && sigma.width() <= kMaxSigma &&
   *              sigma.height() >= 0.f && sigma.height() <= kMaxSigma);
   *
   *     // By default, FilterResult::blur() will calculate a more optimal output automatically, so
   *     // convey the original output to it.
   *     skif::LayerSpace<SkIRect> maxOutput = ctx.desiredOutput();
   *     if (fLegacyTileMode != SkTileMode::kDecal) {
   *         // Legacy tiling output is also dependent on the original child output bounds ignoring
   *         // the tile mode's effect.
   *         maxOutput = this->kernelBounds(ctx.mapping(), childOutput.layerBounds());
   *         if (!maxOutput.intersect(ctx.desiredOutput())) {
   *             return {};
   *         }
   *     }
   *     if (fLegacyTileMode != SkTileMode::kDecal) {
   *         // Legacy tiling applied to the input image when there was no explicit crop rect. Use the
   *         // child's output image's layer bounds as the crop rectangle to adjust the edge tile mode
   *         // without restricting the image.
   *         childOutput = childOutput.applyCrop(inputCtx,
   *                                             childOutput.layerBounds(),
   *                                             fLegacyTileMode);
   *     }
   *
   *     // For non-legacy tiling, 'maxOutput' is equal to the desired output. For decal's it matches
   *     // what Builder::blur() calculates internally. For legacy tiling, however, it's dependent on
   *     // the original child output's bounds ignoring the tile mode's effect.
   *     skif::Context croppedOutput = ctx.withNewDesiredOutput(maxOutput);
   *     skif::FilterResult::Builder builder{croppedOutput};
   *     builder.add(childOutput);
   *     return builder.blur(sigma);
   * }
   * ```
   */
  public override fun onFilterImage(context: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkBlurImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     skif::LayerSpace<SkIRect> requiredInput =
   *             this->kernelBounds(mapping, desiredOutput);
   *     return this->getChildInputLayerBounds(0, mapping, requiredInput, contentBounds);
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
   * std::optional<skif::LayerSpace<SkIRect>> SkBlurImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     auto childOutput = this->getChildOutputLayerBounds(0, mapping, contentBounds);
   *     if (childOutput) {
   *         return this->kernelBounds(mapping, *childOutput);
   *     } else {
   *         return skif::LayerSpace<SkIRect>::Unbounded();
   *     }
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkSize> SkBlurImageFilter::mapSigma(const skif::Mapping& mapping) const {
   *     skif::LayerSpace<SkSize> sigma = mapping.paramToLayer(fSigma);
   *     // Clamp to the maximum sigma
   *     sigma = skif::LayerSpace<SkSize>({std::min(sigma.width(), kMaxSigma),
   *                                       std::min(sigma.height(), kMaxSigma)});
   *
   *     // Disable bluring on axes that are not finite, or that are small enough that the blur is
   *     // effectively an identity.
   *     if (!SkIsFinite(sigma.width()) || SkBlurEngine::IsEffectivelyIdentity(sigma.width())) {
   *         sigma = skif::LayerSpace<SkSize>({0.f, sigma.height()});
   *     }
   *
   *     if (!SkIsFinite(sigma.height()) || SkBlurEngine::IsEffectivelyIdentity(sigma.height())) {
   *         sigma = skif::LayerSpace<SkSize>({sigma.width(), 0.f});
   *     }
   *
   *     return sigma;
   * }
   * ```
   */
  private fun mapSigma(mapping: Mapping): LayerSpace<SkSize> {
    TODO("Implement mapSigma")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> kernelBounds(const skif::Mapping& mapping,
   *                                            skif::LayerSpace<SkIRect> bounds) const {
   *         skif::LayerSpace<SkSize> sigma = this->mapSigma(mapping);
   *         bounds.outset(skif::LayerSpace<SkSize>({3 * sigma.width(), 3 * sigma.height()}).ceil());
   *         return bounds;
   *     }
   * ```
   */
  private fun kernelBounds(mapping: Mapping, bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement kernelBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkBlurImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *     SkScalar sigmaX = buffer.readScalar();
   *     SkScalar sigmaY = buffer.readScalar();
   *     SkTileMode tileMode = buffer.read32LE(SkTileMode::kLastTileMode);
   *
   *     // NOTE: For new SKPs, 'tileMode' holds the "legacy" tile mode; any originally specified tile
   *     // mode with valid tiling geometry is handled in the SkCropImageFilters that wrap the blur.
   *     // In a new SKP, when 'tileMode' is not kDecal, common.cropRect() will be null and the blur
   *     // will automatically emulate the legacy tiling.
   *     //
   *     // In old SKPs, the 'tileMode' and common.cropRect() may not be null. ::Blur() automatically
   *     // detects when this is a legacy or valid tiling and constructs the DAG appropriately.
   *     return SkImageFilters::Blur(
   *           sigmaX, sigmaY, tileMode, common.getInput(0), common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
