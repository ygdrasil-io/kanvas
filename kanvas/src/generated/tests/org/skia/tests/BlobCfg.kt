package org.skia.tests

import kotlin.UInt
import org.skia.math.SkScalar

/**
 * C++ original:
 * ```cpp
 * struct BlobCfg {
 *     unsigned count;
 *     Pos      pos;
 *     SkScalar scale;
 * }
 * ```
 */
public data class BlobCfg public constructor(
  /**
   * C++ original:
   * ```cpp
   * unsigned count
   * ```
   */
  public var count: UInt,
  /**
   * C++ original:
   * ```cpp
   * Pos      pos
   * ```
   */
  public var pos: Pos,
  /**
   * C++ original:
   * ```cpp
   * SkScalar scale
   * ```
   */
  public var scale: SkScalar,
)
