package org.skia.modules

import kotlin.Int
import kotlin.collections.List
import org.skia.foundation.SkShader
import org.skia.foundation.SkSp
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import undefined.SkColor4f
import undefined.StartCenter

/**
 * C++ original:
 * ```cpp
 * class RadialGradient final : public Gradient {
 * public:
 *     static sk_sp<RadialGradient> Make() {
 *         return sk_sp<RadialGradient>(new RadialGradient());
 *     }
 *
 *     SG_ATTRIBUTE(StartCenter, SkPoint , fStartCenter)
 *     SG_ATTRIBUTE(EndCenter  , SkPoint , fEndCenter  )
 *     SG_ATTRIBUTE(StartRadius, SkScalar, fStartRadius)
 *     SG_ATTRIBUTE(EndRadius  , SkScalar, fEndRadius  )
 *
 * protected:
 *     sk_sp<SkShader> onMakeShader(const std::vector<SkColor4f>&,
 *                                  const std::vector<SkScalar >&) const override;
 *
 * private:
 *     RadialGradient() = default;
 *
 *     SkPoint  fStartCenter = SkPoint::Make(0, 0),
 *              fEndCenter   = SkPoint::Make(0, 0);
 *     SkScalar fStartRadius = 0,
 *              fEndRadius   = 0;
 *
 *     using INHERITED = Gradient;
 * }
 * ```
 */
public class RadialGradient public constructor() : Gradient() {
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fStartCenter
   * ```
   */
  private var fStartCenter: Int = TODO("Initialize fStartCenter")

  /**
   * C++ original:
   * ```cpp
   * SkPoint  fStartCenter = SkPoint::Make(0, 0),
   *              fEndCenter
   * ```
   */
  private var fEndCenter: Int = TODO("Initialize fEndCenter")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fStartRadius
   * ```
   */
  private var fStartRadius: Int = TODO("Initialize fStartRadius")

  /**
   * C++ original:
   * ```cpp
   * SkScalar fStartRadius = 0,
   *              fEndRadius
   * ```
   */
  private var fEndRadius: Int = TODO("Initialize fEndRadius")

  /**
   * C++ original:
   * ```cpp
   * SG_ATTRIBUTE(StartCenter, SkPoint , fStartCenter)
   * ```
   */
  public fun sgATTRIBUTE(param0: StartCenter, param1: SkPoint): Int {
    TODO("Implement sgATTRIBUTE")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> RadialGradient::onMakeShader(const std::vector<SkColor4f>& colors,
   *                                              const std::vector<SkScalar >& positions) const {
   *     SkASSERT(colors.size() == positions.size());
   *     SkGradient grad = {{colors, positions, this->getTileMode()}, {}};
   *
   *     return (fStartRadius <= 0 && fStartCenter == fEndCenter)
   *         ? SkShaders::RadialGradient(fEndCenter, fEndRadius, grad)
   *         : SkShaders::TwoPointConicalGradient(fStartCenter, fStartRadius, fEndCenter, fEndRadius,
   *                                              grad);
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
     * static sk_sp<RadialGradient> Make() {
     *         return sk_sp<RadialGradient>(new RadialGradient());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
