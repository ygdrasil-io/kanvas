package org.skia.core

import kotlin.Boolean
import kotlin.Int
import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * class SkSafeRange {
 * public:
 *     explicit operator bool() const { return fOK; }
 *
 *     bool ok() const { return fOK; }
 *
 *     // checks 0 <= value <= max.
 *     // On success, returns value
 *     // On failure, returns 0 and sets ok() to false
 *     template <typename T> T checkLE(uint64_t value, T max) {
 *         SkASSERT(static_cast<int64_t>(max) >= 0);
 *         if (value > static_cast<uint64_t>(max)) {
 *             fOK = false;
 *             value = 0;
 *         }
 *         return static_cast<T>(value);
 *     }
 *
 *     int checkGE(int value, int min) {
 *         if (value < min) {
 *             fOK = false;
 *             value = min;
 *         }
 *         return value;
 *     }
 *
 * private:
 *     bool fOK = true;
 * }
 * ```
 */
public data class SkSafeRange public constructor(
  /**
   * C++ original:
   * ```cpp
   * bool fOK = true
   * ```
   */
  private var fOK: Boolean,
) {
  /**
   * C++ original:
   * ```cpp
   * bool ok() const { return fOK; }
   * ```
   */
  public fun ok(): Boolean {
    TODO("Implement ok")
  }

  /**
   * C++ original:
   * ```cpp
   * T checkLE(uint64_t value, T max) {
   *         SkASSERT(static_cast<int64_t>(max) >= 0);
   *         if (value > static_cast<uint64_t>(max)) {
   *             fOK = false;
   *             value = 0;
   *         }
   *         return static_cast<T>(value);
   *     }
   * ```
   */
  public fun checkLE(`value`: ULong, max: T): T {
    TODO("Implement checkLE")
  }

  /**
   * C++ original:
   * ```cpp
   * int checkGE(int value, int min) {
   *         if (value < min) {
   *             fOK = false;
   *             value = min;
   *         }
   *         return value;
   *     }
   * ```
   */
  public fun checkGE(`value`: Int, min: Int): Int {
    TODO("Implement checkGE")
  }
}
