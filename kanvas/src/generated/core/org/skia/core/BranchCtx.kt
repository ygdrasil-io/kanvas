package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct BranchCtx {
 *     int offset;  // contains the label ID during compilation, and the program offset when compiled
 * }
 * ```
 */
public open class BranchCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int offset
   * ```
   */
  public var offset: Int,
)
