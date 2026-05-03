package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.Pair
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkPathFillType
import org.skia.math.SkPoint
import undefined.X1

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGLine final : public SkSVGShape {
 * public:
 *     static sk_sp<SkSVGLine> Make() { return sk_sp<SkSVGLine>(new SkSVGLine()); }
 *
 *     SVG_ATTR(X1, SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Y1, SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(X2, SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Y2, SkSVGLength, SkSVGLength(0))
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     void onDraw(SkCanvas*, const SkSVGLengthContext&, const SkPaint&,
 *                 SkPathFillType) const override;
 *
 *     SkPath onAsPath(const SkSVGRenderContext&) const override;
 *
 * private:
 *     SkSVGLine();
 *
 *     // resolve and return the two endpoints
 *     std::tuple<SkPoint, SkPoint> resolve(const SkSVGLengthContext&) const;
 *
 *     using INHERITED = SkSVGShape;
 * }
 * ```
 */
public class SkSVGLine public constructor() : SkSVGShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X1, SkSVGLength, SkSVGLength(0))
   * ```
   */
  public fun svgATTR(
    param0: X1,
    param1: SkSVGLength,
    param2: (Int) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGLine::onDraw(SkCanvas* canvas, const SkSVGLengthContext& lctx,
   *                        const SkPaint& paint, SkPathFillType) const {
   *     SkPoint p0, p1;
   *     std::tie(p0, p1) = this->resolve(lctx);
   *
   *     canvas->drawLine(p0, p1, paint);
   * }
   * ```
   */
  protected override fun onDraw(
    canvas: SkCanvas?,
    lctx: SkSVGLengthContext,
    paint: SkPaint,
    param3: SkPathFillType,
  ) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGLine::onAsPath(const SkSVGRenderContext& ctx) const {
   *     SkPoint p0, p1;
   *     std::tie(p0, p1) = this->resolve(ctx.lengthContext());
   *
   *     return this->mapToParent(SkPath::Line(p0, p1));
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<SkPoint, SkPoint> SkSVGLine::resolve(const SkSVGLengthContext& lctx) const {
   *     return std::make_tuple(
   *         SkPoint::Make(lctx.resolve(fX1, SkSVGLengthContext::LengthType::kHorizontal),
   *                       lctx.resolve(fY1, SkSVGLengthContext::LengthType::kVertical)),
   *         SkPoint::Make(lctx.resolve(fX2, SkSVGLengthContext::LengthType::kHorizontal),
   *                       lctx.resolve(fY2, SkSVGLengthContext::LengthType::kVertical)));
   * }
   * ```
   */
  private fun resolve(lctx: SkSVGLengthContext): Pair<SkPoint, SkPoint> {
    TODO("Implement resolve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGLine::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setX1(SkSVGAttributeParser::parse<SkSVGLength>("x1", n, v)) ||
   *            this->setY1(SkSVGAttributeParser::parse<SkSVGLength>("y1", n, v)) ||
   *            this->setX2(SkSVGAttributeParser::parse<SkSVGLength>("x2", n, v)) ||
   *            this->setY2(SkSVGAttributeParser::parse<SkSVGLength>("y2", n, v));
   * }
   * ```
   */
  public fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGLine> Make() { return sk_sp<SkSVGLine>(new SkSVGLine()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
