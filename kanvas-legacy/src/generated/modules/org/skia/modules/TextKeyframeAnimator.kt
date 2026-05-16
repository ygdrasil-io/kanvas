package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.core.SkCubicMap
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class TextKeyframeAnimator final : public KeyframeAnimator {
 * public:
 *     TextKeyframeAnimator(std::vector<Keyframe> kfs, std::vector<SkCubicMap> cms,
 *                          std::vector<TextValue> vs, TextValue* target_value)
 *         : INHERITED(std::move(kfs), std::move(cms))
 *         , fValues(std::move(vs))
 *         , fTarget(target_value) {}
 *
 * private:
 *     StateChanged onSeek(float t) override {
 *         const auto& lerp_info = this->getLERPInfo(t);
 *
 *         // Text value keyframes are treated as selectors, not as interpolated values.
 *         if (*fTarget != fValues[SkToSizeT(lerp_info.vrec0.idx)]) {
 *             *fTarget = fValues[SkToSizeT(lerp_info.vrec0.idx)];
 *             return true;
 *         }
 *
 *         return false;
 *     }
 *
 *     const std::vector<TextValue> fValues;
 *     TextValue*                   fTarget;
 *
 *     using INHERITED = KeyframeAnimator;
 * }
 * ```
 */
public class TextKeyframeAnimator public constructor(
  kfs: List<Keyframe>,
  cms: List<SkCubicMap>,
  vs: List<TextValue>,
  targetValue: TextValue?,
) : KeyframeAnimator(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<TextValue> fValues
   * ```
   */
  private val fValues: Int = TODO("Initialize fValues")

  /**
   * C++ original:
   * ```cpp
   * TextValue*                   fTarget
   * ```
   */
  private var fTarget: TextValue? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         const auto& lerp_info = this->getLERPInfo(t);
   *
   *         // Text value keyframes are treated as selectors, not as interpolated values.
   *         if (*fTarget != fValues[SkToSizeT(lerp_info.vrec0.idx)]) {
   *             *fTarget = fValues[SkToSizeT(lerp_info.vrec0.idx)];
   *             return true;
   *         }
   *
   *         return false;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
