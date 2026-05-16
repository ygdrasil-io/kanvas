package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFontFamily {
 * public:
 *     enum class Type {
 *         kFamily,
 *         kInherit,
 *     };
 *
 *     SkSVGFontFamily() : fType(Type::kInherit) {}
 *     explicit SkSVGFontFamily(const char family[])
 *         : fType(Type::kFamily)
 *         , fFamily(family) {}
 *
 *     bool operator==(const SkSVGFontFamily& other) const {
 *         return fType == other.fType && fFamily == other.fFamily;
 *     }
 *     bool operator!=(const SkSVGFontFamily& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 *     const SkString& family() const { return fFamily; }
 *
 * private:
 *     Type     fType;
 *     SkString fFamily;
 * }
 * ```
 */
public data class SkSVGFontFamily public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type     fType
   * ```
   */
  private var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * SkString fFamily
   * ```
   */
  private var fFamily: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGFontFamily& other) const {
   *         return fType == other.fType && fFamily == other.fFamily;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGFontFamily& other) const { return !(*this == other); }
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
  public fun family(): Int {
    TODO("Implement family")
  }

  public enum class Type {
    kFamily,
    kInherit,
  }
}
