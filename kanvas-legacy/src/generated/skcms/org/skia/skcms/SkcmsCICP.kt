package org.skia.skcms

import kotlin.Any
import kotlin.UByte

/**
 * C++ original:
 * ```cpp
 * struct skcms_CICP {
 *     uint8_t color_primaries;
 *     uint8_t transfer_characteristics;
 *     uint8_t matrix_coefficients;
 *     uint8_t video_full_range_flag;
 * }
 * ```
 */
public data class SkcmsCICP public constructor(
  /**
   * C++ original:
   * ```cpp
   * uint8_t color_primaries
   * ```
   */
  public var colorPrimaries: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t transfer_characteristics
   * ```
   */
  public var transferCharacteristics: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t matrix_coefficients
   * ```
   */
  public var matrixCoefficients: UByte,
  /**
   * C++ original:
   * ```cpp
   * uint8_t video_full_range_flag
   * ```
   */
  public var videoFullRangeFlag: UByte,
)

public typealias SkcmsCICP = Any
