package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeGaussianBlur : public SkSVGFe {
 * public:
 *     struct StdDeviation {
 *         SkSVGNumberType fX;
 *         SkSVGNumberType fY;
 *     };
 *
 *     static sk_sp<SkSVGFeGaussianBlur> Make() {
 *         return sk_sp<SkSVGFeGaussianBlur>(new SkSVGFeGaussianBlur());
 *     }
 *
 *     SVG_ATTR(StdDeviation, StdDeviation, StdDeviation({0, 0}))
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
 *     SkSVGFeGaussianBlur() : INHERITED(SkSVGTag::kFeGaussianBlur) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeGaussianBlur public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(StdDeviation, StdDeviation, StdDeviation({0, 0}))
   * ```
   */
  public fun svgATTR(
    param0: StdDeviation,
    param1: StdDeviation,
    param2: StdDeviation,
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
   * bool SkSVGFeGaussianBlur::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setStdDeviation(SkSVGAttributeParser::parse<SkSVGFeGaussianBlur::StdDeviation>(
   *                    "stdDeviation", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeGaussianBlur::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                             const SkSVGFilterContext& fctx) const {
   *     const auto sigma = SkV2{fStdDeviation.fX, fStdDeviation.fY}
   *                      * ctx.transformForCurrentOBB(fctx.primitiveUnits()).scale;
   *
   *     return SkImageFilters::Blur(
   *             sigma.x, sigma.y,
   *             fctx.resolveInput(ctx, this->getIn(), this->resolveColorspace(ctx, fctx)),
   *             this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  public fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): SkSp<SkImageFilter> {
    TODO("Implement onMakeImageFilter")
  }

  public data class StdDeviation public constructor(
    public var fX: Int,
    public var fY: Int,
  )

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeGaussianBlur> Make() {
     *         return sk_sp<SkSVGFeGaussianBlur>(new SkSVGFeGaussianBlur());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
