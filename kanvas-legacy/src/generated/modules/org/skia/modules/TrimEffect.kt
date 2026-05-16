package org.skia.modules

import kotlin.Int
import org.skia.foundation.SkPath
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.Start

/**
 * C++ original:
 * ```cpp
 * class TrimEffect final : public GeometryEffect {
 * public:
 *     static sk_sp<TrimEffect> Make(sk_sp<GeometryNode> child) {
 *         return child ? sk_sp<TrimEffect>(new TrimEffect(std::move(child))) : nullptr;
 *     }
 *
 *     SG_ATTRIBUTE(Start , SkScalar              , fStart )
 *     SG_ATTRIBUTE(Stop  , SkScalar              , fStop  )
 *     SG_ATTRIBUTE(Mode  , SkTrimPathEffect::Mode, fMode  )
 *
 * private:
 *     explicit TrimEffect(sk_sp<GeometryNode> child) : INHERITED(std::move(child)) {}
 *
 *     SkPath onRevalidateEffect(const sk_sp<GeometryNode>&, const SkMatrix&) override;
 *
 *     SkScalar               fStart = 0,
 *                            fStop  = 1;
 *     SkTrimPathEffect::Mode fMode  = SkTrimPathEffect::Mode::kNormal;
 *
 *     using INHERITED = GeometryEffect;
 * }
 * ```
 */
public class TrimEffect : GeometryEffect() {
  /**
   * C++ original:
   * ```cpp
   * SkScalar               fStart
   * ```
   */
  private var fStart: Int = TODO("Initialize fStart")

  /**
   * C++ original:
   * ```cpp
   * SkScalar               fStart = 0,
   *                            fStop
   * ```
   */
  private var fStop: Int = TODO("Initialize fStop")

  /**
   * C++ original:
   * ```cpp
   * SkTrimPathEffect::Mode fMode
   * ```
   */
  private var fMode: Int = TODO("Initialize fMode")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(Start , SkScalar              , fStart )
   * ```
   */
  public fun sgATTRIBUTE(param0: Start, param1: SkScalar): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath TrimEffect::onRevalidateEffect(const sk_sp<GeometryNode>& child, const SkMatrix&) {
   *     SkPath path = child->asPath();
   *
   *     if (const auto trim = SkTrimPathEffect::Make(fStart, fStop, fMode)) {
   *         SkStrokeRec rec(SkStrokeRec::kHairline_InitStyle);
   *         SkASSERT(!trim->needsCTM());
   *         SkPathBuilder builder;
   *         SkAssertResult(trim->filterPath(&builder, path, &rec, nullptr, SkMatrix::I()));
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
     * static sk_sp<TrimEffect> Make(sk_sp<GeometryNode> child) {
     *         return child ? sk_sp<TrimEffect>(new TrimEffect(std::move(child))) : nullptr;
     *     }
     * ```
     */
    public fun make(child: SkSp<GeometryNode>): Int {
      TODO("Implement make")
    }
  }
}
