package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class OpacityAdapter final : public DiscardableAdapterBase<OpacityAdapter, sksg::OpacityEffect> {
 * public:
 *     OpacityAdapter(const skjson::ObjectValue& jobject,
 *                    sk_sp<sksg::RenderNode> child,
 *                    const AnimationBuilder& abuilder)
 *         : INHERITED(sksg::OpacityEffect::Make(std::move(child))) {
 *         this->bind(abuilder, jobject["o"], fOpacity);
 *     }
 *
 * private:
 *     void onSync() override {
 *         this->node()->setOpacity(fOpacity * 0.01f);
 *     }
 *
 *     ScalarValue fOpacity = 100;
 *
 *     using INHERITED = DiscardableAdapterBase<OpacityAdapter, sksg::OpacityEffect>;
 * }
 * ```
 */
public class OpacityAdapter public constructor(
  jobject: ObjectValue,
  child: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(TODO()),
    OpacityAdapter,
    OpacityEffect {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue fOpacity = 100
   * ```
   */
  private var fOpacity: ScalarValue = TODO("Initialize fOpacity")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         this->node()->setOpacity(fOpacity * 0.01f);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
