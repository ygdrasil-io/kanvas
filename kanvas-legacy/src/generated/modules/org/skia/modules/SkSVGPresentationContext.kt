package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SK_API SkSVGPresentationContext {
 *     SkSVGPresentationContext();
 *     SkSVGPresentationContext(const SkSVGPresentationContext&)            = default;
 *     SkSVGPresentationContext& operator=(const SkSVGPresentationContext&) = default;
 *
 *     const skia_private::THashMap<SkString, SkSVGColorType>* fNamedColors = nullptr;
 *
 *     // Inherited presentation attributes, computed for the current node.
 *     SkSVGPresentationAttributes fInherited;
 * }
 * ```
 */
public data class SkSVGPresentationContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * const skia_private::THashMap<SkString, SkSVGColorType>* fNamedColors
   * ```
   */
  public val fNamedColors: Int?,
  /**
   * C++ original:
   * ```cpp
   * SkSVGPresentationAttributes fInherited
   * ```
   */
  public var fInherited: SkSVGPresentationContext,
) {
  /**
   * C++ original:
   * ```cpp
   * SkSVGPresentationContext& operator=(const SkSVGPresentationContext&) = default
   * ```
   */
  public fun assign(param0: SkSVGPresentationContext) {
    TODO("Implement assign")
  }
}
