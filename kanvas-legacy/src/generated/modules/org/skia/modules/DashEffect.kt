package org.skia.modules

import kotlin.Float
import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import undefined.Intervals

/**
 * C++ original:
 * ```cpp
 * class DashEffect final : public GeometryEffect {
 * public:
 *     static sk_sp<DashEffect> Make(sk_sp<GeometryNode> child) {
 *         return child ? sk_sp<DashEffect>(new DashEffect(std::move(child))) : nullptr;
 *     }
 *
 *     SG_ATTRIBUTE(Intervals, std::vector<float>, fIntervals)
 *     SG_ATTRIBUTE(Phase,                 float , fPhase    )
 *
 * private:
 *     explicit DashEffect(sk_sp<GeometryNode> child) : INHERITED(std::move(child)) {}
 *
 *     SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) override;
 *
 *     std::vector<float> fIntervals;
 *     float              fPhase = 0;
 *
 *     using INHERITED = GeometryEffect;
 * }
 * ```
 */
public class DashEffect : GeometryEffect() {
  /**
   * C++ original:
   * ```cpp
   * std::vector<float> fIntervals
   * ```
   */
  private var fIntervals: Int = TODO("Initialize fIntervals")

  /**
   * C++ original:
   * ```cpp
   * float              fPhase = 0
   * ```
   */
  private var fPhase: Float = TODO("Initialize fPhase")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Intervals, std::vector<float>, fIntervals)
   * ```
   */
  public fun sgATTRIBUTE(param0: Intervals, param1: List<Float>): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath DashEffect::onRevalidateEffect(const sk_sp<GeometryNode>& child, const SkMatrix&) {
   *     SkPath path = child->asPath();
   *
   *     if (const auto dash_patheffect = make_dash(fIntervals, fPhase)) {
   *         SkStrokeRec rec(SkStrokeRec::kHairline_InitStyle);
   *         SkASSERT(!dash_patheffect->needsCTM());
   *         SkPathBuilder builder;
   *         dash_patheffect->filterPath(&builder, path, &rec);
   *         return builder.detach();
   *     }
   *
   *     return path;
   * }
   * ```
   */
  public fun onRevalidateEffect(child: SkSp<GeometryNode>, param1: SkMatrix): SkPath {
    TODO("Implement onRevalidateEffect")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<DashEffect> Make(sk_sp<GeometryNode> child) {
     *         return child ? sk_sp<DashEffect>(new DashEffect(std::move(child))) : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<GeometryNode>): Int {
      TODO("Implement make")
    }
  }
}
