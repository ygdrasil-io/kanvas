package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.Offset

/**
 * C++ original:
 * ```cpp
 * class OffsetEffect final : public GeometryEffect {
 * public:
 *     static sk_sp<OffsetEffect> Make(sk_sp<GeometryNode> child) {
 *         return child ? sk_sp<OffsetEffect>(new OffsetEffect(std::move(child))) : nullptr;
 *     }
 *
 *     SG_ATTRIBUTE(Offset     , SkScalar     , fOffset    )
 *     SG_ATTRIBUTE(MiterLimit , SkScalar     , fMiterLimit)
 *     SG_ATTRIBUTE(Join       , SkPaint::Join, fJoin      )
 *
 * private:
 *     explicit OffsetEffect(sk_sp<GeometryNode> child) : INHERITED(std::move(child)) {}
 *
 *     SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) override;
 *
 *     SkScalar fOffset     = 0,
 *              fMiterLimit = 4;
 *     SkPaint::Join  fJoin = SkPaint::kMiter_Join;
 *
 *     using INHERITED = GeometryEffect;
 * }
 * ```
 */
public class OffsetEffect : GeometryEffect() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar fOffset
   * ```
   */
  private var fOffset: Int = TODO("Initialize fOffset")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fOffset     = 0,
   *              fMiterLimit
   * ```
   */
  private var fMiterLimit: Int = TODO("Initialize fMiterLimit")

  /**
   * C++ original:
   * ```cpp
   * SkPaint::Join  fJoin
   * ```
   */
  private var fJoin: Int = TODO("Initialize fJoin")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Offset     , SkScalar     , fOffset    )
   * ```
   */
  public fun sgATTRIBUTE(param0: Offset, param1: SkScalar): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath OffsetEffect::onRevalidateEffect(const sk_sp<GeometryNode>& child, const SkMatrix& ctm) {
   *     SkPath path = child->asPath();
   *
   *     if (!SkScalarNearlyZero(fOffset)) {
   *         // Clamp the offset value in device space, to avoid overwhelming pathops.
   *         static constexpr float kMaxDevOffset = 100000;
   *         const float min_scale = ctm.getMinScale(),
   *                max_abs_offset = min_scale < 0 ? kMaxDevOffset : kMaxDevOffset / min_scale,
   *                    abs_offset = std::min(max_abs_offset, std::abs(fOffset));
   *
   *         SkPaint paint;
   *         paint.setStyle(SkPaint::kStroke_Style);
   *         paint.setStrokeWidth(abs_offset * 2);
   *         paint.setStrokeMiter(fMiterLimit);
   *         paint.setStrokeJoin(fJoin);
   *
   *         SkPath fill_path = skpathutils::FillPathWithPaint(path, paint);
   *
   *         SkPathOp op = fOffset > 0 ? kUnion_SkPathOp
   *                                   : kDifference_SkPathOp;
   *         if (auto result = Op(path, fill_path, op)) {
   *             path = *result;
   *         }
   *
   *         // TODO: this seems to break path combining (winding mismatch?)
   *         // Simplify(path, &path);
   *     }
   *
   *     return path;
   * }
   * ```
   */
  public fun onRevalidateEffect(child: SkSp<GeometryNode>, ctm: SkMatrix): SkPath {
    TODO("Implement onRevalidateEffect")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<OffsetEffect> Make(sk_sp<GeometryNode> child) {
     *         return child ? sk_sp<OffsetEffect>(new OffsetEffect(std::move(child))) : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<GeometryNode>): Int {
      TODO("Implement make")
    }
  }
}
