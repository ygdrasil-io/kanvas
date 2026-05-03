package org.skia.modules

import org.skia.foundation.SkSp
import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class RadialWipeAdapter final : public DiscardableAdapterBase<RadialWipeAdapter, RWipeRenderNode> {
 * public:
 *     RadialWipeAdapter(const skjson::ArrayValue& jprops,
 *                       sk_sp<sksg::RenderNode> layer,
 *                       const AnimationBuilder& abuilder)
 *         : INHERITED(sk_make_sp<RWipeRenderNode>(std::move(layer))) {
 *
 *         enum : size_t {
 *             kCompletion_Index = 0,
 *             kStartAngle_Index = 1,
 *             kWipeCenter_Index = 2,
 *                   kWipe_Index = 3,
 *                kFeather_Index = 4,
 *         };
 *
 *         EffectBinder(jprops, abuilder, this)
 *             .bind(kCompletion_Index, fCompletion)
 *             .bind(kStartAngle_Index, fStartAngle)
 *             .bind(kWipeCenter_Index, fWipeCenter)
 *             .bind(      kWipe_Index, fWipe      )
 *             .bind(   kFeather_Index, fFeather   );
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto& wiper = this->node();
 *
 *         wiper->setCompletion(fCompletion);
 *         wiper->setStartAngle(fStartAngle);
 *         wiper->setWipeCenter({fWipeCenter.x, fWipeCenter.y});
 *         wiper->setWipe(fWipe);
 *         wiper->setFeather(fFeather);
 *     }
 *
 *     Vec2Value   fWipeCenter = {0,0};
 *     ScalarValue fCompletion = 0,
 *                 fStartAngle = 0,
 *                 fWipe       = 0,
 *                 fFeather    = 0;
 *
 *     using INHERITED = DiscardableAdapterBase<RadialWipeAdapter, RWipeRenderNode>;
 * }
 * ```
 */
public class RadialWipeAdapter public constructor(
  jprops: ArrayValue,
  layer: SkSp<RenderNode>,
  abuilder: AnimationBuilder,
) : DiscardableAdapterBase(TODO()),
    RadialWipeAdapter,
    RWipeRenderNode {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fWipeCenter = {0,0}
   * ```
   */
  private var fWipeCenter: Vec2Value = TODO("Initialize fWipeCenter")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fCompletion = 0
   * ```
   */
  private var fCompletion: ScalarValue = TODO("Initialize fCompletion")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fCompletion = 0,
   *                 fStartAngle = 0
   * ```
   */
  private var fStartAngle: ScalarValue = TODO("Initialize fStartAngle")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fCompletion = 0,
   *                 fStartAngle = 0,
   *                 fWipe       = 0
   * ```
   */
  private var fWipe: ScalarValue = TODO("Initialize fWipe")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fCompletion = 0,
   *                 fStartAngle = 0,
   *                 fWipe       = 0,
   *                 fFeather    = 0
   * ```
   */
  private var fFeather: ScalarValue = TODO("Initialize fFeather")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto& wiper = this->node();
   *
   *         wiper->setCompletion(fCompletion);
   *         wiper->setStartAngle(fStartAngle);
   *         wiper->setWipeCenter({fWipeCenter.x, fWipeCenter.y});
   *         wiper->setWipe(fWipe);
   *         wiper->setFeather(fFeather);
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
