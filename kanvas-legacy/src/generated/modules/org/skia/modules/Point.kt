package org.skia.modules

import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct Point {
 *     int32_t x;
 *     int32_t y;
 *
 *     // Relation for ordering events.
 *     friend bool operator<(const Point& p0, const Point& p1);
 *     friend bool operator>(const Point& p0, const Point& p1);
 *     friend bool operator>=(const Point& p0, const Point& p1);
 *     friend bool operator<=(const Point& p0, const Point& p1);
 *
 *     // Equality
 *     friend bool operator==(const Point& p0, const Point& p1);
 *     friend bool operator!=(const Point& p0, const Point& p1);
 *
 *     // Extremes
 *     static Point Smallest();
 *     static Point Largest();
 *     static bool DifferenceTooBig(Point p0, Point p1);
 *
 *     // Terms
 *     friend Point operator+(const Point& p0, const Point& p1) {
 *         return {p0.x + p1.x, p0.y + p1.y};
 *     }
 *     friend Point operator-(const Point& p0, const Point& p1) {
 *         return {p0.x - p1.x, p0.y - p1.y};
 *     }
 * }
 * ```
 */
public open class Point public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t x
   * ```
   */
  public var x: Int,
  /**
   * C++ original:
   * ```cpp
   * int32_t y
   * ```
   */
  public var y: Int,
) {
  public companion object {
    /**
     * C++ original:
     * ```cpp
     * Point Point::Smallest() {
     *     const int32_t kMinCoordinate = std::numeric_limits<int32_t>::min();
     *     return {kMinCoordinate, kMinCoordinate};
     * }
     * ```
     */
    public fun smallest(): Point {
      TODO("Implement smallest")
    }

    /**
     * C++ original:
     * ```cpp
     * Point Point::Largest() {
     *     const int32_t kMaxCoordinate = std::numeric_limits<int32_t>::max();
     *     return {kMaxCoordinate, kMaxCoordinate};
     * }
     * ```
     */
    public fun largest(): Point {
      TODO("Implement largest")
    }

    /**
     * C++ original:
     * ```cpp
     * bool Point::DifferenceTooBig(Point p0, Point p1) {
     *     auto tooBig = [](int32_t a, int32_t b) {
     *         return (b > 0 && a < std::numeric_limits<int32_t>::min() + b) ||
     *                (b < 0 && a > std::numeric_limits<int32_t>::max() + b);
     *     };
     *
     *     return tooBig(p0.x, p1.x) || tooBig(p0.y, p1.y);
     * }
     * ```
     */
    public fun differenceTooBig(p0: Point, p1: Point): Boolean {
      TODO("Implement differenceTooBig")
    }
  }
}
