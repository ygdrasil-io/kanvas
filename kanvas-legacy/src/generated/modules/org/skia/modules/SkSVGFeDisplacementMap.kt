package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import undefined.In2

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeDisplacementMap : public SkSVGFe {
 * public:
 *     using ChannelSelector = SkColorChannel;
 *
 *     static sk_sp<SkSVGFeDisplacementMap> Make() {
 *         return sk_sp<SkSVGFeDisplacementMap>(new SkSVGFeDisplacementMap());
 *     }
 *
 *     SkSVGColorspace resolveColorspace(const SkSVGRenderContext&,
 *                                       const SkSVGFilterContext&) const final;
 *
 *     SVG_ATTR(In2             , SkSVGFeInputType, SkSVGFeInputType())
 *     SVG_ATTR(XChannelSelector, ChannelSelector , ChannelSelector::kA)
 *     SVG_ATTR(YChannelSelector, ChannelSelector , ChannelSelector::kA)
 *     SVG_ATTR(Scale           , SkSVGNumberType , SkSVGNumberType(0))
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override {
 *         return {this->getIn(), this->getIn2()};
 *     }
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     SkSVGFeDisplacementMap() : INHERITED(SkSVGTag::kFeDisplacementMap) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeDisplacementMap public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGColorspace SkSVGFeDisplacementMap::resolveColorspace(const SkSVGRenderContext& ctx,
   *                                                           const SkSVGFilterContext& fctx) const {
   *     // According to spec https://www.w3.org/TR/SVG11/filters.html#feDisplacementMapElement,
   *     // the 'in' source image must remain in its current colorspace, which means the colorspace of
   *     // this FE node is the same as the input.
   *     return fctx.resolveInputColorspace(ctx, this->getIn());
   * }
   * ```
   */
  public override fun resolveColorspace(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement resolveColorspace")
  }

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(In2             , SkSVGFeInputType, SkSVGFeInputType())
   * ```
   */
  public override fun svgATTR(
    param0: In2,
    param1: SkSVGFeInputType,
    param2: () -> SkSVGFeInputType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGFeInputType> getInputs() const override {
   *         return {this->getIn(), this->getIn2()};
   *     }
   * ```
   */
  protected override fun getInputs(): Int {
    TODO("Implement getInputs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeDisplacementMap::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setIn2(SkSVGAttributeParser::parse<SkSVGFeInputType>("in2", name, value)) ||
   *            this->setXChannelSelector(
   *                    SkSVGAttributeParser::parse<SkSVGFeDisplacementMap::ChannelSelector>(
   *                            "xChannelSelector", name, value)) ||
   *            this->setYChannelSelector(
   *                    SkSVGAttributeParser::parse<SkSVGFeDisplacementMap::ChannelSelector>(
   *                            "yChannelSelector", name, value)) ||
   *            this->setScale(SkSVGAttributeParser::parse<SkSVGNumberType>("scale", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeDisplacementMap::onMakeImageFilter(
   *         const SkSVGRenderContext& ctx, const SkSVGFilterContext& fctx) const {
   *     const SkRect cropRect = this->resolveFilterSubregion(ctx, fctx);
   *     const SkSVGColorspace colorspace = this->resolveColorspace(ctx, fctx);
   *
   *     // According to spec https://www.w3.org/TR/SVG11/filters.html#feDisplacementMapElement,
   *     // the 'in' source image must remain in its current colorspace.
   *     sk_sp<SkImageFilter> in = fctx.resolveInput(ctx, this->getIn());
   *     sk_sp<SkImageFilter> in2 = fctx.resolveInput(ctx, this->getIn2(), colorspace);
   *
   *     SkScalar scale = fScale;
   *     if (fctx.primitiveUnits().type() == SkSVGObjectBoundingBoxUnits::Type::kObjectBoundingBox) {
   *         const auto obbt = ctx.transformForCurrentOBB(fctx.primitiveUnits());
   *         scale = SkSVGLengthContext({obbt.scale.x, obbt.scale.y})
   *                     .resolve(SkSVGLength(scale, SkSVGLength::Unit::kPercentage),
   *                              SkSVGLengthContext::LengthType::kOther);
   *     }
   *
   *     return SkImageFilters::DisplacementMap(
   *             fXChannelSelector, fYChannelSelector, scale, in2, in, cropRect);
   * }
   * ```
   */
  public fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): SkSp<SkImageFilter> {
    TODO("Implement onMakeImageFilter")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeDisplacementMap> Make() {
     *         return sk_sp<SkSVGFeDisplacementMap>(new SkSVGFeDisplacementMap());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
