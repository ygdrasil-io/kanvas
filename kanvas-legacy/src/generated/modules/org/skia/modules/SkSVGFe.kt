package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkSp
import undefined.In

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFe : public SkSVGHiddenContainer {
 * public:
 *     static bool IsFilterEffect(const sk_sp<SkSVGNode>& node) {
 *         switch (node->tag()) {
 *             case SkSVGTag::kFeBlend:
 *             case SkSVGTag::kFeColorMatrix:
 *             case SkSVGTag::kFeComponentTransfer:
 *             case SkSVGTag::kFeComposite:
 *             case SkSVGTag::kFeDiffuseLighting:
 *             case SkSVGTag::kFeDisplacementMap:
 *             case SkSVGTag::kFeFlood:
 *             case SkSVGTag::kFeGaussianBlur:
 *             case SkSVGTag::kFeImage:
 *             case SkSVGTag::kFeMerge:
 *             case SkSVGTag::kFeMorphology:
 *             case SkSVGTag::kFeOffset:
 *             case SkSVGTag::kFeSpecularLighting:
 *             case SkSVGTag::kFeTurbulence:
 *                 return true;
 *             default:
 *                 return false;
 *         }
 *     }
 *
 *     sk_sp<SkImageFilter> makeImageFilter(const SkSVGRenderContext& ctx,
 *                                          const SkSVGFilterContext& fctx) const;
 *
 *     // https://www.w3.org/TR/SVG11/filters.html#FilterPrimitiveSubRegion
 *     SkRect resolveFilterSubregion(const SkSVGRenderContext&, const SkSVGFilterContext&) const;
 *
 *     /**
 *      * Resolves the colorspace within which this filter effect should be applied.
 *      * Spec: https://www.w3.org/TR/SVG11/painting.html#ColorInterpolationProperties
 *      * 'color-interpolation-filters' property.
 *      */
 *     virtual SkSVGColorspace resolveColorspace(const SkSVGRenderContext&,
 *                                               const SkSVGFilterContext&) const;
 *
 *     /** Propagates any inherited presentation attributes in the given context. */
 *     void applyProperties(SkSVGRenderContext*) const;
 *
 *     SVG_ATTR(In, SkSVGFeInputType, SkSVGFeInputType())
 *     SVG_ATTR(Result, SkSVGStringType, SkSVGStringType())
 *     SVG_OPTIONAL_ATTR(X, SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Y, SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Width, SkSVGLength)
 *     SVG_OPTIONAL_ATTR(Height, SkSVGLength)
 *
 * protected:
 *     explicit SkSVGFe(SkSVGTag t) : INHERITED(t) {}
 *
 *     virtual sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                                    const SkSVGFilterContext&) const = 0;
 *
 *     virtual std::vector<SkSVGFeInputType> getInputs() const = 0;
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     /**
 *      * Resolves the rect specified by the x, y, width and height attributes (if specified) on this
 *      * filter effect. These attributes are resolved according to the given length context and
 *      * the value of 'primitiveUnits' on the parent <filter> element.
 *      */
 *     SkRect resolveBoundaries(const SkSVGRenderContext&, const SkSVGFilterContext&) const;
 *
 *     using INHERITED = SkSVGHiddenContainer;
 * }
 * ```
 */
public abstract class SkSVGFe : SkSVGHiddenContainer() {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFe::makeImageFilter(const SkSVGRenderContext& ctx,
   *                                               const SkSVGFilterContext& fctx) const {
   *     return this->onMakeImageFilter(ctx, fctx);
   * }
   * ```
   */
  public fun makeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement makeImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGFe::resolveFilterSubregion(const SkSVGRenderContext& ctx,
   *                                        const SkSVGFilterContext& fctx) const {
   *     // From https://www.w3.org/TR/SVG11/filters.html#FilterPrimitiveSubRegion,
   *     // the default filter effect subregion is equal to the union of the subregions defined
   *     // for all "referenced nodes" (filter effect inputs). If there are no inputs, the
   *     // default subregion is equal to the filter effects region
   *     // (https://www.w3.org/TR/SVG11/filters.html#FilterEffectsRegion).
   *     const std::vector<SkSVGFeInputType> inputs = this->getInputs();
   *     SkRect defaultSubregion;
   *     if (inputs.empty() || AnyIsStandardInput(fctx, inputs)) {
   *         defaultSubregion = fctx.filterEffectsRegion();
   *     } else {
   *         defaultSubregion = fctx.filterPrimitiveSubregion(inputs[0]);
   *         for (size_t i = 1; i < inputs.size(); i++) {
   *             defaultSubregion.join(fctx.filterPrimitiveSubregion(inputs[i]));
   *         }
   *     }
   *
   *     // Next resolve the rect specified by the x, y, width, height attributes on this filter effect.
   *     // If those attributes were given, they override the corresponding attribute of the default
   *     // filter effect subregion calculated above.
   *     const SkRect boundaries = this->resolveBoundaries(ctx, fctx);
   *
   *     // Compute and return the fully resolved subregion.
   *     return SkRect::MakeXYWH(fX.has_value() ? boundaries.fLeft : defaultSubregion.fLeft,
   *                             fY.has_value() ? boundaries.fTop : defaultSubregion.fTop,
   *                             fWidth.has_value() ? boundaries.width() : defaultSubregion.width(),
   *                             fHeight.has_value() ? boundaries.height() : defaultSubregion.height());
   * }
   * ```
   */
  public fun resolveFilterSubregion(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement resolveFilterSubregion")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSVGColorspace SkSVGFe::resolveColorspace(const SkSVGRenderContext& ctx,
   *                                            const SkSVGFilterContext&) const {
   *     constexpr SkSVGColorspace kDefaultCS = SkSVGColorspace::kSRGB;
   *     const SkSVGColorspace cs = *ctx.presentationContext().fInherited.fColorInterpolationFilters;
   *     return cs == SkSVGColorspace::kAuto ? kDefaultCS : cs;
   * }
   * ```
   */
  public open fun resolveColorspace(ctx: SkSVGRenderContext, param1: SkSVGFilterContext): Int {
    TODO("Implement resolveColorspace")
  }

  /**
   * C++ original:
   * ```cpp
   * void SkSVGFe::applyProperties(SkSVGRenderContext* ctx) const { this->onPrepareToRender(ctx); }
   * ```
   */
  public fun applyProperties(ctx: SkSVGRenderContext?) {
    TODO("Implement applyProperties")
  }

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(In, SkSVGFeInputType, SkSVGFeInputType())
   * ```
   */
  public fun svgATTR(
    param0: In,
    param1: SkSVGFeInputType,
    param2: () -> SkSVGFeInputType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual std::vector<SkSVGFeInputType> getInputs() const = 0
   * ```
   */
  protected abstract fun getInputs(): Int

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFe::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setIn(SkSVGAttributeParser::parse<SkSVGFeInputType>("in", name, value)) ||
   *            this->setResult(SkSVGAttributeParser::parse<SkSVGStringType>("result", name, value)) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGLength>("x", name, value)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGLength>("y", name, value)) ||
   *            this->setWidth(SkSVGAttributeParser::parse<SkSVGLength>("width", name, value)) ||
   *            this->setHeight(SkSVGAttributeParser::parse<SkSVGLength>("height", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * SkRect SkSVGFe::resolveBoundaries(const SkSVGRenderContext& ctx,
   *                                   const SkSVGFilterContext& fctx) const {
   *     const auto x = fX.has_value() ? *fX : SkSVGLength(0, SkSVGLength::Unit::kPercentage);
   *     const auto y = fY.has_value() ? *fY : SkSVGLength(0, SkSVGLength::Unit::kPercentage);
   *     const auto w = fWidth.has_value() ? *fWidth : SkSVGLength(100, SkSVGLength::Unit::kPercentage);
   *     const auto h = fHeight.has_value() ? *fHeight : SkSVGLength(100, SkSVGLength::Unit::kPercentage);
   *
   *     return ctx.resolveOBBRect(x, y, w, h, fctx.primitiveUnits());
   * }
   * ```
   */
  private fun resolveBoundaries(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement resolveBoundaries")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static bool IsFilterEffect(const sk_sp<SkSVGNode>& node) {
     *         switch (node->tag()) {
     *             case SkSVGTag::kFeBlend:
     *             case SkSVGTag::kFeColorMatrix:
     *             case SkSVGTag::kFeComponentTransfer:
     *             case SkSVGTag::kFeComposite:
     *             case SkSVGTag::kFeDiffuseLighting:
     *             case SkSVGTag::kFeDisplacementMap:
     *             case SkSVGTag::kFeFlood:
     *             case SkSVGTag::kFeGaussianBlur:
     *             case SkSVGTag::kFeImage:
     *             case SkSVGTag::kFeMerge:
     *             case SkSVGTag::kFeMorphology:
     *             case SkSVGTag::kFeOffset:
     *             case SkSVGTag::kFeSpecularLighting:
     *             case SkSVGTag::kFeTurbulence:
     *                 return true;
     *             default:
     *                 return false;
     *         }
     *     }
     * ```
     */
    public fun isFilterEffect(node: SkSp<SkSVGNode>): Boolean {
      TODO("Implement isFilterEffect")
    }
  }
}
