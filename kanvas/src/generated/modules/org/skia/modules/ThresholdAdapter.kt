package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class ThresholdAdapter final : public DiscardableAdapterBase<ThresholdAdapter,
 *                                                              sksg::ExternalColorFilter> {
 * public:
 *     ThresholdAdapter(const skjson::ArrayValue& jprops,
 *                      sk_sp<sksg::RenderNode> layer,
 *                      const AnimationBuilder& abuilder)
 *         : INHERITED(sksg::ExternalColorFilter::Make(std::move(layer)))
 *     {
 *         enum : size_t {
 *             kLevel_Index = 0,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this).bind(kLevel_Index, fLevel);
 *     }
 *
 * private:
 *     void onSync() override {
 *         auto cf =
 *                 threshold_effect()->makeColorFilter(SkData::MakeWithCopy(&fLevel, sizeof(fLevel)));
 *
 *         this->node()->setColorFilter(std::move(cf));
 *     }
 *
 *     ScalarValue fLevel = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<ThresholdAdapter, sksg::ExternalColorFilter>;
 * }
 * ```
 */
public class ThresholdAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(TODO()),
    ThresholdAdapter,
    ExternalColorFilter {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fLevel = 0
   * ```
   */
  private var fLevel: ScalarValue = TODO("Initialize fLevel")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         auto cf =
   *                 threshold_effect()->makeColorFilter(SkData::MakeWithCopy(&fLevel, sizeof(fLevel)));
   *
   *         this->node()->setColorFilter(std::move(cf));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
