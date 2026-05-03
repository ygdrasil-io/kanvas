package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFillRule {
 * public:
 *     enum class Type {
 *         kNonZero,
 *         kEvenOdd,
 *         kInherit,
 *     };
 *
 *     constexpr SkSVGFillRule() : fType(Type::kInherit) {}
 *     constexpr explicit SkSVGFillRule(Type t) : fType(t) {}
 *
 *     SkSVGFillRule(const SkSVGFillRule&)            = default;
 *     SkSVGFillRule& operator=(const SkSVGFillRule&) = default;
 *
 *     bool operator==(const SkSVGFillRule& other) const { return fType == other.fType; }
 *     bool operator!=(const SkSVGFillRule& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 *     SkPathFillType asFillType() const {
 *         SkASSERT(fType != Type::kInherit); // should never be called for unresolved values.
 *         return fType == Type::kEvenOdd ? SkPathFillType::kEvenOdd : SkPathFillType::kWinding;
 *     }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGFillRule public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  private var fType: Type,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGFillRule& operator=(const SkSVGFillRule&) = default
   * ```
   */
  public fun assign(param0: SkSVGFillRule) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGFillRule& other) const { return fType == other.fType; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGFillRule& other) const { return !(*this == other); }
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
  public fun asFillType(): Int {
    TODO("Implement asFillType")
  }

  public enum class Type {
    kNonZero,
    kEvenOdd,
    kInherit,
  }
}
