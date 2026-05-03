package org.skia.effects

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
 * class SkMergeImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkMergeImageFilter(sk_sp<SkImageFilter>* const filters, int count)
 *             : SkImageFilter_Base(filters, count) {
 *         SkASSERT(filters && count > 0);
 *     }
 *
 *     SkRect computeFastBounds(const SkRect&) const override;
 *
 *     // No need to override flatten() since there's no additional state to write over base class.
 *
 * private:
 *     friend void ::SkRegisterMergeImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkMergeImageFilter)
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
 * }
 * ```
 */
public class SkMergeImageFilter public constructor(
  filters: SkSp<SkImageFilter>?,
  count: Int,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkRect SkMergeImageFilter::computeFastBounds(const SkRect& rect) const {
   *     // The base computeFastBounds() implementation is the union of all fast bounds from children,
   *     // or 'rect' if there are none. For merge, zero children means zero output so only call the
   *     // base implementation when there are filters to merge.
   *     // TODO: When the bounds update is complete, this default implementation may go away and we
   *     // can move the union'ing logic here.
   *     return SkImageFilter_Base::computeFastBounds(rect);
   * }
   * ```
   */
  public override fun computeFastBounds(rect: SkRect): SkRect {
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
   * skif::FilterResult SkMergeImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     const int inputCount = this->countInputs();
   *     skif::FilterResult::Builder builder{ctx};
   *     for (int i = 0; i < inputCount; ++i) {
   *         builder.add(this->getChildOutput(i, ctx));
   *     }
   *     return builder.merge();
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkMergeImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     const int inputCount = this->countInputs();
   *     // Union of all child input bounds so that one source image can provide for all of them.
   *     return skif::LayerSpace<SkIRect>::Union(
   *             inputCount,
   *             [&](int i) {
   *                 return this->getChildInputLayerBounds(i, mapping, desiredOutput, contentBounds);
   *             });
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
   * std::optional<skif::LayerSpace<SkIRect>> SkMergeImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     const int inputCount = this->countInputs();
   *     // Merge is src-over of all child outputs, so covers their union but no more
   *     bool childIsUnbounded = false;
   *     auto childOutput = skif::LayerSpace<SkIRect>::Union(
   *             inputCount,
   *             [&](int i) {
   *                 auto o = this->getChildOutputLayerBounds(i, mapping, contentBounds);
   *                 if (o) {
   *                     return *o;
   *                 } else {
   *                     childIsUnbounded = true;
   *                     // This value doesn't matter once childIsUnbounded is true
   *                     return skif::LayerSpace<SkIRect>::Empty();
   *                 }
   *             });
   *     if (childIsUnbounded) {
   *         return skif::LayerSpace<SkIRect>::Unbounded();
   *     } else {
   *         return childOutput;
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
   * sk_sp<SkFlattenable> SkMergeImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     Common common;
   *     if (!common.unflatten(buffer, -1) || !buffer.isValid()) {
   *         return nullptr;
   *     }
   *     return SkImageFilters::Merge(common.inputs(), common.inputCount(), common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
