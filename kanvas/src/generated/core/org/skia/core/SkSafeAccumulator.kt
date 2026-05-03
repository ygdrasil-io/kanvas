package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SkSafeAccumulator {
 * public:
 *     SkSafeAccumulator(size_t n = 0) : fTotal(n) {}
 *
 *     bool ok() const { return fSafe.ok(); }
 *     explicit operator bool() const { return fSafe.ok(); }
 *
 *     SkSafeAccumulator& add(size_t n) {
 *         fTotal = fSafe.add(fTotal, n);
 *         return *this;
 *     }
 *
 *     SkSafeAccumulator& addMul(size_t a, size_t b) {
 *         fTotal = fSafe.add(fTotal, fSafe.mul(a, b));
 *         return *this;
 *     }
 *
 *     std::optional<size_t> total() const {
 *         if (fSafe.ok()) {
 *             return fTotal;
 *         }
 *         return {};
 *     }
 * private:
 *     SkSafeMath fSafe;
 *     size_t     fTotal;
 * }
 * ```
 */
public data class SkSafeAccumulator public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSafeMath fSafe
   * ```
   */
  private var fSafe: SkSafeMath,
  /**
   * C++ original:
   * ```cpp
   * size_t     fTotal
   * ```
   */
  private var fTotal: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool ok() const { return fSafe.ok(); }
   * ```
   */
  public fun ok(): Boolean {
    TODO("Implement ok")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSafeAccumulator& add(size_t n) {
   *         fTotal = fSafe.add(fTotal, n);
   *         return *this;
   *     }
   * ```
   */
  public fun add(n: ULong): SkSafeAccumulator {
    TODO("Implement add")
  }

  /**
   * C++ original:
   * ```cpp
   * SkSafeAccumulator& addMul(size_t a, size_t b) {
   *         fTotal = fSafe.add(fTotal, fSafe.mul(a, b));
   *         return *this;
   *     }
   * ```
   */
  public fun addMul(a: ULong, b: ULong): SkSafeAccumulator {
    TODO("Implement addMul")
  }

  /**
   * C++ original:
   * ```cpp
   * std::optional<size_t> total() const {
   *         if (fSafe.ok()) {
   *             return fTotal;
   *         }
   *         return {};
   *     }
   * ```
   */
  public fun total(): Int {
    TODO("Implement total")
  }
}
