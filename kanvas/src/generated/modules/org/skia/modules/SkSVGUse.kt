package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGUse final : public SkSVGTransformableNode {
 * public:
 *     static sk_sp<SkSVGUse> Make() { return sk_sp<SkSVGUse>(new SkSVGUse()); }
 *
 *     void appendChild(sk_sp<SkSVGNode>) override;
 *
 *     SVG_ATTR(X   , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Y   , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Href, SkSVGIRI   , SkSVGIRI())
 *
 * protected:
 *     bool onPrepareToRender(SkSVGRenderContext*) const override;
 *     void onRender(const SkSVGRenderContext&) const override;
 *     SkPath onAsPath(const SkSVGRenderContext&) const override;
 *     SkRect onTransformableObjectBoundingBox(const SkSVGRenderContext&) const override;
 *
 * private:
 *     SkSVGUse();
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     using INHERITED = SkSVGTransformableNode;
 * }
 * ```
 */
public class SkSVGUse public constructor() : SkSVGTransformableNode(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * void SkSVGUse::appendChild(sk_sp<SkSVGNode>) {
   *     SkDEBUGF("cannot append child nodes to this element.\n");
   * }
   * ```
   */
  public override fun appendChild(param0: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X   , SkSVGLength, SkSVGLength(0))
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
   * void SkSVGUse::onRender(const SkSVGRenderContext& ctx) const {
   *     const auto ref = ctx.findNodeById(fHref);
   *     if (!ref) {
   *         return;
   *     }
   *
   *     ref->render(ctx);
   * }
   * ```
   */
  protected override fun onRender(ctx: SkSVGRenderContext) {
    TODO("Implement onRender")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGUse::onAsPath(const SkSVGRenderContext& ctx) const {
   *     const auto ref = ctx.findNodeById(fHref);
   *     if (!ref) {
   *         return SkPath();
   *     }
   *
   *     return ref->asPath(ctx);
   * }
   * ```
   */
  protected override fun onAsPath(ctx: SkSVGRenderContext): Int {
    TODO("Implement onAsPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGUse::onTransformableObjectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     const auto ref = ctx.findNodeById(fHref);
   *     if (!ref) {
   *         return SkRect::MakeEmpty();
   *     }
   *
   *     const SkSVGLengthContext& lctx = ctx.lengthContext();
   *     const SkScalar x = lctx.resolve(fX, SkSVGLengthContext::LengthType::kHorizontal);
   *     const SkScalar y = lctx.resolve(fY, SkSVGLengthContext::LengthType::kVertical);
   *
   *     SkRect bounds = ref->objectBoundingBox(ctx);
   *     bounds.offset(x, y);
   *
   *     return bounds;
   * }
   * ```
   */
  protected override fun onTransformableObjectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement onTransformableObjectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGUse::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGLength>("x", n, v)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGLength>("y", n, v)) ||
   *            this->setHref(SkSVGAttributeParser::parse<SkSVGIRI>("xlink:href", n, v));
   * }
   * ```
   */
  public override fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGUse::onPrepareToRender(SkSVGRenderContext* ctx) const {
   *     if (fHref.iri().isEmpty() || !INHERITED::onPrepareToRender(ctx)) {
   *         return false;
   *     }
   *
   *     if (fX.value() || fY.value()) {
   *         // Restored when the local SkSVGRenderContext leaves scope.
   *         ctx->saveOnce();
   *         ctx->canvas()->translate(fX.value(), fY.value());
   *     }
   *
   *     // TODO: width/height override for <svg> targets.
   *
   *     return true;
   * }
   * ```
   */
  public override fun onPrepareToRender(ctx: SkSVGRenderContext?): Boolean {
    TODO("Implement onPrepareToRender")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGUse> Make() { return sk_sp<SkSVGUse>(new SkSVGUse()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
