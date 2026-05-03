package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGFontSize {
 * public:
 *     enum class Type {
 *         kLength,
 *         kInherit,
 *     };
 *
 *     SkSVGFontSize() : fType(Type::kInherit), fSize(0) {}
 *     explicit SkSVGFontSize(const SkSVGLength& s)
 *         : fType(Type::kLength)
 *         , fSize(s) {}
 *
 *     bool operator==(const SkSVGFontSize& other) const {
 *         return fType == other.fType && fSize == other.fSize;
 *     }
 *     bool operator!=(const SkSVGFontSize& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 *     const SkSVGLength& size() const { return fSize; }
 *
 * private:
 *     Type        fType;
 *     SkSVGLength fSize;
 * }
 * ```
 */
public data class SkSVGFontSize public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type        fType
   * ```
   */
  private var fType: Type,
  /**
   * C++ original:
   * ```cpp
   * SkSVGLength fSize
   * ```
   */
  private var fSize: SkSVGLength,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGFontSize& other) const {
   *         return fType == other.fType && fSize == other.fSize;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGFontSize& other) const { return !(*this == other); }
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
  public fun size(): SkSVGLength {
    TODO("Implement size")
  }

  public enum class Type {
    kLength,
    kInherit,
  }
}
