package org.skia.modules

import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class EllipseGeometryAdapter final :
 *         public DiscardableAdapterBase<EllipseGeometryAdapter, sksg::RRect> {
 * public:
 *     EllipseGeometryAdapter(const skjson::ObjectValue& jellipse,
 *                            const AnimationBuilder* abuilder) {
 *         this->node()->setDirection(ParseDefault(jellipse["d"], -1) == 3 ? SkPathDirection::kCCW
 *                                                                         : SkPathDirection::kCW);
 *         this->node()->setInitialPointIndex(1); // starting point: (Center, Top)
 *
 *         this->bind(*abuilder, jellipse["s"], fSize);
 *         this->bind(*abuilder, jellipse["p"], fPosition);
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto bounds = SkRect::MakeXYWH(fPosition.x - fSize.x / 2,
 *                                              fPosition.y - fSize.y / 2,
 *                                              fSize.x, fSize.y);
 *
 *         this->node()->setRRect(SkRRect::MakeOval(bounds));
 *     }
 *
 *     Vec2Value fSize     = {0,0},
 *               fPosition = {0,0}; // center
 * }
 * ```
 */
public class EllipseGeometryAdapter public constructor(
  jellipse: ObjectValue,
  abuilder: AnimationBuilder?,
) : DiscardableAdapterBase(),
    EllipseGeometryAdapter,
    RRect {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value fSize     = {0,0}
   * ```
   */
  private var fSize: Vec2Value = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value fSize     = {0,0},
   *               fPosition = {0,0}
   * ```
   */
  private var fPosition: Vec2Value = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto bounds = SkRect::MakeXYWH(fPosition.x - fSize.x / 2,
   *                                              fPosition.y - fSize.y / 2,
   *                                              fSize.x, fSize.y);
   *
   *         this->node()->setRRect(SkRRect::MakeOval(bounds));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
