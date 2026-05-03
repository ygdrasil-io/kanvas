package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int
import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * class SampleCount {
 * public:
 *     // Do not refer to V directly; use these constants as if SampleCount were a class enum, e.g.
 *     // SampleCount::k4.
 *     enum V : uint8_t {
 *         k1  = 1,
 *         k2  = 2,
 *         k4  = 4,
 *         k8  = 8,
 *         k16 = 16
 *     };
 *
 *     constexpr SampleCount() : fValue(k1) {}
 *     /*implicit*/ constexpr SampleCount(V v) : fValue(v) {}
 *
 *     // Behave like an enum
 *     constexpr bool operator ==(const SampleCount& o) const { return fValue == o.fValue; }
 *     constexpr bool operator  <(const SampleCount& o) const { return fValue  < o.fValue; }
 *     constexpr bool operator <=(const SampleCount& o) const { return fValue <= o.fValue; }
 *     constexpr bool operator  >(const SampleCount& o) const { return fValue  > o.fValue; }
 *     constexpr bool operator >=(const SampleCount& o) const { return fValue >= o.fValue; }
 *
 *     // This needs to be explicit so that ternaries that return constants mixed with variables aren't
 *     // ambiguous; internal code can cast for switch statements.
 *     explicit constexpr operator SampleCount::V() const { return fValue; }
 *     explicit constexpr operator uint8_t()        const { return (uint8_t) fValue; }
 *     explicit constexpr operator unsigned int()   const { return (unsigned int) fValue; }
 *
 *     // Assist migration from old code that would assign integers to sample count fields that used
 *     // to be uint8_t and are now more strictly typed to SampleCount. Asserts if the value doesn't
 *     // match a SampleCount value.
 *     /*implicit*/ constexpr SampleCount(uint8_t v) : fValue((V) v) {
 *         SkASSERT(v == 1 || v == 2 || v == 4 || v == 8 || v == 16);
 *     }
 *     constexpr SampleCount& operator=(uint8_t sampleCount) {
 *         return (*this = SampleCount(sampleCount));
 *     }
 *
 * private:
 *     V fValue;
 * }
 * ```
 */
public data class SampleCount public constructor(
  /**
   * C++ original:
   * ```cpp
   * V fValue
   * ```
   */
  private var fValue: V,
) {
  /**
   * C++ original:
   * ```cpp
   * constexpr bool operator ==(const SampleCount& o) const { return fValue == o.fValue; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool operator  <(const SampleCount& o) const { return fValue  < o.fValue; }
   * ```
   */
  public operator fun compareTo(o: SampleCount): Int {
    TODO("Implement compareTo")
  }

  /**
   * C++ original:
   * ```cpp
   * constexpr bool operator <=(const SampleCount& o) const { return fValue <= o.fValue; }
   * ```
   */
  public fun assign(sampleCount: UByte) {
    TODO("Implement assign")
  }

  public enum class V {
    k1,
    k2,
    k4,
    k8,
    k16,
  }
}
