package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkTileMode
import org.skia.math.SkMatrix
import org.skia.math.SkScalar
import undefined.SkColor4f
import undefined.X1

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGLinearGradient final : public SkSVGGradient {
 * public:
 *     static sk_sp<SkSVGLinearGradient> Make() {
 *         return sk_sp<SkSVGLinearGradient>(new SkSVGLinearGradient());
 *     }
 *
 *     SVG_ATTR(X1, SkSVGLength, SkSVGLength(0  , SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Y1, SkSVGLength, SkSVGLength(0  , SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(X2, SkSVGLength, SkSVGLength(100, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Y2, SkSVGLength, SkSVGLength(0  , SkSVGLength::Unit::kPercentage))
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     sk_sp<SkShader> onMakeShader(const SkSVGRenderContext&,
 *                                  const SkColor4f*, const SkScalar*, int count,
 *                                  SkTileMode, const SkMatrix&) const override;
 * private:
 *     SkSVGLinearGradient();
 *
 *     using INHERITED = SkSVGGradient;
 * }
 * ```
 */
public class SkSVGLinearGradient public constructor() : SkSVGGradient(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X1, SkSVGLength, SkSVGLength(0  , SkSVGLength::Unit::kPercentage))
   * ```
   */
  public override fun svgATTR(
    param0: X1,
    param1: SkSVGLength,
    param2: (Int, SkSVGLength.Unit.KPercentage) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkShader> SkSVGLinearGradient::onMakeShader(const SkSVGRenderContext& ctx,
   *                                                   const SkColor4f* colors, const SkScalar* pos,
   *                                                   int count, SkTileMode tm,
   *                                                   const SkMatrix& localMatrix) const {
   *     const SkSVGLengthContext lctx =
   *             this->getGradientUnits().type() == SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox
   *                     ? SkSVGLengthContext({1, 1})
   *                     : ctx.lengthContext();
   *
   *     const auto x1 = lctx.resolve(fX1, SkSVGLengthContext::LengthType::kHorizontal);
   *     const auto y1 = lctx.resolve(fY1, SkSVGLengthContext::LengthType::kVertical);
   *     const auto x2 = lctx.resolve(fX2, SkSVGLengthContext::LengthType::kHorizontal);
   *     const auto y2 = lctx.resolve(fY2, SkSVGLengthContext::LengthType::kVertical);
   *
   *     const SkPoint pts[2] = { {x1, y1}, {x2, y2}};
   *
   *     SkSpan<const float> positions;
   *     if (pos) {
   *         positions = {pos, (size_t)count};
   *     }
   *     SkGradient grad = {{{colors, (size_t)count}, positions, tm, nullptr}, {}};
   *     return SkShaders::LinearGradient(pts, grad, &localMatrix);
   * }
   * ```
   */
  protected override fun onMakeShader(
    ctx: SkSVGRenderContext,
    colors: SkColor4f?,
    pos: SkScalar?,
    count: Int,
    tm: SkTileMode,
    localMatrix: SkMatrix,
  ): Int {
    TODO("Implement onMakeShader")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGLinearGradient::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setX1(SkSVGAttributeParser::parse<SkSVGLength>("x1", name, value)) ||
   *            this->setY1(SkSVGAttributeParser::parse<SkSVGLength>("y1", name, value)) ||
   *            this->setX2(SkSVGAttributeParser::parse<SkSVGLength>("x2", name, value)) ||
   *            this->setY2(SkSVGAttributeParser::parse<SkSVGLength>("y2", name, value));
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
     * static sk_sp<SkSVGLinearGradient> Make() {
     *         return sk_sp<SkSVGLinearGradient>(new SkSVGLinearGradient());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
