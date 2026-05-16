package org.skia.core

import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct SkSFNTHeader {
 *     uint32_t    fVersion;
 *     uint16_t    fNumTables;
 *     uint16_t    fSearchRange;
 *     uint16_t    fEntrySelector;
 *     uint16_t    fRangeShift;
 * }
 * ```
 */
public data class SkSFNTHeader public constructor(
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
   * uint16_t    fNumTables
   * ```
   */
  public var fNumTables: Int,
  /**
   * C++ original:
   * ```cpp
   * uint16_t    fSearchRange
   * ```
   */
  public var fSearchRange: Int,
  /**
   * C++ original:
   * ```cpp
   * uint16_t    fEntrySelector
   * ```
   */
  public var fEntrySelector: Int,
  /**
   * C++ original:
   * ```cpp
   * uint16_t    fRangeShift
   * ```
   */
  public var fRangeShift: Int,
)
