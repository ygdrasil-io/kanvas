package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeFlood : public SkSVGFe {
 * public:
 *     static sk_sp<SkSVGFeFlood> Make() { return sk_sp<SkSVGFeFlood>(new SkSVGFeFlood()); }
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override { return {}; }
 *
 * private:
 *     SkSVGFeFlood() : INHERITED(SkSVGTag::kFeFlood) {}
 *
 *     SkColor resolveFloodColor(const SkSVGRenderContext&) const;
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeFlood public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeFlood::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                      const SkSVGFilterContext& fctx) const {
   *     return SkImageFilters::Shader(SkShaders::Color(resolveFloodColor(ctx)),
   *                                   this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  protected override fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement onMakeImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGFeInputType> getInputs() const override { return {}; }
   * ```
   */
  protected override fun getInputs(): Int {
    TODO("Implement getInputs")
  }

  /**
   * C++ original:
   * ```cpp
   * SkColor SkSVGFeFlood::resolveFloodColor(const SkSVGRenderContext& ctx) const {
   *     const auto floodColor = this->getFloodColor();
   *     const auto floodOpacity = this->getFloodOpacity();
   *     // Uninherited presentation attributes should have a concrete value by now.
   *     if (!floodColor.isValue() || !floodOpacity.isValue()) {
   *         SkDEBUGF("unhandled: flood-color or flood-opacity has no value\n");
   *         return SK_ColorBLACK;
   *     }
   *
   *     const SkColor color = ctx.resolveSvgColor(*floodColor);
   *     return SkColorSetA(color, SkScalarRoundToInt(*floodOpacity * 255));
   * }
   * ```
   */
  private fun resolveFloodColor(ctx: SkSVGRenderContext): Int {
    TODO("Implement resolveFloodColor")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeFlood> Make() { return sk_sp<SkSVGFeFlood>(new SkSVGFeFlood()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
