package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGTextAnchor {
 * public:
 *     enum class Type {
 *         kStart,
 *         kMiddle,
 *         kEnd,
 *         kInherit,
 *     };
 *
 *     SkSVGTextAnchor() : fType(Type::kInherit) {}
 *     explicit SkSVGTextAnchor(Type t) : fType(t) {}
 *
 *     bool operator==(const SkSVGTextAnchor& other) const {
 *         return fType == other.fType;
 *     }
 *     bool operator!=(const SkSVGTextAnchor& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGTextAnchor public constructor(
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
   * bool operator==(const SkSVGTextAnchor& other) const {
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
   * bool operator!=(const SkSVGTextAnchor& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kStart,
    kMiddle,
    kEnd,
    kInherit,
  }
}
