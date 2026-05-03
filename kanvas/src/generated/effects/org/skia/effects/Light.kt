package org.skia.effects

import kotlin.Float
import org.skia.core.ParameterSpace
import org.skia.core.Vector
import org.skia.foundation.SkColor
import org.skia.math.SkPoint
import org.skia.math.SkPoint3

/**
 * C++ original:
 * ```cpp
 * struct Light {
 *     enum class Type {
 *         kDistant,
 *         kPoint,
 *         kSpot,
 *         kLast = kSpot
 *     };
 *
 *     Type fType;
 *     SkColor fLightColor; // All lights
 *
 *     // Location and direction are decomposed into typed XY and Z for how they are transformed from
 *     // parameter space to layer space.
 *     skif::ParameterSpace<SkPoint> fLocationXY; // Spotlight and point lights only
 *     skif::ParameterSpace<ZValue>  fLocationZ;  //  ""
 *
 *     skif::ParameterSpace<skif::Vector> fDirectionXY; // Spotlight and distant lights only
 *     skif::ParameterSpace<ZValue>       fDirectionZ;  //  ""
 *
 *     // Spotlight only (and unchanged by layer matrix)
 *     float fFalloffExponent;
 *     float fCosCutoffAngle;
 *
 *     static Light Point(SkColor color, const SkPoint3& location) {
 *         return {Type::kPoint,
 *                 color,
 *                 skif::ParameterSpace<SkPoint>({location.fX, location.fY}),
 *                 skif::ParameterSpace<ZValue>(location.fZ),
 *                 /*directionXY=*/{},
 *                 /*directionZ=*/{},
 *                 /*falloffExponent=*/0.f,
 *                 /*cutoffAngle=*/0.f};
 *     }
 *
 *     static Light Distant(SkColor color, const SkPoint3& direction) {
 *         return {Type::kDistant,
 *                 color,
 *                 /*locationXY=*/{},
 *                 /*locationZ=*/{},
 *                 skif::ParameterSpace<skif::Vector>({direction.fX, direction.fY}),
 *                 skif::ParameterSpace<ZValue>(direction.fZ),
 *                 /*falloffExponent=*/0.f,
 *                 /*cutoffAngle=*/0.f};
 *     }
 *
 *     static Light Spot(SkColor color, const SkPoint3& location, const SkPoint3& direction,
 *                       float falloffExponent, float cosCutoffAngle) {
 *         return {Type::kSpot,
 *                 color,
 *                 skif::ParameterSpace<SkPoint>({location.fX, location.fY}),
 *                 skif::ParameterSpace<ZValue>(location.fZ),
 *                 skif::ParameterSpace<skif::Vector>({direction.fX, direction.fY}),
 *                 skif::ParameterSpace<ZValue>(direction.fZ),
 *                 falloffExponent,
 *                 cosCutoffAngle};
 *     }
 * }
 * ```
 */
public data class Light public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  public var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * SkColor fLightColor
   * ```
   */
  public var fLightColor: SkColor,
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<SkPoint> fLocationXY
   * ```
   */
  public var fLocationXY: ParameterSpace<SkPoint>,
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<ZValue>  fLocationZ
   * ```
   */
  public var fLocationZ: ParameterSpace<ZValue>,
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<skif::Vector> fDirectionXY
   * ```
   */
  public var fDirectionXY: ParameterSpace<Vector>,
  /**
   * C++ original:
   * ```cpp
   * skif::ParameterSpace<ZValue>       fDirectionZ
   * ```
   */
  public var fDirectionZ: ParameterSpace<ZValue>,
  /**
   * C++ original:
   * ```cpp
   * float fFalloffExponent
   * ```
   */
  public var fFalloffExponent: Float,
  /**
   * C++ original:
   * ```cpp
   * float fCosCutoffAngle
   * ```
   */
  public var fCosCutoffAngle: Float,
) {
  public enum class Type {
    kDistant,
    kPoint,
    kSpot,
    kLast,
  }

  public companion object {
    /**
     * C++ original:
     * ```cpp
     * static Light Point(SkColor color, const SkPoint3& location) {
     *         return {Type::kPoint,
     *                 color,
     *                 skif::ParameterSpace<SkPoint>({location.fX, location.fY}),
     *                 skif::ParameterSpace<ZValue>(location.fZ),
     *                 /*directionXY=*/{},
     *                 /*directionZ=*/{},
     *                 /*falloffExponent=*/0.f,
     *                 /*cutoffAngle=*/0.f};
     *     }
     * ```
     */
    public fun point(color: SkColor, location: SkPoint3): Light {
      TODO("Implement point")
    }

    /**
     * C++ original:
     * ```cpp
     * static Light Distant(SkColor color, const SkPoint3& direction) {
     *         return {Type::kDistant,
     *                 color,
     *                 /*locationXY=*/{},
     *                 /*locationZ=*/{},
     *                 skif::ParameterSpace<skif::Vector>({direction.fX, direction.fY}),
     *                 skif::ParameterSpace<ZValue>(direction.fZ),
     *                 /*falloffExponent=*/0.f,
     *                 /*cutoffAngle=*/0.f};
     *     }
     * ```
     */
    public fun distant(color: SkColor, direction: SkPoint3): Light {
      TODO("Implement distant")
    }

    /**
     * C++ original:
     * ```cpp
     * static Light Spot(SkColor color, const SkPoint3& location, const SkPoint3& direction,
     *                       float falloffExponent, float cosCutoffAngle) {
     *         return {Type::kSpot,
     *                 color,
     *                 skif::ParameterSpace<SkPoint>({location.fX, location.fY}),
     *                 skif::ParameterSpace<ZValue>(location.fZ),
     *                 skif::ParameterSpace<skif::Vector>({direction.fX, direction.fY}),
     *                 skif::ParameterSpace<ZValue>(direction.fZ),
     *                 falloffExponent,
     *                 cosCutoffAngle};
     *     }
     * ```
     */
    public fun spot(
      color: SkColor,
      location: SkPoint3,
      direction: SkPoint3,
      falloffExponent: Float,
      cosCutoffAngle: Float,
    ): Light {
      TODO("Implement spot")
    }
  }
}
