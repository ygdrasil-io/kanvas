package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkSp

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFeTurbulence : public SkSVGFe {
 * public:
 *     static sk_sp<SkSVGFeTurbulence> Make() {
 *         return sk_sp<SkSVGFeTurbulence>(new SkSVGFeTurbulence());
 *     }
 *
 *     SVG_ATTR(BaseFrequency, SkSVGFeTurbulenceBaseFrequency, SkSVGFeTurbulenceBaseFrequency({}))
 *     SVG_ATTR(NumOctaves, SkSVGIntegerType, SkSVGIntegerType(1))
 *     SVG_ATTR(Seed, SkSVGNumberType, SkSVGNumberType(0))
 *     SVG_ATTR(TurbulenceType,
 *              SkSVGFeTurbulenceType,
 *              SkSVGFeTurbulenceType(SkSVGFeTurbulenceType::Type::kTurbulence))
 *
 * protected:
 *     sk_sp<SkImageFilter> onMakeImageFilter(const SkSVGRenderContext&,
 *                                            const SkSVGFilterContext&) const override;
 *
 *     std::vector<SkSVGFeInputType> getInputs() const override { return {}; }
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 * private:
 *     SkSVGFeTurbulence() : INHERITED(SkSVGTag::kFeTurbulence) {}
 *
 *     using INHERITED = SkSVGFe;
 * }
 * ```
 */
public open class SkSVGFeTurbulence public constructor() : SkSVGFe(TODO()) {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(BaseFrequency, SkSVGFeTurbulenceBaseFrequency, SkSVGFeTurbulenceBaseFrequency({}))
   * ```
   */
  public override fun svgATTR(
    param0: Int,
    param1: Int,
    param2: Int,
  ): Int {
    TODO("Implement svgATTR")
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
   * bool SkSVGFeTurbulence::parseAndSetAttribute(const char* name, const char* value) {
   *     return INHERITED::parseAndSetAttribute(name, value) ||
   *            this->setNumOctaves(
   *                    SkSVGAttributeParser::parse<SkSVGIntegerType>("numOctaves", name, value)) ||
   *            this->setSeed(SkSVGAttributeParser::parse<SkSVGNumberType>("seed", name, value)) ||
   *            this->setBaseFrequency(SkSVGAttributeParser::parse<SkSVGFeTurbulenceBaseFrequency>(
   *                    "baseFrequency", name, value)) ||
   *            this->setTurbulenceType(SkSVGAttributeParser::parse<SkSVGFeTurbulenceType>(
   *                    "type", name, value));
   * }
   * ```
   */
  protected override fun parseAndSetAttribute(name: String?, `value`: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  /**
   * C++ original:
   * ```cpp
   * sk_sp<SkImageFilter> SkSVGFeTurbulence::onMakeImageFilter(const SkSVGRenderContext& ctx,
   *                                                           const SkSVGFilterContext& fctx) const {
   *     const SkISize* tileSize = nullptr;  // TODO: needs filter element subregion properties
   *
   *     sk_sp<SkShader> shader;
   *     switch (fTurbulenceType.fType) {
   *         case SkSVGFeTurbulenceType::Type::kTurbulence:
   *             shader = SkShaders::MakeTurbulence(
   *                     fBaseFrequency.freqX(), fBaseFrequency.freqY(), fNumOctaves, fSeed, tileSize);
   *             break;
   *         case SkSVGFeTurbulenceType::Type::kFractalNoise:
   *             shader = SkShaders::MakeFractalNoise(
   *                     fBaseFrequency.freqX(), fBaseFrequency.freqY(), fNumOctaves, fSeed, tileSize);
   *             break;
   *     }
   *
   *     return SkImageFilters::Shader(shader, this->resolveFilterSubregion(ctx, fctx));
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
     * static sk_sp<SkSVGFeTurbulence> Make() {
     *         return sk_sp<SkSVGFeTurbulence>(new SkSVGFeTurbulence());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
