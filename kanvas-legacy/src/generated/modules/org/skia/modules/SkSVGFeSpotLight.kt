package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkSVGFeSpotLight final : public SkSVGFeLightSource {
 * public:
 *     static sk_sp<SkSVGFeSpotLight> Make() {
 *         return sk_sp<SkSVGFeSpotLight>(new SkSVGFeSpotLight());
 *     }
 *
 *     SVG_ATTR(X               , SkSVGNumberType, 0)
 *     SVG_ATTR(Y               , SkSVGNumberType, 0)
 *     SVG_ATTR(Z               , SkSVGNumberType, 0)
 *     SVG_ATTR(PointsAtX       , SkSVGNumberType, 0)
 *     SVG_ATTR(PointsAtY       , SkSVGNumberType, 0)
 *     SVG_ATTR(PointsAtZ       , SkSVGNumberType, 0)
 *     SVG_ATTR(SpecularExponent, SkSVGNumberType, 1)
 *
 *     SVG_OPTIONAL_ATTR(LimitingConeAngle, SkSVGNumberType)
 *
 * private:
 *     SkSVGFeSpotLight() : INHERITED(SkSVGTag::kFeSpotLight) {}
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     using INHERITED = SkSVGFeLightSource;
 * }
 * ```
 */
public class SkSVGFeSpotLight : SkSVGFeLightSource() {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X               , SkSVGNumberType, 0)
   * ```
   */
  public fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeSpotLight::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGNumberType>("x", n, v)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGNumberType>("y", n, v)) ||
   *            this->setZ(SkSVGAttributeParser::parse<SkSVGNumberType>("z", n, v)) ||
   *            this->setPointsAtX(SkSVGAttributeParser::parse<SkSVGNumberType>("pointsAtX", n, v)) ||
   *            this->setPointsAtY(SkSVGAttributeParser::parse<SkSVGNumberType>("pointsAtY", n, v)) ||
   *            this->setPointsAtZ(SkSVGAttributeParser::parse<SkSVGNumberType>("pointsAtZ", n, v)) ||
   *            this->setSpecularExponent(
   *                    SkSVGAttributeParser::parse<SkSVGNumberType>("specularExponent", n, v)) ||
   *            this->setLimitingConeAngle(
   *                    SkSVGAttributeParser::parse<SkSVGNumberType>("limitingConeAngle", n, v));
   * }
   * ```
   */
  public fun parseAndSetAttribute(n: String?, v: String?): Boolean {
    TODO("Implement parseAndSetAttribute")
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static sk_sp<SkSVGFeSpotLight> Make() {
     *         return sk_sp<SkSVGFeSpotLight>(new SkSVGFeSpotLight());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
