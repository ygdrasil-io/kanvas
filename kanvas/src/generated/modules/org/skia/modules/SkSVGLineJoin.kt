package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGLineJoin {
 * public:
 *     enum class Type {
 *         kMiter,
 *         kRound,
 *         kBevel,
 *         kInherit,
 *     };
 *
 *     constexpr SkSVGLineJoin() : fType(Type::kInherit) {}
 *     constexpr explicit SkSVGLineJoin(Type t) : fType(t) {}
 *
 *     SkSVGLineJoin(const SkSVGLineJoin&)            = default;
 *     SkSVGLineJoin& operator=(const SkSVGLineJoin&) = default;
 *
 *     bool operator==(const SkSVGLineJoin& other) const { return fType == other.fType; }
 *     bool operator!=(const SkSVGLineJoin& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGLineJoin public constructor(
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
   * SkSVGLineJoin& operator=(const SkSVGLineJoin&) = default
   * ```
   */
  public fun assign(param0: SkSVGLineJoin) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGLineJoin& other) const { return fType == other.fType; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGLineJoin& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kMiter,
    kRound,
    kBevel,
    kInherit,
  }
}
