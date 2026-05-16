package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import undefined.Dx
import undefined.SkSVGNumberType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeOffset : public SkSVGFe {
 * public:
 *     static sk_sp<SkSVGFeOffset> Make() { return sk_sp<SkSVGFeOffset>(new SkSVGFeOffset()); }
 *
 *     SVG_ATTR(Dx, SkSVGNumberType, SkSVGNumberType(0))
 *     SVG_ATTR(Dy, SkSVGNumberType, SkSVGNumberType(0))
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override { return {this->getIn()}; }
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 * private:
 *     SkSVGFeOffset() : INHERITED(SkSVGTag::kFeOffset) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeOffset public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Dx, SkSVGNumberType, SkSVGNumberType(0))
   * ```
   */
  public override fun svgATTR(
    param0: Dx,
    param1: SkSVGNumberType,
    param2: (Int) -> SkSVGNumberType,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGFeInputType> getInputs() const override { return {this->getIn()}; }
   * ```
   */
  protected override fun getInputs(): Int {
    TODO("Implement getInputs")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeOffset::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setDx(SkSVGAttributeParser::parse<SkSVGNumberType>("dx", name, value)) ||
   *            this->setDy(SkSVGAttributeParser::parse<SkSVGNumberType>("dy", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeOffset::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                       const SkSVGFilterContext& fctx) const {
   *     const auto d = SkV2{this->getDx(), this->getDy()}
   *                  * ctx.transformForCurrentOBB(fctx.primitiveUnits()).scale;
   *
   *     sk_sp<SkImageFilter> in =
   *             fctx.resolveInput(ctx, this->getIn(), this->resolveColorspace(ctx, fctx));
   *     return SkImageFilters::Offset(d.x, d.y, std::move(in), this->resolveFilterSubregion(ctx, fctx));
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
     * static sk_sp<SkSVGFeOffset> Make() { return sk_sp<SkSVGFeOffset>(new SkSVGFeOffset()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
