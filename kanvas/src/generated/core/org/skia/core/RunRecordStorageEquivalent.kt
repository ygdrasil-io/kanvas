package org.skia.core

import kotlin.UInt
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct RunRecordStorageEquivalent {
 *     SkFont   fFont;
 *     SkPoint  fOffset;
 *     uint32_t fCount;
 *     uint32_t fFlags;
 *     SkDEBUGCODE(unsigned fMagic;)
 * }
 * ```
 */
public data class RunRecordStorageEquivalent public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkFont   fFont
   * ```
   */
  public var fFont: SkFont,
  /**
   * C++ original:
   * ```cpp
   * SkPoint  fOffset
   * ```
   */
  public var fOffset: SkPoint,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fCount
   * ```
   */
  public var fCount: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t fFlags
   * ```
   */
  public var fFlags: UInt,
)
