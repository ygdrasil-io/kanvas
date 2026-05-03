package org.skia.modules

import kotlin.Boolean
import kotlin.Int
import kotlin.String

/**
 * C++ original:
 * ```cpp
 * class SkSVGFeDistantLight final : public SkSVGFeLightSource {
 * public:
 *     static sk_sp<SkSVGFeDistantLight> Make() {
 *         return sk_sp<SkSVGFeDistantLight>(new SkSVGFeDistantLight());
 *     }
 *
 *     SkPoint3 computeDirection() const;
 *
 *     SVG_ATTR(Azimuth  , SkSVGNumberType, 0)
 *     SVG_ATTR(Elevation, SkSVGNumberType, 0)
 *
 * private:
 *     SkSVGFeDistantLight() : INHERITED(SkSVGTag::kFeDistantLight) {}
 *
 *     bool parseAndSetAttribute(const char*, const char*) override;
 *
 *     using INHERITED = SkSVGFeLightSource;
 * }
 * ```
 */
public class SkSVGFeDistantLight : SkSVGFeLightSource() {
  /**
   * C++ original:
   * ```cpp
   * SkPoint3 SkSVGFeDistantLight::computeDirection() const {
   *     // Computing direction from azimuth+elevation is two 3D rotations:
   *     //  - Rotate [1,0,0] about y axis first (elevation)
   *     //  - Rotate result about z axis (azimuth)
   *     // Which is just the first column vector in the 3x3 matrix Rz*Ry.
   *     const float azimuthRad = SkDegreesToRadians(fAzimuth);
   *     const float elevationRad = SkDegreesToRadians(fElevation);
   *     const float sinAzimuth = sinf(azimuthRad), cosAzimuth = cosf(azimuthRad);
   *     const float sinElevation = sinf(elevationRad), cosElevation = cosf(elevationRad);
   *     return SkPoint3::Make(cosAzimuth * cosElevation, sinAzimuth * cosElevation, sinElevation);
   * }
   * ```
   */
  public fun computeDirection(): Int {
    TODO("Implement computeDirection")
  }

  /**
   * C++ original:
   * ```cpp
   * SVG_ATTR(Azimuth  , SkSVGNumberType, 0)
   * ```
   */
  public fun svgATTR(param0: Int, param1: Int): Int {
    TODO("Implement svgATTR")
  }

  /**
   * C++ original:
   * ```cpp
   * bool SkSVGFeDistantLight::parseAndSetAttribute(const char* n, const char* v) {
   *     return INHERITED::parseAndSetAttribute(n, v) ||
   *            this->setAzimuth(SkSVGAttributeParser::parse<SkSVGNumberType>("azimuth", n, v)) ||
   *            this->setElevation(SkSVGAttributeParser::parse<SkSVGNumberType>("elevation", n, v));
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
     * static sk_sp<SkSVGFeDistantLight> Make() {
     *         return sk_sp<SkSVGFeDistantLight>(new SkSVGFeDistantLight());
     *     }
     * ```
     */
    public fun make(): Int {
      TODO("Implement make")
    }
  }
}
