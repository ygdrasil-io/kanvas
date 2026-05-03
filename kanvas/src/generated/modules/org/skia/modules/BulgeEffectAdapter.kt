package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class BulgeEffectAdapter final : public DiscardableAdapterBase<BulgeEffectAdapter,
 *                                                              BulgeNode> {
 * public:
 *     BulgeEffectAdapter(const skjson::ArrayValue& jprops,
 *                       const AnimationBuilder& abuilder,
 *                       sk_sp<BulgeNode> node)
 *         : INHERITED(std::move(node))
 *     {
 *         enum : size_t {
 *             kHorizontalRadius_Index = 0,
 *             kVerticalRadius_Index = 1,
 *             kBulgeCenter_Index = 2,
 *             kBulgeHeight_Index = 3,
 *             // kTaper_Index = 4,
 *             // kAA_Index = 5,
 *             // kPinning_Index = 6,
 *         };
 *         EffectBinder(jprops, abuilder, this).bind(kHorizontalRadius_Index, fHorizontalRadius)
 *                                             .bind(kVerticalRadius_Index, fVerticalRadius)
 *                                             .bind(kBulgeCenter_Index, fCenter)
 *                                             .bind(kBulgeHeight_Index, fBulgeHeight);
 *     }
 *
 * private:
 *     void onSync() override {
 *         // pre-shader math
 *         auto n = this->node();
 *         n->setCenter({fCenter.x, fCenter.y});
 *         n->setRadius({fHorizontalRadius, fVerticalRadius});
 *         n->setHeight(fBulgeHeight);
 *     }
 *
 *     Vec2Value fCenter;
 *     ScalarValue fHorizontalRadius,
 *                 fVerticalRadius,
 *                 fBulgeHeight;
 *     using INHERITED = DiscardableAdapterBase<BulgeEffectAdapter, BulgeNode>;
 * }
 * ```
 */
public class BulgeEffectAdapter public constructor(
  jprops: ArrayValue,
  abuilder: AnimationBuilder,
  node: SkSp<BulgeNode>,
) : DiscardableAdapterBase(TODO()),
    BulgeEffectAdapter,
    BulgeNode {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value fCenter
   * ```
   */
  private var fCenter: Vec2Value = TODO("Initialize fCenter")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fHorizontalRadius
   * ```
   */
  private var fHorizontalRadius: ScalarValue = TODO("Initialize fHorizontalRadius")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fHorizontalRadius,
   *                 fVerticalRadius
   * ```
   */
  private var fVerticalRadius: ScalarValue = TODO("Initialize fVerticalRadius")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fHorizontalRadius,
   *                 fVerticalRadius,
   *                 fBulgeHeight
   * ```
   */
  private var fBulgeHeight: ScalarValue = TODO("Initialize fBulgeHeight")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         // pre-shader math
   *         auto n = this->node();
   *         n->setCenter({fCenter.x, fCenter.y});
   *         n->setRadius({fHorizontalRadius, fVerticalRadius});
   *         n->setHeight(fBulgeHeight);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
