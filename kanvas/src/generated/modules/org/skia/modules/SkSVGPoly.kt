package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkPathFillType
import undefined.Points
import undefined.SkSVGPointsType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGPoly final : public SkSVGShape {
 * public:
 *     static sk_sp<SkSVGPoly> MakePolygon() {
 *         return sk_sp<SkSVGPoly>(new SkSVGPoly(SkSVGTag::kPolygon));
 *     }
 *
 *     static sk_sp<SkSVGPoly> MakePolyline() {
 *         return sk_sp<SkSVGPoly>(new SkSVGPoly(SkSVGTag::kPolyline));
 *     }
 *
 *     SVG_ATTR(Points, SkSVGPointsType, SkSVGPointsType())
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
 *     explicit SkSVGPoly(SkSVGTag);
 *
 *     mutable SkPath fPath;  // mutated in onDraw(), to apply inherited fill types.
 *
 *     using INHERITED = SkSVGShape;
 * }
 * ```
 */
public class SkSVGPoly public constructor(
  t: SkSVGTag,
) : SkSVGShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * mutable SkPath fPath
   * ```
   */
  private var fPath: Int = TODO("Initialize fPath")

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Points, SkSVGPointsType, SkSVGPointsType())
   * ```
   */
  public fun svgATTR(
    param0: Points,
    param1: SkSVGPointsType,
    param2: () -> SkSVGPointsType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGPoly::onDraw(SkCanvas* canvas, const SkSVGLengthContext&, const SkPaint& paint,
   *                        SkPathFillType fillType) const {
   *     // the passed fillType follows inheritance rules and needs to be applied at draw time.
   *     fPath.setFillType(fillType);
   *     canvas->drawPath(fPath, paint);
   * }
   * ```
   */
  protected override fun onDraw(
    canvas: SkCanvas?,
    param1: SkSVGLengthContext,
    paint: SkPaint,
    fillType: SkPathFillType,
  ) {
    TODO("Implement onDraw")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGPoly::onAsPath(const SkSVGRenderContext& ctx) const {
   *     SkPath path = fPath;
   *
   *     // clip-rule can be inherited and needs to be applied at clip time.
   *     path.setFillType(ctx.presentationContext().fInherited.fClipRule->asFillType());
   *
   *     return this->mapToParent(path);
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGPoly::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     return fPath.getBounds();
   * }
   * ```
   */
  protected override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGPoly::parseAndSetAttribute(const char* n, const char* v) {
   *     if (INHERITED::parseAndSetAttribute(n, v)) {
   *         return true;
   *     }
   *
   *     if (this->setPoints(SkSVGAttributeParser::parse<SkSVGPointsType>("points", n, v))) {
   *         // TODO: we can likely just keep the points array and create the SkPath when needed.
   *         // only polygons are auto-closed
   *         fPath = SkPath::Polygon(fPoints, this->tag() == SkSVGTag::kPolygon);
   *     }
   *
   *     // No other attributes on this node
   *     return false;
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
     * static sk_sp<SkSVGPoly> MakePolygon() {
     *         return sk_sp<SkSVGPoly>(new SkSVGPoly(SkSVGTag::kPolygon));
     *     }
     * ```
     */
    public fun makePolygon(): Int {
      TODO("Implement makePolygon")
    }

    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGPoly> MakePolyline() {
     *         return sk_sp<SkSVGPoly>(new SkSVGPoly(SkSVGTag::kPolyline));
     *     }
     * ```
     */
    public fun makePolyline(): Int {
      TODO("Implement makePolyline")
    }
  }
}
