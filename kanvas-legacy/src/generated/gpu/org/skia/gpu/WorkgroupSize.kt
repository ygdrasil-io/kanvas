package org.skia.gpu

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct WorkgroupSize {
 *     WorkgroupSize() = default;
 *     WorkgroupSize(uint32_t width, uint32_t height, uint32_t depth)
 *             : fWidth(width)
 *             , fHeight(height)
 *             , fDepth(depth) {}
 *
 *     uint32_t scalarSize() const { return fWidth * fHeight * fDepth; }
 *
 *     uint32_t fWidth = 1;
 *     uint32_t fHeight = 1;
 *     uint32_t fDepth = 1;
 * }
 * ```
 */
public data class WorkgroupSize public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint32_t fWidth
   * ```
   */
  public var fWidth: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fHeight
   * ```
   */
  public var fHeight: Int,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fDepth
   * ```
   */
  public var fDepth: Int,
) {
  /**
   * C++ original:
   * ```cpp
   * uint32_t scalarSize() const { return fWidth * fHeight * fDepth; }
   * ```
   */
  public fun scalarSize(): Int {
    TODO("Implement scalarSize")
  }
}
