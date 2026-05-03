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
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkMatrixTransformImageFilter final : public SkImageFilter_Base {
 * public:
 *     // TODO(michaelludwig): Update this to use SkM44.
 *     SkMatrixTransformImageFilter(const SkMatrix& transform,
 *                                  const SkSamplingOptions& sampling,
 *                                  sk_sp<SkImageFilter> input)
 *             : SkImageFilter_Base(&input, 1)
 *             , fTransform(transform)
 *             , fSampling(sampling) {
 *         // Pre-cache so future calls to fTransform.getType() are threadsafe.
 *         (void) static_cast<const SkMatrix&>(fTransform).getType();
 *     }
 *
 *     SkRect computeFastBounds(const SkRect&) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterMatrixTransformImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkMatrixTransformImageFilter)
 *     static sk_sp<SkFlattenable> LegacyOffsetCreateProc(SkReadBuffer& buffer);
 *
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
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
 *     skif::LayerSpace<SkIRect> requiredInput(const skif::Mapping& mapping,
 *                                             const skif::LayerSpace<SkIRect>& desiredOutput) const;
 *
 *     skif::ParameterSpace<SkMatrix> fTransform;
 *     SkSamplingOptions fSampling;
 * }
 * ```
 */
public class SkMatrixTransformImageFilter public constructor(
  transform: SkMatrix,
  sampling: SkSamplingOptions,
  input: SkSp<SkImageFilter>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkMatrix> fTransform
   * ```
   */
  private var fTransform: ParameterSpace<SkMatrix> = TODO("Initialize fTransform")

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
   * SkRect SkMatrixTransformImageFilter::computeFastBounds(const SkRect& src) const {
   *     SkRect bounds = this->getInput(0) ? this->getInput(0)->computeFastBounds(src) : src;
   *     return static_cast<const SkMatrix&>(fTransform).mapRect(bounds);
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkMatrixTransformImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeMatrix(SkMatrix(fTransform));
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
   * MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
   * ```
   */
  public override fun onGetCTMCapability(): MatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkMatrixTransformImageFilter::onFilterImage(const skif::Context& context) const {
   *     skif::LayerSpace<SkIRect> requiredInput =
   *             this->requiredInput(context.mapping(), context.desiredOutput());
   *     skif::FilterResult childOutput =
   *             this->getChildOutput(0, context.withNewDesiredOutput(requiredInput));
   *
   *     skif::LayerSpace<SkMatrix> transform = context.mapping().paramToLayer(fTransform);
   *     return childOutput.applyTransform(context, transform, fSampling);
   * }
   * ```
   */
  public override fun onFilterImage(context: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMatrixTransformImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // Our required input is the desired output for our child image filter.
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
   * std::optional<skif::LayerSpace<SkIRect>> SkMatrixTransformImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The output of this filter is the transformed bounds of its child's output.
   *     auto childOutput = this->getChildOutputLayerBounds(0, mapping, contentBounds);
   *     if (childOutput) {
   *         return mapping.paramToLayer(fTransform).mapRect(*childOutput);
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
   * skif::LayerSpace<SkIRect> SkMatrixTransformImageFilter::requiredInput(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput) const {
   *     // The required input for this filter to cover 'desiredOutput' is the smallest rectangle such
   *     // that after being transformed by the layer-space adjusted 'fTransform', it contains the output
   *     skif::LayerSpace<SkIRect> requiredInput;
   *     if (!mapping.paramToLayer(fTransform).inverseMapRect(desiredOutput, &requiredInput)) {
   *         return skif::LayerSpace<SkIRect>::Empty();
   *     }
   *
   *     // Additionally if there is any filtering beyond nearest neighbor, we request an extra buffer of
   *     // pixels so that the content is available to the bilerp/bicubic kernel.
   *     if (fSampling != SkSamplingOptions()) {
   *         requiredInput.outset(skif::LayerSpace<SkISize>({1, 1}));
   *     }
   *     return requiredInput;
   * }
   * ```
   */
  private fun requiredInput(mapping: Mapping, desiredOutput: LayerSpace<SkIRect>): LayerSpace<SkIRect> {
    TODO("Implement requiredInput")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkMatrixTransformImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *     SkMatrix matrix;
   *     buffer.readMatrix(&matrix);
   *
   *     auto sampling = [&]() {
   *         if (buffer.isVersionLT(SkPicturePriv::kMatrixImageFilterSampling_Version)) {
   *             return SkSamplingPriv::FromFQ(buffer.read32LE(kLast_SkLegacyFQ), kLinear_SkMediumAs);
   *         } else {
   *             return buffer.readSampling();
   *         }
   *     }();
   *     return SkImageFilters::MatrixTransform(matrix, sampling, common.getInput(0));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * sk_sp<SkFlattenable> SkMatrixTransformImageFilter::LegacyOffsetCreateProc(SkReadBuffer& buffer) {
     *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
     *     SkPoint offset;
     *     buffer.readPoint(&offset);
     *     return SkImageFilters::Offset(offset.x(), offset.y(), common.getInput(0), common.cropRect());
     * }
     * ```
     */
    private fun legacyOffsetCreateProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
      TODO("Implement legacyOffsetCreateProc")
    }
  }
}
