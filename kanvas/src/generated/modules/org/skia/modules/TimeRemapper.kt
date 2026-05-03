package org.skia.modules

import kotlin.Float
import undefined.ScalarValue

/**
 * C++ original:
 * ```cpp
 * class TimeRemapper final : public AnimatablePropertyContainer {
 * public:
 *     TimeRemapper(const skjson::ObjectValue& jtm, const AnimationBuilder* abuilder, float scale)
 *         : fScale(scale) {
 *         this->bind(*abuilder, jtm, fT);
 *     }
 *
 *     float t() const { return fT * fScale; }
 *
 * private:
 *     void onSync() override {
 *         // nothing to sync - we just track t
 *     }
 *
 *     const float fScale;
 *
 *     ScalarValue fT = 0;
 * }
 * ```
 */
public class TimeRemapper public constructor(
  jtm: ObjectValue,
  abuilder: AnimationBuilder?,
  scale: Float,
) : AnimatablePropertyContainer() {
  /**
   * C++ original:
   * ```cpp
   * const float fScale
   * ```
   */
  private val fScale: Float = TODO("Initialize fScale")

  /**
   * C++ original:
   * ```cpp
   * ScalarValue fT = 0
   * ```
   */
  private var fT: ScalarValue = TODO("Initialize fT")

  /**
   * C++ original:
   * ```cpp
   * float t() const { return fT * fScale; }
   * ```
   */
  public fun t(): Float {
    TODO("Implement t")
  }

  /**
   * C++ original:
   * ```cpp
   * void onSync() override {
   *         // nothing to sync - we just track t
   *     }
   * ```
   */
  public override fun onSync() {
    TODO("Implement onSync")
  }
}
