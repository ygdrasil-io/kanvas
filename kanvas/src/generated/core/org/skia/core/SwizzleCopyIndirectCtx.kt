package org.skia.core

import kotlin.IntArray

/**
 * C++ original:
 * ```cpp
 * struct SwizzleCopyIndirectCtx : public CopyIndirectCtx {
 *     uint16_t offsets[4];  // values must be byte offsets (4 * highp-stride * component-index)
 * }
 * ```
 */
public open class SwizzleCopyIndirectCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint16_t offsets[4]
   * ```
   */
  public var offsets: IntArray,
) : CopyIndirectCtx(TODO())
