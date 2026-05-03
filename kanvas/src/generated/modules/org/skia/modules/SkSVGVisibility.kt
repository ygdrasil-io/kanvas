package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGVisibility {
 * public:
 *     enum class Type {
 *         kVisible,
 *         kHidden,
 *         kCollapse,
 *         kInherit,
 *     };
 *
 *     constexpr SkSVGVisibility() : fType(Type::kVisible) {}
 *     constexpr explicit SkSVGVisibility(Type t) : fType(t) {}
 *
 *     SkSVGVisibility(const SkSVGVisibility&)            = default;
 *     SkSVGVisibility& operator=(const SkSVGVisibility&) = default;
 *
 *     bool operator==(const SkSVGVisibility& other) const { return fType == other.fType; }
 *     bool operator!=(const SkSVGVisibility& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGVisibility public constructor(
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
   * SkSVGVisibility& operator=(const SkSVGVisibility&) = default
   * ```
   */
  public fun assign(param0: SkSVGVisibility) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGVisibility& other) const { return fType == other.fType; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGVisibility& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kVisible,
    kHidden,
    kCollapse,
    kInherit,
  }
}
