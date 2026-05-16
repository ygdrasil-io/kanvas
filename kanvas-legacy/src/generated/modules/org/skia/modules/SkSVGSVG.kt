package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkSize

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGSVG : public SkSVGContainer {
 * public:
 *     enum class Type {
 *         kRoot,
 *         kInner,
 *     };
 *     static sk_sp<SkSVGSVG> Make(Type t = Type::kInner) { return sk_sp<SkSVGSVG>(new SkSVGSVG(t)); }
 *
 *     SVG_ATTR(X                  , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Y                  , SkSVGLength, SkSVGLength(0))
 *     SVG_ATTR(Width              , SkSVGLength, SkSVGLength(100, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Height             , SkSVGLength, SkSVGLength(100, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(PreserveAspectRatio, SkSVGPreserveAspectRatio, SkSVGPreserveAspectRatio())
 *
 *     SVG_OPTIONAL_ATTR(ViewBox, SkSVGViewBoxType)
 *
 *     SkSize intrinsicSize(const SkSVGLengthContext&) const;
 *
 *     void renderNode(const SkSVGRenderContext&, const SkSVGIRI& iri) const;
 *
 * protected:
 *     bool onPrepareToRender(SkSVGRenderContext*) const override;
 *
 *     void onSetAttribute(SkSVGAttribute, const SkSVGValue&) override;
 *
 * private:
 *     explicit SkSVGSVG(Type t)
 *         : INHERITED(SkSVGTag::kSvg)
 *         , fType(t)
 *     {}
 *
 *     // Some attributes behave differently for the outermost svg element.
 *     const Type fType;
 *
 *     using INHERITED = SkSVGContainer;
 * }
 * ```
 */
public open class SkSVGSVG public constructor(
  t: Type,
) : SkSVGContainer(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * const Type fType
   * ```
   */
  private val fType: Type = TODO("Initialize fType")

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X                  , SkSVGLength, SkSVGLength(0))
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
   * void SkSVGSVG::renderNode(const SkSVGRenderContext& ctx, const SkSVGIRI& iri) const {
   *     SkSVGRenderContext localContext(ctx, this);
   *     SkSVGRenderContext::BorrowedNode node = localContext.findNodeById(iri);
   *     if (!node) {
   *         return;
   *     }
   *
   *     if (this->onPrepareToRender(&localContext)) {
   *         if (this == node.get()) {
   *             this->onRender(ctx);
   *         } else {
   *             node->render(localContext);
   *         }
   *     }
   * }
   * ```
   */
  public fun renderNode(ctx: SkSVGRenderContext, iri: SkSVGIRI) {
    TODO("Implement renderNode")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGSVG::onPrepareToRender(SkSVGRenderContext* ctx) const {
   *     // x/y are ignored for outermost svg elements
   *     const auto x = fType == Type::kInner ? fX : SkSVGLength(0);
   *     const auto y = fType == Type::kInner ? fY : SkSVGLength(0);
   *
   *     auto viewPortRect  = ctx->lengthContext().resolveRect(x, y, fWidth, fHeight);
   *     auto contentMatrix = SkMatrix::Translate(viewPortRect.x(), viewPortRect.y());
   *     auto viewPort      = SkSize::Make(viewPortRect.width(), viewPortRect.height());
   *
   *     if (fViewBox.has_value()) {
   *         const SkRect& viewBox = *fViewBox;
   *
   *         // An empty viewbox disables rendering.
   *         if (viewBox.isEmpty()) {
   *             return false;
   *         }
   *
   *         // A viewBox overrides the intrinsic viewport.
   *         viewPort = SkSize::Make(viewBox.width(), viewBox.height());
   *
   *         contentMatrix.preConcat(ComputeViewboxMatrix(viewBox, viewPortRect, fPreserveAspectRatio));
   *     }
   *
   *     if (!contentMatrix.isIdentity()) {
   *         ctx->saveOnce();
   *         ctx->canvas()->concat(contentMatrix);
   *     }
   *
   *     if (viewPort != ctx->lengthContext().viewPort()) {
   *         ctx->writableLengthContext()->setViewPort(viewPort);
   *     }
   *
   *     return this->INHERITED::onPrepareToRender(ctx);
   * }
   * ```
   */
  protected override fun onPrepareToRender(ctx: SkSVGRenderContext?): Boolean {
    TODO("Implement onPrepareToRender")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGSVG::onSetAttribute(SkSVGAttribute attr, const SkSVGValue& v) {
   *     switch (attr) {
   *     case SkSVGAttribute::kX:
   *         if (const auto* x = v.as<SkSVGLengthValue>()) {
   *             this->setX(SkSVGLength(*x));
   *         }
   *         break;
   *     case SkSVGAttribute::kY:
   *         if (const auto* y = v.as<SkSVGLengthValue>()) {
   *             this->setY(SkSVGLength(*y));
   *         }
   *         break;
   *     case SkSVGAttribute::kWidth:
   *         if (const auto* w = v.as<SkSVGLengthValue>()) {
   *             this->setWidth(SkSVGLength(*w));
   *         }
   *         break;
   *     case SkSVGAttribute::kHeight:
   *         if (const auto* h = v.as<SkSVGLengthValue>()) {
   *             this->setHeight(SkSVGLength(*h));
   *         }
   *         break;
   *     case SkSVGAttribute::kViewBox:
   *         if (const auto* vb = v.as<SkSVGViewBoxValue>()) {
   *             this->setViewBox(SkSVGViewBoxType(*vb));
   *         }
   *         break;
   *     case SkSVGAttribute::kPreserveAspectRatio:
   *         if (const auto* par = v.as<SkSVGPreserveAspectRatioValue>()) {
   *             this->setPreserveAspectRatio(SkSVGPreserveAspectRatio(*par));
   *         }
   *         break;
   *     default:
   *         this->INHERITED::onSetAttribute(attr, v);
   *     }
   * }
   * ```
   */
  protected override fun onSetAttribute(attr: SkSVGAttribute, v: SkSVGValue) {
    TODO("Implement onSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSize SkSVGSVG::intrinsicSize(const SkSVGLengthContext& lctx) const {
   *     // Percentage values do not provide an intrinsic size.
   *     if (fWidth.unit() == SkSVGLength::Unit::kPercentage ||
   *         fHeight.unit() == SkSVGLength::Unit::kPercentage) {
   *         return SkSize::Make(0, 0);
   *     }
   *
   *     return SkSize::Make(lctx.resolve(fWidth, SkSVGLengthContext::LengthType::kHorizontal),
   *                         lctx.resolve(fHeight, SkSVGLengthContext::LengthType::kVertical));
   * }
   * ```
   */
  public fun intrinsicSize(lctx: SkSVGLengthContext): SkSize {
    TODO("Implement intrinsicSize")
  }

  public enum class Type {
    kRoot,
    kInner,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGSVG> Make(Type t = Type::kInner) { return sk_sp<SkSVGSVG>(new SkSVGSVG(t)); }
     * ```
     */
    public fun make(t: Type = TODO()): Int {
      TODO("Implement make")
    }
  }
}
