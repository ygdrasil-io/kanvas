package org.skia.effects

import kotlin.Float
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
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkMagnifierImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkMagnifierImageFilter(const SkRect& lensBounds,
 *                            float zoomAmount,
 *                            float inset,
 *                            const SkSamplingOptions& sampling,
 *                            sk_sp<SkImageFilter> input)
 *         : SkImageFilter_Base(&input, 1)
 *         , fLensBounds(lensBounds)
 *         , fZoomAmount(zoomAmount)
 *         , fInset(inset)
 *         , fSampling(sampling) {}
 *
 *     SkRect computeFastBounds(const SkRect&) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterMagnifierImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkMagnifierImageFilter)
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
 *     skif::ParameterSpace<SkRect> fLensBounds;
 *     // Zoom is relative so does not belong to a coordinate space, see note in onFilterImage().
 *     float fZoomAmount;
 *     // Inset is really a ParameterSpace<SkSize> where width = height = fInset, but we store just the
 *     // float here for easier serialization and convert to a size in onFilterImage().
 *     float fInset;
 *     SkSamplingOptions fSampling;
 * }
 * ```
 */
public class SkMagnifierImageFilter public constructor(
  lensBounds: SkRect,
  zoomAmount: Float,
  inset: Float,
  sampling: SkSamplingOptions,
  input: SkSp<SkImageFilter>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkRect> fLensBounds
   * ```
   */
  private var fLensBounds: ParameterSpace<SkRect> = TODO("Initialize fLensBounds")

  /**
   * C++ original:
   * ```cpp
   * float fZoomAmount
   * ```
   */
  private var fZoomAmount: Float = TODO("Initialize fZoomAmount")

  /**
   * C++ original:
   * ```cpp
   * float fInset
   * ```
   */
  private var fInset: Float = TODO("Initialize fInset")

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
   * SkRect SkMagnifierImageFilter::computeFastBounds(const SkRect& src) const {
   *     SkRect bounds = this->getInput(0) ? this->getInput(0)->computeFastBounds(src) : src;
   *     if (bounds.intersect(SkRect(fLensBounds))) {
   *         return bounds;
   *     } else {
   *         return SkRect::MakeEmpty();
   *     }
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMagnifierImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeRect(SkRect(fLensBounds));
   *     buffer.writeScalar(fZoomAmount);
   *     buffer.writeScalar(fInset);
   *     buffer.writeSampling(fSampling);
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkMagnifierImageFilter::onFilterImage(const skif::Context& context) const {
   *     // These represent the full lens bounds and the ideal zoom center if everything is visible.
   *     skif::LayerSpace<SkRect> lensBounds = context.mapping().paramToLayer(fLensBounds);
   *     skif::LayerSpace<SkPoint> zoomCenter = lensBounds.center();
   *
   *     // When magnifying near the edge of the screen, it's common for part of the lens bounds to be
   *     // offscreen, which also means its input filter cannot provide the full required input.
   *     // The magnifier's auto-sizing's goal is to cover the visible portion of the lens bounds.
   *     skif::LayerSpace<SkRect> visibleLensBounds = lensBounds;
   *     if (!visibleLensBounds.intersect(skif::LayerSpace<SkRect>(context.desiredOutput()))) {
   *         return {};
   *     }
   *
   *     // We pre-emptively fit the zoomed-in src rect to what we expect the child input filter to
   *     // produce. This should be correct in all cases except for failure to create an offscreen image,
   *     // at which point there's nothing to be done anyway.
   *     skif::LayerSpace<SkRect> expectedChildOutput = lensBounds;
   *     if (std::optional<skif::LayerSpace<SkIRect>> output =
   *             this->getChildOutputLayerBounds(0, context.mapping(), context.source().layerBounds())) {
   *         expectedChildOutput = skif::LayerSpace<SkRect>(*output);
   *     }
   *
   *     // Clamp the zoom center to be within the childOutput image
   *     zoomCenter = expectedChildOutput.clamp(zoomCenter);
   *
   *     // The zoom we want to apply in layer-space is equal to
   *     // mapping.paramToLayer(SkMatrix::Scale(fZoomAmount)).decomposeScale(&layerZoom).
   *     // Because this filter only supports scale+translate matrices, the paramToLayer transform of
   *     // the parameter-space scale matrix is a no-op. Thus layerZoom == fZoomAmount and we can avoid
   *     // all of that math. This assumption is invalid if the matrix complexity is more than S+T.
   *     SkASSERT(this->getCTMCapability() == MatrixCapability::kScaleTranslate);
   *     // But also clamp the maximum amount of zoom to scale half of a layer pixel to the entire lens.
   *     const float maxLensSize = std::max(1.f, std::max(lensBounds.width(), lensBounds.height()));
   *     const float invZoom = 1.f /  std::min(fZoomAmount, 2.f * maxLensSize);
   *
   *     // The srcRect is the bounding box of the pixels that are linearly scaled up, about zoomCenter.
   *     // This is not the visual bounds of this upscaled region, but the bounds of the source pixels
   *     // that will fill the main magnified region (which is simply the inset of lensBounds). When
   *     // lensBounds has not been cropped by the actual input image, these equations are identical to
   *     // the more intuitive L/R = center.x -/+ width/(2*zoom) and T/B = center.y -/+ height/(2*zoom).
   *     // However, when lensBounds is cropped this automatically shifts the source rectangle away from
   *     // the original zoom center such that the upscaled area is contained within the input image.
   *     skif::LayerSpace<SkRect> srcRect{{
   *             lensBounds.left()  * invZoom + zoomCenter.x()*(1.f - invZoom),
   *             lensBounds.top()   * invZoom + zoomCenter.y()*(1.f - invZoom),
   *             lensBounds.right() * invZoom + zoomCenter.x()*(1.f - invZoom),
   *             lensBounds.bottom()* invZoom + zoomCenter.y()*(1.f - invZoom)}};
   *
   *     // The above adjustment helps to account for offscreen, but when the magnifier is combined with
   *     // backdrop offsets, more significant fitting needs to be performed to pin the visible src
   *     // rect to what's available.
   *     auto zoomXform = skif::LayerSpace<SkMatrix>::RectToRect(lensBounds, srcRect);
   *     if (!expectedChildOutput.contains(visibleLensBounds)) {
   *         // We need to pick a new srcRect such that srcRect is contained within fitRect and fills
   *         // visibleLens, while maintaining the aspect ratio of the original srcRect -> lensBounds.
   *         srcRect = zoomXform.mapRect(visibleLensBounds);
   *
   *         if (expectedChildOutput.width() >= srcRect.width() &&
   *             expectedChildOutput.height() >= srcRect.height()) {
   *             float left = srcRect.left() < expectedChildOutput.left() ?
   *                     expectedChildOutput.left() :
   *                     std::min(srcRect.right(), expectedChildOutput.right()) - srcRect.width();
   *             float top = srcRect.top() < expectedChildOutput.top() ?
   *                     expectedChildOutput.top() :
   *                     std::min(srcRect.bottom(), expectedChildOutput.bottom()) - srcRect.height();
   *
   *             // Update transform to reflect fitted src
   *             srcRect = skif::LayerSpace<SkRect>(
   *                     SkRect::MakeXYWH(left, top, srcRect.width(), srcRect.height()));
   *             zoomXform = skif::LayerSpace<SkMatrix>::RectToRect(visibleLensBounds, srcRect);
   *         } // Else not enough of the target is available to cover, so don't try adjusting
   *     }
   *
   *     // When there is no SkSL support, or there's a 0 inset, the magnifier is equivalent to a
   *     // rect->rect transform and crop.
   *     skif::LayerSpace<SkSize> inset = context.mapping().paramToLayer(
   *             skif::ParameterSpace<SkSize>({fInset, fInset}));
   *     if (inset.width() <= 0.f || inset.height() <= 0.f)
   *     {
   *         // When applying the zoom as a direct transform, we only require the visibleSrcRect as
   *         // input from the child filter, and transform it by the inverse of zoomXform (to go from
   *         // src to lens bounds, since it was constructed to go from lens to src).
   *         skif::LayerSpace<SkMatrix> invZoomXform;
   *         if (!zoomXform.invert(&invZoomXform)) {
   *             return {}; // pathological input
   *         }
   *         skif::FilterResult childOutput =
   *                 this->getChildOutput(0, context.withNewDesiredOutput(srcRect.roundOut()));
   *         return childOutput.applyTransform(context, invZoomXform, fSampling)
   *                           .applyCrop(context, lensBounds.roundOut());
   *     }
   *
   *     using ShaderFlags = skif::FilterResult::ShaderFlags;
   *     skif::FilterResult::Builder builder{context};
   *     builder.add(this->getChildOutput(0, context.withNewDesiredOutput(visibleLensBounds.roundOut())),
   *                 {}, ShaderFlags::kNonTrivialSampling, fSampling);
   *     return builder.eval([&](SkSpan<sk_sp<SkShader>> inputs) {
   *             // If the input resolved to a null shader, the magnified output will be transparent too
   *             return inputs[0] ? make_magnifier_shader(inputs[0], lensBounds, zoomXform, inset)
   *                              : nullptr;
   *         }, lensBounds.roundOut());
   * }
   * ```
   */
  public override fun onFilterImage(context: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMagnifierImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The required input is always the lens bounds. The filter distorts the pixels contained within
   *     // these bounds to zoom in on a portion of it, depending on the inset and zoom amount. However,
   *     // it adjusts the region based on cropping that occurs between what's requested and what's
   *     // provided. Theoretically it's possible that we could restrict the required input by the
   *     // desired output, but that cropping should not adjust the zoom region or inset. This is non
   *     // trivial to separate and is an unlikely use case so for now just require fLensBounds.
   *     skif::LayerSpace<SkIRect> requiredInput = mapping.paramToLayer(fLensBounds).roundOut();
   *     // Our required input is the desired output for our child image filter.
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
   * std::optional<skif::LayerSpace<SkIRect>> SkMagnifierImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The output of this filter is fLensBounds intersected with its child's output.
   *     auto output = this->getChildOutputLayerBounds(0, mapping, contentBounds);
   *     skif::LayerSpace<SkIRect> lensBounds = mapping.paramToLayer(fLensBounds).roundOut();
   *     if (!output || lensBounds.intersect(*output)) {
   *         return lensBounds;
   *     } else {
   *         // Nothing to magnify
   *         return skif::LayerSpace<SkIRect>::Empty();
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
   * sk_sp<SkFlattenable> SkMagnifierImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     if (buffer.isVersionLT(SkPicturePriv::kRevampMagnifierFilter)) {
   *         // This was actually a legacy magnifier image filter that was serialized. Chrome is the
   *         // only known client of the magnifier and its not used on webpages, so there shouldn't be
   *         // SKPs that actually contain a flattened magnifier filter (legacy or new).
   *         return nullptr;
   *     }
   *
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *
   *     SkRect lensBounds;
   *     buffer.readRect(&lensBounds);
   *     SkScalar zoomAmount = buffer.readScalar();
   *     SkScalar inset = buffer.readScalar();
   *     SkSamplingOptions sampling = buffer.readSampling();
   *     return SkImageFilters::Magnifier(lensBounds, zoomAmount, inset, sampling, common.getInput(0));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
