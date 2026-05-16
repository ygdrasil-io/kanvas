package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import undefined.SkSVGNumberType

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeLighting : public SkSVGFe {
 * public:
 *     struct KernelUnitLength {
 *         SkSVGNumberType fDx;
 *         SkSVGNumberType fDy;
 *     };
 *
 *     SVG_ATTR(SurfaceScale, SkSVGNumberType, 1)
 *     SVG_OPTIONAL_ATTR(KernelUnitLength, KernelUnitLength)
 *
 * protected:
 *     explicit SkSVGFeLighting(SkSVGTag t) : INHERITED(t) {}
 *
 *     std::vector<SkSVGFeInputType> getInputs() const final { return {this->getIn()}; }
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const final;
 *
 *     virtual sk_sp<SkImageFilter> makeDistantLight(const SkSVGRenderContext&,
 *                                                   const SkSVGFilterContext&,
 *                                                   const SkSVGFeDistantLight*) const = 0;
 *
 *     virtual sk_sp<SkImageFilter> makePointLight(const SkSVGRenderContext&,
 *                                                 const SkSVGFilterContext&,
 *                                                 const SkSVGFePointLight*) const = 0;
 *
 *     virtual sk_sp<SkImageFilter> makeSpotLight(const SkSVGRenderContext&,
 *                                                const SkSVGFilterContext&,
 *                                                const SkSVGFeSpotLight*) const = 0;
 *
 *     SkColor resolveLightingColor(const SkSVGRenderContext&) const;
 *
 *     SkPoint3 resolveXYZ(const SkSVGRenderContext&,
 *                         const SkSVGFilterContext&,
 *                         SkSVGNumberType,
 *                         SkSVGNumberType,
 *                         SkSVGNumberType) const;
 *
 * private:
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public abstract class SkSVGFeLighting : SkSVGFe() {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(SurfaceScale, SkSVGNumberType, 1)
   * ```
   */
  public fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeLighting::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                         const SkSVGFilterContext& fctx) const {
   *     for (const auto& child : fChildren) {
   *         switch (child->tag()) {
   *             case SkSVGTag::kFeDistantLight:
   *                 return this->makeDistantLight(
   *                         ctx, fctx, static_cast<const SkSVGFeDistantLight*>(child.get()));
   *             case SkSVGTag::kFePointLight:
   *                 return this->makePointLight(
   *                         ctx, fctx, static_cast<const SkSVGFePointLight*>(child.get()));
   *             case SkSVGTag::kFeSpotLight:
   *                 return this->makeSpotLight(
   *                         ctx, fctx, static_cast<const SkSVGFeSpotLight*>(child.get()));
   *             default:
   *                 // Ignore unknown children, such as <desc> elements
   *                 break;
   *         }
   *     }
   *
   *     SkDEBUGF("lighting filter effect needs exactly one light source\n");
   *     return nullptr;
   * }
   * ```
   */
  protected fun onMakeImageFilter(ctx: SkSVGRenderContext, fctx: SkSVGFilterContext): Int {
    TODO("Implement onMakeImageFilter")
  }

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImageFilter> makeDistantLight(const SkSVGRenderContext&,
   *                                                   const SkSVGFilterContext&,
   *                                                   const SkSVGFeDistantLight*) const = 0
   * ```
   */
  protected abstract fun makeDistantLight(
    param0: SkSVGRenderContext,
    param1: SkSVGFilterContext,
    param2: SkSVGFeDistantLight?,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImageFilter> makePointLight(const SkSVGRenderContext&,
   *                                                 const SkSVGFilterContext&,
   *                                                 const SkSVGFePointLight*) const = 0
   * ```
   */
  protected abstract fun makePointLight(
    param0: SkSVGRenderContext,
    param1: SkSVGFilterContext,
    param2: SkSVGFePointLight?,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * virtual sk_sp<SkImageFilter> makeSpotLight(const SkSVGRenderContext&,
   *                                                const SkSVGFilterContext&,
   *                                                const SkSVGFeSpotLight*) const = 0
   * ```
   */
  protected abstract fun makeSpotLight(
    param0: SkSVGRenderContext,
    param1: SkSVGFilterContext,
    param2: SkSVGFeSpotLight?,
  ): Int

  /**
   * C++ original:
   * ```cpp
   * SkColor SkSVGFeLighting::resolveLightingColor(const SkSVGRenderContext& ctx) const {
   *     const auto color = this->getLightingColor();
   *     if (!color.isValue()) {
   *         // Uninherited presentation attributes should have a concrete value by now.
   *         SkDEBUGF("unhandled: lighting-color has no value\n");
   *         return SK_ColorWHITE;
   *     }
   *
   *     return ctx.resolveSvgColor(*color);
   * }
   * ```
   */
  protected fun resolveLightingColor(ctx: SkSVGRenderContext): Int {
    TODO("Implement resolveLightingColor")
  }

  /**
   * C++ original:
   * ```cpp
   * SkPoint3 SkSVGFeLighting::resolveXYZ(const SkSVGRenderContext& ctx,
   *                                      const SkSVGFilterContext& fctx,
   *                                      SkSVGNumberType x,
   *                                      SkSVGNumberType y,
   *                                      SkSVGNumberType z) const {
   *     const auto obbt = ctx.transformForCurrentOBB(fctx.primitiveUnits());
   *     const auto xy = SkV2{x,y} * obbt.scale + obbt.offset;
   *     z = SkSVGLengthContext({obbt.scale.x, obbt.scale.y})
   *             .resolve(SkSVGLength(z * 100.f, SkSVGLength::Unit::kPercentage),
   *                      SkSVGLengthContext::LengthType::kOther);
   *     return SkPoint3::Make(xy.x, xy.y, z);
   * }
   * ```
   */
  protected fun resolveXYZ(
    ctx: SkSVGRenderContext,
    fctx: SkSVGFilterContext,
    x: SkSVGNumberType,
    y: SkSVGNumberType,
    z: SkSVGNumberType,
  ): Int {
    TODO("Implement resolveXYZ")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeLighting::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setSurfaceScale(
   *                    SkSVGAttributeParser::parse<SkSVGNumberType>("surfaceScale", n, v)) ||
   *            this->setKernelUnitLength(SkSVGAttributeParser::parse<SkSVGFeLighting::KernelUnitLength>(
   *                    "kernelUnitLength", n, v));
   * }
   * ```
   */
  public override fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public data class KernelUnitLength public constructor(
    public var fDx: Int,
    public var fDy: Int,
  )
}

public typealias SkSVGFeSpecularLightingINHERITED = SkSVGFeLighting

public typealias SkSVGFeDiffuseLightingINHERITED = SkSVGFeLighting
