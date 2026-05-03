package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkTTCFHeader {
 *     uint32_t    fTag;
 *     uint32_t    fVersion;
 *     uint32_t    fNumOffsets;
 *     uint32_t    fOffset0;   // the first of N (fNumOffsets)
 * }
 * ```
 */
public data class SkTTCFHeader public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fTag
   * ```
   */
  public var fTag: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fVersion
   * ```
   */
  public var fVersion: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fNumOffsets
   * ```
   */
  public var fNumOffsets: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fOffset0
   * ```
   */
  public var fOffset0: Int,
)
