package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGLength {
 * public:
 *     enum class Unit {
 *         kUnknown,
 *         kNumber,
 *         kPercentage,
 *         kEMS,
 *         kEXS,
 *         kPX,
 *         kCM,
 *         kMM,
 *         kIN,
 *         kPT,
 *         kPC,
 *     };
 *
 *     constexpr SkSVGLength()                    : fValue(0), fUnit(Unit::kUnknown) {}
 *     explicit constexpr SkSVGLength(SkScalar v, Unit u = Unit::kNumber)
 *         : fValue(v), fUnit(u) {}
 *     SkSVGLength(const SkSVGLength&)            = default;
 *     SkSVGLength& operator=(const SkSVGLength&) = default;
 *
 *     bool operator==(const SkSVGLength& other) const {
 *         return fUnit == other.fUnit && fValue == other.fValue;
 *     }
 *     bool operator!=(const SkSVGLength& other) const { return !(*this == other); }
 *
 *     const SkScalar& value() const { return fValue; }
 *     const Unit&     unit()  const { return fUnit;  }
 *
 * private:
 *     SkScalar fValue;
 *     Unit     fUnit;
 * }
 * ```
 */
public data class SkSVGLength public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkScalar fValue
   * ```
   */
  private var fValue: Int,
  /**
   * C++ original:
   * ```cpp
   * Unit     fUnit
   * ```
   */
  private var fUnit: Unit,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGLength& operator=(const SkSVGLength&) = default
   * ```
   */
  public fun assign(param0: SkSVGLength) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGLength& other) const {
   *         return fUnit == other.fUnit && fValue == other.fValue;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGLength& other) const { return !(*this == other); }
   * ```
   */
  public fun `value`(): Int {
    TODO("Implement value")
  }

  /**
   * C++ original:
   * ```cpp
   * const SkScalar& value() const { return fValue; }
   * ```
   */
  public fun unit(): Unit {
    TODO("Implement unit")
  }

  public enum class Unit {
    kUnknown,
    kNumber,
    kPercentage,
    kEMS,
    kEXS,
    kPX,
    kCM,
    kMM,
    kIN,
    kPT,
    kPC,
  }
}
