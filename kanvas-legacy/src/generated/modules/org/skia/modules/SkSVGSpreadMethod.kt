package org.skia.modules

import kotlin.Any
import kotlin.Boolean

/**
 * C++ original:
 * ```cpp
 * class SK_API SkSVGSpreadMethod {
 * public:
 *     // These values must match Skia's SkShader::TileMode enum.
 *     enum class Type {
 *         kPad,       // kClamp_TileMode
 *         kRepeat,    // kRepeat_TileMode
 *         kReflect,   // kMirror_TileMode
 *     };
 *
 *     constexpr SkSVGSpreadMethod() : fType(Type::kPad) {}
 *     constexpr explicit SkSVGSpreadMethod(Type t) : fType(t) {}
 *
 *     SkSVGSpreadMethod(const SkSVGSpreadMethod&)            = default;
 *     SkSVGSpreadMethod& operator=(const SkSVGSpreadMethod&) = default;
 *
 *     bool operator==(const SkSVGSpreadMethod& other) const { return fType == other.fType; }
 *     bool operator!=(const SkSVGSpreadMethod& other) const { return !(*this == other); }
 *
 *     Type type() const { return fType; }
 *
 * private:
 *     Type fType;
 * }
 * ```
 */
public data class SkSVGSpreadMethod public constructor(
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
   * SkSVGSpreadMethod& operator=(const SkSVGSpreadMethod&) = default
   * ```
   */
  public fun assign(param0: SkSVGSpreadMethod) {
    TODO("Implement assign")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator==(const SkSVGSpreadMethod& other) const { return fType == other.fType; }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }

  /**
   * C++ original:
   * ```cpp
   * bool operator!=(const SkSVGSpreadMethod& other) const { return !(*this == other); }
   * ```
   */
  public fun type(): Type {
    TODO("Implement type")
  }

  public enum class Type {
    kPad,
    kRepeat,
    kReflect,
  }
}
