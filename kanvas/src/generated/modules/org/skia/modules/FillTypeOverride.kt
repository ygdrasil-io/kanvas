package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkPathFillType
import undefined.FillType

/**
 * C++ original:
 * ```cpp
 * class FillTypeOverride final : public GeometryEffect {
 * public:
 *     static sk_sp<FillTypeOverride> Make(sk_sp<GeometryNode> child, SkPathFillType ft) {
 *         return child ? sk_sp<FillTypeOverride>(new FillTypeOverride(std::move(child), ft))
 *                      : nullptr;
 *     }
 *
 *     SG_ATTRIBUTE(FillType, SkPathFillType, fFillType)
 *
 * private:
 *     FillTypeOverride(sk_sp<GeometryNode> child, SkPathFillType ft)
 *         : INHERITED(std::move(child))
 *         , fFillType(ft)
 *     {}
 *
 *     SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) override;
 *
 *     SkPathFillType fFillType = SkPathFillType::kDefault;
 *
 *     using INHERITED = GeometryEffect;
 * }
 * ```
 */
public class FillTypeOverride : GeometryEffect() {
  /**
   * C++ original:
   * ```cpp
   * SkPathFillType fFillType
   * ```
   */
  private var fFillType: Int = TODO("Initialize fFillType")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(FillType, SkPathFillType, fFillType)
   * ```
   */
  public fun sgATTRIBUTE(param0: FillType, param1: SkPathFillType): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath FillTypeOverride::onRevalidateEffect(const sk_sp<GeometryNode>& child, const SkMatrix&) {
   *     SkPath path = child->asPath();
   *     path.setFillType(fFillType);
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
     * static sk_sp<FillTypeOverride> Make(sk_sp<GeometryNode> child, SkPathFillType ft) {
     *         return child ? sk_sp<FillTypeOverride>(new FillTypeOverride(std::move(child), ft))
     *                      : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<GeometryNode>, ft: SkPathFillType): Int {
      TODO("Implement make")
    }
  }
}
