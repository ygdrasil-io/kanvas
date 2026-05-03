package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkSVGFePointLight final : public SkSVGFeLightSource {
 * public:
 *     static sk_sp<SkSVGFePointLight> Make() {
 *         return sk_sp<SkSVGFePointLight>(new SkSVGFePointLight());
 *     }
 *
 *     SVG_ATTR(X, SkSVGNumberType, 0)
 *     SVG_ATTR(Y, SkSVGNumberType, 0)
 *     SVG_ATTR(Z, SkSVGNumberType, 0)
 *
 * private:
 *     SkSVGFePointLight() : INHERITED(SkSVGTag::kFePointLight) {}
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     using INHERITED = SkSVGFeLightSource;
 * }
 * ```
 */
public class SkSVGFePointLight : SkSVGFeLightSource() {
  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(X, SkSVGNumberType, 0)
   * ```
   */
  public fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFePointLight::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setX(SkSVGAttributeParser::parse<SkSVGNumberType>("x", n, v)) ||
   *            this->setY(SkSVGAttributeParser::parse<SkSVGNumberType>("y", n, v)) ||
   *            this->setZ(SkSVGAttributeParser::parse<SkSVGNumberType>("z", n, v));
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
     * static sk_sp<SkSVGFePointLight> Make() {
     *         return sk_sp<SkSVGFePointLight>(new SkSVGFePointLight());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
