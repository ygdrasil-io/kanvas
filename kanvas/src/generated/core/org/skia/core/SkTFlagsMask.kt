package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SkTFlagsMask {
 * public:
 *     constexpr explicit SkTFlagsMask(TFlags value) : SkTFlagsMask(static_cast<int>(value)) {}
 *     constexpr explicit SkTFlagsMask(int value) : fValue(value) {}
 *     constexpr int value() const { return fValue; }
 * private:
 *     const int fValue;
 * }
 * ```
 */
public data class SkTFlagsMask<TFlags> public constructor(
  /**
   * C++ original:
   * ```cpp
   * const int fValue
   * ```
   */
  private val fValue: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr int value() const { return fValue; }
   * ```
   */
  public fun `value`(): Int {
    TODO("Implement value")
  }
}
