package org.skia.gpu

import kotlin.Int
import org.skia.math.SkPoint

/**
 * C++ original:
 * ```cpp
 * struct NullTriangulator {
 *     NullTriangulator(int, SkPoint) {}
 * }
 * ```
 */
public open class NullTriangulator public constructor(
  param0: Int,
  param1: SkPoint,
)
