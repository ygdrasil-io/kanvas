package org.skia.modules

import kotlin.Float
import org.skia.core.SkCubicMap

/**
 * C++ original:
 * ```cpp
 * struct ShapeGenerator {
 *     SkCubicMap shape_mapper,
 *                 ease_mapper;
 *     float      e0, e1, crs;
 *
 *     ShapeGenerator(const ShapeInfo& sinfo, float ease_lo, float ease_hi)
 *         : shape_mapper(sinfo.ctrl0, sinfo.ctrl1)
 *         , ease_mapper(EaseVec(ease_lo), SkVector{1,1} - EaseVec(ease_hi))
 *         , e0(sinfo.e0)
 *         , e1(sinfo.e1)
 *         , crs(sinfo.crs) {}
 *
 *     float operator()(float t) const {
 *         // SkCubicMap clamps its input, so we can let it all hang out.
 *         t = std::min(t - e0, e1 - t);
 *         t = sk_ieee_float_divide(t, crs);
 *
 *         return ease_mapper.computeYFromX(shape_mapper.computeYFromX(t));
 *     }
 * }
 * ```
 */
public data class ShapeGenerator public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkCubicMap shape_mapper
   * ```
   */
  public var shapeMapper: SkCubicMap,
  /**
   * C++ original:
   * ```cpp
   * SkCubicMap shape_mapper,
   *                 ease_mapper
   * ```
   */
  public var easeMapper: SkCubicMap,
  /**
   * C++ original:
   * ```cpp
   * float      e0
   * ```
   */
  public var e0: Float,
  /**
   * C++ original:
   * ```cpp
   * float      e0, e1
   * ```
   */
  public var e1: Float,
  /**
   * C++ original:
   * ```cpp
   * float      e0, e1, crs
   * ```
   */
  public var crs: Float,
) {
  /**
   * C++ original:
   * ```cpp
   * float operator()(float t) const {
   *         // SkCubicMap clamps its input, so we can let it all hang out.
   *         t = std::min(t - e0, e1 - t);
   *         t = sk_ieee_float_divide(t, crs);
   *
   *         return ease_mapper.computeYFromX(shape_mapper.computeYFromX(t));
   *     }
   * ```
   */
  public operator fun invoke(t: Float): Float {
    TODO("Implement invoke")
  }
}
