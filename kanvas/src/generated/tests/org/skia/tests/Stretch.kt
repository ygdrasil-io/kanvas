package org.skia.tests

import org.skia.core.SkRecord

/**
 * C++ original:
 * ```cpp
 * struct Stretch {
 *     template <typename T> void operator()(T*) {}
 *     void operator()(SkRecords::DrawRect* draw) {
 *         draw->rect.fRight *= 2;
 *         draw->rect.fBottom *= 2;
 *     }
 *
 *     void apply(SkRecord* record) {
 *         for (int i = 0; i < record->count(); i++) {
 *             record->mutate(i, *this);
 *         }
 *     }
 * }
 * ```
 */
public open class Stretch {
  /**
   * C++ original:
   * ```cpp
   * void operator()(T*) {}
   * ```
   */
  public operator fun invoke(param0: T?) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T> void operator()(T*) {}
   *     void operator()(SkRecords::DrawRect* draw) {
   *         draw->rect.fRight *= 2;
   *         draw->rect.fBottom *= 2;
   *     }
   * ```
   */
  public fun apply(record: SkRecord?) {
    TODO("Implement apply")
  }
}
