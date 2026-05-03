package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct BranchIfAllLanesActiveCtx : public BranchCtx {
 *     uint8_t* tail = nullptr;  // lanes past the tail are _never_ active, so we need to exclude them
 * }
 * ```
 */
public open class BranchIfAllLanesActiveCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t* tail
   * ```
   */
  public var tail: Int?,
) : BranchCtx(TODO())
