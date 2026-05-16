package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct BranchIfEqualCtx : public BranchCtx {
 *     int value;
 *     const int* ptr;
 * }
 * ```
 */
public open class BranchIfEqualCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int value
   * ```
   */
  public var `value`: Int,
  /**
   * C++ original:
   * ```cpp
   * const int* ptr
   * ```
   */
  public val ptr: Int?,
) : BranchCtx(TODO())
