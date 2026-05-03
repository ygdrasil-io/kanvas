package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.math.SkPathFillType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGPath final : public SkSVGShape {
 * public:
 *     static sk_sp<SkSVGPath> Make() { return sk_sp<SkSVGPath>(new SkSVGPath()); }
 *
 *     SVG_ATTR(Path, SkPath, SkPath())
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
 *     SkSVGPath();
 *
 *     using INHERITED = SkSVGShape;
 * }
 * ```
 */
public class SkSVGPath public constructor() : SkSVGShape(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Path, SkPath, SkPath())
   * ```
   */
  public fun svgATTR(
    param0: Path,
    param1: SkPath,
    param2: () -> SkPath,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGPath::onDraw(SkCanvas* canvas, const SkSVGLengthContext&, const SkPaint& paint,
   *                        SkPathFillType fillType) const {
   *     // the passed fillType follows inheritance rules and needs to be applied at draw time.
   *     SkPath path = fPath;  // Note: point and verb data are CoW
   *     path.setFillType(fillType);
   *     canvas->drawPath(path, paint);
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
   * SkPath SkSVGPath::onAsPath(const SkSVGRenderContext& ctx) const {
   *     SkPath path = fPath;
   *     // clip-rule can be inherited and needs to be applied at clip time.
   *     path.setFillType(ctx.presentationContext().fInherited.fClipRule->asFillType());
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
   * SkRect SkSVGPath::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     return fPath.computeTightBounds();
   * }
   * ```
   */
  protected override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGPath::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setPath(SkSVGAttributeParser::parse<SkPath>("d", n, v));
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
     * static sk_sp<SkSVGPath> Make() { return sk_sp<SkSVGPath>(new SkSVGPath()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
