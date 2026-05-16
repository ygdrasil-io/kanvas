package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkPathFillType
import undefined.Cx

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGCircle final : public SkSVGShape {
 * public:
 *     static sk_sp<SkSVGCircle> Make() { return sk_sp<SkSVGCircle>(new SkSVGCircle()); }
 *
 *     SVG_ATTR(Cx, SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Cy, SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(R , SkSVGLength, SkSVGLength(0))
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
 *     SkSVGCircle();
 *
 *     // resolve and return the center and radius values
 *     std::tuple<SkPoint, SkScalar> resolve(const SkSVGLengthContext&) const;
 *
 *     using INHERITED = SkSVGShape;
 * }
 * ```
 */
public class SkSVGCircle public constructor() : SkSVGShape(TODO()) {
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
   * void SkSVGCircle::onDraw(SkCanvas* canvas, const SkSVGLengthContext& lctx,
   *                          const SkPaint& paint, SkPathFillType) const {
   *     SkPoint pos;
   *     SkScalar r;
   *     std::tie(pos, r) = this->resolve(lctx);
   *
   *     if (r > 0) {
   *         canvas->drawCircle(pos.x(), pos.y(), r, paint);
   *     }
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
   * SkPath SkSVGCircle::onAsPath(const SkSVGRenderContext& ctx) const {
   *     SkPoint pos;
   *     SkScalar r;
   *     std::tie(pos, r) = this->resolve(ctx.lengthContext());
   *
   *     return this->mapToParent(SkPath::Circle(pos.x(), pos.y(), r));
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGCircle::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     const auto [pos, r] = this->resolve(ctx.lengthContext());
   *     return SkRect::MakeXYWH(pos.fX - r, pos.fY - r, 2 * r, 2 * r);
   * }
   * ```
   */
  protected override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<SkPoint, SkScalar> SkSVGCircle::resolve(const SkSVGLengthContext& lctx) const {
   *     const auto cx = lctx.resolve(fCx, SkSVGLengthContext::LengthType::kHorizontal);
   *     const auto cy = lctx.resolve(fCy, SkSVGLengthContext::LengthType::kVertical);
   *     const auto  r = lctx.resolve(fR , SkSVGLengthContext::LengthType::kOther);
   *
   *     return std::make_tuple(SkPoint::Make(cx, cy), r);
   * }
   * ```
   */
  private fun resolve(lctx: SkSVGLengthContext): Int {
    TODO("Implement resolve")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGCircle::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setCx(SkSVGAttributeParser::parse<SkSVGLength>("cx", n, v)) ||
   *            this->setCy(SkSVGAttributeParser::parse<SkSVGLength>("cy", n, v)) ||
   *            this->setR(SkSVGAttributeParser::parse<SkSVGLength>("r", n, v));
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
     * static sk_sp<SkSVGCircle> Make() { return sk_sp<SkSVGCircle>(new SkSVGCircle()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
