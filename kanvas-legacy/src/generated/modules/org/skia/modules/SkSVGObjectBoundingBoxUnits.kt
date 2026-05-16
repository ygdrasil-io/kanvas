package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGObjectBoundingBoxUnits {
 * public:
 *     enum class Type {
 *         kUserSpaceOnUse,
 *         kObjectBoundingBox,
 *     };
 *
 *     SkSVGObjectBoundingBoxUnits() : fType(Type::kUserSpaceOnUse) {}
 *     explicit SkSVGObjectBoundingBoxUnits(Type t) : fType(t) {}
 *
 *     bool operator==(const SkSVGObjectBoundingBoxUnits& other) const {
 *         return fType == other.fType;
 *     }
 *     bool operator!=(const SkSVGObjectBoundingBoxUnits& other) const {
 *         return !(*this == other);
 *     }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGObjectBoundingBoxUnits public constructor(
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
   * bool operator==(const SkSVGObjectBoundingBoxUnits& other) const {
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
   * bool operator!=(const SkSVGObjectBoundingBoxUnits& other) const {
   *         return !(*this == other);
   *     }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kUserSpaceOnUse,
    kObjectBoundingBox,
  }
}
