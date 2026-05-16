package org.skia.tests

import kotlin.Int
import kotlin.IntArray
import org.skia.core.SkRecord

/**
 * C++ original:
 * ```cpp
 * class Tally {
 * public:
 *     Tally() { sk_bzero(&fHistogram, sizeof(fHistogram)); }
 *
 *     template <typename T>
 *     void operator()(const T&) { ++fHistogram[T::kType]; }
 *
 *     template <typename T>
 *     int count() const { return fHistogram[T::kType]; }
 *
 *     void apply(const SkRecord& record) {
 *         for (int i = 0; i < record.count(); i++) {
 *             record.visit(i, *this);
 *         }
 *     }
 *
 * private:
 *     int fHistogram[kRecordTypes];
 * }
 * ```
 */
public data class Tally public constructor(
  /**
   * C++ original:
   * ```cpp
   * int fHistogram[kRecordTypes]
   * ```
   */
  private var fHistogram: IntArray,
) {
  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     void operator()(const T&) { ++fHistogram[T::kType]; }
   * ```
   */
  public operator fun <T> invoke(param0: T) {
    TODO("Implement invoke")
  }

  /**
   * C++ original:
   * ```cpp
   *     template <typename T>
   *     int count() const { return fHistogram[T::kType]; }
   * ```
   */
  public fun <T> count(): Int {
    TODO("Implement count")
  }

  /**
   * C++ original:
   * ```cpp
   * void apply(const SkRecord& record) {
   *         for (int i = 0; i < record.count(); i++) {
   *             record.visit(i, *this);
   *         }
   *     }
   * ```
   */
  public fun apply(record: SkRecord) {
    TODO("Implement apply")
  }
}
