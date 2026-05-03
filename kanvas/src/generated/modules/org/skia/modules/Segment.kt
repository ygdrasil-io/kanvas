package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Pair
import kotlin.collections.List

/**
 * C++ original:
 * ```cpp
 * class Segment {
 * public:
 *     constexpr Segment(Point p0, Point p1)
 *         : Segment{std::minmax(p0, p1)} {}
 *
 *     const Point& upper() const;
 *     const Point& lower() const;
 *     std::tuple<int32_t, int32_t, int32_t, int32_t> bounds() const;
 *     bool isHorizontal() const;
 *     bool isVertical() const;
 *     friend constexpr bool operator<(const Segment& s0, const Segment& s1);
 *     friend constexpr bool operator==(const Segment& s0, const Segment& s1);
 *     friend constexpr bool operator!=(const Segment& s0, const Segment& s1);
 *
 * private:
 *     constexpr Segment(const std::tuple<Point, Point>& ps)
 *             : fUpper{std::get<0>(ps)}
 *             , fLower{std::get<1>(ps)} {
 *         SkASSERT(fUpper != fLower);
 *         SkASSERT(fUpper < fLower);
 *     }
 *
 *     Point fUpper;
 *     Point fLower;
 * }
 * ```
 */
public open class Segment public constructor(
  p0: Point,
  p1: Point,
) {
  /**
   * C++ original:
   * ```cpp
   * Point fUpper
   * ```
   */
  private var fUpper: Point = TODO("Initialize fUpper")

  /**
   * C++ original:
   * ```cpp
   * Point fLower
   * ```
   */
  private var fLower: Point = TODO("Initialize fLower")

  /**
   * C++ original:
   * ```cpp
   * constexpr Segment(Point p0, Point p1)
   *         : Segment{std::minmax(p0, p1)} {}
   * ```
   */
  public constructor(ps: Pair<Point, Point>) : this() {
    TODO("Implement constructor")
  }

  /**
   * C++ original:
   * ```cpp
   * const Point& Segment::upper() const {
   *     return fUpper;
   * }
   * ```
   */
  public fun upper(): Point {
    TODO("Implement upper")
  }

  /**
   * C++ original:
   * ```cpp
   * const Point& Segment::lower() const {
   *     return fLower;
   * }
   * ```
   */
  public fun lower(): Point {
    TODO("Implement lower")
  }

  /**
   * C++ original:
   * ```cpp
   * std::tuple<int32_t, int32_t, int32_t, int32_t> Segment::bounds() const {
   *     auto [left, right] = std::minmax(fUpper.x, fLower.x);
   *     return std::make_tuple(left, fUpper.y, right, fLower.y);
   * }
   * ```
   */
  public fun bounds(): List<Any> {
    TODO("Implement bounds")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Segment::isHorizontal() const {
   *     return fUpper.y == fLower.y;
   * }
   * ```
   */
  public fun isHorizontal(): Boolean {
    TODO("Implement isHorizontal")
  }

  /**
   * C++ original:
   * ```cpp
   * bool Segment::isVertical() const {
   *     return fUpper.x == fLower.x;
   * }
   * ```
   */
  public fun isVertical(): Boolean {
    TODO("Implement isVertical")
  }
}
