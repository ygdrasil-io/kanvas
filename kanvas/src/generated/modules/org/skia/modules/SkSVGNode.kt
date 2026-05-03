package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRefCnt
import org.skia.foundation.SkSp
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGNode : public SkRefCnt {
 * public:
 *     ~SkSVGNode() override;
 *
 *     SkSVGTag tag() const { return fTag; }
 *
 *     virtual void appendChild(sk_sp<SkSVGNode>) = 0;
 *
 *     void render(const SkSVGRenderContext&) const;
 *     bool asPaint(const SkSVGRenderContext&, SkPaint*) const;
 *     SkPath asPath(const SkSVGRenderContext&) const;
 *     SkRect objectBoundingBox(const SkSVGRenderContext&) const;
 *
 *     void setAttribute(SkSVGAttribute, const SkSVGValue&);
 *     bool setAttribute(const char* attributeName, const char* attributeValue);
 *
 *     // TODO: consolidate with existing setAttribute
 *     virtual bool parseAndSetAttribute(const char* name, const char* value);
 *
 *     // inherited
 *     SVG_PRES_ATTR(ClipRule                 , SkSVGFillRule  , true)
 *     SVG_PRES_ATTR(Color                    , SkSVGColorType , true)
 *     SVG_PRES_ATTR(ColorInterpolation       , SkSVGColorspace, true)
 *     SVG_PRES_ATTR(ColorInterpolationFilters, SkSVGColorspace, true)
 *     SVG_PRES_ATTR(FillRule                 , SkSVGFillRule  , true)
 *     SVG_PRES_ATTR(Fill                     , SkSVGPaint     , true)
 *     SVG_PRES_ATTR(FillOpacity              , SkSVGNumberType, true)
 *     SVG_PRES_ATTR(FontFamily               , SkSVGFontFamily, true)
 *     SVG_PRES_ATTR(FontSize                 , SkSVGFontSize  , true)
 *     SVG_PRES_ATTR(FontStyle                , SkSVGFontStyle , true)
 *     SVG_PRES_ATTR(FontWeight               , SkSVGFontWeight, true)
 *     SVG_PRES_ATTR(Stroke                   , SkSVGPaint     , true)
 *     SVG_PRES_ATTR(StrokeDashArray          , SkSVGDashArray , true)
 *     SVG_PRES_ATTR(StrokeDashOffset         , SkSVGLength    , true)
 *     SVG_PRES_ATTR(StrokeLineCap            , SkSVGLineCap   , true)
 *     SVG_PRES_ATTR(StrokeLineJoin           , SkSVGLineJoin  , true)
 *     SVG_PRES_ATTR(StrokeMiterLimit         , SkSVGNumberType, true)
 *     SVG_PRES_ATTR(StrokeOpacity            , SkSVGNumberType, true)
 *     SVG_PRES_ATTR(StrokeWidth              , SkSVGLength    , true)
 *     SVG_PRES_ATTR(TextAnchor               , SkSVGTextAnchor, true)
 *     SVG_PRES_ATTR(Visibility               , SkSVGVisibility, true)
 *
 *     // not inherited
 *     SVG_PRES_ATTR(ClipPath                 , SkSVGFuncIRI   , false)
 *     SVG_PRES_ATTR(Display                  , SkSVGDisplay   , false)
 *     SVG_PRES_ATTR(Mask                     , SkSVGFuncIRI   , false)
 *     SVG_PRES_ATTR(Filter                   , SkSVGFuncIRI   , false)
 *     SVG_PRES_ATTR(Opacity                  , SkSVGNumberType, false)
 *     SVG_PRES_ATTR(StopColor                , SkSVGColor     , false)
 *     SVG_PRES_ATTR(StopOpacity              , SkSVGNumberType, false)
 *     SVG_PRES_ATTR(FloodColor               , SkSVGColor     , false)
 *     SVG_PRES_ATTR(FloodOpacity             , SkSVGNumberType, false)
 *     SVG_PRES_ATTR(LightingColor            , SkSVGColor     , false)
 *
 * protected:
 *     explicit SkSVGNode(SkSVGTag);
 *
 *     static SkMatrix ComputeViewboxMatrix(const SkRect&, const SkRect&, SkSVGPreserveAspectRatio);
 *
 *     // Called before onRender(), to apply local attributes to the context.  Unlike onRender(),
 *     // onPrepareToRender() bubbles up the inheritance chain: overriders should always call
 *     // INHERITED::onPrepareToRender(), unless they intend to short-circuit rendering
 *     // (return false).
 *     // Implementations are expected to return true if rendering is to continue, or false if
 *     // the node/subtree rendering is disabled.
 *     virtual bool onPrepareToRender(SkSVGRenderContext*) const;
 *
 *     virtual void onRender(const SkSVGRenderContext&) const = 0;
 *
 *     virtual bool onAsPaint(const SkSVGRenderContext&, SkPaint*) const { return false; }
 *
 *     virtual SkPath onAsPath(const SkSVGRenderContext&) const = 0;
 *
 *     virtual void onSetAttribute(SkSVGAttribute, const SkSVGValue&) {}
 *
 *     virtual bool hasChildren() const { return false; }
 *
 *     virtual SkRect onObjectBoundingBox(const SkSVGRenderContext&) const {
 *         return SkRect::MakeEmpty();
 *     }
 *
 * private:
 *     SkSVGTag                    fTag;
 *
 *     // FIXME: this should be sparse
 *     SkSVGPresentationAttributes fPresentationAttributes;
 *
 *     using INHERITED = SkRefCnt;
 * }
 * ```
 */
public abstract class SkSVGNode public constructor(
  t: SkSVGTag,
) : SkRefCnt() {
  /**
   * C++ original:
   * ```cpp
   * SkSVGTag                    fTag
   * ```
   */
  private var fTag: SkSVGTag = TODO("Initialize fTag")

  /**
   * C++ original:
   * ```cpp
   * SkSVGPresentationAttributes fPresentationAttributes
   * ```
   */
  private var fPresentationAttributes: Int = TODO("Initialize fPresentationAttributes")

  /**
   * C++ original:
   * ```cpp
   * SkSVGTag tag() const { return fTag; }
   * ```
   */
  public fun tag(): SkSVGTag {
    TODO("Implement tag")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void appendChild(sk_sp<SkSVGNode>) = 0
   * ```
   */
  public abstract fun appendChild(param0: SkSp<SkSVGNode>)

  /**
   * C++ original:
   * ```cpp
   * void SkSVGNode::render(const SkSVGRenderContext& ctx) const {
   *     SkSVGRenderContext localContext(ctx, this);
   *
   *     if (this->onPrepareToRender(&localContext)) {
   *         this->onRender(localContext);
   *     }
   * }
   * ```
   */
  public fun render(ctx: SkSVGRenderContext) {
    TODO("Implement render")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGNode::asPaint(const SkSVGRenderContext& ctx, SkPaint* paint) const {
   *     SkSVGRenderContext localContext(ctx);
   *
   *     return this->onPrepareToRender(&localContext) && this->onAsPaint(localContext, paint);
   * }
   * ```
   */
  public fun asPaint(ctx: SkSVGRenderContext, paint: SkPaint?): Boolean {
    TODO("Implement asPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPath SkSVGNode::asPath(const SkSVGRenderContext& ctx) const {
   *     SkSVGRenderContext localContext(ctx);
   *     if (!this->onPrepareToRender(&localContext)) {
   *         return SkPath();
   *     }
   *
   *     SkPath path = this->onAsPath(localContext);
   *
   *     if (const auto* clipPath = localContext.clipPath()) {
   *         // There is a clip-path present on the current node.
   *         if (auto result = Op(path, *clipPath, kIntersect_SkPathOp)) {
   *             path = *result;
   *         }
   *     }
   *
   *     return path;
   * }
   * ```
   */
  public fun asPath(ctx: SkSVGRenderContext): SkPath {
    TODO("Implement asPath")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGNode::objectBoundingBox(const SkSVGRenderContext& ctx) const {
   *     return this->onObjectBoundingBox(ctx);
   * }
   * ```
   */
  public fun objectBoundingBox(ctx: SkSVGRenderContext): Int {
    TODO("Implement objectBoundingBox")
  }

  /**
   * C++ original:
   * ```cpp
   * void setAttribute(SkSVGAttribute, const SkSVGValue&)
   * ```
   */
  public fun setAttribute(param0: SkSVGAttribute, param1: SkSVGValue) {
    TODO("Implement setAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGNode::setAttribute(const char* attributeName, const char* attributeValue) {
   *     return set_string_attribute(sk_ref_sp(this), attributeName, attributeValue);
   * }
   * ```
   */
  public fun setAttribute(attributeName: String?, attributeValue: String?): Boolean {
    TODO("Implement setAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGNode::parseAndSetAttribute(const char* n, const char* v) {
   * #define PARSE_AND_SET(svgName, attrName)                                                        \
   *     this->set##attrName(                                                                        \
   *             SkSVGAttributeParser::parseProperty<decltype(fPresentationAttributes.f##attrName)>( \
   *                     svgName, n, v))
   *
   *     return PARSE_AND_SET(   "clip-path"                  , ClipPath)
   *            || PARSE_AND_SET("clip-rule"                  , ClipRule)
   *            || PARSE_AND_SET("color"                      , Color)
   *            || PARSE_AND_SET("color-interpolation"        , ColorInterpolation)
   *            || PARSE_AND_SET("color-interpolation-filters", ColorInterpolationFilters)
   *            || PARSE_AND_SET("display"                    , Display)
   *            || PARSE_AND_SET("fill"                       , Fill)
   *            || PARSE_AND_SET("fill-opacity"               , FillOpacity)
   *            || PARSE_AND_SET("fill-rule"                  , FillRule)
   *            || PARSE_AND_SET("filter"                     , Filter)
   *            || PARSE_AND_SET("flood-color"                , FloodColor)
   *            || PARSE_AND_SET("flood-opacity"              , FloodOpacity)
   *            || PARSE_AND_SET("font-family"                , FontFamily)
   *            || PARSE_AND_SET("font-size"                  , FontSize)
   *            || PARSE_AND_SET("font-style"                 , FontStyle)
   *            || PARSE_AND_SET("font-weight"                , FontWeight)
   *            || PARSE_AND_SET("lighting-color"             , LightingColor)
   *            || PARSE_AND_SET("mask"                       , Mask)
   *            || PARSE_AND_SET("opacity"                    , Opacity)
   *            || PARSE_AND_SET("stop-color"                 , StopColor)
   *            || PARSE_AND_SET("stop-opacity"               , StopOpacity)
   *            || PARSE_AND_SET("stroke"                     , Stroke)
   *            || PARSE_AND_SET("stroke-dasharray"           , StrokeDashArray)
   *            || PARSE_AND_SET("stroke-dashoffset"          , StrokeDashOffset)
   *            || PARSE_AND_SET("stroke-linecap"             , StrokeLineCap)
   *            || PARSE_AND_SET("stroke-linejoin"            , StrokeLineJoin)
   *            || PARSE_AND_SET("stroke-miterlimit"          , StrokeMiterLimit)
   *            || PARSE_AND_SET("stroke-opacity"             , StrokeOpacity)
   *            || PARSE_AND_SET("stroke-width"               , StrokeWidth)
   *            || PARSE_AND_SET("text-anchor"                , TextAnchor)
   *            || PARSE_AND_SET("visibility"                 , Visibility);
   *
   * #undef PARSE_AND_SET
   * }
   * ```
   */
  public open fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGNode::onPrepareToRender(SkSVGRenderContext* ctx) const {
   *     ctx->applyPresentationAttributes(fPresentationAttributes,
   *                                      this->hasChildren() ? 0 : SkSVGRenderContext::kLeaf);
   *
   *     // visibility:hidden and display:none disable rendering.
   *     // TODO: if display is not a value (true when display="inherit"), we currently
   *     //   ignore it. Eventually we should be able to add SkASSERT(display.isValue()).
   *     const auto visibility = ctx->presentationContext().fInherited.fVisibility->type();
   *     const auto display = fPresentationAttributes.fDisplay;  // display is uninherited
   *     return visibility != SkSVGVisibility::Type::kHidden &&
   *            (!display.isValue() || *display != SkSVGDisplay::kNone);
   * }
   * ```
   */
  protected open fun onPrepareToRender(ctx: SkSVGRenderContext?): Boolean {
    TODO("Implement onPrepareToRender")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual void onRender(const SkSVGRenderContext&) const = 0
   * ```
   */
  protected abstract fun onRender(param0: SkSVGRenderContext)

  /**
   * C++ original:
   * ```cpp
   * virtual bool onAsPaint(const SkSVGRenderContext&, SkPaint*) const { return false; }
   * ```
   */
  protected open fun onAsPaint(param0: SkSVGRenderContext, param1: SkPaint?): Boolean {
    TODO("Implement onAsPaint")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkPath onAsPath(const SkSVGRenderContext&) const = 0
   * ```
   */
  protected abstract fun onAsPath(param0: SkSVGRenderContext): SkPath

  /**
   * C++ original:
   * ```cpp
   * virtual void onSetAttribute(SkSVGAttribute, const SkSVGValue&) {}
   * ```
   */
  protected open fun onSetAttribute(param0: SkSVGAttribute, param1: SkSVGValue) {
    TODO("Implement onSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual bool hasChildren() const { return false; }
   * ```
   */
  protected open fun hasChildren(): Boolean {
    TODO("Implement hasChildren")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual SkRect onObjectBoundingBox(const SkSVGRenderContext&) const {
   *         return SkRect::MakeEmpty();
   *     }
   * ```
   */
  protected open fun onObjectBoundingBox(param0: SkSVGRenderContext): Int {
    TODO("Implement onObjectBoundingBox")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * SkMatrix SkSVGNode::ComputeViewboxMatrix(const SkRect& viewBox,
     *                                          const SkRect& viewPort,
     *                                          SkSVGPreserveAspectRatio par) {
     *     if (viewBox.isEmpty() || viewPort.isEmpty()) {
     *         return SkMatrix::Scale(0, 0);
     *     }
     *
     *     auto compute_scale = [&]() -> SkV2 {
     *         const auto sx = viewPort.width()  / viewBox.width(),
     *                    sy = viewPort.height() / viewBox.height();
     *
     *         if (par.fAlign == SkSVGPreserveAspectRatio::kNone) {
     *             // none -> anisotropic scaling, regardless of fScale
     *             return {sx, sy};
     *         }
     *
     *         // isotropic scaling
     *         const auto s = par.fScale == SkSVGPreserveAspectRatio::kMeet
     *                             ? std::min(sx, sy)
     *                             : std::max(sx, sy);
     *         return {s, s};
     *     };
     *
     *     auto compute_trans = [&](const SkV2& scale) -> SkV2 {
     *         static constexpr float gAlignCoeffs[] = {
     *                 0.0f, // Min
     *                 0.5f, // Mid
     *                 1.0f  // Max
     *         };
     *
     *         const size_t x_coeff = par.fAlign >> 0 & 0x03,
     *                      y_coeff = par.fAlign >> 2 & 0x03;
     *
     *         SkASSERT(x_coeff < std::size(gAlignCoeffs) &&
     *                  y_coeff < std::size(gAlignCoeffs));
     *
     *         const auto tx = -viewBox.x() * scale.x,
     *                    ty = -viewBox.y() * scale.y,
     *                    dx = viewPort.width()  - viewBox.width() * scale.x,
     *                    dy = viewPort.height() - viewBox.height() * scale.y;
     *
     *         return {
     *             tx + dx * gAlignCoeffs[x_coeff],
     *             ty + dy * gAlignCoeffs[y_coeff]
     *         };
     *     };
     *
     *     const auto s = compute_scale(),
     *                t = compute_trans(s);
     *
     *     return SkMatrix::Translate(t.x, t.y) *
     *            SkMatrix::Scale(s.x, s.y);
     * }
     * ```
     */
    protected fun computeViewboxMatrix(
      viewBox: SkRect,
      viewPort: SkRect,
      par: SkSVGPreserveAspectRatio,
    ): SkMatrix {
      TODO("Implement computeViewboxMatrix")
    }
  }
}
