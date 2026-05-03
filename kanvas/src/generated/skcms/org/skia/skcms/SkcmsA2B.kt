package org.skia.skcms

import kotlin.Any
import kotlin.Array
import kotlin.UByte
import kotlin.UInt

/**
 * C++ original:
 * ```cpp
 * struct skcms_A2B {
 *     // Optional: N 1D "A" curves, followed by an N-dimensional CLUT.
 *     // If input_channels == 0, these curves and CLUT are skipped,
 *     // Otherwise, input_channels must be in [1, 4].
 *     skcms_Curve     input_curves[4];
 *     const uint8_t*  grid_8;
 *     const uint8_t*  grid_16;
 *     uint32_t        input_channels;
 *     uint8_t         grid_points[4];
 *
 *     // Optional: 3 1D "M" curves, followed by a color matrix.
 *     // If matrix_channels == 0, these curves and matrix are skipped,
 *     // Otherwise, matrix_channels must be 3.
 *     skcms_Curve     matrix_curves[3];
 *     skcms_Matrix3x4 matrix;
 *     uint32_t        matrix_channels;
 *
 *     // Required: 3 1D "B" curves. Always present, and output_channels must be 3.
 *     uint32_t        output_channels; // list first to pack with matrix_channels
 *     skcms_Curve     output_curves[3];
 * }
 * ```
 */
public data class SkcmsA2B public constructor(
  /**
   * C++ original:
   * ```cpp
   * skcms_Curve     input_curves[4]
   * ```
   */
  public var inputCurves: Array<SkcmsCurve>,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t*  grid_8
   * ```
   */
  public val grid8: UByte?,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t*  grid_16
   * ```
   */
  public val grid16: UByte?,
  /**
   * C++ original:
   * ```cpp
   * uint32_t        input_channels
   * ```
   */
  public var inputChannels: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint8_t         grid_points[4]
   * ```
   */
  public var gridPoints: Array<UByte>,
  /**
   * C++ original:
   * ```cpp
   * skcms_Curve     matrix_curves[3]
   * ```
   */
  public var matrixCurves: Array<SkcmsCurve>,
  /**
   * C++ original:
   * ```cpp
   * skcms_Matrix3x4 matrix
   * ```
   */
  public var matrix: SkcmsMatrix3x4,
  /**
   * C++ original:
   * ```cpp
   * uint32_t        matrix_channels
   * ```
   */
  public var matrixChannels: UInt,
  /**
   * C++ original:
   * ```cpp
   * uint32_t        output_channels
   * ```
   */
  public var outputChannels: UInt,
  /**
   * C++ original:
   * ```cpp
   * skcms_Curve     output_curves[3]
   * ```
   */
  public var outputCurves: Array<SkcmsCurve>,
)

public typealias SkcmsA2B = Any
