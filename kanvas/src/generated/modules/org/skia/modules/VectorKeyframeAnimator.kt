package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.ULong
import kotlin.collections.List
import org.skia.core.SkCubicMap
import undefined.StateChanged

/**
 * C++ original:
 * ```cpp
 * class VectorKeyframeAnimator final : public KeyframeAnimator {
 * public:
 *     VectorKeyframeAnimator(std::vector<Keyframe> kfs,
 *                            std::vector<SkCubicMap> cms,
 *                            std::vector<float> storage,
 *                            size_t vec_len,
 *                            std::vector<float>* target_value)
 *         : INHERITED(std::move(kfs), std::move(cms))
 *         , fStorage(std::move(storage))
 *         , fVecLen(vec_len)
 *         , fTarget(target_value) {
 *
 *         // Resize the target value appropriately.
 *         fTarget->resize(fVecLen);
 *     }
 *
 * private:
 *     StateChanged onSeek(float t) override {
 *         const auto& lerp_info = this->getLERPInfo(t);
 *
 *         SkASSERT(lerp_info.vrec0.idx + fVecLen <= fStorage.size());
 *         SkASSERT(lerp_info.vrec1.idx + fVecLen <= fStorage.size());
 *         SkASSERT(fTarget->size() == fVecLen);
 *
 *         const auto* v0  = fStorage.data() + lerp_info.vrec0.idx;
 *         const auto* v1  = fStorage.data() + lerp_info.vrec1.idx;
 *               auto* dst = fTarget->data();
 *
 *         const auto is_constant = lerp_info.vrec0.equals(lerp_info.vrec1,
 *                                                         Keyframe::Value::Type::kIndex);
 *         if (is_constant) {
 *             if (0 != std::memcmp(dst, v0, fVecLen * sizeof(float))) {
 *                 std::copy(v0, v0 + fVecLen, dst);
 *                 return true;
 *             }
 *             return false;
 *         }
 *
 *         size_t count = fVecLen;
 *         bool changed = false;
 *
 *         while (count >= 4) {
 *             const auto old_val = skvx::float4::Load(dst),
 *                        new_val = Lerp(skvx::float4::Load(v0),
 *                                       skvx::float4::Load(v1),
 *                                       lerp_info.weight);
 *
 *             changed |= any(new_val != old_val);
 *             new_val.store(dst);
 *
 *             v0    += 4;
 *             v1    += 4;
 *             dst   += 4;
 *             count -= 4;
 *         }
 *
 *         while (count-- > 0) {
 *             const auto new_val = Lerp(*v0++, *v1++, lerp_info.weight);
 *
 *             changed |= (new_val != *dst);
 *             *dst++ = new_val;
 *         }
 *
 *         return changed;
 *     }
 *
 *     const std::vector<float> fStorage;
 *     const size_t             fVecLen;
 *
 *     std::vector<float>*      fTarget;
 *
 *     using INHERITED = KeyframeAnimator;
 * }
 * ```
 */
public class VectorKeyframeAnimator public constructor(
  kfs: List<Keyframe>,
  cms: List<SkCubicMap>,
  storage: List<Float>,
  vecLen: ULong,
  targetValue: List<Float>?,
) : KeyframeAnimator(TODO(), TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const std::vector<float> fStorage
   * ```
   */
  private val fStorage: Int = TODO("Initialize fStorage")

  /**
   * C++ original:
   * ```cpp
   * const size_t             fVecLen
   * ```
   */
  private val fVecLen: ULong = TODO("Initialize fVecLen")

  /**
   * C++ original:
   * ```cpp
   * std::vector<float>*      fTarget
   * ```
   */
  private var fTarget: Int? = TODO("Initialize fTarget")

  /**
   * C++ original:
   * ```cpp
   * StateChanged onSeek(float t) override {
   *         const auto& lerp_info = this->getLERPInfo(t);
   *
   *         SkASSERT(lerp_info.vrec0.idx + fVecLen <= fStorage.size());
   *         SkASSERT(lerp_info.vrec1.idx + fVecLen <= fStorage.size());
   *         SkASSERT(fTarget->size() == fVecLen);
   *
   *         const auto* v0  = fStorage.data() + lerp_info.vrec0.idx;
   *         const auto* v1  = fStorage.data() + lerp_info.vrec1.idx;
   *               auto* dst = fTarget->data();
   *
   *         const auto is_constant = lerp_info.vrec0.equals(lerp_info.vrec1,
   *                                                         Keyframe::Value::Type::kIndex);
   *         if (is_constant) {
   *             if (0 != std::memcmp(dst, v0, fVecLen * sizeof(float))) {
   *                 std::copy(v0, v0 + fVecLen, dst);
   *                 return true;
   *             }
   *             return false;
   *         }
   *
   *         size_t count = fVecLen;
   *         bool changed = false;
   *
   *         while (count >= 4) {
   *             const auto old_val = skvx::float4::Load(dst),
   *                        new_val = Lerp(skvx::float4::Load(v0),
   *                                       skvx::float4::Load(v1),
   *                                       lerp_info.weight);
   *
   *             changed |= any(new_val != old_val);
   *             new_val.store(dst);
   *
   *             v0    += 4;
   *             v1    += 4;
   *             dst   += 4;
   *             count -= 4;
   *         }
   *
   *         while (count-- > 0) {
   *             const auto new_val = Lerp(*v0++, *v1++, lerp_info.weight);
   *
   *             changed |= (new_val != *dst);
   *             *dst++ = new_val;
   *         }
   *
   *         return changed;
   *     }
   * ```
   */
  public override fun onSeek(t: Float): StateChanged {
    TODO("Implement onSeek")
  }
}
