package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp
import undefined.In2

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeComposite final : public SkSVGFe {
 * public:
 *     static sk_sp<SkSVGFeComposite> Make() {
 *         return sk_sp<SkSVGFeComposite>(new SkSVGFeComposite());
 *     }
 *
 *     SVG_ATTR(In2, SkSVGFeInputType, SkSVGFeInputType())
 *     SVG_ATTR(K1, SkSVGNumberType, SkSVGNumberType(0))
 *     SVG_ATTR(K2, SkSVGNumberType, SkSVGNumberType(0))
 *     SVG_ATTR(K3, SkSVGNumberType, SkSVGNumberType(0))
 *     SVG_ATTR(K4, SkSVGNumberType, SkSVGNumberType(0))
 *     SVG_ATTR(Operator, SkSVGFeCompositeOperator, SkSVGFeCompositeOperator::kOver)
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
 *     SkSVGFeComposite() : INHERITED(SkSVGTag::kFeComposite) {}
 *
 *     static SkBlendMode BlendModeForOperator(SkSVGFeCompositeOperator);
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public class SkSVGFeComposite public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(In2, SkSVGFeInputType, SkSVGFeInputType())
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
   * bool SkSVGFeComposite::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            // SkSVGFeInputType parsing defined in SkSVGFe.cpp:
   *            this->setIn2(SkSVGAttributeParser::parse<SkSVGFeInputType>("in2", name, value)) ||
   *            this->setK1(SkSVGAttributeParser::parse<SkSVGNumberType>("k1", name, value)) ||
   *            this->setK2(SkSVGAttributeParser::parse<SkSVGNumberType>("k2", name, value)) ||
   *            this->setK3(SkSVGAttributeParser::parse<SkSVGNumberType>("k3", name, value)) ||
   *            this->setK4(SkSVGAttributeParser::parse<SkSVGNumberType>("k4", name, value)) ||
   *            this->setOperator(
   *                    SkSVGAttributeParser::parse<SkSVGFeCompositeOperator>("operator", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeComposite::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                          const SkSVGFilterContext& fctx) const {
   *     const SkRect cropRect = this->resolveFilterSubregion(ctx, fctx);
   *     const SkSVGColorspace colorspace = this->resolveColorspace(ctx, fctx);
   *     const sk_sp<SkImageFilter> background = fctx.resolveInput(ctx, fIn2, colorspace);
   *     const sk_sp<SkImageFilter> foreground = fctx.resolveInput(ctx, this->getIn(), colorspace);
   *     if (fOperator == SkSVGFeCompositeOperator::kArithmetic) {
   *         constexpr bool enforcePMColor = true;
   *         return SkImageFilters::Arithmetic(
   *                 fK1, fK2, fK3, fK4, enforcePMColor, background, foreground, cropRect);
   *     } else {
   *         return SkImageFilters::Blend(
   *                 BlendModeForOperator(fOperator), background, foreground, cropRect);
   *     }
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
     * static sk_sp<SkSVGFeComposite> Make() {
     *         return sk_sp<SkSVGFeComposite>(new SkSVGFeComposite());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }

    /**
     * C++ original:
     * ```cpp
     * SkBlendMode SkSVGFeComposite::BlendModeForOperator(SkSVGFeCompositeOperator op) {
     *     switch (op) {
     *         case SkSVGFeCompositeOperator::kOver:
     *             return SkBlendMode::kSrcOver;
     *         case SkSVGFeCompositeOperator::kIn:
     *             return SkBlendMode::kSrcIn;
     *         case SkSVGFeCompositeOperator::kOut:
     *             return SkBlendMode::kSrcOut;
     *         case SkSVGFeCompositeOperator::kAtop:
     *             return SkBlendMode::kSrcATop;
     *         case SkSVGFeCompositeOperator::kXor:
     *             return SkBlendMode::kXor;
     *         case SkSVGFeCompositeOperator::kArithmetic:
     *             // Arithmetic is not handled with a blend
     *             SkASSERT(false);
     *             return SkBlendMode::kSrcOver;
     *     }
     *
     *     SkUNREACHABLE;
     * }
     * ```
     */
    private fun blendModeForOperator(op: SkSVGFeCompositeOperator): SkBlendMode {
      TODO("Implement blendModeForOperator")
    }
  }
}
