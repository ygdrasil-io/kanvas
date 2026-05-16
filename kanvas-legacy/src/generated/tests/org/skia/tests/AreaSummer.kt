package org.skia.tests

import kotlin.Int
import org.skia.core.SkRecord

/**
 * C++ original:
 * ```cpp
 * class AreaSummer {
 * public:
 *     AreaSummer() : fArea(0) {}
 *
 *     template <typename T> void operator()(const T&) { }
 *
 *     void operator()(const SkRecords::DrawRect& draw) {
 *         fArea += (int)(draw.rect.width() * draw.rect.height());
 *     }
 *
 *     int area() const { return fArea; }
 *
 *     void apply(const SkRecord& record) {
 *         for (int i = 0; i < record.count(); i++) {
 *             record.visit(i, *this);
 *         }
 *     }
 *
 * private:
 *     int fArea;
 * }
 * ```
 */
public data class AreaSummer public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fArea
   * ```
   */
  private var fArea: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * void operator()(const T&) { }
   * ```
   */
  public operator fun invoke(param0: T) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T> void operator()(const T&) { }
   *
   *     void operator()(const SkRecords::DrawRect& draw) {
   *         fArea += (int)(draw.rect.width() * draw.rect.height());
   *     }
   * ```
   */
  public fun area(): Int {
    TODO("Implement area")
  }

  /**
   * C++ original:
   * ```cpp
   * int area() const { return fArea; }
   * ```
   */
  public fun apply(record: SkRecord) {
    TODO("Implement apply")
  }
}
