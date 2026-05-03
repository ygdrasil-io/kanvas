package org.skia.modules

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct ConstructionContext {
 *     ConstructionContext(SkSVGIDMapper* mapper) : fParent(nullptr), fIDMapper(mapper) {}
 *     ConstructionContext(const ConstructionContext& other, const sk_sp<SkSVGNode>& newParent)
 *         : fParent(newParent.get()), fIDMapper(other.fIDMapper) {}
 *
 *     SkSVGNode*     fParent;
 *     SkSVGIDMapper* fIDMapper;
 * }
 * ```
 */
public data class ConstructionContext public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkSVGNode*     fParent
   * ```
   */
  public var fParent: SkSVGNode?,
  /**
   * C++ original:
   * ```cpp
   * SkSVGIDMapper* fIDMapper
   * ```
   */
  public var fIDMapper: Int?,
)
