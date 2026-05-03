package org.skia.effects

import kotlin.Boolean
import kotlin.Int
import org.skia.core.Context
import org.skia.core.FilterResult
import org.skia.core.LayerSpace
import org.skia.core.Mapping
import org.skia.core.MatrixCapability
import org.skia.core.SkImageFilterBase
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkFlattenable
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkReadBuffer
import org.skia.foundation.SkSp
import org.skia.foundation.SkWriteBuffer
import org.skia.math.SkIRect
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SkColorFilterImageFilter final : public SkImageFilter_Base {
 * public:
 *     SkColorFilterImageFilter(sk_sp<SkColorFilter> cf, sk_sp<SkImageFilter> input)
 *             : SkImageFilter_Base(&input, 1)
 *             , fColorFilter(std::move(cf)) {}
 *
 *     SkRect computeFastBounds(const SkRect& bounds) const override;
 *
 * protected:
 *     void flatten(SkWriteBuffer&) const override;
 *
 * private:
 *     friend void ::SkRegisterColorFilterImageFilterFlattenable();
 *     SK_FLATTENABLE_HOOKS(SkColorFilterImageFilter)
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
 *     MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
 *
 *     bool onAffectsTransparentBlack() const override {
 *         return as_CFB(fColorFilter)->affectsTransparentBlack();
 *     }
 *
 *     bool onIsColorFilterNode(SkColorFilter** filter) const override {
 *         SkASSERT(1 == this->countInputs());
 *         if (filter) {
 *             *filter = SkRef(fColorFilter.get());
 *         }
 *         return true;
 *     }
 *
 *     sk_sp<SkColorFilter> fColorFilter;
 * }
 * ```
 */
public class SkColorFilterImageFilter public constructor(
  cf: SkSp<SkColorFilter>,
  input: SkSp<SkImageFilter>,
) : SkImageFilterBase(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkColorFilter> fColorFilter
   * ```
   */
  private var fColorFilter: SkSp<SkColorFilter> = TODO("Initialize fColorFilter")

  /**
   * C++ original:
   * ```cpp
   * SkRect SkColorFilterImageFilter::computeFastBounds(const SkRect& bounds) const {
   *     // See comment in onGetOutputLayerBounds().
   *     if (as_CFB(fColorFilter)->affectsTransparentBlack()) {
   *         return SkRectPriv::MakeLargeS32();
   *     } else if (this->getInput(0)) {
   *         return this->getInput(0)->computeFastBounds(bounds);
   *     } else {
   *         return bounds;
   *     }
   * }
   * ```
   */
  public override fun computeFastBounds(bounds: SkRect): SkRect {
    TODO("Implement computeFastBounds")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkColorFilterImageFilter::flatten(SkWriteBuffer& buffer) const {
   *     this->SkImageFilter_Base::flatten(buffer);
   *     buffer.writeFlattenable(fColorFilter.get());
   * }
   * ```
   */
  protected override fun flatten(buffer: SkWriteBuffer) {
    TODO("Implement flatten")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::FilterResult SkColorFilterImageFilter::onFilterImage(const skif::Context& ctx) const {
   *     return this->getChildOutput(0, ctx).applyColorFilter(ctx, fColorFilter);
   * }
   * ```
   */
  public override fun onFilterImage(ctx: Context): FilterResult {
    TODO("Implement onFilterImage")
  }

  /**
   * C++ original:
   * ```cpp
   * skif::LayerSpace<SkIRect> SkColorFilterImageFilter::onGetInputLayerBounds(
   *         const skif::Mapping& mapping,
   *         const skif::LayerSpace<SkIRect>& desiredOutput,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     return this->getChildInputLayerBounds(0, mapping, desiredOutput, contentBounds);
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
   * std::optional<skif::LayerSpace<SkIRect>> SkColorFilterImageFilter::onGetOutputLayerBounds(
   *         const skif::Mapping& mapping,
   *         std::optional<skif::LayerSpace<SkIRect>> contentBounds) const {
   *     // For bounds calculations, we only need to consider the current node's transparency
   *     // effect, since any child's transparency-affecting behavior should be accounted for in
   *     // the child's bounds call.
   *     if (as_CFB(fColorFilter)->affectsTransparentBlack()) {
   *         return skif::LayerSpace<SkIRect>::Unbounded();
   *     } else {
   *         return this->getChildOutputLayerBounds(0, mapping, contentBounds);
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
   * MatrixCapability onGetCTMCapability() const override { return MatrixCapability::kComplex; }
   * ```
   */
  public override fun onGetCTMCapability(): MatrixCapability {
    TODO("Implement onGetCTMCapability")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onAffectsTransparentBlack() const override {
   *         return as_CFB(fColorFilter)->affectsTransparentBlack();
   *     }
   * ```
   */
  public override fun onAffectsTransparentBlack(): Boolean {
    TODO("Implement onAffectsTransparentBlack")
  }

  /**
   * C++ original:
   * ```cpp
   * bool onIsColorFilterNode(SkColorFilter** filter) const override {
   *         SkASSERT(1 == this->countInputs());
   *         if (filter) {
   *             *filter = SkRef(fColorFilter.get());
   *         }
   *         return true;
   *     }
   * ```
   */
  public override fun onIsColorFilterNode(filter: Int?): Boolean {
    TODO("Implement onIsColorFilterNode")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkFlattenable> SkColorFilterImageFilter::CreateProc(SkReadBuffer& buffer) {
   *     SK_IMAGEFILTER_UNFLATTEN_COMMON(common, 1);
   *     sk_sp<SkColorFilter> cf(buffer.readColorFilter());
   *     return SkImageFilters::ColorFilter(std::move(cf), common.getInput(0), common.cropRect());
   * }
   * ```
   */
  public fun createProc(buffer: SkReadBuffer): SkSp<SkFlattenable> {
    TODO("Implement createProc")
  }
}
