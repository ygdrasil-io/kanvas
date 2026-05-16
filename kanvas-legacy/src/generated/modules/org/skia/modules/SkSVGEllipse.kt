package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkPathFillType
import org.skia.math.SkRect
import undefined.Cx

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGEllipse final : public SkSVGShape {
 * public:
 *     static sk_sp<SkSVGEllipse> Make() { return sk_sp<SkSVGEllipse>(new SkSVGEllipse()); }
 *
 *     SVG_ATTR(Cx, SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Cy, SkSVGLength, SkSVGLength(0))
 *
 *     SVG_OPTIONAL_ATTR(Rx, SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Ry, SkSVGLength)
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
 *     SkSVGEllipse();
 *
 *     SkRect resolve(const SkSVGLengthContext&) const;
 *
 *     using INHERITED = SkSVGShape;
 * }
 * ```
 */
public class SkSVGEllipse public constructor() : SkSVGShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Cx, SkSVGLength, SkSVGLength(0))
   * ```
   */
  public fun svgATTR(
    param0: Cx,
    param1: SkSVGLength,
    param2: (Int) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGEllipse::onDraw(SkCanvas* canvas, const SkSVGLengthContext& lctx,
   *                           const SkPaint& paint, SkPathFillType) const {
   *     canvas->drawOval(this->resolve(lctx), paint);
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
   * SkPath SkSVGEllipse::onAsPath(const SkSVGRenderContext& ctx) const {
   *     return this->mapToParent(SkPath::Oval(this->resolve(ctx.lengthContext())));
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGEllipse::resolve(const SkSVGLengthContext& lctx) const {
   *     const auto cx = lctx.resolve(fCx, SkSVGLengthContext::LengthType::kHorizontal);
   *     const auto cy = lctx.resolve(fCy, SkSVGLengthContext::LengthType::kVertical);
   *
   *     // https://www.w3.org/TR/SVG2/shapes.html#EllipseElement
   *     //
   *     // An auto value for either rx or ry is converted to a used value, following the rules given
   *     // above for rectangles (but without any clamping based on width or height).
   *     const auto [ rx, ry ] = ResolveOptionalRadii(fRx, fRy, lctx);
   *
   *     // A computed value of zero for either dimension, or a computed value of auto for both
   *     // dimensions, disables rendering of the element.
   *     return (rx > 0 && ry > 0)
   *         ? SkRect::MakeXYWH(cx - rx, cy - ry, rx * 2, ry * 2)
   *         : SkRect::MakeEmpty();
   * }
   * ```
   */
  private fun resolve(lctx: SkSVGLengthContext): SkRect {
    TODO("Implement resolve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGEllipse::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setCx(SkSVGAttributeParser::parse<SkSVGLength>("cx", n, v)) ||
   *            this->setCy(SkSVGAttributeParser::parse<SkSVGLength>("cy", n, v)) ||
   *            this->setRx(SkSVGAttributeParser::parse<SkSVGLength>("rx", n, v)) ||
   *            this->setRy(SkSVGAttributeParser::parse<SkSVGLength>("ry", n, v));
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
     * static sk_sp<SkSVGEllipse> Make() { return sk_sp<SkSVGEllipse>(new SkSVGEllipse()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
