package org.skia.modules

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkSVGFeTurbulenceType {
 *     enum Type {
 *         kFractalNoise,
 *         kTurbulence,
 *     };
 *
 *     Type fType;
 *
 *     SkSVGFeTurbulenceType() : fType(kTurbulence) {}
 *     explicit SkSVGFeTurbulenceType(Type type) : fType(type) {}
 * }
 * ```
 */
public data class SkSVGFeTurbulenceType public constructor(
  /**
   * C++ original:
   * ```cpp
   * Type fType
   * ```
   */
  public var fType: Type,
) {
  public enum class Type {
    kFractalNoise,
    kTurbulence,
  }
}
