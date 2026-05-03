package org.skia.gpu

import kotlin.Any
import kotlin.Boolean
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct BindBufferInfo {
 *     const Buffer* fBuffer = nullptr;
 *     uint32_t fOffset = 0;
 *     uint32_t fSize = 0;
 *
 *     explicit operator bool() const { return SkToBool(fBuffer); }
 *
 *     bool operator==(const BindBufferInfo& o) const {
 *         return fBuffer == o.fBuffer && (!fBuffer || (fOffset == o.fOffset && fSize == o.fSize));
 *     }
 *     bool operator!=(const BindBufferInfo& o) const { return !(*this == o); }
 * }
 * ```
 */
public data class BindBufferInfo public constructor(
  /**
   * C++ original:
   * ```cpp
   * const Buffer* fBuffer = nullptr
   * ```
   */
  public val fBuffer: Buffer?,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fOffset
   * ```
   */
  public var fOffset: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fSize
   * ```
   */
  public var fSize: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * bool operator==(const BindBufferInfo& o) const {
   *         return fBuffer == o.fBuffer && (!fBuffer || (fOffset == o.fOffset && fSize == o.fSize));
   *     }
   * ```
   */
  public override operator fun equals(other: Any?): Boolean {
    TODO("Implement equals")
  }
}
