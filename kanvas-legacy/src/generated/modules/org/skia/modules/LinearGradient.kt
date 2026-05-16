package org.skia.modules

import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import undefined.SkColor4f
import undefined.StartPoint

/**
 * C++ original:
 * ```cpp
 * class LinearGradient final : public Gradient {
 * public:
 *     static sk_sp<LinearGradient> Make() {
 *         return sk_sp<LinearGradient>(new LinearGradient());
 *     }
 *
 *     SG_ATTRIBUTE(StartPoint, SkPoint, fStartPoint)
 *     SG_ATTRIBUTE(EndPoint  , SkPoint, fEndPoint  )
 *
 * protected:
 *     sk_sp<SkShader> onMakeShader(const std::vector<SkColor4f>&,
 *                                  const std::vector<SkScalar >&) const override;
 *
 * private:
 *     LinearGradient() = default;
 *
 *     SkPoint fStartPoint = SkPoint::Make(0, 0),
 *             fEndPoint   = SkPoint::Make(0, 0);
 *
 *     using INHERITED = Gradient;
 * }
 * ```
 */
public class LinearGradient public constructor() : Gradient() {
  /**
   * C++ original:
   * ```cpp
   * SkPoint fStartPoint
   * ```
   */
  private var fStartPoint: Int = TODO("Initialize fStartPoint")

  /**
   * C++ original:
   * ```cpp
   * SkPoint fStartPoint = SkPoint::Make(0, 0),
   *             fEndPoint
   * ```
   */
  private var fEndPoint: Int = TODO("Initialize fEndPoint")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(StartPoint, SkPoint, fStartPoint)
   * ```
   */
  public fun sgATTRIBUTE(param0: StartPoint, param1: SkPoint): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> LinearGradient::onMakeShader(const std::vector<SkColor4f>& colors,
   *                                              const std::vector<SkScalar >& positions) const {
   *     SkASSERT(colors.size() == positions.size());
   *
   *     const SkPoint pts[] = { fStartPoint, fEndPoint };
   *     return SkShaders::LinearGradient(pts, {{colors, positions, this->getTileMode()}, {}});
   * }
   * ```
   */
  public override fun onMakeShader(colors: List<SkColor4f>, positions: List<SkScalar>): SkSp<SkShader> {
    TODO("Implement onMakeShader")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<LinearGradient> Make() {
     *         return sk_sp<LinearGradient>(new LinearGradient());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
