package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import undefined.Href

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeImage : public SkSVGFe {
 * public:
 *     static sk_sp<SkSVGFeImage> Make() { return sk_sp<SkSVGFeImage>(new SkSVGFeImage()); }
 *
 *     SVG_ATTR(Href               , SkSVGIRI                , SkSVGIRI())
 *     SVG_ATTR(PreserveAspectRatio, SkSVGPreserveAspectRatio, SkSVGPreserveAspectRatio())
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override { return {}; }
 *
 * private:
 *     SkSVGFeImage() : INHERITED(SkSVGTag::kFeImage) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeImage public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Href               , SkSVGIRI                , SkSVGIRI())
   * ```
   */
  public override fun svgATTR(
    param0: Href,
    param1: SkSVGIRI,
    param2: () -> SkSVGIRI,
  ): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeImage::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                      const SkSVGFilterContext& fctx) const {
   *     // Load image and map viewbox (image bounds) to viewport (filter effects subregion).
   *     const SkRect viewport = this->resolveFilterSubregion(ctx, fctx);
   *     const auto imgInfo =
   *             SkSVGImage::LoadImage(ctx.resourceProvider(), fHref, viewport, fPreserveAspectRatio);
   *     if (!imgInfo.fImage) {
   *         return nullptr;
   *     }
   *
   *     // Create the image filter mapped according to aspect ratio
   *     const SkRect srcRect = SkRect::Make(imgInfo.fImage->bounds());
   *     const SkRect& dstRect = imgInfo.fDst;
   *     // TODO: image-rendering property
   *     auto imgfilt = SkImageFilters::Image(imgInfo.fImage, srcRect, dstRect,
   *                                          SkSamplingOptions(SkFilterMode::kLinear,
   *                                                            SkMipmapMode::kNearest));
   *
   *     // Aspect ratio mapping may end up drawing content outside of the filter effects region,
   *     // so perform an explicit crop.
   *     return SkImageFilters::Merge(&imgfilt, 1, fctx.filterEffectsRegion());
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
   * bool SkSVGFeImage::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setHref(SkSVGAttributeParser::parse<SkSVGIRI>("xlink:href", n, v)) ||
   *            this->setPreserveAspectRatio(SkSVGAttributeParser::parse<SkSVGPreserveAspectRatio>(
   *                    "preserveAspectRatio", n, v));
   * }
   * ```
   */
  public override fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeImage> Make() { return sk_sp<SkSVGFeImage>(new SkSVGFeImage()); }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
