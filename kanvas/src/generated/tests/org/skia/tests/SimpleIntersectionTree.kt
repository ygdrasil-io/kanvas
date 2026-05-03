package org.skia.tests

import kotlin.Boolean
import kotlin.Int
import org.skia.math.SkRect

/**
 * C++ original:
 * ```cpp
 * class SimpleIntersectionTree {
 * public:
 *     bool add(SkRect rect) {
 *         for (const SkRect& r : fRects) {
 *             if (r.intersects(rect)) {
 *                 return false;
 *             }
 *         }
 *         fRects.push_back(rect);
 *         return true;
 *     }
 *
 * private:
 *     std::vector<SkRect> fRects;
 * }
 * ```
 */
public data class SimpleIntersectionTree public constructor(
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkRect> fRects
   * ```
   */
  private var fRects: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool add(SkRect rect) {
   *         for (const SkRect& r : fRects) {
   *             if (r.intersects(rect)) {
   *                 return false;
   *             }
   *         }
   *         fRects.push_back(rect);
   *         return true;
   *     }
   * ```
   */
  public fun add(rect: SkRect): Boolean {
    TODO("Implement add")
  }
}
