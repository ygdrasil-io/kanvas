package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeBlend : public SkSVGFe {
 * public:
 *     enum class Mode {
 *         kNormal,
 *         kMultiply,
 *         kScreen,
 *         kDarken,
 *         kLighten,
 *     };
 *
 *     static sk_sp<SkSVGFeBlend> Make() { return sk_sp<SkSVGFeBlend>(new SkSVGFeBlend()); }
 *
 *     SVG_ATTR(Mode, Mode, Mode::kNormal)
 *     SVG_ATTR(In2, SkSVGFeInputType, SkSVGFeInputType())
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
 *     SkSVGFeBlend() : INHERITED(SkSVGTag::kFeBlend) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeBlend public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Mode, Mode, Mode::kNormal)
   * ```
   */
  public fun svgATTR(
    param0: Mode,
    param1: Mode,
    param2: org.skia.`external`.Mode.KNormal,
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
   * bool SkSVGFeBlend::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setIn2(SkSVGAttributeParser::parse<SkSVGFeInputType>("in2", name, value)) ||
   *            this->setMode(SkSVGAttributeParser::parse<SkSVGFeBlend::Mode>("mode", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeBlend::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                      const SkSVGFilterContext& fctx) const {
   *     const SkRect cropRect = this->resolveFilterSubregion(ctx, fctx);
   *     const SkBlendMode blendMode = GetBlendMode(this->getMode());
   *     const SkSVGColorspace colorspace = this->resolveColorspace(ctx, fctx);
   *     const sk_sp<SkImageFilter> background = fctx.resolveInput(ctx, fIn2, colorspace);
   *     const sk_sp<SkImageFilter> foreground = fctx.resolveInput(ctx, this->getIn(), colorspace);
   *     return SkImageFilters::Blend(blendMode, background, foreground, cropRect);
   * }
   * ```
   */
  public fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): SkSp<SkImageFilter> {
    TODO("Implement onMakeImageFilter")
  }

  public enum class Mode {
    kNormal,
    kMultiply,
    kScreen,
    kDarken,
    kLighten,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeBlend> Make() { return sk_sp<SkSVGFeBlend>(new SkSVGFeBlend()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
