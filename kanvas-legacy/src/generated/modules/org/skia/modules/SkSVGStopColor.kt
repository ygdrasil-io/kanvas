package org.skia.modules

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGStopColor {
 * public:
 *     enum class Type {
 *         kColor,
 *         kCurrentColor,
 *         kICCColor,
 *         kInherit,
 *     };
 *
 *     SkSVGStopColor() : fType(Type::kColor), fColor(SK_ColorBLACK) {}
 *     explicit SkSVGStopColor(Type t) : fType(t), fColor(SK_ColorBLACK) {}
 *     explicit SkSVGStopColor(const SkSVGColorType& c) : fType(Type::kColor), fColor(c) {}
 *
 *     SkSVGStopColor(const SkSVGStopColor&)            = default;
 *     SkSVGStopColor& operator=(const SkSVGStopColor&) = default;
 *
 *     bool operator==(const SkSVGStopColor& other) const {
 *         return fType == other.fType && fColor == other.fColor;
 *     }
 *     bool operator!=(const SkSVGStopColor& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *     const SkSVGColorType& color() const { SkASSERT(fType == Type::kColor); return fColor; }
 *
 * private:
 *     Type fType;
 *     SkSVGColorType fColor;
 * }
 * ```
 */
public data class SkSVGStopColor public constructor(
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
   * SkSVGColorType fColor
   * ```
   */
  private var fColor: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGStopColor& operator=(const SkSVGStopColor&) = default
   * ```
   */
  public fun assign(param0: SkSVGStopColor) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGStopColor& other) const {
   *         return fType == other.fType && fColor == other.fColor;
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGStopColor& other) const { return !(*this == other); }
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
  public fun color(): Int {
    TODO("Implement color")
  }

  public enum class Type {
    kColor,
    kCurrentColor,
    kICCColor,
    kInherit,
  }
}
