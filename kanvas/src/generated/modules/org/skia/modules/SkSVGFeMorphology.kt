package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SkSVGFeMorphology : public SkSVGFe {
 * public:
 *     struct Radius {
 *         SkSVGNumberType fX;
 *         SkSVGNumberType fY;
 *     };
 *
 *     enum class Operator {
 *         kErode,
 *         kDilate,
 *     };
 *
 *     static sk_sp<SkSVGFeMorphology> Make() {
 *         return sk_sp<SkSVGFeMorphology>(new SkSVGFeMorphology());
 *     }
 *
 *     SVG_ATTR(Operator, Operator, Operator::kErode)
 *     SVG_ATTR(Radius  , Radius  , Radius({0, 0}))
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
 *     SkSVGFeMorphology() : INHERITED(SkSVGTag::kFeMorphology) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeMorphology public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Operator, Operator, Operator::kErode)
   * ```
   */
  public fun svgATTR(
    param0: Operator,
    param1: Operator,
    param2: org.skia.sksl.Operator.KErode,
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
   * bool SkSVGFeMorphology::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setOperator(SkSVGAttributeParser::parse<SkSVGFeMorphology::Operator>(
   *                    "operator", name, value)) ||
   *            this->setRadius(SkSVGAttributeParser::parse<SkSVGFeMorphology::Radius>(
   *                    "radius", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeMorphology::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                           const SkSVGFilterContext& fctx) const {
   *     const SkRect cropRect = this->resolveFilterSubregion(ctx, fctx);
   *     const SkSVGColorspace colorspace = this->resolveColorspace(ctx, fctx);
   *     sk_sp<SkImageFilter> input = fctx.resolveInput(ctx, this->getIn(), colorspace);
   *
   *     const auto r = SkV2{fRadius.fX, fRadius.fY}
   *                  * ctx.transformForCurrentOBB(fctx.primitiveUnits()).scale;
   *     switch (fOperator) {
   *         case Operator::kErode:
   *             return SkImageFilters::Erode(r.x, r.y, input, cropRect);
   *         case Operator::kDilate:
   *             return SkImageFilters::Dilate(r.x, r.y, input, cropRect);
   *     }
   *
   *     SkUNREACHABLE;
   * }
   * ```
   */
  public fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): SkSp<SkImageFilter> {
    TODO("Implement onMakeImageFilter")
  }

  public data class Radius public constructor(
    public var fX: Int,
    public var fY: Int,
  )

  public enum class Operator {
    kErode,
    kDilate,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeMorphology> Make() {
     *         return sk_sp<SkSVGFeMorphology>(new SkSVGFeMorphology());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
