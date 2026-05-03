package org.skia.modules

import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class SharpenAdapter final : public DiscardableAdapterBase<SharpenAdapter,
 *                                                            sksg::ExternalImageFilter> {
 *     public:
 *         SharpenAdapter(const skjson::ArrayValue& jprops,
 *                        const AnimationBuilder& abuilder)
 *             : INHERITED(sksg::ExternalImageFilter::Make())
 *         {
 *             enum : size_t {
 *                 kSharpenAmount_Index  = 0,
 *             };
 *
 *             EffectBinder(jprops, abuilder, this).bind(kSharpenAmount_Index, fAmount);
 *         }
 *     private:
 *         void onSync() override {
 *             SkScalar intensity = 1 + (fAmount * 0.01);
 *             SkScalar discount = (1 - intensity) / 8.0;
 *             SkScalar kernel[9] = {
 *                 discount, discount, discount,
 *                 discount, intensity, discount,
 *                 discount, discount, discount,
 *             };
 *             auto filter = SkImageFilters::MatrixConvolution(SkISize::Make(3,3), kernel, 1, 0,
 *                                                             SkIPoint::Make(1,1), SkTileMode::kRepeat,
 *                                                             true, nullptr);
 *             this->node()->setImageFilter(std::move(filter));
 *         }
 *
 *         ScalarValue fAmount = 0;
 *
 *         using INHERITED = DiscardableAdapterBase<SharpenAdapter, sksg::ExternalImageFilter>;
 * }
 * ```
 */
public class SharpenAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(TODO()),
    SharpenAdapter,
    ExternalImageFilter {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fAmount = 0
   * ```
   */
  private var fAmount: ScalarValue = TODO("Initialize fAmount")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *             SkScalar intensity = 1 + (fAmount * 0.01);
   *             SkScalar discount = (1 - intensity) / 8.0;
   *             SkScalar kernel[9] = {
   *                 discount, discount, discount,
   *                 discount, intensity, discount,
   *                 discount, discount, discount,
   *             };
   *             auto filter = SkImageFilters::MatrixConvolution(SkISize::Make(3,3), kernel, 1, 0,
   *                                                             SkIPoint::Make(1,1), SkTileMode::kRepeat,
   *                                                             true, nullptr);
   *             this->node()->setImageFilter(std::move(filter));
   *         }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
