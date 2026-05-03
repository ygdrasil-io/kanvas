package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGDashArray {
 * public:
 *     enum class Type {
 *         kNone,
 *         kDashArray,
 *         kInherit,
 *     };
 *
 *     SkSVGDashArray()                : fType(Type::kNone) {}
 *     explicit SkSVGDashArray(Type t) : fType(t) {}
 *     explicit SkSVGDashArray(std::vector<SkSVGLength>&& dashArray)
 *         : fType(Type::kDashArray)
 *         , fDashArray(std::move(dashArray)) {}
 *
 *     SkSVGDashArray(const SkSVGDashArray&)            = default;
 *     SkSVGDashArray& operator=(const SkSVGDashArray&) = default;
 *
 *     bool operator==(const SkSVGDashArray& other) const {
 *         return fType == other.fType && fDashArray == other.fDashArray;
 *     }
 *     bool operator!=(const SkSVGDashArray& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 *     const std::vector<SkSVGLength>& dashArray() const { return fDashArray; }
 *
 * private:
 *     Type fType;
 *     std::vector<SkSVGLength> fDashArray;
 * }
 * ```
 */
public data class SkSVGDashArray public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  private var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * std::vector<SkSVGLength> fDashArray
   * ```
   */
  private var fDashArray: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGDashArray& operator=(const SkSVGDashArray&) = default
   * ```
   */
  public fun assign(param0: SkSVGDashArray) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGDashArray& other) const {
   *         return fType == other.fType && fDashArray == other.fDashArray;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGDashArray& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  /**
   * C++ original:
   * ```cpp
   * Type type() const { return fType; }
   * ```
   */
  public fun dashArray(): Int {
    TODO("Implement dashArray")
  }

  public enum class Type {
    kNone,
    kDashArray,
    kInherit,
  }
}
