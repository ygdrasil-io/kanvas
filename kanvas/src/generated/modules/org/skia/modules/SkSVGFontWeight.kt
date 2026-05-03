package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFontWeight {
 * public:
 *     enum class Type {
 *         k100,
 *         k200,
 *         k300,
 *         k400,
 *         k500,
 *         k600,
 *         k700,
 *         k800,
 *         k900,
 *         kNormal,
 *         kBold,
 *         kBolder,
 *         kLighter,
 *         kInherit,
 *     };
 *
 *     SkSVGFontWeight() : fType(Type::kInherit) {}
 *     explicit SkSVGFontWeight(Type t) : fType(t) {}
 *
 *     bool operator==(const SkSVGFontWeight& other) const {
 *         return fType == other.fType;
 *     }
 *     bool operator!=(const SkSVGFontWeight& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGFontWeight public constructor(
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
   * bool operator==(const SkSVGFontWeight& other) const {
   *         return fType == other.fType;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGFontWeight& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    k100,
    k200,
    k300,
    k400,
    k500,
    k600,
    k700,
    k800,
    k900,
    kNormal,
    kBold,
    kBolder,
    kLighter,
    kInherit,
  }
}
