package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.math.SkPathFillType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGRect final : public SkSVGShape {
 * public:
 *     static sk_sp<SkSVGRect> Make() { return sk_sp<SkSVGRect>(new SkSVGRect()); }
 *
 *     SVG_ATTR(X     , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Y     , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Width , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Height, SkSVGLength, SkSVGLength(0))
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
 *     SkRect onTransformableObjectBoundingBox(const SkSVGRenderContext&) const override;
 *
 * private:
 *     SkSVGRect();
 *
 *     SkRRect resolve(const SkSVGLengthContext&) const;
 *
 *     using INHERITED = SkSVGShape;
 * }
 * ```
 */
public class SkSVGRect public constructor() : SkSVGShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X     , SkSVGLength, SkSVGLength(0))
   * ```
   */
  public fun svgATTR(
    param0: X,
    param1: SkSVGLength,
    param2: (Int) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGRect::onDraw(SkCanvas* canvas, const SkSVGLengthContext& lctx,
   *                        const SkPaint& paint, SkPathFillType) const {
   *     canvas->drawRRect(this->resolve(lctx), paint);
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
   * SkPath SkSVGRect::onAsPath(const SkSVGRenderContext& ctx) const {
   *     return this->mapToParent(SkPath::RRect(this->resolve(ctx.lengthContext())));
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGRect::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     return ctx.lengthContext().resolveRect(fX, fY, fWidth, fHeight);
   * }
   * ```
   */
  protected override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): SkRRect {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRRect SkSVGRect::resolve(const SkSVGLengthContext& lctx) const {
   *     const auto rect = lctx.resolveRect(fX, fY, fWidth, fHeight);
   *     const auto [ rx, ry ] = ResolveOptionalRadii(fRx, fRy, lctx);
   *
   *     // https://www.w3.org/TR/SVG2/shapes.html#RectElement
   *     // ...
   *     // 3. Finally, apply clamping to generate the used values:
   *     //     1. If the absolute rx (after the above steps) is greater than half of the used width,
   *     //        then the used value of rx is half of the used width.
   *     //     2. If the absolute ry (after the above steps) is greater than half of the used height,
   *     //        then the used value of ry is half of the used height.
   *     //     3. Otherwise, the used values of rx and ry are the absolute values computed previously.
   *
   *     return SkRRect::MakeRectXY(rect,
   *                                std::min(rx, rect.width() / 2),
   *                                std::min(ry, rect.height() / 2));
   * }
   * ```
   */
  private fun resolve(lctx: SkSVGLengthContext): SkRRect {
    TODO("Implement resolve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGRect::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGLength>("x", n, v)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGLength>("y", n, v)) ||
   *            this->setWidth(SkSVGAttributeParser::parse<SkSVGLength>("width", n, v)) ||
   *            this->setHeight(SkSVGAttributeParser::parse<SkSVGLength>("height", n, v)) ||
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
     * static sk_sp<SkSVGRect> Make() { return sk_sp<SkSVGRect>(new SkSVGRect()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
