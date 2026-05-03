package org.skia.skcms

import kotlin.Any
import kotlin.FloatArray

/**
 * C++ original:
 * ```cpp
 * struct skcms_Matrix3x3 {
 *     float vals[3][3];
 * }
 * ```
 */
public data class SkcmsMatrix3x3 public constructor(
  /**
   * C++ original:
   * ```cpp
   * float vals[3][3]
   * ```
   */
  public var vals: FloatArray,
)

public typealias SkcmsMatrix3x3 = Any
