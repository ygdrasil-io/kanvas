package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct CopyIndirectCtx {
 *     int32_t* dst;
 *     const int32_t* src;
 *     const uint32_t *indirectOffset;  // this applies to `src` or `dst` based on the op
 *     uint32_t indirectLimit;          // the indirect offset is clamped to this upper bound
 *     uint32_t slots;                  // the number of slots to copy
 * }
 * ```
 */
public open class CopyIndirectCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * int32_t* dst
   * ```
   */
  public var dst: Int?,
  /**
   * C++ original:
   * ```cpp
   * const int32_t* src
   * ```
   */
  public val src: Int?,
  /**
   * C++ original:
   * ```cpp
   * const uint32_t *indirectOffset
   * ```
   */
  public val indirectOffset: Int?,
  /**
   * C++ original:
   * ```cpp
   * uint32_t indirectLimit
   * ```
   */
  public var indirectLimit: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t slots
   * ```
   */
  public var slots: Int,
)
