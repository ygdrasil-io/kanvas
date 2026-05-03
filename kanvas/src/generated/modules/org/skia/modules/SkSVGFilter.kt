package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFilter final : public SkSVGHiddenContainer {
 * public:
 *     static sk_sp<SkSVGFilter> Make() { return sk_sp<SkSVGFilter>(new SkSVGFilter()); }
 *
 *     /** Propagates any inherited presentation attributes in the given context. */
 *     void applyProperties(SkSVGRenderContext*) const;
 *
 *     sk_sp<SkImageFilter> buildFilterDAG(const SkSVGRenderContext&) const;
 *
 *     SVG_ATTR(X, SkSVGLength, SkSVGLength(-10, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Y, SkSVGLength, SkSVGLength(-10, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Width, SkSVGLength, SkSVGLength(120, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(Height, SkSVGLength, SkSVGLength(120, SkSVGLength::Unit::kPercentage))
 *     SVG_ATTR(FilterUnits,
 *              SkSVGObjectBoundingBoxUnits,
 *              SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox))
 *     SVG_ATTR(PrimitiveUnits,
 *              SkSVGObjectBoundingBoxUnits,
 *              SkSVGObjectBoundingBoxUnits(SkSVGObjectBoundingBoxUnits::Type::kUserSpaceOnUse))
 *
 * private:
 *     SkSVGFilter() : INHERITED(SkSVGTag::kFilter) {}
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public class SkSVGFilter : SkSVGHiddenContainer() {
  /**
   * C++ original:
   * ```cpp
   * void SkSVGFilter::applyProperties(SkSVGRenderContext* ctx) const { this->onPrepareToRender(ctx); }
   * ```
   */
  public fun applyProperties(ctx: SkSVGRenderContext?) {
    TODO("Implement applyProperties")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFilter::buildFilterDAG(const SkSVGRenderContext& ctx) const {
   *     sk_sp<SkImageFilter> filter;
   *     SkSVGFilterContext fctx(ctx.resolveOBBRect(fX, fY, fWidth, fHeight, fFilterUnits),
   *                             fPrimitiveUnits);
   *     SkSVGRenderContext localCtx(ctx);
   *     this->applyProperties(&localCtx);
   *     SkSVGColorspace cs = SkSVGColorspace::kSRGB;
   *     for (const auto& child : fChildren) {
   *         if (!SkSVGFe::IsFilterEffect(child)) {
   *             continue;
   *         }
   *
   *         const auto& feNode = static_cast<const SkSVGFe&>(*child);
   *         const auto& feResultType = feNode.getResult();
   *
   *         // Propagate any inherited properties that may impact filter effect behavior (e.g.
   *         // color-interpolation-filters). We call this explicitly here because the SkSVGFe
   *         // nodes do not participate in the normal onRender path, which is when property
   *         // propagation currently occurs.
   *         SkSVGRenderContext localChildCtx(localCtx);
   *         feNode.applyProperties(&localChildCtx);
   *
   *         const SkRect filterSubregion = feNode.resolveFilterSubregion(localChildCtx, fctx);
   *         cs = feNode.resolveColorspace(localChildCtx, fctx);
   *         filter = feNode.makeImageFilter(localChildCtx, fctx);
   *
   *         if (!feResultType.isEmpty()) {
   *             fctx.registerResult(feResultType, filter, filterSubregion, cs);
   *         }
   *
   *         // Unspecified 'in' and 'in2' inputs implicitly resolve to the previous filter's result.
   *         fctx.setPreviousResult(filter, filterSubregion, cs);
   *     }
   *
   *     // Convert to final destination colorspace
   *     if (cs != SkSVGColorspace::kSRGB) {
   *         filter = SkImageFilters::ColorFilter(SkColorFilters::LinearToSRGBGamma(), filter);
   *     }
   *
   *     return filter;
   * }
   * ```
   */
  public fun buildFilterDAG(ctx: SkSVGRenderContext): Int {
    TODO("Implement buildFilterDAG")
  }

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X, SkSVGLength, SkSVGLength(-10, SkSVGLength::Unit::kPercentage))
   * ```
   */
  public fun svgATTR(
    param0: X,
    param1: SkSVGLength,
    param2: (Int, SkSVGLength.Unit.KPercentage) -> SkSVGLength,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFilter::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGLength>("x", name, value)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGLength>("y", name, value)) ||
   *            this->setWidth(SkSVGAttributeParser::parse<SkSVGLength>("width", name, value)) ||
   *            this->setHeight(SkSVGAttributeParser::parse<SkSVGLength>("height", name, value)) ||
   *            this->setFilterUnits(SkSVGAttributeParser::parse<SkSVGObjectBoundingBoxUnits>(
   *                    "filterUnits", name, value)) ||
   *            this->setPrimitiveUnits(SkSVGAttributeParser::parse<SkSVGObjectBoundingBoxUnits>(
   *                    "primitiveUnits", name, value));
   * }
   * ```
   */
  public fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFilter> Make() { return sk_sp<SkSVGFilter>(new SkSVGFilter()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
