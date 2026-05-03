package org.skia.modules

import kotlin.Float
import kotlin.collections.List
import org.skia.core.SkCubicMap
import undefined.ScalarValue
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class ScalarKeyframeAnimator final : public KeyframeAnimator {
 * public:
 *     ScalarKeyframeAnimator(std::vector<Keyframe> kfs,
 *                            std::vector<SkCubicMap> cms,
 *                            ScalarValue* target_value)
 *         : INHERITED(std::move(kfs), std::move(cms))
 *         , fTarget(target_value) {}
 *
 * private:
 *
 *     StateChanged onSeek(float t) override {
 *         const auto& lerp_info = this->getLERPInfo(t);
 *         const auto  old_value = *fTarget;
 *
 *         *fTarget = Lerp(lerp_info.vrec0.flt, lerp_info.vrec1.flt, lerp_info.weight);
 *
 *         return *fTarget != old_value;
 *     }
 *
 *     ScalarValue* fTarget;
 *
 *     using INHERITED = KeyframeAnimator;
 * }
 * ```
 */
public class ScalarKeyframeAnimator public constructor(
  kfs: List<Keyframe>,
  cms: List<SkCubicMap>,
  targetValue: ScalarValue?,
) : KeyframeAnimator(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * ScalarValue* fTarget
   * ```
   */
  private var fTarget: ScalarValue? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         const auto& lerp_info = this->getLERPInfo(t);
   *         const auto  old_value = *fTarget;
   *
   *         *fTarget = Lerp(lerp_info.vrec0.flt, lerp_info.vrec1.flt, lerp_info.weight);
   *
   *         return *fTarget != old_value;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
