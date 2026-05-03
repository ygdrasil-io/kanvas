package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class PuckerBloatAdapter final : public DiscardableAdapterBase<PuckerBloatAdapter,
 *                                                                PuckerBloatEffect> {
 * public:
 *     PuckerBloatAdapter(const skjson::ObjectValue& joffset,
 *                        const AnimationBuilder& abuilder,
 *                        sk_sp<sksg::GeometryNode> child)
 *         : INHERITED(sk_make_sp<PuckerBloatEffect>(std::move(child))) {
 *         this->bind(abuilder, joffset["a" ], fAmount);
 *     }
 *
 * private:
 *     void onSync() override {
 *         // AE amount is percentage-based.
 *         this->node()->setAmount(fAmount / 100);
 *     }
 *
 *     ScalarValue fAmount = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<PuckerBloatAdapter, PuckerBloatEffect>;
 * }
 * ```
 */
public class PuckerBloatAdapter public constructor(
  joffset: ObjectValue,
  abuilder: AnimationBuilder,
  child: SkSp<GeometryNode>,
) : DiscardableAdapterBase(TODO()),
    PuckerBloatAdapter,
    PuckerBloatEffect {
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
   *         // AE amount is percentage-based.
   *         this->node()->setAmount(fAmount / 100);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
