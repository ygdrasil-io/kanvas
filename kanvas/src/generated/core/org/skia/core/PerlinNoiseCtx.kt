package org.skia.core

import kotlin.Boolean
import kotlin.Float
import kotlin.Int

/**
 * C++ original:
 * ```cpp
 * struct PerlinNoiseCtx {
 *     SkPerlinNoiseShaderType noiseType;
 *     float baseFrequencyX, baseFrequencyY;
 *     float stitchDataInX, stitchDataInY;
 *     bool stitching;
 *     int numOctaves;
 *     const uint8_t* latticeSelector;  // [256 values]
 *     const uint16_t* noiseData;       // [4 channels][256 elements][vector of 2]
 * }
 * ```
 */
public data class PerlinNoiseCtx public constructor(
  /**
   * C++ original:
   * ```cpp
   * SkPerlinNoiseShaderType noiseType
   * ```
   */
  public var noiseType: SkPerlinNoiseShaderType,
  /**
   * C++ original:
   * ```cpp
   * float baseFrequencyX
   * ```
   */
  public var baseFrequencyX: Float,
  /**
   * C++ original:
   * ```cpp
   * float baseFrequencyX, baseFrequencyY
   * ```
   */
  public var baseFrequencyY: Float,
  /**
   * C++ original:
   * ```cpp
   * float stitchDataInX
   * ```
   */
  public var stitchDataInX: Float,
  /**
   * C++ original:
   * ```cpp
   * float stitchDataInX, stitchDataInY
   * ```
   */
  public var stitchDataInY: Float,
  /**
   * C++ original:
   * ```cpp
   * bool stitching
   * ```
   */
  public var stitching: Boolean,
  /**
   * C++ original:
   * ```cpp
   * int numOctaves
   * ```
   */
  public var numOctaves: Int,
  /**
   * C++ original:
   * ```cpp
   * const uint8_t* latticeSelector
   * ```
   */
  public val latticeSelector: Int?,
  /**
   * C++ original:
   * ```cpp
   * const uint16_t* noiseData
   * ```
   */
  public val noiseData: Int?,
)
