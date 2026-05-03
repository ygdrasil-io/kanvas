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
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class SkMorphologyImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkMorphologyImageFilter(MorphType type, SkSize radii, sk_sp<SkImageFilter> input)
 *             : SkImageFilter_Base(&input, 1)
 *             , fType(type)
 *             , fRadii(radii) {}
 *
 *     SkRect computeFastBounds(const SkRect& src) const override;
 *
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterMorphologyImageFilterFlattenables();
 *     SK_FLATTENABLE_HOOKS(SkMorphologyImageFilter)
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
 *     skif::LayerSpace<SkISize> radii(const skif::Mapping& mapping) const {
 *         skif::LayerSpace<SkISize> radii = mapping.paramToLayer(fRadii).round();
 *         SkASSERT(radii.width() >= 0 && radii.height() >= 0);
 *
 *         // We limit the radius to something small, to avoid slow draw calls: crbug.com/1123035
 *         static constexpr int kMaxRadii = 256;
 *         return skif::LayerSpace<SkISize>({std::min(radii.width(), kMaxRadii),
 *                                           std::min(radii.height(), kMaxRadii)});
 *     }
 *
 *     skif::LayerSpace<SkIRect> requiredInput(const skif::Mapping& mapping,
 *                                             skif::LayerSpace<SkIRect> bounds) const {
 *         // The input for a morphology filter is always the kernel outset, regardless of morph type.
 *         bounds.outset(this->radii(mapping));
 *         return bounds;
 *     }
 *
 *     skif::LayerSpace<SkIRect> kernelOutputBounds(const skif::Mapping& mapping,
 *                                                  skif::LayerSpace<SkIRect> bounds) const {
 *         skif::LayerSpace<SkISize> radii = this->radii(mapping);
 *         if (fType == MorphType::kDilate) {
 *             // Transparent pixels up to the kernel radius away will be overridden by kDilate's "max"
 *             // function and be set to the input's boundary pixel colors, thus expanding the output.
 *             bounds.outset(radii);
 *         } else {
 *             // Pixels closer than the kernel radius to the input image's edges are overridden by
 *             // kErode's "min" function and will be set to transparent black, contracting the output.
 *             bounds.inset(radii);
 *         }
 *         return bounds;
 *     }
 *
 *     MorphType fType;
 *     skif::ParameterSpace<SkSize> fRadii;
 * }
 * ```
 */
public class SkMorphologyImageFilter public constructor(
  type: MorphType,
  radii: SkSize,
  input: SkSp<SkImageFilter>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * MorphType fType
   * ```
   */
  private var fType: MorphType = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkSize> fRadii
   * ```
   */
  private var fRadii: ParameterSpace<SkSize> = TODO("Initialize fRadii")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkMorphologyImageFilter::computeFastBounds(const SkRect& src) const {
   *     // See kernelOutputBounds() for rationale
   *     SkRect bounds = this->getInput(0) ? this->getInput(0)->computeFastBounds(src) : src;
   *     if (fType == MorphType::kDilate) {
   *         bounds.outset(SkSize(fRadii).width(), SkSize(fRadii).height());
   *     } else {
   *         bounds.inset(SkSize(fRadii).width(), SkSize(fRadii).height());
   *     }
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
   * void SkMorphologyImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeScalar(SkSize(fRadii).width());
   *     buffer.writeScalar(SkSize(fRadii).height());
   *     buffer.writeInt(static_cast<int>(fType));
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkMorphologyImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     skif::LayerSpace<SkIRect> requiredInput =
   *             this->requiredInput(ctx.mapping(), ctx.desiredOutput());
   *     skif::FilterResult childOutput =
   *             this->getChildOutput(0, ctx.withNewDesiredOutput(requiredInput));
   *
   *     // If childOutput completely fulfilled requiredInput, maxOutput will match the context's
   *     // desired output, but if the output image is smaller, this will restrict the morphology output
   *     // to what is actual produceable.
   *     skif::LayerSpace<SkIRect> maxOutput =
   *         this->kernelOutputBounds(ctx.mapping(), childOutput.layerBounds());
   *     if (!maxOutput.intersect(ctx.desiredOutput())) {
   *         return {};
   *     }
   *
   *     // The X pass has to preserve the extra rows to later be consumed by the Y pass.
   *     skif::LayerSpace<SkISize> radii = this->radii(ctx.mapping());
   *     skif::LayerSpace<SkIRect> maxOutputX = maxOutput;
   *     maxOutputX.outset(skif::LayerSpace<SkISize>({0, radii.height()}));
   *     childOutput = morphology_pass(ctx.withNewDesiredOutput(maxOutputX), childOutput, fType,
   *                                   MorphDirection::kX, radii.width());
   *     childOutput = morphology_pass(ctx.withNewDesiredOutput(maxOutput), childOutput, fType,
   *                                   MorphDirection::kY, radii.height());
   *     return childOutput;
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMorphologyImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     skif::LayerSpace<SkIRect> requiredInput = this->requiredInput(mapping, desiredOutput);
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
   * std::optional<skif::LayerSpace<SkIRect>> SkMorphologyImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     auto childOutput = this->getChildOutputLayerBounds(0, mapping, contentBounds);
   *     if (childOutput) {
   *         return this->kernelOutputBounds(mapping, *childOutput);
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
   * skif::LayerSpace<SkISize> radii(const skif::Mapping& mapping) const {
   *         skif::LayerSpace<SkISize> radii = mapping.paramToLayer(fRadii).round();
   *         SkASSERT(radii.width() >= 0 && radii.height() >= 0);
   *
   *         // We limit the radius to something small, to avoid slow draw calls: crbug.com/1123035
   *         static constexpr int kMaxRadii = 256;
   *         return skif::LayerSpace<SkISize>({std::min(radii.width(), kMaxRadii),
   *                                           std::min(radii.height(), kMaxRadii)});
   *     }
   * ```
   */
  private fun radii(mapping: Mapping): LayerSpace<SkISize> {
    TODO("Implement radii")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> requiredInput(const skif::Mapping& mapping,
   *                                             skif::LayerSpace<SkIRect> bounds) const {
   *         // The input for a morphology filter is always the kernel outset, regardless of morph type.
   *         bounds.outset(this->radii(mapping));
   *         return bounds;
   *     }
   * ```
   */
  private fun requiredInput(mapping: Mapping, bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement requiredInput")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> kernelOutputBounds(const skif::Mapping& mapping,
   *                                                  skif::LayerSpace<SkIRect> bounds) const {
   *         skif::LayerSpace<SkISize> radii = this->radii(mapping);
   *         if (fType == MorphType::kDilate) {
   *             // Transparent pixels up to the kernel radius away will be overridden by kDilate's "max"
   *             // function and be set to the input's boundary pixel colors, thus expanding the output.
   *             bounds.outset(radii);
   *         } else {
   *             // Pixels closer than the kernel radius to the input image's edges are overridden by
   *             // kErode's "min" function and will be set to transparent black, contracting the output.
   *             bounds.inset(radii);
   *         }
   *         return bounds;
   *     }
   * ```
   */
  private fun kernelOutputBounds(mapping: Mapping, bounds: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement kernelOutputBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkMorphologyImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *
   *     SkScalar width = buffer.readScalar();
   *     SkScalar height = buffer.readScalar();
   *     MorphType filterType = buffer.read32LE(MorphType::kLastType);
   *
   *     if (filterType == MorphType::kDilate) {
   *         return SkImageFilters::Dilate(width, height, common.getInput(0), common.cropRect());
   *     } else if (filterType == MorphType::kErode) {
   *         return SkImageFilters::Erode(width, height, common.getInput(0), common.cropRect());
   *     } else {
   *         return nullptr;
   *     }
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
