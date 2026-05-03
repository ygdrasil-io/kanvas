package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkSFNTDirEntry {
 *     uint32_t    fTag;
 *     uint32_t    fChecksum;
 *     uint32_t    fOffset;
 *     uint32_t    fLength;
 * }
 * ```
 */
public data class SkSFNTDirEntry public constructor(
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
   * uint32_t    fChecksum
   * ```
   */
  public var fChecksum: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fOffset
   * ```
   */
  public var fOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t    fLength
   * ```
   */
  public var fLength: Int,
)
