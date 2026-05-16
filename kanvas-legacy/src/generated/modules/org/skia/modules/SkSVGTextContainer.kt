package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkSVGTextContainer : public SkSVGTextFragment {
 * public:
 *     SVG_ATTR(X, std::vector<SkSVGLength>, {})
 *     SVG_ATTR(Y, std::vector<SkSVGLength>, {})
 *     SVG_ATTR(Dx, std::vector<SkSVGLength>, {})
 *     SVG_ATTR(Dy, std::vector<SkSVGLength>, {})
 *     SVG_ATTR(Rotate, std::vector<SkSVGNumberType>, {})
 *
 *     SVG_ATTR(XmlSpace, SkSVGXmlSpace, SkSVGXmlSpace::kDefault)
 *
 *     void appendChild(sk_sp<SkSVGNode>) final;
 *
 * protected:
 *     explicit SkSVGTextContainer(SkSVGTag t) : INHERITED(t) {}
 *
 *     void onShapeText(const SkSVGRenderContext&, SkSVGTextContext*, SkSVGXmlSpace) const override;
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     std::vector<sk_sp<SkSVGTextFragment>> fChildren;
 *
 *     using INHERITED = SkSVGTextFragment;
 * }
 * ```
 */
public open class SkSVGTextContainer public constructor(
  t: SkSVGTag,
) : SkSVGTextFragment(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * std::vector<sk_sp<SkSVGTextFragment>> fChildren
   * ```
   */
  private var fChildren: Int = TODO("Initialize fChildren")

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X, std::vector<SkSVGLength>, {})
   * ```
   */
  public fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContainer::onShapeText(const SkSVGRenderContext& ctx, SkSVGTextContext* tctx,
   *                                      SkSVGXmlSpace) const {
   *     SkASSERT(tctx);
   *
   *     const SkSVGTextContext::ScopedPosResolver resolver(*this, ctx.lengthContext(), tctx);
   *
   *     for (const auto& frag : fChildren) {
   *         // Containers always override xml:space with the local value.
   *         frag->renderText(ctx, tctx, this->getXmlSpace());
   *     }
   * }
   * ```
   */
  protected override fun onShapeText(
    ctx: SkSVGRenderContext,
    tctx: SkSVGTextContext?,
    param2: SkSVGXmlSpace,
  ) {
    TODO("Implement onShapeText")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGTextContainer::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setX(SkSVGAttributeParser::parse<std::vector<SkSVGLength>>("x", name, value)) ||
   *            this->setY(SkSVGAttributeParser::parse<std::vector<SkSVGLength>>("y", name, value)) ||
   *            this->setDx(SkSVGAttributeParser::parse<std::vector<SkSVGLength>>("dx", name, value)) ||
   *            this->setDy(SkSVGAttributeParser::parse<std::vector<SkSVGLength>>("dy", name, value)) ||
   *            this->setRotate(SkSVGAttributeParser::parse<std::vector<SkSVGNumberType>>("rotate",
   *                                                                                      name,
   *                                                                                      value)) ||
   *            this->setXmlSpace(SkSVGAttributeParser::parse<SkSVGXmlSpace>("xml:space", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGTextContainer::appendChild(sk_sp<SkSVGNode> child) {
   *     // Only allow text content child nodes.
   *     switch (child->tag()) {
   *     case SkSVGTag::kTextLiteral:
   *     case SkSVGTag::kTextPath:
   *     case SkSVGTag::kTSpan:
   *         fChildren.push_back(
   *             sk_sp<SkSVGTextFragment>(static_cast<SkSVGTextFragment*>(child.release())));
   *         break;
   *     default:
   *         break;
   *     }
   * }
   * ```
   */
  public fun appendChild(child: SkSp<SkSVGNode>) {
    TODO("Implement appendChild")
  }
}

public typealias SkSVGTextINHERITED = SkSVGTextContainer

public typealias SkSVGTSpanINHERITED = SkSVGTextContainer

public typealias SkSVGTextPathINHERITED = SkSVGTextContainer
