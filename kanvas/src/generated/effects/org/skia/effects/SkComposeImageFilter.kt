package org.skia.effects

import kotlin.Array
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkComposeImageFilter final : public SkImageFilter_Base {
 *     static constexpr int kOuter = 0;
 *     static constexpr int kInner = 1;
 *
 * public:
 *     explicit SkComposeImageFilter(sk_sp<SkImageFilter> inputs[2])
 *             : SkImageFilter_Base(inputs, 2,
 *                                  // Compose only uses the source if the inner filter uses the source
 *                                  // image. Any outer reference to source is rebound to the result of
 *                                  // the inner.
 *                                  inputs[kInner] ? as_IFB(inputs[kInner])->usesSource() : false) {
 *         SkASSERT(inputs[kOuter].get());
 *         SkASSERT(inputs[kInner].get());
 *     }
 *
 *     SkRect computeFastBounds(const SkRect& src) const override;
 *
 * protected:
 *     // No flatten() needed since this does not add state beyond the input image filters handled
 *     // by the parent implementation.
 *
 * private:
 *     friend void ::SkRegisterComposeImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkComposeImageFilter)
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
 * }
 * ```
 */
public class SkComposeImageFilter public constructor(
  inputs: Array<SkSp<SkImageFilter>>,
) : SkImageFilterBase(TODO(), TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect SkComposeImageFilter::computeFastBounds(const SkRect& src) const {
   *     return this->getInput(kOuter)->computeFastBounds(
   *             this->getInput(kInner)->computeFastBounds(src));
   * }
   * ```
   */
  public override fun computeFastBounds(src: SkRect): SkRect {
    TODO("Implement computeFastBounds")
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
   * skif::FilterResult SkComposeImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     // Get the expected output of the inner filter, given the source image's layer bounds as content
   *     auto innerOutputBounds =
   *             this->getChildOutputLayerBounds(kInner, ctx.mapping(), ctx.source().layerBounds());
   *     // Get the required input for the outer filter, that it needs to cover the desired output.
   *     skif::LayerSpace<SkIRect> outerRequiredInput =
   *             this->getChildInputLayerBounds(kOuter,
   *                                            ctx.mapping(),
   *                                            ctx.desiredOutput(),
   *                                            innerOutputBounds);
   *
   *     // Evalute the inner filter and pass that to the outer filter.
   *     skif::FilterResult innerResult =
   *             this->getChildOutput(kInner, ctx.withNewDesiredOutput(outerRequiredInput));
   *
   *     // NOTE: This is the only spot in image filtering where the source image of the context
   *     // is not constant for the entire DAG evaluation. Given that the inner and outer DAG branches
   *     // were already created, there's no alternative way for the leaf nodes of the outer DAG to
   *     // get the results of the inner DAG. Overriding the source image of the context has the correct
   *     // effect, but means that the source image is not fixed for the entire filter process.
   *     return this->getChildOutput(kOuter, ctx.withNewSource(innerResult));
   * }
   * ```
   */
  public override fun onFilterImage(context: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkComposeImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The outer filter must produce 'desiredOutput'. Its required input bounds becomes the desired
   *     // output of the inner filter. However, 'contentBounds' is the bounds visible to the input
   *     // filter. The output bounds of the inner filter represents the content bounds of the outer.
   *     std::optional<skif::LayerSpace<SkIRect>> outerContentBounds;
   *     if (contentBounds) {
   *         outerContentBounds = this->getChildOutputLayerBounds(kInner, mapping, *contentBounds);
   *     } // else leave outer's content bounds "unbounded"
   *
   *     skif::LayerSpace<SkIRect> innerDesiredOutput =
   *             this->getChildInputLayerBounds(kOuter, mapping, desiredOutput, outerContentBounds);
   *     return this->getChildInputLayerBounds(kInner, mapping, innerDesiredOutput, contentBounds);
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
   * std::optional<skif::LayerSpace<SkIRect>> SkComposeImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // The 'contentBounds' is processed by the inner filter, producing the content bounds for the
   *     // outer filter of the composition, which then produces the final output bounds.
   *     auto innerBounds = this->getChildOutputLayerBounds(kInner, mapping, contentBounds);
   *     // NOTE: Even if innerBounds is unbounded, the outer image filter may be capable of restricting
   *     // it if it contains a crop image filter.
   *     return this->getChildOutputLayerBounds(kOuter, mapping, innerBounds);
   * }
   * ```
   */
  public override fun onGetOutputLayerBounds(mapping: Mapping, contentBounds: LayerSpace<SkIRect>?): Int {
    TODO("Implement onGetOutputLayerBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkComposeImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 2);
   *     return SkImageFilters::Compose(common.getInput(kOuter), common.getInput(kInner));
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }

  public companion object {
    private val kOuter: Int = TODO("Initialize kOuter")

    private val kInner: Int = TODO("Initialize kInner")
  }
}
