package org.skia.modules

import undefined.ScalarValue
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class RectangleGeometryAdapter final :
 *         public DiscardableAdapterBase<RectangleGeometryAdapter, sksg::RRect> {
 * public:
 *     RectangleGeometryAdapter(const skjson::ObjectValue& jrect,
 *                              const AnimationBuilder* abuilder) {
 *         this->node()->setDirection(ParseDefault(jrect["d"], -1) == 3 ? SkPathDirection::kCCW
 *                                                                      : SkPathDirection::kCW);
 *         this->node()->setInitialPointIndex(2); // starting point: (Right, Top - radius.y)
 *
 *         this->bind(*abuilder, jrect["s"], fSize     );
 *         this->bind(*abuilder, jrect["p"], fPosition );
 *         this->bind(*abuilder, jrect["r"], fRoundness);
 *     }
 *
 * private:
 *     void onSync() override {
 *         const auto bounds = SkRect::MakeXYWH(fPosition.x - fSize.x / 2,
 *                                              fPosition.y - fSize.y / 2,
 *                                              fSize.x, fSize.y);
 *
 *         this->node()->setRRect(SkRRect::MakeRectXY(bounds, fRoundness, fRoundness));
 *     }
 *
 *     Vec2Value   fSize      = {0,0},
 *                 fPosition  = {0,0}; // center
 *     ScalarValue fRoundness = 0;
 * }
 * ```
 */
public class RectangleGeometryAdapter public constructor(
  jrect: ObjectValue,
  abuilder: AnimationBuilder?,
) : DiscardableAdapterBase(),
    RectangleGeometryAdapter,
    RRect {
  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fSize      = {0,0}
   * ```
   */
  private var fSize: Vec2Value = TODO("Initialize fSize")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value   fSize      = {0,0},
   *                 fPosition  = {0,0}
   * ```
   */
  private var fPosition: Vec2Value = TODO("Initialize fPosition")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fRoundness = 0
   * ```
   */
  private var fRoundness: ScalarValue = TODO("Initialize fRoundness")

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         const auto bounds = SkRect::MakeXYWH(fPosition.x - fSize.x / 2,
   *                                              fPosition.y - fSize.y / 2,
   *                                              fSize.x, fSize.y);
   *
   *         this->node()->setRRect(SkRRect::MakeRectXY(bounds, fRoundness, fRoundness));
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
