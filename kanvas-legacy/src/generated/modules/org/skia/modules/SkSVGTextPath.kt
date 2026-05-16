package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkSVGTextPath final : public SkSVGTextContainer {
 * public:
 *     static sk_sp<SkSVGTextPath> Make() { return sk_sp<SkSVGTextPath>(new SkSVGTextPath()); }
 *
 *     SVG_ATTR(Href       , SkSVGIRI   , {}  )
 *     SVG_ATTR(StartOffset, SkSVGLength, SkSVGLength(0))
 *
 * private:
 *     SkSVGTextPath() : INHERITED(SkSVGTag::kTextPath) {}
 *
 *     void onShapeText(const SkSVGRenderContext&, SkSVGTextContext*, SkSVGXmlSpace) const override;
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     using INHERITED = SkSVGTextContainer;
 * }
 * ```
 */
public class SkSVGTextPath : SkSVGTextContainer() {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Href       , SkSVGIRI   , {}  )
   * ```
   */
  public override fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGTextPath::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *         this->setHref(SkSVGAttributeParser::parse<SkSVGIRI>("xlink:href", name, value)) ||
   *         this->setStartOffset(SkSVGAttributeParser::parse<SkSVGLength>("startOffset", name, value));
   * }
   * ```
   */
  public override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextPath::onShapeText(const SkSVGRenderContext& ctx, SkSVGTextContext* parent_tctx,
   *                                  SkSVGXmlSpace xs) const {
   *     SkASSERT(parent_tctx);
   *
   *     // textPath nodes establish a new text layout context.
   *     SkSVGTextContext tctx(ctx, parent_tctx->getCallback(), this);
   *
   *     this->INHERITED::onShapeText(ctx, &tctx, xs);
   * }
   * ```
   */
  public fun onShapeText(
    ctx: SkSVGRenderContext,
    parentTctx: SkSVGTextContext?,
    xs: SkSVGXmlSpace,
  ) {
    TODO("Implement onShapeText")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGTextPath> Make() { return sk_sp<SkSVGTextPath>(new SkSVGTextPath()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
