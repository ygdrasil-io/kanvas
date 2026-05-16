package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkTileMode
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.Cx
import undefined.SkColor4f

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGRadialGradient final : public SkSVGGradient {
 * public:
 *     static sk_sp<SkSVGRadialGradient> Make() {
 *         return sk_sp<SkSVGRadialGradient>(new SkSVGRadialGradient());
 *     }
 *
 *     SVG_ATTR(Cx, SkSVGLength, SkSVGLength(50, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Cy, SkSVGLength, SkSVGLength(50, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(R,  SkSVGLength, SkSVGLength(50, SkSVGLength::Unit::kPercentage))
 *     SVG_OPTIONAL_ATTR(Fx, SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Fy, SkSVGLength)
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     sk_sp<SkShader> onMakeShader(const SkSVGRenderContext&,
 *                                  const SkColor4f*, const SkScalar*, int count,
 *                                  SkTileMode, const SkMatrix&) const override;
 * private:
 *     SkSVGRadialGradient();
 *
 *     using INHERITED = SkSVGGradient;
 * }
 * ```
 */
public class SkSVGRadialGradient public constructor() : SkSVGGradient(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Cx, SkSVGLength, SkSVGLength(50, SkSVGLength::Unit::kPercentage))
   * ```
   */
  public override fun svgATTR(
    param0: Cx,
    param1: SkSVGLength,
    param2: (Int, SkSVGLength.Unit.KPercentage) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkSVGRadialGradient::onMakeShader(const SkSVGRenderContext& ctx,
   *                                                   const SkColor4f* colors, const SkScalar* pos,
   *                                                   int count, SkTileMode tm,
   *                                                   const SkMatrix& m) const {
   *     const SkSVGLengthContext lctx =
   *             this->getGradientUnits().type() == SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox
   *                     ? SkSVGLengthContext({1, 1})
   *                     : ctx.lengthContext();
   *
   *     const auto      r = lctx.resolve(fR , SkSVGLengthContext::LengthType::kOther);
   *     const auto center = SkPoint::Make(
   *             lctx.resolve(fCx, SkSVGLengthContext::LengthType::kHorizontal),
   *             lctx.resolve(fCy, SkSVGLengthContext::LengthType::kVertical));
   *     const auto  focal = SkPoint::Make(
   *         fFx.has_value() ? lctx.resolve(*fFx, SkSVGLengthContext::LengthType::kHorizontal)
   *                         : center.x(),
   *         fFy.has_value() ? lctx.resolve(*fFy, SkSVGLengthContext::LengthType::kVertical)
   *                         : center.y());
   *
   *     if (r == 0) {
   *         const auto last_color = count > 0 ? colors[count - 1] : SkColors::kBlack;
   *         return SkShaders::Color(last_color, nullptr);
   *     }
   *
   *     SkSpan<const float> positions;
   *     if (pos) {
   *         positions = {pos, (size_t)count};
   *     }
   *     SkGradient grad = {{{colors, (size_t)count}, positions, tm, nullptr}, {}};
   *     return center == focal
   *         ? SkShaders::RadialGradient(center, r, grad, &m)
   *         : SkShaders::TwoPointConicalGradient(focal, 0, center, r, grad, &m);
   * }
   * ```
   */
  protected override fun onMakeShader(
    ctx: SkSVGRenderContext,
    colors: SkColor4f?,
    pos: SkScalar?,
    count: Int,
    tm: SkTileMode,
    m: SkMatrix,
  ): Int {
    TODO("Implement onMakeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGRadialGradient::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setCx(SkSVGAttributeParser::parse<SkSVGLength>("cx", name, value)) ||
   *            this->setCy(SkSVGAttributeParser::parse<SkSVGLength>("cy", name, value)) ||
   *            this->setR(SkSVGAttributeParser::parse<SkSVGLength>("r", name, value)) ||
   *            this->setFx(SkSVGAttributeParser::parse<SkSVGLength>("fx", name, value)) ||
   *            this->setFy(SkSVGAttributeParser::parse<SkSVGLength>("fy", name, value));
   * }
   * ```
   */
  public override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGRadialGradient> Make() {
     *         return sk_sp<SkSVGRadialGradient>(new SkSVGRadialGradient());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
