package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.core.SkContourMeasure
import org.skia.core.SkCubicMap
import org.skia.foundation.SkSp
import undefined.StateChanged
import undefined.Vec2Value

/**
 * C++ original:
 * ```cpp
 * class Vec2KeyframeAnimator final : public KeyframeAnimator {
 * public:
 *     struct SpatialValue {
 *         Vec2Value               v2;
 *         sk_sp<SkContourMeasure> cmeasure;
 *     };
 *
 *     Vec2KeyframeAnimator(std::vector<Keyframe> kfs, std::vector<SkCubicMap> cms,
 *                          std::vector<SpatialValue> vs, Vec2Value* vec_target, float* rot_target)
 *         : INHERITED(std::move(kfs), std::move(cms))
 *         , fValues(std::move(vs))
 *         , fVecTarget(vec_target)
 *         , fRotTarget(rot_target) {}
 *
 * private:
 *     StateChanged update(const Vec2Value& new_vec_value, const Vec2Value& new_tan_value) {
 *         auto changed = (new_vec_value != *fVecTarget);
 *         *fVecTarget = new_vec_value;
 *
 *         if (fRotTarget) {
 *             const auto new_rot_value = SkRadiansToDegrees(std::atan2(new_tan_value.y,
 *                                                                      new_tan_value.x));
 *             changed |= new_rot_value != *fRotTarget;
 *             *fRotTarget = new_rot_value;
 *         }
 *
 *         return changed;
 *     }
 *
 *     StateChanged onSeek(float t) override {
 *         auto get_lerp_info = [this](float t) {
 *             auto lerp_info = this->getLERPInfo(t);
 *
 *             // When tracking rotation/orientation, the last keyframe requires special handling:
 *             // it doesn't store any spatial information but it is expected to maintain the
 *             // previous orientation (per AE semantics).
 *             //
 *             // The easiest way to achieve this is to actually swap with the previous keyframe,
 *             // with an adjusted weight of 1.
 *             const auto vidx = lerp_info.vrec0.idx;
 *             if (fRotTarget && vidx == fValues.size() - 1 && vidx > 0) {
 *                 SkASSERT(!fValues[vidx].cmeasure);
 *                 SkASSERT(lerp_info.vrec1.idx == vidx);
 *
 *                 // Change LERPInfo{0, SIZE - 1, SIZE - 1}
 *                 // to     LERPInfo{1, SIZE - 2, SIZE - 1}
 *                 lerp_info.weight = 1;
 *                 lerp_info.vrec0  = {vidx - 1};
 *
 *                 // This yields equivalent lerp results because keyframed values are contiguous
 *                 // i.e frame[n-1].end_val == frame[n].start_val.
 *             }
 *
 *             return lerp_info;
 *         };
 *
 *         const auto lerp_info = get_lerp_info(t);
 *
 *         const auto& v0 = fValues[lerp_info.vrec0.idx];
 *         if (v0.cmeasure) {
 *             // Spatial keyframe: the computed weight is relative to the interpolation path
 *             // arc length.
 *             SkPoint  pos;
 *             SkVector tan;
 *             const float len = v0.cmeasure->length(),
 *                    distance = len * lerp_info.weight;
 *             if (v0.cmeasure->getPosTan(distance, &pos, &tan)) {
 *                 // Easing can yield a sub/super normal weight, which in turn can cause the
 *                 // interpolation position to become negative or larger than the path length.
 *                 // In those cases the expectation is to extrapolate using the endpoint tangent.
 *                 if (distance < 0 || distance > len) {
 *                     const float overshoot = std::copysign(std::max(-distance, distance - len),
 *                                                           distance);
 *                     pos += tan * overshoot;
 *                 }
 *
 *                 return this->update({ pos.fX, pos.fY }, {tan.fX, tan.fY});
 *             }
 *         }
 *
 *         const auto& v1 = fValues[lerp_info.vrec1.idx];
 *         const auto tan = v1.v2 - v0.v2;
 *
 *         return this->update(Lerp(v0.v2, v1.v2, lerp_info.weight), tan);
 *     }
 *
 *     const std::vector<Vec2KeyframeAnimator::SpatialValue> fValues;
 *     Vec2Value*                      fVecTarget;
 *     float*                          fRotTarget;
 *
 *     using INHERITED = KeyframeAnimator;
 * }
 * ```
 */
public class Vec2KeyframeAnimator public constructor(
  kfs: List<Keyframe>,
  cms: List<SkCubicMap>,
  vs: List<SpatialValue>,
  vecTarget: Vec2Value?,
  rotTarget: Float?,
) : KeyframeAnimator(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<Vec2KeyframeAnimator::SpatialValue> fValues
   * ```
   */
  private val fValues: Int = TODO("Initialize fValues")

  /**
   * C++ original:
   * ```cpp
   * Vec2Value*                      fVecTarget
   * ```
   */
  private var fVecTarget: Vec2Value? = TODO("Initialize fVecTarget")

  /**
   * C++ original:
   * ```cpp
   * float*                          fRotTarget
   * ```
   */
  private var fRotTarget: Float? = TODO("Initialize fRotTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged update(const Vec2Value& new_vec_value, const Vec2Value& new_tan_value) {
   *         auto changed = (new_vec_value != *fVecTarget);
   *         *fVecTarget = new_vec_value;
   *
   *         if (fRotTarget) {
   *             const auto new_rot_value = SkRadiansToDegrees(std::atan2(new_tan_value.y,
   *                                                                      new_tan_value.x));
   *             changed |= new_rot_value != *fRotTarget;
   *             *fRotTarget = new_rot_value;
   *         }
   *
   *         return changed;
   *     }
   * ```
   */
  private fun update(newVecValue: Vec2Value, newTanValue: Vec2Value): StateChanged {
    TODO("Implement update")
  }

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         auto get_lerp_info = [this](float t) {
   *             auto lerp_info = this->getLERPInfo(t);
   *
   *             // When tracking rotation/orientation, the last keyframe requires special handling:
   *             // it doesn't store any spatial information but it is expected to maintain the
   *             // previous orientation (per AE semantics).
   *             //
   *             // The easiest way to achieve this is to actually swap with the previous keyframe,
   *             // with an adjusted weight of 1.
   *             const auto vidx = lerp_info.vrec0.idx;
   *             if (fRotTarget && vidx == fValues.size() - 1 && vidx > 0) {
   *                 SkASSERT(!fValues[vidx].cmeasure);
   *                 SkASSERT(lerp_info.vrec1.idx == vidx);
   *
   *                 // Change LERPInfo{0, SIZE - 1, SIZE - 1}
   *                 // to     LERPInfo{1, SIZE - 2, SIZE - 1}
   *                 lerp_info.weight = 1;
   *                 lerp_info.vrec0  = {vidx - 1};
   *
   *                 // This yields equivalent lerp results because keyframed values are contiguous
   *                 // i.e frame[n-1].end_val == frame[n].start_val.
   *             }
   *
   *             return lerp_info;
   *         };
   *
   *         const auto lerp_info = get_lerp_info(t);
   *
   *         const auto& v0 = fValues[lerp_info.vrec0.idx];
   *         if (v0.cmeasure) {
   *             // Spatial keyframe: the computed weight is relative to the interpolation path
   *             // arc length.
   *             SkPoint  pos;
   *             SkVector tan;
   *             const float len = v0.cmeasure->length(),
   *                    distance = len * lerp_info.weight;
   *             if (v0.cmeasure->getPosTan(distance, &pos, &tan)) {
   *                 // Easing can yield a sub/super normal weight, which in turn can cause the
   *                 // interpolation position to become negative or larger than the path length.
   *                 // In those cases the expectation is to extrapolate using the endpoint tangent.
   *                 if (distance < 0 || distance > len) {
   *                     const float overshoot = std::copysign(std::max(-distance, distance - len),
   *                                                           distance);
   *                     pos += tan * overshoot;
   *                 }
   *
   *                 return this->update({ pos.fX, pos.fY }, {tan.fX, tan.fY});
   *             }
   *         }
   *
   *         const auto& v1 = fValues[lerp_info.vrec1.idx];
   *         const auto tan = v1.v2 - v0.v2;
   *
   *         return this->update(Lerp(v0.v2, v1.v2, lerp_info.weight), tan);
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }

  public data class SpatialValue public constructor(
    public var v2: Vec2Value,
    public var cmeasure: SkSp<SkContourMeasure>,
  )
}
