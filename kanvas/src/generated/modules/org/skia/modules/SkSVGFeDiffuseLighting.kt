package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkSVGFeDiffuseLighting final : public SkSVGFeLighting {
 * public:
 *     static sk_sp<SkSVGFeDiffuseLighting> Make() {
 *         return sk_sp<SkSVGFeDiffuseLighting>(new SkSVGFeDiffuseLighting());
 *     }
 *
 *     SVG_ATTR(DiffuseConstant, SkSVGNumberType, 1)
 *
 * protected:
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     sk_sp<SkImageFilter> makeDistantLight(const SkSVGRenderContext&,
 *                                           const SkSVGFilterContext&,
 *                                           const SkSVGFeDistantLight*) const final;
 *
 *     sk_sp<SkImageFilter> makePointLight(const SkSVGRenderContext&,
 *                                         const SkSVGFilterContext&,
 *                                         const SkSVGFePointLight*) const final;
 *
 *     sk_sp<SkImageFilter> makeSpotLight(const SkSVGRenderContext&,
 *                                        const SkSVGFilterContext&,
 *                                        const SkSVGFeSpotLight*) const final;
 *
 * private:
 *     SkSVGFeDiffuseLighting() : INHERITED(SkSVGTag::kFeDiffuseLighting) {}
 *
 *     using INHERITED = SkSVGFeLighting;
 * }
 * ```
 */
public class SkSVGFeDiffuseLighting public constructor() : SkSVGFeLighting(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(DiffuseConstant, SkSVGNumberType, 1)
   * ```
   */
  public override fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeDiffuseLighting::makeDistantLight(
   *         const SkSVGRenderContext& ctx,
   *         const SkSVGFilterContext& fctx,
   *         const SkSVGFeDistantLight* light) const {
   *     const SkPoint3 dir = light->computeDirection();
   *     return SkImageFilters::DistantLitDiffuse(
   *             this->resolveXYZ(ctx, fctx, dir.fX, dir.fY, dir.fZ),
   *             this->resolveLightingColor(ctx),
   *             this->getSurfaceScale(),
   *             this->getDiffuseConstant(),
   *             fctx.resolveInput(ctx, this->getIn(), this->resolveColorspace(ctx, fctx)),
   *             this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  protected override fun makeDistantLight(
    ctx: SkSVGRenderContext,
    fctx: SkSVGFilterContext,
    light: SkSVGFeDistantLight?,
  ): Int {
    TODO("Implement makeDistantLight")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeDiffuseLighting::makePointLight(const SkSVGRenderContext& ctx,
   *                                                             const SkSVGFilterContext& fctx,
   *                                                             const SkSVGFePointLight* light) const {
   *     return SkImageFilters::PointLitDiffuse(
   *             this->resolveXYZ(ctx, fctx, light->getX(), light->getY(), light->getZ()),
   *             this->resolveLightingColor(ctx),
   *             this->getSurfaceScale(),
   *             this->getDiffuseConstant(),
   *             fctx.resolveInput(ctx, this->getIn(), this->resolveColorspace(ctx, fctx)),
   *             this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  protected override fun makePointLight(
    ctx: SkSVGRenderContext,
    fctx: SkSVGFilterContext,
    light: SkSVGFePointLight?,
  ): Int {
    TODO("Implement makePointLight")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeDiffuseLighting::makeSpotLight(const SkSVGRenderContext& ctx,
   *                                                            const SkSVGFilterContext& fctx,
   *                                                            const SkSVGFeSpotLight* light) const {
   *     const auto& limitingConeAngle = light->getLimitingConeAngle();
   *     const float cutoffAngle = limitingConeAngle.value_or(180.f);
   *
   *     return SkImageFilters::SpotLitDiffuse(
   *             this->resolveXYZ(ctx, fctx, light->getX(), light->getY(), light->getZ()),
   *             this->resolveXYZ(
   *                     ctx, fctx, light->getPointsAtX(), light->getPointsAtY(), light->getPointsAtZ()),
   *             light->getSpecularExponent(),
   *             cutoffAngle,
   *             this->resolveLightingColor(ctx),
   *             this->getSurfaceScale(),
   *             this->getDiffuseConstant(),
   *             fctx.resolveInput(ctx, this->getIn(), this->resolveColorspace(ctx, fctx)),
   *             this->resolveFilterSubregion(ctx, fctx));
   * }
   * ```
   */
  protected override fun makeSpotLight(
    ctx: SkSVGRenderContext,
    fctx: SkSVGFilterContext,
    light: SkSVGFeSpotLight?,
  ): Int {
    TODO("Implement makeSpotLight")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeDiffuseLighting::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setDiffuseConstant(
   *                    SkSVGAttributeParser::parse<SkSVGNumberType>("diffuseConstant", n, v));
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
     * static sk_sp<SkSVGFeDiffuseLighting> Make() {
     *         return sk_sp<SkSVGFeDiffuseLighting>(new SkSVGFeDiffuseLighting());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
