package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class RoundCornersAdapter final : public DiscardableAdapterBase<RoundCornersAdapter,
 *                                                                 sksg::RoundEffect> {
 * public:
 *     RoundCornersAdapter(const skjson::ObjectValue& jround,
 *                         const AnimationBuilder& abuilder,
 *                         sk_sp<sksg::GeometryNode> child)
 *         : INHERITED(sksg::RoundEffect::Make(std::move(child))) {
 *         this->bind(abuilder, jround["r"], fRadius);
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setRadius(fRadius);
 *     }
 *
 *     ScalarValue fRadius = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<RoundCornersAdapter, sksg::RoundEffect>;
 * }
 * ```
 */
public class RoundCornersAdapter public constructor(
  jround: ObjectValue,
  abuilder: AnimationBuilder,
  child: SkSp<GeometryNode>,
) : DiscardableAdapterBase(TODO()),
    RoundCornersAdapter,
    RoundEffect {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRadius = 0
   * ```
   */
  private var fRadius: ScalarValue = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setRadius(fRadius);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
