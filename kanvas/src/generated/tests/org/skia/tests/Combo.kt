package org.skia.tests

import kotlin.ULong

/**
 * C++ original:
 * ```cpp
 * struct Combo {
 *     GradientCreationFunc fCreateOptionsMtd;
 *     size_t fNumStops;
 * }
 * ```
 */
public data class Combo public constructor(
  /**
   * C++ original:
   * ```cpp
   * GradientCreationFunc fCreateOptionsMtd
   * ```
   */
  public var fCreateOptionsMtd: GradientCreationFunc,
  /**
   * C++ original:
   * ```cpp
   * size_t fNumStops
   * ```
   */
  public var fNumStops: ULong,
)
