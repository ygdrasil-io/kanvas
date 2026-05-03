package org.skia.skcms

import kotlin.Any
import kotlin.FloatArray

/**
 * C++ original:
 * ```cpp
 * struct skcms_Matrix3x4 {
 *     float vals[3][4];
 * }
 * ```
 */
public data class SkcmsMatrix3x4 public constructor(
  /**
   * C++ original:
   * ```cpp
   * float vals[3][4]
   * ```
   */
  public var vals: FloatArray,
)

public typealias SkcmsMatrix3x4 = Any
