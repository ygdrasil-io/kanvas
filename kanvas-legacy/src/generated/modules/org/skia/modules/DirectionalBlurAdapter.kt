package org.skia.modules

import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class DirectionalBlurAdapter final : public DiscardableAdapterBase<DirectionalBlurAdapter,
 *                                                                    sksg::ExternalImageFilter> {
 *     public:
 *         DirectionalBlurAdapter(const skjson::ArrayValue& jprops,
 *                                const AnimationBuilder& abuilder)
 *             : INHERITED(sksg::ExternalImageFilter::Make())
 *         {
 *             enum : size_t {
 *                 kDirection_Index  = 0,
 *                 kBlurLength_Index = 1,
 *             };
 *
 *
 *             EffectBinder(jprops, abuilder, this)
 *                 .bind(       kDirection_Index, fDirection)
 *                 .bind(    kBlurLength_Index, fBlurLength);
 *         }
 *     private:
 *         void onSync() override {
 *             const auto rot = fDirection - 90;
 *             auto filter =
 *             SkImageFilters::MatrixTransform(SkMatrix::RotateDeg(rot),
 *             SkSamplingOptions(SkFilterMode::kLinear),
 *                 SkImageFilters::Blur(fBlurLength * kBlurSizeToSigma, 0,
 *                     SkImageFilters::MatrixTransform(SkMatrix::RotateDeg(-rot),
 *                     SkSamplingOptions(SkFilterMode::kLinear), nullptr)));
 *             this->node()->setImageFilter(std::move(filter));
 *         }
 *
 *         ScalarValue fDirection = 0;
 *         ScalarValue fBlurLength = 0;
 *
 *         using INHERITED = DiscardableAdapterBase<DirectionalBlurAdapter, sksg::ExternalImageFilter>;
 * }
 * ```
 */
public class DirectionalBlurAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(TODO()),
    DirectionalBlurAdapter,
    ExternalImageFilter {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fDirection = 0
   * ```
   */
  private var fDirection: ScalarValue = TODO("Initialize fDirection")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fBlurLength = 0
   * ```
   */
  private var fBlurLength: ScalarValue = TODO("Initialize fBlurLength")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *             const auto rot = fDirection - 90;
   *             auto filter =
   *             SkImageFilters::MatrixTransform(SkMatrix::RotateDeg(rot),
   *             SkSamplingOptions(SkFilterMode::kLinear),
   *                 SkImageFilters::Blur(fBlurLength * kBlurSizeToSigma, 0,
   *                     SkImageFilters::MatrixTransform(SkMatrix::RotateDeg(-rot),
   *                     SkSamplingOptions(SkFilterMode::kLinear), nullptr)));
   *             this->node()->setImageFilter(std::move(filter));
   *         }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
