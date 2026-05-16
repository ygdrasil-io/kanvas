package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFontStyle {
 * public:
 *     enum class Type {
 *         kNormal,
 *         kItalic,
 *         kOblique,
 *         kInherit,
 *     };
 *
 *     SkSVGFontStyle() : fType(Type::kInherit) {}
 *     explicit SkSVGFontStyle(Type t) : fType(t) {}
 *
 *     bool operator==(const SkSVGFontStyle& other) const {
 *         return fType == other.fType;
 *     }
 *     bool operator!=(const SkSVGFontStyle& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGFontStyle public constructor(
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
   * bool operator==(const SkSVGFontStyle& other) const {
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
   * bool operator!=(const SkSVGFontStyle& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kNormal,
    kItalic,
    kOblique,
    kInherit,
  }
}
