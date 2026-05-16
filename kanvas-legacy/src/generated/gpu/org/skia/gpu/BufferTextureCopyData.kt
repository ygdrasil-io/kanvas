package org.skia.gpu

import kotlin.Int
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct BufferTextureCopyData {
 *     size_t fBufferOffset;
 *     size_t fBufferRowBytes;
 *     SkIRect fRect;
 *     unsigned int fMipLevel;
 * }
 * ```
 */
public data class BufferTextureCopyData public constructor(
  /**
   * C++ original:
   * ```cpp
   * size_t fBufferOffset
   * ```
   */
  public var fBufferOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * size_t fBufferRowBytes
   * ```
   */
  public var fBufferRowBytes: Int,
  /**
   * C++ original:
   * ```cpp
   * SkIRect fRect
   * ```
   */
  public var fRect: Int,
  /**
   * C++ original:
   * ```cpp
   * unsigned int fMipLevel
   * ```
   */
  public var fMipLevel: UInt,
)
