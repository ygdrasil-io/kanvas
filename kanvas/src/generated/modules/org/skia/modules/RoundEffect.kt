package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.Radius

/**
 * C++ original:
 * ```cpp
 * class RoundEffect final : public GeometryEffect {
 * public:
 *     static sk_sp<RoundEffect> Make(sk_sp<GeometryNode> child) {
 *         return child ? sk_sp<RoundEffect>(new RoundEffect(std::move(child))) : nullptr;
 *     }
 *
 *     SG_ATTRIBUTE(Radius, SkScalar, fRadius)
 *
 * private:
 *     explicit RoundEffect(sk_sp<GeometryNode> child) : INHERITED(std::move(child)) {}
 *
 *     SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) override;
 *
 *     SkScalar fRadius = 0;
 *
 *     using INHERITED = GeometryEffect;
 * }
 * ```
 */
public class RoundEffect : GeometryEffect() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fRadius
   * ```
   */
  private var fRadius: Int = TODO("Initialize fRadius")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Radius, SkScalar, fRadius)
   * ```
   */
  public fun sgATTRIBUTE(param0: Radius, param1: SkScalar): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath RoundEffect::onRevalidateEffect(const sk_sp<GeometryNode>& child, const SkMatrix&) {
   *     SkPath path = child->asPath();
   *
   *     if (const auto round = SkCornerPathEffect::Make(fRadius)) {
   *         SkStrokeRec rec(SkStrokeRec::kHairline_InitStyle);
   *         SkASSERT(!round->needsCTM());
   *         SkPathBuilder builder;
   *         SkAssertResult(round->filterPath(&builder, path, &rec));
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
     * static sk_sp<RoundEffect> Make(sk_sp<GeometryNode> child) {
     *         return child ? sk_sp<RoundEffect>(new RoundEffect(std::move(child))) : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<GeometryNode>): Int {
      TODO("Implement make")
    }
  }
}
