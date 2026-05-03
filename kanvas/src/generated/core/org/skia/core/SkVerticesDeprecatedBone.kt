package org.skia.core

import kotlin.FloatArray

/**
 * C++ original:
 * ```cpp
 * struct SkVertices_DeprecatedBone { float values[6]; }
 * ```
 */
public data class SkVerticesDeprecatedBone public constructor(
  /**
   * C++ original:
   * ```cpp
   * float values[6]
   * ```
   */
  public var values: FloatArray,
)
